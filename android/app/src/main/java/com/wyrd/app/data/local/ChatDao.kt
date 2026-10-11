package com.wyrd.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE title LIKE '%' || :query || '%' ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    fun searchChats(query: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE projectId = :projectId ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    fun getChatsByProject(projectId: Long): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE projectId IS NULL ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    fun getGenericChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE syncId = :syncId LIMIT 1")
    suspend fun getChatBySyncId(syncId: String): ChatEntity?

    @Query("SELECT * FROM chats ORDER BY isPinned DESC, updatedAt DESC, id DESC")
    suspend fun getAllChatsList(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updatePinned(chatId: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updateTitle(chatId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET projectId = :projectId, projectSyncId = :projectSyncId, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updateProject(chatId: Long, projectId: Long?, projectSyncId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET projectId = NULL, projectSyncId = NULL WHERE projectId = :projectId")
    suspend fun detachChatsFromProject(projectId: Long)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("DELETE FROM chats WHERE isPinned = 0")
    suspend fun deleteAllUnpinnedChats(): Int

    @Query("SELECT COUNT(*) FROM chats WHERE isPinned = 0")
    suspend fun countUnpinnedChats(): Int
}
