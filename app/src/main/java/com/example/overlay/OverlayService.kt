package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.domain.OverlayUiState
import com.example.overlay.ui.ExpandedPanel
import com.example.overlay.ui.FloatingCapsule
import com.example.ui.theme.GameConciergeTheme
import com.example.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground Service hosting the Game Concierge WindowManager overlay.
 * Implements AndroidX Lifecycle & SavedState contracts so Compose functions natively outside an Activity.
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        const val CHANNEL_ID = "game_concierge_overlay_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_TOGGLE = "com.example.overlay.ACTION_TOGGLE"

        var instance: OverlayService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_TOGGLE
            }
            context.startService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val viewModelStoreInstance = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isViewAttached = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        startInForeground()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        com.example.capture.ScreenCaptureManager.initialize(this)
        initOverlayView()
        observeOverlayState()

        OverlayStateManager.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                val currentState = OverlayStateManager.uiState.value
                if (currentState.isExpanded()) {
                    OverlayStateManager.requestTransition(OverlayUiState.COLLAPSED)
                } else {
                    OverlayStateManager.requestTransition(OverlayUiState.EXPANDED)
                }
            }
            ACTION_START -> {
                if (OverlayStateManager.uiState.value == OverlayUiState.SUSPENDED) {
                    OverlayStateManager.forceState(OverlayUiState.COLLAPSED)
                }
            }
        }
        return START_STICKY
    }

    private fun initOverlayView() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)

            setContent {
                GameConciergeTheme {
                    val uiState by OverlayStateManager.uiState.collectAsState()
                    val pos by OverlayStateManager.capsulePosition.collectAsState()
                    val activeGame by OverlayStateManager.activeGame.collectAsState()
                    val chatViewModel = androidx.compose.runtime.remember { com.example.chat.ui.ChatViewModel() }
                    val chatUiState by chatViewModel.uiState.collectAsState()
                    val contextRepo = androidx.compose.runtime.remember { com.example.context.data.DefaultGameContextRepository() }
                    val sessionContext by contextRepo.sessionContext.collectAsState()
                    val captureEvent by com.example.capture.ScreenCaptureManager.lastCaptureEvent.collectAsState()

                    androidx.compose.runtime.LaunchedEffect(captureEvent) {
                        when (val event = captureEvent) {
                            is com.example.capture.ScreenCaptureEvent.Success -> {
                                chatViewModel.analyzeScreenshot(event.bitmap, activeGame, contextRepo)
                                com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                            }
                            is com.example.capture.ScreenCaptureEvent.Denied -> {
                                chatViewModel.onScreenshotDenied()
                                com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                            }
                            is com.example.capture.ScreenCaptureEvent.Revoked -> {
                                chatViewModel.onScreenshotRevoked()
                                com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                            }
                            is com.example.capture.ScreenCaptureEvent.Error -> {
                                chatViewModel.onScreenshotError(event.message)
                                com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                            }
                            null -> {}
                        }
                    }

                    val completedMsg by chatViewModel.latestCompletedMessage.collectAsState()
                    androidx.compose.runtime.LaunchedEffect(completedMsg) {
                        completedMsg?.let { msg ->
                            if (com.example.voice.VoiceManager.autoReadAloud.value) {
                                com.example.voice.VoiceManager.speak(this@OverlayService, msg.content, msg.id)
                            }
                            chatViewModel.consumeLatestCompletedMessage()
                        }
                    }

                    val listeningState by com.example.voice.VoiceManager.listeningState.collectAsState()
                    val ttsState by com.example.voice.VoiceManager.ttsState.collectAsState()

                    androidx.compose.runtime.LaunchedEffect(listeningState, chatUiState.isGenerating, ttsState, uiState) {
                        if (uiState != OverlayUiState.COLLAPSED && uiState != OverlayUiState.SUSPENDED) {
                            when {
                                listeningState is com.example.voice.ListeningState.Listening -> {
                                    if (uiState != OverlayUiState.LISTENING) {
                                        OverlayStateManager.requestTransition(OverlayUiState.LISTENING)
                                    }
                                }
                                chatUiState.isGenerating -> {
                                    if (uiState != OverlayUiState.THINKING) {
                                        OverlayStateManager.requestTransition(OverlayUiState.THINKING)
                                    }
                                }
                                ttsState is com.example.voice.TtsPlaybackState.Speaking -> {
                                    if (uiState != OverlayUiState.SPEAKING) {
                                        OverlayStateManager.requestTransition(OverlayUiState.SPEAKING)
                                    }
                                }
                                uiState in setOf(OverlayUiState.LISTENING, OverlayUiState.THINKING, OverlayUiState.SPEAKING) -> {
                                    OverlayStateManager.requestTransition(OverlayUiState.EXPANDED)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            uiState == OverlayUiState.COLLAPSED -> {
                                FloatingCapsule(
                                    isAttachedToRight = pos.isAttachedToRight,
                                    onClick = {
                                        OverlayStateManager.requestTransition(OverlayUiState.EXPANDED)
                                    },
                                    onDragDelta = { dx, dy ->
                                        handleCapsuleDrag(dx, dy)
                                    },
                                    onDragEnd = {
                                        handleCapsuleDragEnd()
                                    },
                                    modifier = Modifier.align(
                                        if (pos.isAttachedToRight) Alignment.CenterEnd else Alignment.CenterStart
                                    )
                                )
                            }
                            uiState.isExpanded() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    ExpandedPanel(
                                        activeGame = activeGame,
                                        chatUiState = chatUiState,
                                        sessionContext = sessionContext,
                                        contextRepo = contextRepo,
                                        onSendMessage = { chatViewModel.sendMessage(it, activeGame) },
                                        onCancelChat = {
                                            chatViewModel.cancelGeneration()
                                            com.example.voice.VoiceManager.stopSpeaking()
                                        },
                                        onRetryChat = { chatViewModel.retryLastMessage(activeGame) },
                                        onClearChat = {
                                            chatViewModel.clearHistory()
                                            com.example.voice.VoiceManager.stopSpeaking()
                                            com.example.voice.VoiceManager.cancelListening()
                                        },
                                        onCaptureScreenshot = {
                                            com.example.capture.ScreenCaptureManager.requestScreenCaptureConsent(this@OverlayService)
                                        },
                                        onSelectGame = { OverlayStateManager.setActiveGame(it) },
                                        onCollapse = {
                                            OverlayStateManager.requestTransition(OverlayUiState.COLLAPSED)
                                        },
                                        onClose = {
                                            stopSelf()
                                        }
                                    )
                                }
                            }
                            else -> {
                                // Suspended or Error
                            }
                        }
                    }
                }
            }
        }

        updateWindowLayout(OverlayStateManager.uiState.value)
    }

    private fun observeOverlayState() {
        OverlayStateManager.uiState.onEach { state ->
            if (state == OverlayUiState.SUSPENDED) {
                removeOverlayView()
            } else {
                updateWindowLayout(state)
            }
        }.launchIn(serviceScope)
    }

    private fun updateWindowLayout(state: OverlayUiState) {
        val wm = windowManager ?: return
        val view = composeView ?: return

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val pos = OverlayStateManager.capsulePosition.value

        val params = if (state.isExpanded()) {
            // Expanded HUD panel: wide and interactable with keyboard support
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (screenHeight * 0.08f).toInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        } else {
            // Collapsed capsule: WRAP_CONTENT, non-focusable so gameplay touches are unaffected
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or (if (pos.isAttachedToRight) Gravity.END else Gravity.START)
                x = 0
                y = (screenHeight * pos.normalizedY).toInt()
            }
        }

        currentLayoutParams = params

        try {
            if (!isViewAttached) {
                wm.addView(view, params)
                isViewAttached = true
            } else {
                wm.updateViewLayout(view, params)
            }
        } catch (e: IllegalArgumentException) {
            // View may have been detached by system or configuration change: attempt recovery
            try {
                wm.addView(view, params)
                isViewAttached = true
            } catch (_: Exception) {
                isViewAttached = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleCapsuleDrag(dx: Float, dy: Float) {
        val params = currentLayoutParams ?: return
        val wm = windowManager ?: return
        val view = composeView ?: return

        params.x = (params.x - dx).toInt()
        params.y = (params.y + dy).toInt()

        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleCapsuleDragEnd() {
        val params = currentLayoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Determine nearest edge (left or right)
        val isRight = params.x < screenWidth / 2
        val normalizedY = (params.y.toFloat() / screenHeight).coerceIn(0.08f, 0.88f)

        OverlayStateManager.updatePosition(isAttachedToRight = isRight, normalizedY = normalizedY)
        updateWindowLayout(OverlayUiState.COLLAPSED)
    }

    private fun removeOverlayView() {
        if (isViewAttached) {
            try {
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isViewAttached = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_concierge)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_tile_concierge, getString(R.string.action_toggle), togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_stop), stopPendingIntent)
            .build()
    }

    private fun startInForeground() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun promoteToMediaProjectionForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun revertToStandardForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Screen orientation, display density, or multi-window bounds changed: re-anchor window layout
        if (OverlayStateManager.isServiceRunning.value) {
            updateWindowLayout(OverlayStateManager.uiState.value)
        }
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        removeOverlayView()
        OverlayStateManager.setServiceRunning(false)
        OverlayStateManager.forceState(OverlayUiState.SUSPENDED)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreInstance.clear()
        com.example.voice.VoiceManager.cleanup()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
