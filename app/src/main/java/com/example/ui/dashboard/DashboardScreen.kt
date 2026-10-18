package com.example.ui.dashboard

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.OverlayUiState
import com.example.overlay.OverlayService
import com.example.overlay.OverlayStateManager
import com.example.overlay.ui.ExpandedPanel
import com.example.overlay.ui.FloatingCapsule
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GameBorder
import com.example.ui.theme.GameDarkCard
import com.example.ui.theme.GameDarkSurface
import com.example.ui.theme.GameDarkSurfaceVariant
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.PermissionUtils

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.example.capture.ScreenCaptureManager.initialize(context)
    }

    var hasOverlayPermission by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }
    var hasNotifPermission by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var hasAudioPermission by remember { mutableStateOf(PermissionUtils.hasRecordAudioPermission(context)) }

    // Re-check permissions automatically when user navigates back from system Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = PermissionUtils.canDrawOverlays(context)
                hasNotifPermission = PermissionUtils.hasNotificationPermission(context)
                hasAudioPermission = PermissionUtils.hasRecordAudioPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        com.example.voice.VoiceManager.handlePermissionResult(granted)
    }

    val isRunning by OverlayStateManager.isServiceRunning.collectAsState()
    val uiState by OverlayStateManager.uiState.collectAsState()
    val activeGame by OverlayStateManager.activeGame.collectAsState()
    val captureStatus by com.example.capture.ScreenCaptureManager.captureStatus.collectAsState()
    val lastCaptureEvent by com.example.capture.ScreenCaptureManager.lastCaptureEvent.collectAsState()

    val listeningState by com.example.voice.VoiceManager.listeningState.collectAsState()
    val ttsState by com.example.voice.VoiceManager.ttsState.collectAsState()
    val lastVoiceEvent by com.example.voice.VoiceManager.lastEvent.collectAsState()

    var showInAppSimulator by remember { mutableStateOf(false) }
    var simState by remember { mutableStateOf(OverlayUiState.COLLAPSED) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GameDarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(CyberCyan, NeonPurple)),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Text(
                    text = "GAME CONCIERGE",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Contextual In-Game Companion • Phase 1",
                    fontSize = 12.sp,
                    color = CyberCyan
                )
            }
        }

        // Live Companion Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GameDarkSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isRunning) NeonEmerald else GameBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "COMPANION STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isRunning) NeonEmerald else CyberAmber, CircleShape)
                            )
                            Text(
                                text = if (isRunning) "Floating Overlay Active ($uiState)" else "Service Inactive",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRunning) NeonEmerald else TextPrimary
                            )
                        }
                    }

                    if (isRunning) {
                        Button(
                            onClick = { OverlayService.stop(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_stop_service")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (hasOverlayPermission) {
                                    OverlayService.start(context)
                                } else {
                                    context.startActivity(PermissionUtils.getOverlayPermissionIntent(context))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_start_service")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Launch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Required Permissions Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GameDarkSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = "SYSTEM ACCESS & PERMISSIONS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                HorizontalDivider(color = GameBorder)

                // 1. Overlay Permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasOverlayPermission) NeonEmerald else CyberAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Display Over Other Apps",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Required for the floating capsule to hover over compatible games.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!hasOverlayPermission) {
                        Button(
                            onClick = {
                                context.startActivity(PermissionUtils.getOverlayPermissionIntent(context))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_request_overlay_permission")
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonEmerald.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Granted",
                                color = NeonEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 2. Notification Permission (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(color = GameBorder.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (hasNotifPermission) Icons.Default.CheckCircle else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (hasNotifPermission) NeonEmerald else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Foreground Notification",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "Keeps companion active in background without system termination.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (!hasNotifPermission) {
                            OutlinedButton(
                                onClick = {
                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_request_notif_permission")
                            ) {
                                Text("Allow", fontSize = 11.sp)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonEmerald.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Active",
                                    color = NeonEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Explicit Screen Capture & Vision Analysis (Phase 5)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_phase5_capture"),
            colors = CardDefaults.cardColors(containerColor = GameDarkSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = CyberAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "SCREEN ANALYSIS & CONSENT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (captureStatus) {
                            com.example.capture.CaptureStatus.COMPLETED -> NeonEmerald.copy(alpha = 0.2f)
                            com.example.capture.CaptureStatus.CAPTURING,
                            com.example.capture.CaptureStatus.REQUESTING_CONSENT -> CyberAmber.copy(alpha = 0.2f)
                            com.example.capture.CaptureStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.2f)
                            else -> GameDarkCard
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (captureStatus) {
                                com.example.capture.CaptureStatus.COMPLETED -> NeonEmerald
                                com.example.capture.CaptureStatus.CAPTURING,
                                com.example.capture.CaptureStatus.REQUESTING_CONSENT -> CyberAmber
                                com.example.capture.CaptureStatus.ERROR -> Color(0xFFEF4444)
                                else -> GameBorder
                            }
                        )
                    ) {
                        Text(
                            text = captureStatus.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (captureStatus) {
                                com.example.capture.CaptureStatus.COMPLETED -> NeonEmerald
                                com.example.capture.CaptureStatus.CAPTURING,
                                com.example.capture.CaptureStatus.REQUESTING_CONSENT -> CyberAmber
                                com.example.capture.CaptureStatus.ERROR -> Color(0xFFEF4444)
                                else -> TextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Explicit MediaProjection consent prompt: captures a single frame on demand, releases all projection resources immediately, and sends the frame for multimodal AI analysis.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                // Event Feedback Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GameDarkCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CAPTURE TELEMETRY & LIFECYCLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        val eventText = when (val ev = lastCaptureEvent) {
                            is com.example.capture.ScreenCaptureEvent.Success ->
                                "Frame Captured: ${ev.width}x${ev.height} px • Resources stopped & closed."
                            is com.example.capture.ScreenCaptureEvent.Denied ->
                                "Consent Denied: User cancelled MediaProjection consent."
                            is com.example.capture.ScreenCaptureEvent.Revoked ->
                                "Consent Revoked: Android system revoked screen projection."
                            is com.example.capture.ScreenCaptureEvent.Error ->
                                "Capture Error: ${ev.message}"
                            null -> "Status: Idle • Ready for explicit user capture."
                        }
                        Text(
                            text = eventText,
                            fontSize = 12.sp,
                            color = when (lastCaptureEvent) {
                                is com.example.capture.ScreenCaptureEvent.Success -> NeonEmerald
                                is com.example.capture.ScreenCaptureEvent.Denied,
                                is com.example.capture.ScreenCaptureEvent.Revoked -> CyberAmber
                                is com.example.capture.ScreenCaptureEvent.Error -> Color(0xFFEF4444)
                                null -> TextPrimary
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            com.example.capture.ScreenCaptureManager.requestScreenCaptureConsent(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("btn_request_capture")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Capture Frame", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            com.example.capture.ScreenCaptureManager.handleConsentRevoked()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_simulate_revocation")
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Revoke", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            com.example.capture.ScreenCaptureManager.handleCaptureError("Simulated system failure")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_simulate_error")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fail", fontSize = 11.sp)
                    }
                }
            }
        }

        // Voice Assistant & Text-to-Speech Engine (Phase 6)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_phase6_voice"),
            colors = CardDefaults.cardColors(containerColor = GameDarkSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "VOICE ENGINE & SPEECH (PHASE 6)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (listeningState) {
                            is ListeningState.Listening -> NeonEmerald.copy(alpha = 0.2f)
                            is ListeningState.Processing,
                            is ListeningState.Ready -> CyberCyan.copy(alpha = 0.2f)
                            is ListeningState.Error,
                            is ListeningState.PermissionDenied -> Color(0xFFEF4444).copy(alpha = 0.2f)
                            else -> GameDarkCard
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (listeningState) {
                                is ListeningState.Listening -> NeonEmerald
                                is ListeningState.Processing,
                                is ListeningState.Ready -> CyberCyan
                                is ListeningState.Error,
                                is ListeningState.PermissionDenied -> Color(0xFFEF4444)
                                else -> GameBorder
                            }
                        )
                    ) {
                        Text(
                            text = when (listeningState) {
                                is ListeningState.Idle -> "IDLE"
                                is ListeningState.RequestingPermission -> "REQ_PERM"
                                is ListeningState.Ready -> "MIC READY"
                                is ListeningState.Listening -> "LISTENING"
                                is ListeningState.Processing -> "PROCESSING"
                                is ListeningState.Error -> "ERROR"
                                is ListeningState.PermissionDenied -> "DENIED"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (listeningState) {
                                is ListeningState.Listening -> NeonEmerald
                                is ListeningState.Processing,
                                is ListeningState.Ready -> CyberCyan
                                is ListeningState.Error,
                                is ListeningState.PermissionDenied -> Color(0xFFEF4444)
                                else -> TextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Zero-Silent-Listening Guarantee: Microphone hardware is NEVER activated automatically. Supports real-time SpeechRecognizer, instant cancelation, and TextToSpeech read-aloud.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                // Permissions Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GameDarkCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "RECORD_AUDIO PERMISSION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (hasAudioPermission) "Permission Granted" else "Permission Required",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasAudioPermission) NeonEmerald else CyberAmber
                            )
                        }

                        if (!hasAudioPermission) {
                            Button(
                                onClick = { audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .testTag("btn_request_voice_perm")
                                    .height(30.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Grant Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NeonEmerald.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Audio Telemetry Status Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GameDarkCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "VOICE ENGINE TELEMETRY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )

                        val telemetryText = when (val ls = listeningState) {
                            is ListeningState.Listening ->
                                "Listening Active • Volume: ${"%.1f".format(ls.soundLevelDb)} dB • Partial: \"${ls.partialHypothesis}\""
                            is ListeningState.Processing -> "Processing speech decoding…"
                            is ListeningState.Ready -> "Microphone ready (hardware closed). Tap button to speak."
                            is ListeningState.Error -> "Speech Error: ${ls.message}"
                            is ListeningState.PermissionDenied -> "RECORD_AUDIO was declined by player."
                            is ListeningState.RequestingPermission -> "Requesting runtime permission…"
                            is ListeningState.Idle -> "Microphone Idle • Hardware strictly closed."
                        }

                        Text(
                            text = telemetryText,
                            fontSize = 11.sp,
                            color = when (listeningState) {
                                is ListeningState.Listening -> NeonEmerald
                                is ListeningState.Processing,
                                is ListeningState.Ready -> CyberCyan
                                is ListeningState.Error,
                                is ListeningState.PermissionDenied -> Color(0xFFEF4444)
                                else -> TextPrimary
                            },
                            fontWeight = FontWeight.Medium
                        )

                        // TTS State telemetry
                        val ttsTelemetry = when (val ts = ttsState) {
                            is TtsPlaybackState.Speaking -> "TTS Speaking: \"${ts.textSnippet}\""
                            is TtsPlaybackState.Initializing -> "TTS Initializing engine…"
                            is TtsPlaybackState.Stopped -> "TTS Playback stopped by player."
                            is TtsPlaybackState.Error -> "TTS Error: ${ts.message}"
                            is TtsPlaybackState.Idle -> "TTS Idle • Ready to read guidance aloud."
                        }

                        Text(
                            text = ttsTelemetry,
                            fontSize = 11.sp,
                            color = when (ttsState) {
                                is TtsPlaybackState.Speaking -> NeonEmerald
                                is TtsPlaybackState.Stopped -> CyberAmber
                                is TtsPlaybackState.Error -> Color(0xFFEF4444)
                                else -> TextSecondary
                            }
                        )
                    }
                }

                // SpeechRecognizer Interactive Controls
                Text(
                    text = "SPEECH RECOGNITION CONTROLS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isListening = listeningState is ListeningState.Listening
                    Button(
                        onClick = {
                            if (isListening) {
                                com.example.voice.VoiceManager.stopListening()
                            } else {
                                com.example.voice.VoiceManager.startListening(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) NeonEmerald else CyberCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_start_listening")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isListening) "Finish Speech" else "Start Listen",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { com.example.voice.VoiceManager.cancelListening() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_cancel_listening_dash")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            com.example.voice.VoiceManager.simulateSpeechInput("Where can I find Smithing Stones in Elden Ring?")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("btn_simulate_speech")
                    ) {
                        Text("Simulate", fontSize = 11.sp)
                    }
                }

                // TextToSpeech Controls
                Text(
                    text = "TEXT-TO-SPEECH READ ALOUD CONTROLS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            com.example.voice.VoiceManager.speak(
                                context,
                                "Game Concierge tactical audio ready. Roll towards the dragon when it breathes fire."
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_test_tts")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Speak Sample", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { com.example.voice.VoiceManager.stopSpeaking() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_stop_tts_dash")
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop Audio", fontSize = 11.sp)
                    }
                }
            }
        }

        // Quick Settings Tile Integration Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GameDarkSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "QUICK SETTINGS TILE GUIDE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "Add the Game Concierge tile to your notification shade to launch or hide the companion instantly while gaming:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TileStep(step = "1", text = "Swipe down twice from the top of your screen to open Quick Settings.")
                    TileStep(step = "2", text = "Tap the Edit / Pencil icon in the bottom corner.")
                    TileStep(step = "3", text = "Scroll to find the \"Concierge\" tile and drag it to your active toggles.")
                    TileStep(step = "4", text = "Tap the tile anytime to activate or collapse the companion capsule!")
                }
            }
        }

        // In-App Interactive Sandbox / Overlay Simulation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GameDarkCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "SANDBOX PREVIEW",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Button(
                        onClick = { showInAppSimulator = !showInAppSimulator },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showInAppSimulator) CyberCyan.copy(alpha = 0.2f) else GameDarkSurface,
                            contentColor = CyberCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_toggle_sandbox")
                    ) {
                        Text(if (showInAppSimulator) "Hide Sandbox" else "Test Sandbox", fontSize = 11.sp)
                    }
                }

                Text(
                    text = "Preview how the floating capsule docks to screen bezels and expands into the HUD panel.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                AnimatedVisibility(visible = showInAppSimulator) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .background(Color(0xFF07090E), RoundedCornerShape(12.dp))
                            .border(1.dp, GameBorder, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (simState == OverlayUiState.COLLAPSED) {
                            FloatingCapsule(
                                isAttachedToRight = true,
                                onClick = { simState = OverlayUiState.EXPANDED },
                                onDragDelta = { _, _ -> },
                                onDragEnd = {},
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        } else {
                            val sandboxChatViewModel = remember { com.example.chat.ui.ChatViewModel() }
                            val sandboxChatUiState by sandboxChatViewModel.uiState.collectAsState()
                            val sandboxContextRepo = remember { com.example.context.data.DefaultGameContextRepository() }
                            val sandboxSessionContext by sandboxContextRepo.sessionContext.collectAsState()
                            val captureEvent by com.example.capture.ScreenCaptureManager.lastCaptureEvent.collectAsState()

                            androidx.compose.runtime.LaunchedEffect(captureEvent) {
                                when (val event = captureEvent) {
                                    is com.example.capture.ScreenCaptureEvent.Success -> {
                                        sandboxChatViewModel.analyzeScreenshot(event.bitmap, activeGame, sandboxContextRepo)
                                        com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                                    }
                                    is com.example.capture.ScreenCaptureEvent.Denied -> {
                                        sandboxChatViewModel.onScreenshotDenied()
                                        com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                                    }
                                    is com.example.capture.ScreenCaptureEvent.Revoked -> {
                                        sandboxChatViewModel.onScreenshotRevoked()
                                        com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                                    }
                                    is com.example.capture.ScreenCaptureEvent.Error -> {
                                        sandboxChatViewModel.onScreenshotError(event.message)
                                        com.example.capture.ScreenCaptureManager.consumeCaptureEvent()
                                    }
                                    null -> {}
                                }
                            }

                            ExpandedPanel(
                                activeGame = activeGame,
                                chatUiState = sandboxChatUiState,
                                sessionContext = sandboxSessionContext,
                                contextRepo = sandboxContextRepo,
                                onSendMessage = { sandboxChatViewModel.sendMessage(it, activeGame) },
                                onCancelChat = { sandboxChatViewModel.cancelGeneration() },
                                onRetryChat = { sandboxChatViewModel.retryLastMessage(activeGame) },
                                onClearChat = { sandboxChatViewModel.clearHistory() },
                                onCaptureScreenshot = {
                                    com.example.capture.ScreenCaptureManager.requestScreenCaptureConsent(context)
                                },
                                onSelectGame = { OverlayStateManager.setActiveGame(it) },
                                onCollapse = { simState = OverlayUiState.COLLAPSED },
                                onClose = { simState = OverlayUiState.COLLAPSED },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileStep(step: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = CyberCyan.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = step, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
            }
        }
        Text(text = text, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
    }
}
