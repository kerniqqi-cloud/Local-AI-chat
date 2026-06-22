package com.localai.chat.domain.context

import com.localai.chat.data.repository.ChatMessage
import com.localai.chat.data.repository.ImageAttachment
import com.localai.chat.data.repository.MessageRole
import com.localai.chat.data.repository.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRequestBuilderTest {
    private val builder = ChatRequestBuilder()

    @Test
    fun firstMessageIncludesSystemPromptAndUserMessage() {
        val result = builder.build(
            systemPrompt = "You are helpful.",
            messages = listOf(message(role = MessageRole.User, content = "Hello")),
        )

        assertEquals(
            listOf(
                OpenAiChatMessage("system", "You are helpful."),
                OpenAiChatMessage("user", "Hello"),
            ),
            result.messages,
        )
    }

    @Test
    fun fullPriorConversationIsPreservedInOrder() {
        val result = builder.build(
            systemPrompt = null,
            messages = listOf(
                message(id = "1", orderIndex = 0, role = MessageRole.User, content = "First"),
                message(id = "2", orderIndex = 1, role = MessageRole.Assistant, content = "Second"),
                message(id = "3", orderIndex = 2, role = MessageRole.User, content = "Third"),
            ),
        )

        assertEquals(
            listOf(
                OpenAiChatMessage("user", "First"),
                OpenAiChatMessage("assistant", "Second"),
                OpenAiChatMessage("user", "Third"),
            ),
            result.messages,
        )
    }

    @Test
    fun deletedMessagesAreExcluded() {
        val result = builder.build(
            systemPrompt = "System",
            messages = listOf(
                message(id = "1", orderIndex = 0, role = MessageRole.User, content = "Keep"),
                message(id = "2", orderIndex = 1, role = MessageRole.Assistant, content = "Drop", isDeleted = true),
                message(id = "3", orderIndex = 2, role = MessageRole.User, content = "Keep too"),
            ),
        )

        assertEquals(
            listOf(
                OpenAiChatMessage("system", "System"),
                OpenAiChatMessage("user", "Keep"),
                OpenAiChatMessage("user", "Keep too"),
            ),
            result.messages,
        )
    }

    @Test
    fun regenerationScopeExcludesTargetAndLaterMessages() {
        val result = builder.build(
            systemPrompt = null,
            stopBeforeMessageId = "assistant-to-regenerate",
            messages = listOf(
                message(id = "1", orderIndex = 0, role = MessageRole.User, content = "Prompt"),
                message(
                    id = "assistant-to-regenerate",
                    orderIndex = 1,
                    role = MessageRole.Assistant,
                    content = "Old answer",
                ),
                message(id = "3", orderIndex = 2, role = MessageRole.User, content = "Later prompt"),
            ),
        )

        assertEquals(
            listOf(OpenAiChatMessage("user", "Prompt")),
            result.messages,
        )
    }

    @Test
    fun imageOnlyUserMessageIsIncluded() {
        val attachment = ImageAttachment(
            id = "attachment-id",
            localPath = "/tmp/image.jpg",
            mimeType = "image/jpeg",
            displayName = "image.jpg",
        )
        val result = builder.build(
            systemPrompt = null,
            messages = listOf(
                message(
                    role = MessageRole.User,
                    content = "",
                    attachments = listOf(attachment),
                ),
            ),
        )

        assertEquals(
            listOf(OpenAiChatMessage("user", "", listOf(attachment))),
            result.messages,
        )
    }

    private fun message(
        id: String = "message-id",
        orderIndex: Long = 0,
        role: MessageRole,
        content: String,
        isDeleted: Boolean = false,
        attachments: List<ImageAttachment> = emptyList(),
    ): ChatMessage = ChatMessage(
        id = id,
        chatId = "chat-id",
        role = role,
        content = content,
        status = MessageStatus.Complete,
        createdAt = orderIndex,
        orderIndex = orderIndex,
        isDeleted = isDeleted,
        attachments = attachments,
    )
}
