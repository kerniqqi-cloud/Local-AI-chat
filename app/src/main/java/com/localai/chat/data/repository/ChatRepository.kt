package com.localai.chat.data.repository

import com.localai.chat.data.db.dao.ChatDao
import com.localai.chat.data.db.dao.MessageDao
import com.localai.chat.data.db.entities.ChatEntity
import com.localai.chat.data.db.entities.MessageEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
) {
    fun observeRecentChats(): Flow<List<Chat>> = chatDao.observeRecentChats()
        .map { chats -> chats.map { it.toModel() } }

    fun observeChatSearch(query: String): Flow<List<Chat>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            chatDao.observeRecentChats()
        } else {
            chatDao.observeChatSearch(trimmed)
        }.map { chats -> chats.map { it.toModel() } }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = messageDao.observeMessages(chatId)
        .map { messages -> messages.map { it.toModel() } }

    suspend fun getMessagesForRequest(chatId: String): List<ChatMessage> {
        return messageDao.getMessagesForRequest(chatId).map { it.toModel() }
    }

    suspend fun getMessage(messageId: String): ChatMessage? {
        return messageDao.getMessage(messageId)?.toModel()
    }

    suspend fun getOrCreateInitialChat(): String {
        val existing = chatDao.getMostRecentChat()
        if (existing != null) return existing.id

        return createChat()
    }

    suspend fun getOrCreateFreshChat(): String {
        return chatDao.getMostRecentEmptyChat()?.id ?: createChat()
    }

    suspend fun createChat(title: String = "New chat"): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        chatDao.upsert(
            ChatEntity(
                id = id,
                title = title,
                createdAt = now,
                updatedAt = now,
                lastMessageAt = now,
            ),
        )
        return id
    }

    suspend fun deleteChat(chatId: String) {
        val now = System.currentTimeMillis()
        chatDao.softDeleteChat(chatId, now)
        messageDao.softDeleteMessagesForChat(chatId, now)
    }

    suspend fun deleteAllChats() {
        val now = System.currentTimeMillis()
        chatDao.softDeleteAllChats(now)
        messageDao.softDeleteAllMessages(now)
    }

    suspend fun renameChat(chatId: String, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Chat title cannot be blank." }
        chatDao.renameChat(chatId, trimmed, System.currentTimeMillis())
    }

    suspend fun markStaleStreamingMessagesInterrupted() {
        messageDao.markStreamingMessagesInterrupted(
            streamingStatus = MessageStatus.Streaming.name,
            interruptedStatus = MessageStatus.Interrupted.name,
            updatedAt = System.currentTimeMillis(),
            errorMessage = "Generation was interrupted before completion.",
        )
    }

    suspend fun addUserMessage(
        chatId: String,
        content: String,
        attachments: List<ImageAttachment> = emptyList(),
    ): String {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty() || attachments.isNotEmpty()) { "Message content cannot be blank." }

        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val orderIndex = messageDao.getMaxOrderIndex(chatId) + 1

        messageDao.upsert(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                role = MessageRole.User.name,
                content = trimmed,
                rawContent = trimmed,
                status = MessageStatus.Complete.name,
                createdAt = now,
                updatedAt = now,
                orderIndex = orderIndex,
                attachmentsJson = attachments.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) },
            ),
        )
        chatDao.touchChat(chatId, lastMessageAt = now, updatedAt = now)

        val chat = chatDao.getChat(chatId)
        if (chat?.title == "New chat" && trimmed.isNotBlank()) {
            chatDao.renameChat(chatId, trimmed.toChatTitle(), now)
        }

        return messageId
    }

    suspend fun addAssistantMessage(
        chatId: String,
        content: String,
        rawContent: String = content,
        status: MessageStatus = MessageStatus.Complete,
        errorMessage: String? = null,
        generationDurationMs: Long? = null,
    ): String {
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val orderIndex = messageDao.getMaxOrderIndex(chatId) + 1

        messageDao.upsert(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                role = MessageRole.Assistant.name,
                content = content,
                rawContent = rawContent,
                status = status.name,
                createdAt = now,
                updatedAt = now,
                orderIndex = orderIndex,
                errorCode = if (status == MessageStatus.Failed) "chat_completion_failed" else null,
                errorMessage = errorMessage,
                generationDurationMs = generationDurationMs,
            ),
        )
        chatDao.touchChat(chatId, lastMessageAt = now, updatedAt = now)
        return messageId
    }

    suspend fun updateAssistantMessage(
        messageId: String,
        content: String,
        rawContent: String = content,
        status: MessageStatus,
        errorMessage: String? = null,
        generationDurationMs: Long? = null,
    ) {
        messageDao.updateMessageContent(
            messageId = messageId,
            content = content,
            rawContent = rawContent,
            status = status.name,
            updatedAt = System.currentTimeMillis(),
            errorCode = if (status == MessageStatus.Failed || status == MessageStatus.Interrupted) {
                "chat_completion_failed"
            } else {
                null
            },
            errorMessage = errorMessage,
            generationDurationMs = generationDurationMs,
        )
    }

    suspend fun editUserMessage(
        messageId: String,
        newContent: String,
    ) {
        val trimmed = newContent.trim()
        require(trimmed.isNotEmpty()) { "Message content cannot be blank." }
        val message = messageDao.getMessage(messageId)?.toModel() ?: return
        require(message.role == MessageRole.User) { "Only user messages can be edited." }

        val now = System.currentTimeMillis()
        messageDao.updateMessageForRole(
            messageId = messageId,
            role = MessageRole.User.name,
            content = trimmed,
            status = MessageStatus.Complete.name,
            updatedAt = now,
        )
        messageDao.softDeleteMessagesAfter(message.chatId, message.orderIndex, now)
        chatDao.touchChat(message.chatId, lastMessageAt = now, updatedAt = now)
    }

    suspend fun prepareAssistantRegeneration(messageId: String): ChatMessage? {
        val message = messageDao.getMessage(messageId)?.toModel() ?: return null
        require(message.role == MessageRole.Assistant) { "Only assistant messages can be regenerated." }

        val now = System.currentTimeMillis()
        messageDao.softDeleteMessagesAfter(message.chatId, message.orderIndex, now)
        messageDao.updateMessageContent(
            messageId = messageId,
            content = "",
            rawContent = "",
            status = MessageStatus.Streaming.name,
            updatedAt = now,
            errorCode = null,
            errorMessage = null,
            generationDurationMs = null,
        )
        chatDao.touchChat(message.chatId, lastMessageAt = now, updatedAt = now)
        return message
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.softDeleteMessage(messageId, System.currentTimeMillis())
    }

    private fun String.toChatTitle(): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= MaxTitleLength) normalized else normalized.take(MaxTitleLength).trim() + "..."
    }

    private fun ChatEntity.toModel(): Chat = Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastMessageAt = lastMessageAt,
        isPinned = isPinned,
    )

    private fun MessageEntity.toModel(): ChatMessage = ChatMessage(
        id = id,
        chatId = chatId,
        role = enumValueOfOrDefault(role, MessageRole.User),
        content = content,
        rawContent = rawContent,
        status = enumValueOfOrDefault(status, MessageStatus.Complete),
        createdAt = createdAt,
        orderIndex = orderIndex,
        isDeleted = isDeleted,
        generationDurationMs = generationDurationMs,
        attachments = attachmentsJson?.let { json ->
            runCatching { Json.decodeFromString<List<ImageAttachment>>(json) }.getOrDefault(emptyList())
        } ?: emptyList(),
    )

    private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }

    private companion object {
        const val MaxTitleLength = 56
    }
}
