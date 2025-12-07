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
import androidx.compose.material3.OutlinedTextField
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

    // --- FIX 1: State to hold the text from the TextField ---
    var overlayText by remember { mutableStateOf("Overlay") }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = Settings.canDrawOverlays(context)
        if (!hasPermission) {
            Toast.makeText(context, "Overlay permission was not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Add some padding
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- FIX 2: Add a TextField for user input ---
        OutlinedTextField(
            value = overlayText,
            onValueChange = { overlayText = it },
            label = { Text("Overlay Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (hasPermission) {
                // --- FIX 3: Pass the text from our state to the service ---
                startOverlayService(context, statusBarHeight, overlayText)
            } else {
                Toast.makeText(context, "Please grant overlay permission.", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }) {
            Text(text = "Start / Update Overlay")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            stopOverlayService(context)
        }) {
            Text(text = "Stop Overlay Service")
        }
    }
}

// Updated to accept the overlay text
private fun startOverlayService(context: Context, topMargin: Int, text: String) {
    val intent = Intent(context, OverlayService::class.java).apply {
        putExtra("TOP_MARGIN", topMargin)
        // Put the user's text into the Intent
        putExtra("OVERLAY_TEXT", text)
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
