package com.localai.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localai.chat.data.db.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query(
        """
        SELECT * FROM chats
        WHERE isDeleted = 0 AND isArchived = 0
        ORDER BY isPinned DESC, lastMessageAt DESC
        """,
    )
    fun observeRecentChats(): Flow<List<ChatEntity>>

    @Query(
        """
        SELECT DISTINCT c.* FROM chats c
        LEFT JOIN messages m ON m.chatId = c.id AND m.isDeleted = 0
        WHERE c.isDeleted = 0
          AND c.isArchived = 0
          AND (
              c.title LIKE '%' || :query || '%'
              OR m.content LIKE '%' || :query || '%'
              OR m.rawContent LIKE '%' || :query || '%'
          )
        ORDER BY c.isPinned DESC, c.lastMessageAt DESC
        """,
    )
    fun observeChatSearch(query: String): Flow<List<ChatEntity>>

    @Query(
        """
        SELECT * FROM chats
        WHERE isDeleted = 0 AND isArchived = 0
        ORDER BY isPinned DESC, lastMessageAt DESC
        LIMIT 1
        """,
    )
    suspend fun getMostRecentChat(): ChatEntity?

    @Query(
        """
        SELECT c.* FROM chats c
        WHERE c.isDeleted = 0
          AND c.isArchived = 0
          AND NOT EXISTS (
              SELECT 1 FROM messages m
              WHERE m.chatId = c.id AND m.isDeleted = 0
          )
        ORDER BY c.createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun getMostRecentEmptyChat(): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :chatId AND isDeleted = 0 LIMIT 1")
    suspend fun getChat(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Query(
        """
        UPDATE chats
        SET title = :title, updatedAt = :updatedAt
        WHERE id = :chatId
        """,
    )
    suspend fun renameChat(chatId: String, title: String, updatedAt: Long)

    @Query(
        """
        UPDATE chats
        SET lastMessageAt = :lastMessageAt, updatedAt = :updatedAt
        WHERE id = :chatId
        """,
    )
    suspend fun touchChat(chatId: String, lastMessageAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE chats
        SET isDeleted = 1, updatedAt = :updatedAt
        WHERE id = :chatId
        """,
    )
    suspend fun softDeleteChat(chatId: String, updatedAt: Long)

    @Query(
        """
        UPDATE chats
        SET isDeleted = 1, updatedAt = :updatedAt
        WHERE isDeleted = 0
        """,
    )
    suspend fun softDeleteAllChats(updatedAt: Long)
}
