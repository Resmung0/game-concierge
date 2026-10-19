package com.example.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified Voice and Speech Controller for Game Concierge (Phase 6).
 *
 * Implements the facade pattern delegating to a replaceable [VoiceProvider].
 *
 * Architectural Guarantees:
 * 1. ZERO SILENT LISTENING: The microphone is NEVER activated without explicit, intentional player action.
 * 2. Explicit Permission Handling: RECORD_AUDIO is checked before microphone activation; if missing,
 *    an explicit consent flow is triggered.
 * 3. Replaceable Provider: Can swap between native Android SpeechRecognizer/TextToSpeech and
 *    future real-time audio services or test mocks seamlessly.
 * 4. Bidirectional Voice: Includes SpeechRecognizer for hands-free queries and TextToSpeech for tactical read-alouds.
 * 5. Comprehensive Cancellation: Both ongoing listening and TTS speech output can be cancelled instantly.
 */
object VoiceManager : VoiceProvider {

    private var activeProvider: VoiceProvider = AndroidVoiceProvider()

    /**
     * Swaps the active voice provider. Essential for injecting real-time streaming providers
     * or mock implementations for automated tests.
     */
    fun setProvider(provider: VoiceProvider) {
        activeProvider.cleanup()
        activeProvider = provider
    }

    /**
     * Gets the currently active voice provider.
     */
    fun getProvider(): VoiceProvider = activeProvider

    // Auto read aloud state toggle for spoken assistant responses
    val autoReadAloud = MutableStateFlow(false)

    override val listeningState: StateFlow<ListeningState>
        get() = activeProvider.listeningState

    override val ttsState: StateFlow<TtsPlaybackState>
        get() = activeProvider.ttsState

    override val lastEvent: StateFlow<VoiceEvent?>
        get() = activeProvider.lastEvent

    override val autoReadAloud: StateFlow<Boolean>
        get() = autoReadAloud

    override fun setAutoReadAloud(enabled: Boolean) {
        autoReadAloud.value = enabled
        activeProvider.setAutoReadAloud(enabled)
    }

    override fun hasRecordAudioPermission(context: Context): Boolean {
        return activeProvider.hasRecordAudioPermission(context)
    }

    override fun startListening(context: Context) {
        activeProvider.startListening(context)
    }

    override fun stopListening() {
        activeProvider.stopListening()
    }

    override fun cancelListening() {
        activeProvider.cancelListening()
    }

    override fun simulateSpeechInput(recognizedText: String) {
        activeProvider.simulateSpeechInput(recognizedText)
    }

    override fun consumeLastEvent() {
        activeProvider.consumeLastEvent()
    }

    override fun handlePermissionResult(isGranted: Boolean, isPermanent: Boolean) {
        activeProvider.handlePermissionResult(isGranted, isPermanent)
    }

    override fun speak(context: Context, text: String, utteranceId: String) {
        activeProvider.speak(context, text, utteranceId)
    }

    override fun stopSpeaking() {
        activeProvider.stopSpeaking()
    }

    override fun resetState() {
        activeProvider.resetState()
    }

    override fun cleanup() {
        activeProvider.cleanup()
    }
}
