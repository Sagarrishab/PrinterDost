package com.sds.printerdost.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sds.printerdost.data.local.PrinterEntity
import com.sds.printerdost.data.local.DiagnosticLogEntity
import com.sds.printerdost.ui.components.PrinterWebView
import com.sds.printerdost.ui.theme.*
import com.sds.printerdost.viewmodel.PrinterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PrinterViewModel,
    modifier: Modifier = Modifier
) {
    val printers by viewModel.printers.collectAsStateWithLifecycle()
    val logs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()
    val selectedPrinter by viewModel.selectedPrinter.collectAsStateWithLifecycle()
    
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scannerLogs by viewModel.scannerLogState.collectAsStateWithLifecycle()
    val isTestingPrinterId by viewModel.isTestingPrinterId.collectAsStateWithLifecycle()
    
    val isAIThinking by viewModel.isAIThinking.collectAsStateWithLifecycle()
    val activeSymptom by viewModel.activeSymptom.collectAsStateWithLifecycle()
    val aiDiagnosisResult by viewModel.aiDiagnosisResult.collectAsStateWithLifecycle()
    
    val subnetQuery by viewModel.subnetQuery.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddPrinterDialog.collectAsStateWithLifecycle()
    val showEditDialog by viewModel.showEditPrinterDialog.collectAsStateWithLifecycle()
    val webViewUrl by viewModel.webViewUrl.collectAsStateWithLifecycle()

    val usbPrinterConnected by viewModel.usbPrinterConnected.collectAsStateWithLifecycle()
    val usbDeviceInfo by viewModel.usbDeviceInfo.collectAsStateWithLifecycle()
    val usbDeviceNameState by viewModel.usbDeviceNameState.collectAsStateWithLifecycle()
    val isUsbTroubleshooting by viewModel.isUsbTroubleshooting.collectAsStateWithLifecycle()
    val usbTroubleshootLogs by viewModel.usbTroubleshootLogs.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Printers, 1: AI Diagnostics, 2: Scan Logs, 3: USB
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // If a WebView is active, overlay it full-screen
    if (webViewUrl != null) {
        PrinterWebView(
            url = webViewUrl!!,
            onClose = { viewModel.closeAdminConsole() }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "PD",
                                    color = MidnightBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "PrinterDost",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Network Assistant",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberTeal
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.testTag("api_key_settings_button")
                    ) {
                        val useCustomKey by viewModel.useCustomApiKey.collectAsStateWithLifecycle()
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Gemini API Key Settings",
                            tint = if (useCustomKey) CyberTeal else TextMuted
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshAllPrinters() },
                        modifier = Modifier.testTag("refresh_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh All Connectivity",
                            tint = CyberTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightBlue,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large physical prominent button to Search network directly
                ExtendedFloatingActionButton(
                    onClick = { viewModel.triggerNetworkDiscovery() },
                    containerColor = CyberBlue,
                    contentColor = MidnightBlue,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("scan_network_fab")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = MidnightBlue,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Searching...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search Network")
                            Text("Search Network", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Add manual printer FAB
                FloatingActionButton(
                    onClick = { viewModel.showAddPrinterDialog.value = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MidnightBlue,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_printer_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Printer")
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MidnightBlue)
                .drawBehind {
                    // Modern tech grid outline
                    val stepPx = maxOf(20, 40.dp.toPx().toInt())
                    val widthInt = size.width.toInt()
                    val heightInt = size.height.toInt()
                    if (widthInt > 0 && heightInt > 0) {
                        for (x in 0..widthInt step stepPx) {
                            drawLine(
                                color = GridLine,
                                start = Offset(x.toFloat(), 0f),
                                end = Offset(x.toFloat(), size.height),
                                strokeWidth = 1f
                            )
                        }
                        for (y in 0..heightInt step stepPx) {
                            drawLine(
                                color = GridLine,
                                start = Offset(0f, y.toFloat()),
                                end = Offset(size.width, y.toFloat()),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
        ) {
            val isWide = maxWidth > 620.dp

            if (isWide) {
                // Dual pane desktop / Tablet design
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Left Pane: Printers Discovery & Config (45% weight)
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight()
                    ) {
                        SubnetScannerCard(
                            subnetQuery = subnetQuery,
                            onQueryChange = { viewModel.subnetQuery.value = it },
                            isScanning = isScanning,
                            scanProgress = scanProgress,
                            onScanClick = { viewModel.scanSubnet() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Managed Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        PrintersList(
                            printers = printers,
                            selectedPrinter = selectedPrinter,
                            isTestingPrinterId = isTestingPrinterId,
                            onPrinterSelect = { viewModel.selectPrinter(it) },
                            onTestClick = { viewModel.runSingleDiagnostics(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Pane: Deep Diagnostics & AI Troubleshooting (52% weight)
                    Card(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepSlate.copy(alpha = 0.85f)),
                        border = BorderStroke(1.dp, LightSlate)
                    ) {
                        if (selectedPrinter != null) {
                            PrinterDetailsView(
                                printer = selectedPrinter!!,
                                activeSymptom = activeSymptom,
                                onSymptomSelect = { viewModel.activeSymptom.value = it },
                                isAIThinking = isAIThinking,
                                aiDiagnosisResult = aiDiagnosisResult,
                                logs = logs.filter { it.printerId == selectedPrinter!!.id },
                                onConsultAiClick = { viewModel.performAiDiagnosis(activeSymptom) },
                                onDeleteClick = { viewModel.deletePrinter(selectedPrinter!!) },
                                onOpenAdminClick = { viewModel.openAdminConsole(selectedPrinter!!.ipAddress) },
                                onSingleTest = { viewModel.runSingleDiagnostics(selectedPrinter!!) }
                            )
                        } else {
                            EmptyDetailsView()
                        }
                    }
                }
            } else {
                // Mobile single page scrollable layout with bottom tabs
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp) // Leave safety padding for the fake bottom app bar
                ) {
                    SubnetScannerCard(
                        subnetQuery = subnetQuery,
                        onQueryChange = { viewModel.subnetQuery.value = it },
                        isScanning = isScanning,
                        scanProgress = scanProgress,
                        onScanClick = { viewModel.scanSubnet() },
                        modifier = Modifier.padding(16.dp)
                    )

                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = MidnightBlue,
                        contentColor = CyberBlue,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                color = CyberBlue
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Printers", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("AI Assistant", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("History", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = activeTab == 3,
                            onClick = { activeTab = 3 },
                            text = { Text("USB Connect", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        when (activeTab) {
                            0 -> {
                                PrintersList(
                                    printers = printers,
                                    selectedPrinter = selectedPrinter,
                                    isTestingPrinterId = isTestingPrinterId,
                                    onPrinterSelect = {
                                        viewModel.selectPrinter(it)
                                        activeTab = 1 // Auto-switch to diagnostic tab on touch
                                    },
                                    onTestClick = { viewModel.runSingleDiagnostics(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            1 -> {
                                if (selectedPrinter != null) {
                                    Card(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = DeepSlate),
                                        border = BorderStroke(1.dp, LightSlate)
                                    ) {
                                        PrinterDetailsView(
                                            printer = selectedPrinter!!,
                                            activeSymptom = activeSymptom,
                                            onSymptomSelect = { viewModel.activeSymptom.value = it },
                                            isAIThinking = isAIThinking,
                                            aiDiagnosisResult = aiDiagnosisResult,
                                            logs = logs.filter { it.printerId == selectedPrinter!!.id },
                                            onConsultAiClick = { viewModel.performAiDiagnosis(activeSymptom) },
                                            onDeleteClick = { viewModel.deletePrinter(selectedPrinter!!) },
                                            onOpenAdminClick = { viewModel.openAdminConsole(selectedPrinter!!.ipAddress) },
                                            onSingleTest = { viewModel.runSingleDiagnostics(selectedPrinter!!) }
                                        )
                                    }
                                } else {
                                    EmptyDetailsView()
                                }
                            }
                            2 -> {
                                DiagnosticLogsView(
                                    logs = logs,
                                    onClearAll = { viewModel.clearLogHistory() }
                                )
                            }
                            3 -> {
                                UsbTroubleshootView(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- All Manual Interaction Dialogs ---

        if (showAddDialog) {
            AddPrinterDialog(
                onDismiss = { viewModel.showAddPrinterDialog.value = false },
                onAdd = { name, ip, brand, loc ->
                    viewModel.addPrinter(name, ip, brand, loc)
                }
            )
        }

        if (showApiKeyDialog) {
            GeminiApiKeyDialog(
                onDismiss = { showApiKeyDialog = false },
                viewModel = viewModel
            )
        }
    }
}

// --- Component UI Blocks ---

@Composable
fun SubnetScannerCard(
    subnetQuery: String,
    onQueryChange: (String) -> Unit,
    isScanning: Boolean,
    scanProgress: Float,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, LightSlate)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Subnet Sweeper & Discovery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Triggers concurrent port query on IP bounds to fetch printers dynamically",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = subnetQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Network Target") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = LightSlate,
                        focusedLabelColor = CyberBlue,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subnet_input_field"),
                    maxLines = 1,
                    enabled = !isScanning
                )

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) LightSlate else CyberBlue,
                        contentColor = MidnightBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("subnet_scan_button"),
                    enabled = !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = MidnightBlue,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Scan")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { scanProgress },
                        color = CyberTeal,
                        trackColor = LightSlate,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${(scanProgress * 100).toInt()}%",
                        color = CyberTeal,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PrintersList(
    printers: List<PrinterEntity>,
    selectedPrinter: PrinterEntity?,
    isTestingPrinterId: Int?,
    onPrinterSelect: (PrinterEntity) -> Unit,
    onTestClick: (PrinterEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (printers.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Empty",
                    tint = TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No printers configured",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    "Click + to declare a manual IP, perform a Subnet Scan to find active devices, or hit the refresh icon on top to recheck all states.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(printers, key = { it.id }) { printer ->
                val isSelected = selectedPrinter?.id == printer.id
                val borderCol = if (isSelected) CyberBlue else LightSlate
                val bgCol = if (isSelected) LightSlate.copy(alpha = 0.5f) else DeepSlate.copy(alpha = 0.6f)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPrinterSelect(printer) }
                        .testTag("printer_card_${printer.id}"),
                    colors = CardDefaults.cardColors(containerColor = bgCol),
                    border = BorderStroke(1.dp, borderCol),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand and State Indicators
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (printer.isOnline) SuccessGreen.copy(alpha = 0.15f) else ErrorCoral.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = printer.brand.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = if (printer.isOnline) SuccessGreen else ErrorCoral,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Small LED circle
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(color = if (printer.isOnline) SuccessGreen else ErrorCoral)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = printer.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = printer.ipAddress,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            Text(
                                text = "Loc: ${printer.location}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        // Run diagnostics action
                        Column(horizontalAlignment = Alignment.End) {
                            if (isTestingPrinterId == printer.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = CyberTeal,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { onTestClick(printer) },
                                    modifier = Modifier.testTag("test_diag_btn_${printer.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Test connection",
                                        tint = CyberTeal
                                    )
                                }
                            }

                            if (printer.isOnline && printer.latencyMs > 0) {
                                Text(
                                    text = "${printer.latencyMs}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            } else {
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorCoral,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrinterDetailsView(
    printer: PrinterEntity,
    activeSymptom: String,
    onSymptomSelect: (String) -> Unit,
    isAIThinking: Boolean,
    aiDiagnosisResult: String?,
    logs: List<DiagnosticLogEntity>,
    onConsultAiClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenAdminClick: () -> Unit,
    onSingleTest: () -> Unit
) {
    var expandedDropdown by remember { mutableStateOf(false) }
    val symptoms = listOf(
        "Printer Offline or Unreachable error",
        "Print jobs stuck in Windows Spooler Queue",
        "Paper Jam / Feed failures",
        "Incorrect IP address or DHCP lease conflict",
        "Faded prints, streaks or empty halftones",
        "Blinking orange / amber alert light on printer"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Detail Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = printer.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${printer.brand} • ${printer.ipAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = CyberTeal
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Printer",
                    tint = ErrorCoral
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Quick Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSingleTest,
                border = BorderStroke(1.dp, CyberBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberBlue),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan ports")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Port Scan")
            }

            Button(
                onClick = onOpenAdminClick,
                enabled = printer.isOnline,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberTeal,
                    contentColor = MidnightBlue
                ),
                modifier = Modifier.weight(1.5f)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "HTTP")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Admin Console (Port 80)")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Port Status Matrix
        Text(
            "TCP Printer Port Map",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "Printers communicate via standard listening sockets. Scanned logs:",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PortIndicator(port = 9100, label = "JetDirect RAW", open = printer.port9100Open, modifier = Modifier.weight(1f))
            PortIndicator(port = 631, label = "IPP Sharing", open = printer.port631Open, modifier = Modifier.weight(1f))
            PortIndicator(port = 80, label = "HTTP Admin", open = printer.port80Open, modifier = Modifier.weight(1f))
            PortIndicator(port = 515, label = "LPD/LPR", open = printer.port515Open, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AI Diagnostician Launcher
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LightSlate.copy(alpha = 0.25f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GridLine)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI Diagnostician Suite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue
                )
                Text(
                    "Passes port scan configuration, brand attributes, and selected system logs to PrinterDost Agent. Select active symptom:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("symptom_dropdown_trigger"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, LightSlate)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(activeSymptom, color = TextPrimary)
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Dropdown", tint = CyberBlue)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSlate)
                    ) {
                        symptoms.forEach { symptom ->
                            DropdownMenuItem(
                                text = { Text(symptom, color = TextPrimary) },
                                onClick = {
                                    onSymptomSelect(symptom)
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onConsultAiClick,
                    enabled = activeSymptom in symptoms && !isAIThinking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberBlue,
                        contentColor = MidnightBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("consult_ai_button")
                ) {
                    if (isAIThinking) {
                        CircularProgressIndicator(
                            color = MidnightBlue,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Diagnose with PrinterDost AI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Expanded diagnosis result
        if (aiDiagnosisResult != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_diagnosis_result_card"),
                shape = RoundedCornerShape(16.dp),
                color = MidnightBlue.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, CyberBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Brain Info", tint = CyberBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI Diagnostic Formulation",
                            fontWeight = FontWeight.Bold,
                            color = CyberBlue,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = aiDiagnosisResult,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PortIndicator(
    port: Int,
    label: String,
    open: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MidnightBlue,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (open) SuccessGreen else LightSlate)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$port",
                color = if (open) SuccessGreen else TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = TextMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (open) "OPEN" else "CLOSED",
                color = if (open) SuccessGreen else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyDetailsView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Select",
                tint = TextMuted,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "No target selected",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                "Touch an active printer card from the left panel to execute port diagnostics, browse administration configs, or query Gemini troubleshoot blueprints.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun DiagnosticLogsView(
    logs: List<DiagnosticLogEntity>,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Diagnostic Timeline Runs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear Timeline", color = ErrorCoral)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No historic logs captured. Perform an AI diagnosis to log history.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DeepSlate),
                        border = BorderStroke(1.dp, LightSlate)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.printerName,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = "IP Target: ${log.ipAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = CyberTeal
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Reported Symptom: ${log.selectedSymptom}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberBlue
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = log.diagnosisResult,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 4,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPrinterDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("HP") }
    var location by remember { mutableStateOf("") }
    var brandExpanded by remember { mutableStateOf(false) }
    val brands = listOf("HP", "Canon", "Epson", "Brother", "Generic")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepSlate),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LightSlate)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "Declare Manual Printer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Label") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberTeal,
                        unfocusedBorderColor = LightSlate,
                        focusedLabelColor = CyberTeal,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_name_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IPv4 Address") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberTeal,
                        unfocusedBorderColor = LightSlate,
                        focusedLabelColor = CyberTeal,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_ip_field")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { brandExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, LightSlate)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Brand: $brand", color = TextPrimary)
                            Text("▼", color = CyberTeal)
                        }
                    }

                    DropdownMenu(
                        expanded = brandExpanded,
                        onDismissRequest = { brandExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSlate)
                    ) {
                        brands.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b, color = TextPrimary) },
                                onClick = {
                                    brand = b
                                    brandExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location Context") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberTeal,
                        unfocusedBorderColor = LightSlate,
                        focusedLabelColor = CyberTeal,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_location_field")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(name, ip, brand, location) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = MidnightBlue),
                        modifier = Modifier.testTag("submit_printer_dialog_button")
                    ) {
                        Text("Declare", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UsbTroubleshootView(
    viewModel: PrinterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usbPrinterConnected by viewModel.usbPrinterConnected.collectAsStateWithLifecycle()
    val usbDeviceInfo by viewModel.usbDeviceInfo.collectAsStateWithLifecycle()
    val usbDeviceNameState by viewModel.usbDeviceNameState.collectAsStateWithLifecycle()
    val isUsbTroubleshooting by viewModel.isUsbTroubleshooting.collectAsStateWithLifecycle()
    val usbTroubleshootLogs by viewModel.usbTroubleshootLogs.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = BorderStroke(1.dp, LightSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "USB OTG Connection Hub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Troubleshoot and diagnose physical printers connected directly via USB OTG cables.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Connection Status Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (usbPrinterConnected) SuccessGreen.copy(alpha = 0.08f) else MidnightBlue
                ),
                border = BorderStroke(1.dp, if (usbPrinterConnected) SuccessGreen.copy(alpha = 0.4f) else LightSlate)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (usbPrinterConnected) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = "USB Status Indicator",
                        tint = if (usbPrinterConnected) SuccessGreen else CyberBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (usbPrinterConnected) usbDeviceNameState ?: "USB Printer Connected" else "USB Printer Disconnected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (usbPrinterConnected) usbDeviceInfo ?: "Descriptors ready" else "Connect your printer via USB OTG cable & scan",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    if (usbPrinterConnected) {
                        TextButton(
                            onClick = { viewModel.disconnectUsbPrinter() }
                        ) {
                            Text("Disconnect", color = ErrorCoral, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.scanAndConnectUsbPrinter(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberBlue,
                        contentColor = MidnightBlue
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan USB Bus", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.troubleshootUsbPrinter(context) },
                    enabled = usbPrinterConnected && !isUsbTroubleshooting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberTeal,
                        contentColor = MidnightBlue,
                        disabledContainerColor = LightSlate.copy(alpha = 0.3f),
                        disabledContentColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isUsbTroubleshooting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MidnightBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnosing...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Build, contentDescription = "Fix")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Troubleshoot", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sequence Diagnostics Logs
            Text(
                text = "Troubleshooting Sequence & AI Output",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightBlue),
                border = BorderStroke(1.dp, LightSlate)
            ) {
                if (usbTroubleshootLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No Logs Icon",
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No scan or troubleshooting results yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Tap 'Scan USB Bus' above to query connection registry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        usbTroubleshootLogs.forEach { log ->
                            if (log.startsWith("Gemini AI") || log.startsWith("USB Diagnostics Complete") || log.contains("Offline Fallback Guide")) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "AI Indicator Icon",
                                        tint = CyberTeal,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = CyberTeal
                                    )
                                }
                            } else if (log.contains("ERROR")) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error Indicator Icon",
                                        tint = ErrorCoral,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = ErrorCoral
                                    )
                                }
                            } else {
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiApiKeyDialog(
    onDismiss: () -> Unit,
    viewModel: PrinterViewModel
) {
    val useCustomApiKey by viewModel.useCustomApiKey.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()

    var keyInputValue by remember { mutableStateOf(customApiKey) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepSlate),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, LightSlate),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("gemini_api_key_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Config",
                        tint = CyberTeal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Gemini API Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Configure which API key PrinterDost should use for deep diagnostics synthesis and USB troubleshooting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Option 1: Built-In Key Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { viewModel.setUseCustomApiKey(false) }
                        .testTag("built_in_key_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!useCustomApiKey) MidnightBlue else DeepSlate.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (!useCustomApiKey) CyberTeal.copy(alpha = 0.8f) else LightSlate.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = !useCustomApiKey,
                                onClick = { viewModel.setUseCustomApiKey(false) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = CyberTeal,
                                    unselectedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI Studio Built-In Key",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!useCustomApiKey) CyberTeal else TextPrimary
                                )
                                Text(
                                    text = "Use the default API key configured securely in the AI Studio platform secrets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { 
                                viewModel.setUseCustomApiKey(false)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!useCustomApiKey) CyberTeal else LightSlate.copy(alpha = 0.2f),
                                contentColor = if (!useCustomApiKey) MidnightBlue else TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("activate_built_in_button")
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Activate")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!useCustomApiKey) "Built-In Key Active" else "Use Built-In Key",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Option 2: Custom Key Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_key_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (useCustomApiKey) MidnightBlue else DeepSlate.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (useCustomApiKey) CyberTeal.copy(alpha = 0.8f) else LightSlate.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = useCustomApiKey,
                                onClick = { viewModel.setUseCustomApiKey(true) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = CyberTeal,
                                    unselectedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom User API Key",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (useCustomApiKey) CyberTeal else TextPrimary
                                )
                                Text(
                                    text = "Insert your custom Gemini key to run independent high-speed API diagnostics.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Text field to view/edit custom API Key
                        OutlinedTextField(
                            value = keyInputValue,
                            onValueChange = { keyInputValue = it },
                            label = { Text("Insert Gemini API Key") },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Info else Icons.Default.Lock,
                                        contentDescription = "Toggle Visibility",
                                        tint = TextMuted
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberTeal,
                                unfocusedBorderColor = LightSlate,
                                focusedLabelColor = CyberTeal,
                                unfocusedLabelColor = TextMuted
                            ),
                            placeholder = { Text("AIzaSy...", color = TextMuted.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("custom_key_textfield")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Quick Load Default Fallback Button
                            OutlinedButton(
                                onClick = { keyInputValue = "AIzaSyBixUCp_2fu-HiM5SVd5X8jYWeE6jykBnc" },
                                border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("load_fallback_key_button")
                            ) {
                                Text("Load Key", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }

                            // Save and Activate Key
                            Button(
                                onClick = {
                                    viewModel.setCustomApiKey(keyInputValue)
                                    viewModel.setUseCustomApiKey(true)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (useCustomApiKey) CyberBlue else LightSlate.copy(alpha = 0.2f),
                                    contentColor = if (useCustomApiKey) MidnightBlue else TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.5f).testTag("save_and_use_key_button")
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Save Key Icon")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Use", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Close / Cancel Button at the very bottom
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_api_key_dialog_button")) {
                        Text("Close Panel", color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
