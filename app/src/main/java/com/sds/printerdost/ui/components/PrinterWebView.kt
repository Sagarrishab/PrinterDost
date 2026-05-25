package com.sds.printerdost.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Printer Admin Interface",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close panel")
                }
            },
            actions = {
                IconButton(
                    onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.WHITE)
                    
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        } catch (e: Exception) {
                            // Ignore if not supported
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return false // Handle navigation internally rather than launching external intents
                        }
                    }
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
                if (currentUrl != url) {
                    currentUrl = url
                    view.loadUrl(url)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val view = webViewRef
                webViewRef = null
                view?.stopLoading()
                view?.clearHistory()
                // Safely remove view from its parent before destroying to prevent layout engine crashes
                val parent = view?.parent as? ViewGroup
                parent?.removeView(view)
                view?.destroy()
            } catch (e: Exception) {
                // Safeguard against disposal crashes
                e.printStackTrace()
            }
        }
    }
}
