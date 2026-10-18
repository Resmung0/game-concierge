package com.example.chat

import com.example.chat.data.MockChatRepository
import com.example.chat.model.MessageRole
import com.example.chat.model.MessageStatus
import com.example.chat.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatViewModel(MockChatRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state contains welcome message and idle status`() {
        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals(MessageRole.ASSISTANT, state.messages.first().role)
        assertEquals(MessageStatus.SUCCESS, state.messages.first().status)
        assertFalse(state.isGenerating)
        assertFalse(state.canCancel)
    }

    @Test
    fun `sendMessage appends user message and streams assistant tokens to completion`() = runTest {
        viewModel.sendMessage(
            prompt = "How do I defeat the boss?",
            gameContext = "Elden Ring",
            tokenDelayMs = 5L
        )

        // Immediately after sending, user message is SENT and assistant message is STREAMING
        val initialMessages = viewModel.uiState.value.messages
        assertEquals(3, initialMessages.size)
        assertEquals(MessageRole.USER, initialMessages[1].role)
        assertEquals("How do I defeat the boss?", initialMessages[1].content)
        assertEquals(MessageRole.ASSISTANT, initialMessages[2].role)
        assertEquals(MessageStatus.STREAMING, initialMessages[2].status)
        assertTrue(viewModel.uiState.value.isGenerating)
        assertTrue(viewModel.uiState.value.canCancel)

        // Advance coroutine clock through all emitted tokens
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        val finalAssistant = finalState.messages[2]
        assertEquals(MessageStatus.SUCCESS, finalAssistant.status)
        assertTrue(finalAssistant.content.contains("Elden Ring"))
        assertTrue(finalAssistant.content.contains("Tactical Guidance"))
        assertFalse(finalState.isGenerating)
        assertFalse(finalState.canCancel)
    }

    @Test
    fun `cancelGeneration stops active streaming and updates status to CANCELLED`() = runTest {
        viewModel.sendMessage(
            prompt = "Tell me about area collectibles",
            gameContext = "Hollow Knight",
            tokenDelayMs = 50L
        )

        // Advance partially to receive some tokens
        advanceTimeBy(120L)
        assertTrue(viewModel.uiState.value.isGenerating)

        // Cancel mid-stream
        viewModel.cancelGeneration()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertFalse(state.canCancel)

        val assistantMessage = state.messages.last()
        assertEquals(MessageStatus.CANCELLED, assistantMessage.status)
        assertTrue(assistantMessage.content.contains("stopped by player") || assistantMessage.content.contains("cancelled"))
    }

    @Test
    fun `simulated error triggers error state and retry succeeds`() = runTest {
        viewModel.sendMessage(
            prompt = "Simulate failure !error",
            gameContext = "Zelda: TotK",
            tokenDelayMs = 5L
        )

        advanceUntilIdle()

        val errorState = viewModel.uiState.value
        assertFalse(errorState.isGenerating)
        assertNotNull(errorState.lastError)
        assertEquals("Simulate failure !error", errorState.lastFailedPrompt)

        val failedAssistantMsg = errorState.messages.last()
        assertEquals(MessageStatus.ERROR, failedAssistantMsg.status)

        // Now test retry with valid query
        viewModel.sendMessage("Legitimate boss query", "Zelda: TotK", tokenDelayMs = 5L)
        advanceUntilIdle()

        val retriedState = viewModel.uiState.value
        assertFalse(retriedState.isGenerating)
        val successfulAssistantMsg = retriedState.messages.last()
        assertEquals(MessageStatus.SUCCESS, successfulAssistantMsg.status)
        assertTrue(successfulAssistantMsg.content.contains("Tears of the Kingdom"))
    }

    @Test
    fun `clearHistory resets messages to initial clean state`() = runTest {
        viewModel.sendMessage("First question", "Elden Ring", tokenDelayMs = 5L)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.messages.size)

        viewModel.clearHistory()

        val clearedState = viewModel.uiState.value
        assertEquals(1, clearedState.messages.size)
        assertEquals(MessageRole.ASSISTANT, clearedState.messages.first().role)
        assertTrue(clearedState.messages.first().content.contains("reset"))
        assertFalse(clearedState.isGenerating)
    }
}
