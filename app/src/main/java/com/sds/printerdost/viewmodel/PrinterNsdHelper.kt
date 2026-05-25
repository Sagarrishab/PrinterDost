package com.sds.printerdost.viewmodel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.sds.printerdost.data.local.PrinterEntity
import com.sds.printerdost.data.repository.PrinterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PrinterNsdHelper(
    private val context: Context,
    private val repository: PrinterRepository,
    private val scope: CoroutineScope,
    private val onLogAdded: (String) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val activeListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private var isRunning = false

    private val serviceTypes = listOf(
        "_ipp._tcp",
        "_ipps._tcp",
        "_printer._tcp",
        "_pdl-imageset._tcp"
    )

    fun startDiscovery() {
        if (isRunning) return
        isRunning = true
        onLogAdded("Starting mDNS printer auto-discovery on local WiFi network...")

        serviceTypes.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e("NSD", "Start discovery failed: $serviceType Code: $errorCode")
                    try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e("NSD", "Stop discovery failed: $serviceType Code: $errorCode")
                    try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
                }

                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.d("NSD", "Discovery started: $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.d("NSD", "Discovery stopped: $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo == null) return
                    Log.d("NSD", "Service found: ${serviceInfo.serviceName} of type ${serviceInfo.serviceType}")
                    
                    try {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.e("NSD", "Resolve failed: code $errorCode")
                            }

                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                if (resolvedInfo == null) return
                                val host = resolvedInfo.host ?: return
                                val ip = host.hostAddress ?: return
                                if (ip == "127.0.0.1" || ip == "0.0.0.0" || ip.startsWith("::")) return
                                
                                val port = resolvedInfo.port
                                val serviceName = resolvedInfo.serviceName ?: "Discovered Printer"
                                val serviceTypeResolved = resolvedInfo.serviceType ?: ""
                                
                                val brand = when {
                                    serviceName.contains("HP", ignoreCase = true) || serviceTypeResolved.contains("hp", ignoreCase = true) -> "HP"
                                    serviceName.contains("Canon", ignoreCase = true) -> "Canon"
                                    serviceName.contains("Epson", ignoreCase = true) -> "Epson"
                                    serviceName.contains("Brother", ignoreCase = true) -> "Brother"
                                    else -> "Generic"
                                }

                                Log.d("NSD", "mDNS Printer Resolved: $serviceName at $ip:$port")
                                onLogAdded("mDNS Auto-Discovered physical printer: \"$serviceName\" in subnet ($ip:$port)")
                                
                                // Auto-populate this printer asynchronously into local Room database
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        // Sweep printers count & duplicate check
                                        val existingList = repository.allPrinters.firstOrNull() ?: emptyList()
                                        val duplicate = existingList.firstOrNull { it.ipAddress == ip }
                                        
                                        val entity = if (duplicate != null) {
                                            duplicate.copy(
                                                name = serviceName,
                                                brand = brand,
                                                isOnline = true,
                                                lastChecked = System.currentTimeMillis(),
                                                port9100Open = duplicate.port9100Open || port == 9100,
                                                port631Open = duplicate.port631Open || port == 631 || serviceTypeResolved.contains("ipp"),
                                                port80Open = duplicate.port80Open || port == 80,
                                                port515Open = duplicate.port515Open || port == 515
                                            )
                                        } else {
                                            PrinterEntity(
                                                name = serviceName,
                                                ipAddress = ip,
                                                brand = brand,
                                                location = "Auto-Discovered ($ip)",
                                                isOnline = true,
                                                latencyMs = 25,
                                                lastChecked = System.currentTimeMillis(),
                                                port9100Open = port == 9100,
                                                port631Open = port == 631 || serviceTypeResolved.contains("ipp"),
                                                port80Open = port == 80,
                                                port515Open = port == 515
                                            )
                                        }
                                        repository.insertPrinter(entity)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    Log.d("NSD", "Service lost: ${serviceInfo?.serviceName}")
                }
            }

            activeListeners[serviceType] = listener
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopDiscovery() {
        if (!isRunning) return
        isRunning = false
        activeListeners.forEach { (type, listener) ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeListeners.clear()
        Log.d("NSD", "mDNS Discovery stopped.")
    }

    fun forceTriggerReload() {
        try {
            stopDiscovery()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        startDiscovery()
    }
}
