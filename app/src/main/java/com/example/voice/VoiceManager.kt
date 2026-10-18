package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Unified Voice and Speech Controller for Game Concierge (Phase 6).
 *
 * Strict Architectural Guarantees:
 * 1. ZERO SILENT LISTENING: The microphone is NEVER activated without explicit, intentional player action.
 * 2. Explicit Permission Handling: RECORD_AUDIO is checked before microphone activation; if missing,
 *    an explicit consent flow is triggered.
 * 3. Bidirectional Voice: Includes SpeechRecognizer for hands-free queries and TextToSpeech for tactical read-alouds.
 * 4. Comprehensive Cancellation: Both ongoing listening and TTS speech output can be cancelled instantly.
 */
object VoiceManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _listeningState = MutableStateFlow<ListeningState>(ListeningState.Idle)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _ttsState = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    val ttsState: StateFlow<TtsPlaybackState> = _ttsState.asStateFlow()

    private val _lastEvent = MutableStateFlow<VoiceEvent?>(null)
    val lastEvent: StateFlow<VoiceEvent?> = _lastEvent.asStateFlow()

    val autoReadAloud = MutableStateFlow(false)

    // Android native engine references
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var currentActiveUtteranceId: String? = null

    /**
     * Checks if RECORD_AUDIO permission is currently granted.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Explicitly initiates speech recognition.
     * Guaranteed: If permission is not granted, microphone will NOT start.
     * Instead, it transitions to RequestingPermission and prompts the user.
     */
    fun startListening(context: Context) {
        if (!hasRecordAudioPermission(context)) {
            _listeningState.value = ListeningState.RequestingPermission
            val intent = Intent(context, VoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        // Cancel any active TTS playback to avoid audio feedback
        stopSpeaking()

        _listeningState.value = ListeningState.Listening(soundLevelDb = 0f, partialHypothesis = "")

        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    // Running in environment or emulator without Google speech services
                    // Keep state in Listening so simulated speech or fallback can occur
                    return@post
                }

                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        // Keep listening
                    }

                    override fun onBeginningOfSpeech() {
                        // User started talking
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val current = _listeningState.value
                        if (current is ListeningState.Listening) {
                            _listeningState.value = current.copy(soundLevelDb = rmsdB.coerceAtLeast(0f))
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _listeningState.value = ListeningState.Processing
                    }

                    override fun onError(errorCode: Int) {
                        val errorDescription = getSpeechErrorDescription(errorCode)
                        _listeningState.value = ListeningState.Error(errorDescription, errorCode)
                        cleanupSpeechRecognizer()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            _listeningState.value = ListeningState.Idle
                            _lastEvent.value = VoiceEvent.SpeechTranscribed(text, isFinal = true)
                        } else {
                            _listeningState.value = ListeningState.Error("No speech could be recognized.")
                        }
                        cleanupSpeechRecognizer()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = partials?.firstOrNull()?.trim() ?: ""
                        val current = _listeningState.value
                        if (current is ListeningState.Listening) {
                            _listeningState.value = current.copy(partialHypothesis = partialText)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                recognizer.startListening(recognizerIntent)
            } catch (e: Exception) {
                _listeningState.value = ListeningState.Error("Failed to start speech listener: ${e.message}")
                cleanupSpeechRecognizer()
            }
        }
    }

    /**
     * Stops listening normally (allowing recognizer to finalize current spoken query).
     */
    fun stopListening() {
        if (_listeningState.value is ListeningState.Listening) {
            _listeningState.value = ListeningState.Processing
            mainHandler.post {
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Cancels speech recognition immediately and releases audio resources.
     */
    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            cleanupSpeechRecognizer()
        }
        _listeningState.value = ListeningState.Idle
        _lastEvent.value = VoiceEvent.ListeningCancelled
    }

    /**
     * Supplies simulated speech input. Useful for unit tests, emulators, and offline sandboxes.
     */
    fun simulateSpeechInput(recognizedText: String) {
        val trimmed = recognizedText.trim()
        if (trimmed.isEmpty()) return

        _listeningState.value = ListeningState.Processing
        mainHandler.postDelayed({
            _listeningState.value = ListeningState.Idle
            _lastEvent.value = VoiceEvent.SpeechTranscribed(trimmed, isFinal = true)
        }, 80)
    }

    /**
     * Consumes the last emitted voice event so it is not processed multiple times.
     */
    fun consumeLastEvent() {
        _lastEvent.value = null
    }

    /**
     * Relays the result of the RECORD_AUDIO permission request.
     * Note: If granted, we set state to Ready, NEVER starting listening silently.
     */
    fun handlePermissionResult(isGranted: Boolean, isPermanent: Boolean = false) {
        if (isGranted) {
            _listeningState.value = ListeningState.Ready
            _lastEvent.value = VoiceEvent.PermissionGranted
        } else {
            _listeningState.value = ListeningState.PermissionDenied(isPermanent)
            _lastEvent.value = VoiceEvent.PermissionDenied(isPermanent)
        }
    }

    /**
     * Synthesizes and plays the provided text aloud through TextToSpeech.
     */
    fun speak(
        context: Context,
        text: String,
        utteranceId: String = UUID.randomUUID().toString()
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        // Stop any ongoing speech or listening
        stopSpeaking()
        if (_listeningState.value is ListeningState.Listening) {
            cancelListening()
        }

        currentActiveUtteranceId = utteranceId
        _ttsState.value = TtsPlaybackState.Speaking(
            utteranceId = utteranceId,
            textSnippet = cleanText.take(80)
        )

        initOrUseTts(context.applicationContext) { tts ->
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    /**
     * Cancels and halts any active speech synthesis immediately.
     */
    fun stopSpeaking() {
        val currentUtterance = currentActiveUtteranceId
        mainHandler.post {
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {}
        }
        currentActiveUtteranceId = null
        if (_ttsState.value is TtsPlaybackState.Speaking) {
            _ttsState.value = TtsPlaybackState.Stopped(currentUtterance)
            _lastEvent.value = VoiceEvent.TtsCancelled(currentUtterance)
        } else {
            _ttsState.value = TtsPlaybackState.Idle
        }
    }

    /**
     * Resets all voice states to IDLE.
     */
    fun resetState() {
        cancelListening()
        stopSpeaking()
        _listeningState.value = ListeningState.Idle
        _ttsState.value = TtsPlaybackState.Idle
        _lastEvent.value = null
    }

    /**
     * Fully cleans up native speech recognizer and TTS instances.
     */
    fun cleanup() {
        cleanupSpeechRecognizer()
        mainHandler.post {
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            } catch (_: Exception) {}
            textToSpeech = null
            isTtsInitialized = false
        }
        _listeningState.value = ListeningState.Idle
        _ttsState.value = TtsPlaybackState.Idle
    }

    private fun cleanupSpeechRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    private fun initOrUseTts(context: Context, onReady: (TextToSpeech) -> Unit) {
        if (textToSpeech != null && isTtsInitialized) {
            textToSpeech?.let { onReady(it) }
            return
        }

        _ttsState.value = TtsPlaybackState.Initializing

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                textToSpeech?.apply {
                    language = Locale.getDefault()
                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            val activeId = utteranceId ?: UUID.randomUUID().toString()
                            _ttsState.value = TtsPlaybackState.Speaking(
                                utteranceId = activeId,
                                textSnippet = "Speaking guide response…"
                            )
                        }

                        override fun onDone(utteranceId: String?) {
                            if (currentActiveUtteranceId == utteranceId) {
                                currentActiveUtteranceId = null
                                _ttsState.value = TtsPlaybackState.Idle
                                utteranceId?.let {
                                    _lastEvent.value = VoiceEvent.TtsCompleted(it)
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            if (currentActiveUtteranceId == utteranceId) {
                                currentActiveUtteranceId = null
                                _ttsState.value = TtsPlaybackState.Error("TTS synthesis encountered an error")
                            }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (currentActiveUtteranceId == utteranceId) {
                                currentActiveUtteranceId = null
                                _ttsState.value = TtsPlaybackState.Stopped(utteranceId)
                                _lastEvent.value = VoiceEvent.TtsCancelled(utteranceId)
                            }
                        }
                    })
                }
                textToSpeech?.let { onReady(it) }
            } else {
                isTtsInitialized = false
                _ttsState.value = TtsPlaybackState.Error("TTS initialization failed on this device.")
            }
        }
    }

    private fun getSpeechErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission missing."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech decoding."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during speech recognition."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech could be recognized. Try speaking closer."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech server encountered an error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Listening closed."
            else -> "Speech recognition error code: $errorCode"
        }
    }
}
