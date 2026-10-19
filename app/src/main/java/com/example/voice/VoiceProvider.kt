package com.example.voice

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Replaceable provider interface for speech-to-text (STT) and text-to-speech (TTS) services.
 *
 * Adheres to Section 8 of the Game Concierge architecture:
 * 1. ZERO SILENT LISTENING: The microphone is NEVER activated without explicit, intentional player action.
 * 2. Explicit permission handling before audio recording hardware is touched.
 * 3. Replaceable implementation allowing migration from native Android SpeechRecognizer/TextToSpeech
 *    to real-time bidirectional audio streaming (e.g., Gemini Multimodal Live, WebSockets).
 * 4. Fast, deterministic cancellation of both listening and audio playback.
 */
interface VoiceProvider {
    val listeningState: StateFlow<ListeningState>
    val ttsState: StateFlow<TtsPlaybackState>
    val lastEvent: StateFlow<VoiceEvent?>
    val autoReadAloud: StateFlow<Boolean>

    fun setAutoReadAloud(enabled: Boolean)
    fun hasRecordAudioPermission(context: Context): Boolean
    fun startListening(context: Context)
    fun stopListening()
    fun cancelListening()
    fun simulateSpeechInput(recognizedText: String)
    fun consumeLastEvent()
    fun handlePermissionResult(isGranted: Boolean, isPermanent: Boolean = false)
    fun speak(context: Context, text: String, utteranceId: String)
    fun stopSpeaking()
    fun resetState()
    fun cleanup()
}
