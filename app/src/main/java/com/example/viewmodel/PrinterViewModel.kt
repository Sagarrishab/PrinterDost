package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PrinterEntity
import com.example.data.local.DiagnosticLogEntity
import com.example.data.repository.PrinterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.util.Collections

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val printerDao = AppDatabase.getDatabase(application).printerDao()
    private val repository = PrinterRepository(printerDao)

    // Reactive streams from SQLite database
    val printers: StateFlow<List<PrinterEntity>> = repository.allPrinters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diagnosticLogs: StateFlow<List<DiagnosticLogEntity>> = repository.allDiagnosticLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    val selectedPrinter = MutableStateFlow<PrinterEntity?>(null)
    val isScanning = MutableStateFlow(false)
    val scanProgress = MutableStateFlow(0f)
    val scannerLogState = MutableStateFlow<List<String>>(emptyList())
    
    val isTestingPrinterId = MutableStateFlow<Int?>(null) // Printer ID currently performing active ping/port diagnostics
    val isAIThinking = MutableStateFlow(false)
    val activeSymptom = MutableStateFlow("Select Printer Error Symptom")
    val aiDiagnosisResult = MutableStateFlow<String?>(null)

    // Config Input Controls
    val subnetQuery = MutableStateFlow("192.168.1.x")
    val showAddPrinterDialog = MutableStateFlow(false)
    val showEditPrinterDialog = MutableStateFlow(false)
    val webViewUrl = MutableStateFlow<String?>(null) // For admin console direct access WebView

    init {
        // Automatically find and populate local subnet query structure
        viewModelScope.launch {
            val subnetPrefix = getLocalSubnetPrefix()
            subnetQuery.value = "${subnetPrefix}x"
        }
    }

    /**
     * Resolves local network IPv4 ranges to tailor the subnet query input
     */
    private suspend fun getLocalSubnetPrefix(): String = withContext(Dispatchers.IO) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@withContext "192.168.1."
            for (intf in Collections.list(interfaces)) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        if (sAddr != null) {
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4 && (sAddr.startsWith("192.168.") || sAddr.startsWith("10."))) {
                                val lastDot = sAddr.lastIndexOf('.')
                                if (lastDot != -1) {
                                    return@withContext sAddr.substring(0, lastDot + 1)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Safety ignore
        }
        "192.168.1."
    }

    /**
     * Trigger real-time diagnostic connectivity checks for outer devices concurrently.
     */
    fun refreshAllPrinters() {
        viewModelScope.launch {
            try {
                printers.value.forEach { printer ->
                    runSingleDiagnostics(printer)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun addPrinter(name: String, ipAddress: String, brand: String, location: String) {
        viewModelScope.launch {
            try {
                val newPrinter = PrinterEntity(
                    name = name.ifEmpty { "New Printer" },
                    ipAddress = ipAddress.ifEmpty { "192.168.1.100" },
                    brand = brand.ifEmpty { "Generic" },
                    location = location.ifEmpty { "Default Workspace" }
                )
                repository.insertPrinter(newPrinter)
                showAddPrinterDialog.value = false
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun updatePrinter(printer: PrinterEntity) {
        viewModelScope.launch {
            try {
                repository.updatePrinter(printer)
                showEditPrinterDialog.value = false
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun deletePrinter(printer: PrinterEntity) {
        viewModelScope.launch {
            try {
                repository.deletePrinter(printer)
                if (selectedPrinter.value?.id == printer.id) {
                    selectedPrinter.value = null
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun selectPrinter(printer: PrinterEntity?) {
        selectedPrinter.value = printer
        aiDiagnosisResult.value = null
        activeSymptom.value = "Select Printer Error Symptom"
    }

    /**
     * Executes individual target active diagnostics checking ICMP & specific ports
     */
    fun runSingleDiagnostics(printer: PrinterEntity) {
        viewModelScope.launch {
            try {
                isTestingPrinterId.value = printer.id
                val result = repository.runDiagnostics(printer)
                if (selectedPrinter.value?.id == printer.id) {
                    selectedPrinter.value = result
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                isTestingPrinterId.value = null
            }
        }
    }

    /**
     * Scans the subnet concurrently to discover network printers.
     * Searches a range of 50 IPs in parallel for open printer ports.
     */
    fun scanSubnet() {
        viewModelScope.launch {
            if (isScanning.value) return@launch
            try {
                isScanning.value = true
                scanProgress.value = 0f
                val scanLogs = mutableListOf<String>()
                scanLogs.add("Initializing subnet scan...")
                scannerLogState.value = scanLogs

                var prefix = subnetQuery.value.trim()
                if (prefix.endsWith("x")) {
                    prefix = prefix.substring(0, prefix.length - 1)
                }
                if (!prefix.endsWith(".")) {
                    prefix = "$prefix."
                }

                // Let's sweep a logical pool of IPs from .100 to .150 (common printing DHCP bounds)
                val startIp = 100
                val endIp = 150
                val totalSteps = endIp - startIp + 1
                var finishedCount = 0

                scanLogs.add("Concurrently querying ports [9100, 631, 80] from ${prefix}$startIp to ${prefix}$endIp")
                scannerLogState.value = scanLogs.toList()

                val discoveredPrinters = withContext(Dispatchers.IO) {
                    (startIp..endIp).map { lastOctet ->
                        async {
                            try {
                                val ip = "$prefix$lastOctet"
                                
                                // Check Printer communication ports
                                val p9100 = repository.isPortOpen(ip, 9100, 300)
                                val p631 = repository.isPortOpen(ip, 631, 300)
                                val p80 = repository.isPortOpen(ip, 80, 300)
                                val p515 = repository.isPortOpen(ip, 515, 300)

                                synchronized(this@PrinterViewModel) {
                                    finishedCount++
                                    scanProgress.value = finishedCount.toFloat() / totalSteps
                                }

                                if (p9100 || p631 || p80 || p515) {
                                    // Automatically try to resolve Brand and latency
                                    val latencyCheck = repository.pingIp(ip, 400)
                                    
                                    val resolvedBrand = when {
                                        p9100 && p80 -> "HP"
                                        p631 && p80 -> "Canon"
                                        p515 -> "Brother"
                                        p80 -> "Epson"
                                        else -> "Generic"
                                    }

                                    PrinterEntity(
                                        name = "$resolvedBrand Office ${lastOctet}",
                                        ipAddress = ip,
                                        brand = resolvedBrand,
                                        location = "Auto-Discovered ($ip)",
                                        isOnline = true,
                                        latencyMs = if (latencyCheck.first && latencyCheck.second > 0) latencyCheck.second else 35,
                                        lastChecked = System.currentTimeMillis(),
                                        port9100Open = p9100,
                                        port631Open = p631,
                                        port80Open = p80,
                                        port515Open = p515
                                    )
                                } else {
                                    null
                                }
                            } catch (t: Throwable) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                // Insert discovered printers into Room database
                if (discoveredPrinters.isEmpty()) {
                    scanLogs.add("No new physical printers discovered on subnet $prefix. (Ensure devices are on same LAN)")
                } else {
                    scanLogs.add("Discovered ${discoveredPrinters.size} active network printers! Syncing with SQLite...")
                    discoveredPrinters.forEach { p ->
                        try {
                            repository.insertPrinter(p)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                }

                scanProgress.value = 1.0f
                scannerLogState.value = scanLogs.toList()
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                isScanning.value = false
            }
        }
    }

    /**
     * Calls Gemini assistant and logs diagnostic history reactively
     */
    fun performAiDiagnosis(symptom: String) {
        val printer = selectedPrinter.value ?: return
        viewModelScope.launch {
            try {
                isAIThinking.value = true
                aiDiagnosisResult.value = null
                
                val result = repository.queryGeminiDiagnosis(printer, symptom)
                aiDiagnosisResult.value = result

                // Save this diagnosis to historic diagnostic logs database
                val diagLog = DiagnosticLogEntity(
                    printerId = printer.id,
                    printerName = printer.name,
                    ipAddress = printer.ipAddress,
                    timestamp = System.currentTimeMillis(),
                    isOnline = printer.isOnline,
                    latencyMs = printer.latencyMs,
                    port9100Open = printer.port9100Open,
                    port631Open = printer.port631Open,
                    port80Open = printer.port80Open,
                    port515Open = printer.port515Open,
                    selectedSymptom = symptom,
                    diagnosisResult = result
                )
                repository.insertDiagnosticLog(diagLog)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                isAIThinking.value = false
            }
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllDiagnosticLogs()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * Trigger direct administration link in webView context
     */
    fun openAdminConsole(ip: String) {
        val url = if (ip.startsWith("http://") || ip.startsWith("https://")) {
            ip
        } else {
            "http://$ip"
        }
        webViewUrl.value = url
    }

    fun closeAdminConsole() {
        webViewUrl.value = null
    }
}
