package com.wyrd.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "project_files",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["projectSyncId"]),
        Index(value = ["syncId"])
    ]
)
data class ProjectFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val projectId: Long,
    val projectSyncId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val storagePath: String,
    val textContent: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
