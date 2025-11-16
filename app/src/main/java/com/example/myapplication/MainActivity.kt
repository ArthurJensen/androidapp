package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This allows us to get information about system bars like status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Get status bar height and pass it to our screen
                    // FIX: This requires the WindowInsets class to be available.
                    // The 'asPaddingValues()' and 'calculateTopPadding()' are part of Compose's layout system.
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    OverlayControllerScreen(statusBarHeight = statusBarHeight.value.toInt())
                }
            }
        }
    }
}

@Composable
fun OverlayControllerScreen(statusBarHeight: Int) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = Settings.canDrawOverlays(context)
        if (!hasPermission) {
            Toast.makeText(context, "Overlay permission was not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            if (hasPermission) {
                // Pass the status bar height to the service
                startOverlayService(context, statusBarHeight)
            } else {
                Toast.makeText(context, "Please grant overlay permission.", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }) {
            Text(text = "Start Overlay Service")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            stopOverlayService(context)
        }) {
            Text(text = "Stop Overlay Service")
        }
    }
}

// Updated to accept the top margin
private fun startOverlayService(context: Context, topMargin: Int) {
    val intent = Intent(context, OverlayService::class.java).apply {
        // Put the top margin value into the Intent so the service can read it
        putExtra("TOP_MARGIN", topMargin)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopOverlayService(context: Context) {
    context.stopService(Intent(context, OverlayService::class.java))
}

