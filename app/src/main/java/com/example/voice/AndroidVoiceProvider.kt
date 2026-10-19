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
 * Native Android implementation of [VoiceProvider] using Android's built-in
 * [SpeechRecognizer] and [TextToSpeech] APIs.
 *
 * Enforces all Phase 6 requirements:
 * 1. Zero silent listening: Microphone is NEVER activated automatically or silently.
 * 2. Explicit permission handling with transparent trampoline activity.
 * 3. Bidirectional voice support with mutual acoustic isolation.
 * 4. Instant cancellation for both listening and playback.
 * 5. Graceful handling when speech services are unavailable.
 */
class AndroidVoiceProvider(
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : VoiceProvider {

    private val _listeningState = MutableStateFlow<ListeningState>(ListeningState.Idle)
    override val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _ttsState = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    override val ttsState: StateFlow<TtsPlaybackState> = _ttsState.asStateFlow()

    private val _lastEvent = MutableStateFlow<VoiceEvent?>(null)
    override val lastEvent: StateFlow<VoiceEvent?> = _lastEvent.asStateFlow()

    private val _autoReadAloud = MutableStateFlow(false)
    override val autoReadAloud: StateFlow<Boolean> = _autoReadAloud.asStateFlow()

    override fun setAutoReadAloud(enabled: Boolean) {
        _autoReadAloud.value = enabled
    }

    // Android native engine references
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var currentActiveUtteranceId: String? = null
    private val pendingTtsActions = mutableListOf<(TextToSpeech) -> Unit>()

    override fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun startListening(context: Context) {
        // Enforce explicit permission check before microphone hardware is accessed
        if (!hasRecordAudioPermission(context)) {
            _listeningState.value = ListeningState.RequestingPermission
            val intent = Intent(context, VoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        // Cancel any active TTS playback to avoid audio feedback into the microphone
        stopSpeaking()

        _listeningState.value = ListeningState.Listening(soundLevelDb = 0f, partialHypothesis = "")

        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _listeningState.value = ListeningState.Error(
                        "Speech recognition is not available on this device."
                    )
                    return@post
                }

                cleanupSpeechRecognizer()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onBeginningOfSpeech() {}

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

    override fun stopListening() {
        if (_listeningState.value is ListeningState.Listening) {
            _listeningState.value = ListeningState.Processing
            mainHandler.post {
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Exception) {}
            }
        }
    }

    override fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            cleanupSpeechRecognizer()
        }
        _listeningState.value = ListeningState.Idle
        _lastEvent.value = VoiceEvent.ListeningCancelled
    }

    override fun simulateSpeechInput(recognizedText: String) {
        val trimmed = recognizedText.trim()
        if (trimmed.isEmpty()) return

        _listeningState.value = ListeningState.Processing
        mainHandler.postDelayed({
            _listeningState.value = ListeningState.Idle
            _lastEvent.value = VoiceEvent.SpeechTranscribed(trimmed, isFinal = true)
        }, 80)
    }

    override fun consumeLastEvent() {
        _lastEvent.value = null
    }

    override fun handlePermissionResult(isGranted: Boolean, isPermanent: Boolean) {
        if (isGranted) {
            // Strict safety invariant: Ready state, NEVER start listening automatically/silently
            _listeningState.value = ListeningState.Ready
            _lastEvent.value = VoiceEvent.PermissionGranted
        } else {
            _listeningState.value = ListeningState.PermissionDenied(isPermanent)
            _lastEvent.value = VoiceEvent.PermissionDenied(isPermanent)
        }
    }

    override fun speak(context: Context, text: String, utteranceId: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        // Mutual acoustic isolation: Stop ongoing speech and cancel listening
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

    override fun stopSpeaking() {
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

    override fun resetState() {
        cancelListening()
        stopSpeaking()
        _listeningState.value = ListeningState.Idle
        _ttsState.value = TtsPlaybackState.Idle
        _lastEvent.value = null
    }

    override fun cleanup() {
        cleanupSpeechRecognizer()
        mainHandler.post {
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            } catch (_: Exception) {}
            textToSpeech = null
            isTtsInitialized = false
            pendingTtsActions.clear()
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
        val tts = textToSpeech
        if (tts != null && isTtsInitialized) {
            onReady(tts)
            return
        }

        if (_ttsState.value is TtsPlaybackState.Initializing && textToSpeech != null) {
            pendingTtsActions.add(onReady)
            return
        }

        _ttsState.value = TtsPlaybackState.Initializing
        pendingTtsActions.add(onReady)

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                val engine = textToSpeech
                if (engine != null) {
                    val langResult = engine.setLanguage(Locale.getDefault())
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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

                    while (pendingTtsActions.isNotEmpty()) {
                        val action = pendingTtsActions.removeAt(0)
                        action(engine)
                    }
                }
            } else {
                isTtsInitialized = false
                pendingTtsActions.clear()
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
            11 -> "Speech server disconnected." // ERROR_SERVER_DISCONNECTED (API 31+)
            12 -> "Language not supported for speech." // ERROR_LANGUAGE_NOT_SUPPORTED (API 31+)
            13 -> "Language data unavailable for speech." // ERROR_LANGUAGE_UNAVAILABLE (API 31+)
            14 -> "Too many speech requests. Please wait." // ERROR_TOO_MANY_REQUESTS (API 31+)
            else -> "Speech recognition error code: $errorCode"
        }
    }
}
