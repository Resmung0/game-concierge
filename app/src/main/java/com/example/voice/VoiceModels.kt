package com.example.voice

/**
 * Explicit states representing the speech recognition lifecycle.
 * Strict safety requirement: The microphone is never started silently or automatically.
 */
sealed class ListeningState {
    /**
     * Idle state. Microphone hardware is completely inactive and closed.
     */
    data object Idle : ListeningState()

    /**
     * User explicitly requested voice input, but RECORD_AUDIO permission is required.
     */
    data object RequestingPermission : ListeningState()

    /**
     * Permission granted and hardware is primed, but microphone is NOT listening yet.
     * Waiting for the user's explicit gesture.
     */
    data object Ready : ListeningState()

    /**
     * Active listening state initiated exclusively by explicit user tap.
     * @param soundLevelDb Live audio volume (RMS dB) for reactive waveform UI.
     * @param partialHypothesis Real-time intermediate transcription results.
     */
    data class Listening(
        val soundLevelDb: Float = 0f,
        val partialHypothesis: String = ""
    ) : ListeningState()

    /**
     * Speech ended; recognizer engine is finalizing acoustic and language decoding.
     */
    data object Processing : ListeningState()

    /**
     * Speech recognition encountered an error or timed out without speech.
     */
    data class Error(val message: String, val errorCode: Int? = null) : ListeningState()

    /**
     * User declined the RECORD_AUDIO runtime permission request.
     */
    data class PermissionDenied(val isPermanent: Boolean = false) : ListeningState()
}

/**
 * Explicit states representing the TextToSpeech playback lifecycle.
 */
sealed class TtsPlaybackState {
    /**
     * TTS engine is idle; no audio output is being generated.
     */
    data object Idle : TtsPlaybackState()

    /**
     * TTS engine is initializing or loading the language voice model.
     */
    data object Initializing : TtsPlaybackState()

    /**
     * Actively synthesizing and streaming spoken audio to device speakers.
     * @param utteranceId Unique ID of the current spoken phrase.
     * @param textSnippet Preview of the text currently being read aloud.
     */
    data class Speaking(
        val utteranceId: String,
        val textSnippet: String
    ) : TtsPlaybackState()

    /**
     * Playback was explicitly stopped or interrupted by the player.
     */
    data class Stopped(val utteranceId: String? = null) : TtsPlaybackState()

    /**
     * TTS synthesis failed or language is unavailable.
     */
    data class Error(val message: String) : TtsPlaybackState()
}

/**
 * Discrete events emitted during speech recognition and speech synthesis sessions.
 */
sealed class VoiceEvent {
    data class SpeechTranscribed(val text: String, val isFinal: Boolean) : VoiceEvent()
    data object ListeningCancelled : VoiceEvent()
    data class TtsCompleted(val utteranceId: String) : VoiceEvent()
    data class TtsCancelled(val utteranceId: String?) : VoiceEvent()
    data object PermissionGranted : VoiceEvent()
    data class PermissionDenied(val isPermanent: Boolean) : VoiceEvent()
}
