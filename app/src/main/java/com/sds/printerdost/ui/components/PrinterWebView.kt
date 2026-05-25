package com.sds.printerdost.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sds.printerdost.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PrinterWebView(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("") }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var hasError by remember { mutableStateOf(false) }
    var forceSimulated by remember { mutableStateOf(false) }

    // Strip schemas to find raw IP
    val rawIp = url.replace("http://", "").replace("https://", "").split("/").firstOrNull() ?: "192.168.1.100"

    val simulatedHtml = remember(rawIp) {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>PrinterDost Admin Console</title>
        <style>
          body {
            background-color: #0F172A;
            color: #F8FAFC;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            margin: 0;
            padding: 16px;
          }
          .header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 20px;
            border-bottom: 1px solid #334155;
            padding-bottom: 12px;
          }
          .title {
            font-size: 20px;
            font-weight: 800;
            color: #38BDF8;
            margin: 0;
          }
          .card {
            background-color: #1E293B;
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 16px;
          }
          .card h2 {
            margin-top: 0;
            font-size: 13px;
            font-weight: 700;
            color: #2DD4BF;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            margin-bottom: 12px;
          }
          .status-badge {
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: bold;
            display: inline-block;
            text-transform: uppercase;
          }
          .status-ready { background-color: rgba(16, 185, 129, 0.15); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.3); }
          .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
          .btn {
            background-color: #334155;
            color: #F8FAFC;
            border: 1px solid rgba(255,255,255,0.05);
            padding: 12px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            text-align: center;
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
          }
          .btn:active { background-color: #475569; transform: scale(0.98); }
          .btn-primary { background-color: #38BDF8; color: #0F172A; }
          .btn-primary:active { background-color: #0ea5e9; }
          .console {
            background-color: #030712;
            border: 1px solid #334155;
            border-radius: 8px;
            font-family: 'Courier New', monospace;
            font-size: 11px;
            padding: 12px;
            color: #4ade80;
            height: 120px;
            overflow-y: auto;
            line-height: 1.5;
          }
          .info-row {
            display: flex;
            justify-content: space-between;
            padding: 6px 0;
            border-bottom: 1px solid rgba(255, 255, 255, 0.03);
            font-size: 13px;
          }
          .info-row:last-child { border-bottom: none; }
          .info-label { color: #94A3B8; }
          .info-val { font-weight: 600; color: #F8FAFC; }
        </style>
        </head>
        <body>
          <div class="header">
            <div>
              <div class="title">PrinterDost Embedded-OS</div>
              <div style="font-size:11px; color:#94A3B8; margin-top:2px;">Local Administrative Console Gateway</div>
            </div>
            <span class="status-badge status-ready" id="deviceStatus">ONLINE</span>
          </div>
          
          <div class="card">
            <h2>Hardware Network Properties</h2>
            <div class="info-row">
              <span class="info-label">Active Host IP Address</span>
              <span class="info-val" id="ipAddress">$rawIp</span>
            </div>
            <div class="info-row">
              <span class="info-label">MAC Address Address</span>
              <span class="info-val">00:80:F4:11:9A:C3</span>
            </div>
            <div class="info-row">
              <span class="info-label">Web Engine Version</span>
              <span class="info-val">v2.12.9 (mDNS Ready)</span>
            </div>
            <div class="info-row">
              <span class="info-label">Direct Print Interface</span>
              <span class="info-val">Port 9100 Raw Stream</span>
            </div>
          </div>

          <div class="card">
            <h2>Hardware Engine Diagnostics</h2>
            <div style="margin-bottom:8px; font-size:12px; display:flex; justify-content:space-between;">
              <span style="color:#94A3B8;">Toner/Ink Cartridge Levels</span>
              <span id="inkPercent" style="font-weight:bold; color:#38BDF8;">94%</span>
            </div>
            <div style="background-color:#0F172A; border-radius:10px; height:12px; width:100%; overflow:hidden; border: 1px solid #334155;">
              <div id="inkBar" style="background-color: #38BDF8; width:94%; height:100%; transition: width 0.5s ease-out;"></div>
            </div>
          </div>

          <div class="card">
            <h2>Operations Protocol Actions</h2>
            <div class="grid">
              <button class="btn btn-primary" onclick="executeCommand('PRINT_TEST')">Print Pattern Page</button>
              <button class="btn" onclick="executeCommand('PURGE_POOL')">Purge Print Queue</button>
              <button class="btn" onclick="executeCommand('FLUSH_CACHE')">Flush DNS Cache</button>
              <button class="btn" onclick="executeCommand('CYCLE_POWER')">Soft Power Cycle</button>
            </div>
          </div>

          <div class="card">
            <h2>Diagnostics Console Log</h2>
            <div class="console" id="consoleLogs">
              &gt; Handshake received from local device.<br>
              &gt; Connection established securely over local network socket.<br>
              &gt; Device status register flags are clear (0x18). System READY.<br>
              &gt; Host ping latency: 3ms.
            </div>
          </div>

          <script>
            function log(msg) {
              const c = document.getElementById('consoleLogs');
              c.innerHTML += '<br>&gt; ' + msg;
              c.scrollTop = c.scrollHeight;
            }
            function executeCommand(cmd) {
              if (cmd === 'PRINT_TEST') {
                log('Executing local diagnostic self-test print run...');
                setTimeout(() => log('Spooling complete. Check local output tray for standard grid pattern test sheet.'), 600);
              } else if (cmd === 'PURGE_POOL') {
                log('Querying printer spooler queue registers...');
                setTimeout(() => {
                  log('Purging 0 active pending spool documents.');
                  log('Active queue memory successfully reclaimed.');
                }, 500);
              } else if (cmd === 'FLUSH_CACHE') {
                log('Flushing internal mDNS socket buffers...');
                setTimeout(() => log('Flush complete. 6 host bindings cleared.'), 400);
              } else if (cmd === 'CYCLE_POWER') {
                log('Issuing hardware cold restart request...');
                document.getElementById('deviceStatus').className = 'status-badge';
                document.getElementById('deviceStatus').style.backgroundColor = 'rgba(239, 68, 68, 0.15)';
                document.getElementById('deviceStatus').style.color = '#EF4444';
                document.getElementById('deviceStatus').innerText = 'OFFLINE';
                setTimeout(() => {
                  log('Broadcasting offline warning. Shutting down system interface...');
                  setTimeout(() => {
                    log('Reconnecting... system POST checks passed.');
                    document.getElementById('deviceStatus').className = 'status-badge status-ready';
                    document.getElementById('deviceStatus').innerText = 'ONLINE';
                  }, 1200);
                }, 500);
              }
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Printer Admin Interface",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (forceSimulated || hasError) "Simulated Console Preview (Offline Mode)" else url,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (forceSimulated || hasError) CyberTeal else TextMuted,
                        maxLines = 1
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close panel", tint = TextPrimary)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        forceSimulated = !forceSimulated
                        if (forceSimulated) {
                            webViewRef?.loadDataWithBaseURL("https://printerdost.local", simulatedHtml, "text/html", "UTF-8", null)
                        } else {
                            hasError = false
                            webViewRef?.loadUrl(url)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Force Simulated Admin Console Page",
                        tint = if (forceSimulated) CyberTeal else TextMuted
                    )
                }
                IconButton(
                    onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                IconButton(
                    onClick = {
                        hasError = false
                        if (forceSimulated) {
                            webViewRef?.loadDataWithBaseURL("https://printerdost.local", simulatedHtml, "text/html", "UTF-8", null)
                        } else {
                            webViewRef?.reload()
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MidnightBlue,
                titleContentColor = TextPrimary
            )
        )

        if (loadingProgress in 1..99 && !hasError && !forceSimulated) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = CyberBlue,
                trackColor = LightSlate
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(Color.parseColor("#0F172A"))
                        
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                hasError = true
                                // Safely load fallback HTML when local printer is unreachable via public WAN
                                view?.loadDataWithBaseURL("https://printerdost.local", simulatedHtml, "text/html", "UTF-8", null)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadingProgress = newProgress
                            }
                        }

                        webViewRef = this
                        
                        // Load initial view
                        if (forceSimulated) {
                            loadDataWithBaseURL("https://printerdost.local", simulatedHtml, "text/html", "UTF-8", null)
                        } else {
                            loadUrl(url)
                        }
                    }
                },
                update = { view ->
                    webViewRef = view
                    if (!forceSimulated && currentUrl != url) {
                        currentUrl = url
                        hasError = false
                        view.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (loadingProgress < 100 && !hasError && !forceSimulated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CyberTeal,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Awaiting Printer Web-Console Connection...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Target Address: $url",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = CyberBlue,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepSlate),
                            border = BorderStroke(1.dp, LightSlate.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = "Emulator Note", tint = CyberTeal)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Cloud Emulator Connection",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Your streaming cloud emulator cannot route requests to physical local network IP addresses (192.168.x.x). To explore this gateway, please load the simulated console.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        forceSimulated = true
                                        webViewRef?.loadDataWithBaseURL("https://printerdost.local", simulatedHtml, "text/html", "UTF-8", null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberTeal,
                                        contentColor = MidnightBlue
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Build, contentDescription = "Simulate")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Simulated Admin Page", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val view = webViewRef
                webViewRef = null
                view?.stopLoading()
                view?.clearHistory()
                val parent = view?.parent as? ViewGroup
                parent?.removeView(view)
                view?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
