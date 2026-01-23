package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.delay

class OverlayService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null

    private val albumArt = mutableStateOf<Bitmap?>(null)
    private val appIcon = mutableStateOf<Drawable?>(null)
    private val isMusicPlaying = mutableStateOf(false)
    private val isExpanded = mutableStateOf(false)
    private val isControlsVisible = mutableStateOf(false)

    // Slider-controlled values
    private val hPadding = mutableStateOf(14)
    private val vPadding = mutableStateOf(4)
    private val holeWidth = mutableStateOf(60)
    private val pillHeight = mutableStateOf(36)
    private val circleSize = mutableStateOf(36)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    // Timer for controls dismissal
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { isControlsVisible.value = false }

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry = savedStateController.savedStateRegistry

    override fun onServiceConnected() {
        super.onServiceConnected()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        showOverlay()
        attachMediaListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            hPadding.value = it.getIntExtra("PILL_H_PADDING", hPadding.value)
            vPadding.value = it.getIntExtra("PILL_V_PADDING", vPadding.value)
            holeWidth.value = it.getIntExtra("HOLE_WIDTH", holeWidth.value)
            pillHeight.value = it.getIntExtra("PILL_HEIGHT", pillHeight.value)
            circleSize.value = it.getIntExtra("CIRCLE_SIZE", circleSize.value)

            if (::params.isInitialized) {
                params.x = it.getIntExtra("X_OFFSET", params.x)
                params.y = it.getIntExtra("Y_OFFSET", params.y)
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (_: Exception) {}
            }
        }
        return START_STICKY
    }

    private fun attachMediaListener() {
        val component = ComponentName(this, OverlayService::class.java)
        val refresh = {
            val controllers = mediaSessionManager.getActiveSessions(component)
            val controller = controllers.find {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
            } ?: controllers.firstOrNull()

            if (controller != null) bindController(controller) else clearMedia()
        }
        refresh()
        mediaSessionManager.addOnActiveSessionsChangedListener({ refresh() }, component)
    }

    private fun bindController(controller: MediaController) {
        if (activeController == controller) return
        mediaCallback?.let { activeController?.unregisterCallback(it) }
        activeController = controller

        val sync = {
            val state = controller.playbackState?.state
            isMusicPlaying.value = state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_BUFFERING

            val meta = controller.metadata
            albumArt.value = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

            try {
                appIcon.value = packageManager.getApplicationIcon(controller.packageName)
            } catch (_: Exception) {
                appIcon.value = null
            }
        }

        mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = sync()
            override fun onMetadataChanged(metadata: MediaMetadata?) = sync()
            override fun onSessionDestroyed() = clearMedia()
        }

        controller.registerCallback(mediaCallback!!)
        sync()
    }

    private fun clearMedia() {
        mediaCallback?.let { activeController?.unregisterCallback(it) }
        activeController = null
        mediaCallback = null
        albumArt.value = null
        appIcon.value = null
        isMusicPlaying.value = false
        isControlsVisible.value = false
        mainHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun resetControlsTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        if (isControlsVisible.value) {
            mainHandler.postDelayed(hideControlsRunnable, 1500) // 3.5 seconds
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val density = resources.displayMetrics.density
        var longPressRunnable: Runnable? = null
        var wasLongPress = false

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (150 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        wasLongPress = false
                        longPressRunnable = Runnable {
                            if (isMusicPlaying.value || isExpanded.value) {
                                isControlsVisible.value = true
                                wasLongPress = true
                                resetControlsTimer()
                            }
                        }
                        mainHandler.postDelayed(longPressRunnable!!, 500)
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (!wasLongPress) {
                            if (isControlsVisible.value) {
                                isControlsVisible.value = false
                                mainHandler.removeCallbacks(hideControlsRunnable)
                            } else {
                                isExpanded.value = !isExpanded.value
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        false
                    }
                    else -> false
                }
            }

            setContent {
                DynamicHolePunchOverlay(
                    expanded = isExpanded.value,
                    musicPlaying = isMusicPlaying.value,
                    art = albumArt.value,
                    icon = appIcon.value,
                    hPad = hPadding.value,
                    vPad = vPadding.value,
                    holeW = holeWidth.value,
                    pillH = pillHeight.value,
                    circleS = circleSize.value,
                    showControls = isControlsVisible.value,
                    onCollapse = { isExpanded.value = false },
                    onPrev = {
                        activeController?.transportControls?.skipToPrevious()
                        resetControlsTimer()
                    },
                    onNext = {
                        activeController?.transportControls?.skipToNext()
                        resetControlsTimer()
                    }
                )
            }
        }

        windowManager.addView(composeView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        clearMedia()
        mainHandler.removeCallbacks(hideControlsRunnable)
        composeView?.let { windowManager.removeView(it) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

@Composable
fun DynamicHolePunchOverlay(
    expanded: Boolean,
    musicPlaying: Boolean,
    art: Bitmap?,
    icon: Drawable?,
    hPad: Int,
    vPad: Int,
    holeW: Int,
    pillH: Int,
    circleS: Int,
    showControls: Boolean,
    onCollapse: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    LaunchedEffect(musicPlaying) {
        if (!musicPlaying && expanded) {
            delay(700)
            onCollapse()
        }
    }

    val totalPillWidth = (32 + 12 + holeW + 12 + 32 + (hPad * 2)).dp

    val pillWidth by animateDpAsState(
        targetValue = if (expanded || musicPlaying) totalPillWidth else circleS.dp,
        animationSpec = tween(400), label = "width"
    )

    val height by animateDpAsState(
        targetValue = if (expanded || musicPlaying) pillH.dp else circleS.dp,
        animationSpec = tween(400), label = "height"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .width(pillWidth)
                .height(height)
                .background(Color.Black, RoundedCornerShape(50))
                .padding(horizontal = hPad.dp, vertical = vPad.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (expanded || musicPlaying) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = showControls,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "LeftControlTransform"
                    ) { controls ->
                        if (controls) {
                            IconButton(onClick = onPrev) {
                                Text("⏮", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            }
                        } else {
                            art?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))
                Spacer(Modifier.width(holeW.dp))
                Spacer(Modifier.width(12.dp))

                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = showControls,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "RightControlTransform"
                    ) { controls ->
                        if (controls) {
                            IconButton(onClick = onNext) {
                                Text("⏭", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            }
                        } else {
                            icon?.let {
                                AndroidView(
                                    factory = { context ->
                                        ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                                    },
                                    update = { v -> v.setImageDrawable(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.size(circleS.dp).background(Color.Black, CircleShape))
            }
        }
    }
}