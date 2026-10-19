package com.example.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.model.ChatMessage
import com.example.chat.model.MessageRole
import com.example.chat.model.MessageStatus
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.GameBorder
import com.example.ui.theme.GameDarkCard
import com.example.ui.theme.GameDarkSurfaceVariant
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.ListeningState
import com.example.voice.TtsPlaybackState
import com.example.voice.VoiceEvent
import com.example.voice.VoiceManager

@Composable
fun ConversationView(
    uiState: ChatUiState,
    activeGame: String,
    onSendMessage: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onCaptureScreenshot: () -> Unit = {},
    modifier: Modifier = Modifier,
    maxHeight: Int = 230
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Phase 6 Voice and Speech state bindings
    val listeningState by VoiceManager.listeningState.collectAsState()
    val ttsState by VoiceManager.ttsState.collectAsState()
    val voiceEvent by VoiceManager.lastEvent.collectAsState()
    val autoReadAloud by VoiceManager.autoReadAloud.collectAsState()

    // Handle incoming voice events (e.g. final transcription from SpeechRecognizer)
    LaunchedEffect(voiceEvent) {
        when (val event = voiceEvent) {
            is VoiceEvent.SpeechTranscribed -> {
                inputText = event.text
                // Explicitly send transcribed voice query to assistant
                onSendMessage(event.text)
                VoiceManager.consumeLastEvent()
            }
            else -> {}
        }
    }

    // Auto-scroll to latest message when new message or token arrives
    val lastMessageContent = uiState.messages.lastOrNull()?.content ?: ""
    LaunchedEffect(uiState.messages.size, lastMessageContent.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Conversation List Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = maxHeight.dp)
                .background(GameDarkSurfaceVariant, RoundedCornerShape(12.dp))
                .border(0.5.dp, GameBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_messages_list")
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    val isMessageSpeaking = ttsState is TtsPlaybackState.Speaking &&
                            (ttsState as TtsPlaybackState.Speaking).utteranceId == msg.id

                    ChatMessageItem(
                        message = msg,
                        onRetry = onRetry,
                        isSpeaking = isMessageSpeaking,
                        onToggleTts = {
                            if (isMessageSpeaking) {
                                VoiceManager.stopSpeaking()
                            } else {
                                VoiceManager.speak(context, msg.content, utteranceId = msg.id)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Generating / Streaming Status Bar with Cancel Button
        AnimatedVisibility(visible = uiState.isGenerating) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulsingDot()
                    Text(
                        text = "Synthesizing $activeGame guidance…",
                        fontSize = 11.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .testTag("btn_cancel_generation")
                        .height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Active TTS Playback Banner with Stop button
        AnimatedVisibility(visible = ttsState is TtsPlaybackState.Speaking) {
            val speakingInfo = ttsState as? TtsPlaybackState.Speaking
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NeonEmerald.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("banner_tts_playing")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Reading Aloud: \"${speakingInfo?.textSnippet ?: "Speaking…"}\"",
                            fontSize = 11.sp,
                            color = NeonEmerald,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = { VoiceManager.stopSpeaking() },
                        modifier = Modifier
                            .testTag("btn_stop_tts")
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop TextToSpeech playback",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Active Voice Listening Banner (Microphone is NEVER started silently; shows explicit status)
        AnimatedVisibility(visible = listeningState is ListeningState.Listening) {
            val currentListening = listeningState as? ListeningState.Listening
            val soundLevel = currentListening?.soundLevelDb ?: 0f
            val partial = currentListening?.partialHypothesis.orEmpty()

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyberCyan.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("banner_voice_listening")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VoiceWaveIndicator(soundLevelDb = soundLevel)
                            Text(
                                text = "LISTENING • SPEAK NOW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Finish and submit speech
                            IconButton(
                                onClick = { VoiceManager.stopListening() },
                                modifier = Modifier
                                    .testTag("btn_stop_listening")
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Finish and recognize speech",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Cancel speech listening
                            IconButton(
                                onClick = { VoiceManager.cancelListening() },
                                modifier = Modifier
                                    .testTag("btn_cancel_listening")
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel listening",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (partial.isNotBlank()) {
                        Text(
                            text = "\"$partial\"",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        Text(
                            text = "Awaiting speech input… Speak your query clearly.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Processing speech state
        AnimatedVisibility(visible = listeningState is ListeningState.Processing) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GameDarkCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, GameBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulsingDot()
                    Text("Decoding spoken audio…", fontSize = 11.sp, color = CyberCyan)
                }
            }
        }

        // Voice error notice
        AnimatedVisibility(visible = listeningState is ListeningState.Error) {
            val err = listeningState as? ListeningState.Error
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF7F1D1D).copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Voice: ${err?.message ?: "Recognition error"}",
                        fontSize = 11.sp,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { VoiceManager.resetState() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Permission denied inline prompt
        AnimatedVisibility(visible = listeningState is ListeningState.PermissionDenied) {
            val denied = listeningState as? ListeningState.PermissionDenied
            val isPermanent = denied?.isPermanent == true
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CyberAmber.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("banner_voice_permission_denied")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isPermanent) {
                            "Microphone access blocked. Open App Settings to enable."
                        } else {
                            "Microphone permission required for voice."
                        },
                        fontSize = 11.sp,
                        color = CyberAmber,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isPermanent) {
                                    context.startActivity(com.example.util.PermissionUtils.getAppSettingsIntent(context))
                                } else {
                                    VoiceManager.startListening(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (isPermanent) "Settings" else "Grant", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { VoiceManager.resetState() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CyberAmber, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Quick Suggestion Chips and Audio Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = onCaptureScreenshot,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(12.dp), tint = CyberAmber)
                        Text("Vision", fontSize = 11.sp, color = CyberAmber)
                    }
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = GameDarkCard),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CyberAmber.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("chip_action_screenshot")
            )

            // Auto-Read-Aloud toggle chip
            FilterChip(
                selected = autoReadAloud,
                onClick = { VoiceManager.autoReadAloud.value = !autoReadAloud },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (autoReadAloud) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text("Auto Voice", fontSize = 11.sp)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                    selectedLabelColor = NeonEmerald,
                    containerColor = GameDarkCard,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = autoReadAloud,
                    borderColor = if (autoReadAloud) NeonEmerald else GameBorder
                ),
                modifier = Modifier.testTag("chip_auto_read_aloud")
            )

            AssistChip(
                onClick = {
                    inputText = "How do I beat the next boss?"
                },
                label = { Text("Boss Tactics", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = GameDarkCard, labelColor = TextPrimary),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = GameBorder),
                modifier = Modifier.testTag("chip_prompt_boss")
            )
        }

        // Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask about $activeGame…", fontSize = 12.sp, color = TextSecondary) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GameDarkCard,
                    unfocusedContainerColor = GameDarkCard,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = GameBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 2,
                enabled = !uiState.isGenerating
            )

            // Microphone Button (Explicit start, never silent)
            val isListening = listeningState is ListeningState.Listening
            IconButton(
                onClick = {
                    if (isListening) {
                        VoiceManager.stopListening()
                    } else {
                        VoiceManager.startListening(context)
                    }
                },
                modifier = Modifier
                    .testTag("btn_mic_listen")
                    .size(42.dp)
                    .background(
                        if (isListening) NeonEmerald.copy(alpha = 0.3f) else GameDarkCard,
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (isListening) NeonEmerald else GameBorder,
                        RoundedCornerShape(10.dp)
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = if (isListening) "Listening active. Tap to finish speech" else "Tap to speak your query",
                    tint = if (isListening) NeonEmerald else CyberCyan,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Explicit Screenshot Capture Button
            IconButton(
                onClick = onCaptureScreenshot,
                modifier = Modifier
                    .testTag("chat_btn_screenshot")
                    .size(42.dp)
                    .background(GameDarkCard, RoundedCornerShape(10.dp))
                    .border(1.dp, GameBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture and analyze screenshot",
                    tint = CyberAmber,
                    modifier = Modifier.size(19.dp)
                )
            }

            if (uiState.isGenerating) {
                // Cancel button directly in input row
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .testTag("btn_stop_streaming")
                        .size(42.dp)
                        .background(Color(0xFFDC2626), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Cancel request",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Send button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val prompt = inputText.trim()
                            inputText = ""
                            onSendMessage(prompt)
                        }
                    },
                    modifier = Modifier
                        .testTag("chat_btn_send")
                        .size(42.dp)
                        .background(CyberCyan, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    isSpeaking: Boolean = false,
    onToggleTts: () -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    val isError = message.status == MessageStatus.ERROR
    val isStreaming = message.status == MessageStatus.STREAMING
    val isCancelled = message.status == MessageStatus.CANCELLED

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            color = when {
                isUser -> CyberCyan.copy(alpha = 0.18f)
                isError -> Color(0xFF7F1D1D).copy(alpha = 0.35f)
                else -> GameDarkCard
            },
            border = when {
                isUser -> androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
                isError -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                else -> androidx.compose.foundation.BorderStroke(0.5.dp, GameBorder)
            },
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("chat_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Sender Header & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isUser -> Icons.Default.Gamepad
                                isError -> Icons.Default.ErrorOutline
                                else -> Icons.Default.AutoAwesome
                            },
                            contentDescription = null,
                            tint = when {
                                isUser -> NeonPurple
                                isError -> Color(0xFFEF4444)
                                else -> CyberCyan
                            },
                            modifier = Modifier.size(13.dp)
                        )

                        Text(
                            text = if (isUser) "Player" else "Game Concierge",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isUser -> NeonPurple
                                isError -> Color(0xFFEF4444)
                                else -> CyberCyan
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // TTS Read Aloud Icon Button for Assistant messages
                        if (!isUser && message.content.isNotEmpty() && !isError) {
                            IconButton(
                                onClick = onToggleTts,
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("btn_tts_message_${message.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop reading aloud" else "Read response aloud",
                                    tint = if (isSpeaking) NeonEmerald else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        if (isStreaming) {
                            PulsingDot()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content
                if (message.content.isEmpty() && isStreaming) {
                    Text(
                        text = "Analyzing game state…",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Text(
                        text = message.content,
                        fontSize = 12.sp,
                        color = if (isCancelled) TextSecondary else TextPrimary,
                        fontStyle = if (isCancelled) FontStyle.Italic else FontStyle.Normal,
                        lineHeight = 17.sp
                    )
                }

                // Error Retry Affordance
                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .testTag("btn_retry_chat")
                            .height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceWaveIndicator(soundLevelDb: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val normalizedDb = (soundLevelDb / 10f).coerceIn(0.5f, 1.8f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((8.dp * waveScale * normalizedDb).coerceIn(4.dp, 16.dp))
                .background(CyberCyan, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((12.dp * waveScale * normalizedDb).coerceIn(6.dp, 20.dp))
                .background(NeonEmerald, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((6.dp * waveScale * normalizedDb).coerceIn(3.dp, 12.dp))
                .background(CyberCyan, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(alpha)
            .background(CyberCyan, CircleShape)
    )
}
