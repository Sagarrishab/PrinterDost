package com.sds.printerdost.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sds.printerdost.data.local.AppDatabase
import com.sds.printerdost.data.local.PrinterEntity
import com.sds.printerdost.data.local.DiagnosticLogEntity
import com.sds.printerdost.data.repository.PrinterRepository
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

    private val nsdHelper = PrinterNsdHelper(
        context = application,
        repository = repository,
        scope = viewModelScope,
        onLogAdded = { log ->
            val logs = scannerLogState.value.toMutableList()
            logs.add(log)
            scannerLogState.value = logs
        }
    )

    init {
        // Automatically find and populate local subnet query structure
        viewModelScope.launch {
            val subnetPrefix = getLocalSubnetPrefix()
            subnetQuery.value = "${subnetPrefix}x"
            // Proactively start background WiFi mDNS printer auto-discovery
            nsdHelper.startDiscovery()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            nsdHelper.stopDiscovery()
        } catch (e: Exception) {
            e.printStackTrace()
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
     * Triggers active mDNS reload and starts concurrent subnet port sweeping in parallel.
     */
    fun triggerNetworkDiscovery() {
        try {
            nsdHelper.forceTriggerReload()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scanSubnet()
    }

    /**
     * Scans the subnet concurrently to discover network printers.
     * Searches the specified IP or full 1..254 subnet range in parallel for open printer ports.
     */
    fun scanSubnet() {
        viewModelScope.launch {
            if (isScanning.value) return@launch
            val scanLogs = mutableListOf<String>()
            try {
                isScanning.value = true
                scanProgress.value = 0f
                scanLogs.add("Initializing subnet scan...")
                scannerLogState.value = scanLogs

                val inputQuery = subnetQuery.value.trim()
                val isSingleIp = inputQuery.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))

                val ipsToScan = if (isSingleIp) {
                    scanLogs.add("Identified single IP target. Scanning $inputQuery directly...")
                    listOf(inputQuery)
                } else {
                    var prefix = inputQuery
                    if (prefix.endsWith("x")) {
                        prefix = prefix.substring(0, prefix.length - 1)
                    }
                    if (!prefix.endsWith(".")) {
                        prefix = "$prefix."
                    }
                    scanLogs.add("Sweeping full subnet range ${prefix}1 to ${prefix}254 concurrently...")
                    (1..254).map { "$prefix$it" }
                }

                scannerLogState.value = scanLogs.toList()
                val totalSteps = ipsToScan.size
                var finishedCount = 0

                scanLogs.add("Concurrently querying ports [9100, 631, 80, 515] and pinging for $totalSteps IP target(s)...")
                scannerLogState.value = scanLogs.toList()

                val discoveredPrinters = mutableListOf<PrinterEntity>()
                withContext(Dispatchers.IO) {
                    ipsToScan.chunked(30).forEach { chunk ->
                        val deferreds = chunk.map { ip ->
                            async {
                                try {
                                    val timeout = if (isSingleIp) 1500 else 600
                                    
                                    // Check Printer communication ports
                                    val p9100 = repository.isPortOpen(ip, 9100, timeout)
                                    val p631 = repository.isPortOpen(ip, 631, timeout)
                                    val p80 = repository.isPortOpen(ip, 80, timeout)
                                    val p515 = repository.isPortOpen(ip, 515, timeout)
                                    
                                    // Also verify with general ping diagnostics
                                    val pingResult = repository.pingIp(ip, timeout)
                                    
                                    synchronized(this@PrinterViewModel) {
                                        finishedCount++
                                        scanProgress.value = finishedCount.toFloat() / totalSteps
                                    }

                                    val isDiscovered = p9100 || p631 || p80 || p515 || pingResult.first

                                    if (isDiscovered) {
                                        val resolvedBrand = when {
                                            p9100 && p80 -> "HP"
                                            p631 && p80 -> "Canon"
                                            p515 -> "Brother"
                                            p80 -> "Epson"
                                            else -> "Generic"
                                        }

                                        val lastOctet = ip.substringAfterLast('.')

                                        PrinterEntity(
                                            name = "$resolvedBrand Office $lastOctet",
                                            ipAddress = ip,
                                            brand = resolvedBrand,
                                            location = "Auto-Discovered ($ip)",
                                            isOnline = true,
                                            latencyMs = if (pingResult.first && pingResult.second > 0) pingResult.second else 35,
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
                        }
                        discoveredPrinters.addAll(deferreds.awaitAll().filterNotNull())
                    }
                }

                // Insert discovered printers into Room database
                if (discoveredPrinters.isEmpty()) {
                    if (isSingleIp) {
                        scanLogs.add("No printer listening on ports [9100, 631, 80, 515] at IP $inputQuery.")
                        
                        // FALLBACK: Auto-declare anyway so the user doesn't get "nothing" when searching a specific IP!
                        try {
                            val fallbackPrinter = PrinterEntity(
                                name = "Unverified Printer $inputQuery",
                                ipAddress = inputQuery,
                                brand = "Generic",
                                location = "Direct IP Target ($inputQuery)",
                                isOnline = false,
                                latencyMs = -1,
                                lastChecked = System.currentTimeMillis()
                            )
                            repository.insertPrinter(fallbackPrinter)
                            scanLogs.add("Fallback: Declared $inputQuery in local database for manual troubleshooting.")
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            scanLogs.add("Fallback declaration failed: ${t.localizedMessage}")
                        }
                    } else {
                        scanLogs.add("No new physical printers discovered on subnet with range 1..254. (Ensure devices are on same LAN)")
                    }
                } else {
                    scanLogs.add("Discovered ${discoveredPrinters.size} active network printer(s)! Syncing with SQLite...")
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
                scanLogs.add("Scan failed with error: ${t.localizedMessage}")
                scannerLogState.value = scanLogs.toList()
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
