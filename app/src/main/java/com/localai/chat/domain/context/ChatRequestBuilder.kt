package com.localai.chat.domain.context

import com.localai.chat.data.repository.ChatMessage
import com.localai.chat.data.repository.MessageRole
import javax.inject.Inject
import kotlin.math.ceil

class ChatRequestBuilder @Inject constructor() {
    fun build(
        systemPrompt: String?,
        messages: List<ChatMessage>,
        stopBeforeMessageId: String? = null,
    ): ChatRequestContext {
        val orderedMessages = messages.sortedWith(compareBy<ChatMessage> { it.orderIndex }.thenBy { it.createdAt })
        val scopedMessages = if (stopBeforeMessageId == null) {
            orderedMessages
        } else {
            orderedMessages.takeWhile { it.id != stopBeforeMessageId }
        }

        val requestMessages = buildList {
            systemPrompt
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { add(OpenAiChatMessage(role = "system", content = it)) }

            scopedMessages
                .asSequence()
                .filterNot(ChatMessage::isDeleted)
                .filter { it.content.isNotBlank() || it.attachments.isNotEmpty() }
                .map { message ->
                    OpenAiChatMessage(
                        role = when (message.role) {
                            MessageRole.User -> "user"
                            MessageRole.Assistant -> "assistant"
                        },
                        content = message.content,
                        attachments = if (message.role == MessageRole.User) message.attachments else emptyList(),
                    )
                }
                .forEach(::add)
        }

        val contextChars = requestMessages.sumOf { it.content.length }
        return ChatRequestContext(
            messages = requestMessages,
            approxContextChars = contextChars,
            approxContextTokens = estimateTokens(contextChars),
        )
    }

    private fun estimateTokens(charCount: Int): Int {
        if (charCount <= 0) return 0
        return ceil(charCount / 4.0).toInt()
    }
}

data class ChatRequestContext(
    val messages: List<OpenAiChatMessage>,
    val approxContextChars: Int,
    val approxContextTokens: Int,
)

data class OpenAiChatMessage(
    val role: String,
    val content: String,
    val attachments: List<com.localai.chat.data.repository.ImageAttachment> = emptyList(),
)
