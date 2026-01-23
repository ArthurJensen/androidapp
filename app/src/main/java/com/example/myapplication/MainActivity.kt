package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// ---------- PERMISSION HELPERS ----------

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val serviceId = "${context.packageName}/${context.packageName}.OverlayService"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains(serviceId)
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(context.packageName) == true
}

// ---------- MAIN ACTIVITY ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    OverlayControllerScreen()
                }
            }
        }
    }
}

@Composable
fun OverlayControllerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasMusicAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // 🟢 Circle controls
    var circleSize by remember { mutableStateOf(36f) }

    // 🔵 Pill controls
    var pillHeight by remember { mutableStateOf(36f) }
    var pillHPadding by remember { mutableStateOf(14f) }
    var pillVPadding by remember { mutableStateOf(4f) }
    var holeWidth by remember { mutableStateOf(60f) }

    // Position
    var xOffset by remember { mutableStateOf(0f) }
    var yOffset by remember { mutableStateOf(0f) }

    fun pushUpdate() {
        if (!isEnabled) return
        updateService(
            context = context,
            circleSize = circleSize.toInt(),
            pillHeight = pillHeight.toInt(),
            pillHPadding = pillHPadding.toInt(),
            pillVPadding = pillVPadding.toInt(),
            holeWidth = holeWidth.toInt(),
            x = xOffset.toInt(),
            y = yOffset.toInt()
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = isAccessibilityServiceEnabled(context)
                hasMusicAccess = isNotificationServiceEnabled(context)
                pushUpdate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        circleSize,
        pillHeight,
        pillHPadding,
        pillVPadding,
        holeWidth,
        xOffset,
        yOffset,
        isEnabled
    ) {
        pushUpdate()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (!isEnabled) {
            Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(64.dp))
            Text("Accessibility Required", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("Enable Accessibility")
            }
            return@Column
        }

        Text("Dynamic Island Controls", style = MaterialTheme.typography.headlineSmall)

        if (!hasMusicAccess) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    )
                }
            ) {
                Text("Grant Music Access")
            }
        }

        Spacer(Modifier.height(24.dp))

        // 🟢 CIRCLE
        Text("Idle Circle", style = MaterialTheme.typography.titleMedium)
        SliderItem("Circle Size", circleSize, 20f..60f) { circleSize = it }

        Spacer(Modifier.height(24.dp))

        // 🔵 PILL
        Text("Expanded Pill", style = MaterialTheme.typography.titleMedium)
        SliderItem("Pill Height", pillHeight, 24f..60f) { pillHeight = it }
        SliderItem("Horizontal Padding", pillHPadding, 6f..32f) { pillHPadding = it }
        SliderItem("Vertical Padding", pillVPadding, 2f..20f) { pillVPadding = it }
        SliderItem("Hole Width", holeWidth, 40f..140f) { holeWidth = it }

        Spacer(Modifier.height(24.dp))

        // 📍 POSITION
        Text("Position", style = MaterialTheme.typography.titleMedium)
        SliderItem("X Offset", xOffset, -200f..200f) { xOffset = it }
        SliderItem("Y Offset", yOffset, -200f..200f) { yOffset = it }
    }
}

@Composable
fun SliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$label: ${value.toInt()}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

// ---------- SERVICE UPDATE ----------

private fun updateService(
    context: Context,
    circleSize: Int,
    pillHeight: Int,
    pillHPadding: Int,
    pillVPadding: Int,
    holeWidth: Int,
    x: Int,
    y: Int
) {
    val intent = Intent().apply {
        setClassName(context.packageName, "${context.packageName}.OverlayService")

        putExtra("CIRCLE_SIZE", circleSize)

        putExtra("PILL_HEIGHT", pillHeight)
        putExtra("PILL_H_PADDING", pillHPadding)
        putExtra("PILL_V_PADDING", pillVPadding)
        putExtra("HOLE_WIDTH", holeWidth)

        putExtra("X_OFFSET", x)
        putExtra("Y_OFFSET", y)
    }
    context.startService(intent)
}
