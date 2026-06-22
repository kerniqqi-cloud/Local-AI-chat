package com.localai.chat.data.repository

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
    val isPinned: Boolean,
)

data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val rawContent: String = content,
    val status: MessageStatus,
    val createdAt: Long,
    val orderIndex: Long,
    val isDeleted: Boolean = false,
    val generationDurationMs: Long? = null,
    val attachments: List<ImageAttachment> = emptyList(),
)

enum class MessageRole {
    User,
    Assistant,
}

enum class MessageStatus {
    Complete,
    Streaming,
    Interrupted,
    Failed,
    Cancelled,
}
