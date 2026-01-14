package com.example.myapplication

import android.app.Notification
// FIX: Corrected the malformed import statement below
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    // --- FIX 1: State to hold the overlay text ---
    private val overlayTextState = mutableStateOf("Overlay")

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // --- FIX 2: Read text from Intent and update the state ---
        val textFromIntent = intent?.getStringExtra("OVERLAY_TEXT")
        if (textFromIntent != null) {
            overlayTextState.value = textFromIntent
        }

        // If the service is started for the first time
        if (composeView == null) {
            val topMargin = intent?.getIntExtra("TOP_MARGIN", 32) ?: 32
            val channelId = "OverlayServiceChannel"
            createNotificationChannel(channelId)
            val notification = createNotification(channelId)
            startForeground(1, notification)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            showOverlay(topMargin, 32)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        hideOverlay()
        Toast.makeText(this, "Overlay Service Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showOverlay(topMargin: Int, startMargin: Int) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            // PIXEL 6 SPECIFIC ALIGNMENT:
            // Center it horizontally at the top
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            // x is now an offset from the CENTER. 0 means perfectly centered.
            x = 0

            // y is the distance from the very top of the physical screen.
            // On a Pixel 6, the hole punch center is roughly at 48-60 pixels,
            // but setting it to 0-10 usually looks best for a "ring" or "badge" effect.
            y = 10
        }

        composeView = ComposeView(this).apply {
            setContent {
                MyOverlayContent(
                    text = overlayTextState.value,
                    onDrag = { dx, dy ->
                        // Update the layout params as the user drags
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager.updateViewLayout(this, params)
                    }
                )
            }
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
        }

        windowManager.addView(composeView, params)
    }

    private fun hideOverlay() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
    }

    private fun createNotification(channelId: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Overlay Service Active")
            .setContentText("The overlay is currently running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId, "Overlay Service Channel", NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }
}

// --- FIX 3: Update Composable to accept text ---
@Composable
fun MyOverlayContent(text: String, onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = text, color = Color.White, fontSize = 20.sp)
        }
    }
}
