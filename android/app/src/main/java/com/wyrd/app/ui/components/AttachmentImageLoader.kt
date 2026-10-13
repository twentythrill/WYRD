package com.wyrd.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wyrd.app.WyrdApp
import com.wyrd.app.ui.theme.DarkSurfaceElevated
import com.wyrd.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AttachmentImageLoader {
    private val memoryCache = object : LruCache<String, ImageBitmap>(32) {}

    private val fallbackHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun getCached(key: String): ImageBitmap? = memoryCache.get(key)

    suspend fun loadBitmap(
        context: Context,
        uri: String,
        maxDimensionPx: Int = 1280
    ): ImageBitmap? {
        if (uri.isBlank()) return null
        val cacheKey = "$uri@$maxDimensionPx"
        memoryCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val decoded: Bitmap? = when {
                uri.startsWith("data:image/", ignoreCase = true) -> {
                    decodeDataUri(uri, maxDimensionPx)
                }
                uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
                    loadRemoteBitmap(context, uri, maxDimensionPx)
                }
                else -> {
                    val file = File(uri)
                    if (file.exists()) decodeSubsampledFile(file.absolutePath, maxDimensionPx) else null
                }
            }

            decoded?.asImageBitmap()?.also { imageBitmap ->
                memoryCache.put(cacheKey, imageBitmap)
            }
        }
    }

    private fun decodeDataUri(dataUri: String, maxDimensionPx: Int): Bitmap? {
        return try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex == -1) return null
            val base64Part = dataUri.substring(commaIndex + 1)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            decodeSubsampledBytes(bytes, maxDimensionPx)
        } catch (e: Exception) {
            Log.w("AttachmentImageLoader", "Failed to decode data:image URI", e)
            null
        }
    }

    private fun loadRemoteBitmap(context: Context, uri: String, maxDimensionPx: Int): Bitmap? {
        val safeHash = try {
            val md = MessageDigest.getInstance("MD5")
            md.digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            uri.hashCode().toString()
        }
        val cacheDir = File(context.cacheDir, "image_cache").apply { mkdirs() }
        val cachedFile = File(cacheDir, safeHash)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            decodeSubsampledFile(cachedFile.absolutePath, maxDimensionPx)?.let { return it }
        }

        return try {
            val client = (context.applicationContext as? WyrdApp)?.okHttpClient ?: fallbackHttpClient
            val req = Request.Builder().url(uri).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                try {
                    cachedFile.writeBytes(bytes)
                } catch (_: Exception) {}
                decodeSubsampledBytes(bytes, maxDimensionPx)
            }
        } catch (e: Exception) {
            Log.w("AttachmentImageLoader", "Failed to load image from URL: $uri", e)
            null
        }
    }

    private fun decodeSubsampledFile(path: String, maxDimensionPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSubsampledBytes(bytes: ByteArray, maxDimensionPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        if (width > maxDim || height > maxDim) {
            val halfW = width / 2
            val halfH = height / 2
            while ((halfW / inSampleSize) >= maxDim || (halfH / inSampleSize) >= maxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

@Composable
fun rememberAttachmentBitmap(uri: String, maxDimensionPx: Int = 256): ImageBitmap? {
    val context = LocalContext.current
    val initial = remember(uri, maxDimensionPx) {
        AttachmentImageLoader.getCached("$uri@$maxDimensionPx")
    }
    val state = produceState(initialValue = initial, key1 = uri, key2 = maxDimensionPx) {
        if (value == null && uri.isNotBlank()) {
            value = AttachmentImageLoader.loadBitmap(context, uri, maxDimensionPx)
        }
    }
    return state.value
}

fun openAttachmentUri(context: Context, uri: String, isImage: Boolean = true) {
    try {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            context.startActivity(intent)
        } else if (!uri.startsWith("data:")) {
            val file = File(uri)
            if (file.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val type = if (isImage) "image/*" else (context.contentResolver.getType(contentUri) ?: "*/*")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, type)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
    } catch (e: Exception) {
        Log.w("AttachmentImageLoader", "Could not open attachment: $uri", e)
    }
}

@Composable
fun AttachmentImageView(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bm = rememberAttachmentBitmap(uri = uri, maxDimensionPx = 1280)
    if (bm != null) {
        Image(
            bitmap = bm,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(DarkSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = TextMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
