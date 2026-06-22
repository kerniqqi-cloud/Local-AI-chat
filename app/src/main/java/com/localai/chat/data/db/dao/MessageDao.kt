package com.localai.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localai.chat.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages
        WHERE chatId = :chatId AND isDeleted = 0
        ORDER BY orderIndex ASC, createdAt ASC
        """,
    )
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE chatId = :chatId
        ORDER BY orderIndex ASC, createdAt ASC
        """,
    )
    suspend fun getMessagesForRequest(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM messages WHERE chatId = :chatId")
    suspend fun getMaxOrderIndex(chatId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query(
        """
        UPDATE messages
        SET content = :content,
            rawContent = :rawContent,
            status = :status,
            updatedAt = :updatedAt,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            generationDurationMs = :generationDurationMs
        WHERE id = :messageId
        """,
    )
    suspend fun updateMessageContent(
        messageId: String,
        content: String,
        rawContent: String,
        status: String,
        updatedAt: Long,
        errorCode: String?,
        errorMessage: String?,
        generationDurationMs: Long?,
    )

    @Query(
        """
        UPDATE messages
        SET status = :interruptedStatus,
            updatedAt = :updatedAt,
            errorCode = 'generation_interrupted',
            errorMessage = :errorMessage
        WHERE status = :streamingStatus
        """,
    )
    suspend fun markStreamingMessagesInterrupted(
        streamingStatus: String,
        interruptedStatus: String,
        updatedAt: Long,
        errorMessage: String,
    )

    @Query(
        """
        UPDATE messages
        SET content = :content,
            rawContent = :content,
            status = :status,
            updatedAt = :updatedAt,
            errorCode = NULL,
            errorMessage = NULL
        WHERE id = :messageId AND role = :role
        """,
    )
    suspend fun updateMessageForRole(
        messageId: String,
        role: String,
        content: String,
        status: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE messages
        SET isDeleted = 1, updatedAt = :updatedAt
        WHERE chatId = :chatId AND orderIndex > :orderIndex
        """,
    )
    suspend fun softDeleteMessagesAfter(chatId: String, orderIndex: Long, updatedAt: Long)

    @Query(
        """
        UPDATE messages
        SET isDeleted = 1, updatedAt = :updatedAt
        WHERE id = :messageId
        """,
    )
    suspend fun softDeleteMessage(messageId: String, updatedAt: Long)

    @Query(
        """
        UPDATE messages
        SET isDeleted = 1, updatedAt = :updatedAt
        WHERE chatId = :chatId
        """,
    )
    suspend fun softDeleteMessagesForChat(chatId: String, updatedAt: Long)

    @Query(
        """
        UPDATE messages
        SET isDeleted = 1, updatedAt = :updatedAt
        WHERE isDeleted = 0
        """,
    )
    suspend fun softDeleteAllMessages(updatedAt: Long)
}
