package com.sds.printerdost.data.repository

import com.sds.printerdost.BuildConfig
import com.sds.printerdost.data.api.Content
import com.sds.printerdost.data.api.GenerateContentRequest
import com.sds.printerdost.data.api.Part
import com.sds.printerdost.data.api.RetrofitClient
import com.sds.printerdost.data.local.PrinterDao
import com.sds.printerdost.data.local.PrinterEntity
import com.sds.printerdost.data.local.DiagnosticLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress

class PrinterRepository(private val printerDao: PrinterDao) {

    val allPrinters: Flow<List<PrinterEntity>> = printerDao.getAllPrinters()
    val allDiagnosticLogs: Flow<List<DiagnosticLogEntity>> = printerDao.getAllDiagnosticLogs()

    suspend fun getPrinterById(id: Int): PrinterEntity? {
        return printerDao.getPrinterById(id)
    }

    suspend fun insertPrinter(printer: PrinterEntity): Long {
        return printerDao.insertPrinter(printer)
    }

    suspend fun updatePrinter(printer: PrinterEntity) {
        printerDao.updatePrinter(printer)
    }

    suspend fun deletePrinter(printer: PrinterEntity) {
        printerDao.deletePrinter(printer)
    }

    suspend fun deletePrinterById(id: Int) {
        printerDao.deletePrinterById(id)
    }

    suspend fun insertDiagnosticLog(log: DiagnosticLogEntity) {
        printerDao.insertDiagnosticLog(log)
    }

    suspend fun deleteLogsForPrinter(printerId: Int) {
        printerDao.deleteLogsForPrinter(printerId)
    }

    suspend fun clearAllDiagnosticLogs() {
        printerDao.clearAllDiagnosticLogs()
    }

