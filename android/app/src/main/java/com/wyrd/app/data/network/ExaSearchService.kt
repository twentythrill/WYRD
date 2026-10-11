package com.wyrd.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ExaSearchItem(
    val url: String,
    val publishedDate: String,
    val highlights: List<String>
)

class ExaSearchService(
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

            // 1. If cleanKey is provided, attempt direct REST search first
            if (!cleanKey.isNullOrBlank()) {
                try {
                    val directPayload = JSONObject().apply {
                        put("query", query)
                        put("numResults", 5)
                        put("contents", JSONObject().apply {
                            put("highlights", true)
                        })
                    }

                    val directRequest = Request.Builder()
                        .url(EXA_DIRECT_ENDPOINT)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-api-key", cleanKey)
                        .post(directPayload.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(directRequest).execute().use { directResp ->
                        if (directResp.isSuccessful) {
                            val bodyStr = directResp.body?.string() ?: ""
                            val json = JSONObject(bodyStr)
                            val rawResults = json.optJSONArray("results")
                            val items = mutableListOf<ExaSearchItem>()

                            if (rawResults != null) {
                                for (i in 0 until rawResults.length()) {
                                    val obj = rawResults.getJSONObject(i)
                                    val url = obj.optString("url", "").trim()
                                    if (url.isBlank()) continue
                                    val publishedDate = obj.optString("publishedDate", obj.optString("published_date", "N/A")).ifBlank { "N/A" }
                                    val highlightsList = mutableListOf<String>()
                                    val hlArray = obj.optJSONArray("highlights")
                                    if (hlArray != null) {
                                        for (h in 0 until hlArray.length()) {
                                            val text = hlArray.optString(h, "").trim()
                                            if (text.isNotBlank()) highlightsList.add(text)
                                        }
                                    } else {
                                        val singleHl = obj.optString("highlight", "").trim()
                                        if (singleHl.isNotBlank()) highlightsList.add(singleHl)
                                    }
                                    items.add(ExaSearchItem(url, publishedDate, highlightsList))
                                }
                            }

                            if (items.isEmpty()) {
                                return@withContext Result.success(ZERO_RESULTS_MESSAGE)
                            }
                            return@withContext Result.success(formatSearchResults(items))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct Exa REST API call failed, falling back to MCP", e)
                }
            }

            // 2. Fallback / Default: Exa MCP Endpoint
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", "web_search_exa")
                    put("arguments", JSONObject().apply {
                        put("query", query)
                        put("numResults", 5)
                        put("contents", JSONObject().apply {
                            put("highlights", true)
                        })
                    })
                })
            }

            val requestBuilder = Request.Builder()
                .url(EXA_MCP_ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader("User-Agent", "WYRD/1.0 (Android; Linux)")

            if (!cleanKey.isNullOrBlank()) {
                requestBuilder.addHeader("x-api-key", cleanKey)
                requestBuilder.addHeader("Authorization", "Bearer $cleanKey")
            }

            val request = requestBuilder
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.w(TAG, "Exa MCP HTTP error (${response.code}): $errBody")
                    return@withContext Result.failure(RuntimeException("Exa MCP HTTP error (${response.code}): $errBody"))
                }

                val body = response.body ?: return@withContext Result.success(ZERO_RESULTS_MESSAGE)
                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                val resultText = StringBuilder()
                var isErrorEncountered = false

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine == "data: [DONE]") break

                    val jsonStr = when {
                        currentLine.startsWith("data:") -> currentLine.removePrefix("data:").trim()
                        currentLine.startsWith("{") && currentLine.endsWith("}") -> currentLine
                        else -> null
                    }
                    if (jsonStr != null && jsonStr != "[DONE]") {
                        try {
                            val json = JSONObject(jsonStr)

                            val errorObj = json.optJSONObject("error")
                            if (errorObj != null) {
                                isErrorEncountered = true
                                break
                            }

                            val resultObj = json.optJSONObject("result")
                            if (resultObj != null) {
                                val isError = resultObj.optBoolean("isError", false)
                                val contentArray = resultObj.optJSONArray("content")
                                if (contentArray != null) {
                                    for (i in 0 until contentArray.length()) {
                                        val item = contentArray.getJSONObject(i)
                                        val text = item.optString("text")
                                        if (text.isNotBlank() && text != "null") {
                                            if (isError ||
                                                text.contains("web_search_exa error", ignoreCase = true) ||
                                                text.contains("Invalid API key", ignoreCase = true) ||
                                                text.contains("rate limit", ignoreCase = true) ||
                                                text.contains("too many requests", ignoreCase = true)
                                            ) {
                                                isErrorEncountered = true
                                                break
                                            }
                                            resultText.append(text).append("\n\n")
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // ignore partial JSON lines
                        }
                    }
                }

                if (isErrorEncountered) {
                    return@withContext Result.failure(RuntimeException("Exa search error encountered in stream"))
                }

                if (resultText.isBlank()) {
                    return@withContext Result.success(ZERO_RESULTS_MESSAGE)
                }

                val items = parseMcpBlocks(resultText.toString())
                if (items.isEmpty()) {
                    return@withContext Result.success(ZERO_RESULTS_MESSAGE)
                }

                Result.success(formatSearchResults(items))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exa search failed", t)
            Result.failure(t)
        }
    }

    private fun parseMcpBlocks(text: String): List<ExaSearchItem> {
        val blocks = text.split(Regex("""\n\s*---\s*\n"""))
        val items = mutableListOf<ExaSearchItem>()

        for (block in blocks) {
            val lines = block.trim().split("\n")
            var url = ""
            var publishedDate = "N/A"
            var inHighlights = false
            val highlights = mutableListOf<String>()

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("URL:", ignoreCase = true)) {
                    url = line.substring(4).trim()
                    inHighlights = false
                } else if (line.startsWith("Published:", ignoreCase = true)) {
                    val d = line.substring(10).trim()
                    if (d.isNotBlank() && !d.equals("null", ignoreCase = true) && !d.equals("n/a", ignoreCase = true)) {
                        publishedDate = d
                    }
                    inHighlights = false
                } else if (line.startsWith("Highlights:", ignoreCase = true)) {
                    inHighlights = true
                } else if (inHighlights) {
                    if (line.startsWith("Title:", ignoreCase = true) ||
                        line.startsWith("URL:", ignoreCase = true) ||
                        line.startsWith("Published:", ignoreCase = true) ||
                        line.startsWith("Author:", ignoreCase = true)
                    ) {
                        inHighlights = false
                    } else if (line != "..." && !line.startsWith("#") && line.length > 3) {
                        highlights.add(line)
                    }
                }
            }

            if (url.isNotBlank() && !url.equals("N/A", ignoreCase = true)) {
                items.add(ExaSearchItem(url, publishedDate, highlights))
            }
        }

        return items
    }

    private fun formatSearchResults(items: List<ExaSearchItem>): String {
        if (items.isEmpty()) {
            return ZERO_RESULTS_MESSAGE
        }
        val blocks = items.mapIndexed { index, item ->
            val highlightsText = if (item.highlights.isNotEmpty()) {
                item.highlights.joinToString("\n") { "- ${it.trim()}" }
            } else {
                "- [No snippet highlights available]"
            }
            """
            [Source ${index + 1}]
            URL: ${item.url}
            Publication Date: ${item.publishedDate.ifBlank { "N/A" }}
            Highlights:
            $highlightsText
            """.trimIndent()
        }
        return "${blocks.joinToString("\n\n")}\n\n$VERIFICATION_CONSTRAINT_TRAILER"
    }

    companion object {
        private const val TAG = "ExaSearchService"
        const val EXA_DIRECT_ENDPOINT = "https://api.exa.ai/search"
        const val EXA_MCP_ENDPOINT = "https://mcp.exa.ai/mcp"
        const val ZERO_RESULTS_MESSAGE =
            "Search returned 0 results. No credible sources or records were found for this query."
        const val VERIFICATION_CONSTRAINT_TRAILER =
            "Verification constraint: Base your answer strictly on the snippets above. If these snippets do not explicitly verify the event, state that no credible sources were found. Do not invent details, extrapolate, or fill in gaps from memory."
    }
}
