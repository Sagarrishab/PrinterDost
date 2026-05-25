package com.sds.printerdost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.sds.printerdost.ui.screens.DashboardScreen
import com.sds.printerdost.ui.theme.MyApplicationTheme
import com.sds.printerdost.viewmodel.PrinterViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PrinterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Gracefully catch and log any silent or unexpected crashes during runtime
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("PRINTERDOST_CRASH", "CRITICAL FATAL EXCEPTION on thread '${thread.name}':", throwable)
            // Call system default or terminate safely to prevent hanging
            java.lang.System.exit(1)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