    /**
     * Real-time port connectivity tests.
     * Attempts to open a socket to the specific port on a given IP.
     */
    suspend fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 1000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Pings the IP address and calculates standard latency.
     * Uses InetAddress.isReachable check, and falls back to socket port availability.
     */
    suspend fun pingIp(ip: String, timeoutMs: Int = 1200): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val address = InetAddress.getByName(ip)
            val reachable = address.isReachable(timeoutMs)
            val latency = (System.currentTimeMillis() - startTime).toInt()
            if (reachable) {
                Pair(true, latency)
            } else {
                // If ICMP ping is blocked (common on Android), check if standard HTTP port or Raw printing port is open
                val fallbackCheck = isPortOpen(ip, 80, 500) || isPortOpen(ip, 9100, 500)
                val fallbackLatency = (System.currentTimeMillis() - startTime).toInt()
                if (fallbackCheck) Pair(true, fallbackLatency) else Pair(false, -1)
            }
        } catch (e: Exception) {
            Pair(false, -1)
        }
    }

    /**
     * Deep diagnosis scan for a printer.
     */
    suspend fun runDiagnostics(printer: PrinterEntity): PrinterEntity {
        val pingResult = pingIp(printer.ipAddress)
        val lat = pingResult.second

        val p9100 = isPortOpen(printer.ipAddress, 9100)
        val p631 = isPortOpen(printer.ipAddress, 631)
        val p80 = isPortOpen(printer.ipAddress, 80)
        val p515 = isPortOpen(printer.ipAddress, 515)

        val anyOnline = pingResult.first || p9100 || p631 || p80 || p515

        val updated = printer.copy(
            isOnline = anyOnline,
            latencyMs = if (anyOnline) (if (lat > 0) lat else 45) else -1,
            lastChecked = System.currentTimeMillis(),
            port9100Open = p9100,
            port631Open = p631,
            port80Open = p80,
            port515Open = p515
        )

        updatePrinter(updated)
        return updated
    }

    /**
     * Queries Gemini AI to provide actionable, structured diagnosis guides based on scanned parameters and symptoms.
     */
    suspend fun queryGeminiDiagnosis(
        printer: PrinterEntity,
        symptom: String,
        customKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = if (!customKey.isNullOrBlank()) customKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Configuration Error: Please enter a valid Gemini API Key in the AI Studio secrets panel or configure a custom API Key in settings to unlock AI diagnostics. Currently, your simulated diagnostics can operate on offline guidelines."
        }

        val portStatus = listOfNotNull(
            if (printer.port9100Open) "Port 9100 (HP JetDirect/RAW): OPEN" else "Port 9100 (HP JetDirect/RAW): CLOSED",
            if (printer.port631Open) "Port 631 (IPP Printer Sharing): OPEN" else "Port 631 (IPP Printer Sharing): CLOSED",
            if (printer.port80Open) "Port 80 (HTTP Admin Web-Console): OPEN" else "Port 80 (HTTP Admin Web-Console): CLOSED",
            if (printer.port515Open) "Port 515 (LPR/LPD Queue): OPEN" else "Port 515 (LPR/LPD Queue): CLOSED"
        ).joinToString("\n")

        val stateDescription = if (printer.isOnline) {
            "ONLINE with latency ${printer.latencyMs}ms. Port state:\n$portStatus"
        } else {
            "OFFLINE (Unreachable/Timed out on ICMP and common ports 9100, 631, 80, 515)"
        }

        val prompt = """
            You are PrinterDost, an expert Network Printer Troubleshooting Engineer. 
            Provide a deep technical diagnosis and distinct troubleshooting manual for the printer described below.

            [PRINTER INFORMATION]
            Brand/Manufacturer: ${printer.brand}
            Location: ${printer.location}
            Current IP: ${printer.ipAddress}
            Connectivity State: $stateDescription

            [USER INQUIRY / SYMPTOM]
            Reported Issue: $symptom

            Provide a clean response under the following exact headings:
            ### 1. Primary Diagnosis Summary
            Analyze why this symptom occurs given the printer's specific network port statuses. For example, explain why port 9100/631 configurations matter, or if the printer being offline suggests power or DHCP problems. Do not be generic; combine the brand (${printer.brand}) and network port results in your reasoning.

            ### 2. Actionable Remediation Steps
            Provide a highly detailed numbered list of troubleshooting actions for this specific brand (${printer.brand}). Keep steps sequentially clear and distinct, addressing spooler config, network resets, or hardware checks as appropriate.

            ### 3. Web Console and Admin Panel Advice
            Explain whether they should use the Admin Console Link (http://${printer.ipAddress} on Port 80, if open) or physical menu buttons to apply changes, and state the exact setting page to inspect for this brand's typical dashboard (e.g. Sync Settings, Network configuration, AirPrint, etc.).
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "You are PrinterDost, a professional network printer support assistant. Give concise, highly scientific, structured support with clean bold typography and zero conversational filler.")))
        )

        try {
            val response = RetrofitClient.geminiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Received empty response from PrinterDost Assistant. Please verify network."
        } catch (e: Exception) {
            "AIS Diagnostics Link Fail: Unable to retrieve live AI guidance. Network Error: ${e.message}. Please check if you have connected your device to the Internet or if your GEMINI_API_KEY is active in the secrets."
        }
    }

    /**
     * Executes custom content synthesis using Gemini AI for USB diagnostics or general prompts.
     */
    suspend fun queryGeminiDiagnosisCustom(prompt: String, customKey: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = if (!customKey.isNullOrBlank()) customKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Configuration Error: Please enter a valid Gemini API Key in the AI Studio secrets panel to unlock AI diagnostics."
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "You are PrinterDost, a professional USB hardware and network printer support assistant. Give concise, highly scientific, structured support with clean bold typography and zero conversational filler.")))
        )

        try {
            val response = RetrofitClient.geminiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Received empty response from PrinterDost Assistant."
        } catch (e: Exception) {
            "AIS Diagnostics Link Fail: Unable to retrieve live AI guidance. Network Error: ${e.message}."
        }
    }
}
