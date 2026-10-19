package com.example.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.OverlayStateTransition
import com.example.domain.OverlayUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        VoiceManager.setProvider(AndroidVoiceProvider())
        VoiceManager.resetState()
    }

    @After
    fun tearDown() {
        VoiceManager.cleanup()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle with no active listening or playback`() {
        assertEquals(ListeningState.Idle, VoiceManager.listeningState.value)
        assertEquals(TtsPlaybackState.Idle, VoiceManager.ttsState.value)
        assertNull(VoiceManager.lastEvent.value)
        assertFalse(VoiceManager.autoReadAloud.value)
    }

    @Test
    fun `startListening without RECORD_AUDIO permission transitions to RequestingPermission`() {
        // ShadowApplication does not have RECORD_AUDIO by default
        VoiceManager.startListening(context)

        assertEquals(ListeningState.RequestingPermission, VoiceManager.listeningState.value)
        assertNull(VoiceManager.lastEvent.value)
    }

    @Test
    fun `handlePermissionResult with granted transitions to Ready and NEVER starts listening silently`() {
        // Zero-silent-listening safety guarantee:
        // Even when permission is granted, mic is NOT started automatically
        VoiceManager.handlePermissionResult(isGranted = true)

        assertEquals(ListeningState.Ready, VoiceManager.listeningState.value)
        assertEquals(VoiceEvent.PermissionGranted, VoiceManager.lastEvent.value)
    }

    @Test
    fun `handlePermissionResult with denied transitions to PermissionDenied`() {
        VoiceManager.handlePermissionResult(isGranted = false, isPermanent = false)

        val state = VoiceManager.listeningState.value
        assertTrue(state is ListeningState.PermissionDenied)
        assertFalse((state as ListeningState.PermissionDenied).isPermanent)
        assertEquals(VoiceEvent.PermissionDenied(false), VoiceManager.lastEvent.value)
    }

    @Test
    fun `handlePermissionResult with permanently denied sets isPermanent flag`() {
        VoiceManager.handlePermissionResult(isGranted = false, isPermanent = true)

        val state = VoiceManager.listeningState.value
        assertTrue(state is ListeningState.PermissionDenied)
        assertTrue((state as ListeningState.PermissionDenied).isPermanent)
        assertEquals(VoiceEvent.PermissionDenied(true), VoiceManager.lastEvent.value)
    }

    @Test
    fun `cancelListening cancels speech recognition immediately and transitions to Idle with ListeningCancelled event`() {
        // Simulate an ongoing listening session
        VoiceManager.handlePermissionResult(isGranted = true)
        VoiceManager.cancelListening()

        assertEquals(ListeningState.Idle, VoiceManager.listeningState.value)
        assertEquals(VoiceEvent.ListeningCancelled, VoiceManager.lastEvent.value)
    }

    @Test
    fun `stopListening transitions from Listening to Processing`() {
        // Use a test provider to simulate active listening state
        val mockProvider = FakeVoiceProvider()
        VoiceManager.setProvider(mockProvider)

        mockProvider.simulateActiveListening()
        assertEquals(ListeningState.Listening(soundLevelDb = 5f, partialHypothesis = "Where is"), VoiceManager.listeningState.value)

        VoiceManager.stopListening()
        assertEquals(ListeningState.Processing, VoiceManager.listeningState.value)
    }

    @Test
    fun `simulateSpeechInput emits final transcribed text event and resets to Idle`() {
        VoiceManager.simulateSpeechInput("How do I beat Malenia?")
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(ListeningState.Idle, VoiceManager.listeningState.value)
        val event = VoiceManager.lastEvent.value
        assertTrue(event is VoiceEvent.SpeechTranscribed)
        val transcribed = event as VoiceEvent.SpeechTranscribed
        assertEquals("How do I beat Malenia?", transcribed.text)
        assertTrue(transcribed.isFinal)
    }

    @Test
    fun `consumeLastEvent clears the last emitted voice event`() {
        VoiceManager.handlePermissionResult(isGranted = true)
        assertNotNull(VoiceManager.lastEvent.value)

        VoiceManager.consumeLastEvent()
        assertNull(VoiceManager.lastEvent.value)
    }

    @Test
    fun `speak transitions TTS state to Speaking and cancels active listening if listening`() {
        val mockProvider = FakeVoiceProvider()
        VoiceManager.setProvider(mockProvider)

        mockProvider.simulateActiveListening()
        assertTrue(VoiceManager.listeningState.value is ListeningState.Listening)

        VoiceManager.speak(context, "Roll through the fire breath", "utt_123")

        // Active listening must be cancelled to avoid audio feedback
        assertEquals(ListeningState.Idle, VoiceManager.listeningState.value)
        val tts = VoiceManager.ttsState.value
        assertTrue(tts is TtsPlaybackState.Speaking)
        val speaking = tts as TtsPlaybackState.Speaking
        assertEquals("utt_123", speaking.utteranceId)
        assertEquals("Roll through the fire breath", speaking.textSnippet)
    }

    @Test
    fun `stopSpeaking halts active TTS and transitions to Stopped with TtsCancelled event`() {
        val mockProvider = FakeVoiceProvider()
        VoiceManager.setProvider(mockProvider)

        VoiceManager.speak(context, "Tactical guidance incoming", "utt_456")
        assertTrue(VoiceManager.ttsState.value is TtsPlaybackState.Speaking)

        VoiceManager.stopSpeaking()
        val tts = VoiceManager.ttsState.value
        assertTrue(tts is TtsPlaybackState.Stopped)
        assertEquals("utt_456", (tts as TtsPlaybackState.Stopped).utteranceId)
        assertEquals(VoiceEvent.TtsCancelled("utt_456"), VoiceManager.lastEvent.value)
    }

    @Test
    fun `resetState resets both speech recognition and TTS playback cleanly`() {
        val mockProvider = FakeVoiceProvider()
        VoiceManager.setProvider(mockProvider)

        VoiceManager.speak(context, "Playing audio", "utt_789")
        VoiceManager.resetState()

        assertEquals(ListeningState.Idle, VoiceManager.listeningState.value)
        assertEquals(TtsPlaybackState.Idle, VoiceManager.ttsState.value)
        assertNull(VoiceManager.lastEvent.value)
    }

    @Test
    fun `autoReadAloud toggle updates both VoiceManager and activeProvider`() {
        assertFalse(VoiceManager.autoReadAloud.value)

        VoiceManager.setAutoReadAloud(true)
        assertTrue(VoiceManager.autoReadAloud.value)

        VoiceManager.setAutoReadAloud(false)
        assertFalse(VoiceManager.autoReadAloud.value)
    }

    @Test
    fun `custom VoiceProvider can be injected and replaced cleanly`() {
        val customProvider = FakeVoiceProvider()
        VoiceManager.setProvider(customProvider)

        assertEquals(customProvider, VoiceManager.getProvider())
    }

    @Test
    fun `overlay state machine supports Phase 6 transitions between EXPANDED, LISTENING, THINKING, and SPEAKING`() {
        // Expand
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.EXPANDED, OverlayUiState.LISTENING))
        // Player speaking -> Speech decoding & agent thinking
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.LISTENING, OverlayUiState.THINKING))
        // Agent thinking -> Agent speaking guide aloud
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.THINKING, OverlayUiState.SPEAKING))
        // Agent speaking finishes -> back to expanded panel
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.SPEAKING, OverlayUiState.EXPANDED))
        // Player taps read aloud on existing message directly from EXPANDED
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.EXPANDED, OverlayUiState.SPEAKING))
        // Player interrupts speaking to ask another voice query
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.SPEAKING, OverlayUiState.LISTENING))
    }
}

/**
 * In-memory test double of [VoiceProvider] for deterministic verification without native Android hardware.
 */
class FakeVoiceProvider : VoiceProvider {
    private val _listeningState = MutableStateFlow<ListeningState>(ListeningState.Idle)
    override val listeningState: StateFlow<ListeningState> = _listeningState

    private val _ttsState = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    override val ttsState: StateFlow<TtsPlaybackState> = _ttsState

    private val _lastEvent = MutableStateFlow<VoiceEvent?>(null)
    override val lastEvent: StateFlow<VoiceEvent?> = _lastEvent

    private val _autoReadAloud = MutableStateFlow(false)
    override val autoReadAloud: StateFlow<Boolean> = _autoReadAloud

    private var activeUtteranceId: String? = null

    fun simulateActiveListening() {
        _listeningState.value = ListeningState.Listening(soundLevelDb = 5f, partialHypothesis = "Where is")
    }

    override fun setAutoReadAloud(enabled: Boolean) {
        _autoReadAloud.value = enabled
    }

    override fun hasRecordAudioPermission(context: Context): Boolean = true

    override fun startListening(context: Context) {
        stopSpeaking()
        _listeningState.value = ListeningState.Listening(0f, "")
    }

    override fun stopListening() {
        _listeningState.value = ListeningState.Processing
    }

    override fun cancelListening() {
        _listeningState.value = ListeningState.Idle
        _lastEvent.value = VoiceEvent.ListeningCancelled
    }

    override fun simulateSpeechInput(recognizedText: String) {
        _listeningState.value = ListeningState.Idle
        _lastEvent.value = VoiceEvent.SpeechTranscribed(recognizedText, isFinal = true)
    }

    override fun consumeLastEvent() {
        _lastEvent.value = null
    }

    override fun handlePermissionResult(isGranted: Boolean, isPermanent: Boolean) {
        if (isGranted) {
            _listeningState.value = ListeningState.Ready
            _lastEvent.value = VoiceEvent.PermissionGranted
        } else {
            _listeningState.value = ListeningState.PermissionDenied(isPermanent)
            _lastEvent.value = VoiceEvent.PermissionDenied(isPermanent)
        }
    }

    override fun speak(context: Context, text: String, utteranceId: String) {
        stopSpeaking()
        if (_listeningState.value is ListeningState.Listening) {
            cancelListening()
        }
        activeUtteranceId = utteranceId
        _ttsState.value = TtsPlaybackState.Speaking(utteranceId, text.take(80))
    }

    override fun stopSpeaking() {
        val currentId = activeUtteranceId
        activeUtteranceId = null
        if (_ttsState.value is TtsPlaybackState.Speaking) {
            _ttsState.value = TtsPlaybackState.Stopped(currentId)
            _lastEvent.value = VoiceEvent.TtsCancelled(currentId)
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
        resetState()
    }
}
