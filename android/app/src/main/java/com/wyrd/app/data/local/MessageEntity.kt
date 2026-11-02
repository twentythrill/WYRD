package com.wyrd.app.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // The conversation screen always reads a chat in chronological order. Keeping
    // the sort key in the index avoids a SQLite temp sort as chats grow.
    indices = [
        Index(value = ["chatId", "timestamp"]),
        Index(value = ["syncId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val role: String, // "user", "assistant"
    val content: String,
    val reasoning: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentUri: String? = null,
    val attachmentType: String? = null, // "image", "file"
    val attachmentName: String? = null,
    val attachmentsJson: String? = null,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val modelName: String? = null,
    val reasoningEnabled: Boolean? = null
) {
    fun getAttachmentList(): List<AttachmentItemInfo> {
        if (!attachmentsJson.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(attachmentsJson)
                val list = mutableListOf<AttachmentItemInfo>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        AttachmentItemInfo(
                            uri = obj.getString("uri"),
                            type = obj.getString("type"),
                            name = obj.getString("name"),
                            size = obj.optLong("size", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        if (!attachmentUri.isNullOrBlank()) {
            return listOf(
                AttachmentItemInfo(
                    uri = attachmentUri,
                    type = attachmentType ?: "image",
                    name = attachmentName ?: "attachment"
                )
            )
        }
        return emptyList()
    }
}

@Immutable
data class AttachmentItemInfo(
    val uri: String,
    val type: String, // "image", "file"
    val name: String,
    val size: Long = 0L
)
