package com.example.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.ChatRepository
import com.example.chat.data.MockChatRepository
import com.example.chat.model.ChatMessage
import com.example.chat.model.ChatStreamChunk
import com.example.chat.model.MessageRole
import com.example.chat.model.MessageStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val canCancel: Boolean = false,
    val lastError: String? = null,
    val lastFailedPrompt: String? = null,
    val lastGameContext: String = "Elden Ring"
)

class ChatViewModel(
    private val repository: ChatRepository = MockChatRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = "init_welcome",
                    role = MessageRole.ASSISTANT,
                    content = "Welcome to Game Concierge! Ask for boss strategies, puzzle clues, lore, or collectibles for your active game.",
                    status = MessageStatus.SUCCESS
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _latestCompletedMessage = MutableStateFlow<ChatMessage?>(null)
    val latestCompletedMessage: StateFlow<ChatMessage?> = _latestCompletedMessage.asStateFlow()

    fun consumeLatestCompletedMessage() {
        _latestCompletedMessage.value = null
    }

    private var activeStreamingJob: Job? = null

    fun sendMessage(prompt: String, gameContext: String, tokenDelayMs: Long = 35L) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()

        val userMessage = ChatMessage(
            id = userMessageId,
            role = MessageRole.USER,
            content = trimmed,
            status = MessageStatus.SENT,
            gameContext = gameContext
        )

        val assistantMessage = ChatMessage(
            id = assistantMessageId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
            gameContext = gameContext
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                isGenerating = true,
                canCancel = true,
                lastError = null,
                lastFailedPrompt = null,
                lastGameContext = gameContext
            )
        }

        activeStreamingJob = viewModelScope.launch {
            try {
                val stringBuffer = StringBuilder()

                repository.sendMessageStream(trimmed, gameContext, tokenDelayMs)
                    .collect { chunk ->
                        when (chunk) {
                            is ChatStreamChunk.Token -> {
                                stringBuffer.append(chunk.text)
                                updateAssistantMessage(
                                    id = assistantMessageId,
                                    newContent = stringBuffer.toString(),
                                    status = MessageStatus.STREAMING
                                )
                            }
                            is ChatStreamChunk.Complete -> {
                                val completeContent = stringBuffer.toString()
                                updateAssistantMessage(
                                    id = assistantMessageId,
                                    newContent = completeContent,
                                    status = MessageStatus.SUCCESS
                                )
                                _latestCompletedMessage.value = ChatMessage(
                                    id = assistantMessageId,
                                    role = MessageRole.ASSISTANT,
                                    content = completeContent,
                                    status = MessageStatus.SUCCESS,
                                    gameContext = gameContext
                                )
                                _uiState.update { it.copy(isGenerating = false, canCancel = false) }
                            }
                            is ChatStreamChunk.Error -> {
                                updateAssistantMessage(
                                    id = assistantMessageId,
                                    newContent = stringBuffer.toString().ifEmpty { "Error: Unable to complete guidance." },
                                    status = MessageStatus.ERROR,
                                    errorMessage = chunk.message
                                )
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        canCancel = false,
                                        lastError = chunk.message,
                                        lastFailedPrompt = trimmed
                                    )
                                }
                            }
                            is ChatStreamChunk.Cancelled -> {
                                handleCancellation(assistantMessageId, stringBuffer.toString())
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // User cancelled the streaming job
                val currentText = _uiState.value.messages.find { it.id == assistantMessageId }?.content ?: ""
                handleCancellation(assistantMessageId, currentText)
            } catch (e: Exception) {
                updateAssistantMessage(
                    id = assistantMessageId,
                    newContent = "Error: ${e.localizedMessage ?: "Unexpected error"}",
                    status = MessageStatus.ERROR,
                    errorMessage = e.message
                )
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        canCancel = false,
                        lastError = e.message,
                        lastFailedPrompt = trimmed
                    )
                }
            } finally {
                activeStreamingJob = null
            }
        }
    }

    fun cancelGeneration() {
        activeStreamingJob?.cancel()
        activeStreamingJob = null
    }

    fun retryLastMessage(gameContext: String) {
        val failedPrompt = _uiState.value.lastFailedPrompt ?: return
        // Remove the previous failed assistant message if empty or error
        _uiState.update { state ->
            val cleaned = state.messages.filterNot { it.status == MessageStatus.ERROR && it.role == MessageRole.ASSISTANT }
            state.copy(messages = cleaned, lastError = null)
        }
        sendMessage(failedPrompt, gameContext)
    }

    fun clearHistory() {
        cancelGeneration()
        _uiState.update {
            ChatUiState(
                messages = listOf(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        content = "Chat session reset. Ready for your next query!",
                        status = MessageStatus.SUCCESS
                    )
                ),
                lastGameContext = it.lastGameContext
            )
        }
    }

    private fun updateAssistantMessage(
        id: String,
        newContent: String,
        status: MessageStatus,
        errorMessage: String? = null
    ) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == id) {
                    msg.copy(content = newContent, status = status, errorMessage = errorMessage)
                } else {
                    msg
                }
            }
            state.copy(messages = updated)
        }
    }

    fun analyzeScreenshot(
        bitmap: android.graphics.Bitmap,
        activeGame: String,
        contextRepo: com.example.context.data.GameContextRepository,
        visionRepo: com.example.capture.VisionAnalysisRepository = com.example.capture.MockVisionAnalysisRepository()
    ) {
        val userMsgId = java.util.UUID.randomUUID().toString()
        val assistantMsgId = java.util.UUID.randomUUID().toString()

        val userMessage = ChatMessage(
            id = userMsgId,
            role = MessageRole.USER,
            content = "📸 [Requested visual analysis of current game screen]",
            status = MessageStatus.SENT,
            gameContext = activeGame
        )

        val assistantMessage = ChatMessage(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            content = "Analyzing screenshot capture…",
            status = MessageStatus.STREAMING,
            gameContext = activeGame
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                isGenerating = true,
                canCancel = false
            )
        }

        viewModelScope.launch {
            try {
                val result = visionRepo.analyzeGameScreenshot(bitmap, activeGame)

                // Register unconfirmed visual observations in context engine
                result.visualObservations.forEach { obs ->
                    contextRepo.addVisualObservation(
                        description = obs,
                        confidence = com.example.context.model.ConfidenceLevel.HIGH
                    )
                }

                // Register agent hypothesis in context engine (strictly requiring player confirmation)
                contextRepo.addHypothesis(
                    hypothesis = result.hypothesizedAction,
                    requiresConfirmation = true
                )

                val formattedReport = buildString {
                    append("📸 Visual Analysis Report: ${result.detectedScene}\n\n")
                    append("Observations:\n")
                    result.visualObservations.forEach { append("• $it\n") }
                    append("\nTactical Hypothesis:\n")
                    append(result.hypothesizedAction)
                    append("\n\n⚡ Unconfirmed: Please confirm or correct this deduction in the Context tab.")
                }

                updateAssistantMessage(
                    id = assistantMsgId,
                    newContent = formattedReport,
                    status = MessageStatus.SUCCESS
                )
            } catch (e: Exception) {
                updateAssistantMessage(
                    id = assistantMsgId,
                    newContent = "Visual analysis failed: ${e.message}",
                    status = MessageStatus.ERROR,
                    errorMessage = e.message
                )
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun onScreenshotDenied() {
        _uiState.update { state ->
            val msg = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = "Screen capture consent was denied or cancelled by player. Visual analysis was aborted.",
                status = MessageStatus.CANCELLED
            )
            state.copy(messages = state.messages + msg)
        }
    }

    fun onScreenshotRevoked() {
        _uiState.update { state ->
            val msg = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = "Screen projection was terminated or revoked by Android system.",
                status = MessageStatus.ERROR
            )
            state.copy(messages = state.messages + msg)
        }
    }

    fun onScreenshotError(message: String) {
        _uiState.update { state ->
            val msg = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = "Screen capture failed: $message",
                status = MessageStatus.ERROR,
                errorMessage = message
            )
            state.copy(messages = state.messages + msg)
        }
    }

    private fun handleCancellation(id: String, partialContent: String) {
        val finalText = if (partialContent.isBlank()) {
            "[Request cancelled by player]"
        } else {
            "$partialContent\n\n[Response stopped by player]"
        }
        updateAssistantMessage(id, finalText, MessageStatus.CANCELLED)
        _uiState.update { it.copy(isGenerating = false, canCancel = false) }
    }
}
