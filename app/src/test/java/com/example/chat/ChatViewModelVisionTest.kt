package com.example.chat

import android.graphics.Bitmap
import com.example.capture.MockVisionAnalysisRepository
import com.example.chat.model.MessageRole
import com.example.chat.model.MessageStatus
import com.example.chat.ui.ChatViewModel
import com.example.context.data.DefaultGameContextRepository
import com.example.context.data.InMemoryGameKnowledgeRepository
import com.example.context.model.ObservationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatViewModelVisionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var contextRepo: DefaultGameContextRepository
    private lateinit var visionRepo: MockVisionAnalysisRepository
    private lateinit var testBitmap: Bitmap

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        chatViewModel = ChatViewModel()
        contextRepo = DefaultGameContextRepository(InMemoryGameKnowledgeRepository())
        visionRepo = MockVisionAnalysisRepository()
        testBitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `analyzeScreenshot processes image and streams multimodal guidance into chat and context engine`() = runTest(testDispatcher) {
        chatViewModel.analyzeScreenshot(
            bitmap = testBitmap,
            activeGame = "Elden Ring",
            contextRepo = contextRepo,
            visionRepo = visionRepo
        )

        // Advance coroutines on test dispatcher
        advanceUntilIdle()

        val chatState = chatViewModel.uiState.value
        assertFalse(chatState.isGenerating)

        // Check messages: initial welcome + user capture request + assistant visual analysis
        assertEquals(3, chatState.messages.size)
        val userMsg = chatState.messages[1]
        assertEquals(MessageRole.USER, userMsg.role)
        assertTrue(userMsg.content.contains("visual analysis"))

        val assistantMsg = chatState.messages[2]
        assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
        assertEquals(MessageStatus.SUCCESS, assistantMsg.status)
        assertTrue(assistantMsg.content.contains("Margit the Fell Omen"))
        assertTrue(assistantMsg.content.contains("Visual Analysis Report"))
        assertTrue(assistantMsg.content.contains("Tactical Hypothesis"))

        // Check that visual observations were logged into context engine
        val sessionContext = contextRepo.sessionContext.value
        assertTrue(sessionContext.visualObservations.isNotEmpty())
        assertTrue(sessionContext.visualObservations.all { it.type == ObservationType.VISUAL_OBSERVATION })

        // Check that hypothesis was logged with required player confirmation
        assertTrue(sessionContext.hypotheses.isNotEmpty())
        assertTrue(sessionContext.pendingConfirmations.isNotEmpty())
        val pendingHypothesis = sessionContext.pendingConfirmations.last()
        assertEquals(ObservationType.AGENT_HYPOTHESIS, pendingHypothesis.type)
        assertTrue(pendingHypothesis.content.contains("jumping hammer leap") || pendingHypothesis.content.contains("Roll"))
    }

    @Test
    fun `onScreenshotDenied adds explanatory cancelled message to chat stream`() {
        chatViewModel.onScreenshotDenied()

        val state = chatViewModel.uiState.value
        val deniedMsg = state.messages.last()
        assertEquals(MessageRole.ASSISTANT, deniedMsg.role)
        assertEquals(MessageStatus.CANCELLED, deniedMsg.status)
        assertTrue(deniedMsg.content.contains("consent was denied or cancelled"))
    }

    @Test
    fun `onScreenshotRevoked adds system revoked message with error status`() {
        chatViewModel.onScreenshotRevoked()

        val state = chatViewModel.uiState.value
        val revokedMsg = state.messages.last()
        assertEquals(MessageRole.ASSISTANT, revokedMsg.role)
        assertEquals(MessageStatus.ERROR, revokedMsg.status)
        assertTrue(revokedMsg.content.contains("revoked by Android system") || revokedMsg.content.contains("terminated"))
    }

    @Test
    fun `onScreenshotError records detailed failure message with error status`() {
        val errorMessage = "Display projection permission timeout"
        chatViewModel.onScreenshotError(errorMessage)

        val state = chatViewModel.uiState.value
        val errorMsg = state.messages.last()
        assertEquals(MessageRole.ASSISTANT, errorMsg.role)
        assertEquals(MessageStatus.ERROR, errorMsg.status)
        assertEquals(errorMessage, errorMsg.errorMessage)
        assertTrue(errorMsg.content.contains(errorMessage))
    }
}
