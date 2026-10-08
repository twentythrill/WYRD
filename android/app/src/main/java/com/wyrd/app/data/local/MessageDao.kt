package com.wyrd.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesListForChat(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    suspend fun getRecentMessagesForChat(chatId: Long, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE syncId = :syncId LIMIT 1")
    suspend fun getMessageBySyncId(syncId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND (timestamp > :timestamp OR (timestamp = :timestamp AND id >= :messageId))")
    suspend fun deleteMessagesFrom(chatId: Long, timestamp: Long, messageId: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND (timestamp > :timestamp OR (timestamp = :timestamp AND id > :messageId))")
    suspend fun deleteMessagesAfter(chatId: Long, timestamp: Long, messageId: Long)

    @Query("SELECT * FROM messages")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE chatId NOT IN (SELECT id FROM chats)")
    suspend fun deleteOrphanedMessages(): Int
}
