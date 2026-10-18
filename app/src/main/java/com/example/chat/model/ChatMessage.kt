package com.example.chat.model

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    SENT,
    STREAMING,
    SUCCESS,
    ERROR,
    CANCELLED
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.SUCCESS,
    val timestamp: Long = System.currentTimeMillis(),
    val gameContext: String? = null,
    val errorMessage: String? = null
)

sealed interface ChatStreamChunk {
    data class Token(val text: String) : ChatStreamChunk
    data object Complete : ChatStreamChunk
    data class Error(val message: String) : ChatStreamChunk
    data object Cancelled : ChatStreamChunk
}
