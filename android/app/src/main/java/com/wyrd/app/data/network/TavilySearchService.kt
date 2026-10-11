package com.wyrd.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TavilySearchItem(
    val title: String,
    val url: String,
    val content: String,
    val publishedDate: String
)

class TavilySearchService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun searchWeb(
        query: String,
        apiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = apiKey?.trim()
            if (cleanKey.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Tavily API key is missing. Please configure your Tavily API key in Settings.")
                )
            }

            val payload = JSONObject().apply {
                put("api_key", cleanKey)
                put("query", query)
                put("search_depth", "basic")
                put("max_results", 5)
                put("include_answer", false)
            }

            val request = Request.Builder()
                .url(TAVILY_API_ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.w(TAG, "Tavily HTTP error (${response.code}): $errBody")
                    return@withContext Result.failure(
                        RuntimeException("Tavily search failed (${response.code}): $errBody")
                    )
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val rawResults = json.optJSONArray("results")
                val items = mutableListOf<TavilySearchItem>()

                if (rawResults != null) {
                    for (i in 0 until rawResults.length()) {
                        val obj = rawResults.getJSONObject(i)
                        val url = obj.optString("url", "").trim()
                        if (url.isBlank()) continue
                        val title = obj.optString("title", "Untitled").trim()
                        val content = obj.optString("content", "").trim()
                        val publishedDate = obj.optString("published_date", "Recent").trim().ifBlank { "Recent" }

                        items.add(TavilySearchItem(title, url, content, publishedDate))
                    }
                }

                if (items.isEmpty()) {
                    return@withContext Result.success(ZERO_RESULTS_MESSAGE)
                }

                return@withContext Result.success(formatSearchResults(items))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Tavily search failed", t)
            Result.failure(t)
        }
    }

    private fun formatSearchResults(items: List<TavilySearchItem>): String {
        return buildString {
            items.forEachIndexed { index, item ->
                if (index > 0) append("\n\n")
                append("[Source ${index + 1}]\n")
                append("Title: ${item.title}\n")
                append("URL: ${item.url}\n")
                append("Publication Date: ${item.publishedDate}\n")
                append("Content Snippet:\n")
                append(item.content.ifBlank { "[No content snippet available]" })
            }
            append("\n\n")
            append(VERIFICATION_CONSTRAINT_TRAILER)
        }
    }

    companion object {
        private const val TAG = "TavilySearchService"
        const val TAVILY_API_ENDPOINT = "https://api.tavily.com/search"

        const val ZERO_RESULTS_MESSAGE =
            "Tavily search returned 0 results. No live web sources or records were found for this query."

        const val VERIFICATION_CONSTRAINT_TRAILER =
            "Verification constraint: Base your answer strictly on the live web snippets above. If these snippets do not explicitly verify the event or claim, state that no credible sources were found. Do not invent details, extrapolate, or fill in gaps from memory."
    }
}
