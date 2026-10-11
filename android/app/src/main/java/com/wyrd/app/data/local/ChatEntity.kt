package com.wyrd.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["projectSyncId"])
    ]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val syncId: String = UUID.randomUUID().toString(),
    val projectId: Long? = null,
    val projectSyncId: String? = null
)
