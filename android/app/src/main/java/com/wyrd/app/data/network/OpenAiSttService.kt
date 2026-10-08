package com.wyrd.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiSttService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun transcribeAudio(
        apiKey: String,
        wavBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenAI API key is missing. Please set it in Settings."))
        }
        if (wavBytes.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Recorded audio is empty."))
        }

        try {
            val audioMediaType = "audio/wav".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "gpt-4o-mini-transcribe")
                .addFormDataPart(
                    "file",
                    "recording.wav",
                    wavBytes.toRequestBody(audioMediaType)
                )
                .build()

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(IllegalStateException("STT request failed (${response.code}): $err"))
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val transcript = json.optString("text", "").trim()
                Result.success(transcript)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
    }
}
