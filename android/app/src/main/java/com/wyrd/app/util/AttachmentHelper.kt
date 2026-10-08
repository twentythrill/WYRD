package com.wyrd.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject
import com.wyrd.app.data.local.MessageEntity
import kotlin.math.max
import kotlin.math.min

data class AttachedItem(
    val uriString: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val base64Data: String? = null,
    val textContent: String? = null
)

object AttachmentHelper {

    suspend fun processUri(context: Context, uri: Uri): AttachedItem? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = "attachment"
            var sizeBytes = 0L

            // 1. Query display name and size from content resolver
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex) ?: displayName
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                uri.lastPathSegment?.let { displayName = it }
            }

            var mimeType = contentResolver.getType(uri) ?: guessMimeType(displayName)

            val isImage = mimeType.startsWith("image/") ||
                    listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic").any {
                        displayName.endsWith(it, ignoreCase = true)
                    }

            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localFile = File(attachmentsDir, "attach_${System.currentTimeMillis()}_$safeName")

            // Copy file from content resolver locally first (single-read safe)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val fileSize = if (sizeBytes > 0) sizeBytes else localFile.length()

            if (isImage) {
                // Decode bitmap reliably with modern ImageDecoder or BitmapFactory fallback
                val decodedBitmap = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(localFile)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        val original = BitmapFactory.decodeFile(localFile.absolutePath)
                        if (original != null) {
                            val exif = ExifInterface(localFile.absolutePath)
                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            rotateBitmap(original, orientation)
                        } else null
                    }
                } catch (_: Throwable) {
                    val fallback = BitmapFactory.decodeFile(localFile.absolutePath)
                    if (fallback != null) {
                        try {
                            val exif = ExifInterface(localFile.absolutePath)
                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            rotateBitmap(fallback, orientation)
                        } catch (_: Throwable) { fallback }
                    } else null
                }

                if (decodedBitmap == null) return@withContext null

                // Scale down if either dimension exceeds 1280px
                val maxDim = 1280
                val width = decodedBitmap.width
                val height = decodedBitmap.height
                val scale = min(1f, maxDim.toFloat() / max(width, height))

                val scaledBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        decodedBitmap,
                        (width * scale).toInt(),
                        (height * scale).toInt(),
                        true
                    )
                } else {
                    decodedBitmap
                }

                // Save locally as JPEG
                val bos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, bos)
                val jpegBytes = bos.toByteArray()

                FileOutputStream(localFile).use { it.write(jpegBytes) }
                val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                return@withContext AttachedItem(
                    uriString = localFile.absolutePath,
                    name = displayName,
                    mimeType = "image/jpeg",
                    sizeBytes = localFile.length(),
                    isImage = true,
                    base64Data = base64,
                    textContent = null
                )
            } else {
                // Non-image file: text, code, document, or PDF
                val isPdf = displayName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"
                val textContent = if (isPdf) {
                    "[Attached PDF Document: $displayName (${formatSize(fileSize)})]"
                } else {
                    getTextContentFromPath(localFile.absolutePath) ?: "[Attached File: $displayName (${formatSize(fileSize)})]"
                }

                return@withContext AttachedItem(
                    uriString = localFile.absolutePath,
                    name = displayName,
                    mimeType = mimeType,
                    sizeBytes = fileSize,
                    isImage = false,
                    base64Data = null,
                    textContent = textContent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun isTextMimeType(mimeType: String, name: String): Boolean {
        if (mimeType.startsWith("text/")) return true
        if (mimeType in listOf(
                "application/json", "application/xml", "application/javascript",
                "application/x-javascript", "application/x-sh", "application/x-python"
            )
        ) return true

        val textExtensions = listOf(
            ".txt", ".md", ".json", ".xml", ".csv", ".tsv", ".yaml", ".yml",
            ".py", ".kt", ".java", ".c", ".cpp", ".h", ".hpp", ".cs", ".go",
            ".rs", ".js", ".ts", ".jsx", ".tsx", ".html", ".css", ".scss",
            ".sh", ".bash", ".zsh", ".sql", ".gradle", ".properties", ".env",
            ".ini", ".conf", ".log"
        )
        return textExtensions.any { name.endsWith(it, ignoreCase = true) }
    }

    private fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    fun createCameraTempUri(context: Context): Pair<Uri, File>? {
        return try {
            val cacheDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val tempFile = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
            Pair(uri, tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getBase64FromPath(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists()) {
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getTextContentFromPath(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists()) {
                if (file.name.endsWith(".pdf", ignoreCase = true)) {
                    return "[Attached PDF Document: ${file.name} (${formatSize(file.length())})]"
                }
                val bytes = file.readBytes()
                if (bytes.isEmpty()) return "[Attached File: ${file.name} (Empty)]"
                val sampleSize = minOf(bytes.size, 4096)
                var hasNullByte = false
                for (i in 0 until sampleSize) {
                    if (bytes[i] == 0.toByte()) {
                        hasNullByte = true
                        break
                    }
                }
                if (!hasNullByte) {
                    val text = String(bytes, Charsets.UTF_8)
                    if (text.length > 80_000) {
                        text.take(80_000) + "\n\n... [Content truncated due to length] ..."
                    } else {
                        text
                    }
                } else {
                    "[Attached Binary File: ${file.name} (${formatSize(file.length())})]"
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getPdfPageImagesBase64(path: String, maxPages: Int = 3): List<String> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: android.graphics.pdf.PdfRenderer? = null
        return try {
            val file = File(path)
            if (!file.exists() || !file.name.endsWith(".pdf", ignoreCase = true)) return emptyList()
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, maxPages)
            val result = mutableListOf<String>()
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val maxDim = 1280f
                val scale = minOf(maxDim / page.width, maxDim / page.height, 2.0f).coerceAtLeast(1.0f)
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, bos)
                val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                result.add(b64)
            }
            result
        } catch (e: Exception) {
            emptyList()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun getFormattedSizeFromPath(path: String): String {
        return try {
            val file = File(path)
            if (file.exists()) formatSize(file.length()) else ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun processUris(context: Context, uris: List<Uri>): List<AttachedItem> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> processUri(context, uri) }
    }

    fun serializeAttachments(items: List<AttachedItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("uri", item.uriString)
                put("type", if (item.isImage) "image" else "file")
                put("name", item.name)
                put("size", item.sizeBytes)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun cleanupAttachments(context: Context, messages: List<MessageEntity>) {
        try {
            val attachmentsDir = File(context.filesDir, "attachments")
            for (msg in messages) {
                msg.attachmentUri?.let { uriStr ->
                    deleteLocalFileIfApplicable(uriStr, attachmentsDir)
                }
                if (!msg.attachmentsJson.isNullOrBlank()) {
                    try {
                        val arr = JSONArray(msg.attachmentsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val uriStr = obj.optString("uri", "")
                            deleteLocalFileIfApplicable(uriStr, attachmentsDir)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AttachmentHelper", "Error cleaning up local attachments", e)
        }
    }

    private fun deleteLocalFileIfApplicable(uriStr: String, attachmentsDir: File) {
        if (uriStr.isBlank() || uriStr.startsWith("http://") || uriStr.startsWith("https://")) return
        val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
        try {
            val f = File(path)
            if (f.exists() && (f.parentFile?.absolutePath == attachmentsDir.absolutePath || f.absolutePath.contains("/attachments/"))) {
                f.delete()
            }
        } catch (_: Exception) {}
    }
}
