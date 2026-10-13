package com.wyrd.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "projects",
    indices = [Index(value = ["syncId"])]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val systemPrompt: String = "",
    val color: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
