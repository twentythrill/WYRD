package com.wyrd.app.data.network

import android.util.Log
import com.wyrd.app.data.preferences.ReasoningEffort
import com.wyrd.app.data.preferences.SupportedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class Reasoning(val text: String) : StreamEvent()
    data class Status(val status: String) : StreamEvent()
    object ClearContent : StreamEvent()
}

data class ChatMessagePayload(
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
    val imagesBase64: List<String> = emptyList()
)

/**
 * Robust streaming parser that seamlessly demuxes:
 * 1. Thinking tags (<think>, <thought>, <thinking>) to onReasoning
 * 2. Tool call blocks (<tool_call>, <function_call>, <tool>) to an internal tool buffer
 * 3. Actual user response content to onContent
 *
 * Prevents tool call XML and thinking blocks from leaking into user-facing content.
 */
class ThinkAndToolParser(
    private val onContent: suspend (String) -> Unit,
    private val onReasoning: suspend (String) -> Unit
) {
    private var inThink = false
    private var inTool = false
    private var wasInThinkBeforeTool = false
    private val buffer = StringBuilder()
    private val toolCallBuffer = StringBuilder()

    private val thinkOpenTags = listOf("<think>", "<thought>", "<thinking>")
    private val thinkCloseTags = listOf("</think>", "</thought>", "</thinking>")
    private val toolOpenPrefixes = listOf("<tool_call", "<function_call", "<tool>", "<tool ")
    private val toolCloseTags = listOf("</tool_call>", "</function_call>", "</tool>")

    fun getToolCallContent(): String = toolCallBuffer.toString().trim()

    suspend fun feed(chunk: String) {
        buffer.append(chunk)

        while (buffer.isNotEmpty()) {
            if (inTool) {
                // Inside tool call: search for any tool closing tag
                var closeIdx = -1
                var closeTagLen = 0
                for (tag in toolCloseTags) {
                    val idx = buffer.indexOf(tag)
                    if (idx != -1 && (closeIdx == -1 || idx < closeIdx)) {
                        closeIdx = idx
                        closeTagLen = tag.length
                    }
                }

                if (closeIdx != -1) {
                    toolCallBuffer.append(buffer.substring(0, closeIdx + closeTagLen))
                    buffer.delete(0, closeIdx + closeTagLen)
                    inTool = false
                    inThink = wasInThinkBeforeTool
                    continue
                } else {
                    // Check if end of buffer might be a partial prefix of a close tag
                    val prefixLen = getPrefixLen(buffer, toolCloseTags)
                    val safeLen = buffer.length - prefixLen
                    if (safeLen > 0) {
                        toolCallBuffer.append(buffer.substring(0, safeLen))
                        buffer.delete(0, safeLen)
                    }
                    break
                }
            } else {
                // Not in tool call. Find earliest occurring tag
                var firstTagIdx = -1
                var tagType = TagType.NONE
                var matchedTagStr = ""

                // 1. Tool open prefixes
                for (prefix in toolOpenPrefixes) {
                    val idx = buffer.indexOf(prefix)
                    if (idx != -1 && (firstTagIdx == -1 || idx < firstTagIdx)) {
                        firstTagIdx = idx
                        tagType = TagType.TOOL_OPEN
                        matchedTagStr = prefix
                    }
                }

                // 2. Think open tags (if not already in think)
                if (!inThink) {
                    for (tag in thinkOpenTags) {
                        val idx = buffer.indexOf(tag)
                        if (idx != -1 && (firstTagIdx == -1 || idx < firstTagIdx)) {
                            firstTagIdx = idx
                            tagType = TagType.THINK_OPEN
                            matchedTagStr = tag
                        }
                    }
                    // Lone think close tag
                    for (tag in thinkCloseTags) {
                        val idx = buffer.indexOf(tag)
                        if (idx != -1 && (firstTagIdx == -1 || idx < firstTagIdx)) {
                            firstTagIdx = idx
                            tagType = TagType.LONE_THINK_CLOSE
                            matchedTagStr = tag
                        }
                    }
                } else {
                    // In think: check think close tags
                    for (tag in thinkCloseTags) {
                        val idx = buffer.indexOf(tag)
                        if (idx != -1 && (firstTagIdx == -1 || idx < firstTagIdx)) {
                            firstTagIdx = idx
                            tagType = TagType.THINK_CLOSE
                            matchedTagStr = tag
                        }
                    }
                }

                if (firstTagIdx != -1) {
                    // Emit text before the tag
                    val before = buffer.substring(0, firstTagIdx)
                    if (before.isNotEmpty()) {
                        if (inThink) onReasoning(before) else onContent(before)
                    }

                    when (tagType) {
                        TagType.TOOL_OPEN -> {
                            val closeBracketIdx = buffer.indexOf(">", firstTagIdx)
                            if (closeBracketIdx == -1) {
                                // Keep tag prefix in buffer until full opening tag > arrives
                                buffer.delete(0, firstTagIdx)
                                break
                            }
                            wasInThinkBeforeTool = inThink
                            inTool = true
                            toolCallBuffer.append(buffer.substring(firstTagIdx, closeBracketIdx + 1))
                            buffer.delete(0, closeBracketIdx + 1)
                            continue
                        }
                        TagType.THINK_OPEN -> {
                            buffer.delete(0, firstTagIdx + matchedTagStr.length)
                            inThink = true
                            continue
                        }
                        TagType.LONE_THINK_CLOSE, TagType.THINK_CLOSE -> {
                            buffer.delete(0, firstTagIdx + matchedTagStr.length)
                            inThink = false
                            while (buffer.startsWith("\n") || buffer.startsWith("\r")) {
                                buffer.delete(0, 1)
                            }
                            continue
                        }
                        TagType.NONE -> break
                    }
                } else {
                    // No complete tag found. Check prefix lengths so we don't emit a partial tag
                    val candidates = mutableListOf<String>().apply {
                        addAll(toolOpenPrefixes)
                        if (inThink) {
                            addAll(thinkCloseTags)
                        } else {
                            addAll(thinkOpenTags)
                            addAll(thinkCloseTags)
                        }
                    }
                    val prefixLen = getPrefixLen(buffer, candidates)
                    val safeLen = buffer.length - prefixLen
                    if (safeLen > 0) {
                        val safeText = buffer.substring(0, safeLen)
                        if (inThink) onReasoning(safeText) else onContent(safeText)
                        buffer.delete(0, safeLen)
                    }
                    break
                }
            }
        }
    }

    suspend fun flush() {
        if (inTool) {
            toolCallBuffer.append(buffer)
            buffer.clear()
        } else if (buffer.isNotEmpty()) {
            val rem = buffer.toString()
            buffer.clear()
            val trimmed = rem.trim()
            if (!trimmed.startsWith("<think") && !trimmed.startsWith("<tool") && !trimmed.startsWith("<function")) {
                if (inThink) onReasoning(rem) else onContent(rem)
            }
        }
    }

    private fun getPrefixLen(sb: StringBuilder, candidateTags: List<String>): Int {
        var maxLen = 0
        for (tag in candidateTags) {
            for (len in minOf(sb.length, tag.length - 1) downTo 1) {
                if (tag.startsWith(sb.substring(sb.length - len))) {
                    if (len > maxLen) maxLen = len
                    break
                }
            }
        }
        return maxLen
    }

    private enum class TagType {
        NONE, THINK_OPEN, THINK_CLOSE, LONE_THINK_CLOSE, TOOL_OPEN
    }
}

class XiaomiMimoService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Reasoning models can pause while planning. A short socket timeout made
        // otherwise healthy Android connections look like interrupted replies.
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var lastWorkingEndpoint: String? = null

    fun streamChatCompletion(
        apiKey: String,
        model: SupportedModel,
        isThinkingEnabled: Boolean = true,
        systemPrompt: String,
        messages: List<ChatMessagePayload>,
        exaSearchService: ExaSearchService? = null,
        exaApiKey: String? = null,
        isExaEnabled: Boolean = true,
        tavilySearchService: TavilySearchService? = null,
        tavilyApiKey: String? = null,
        customBaseUrl: String? = null
    ): Flow<StreamEvent> = flow {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotBlank()) { "Xiaomi MiMo API key is missing. Please set it in Settings." }

        emit(StreamEvent.Status("Sending..."))

        // Temporal grounding
        val currentDateTimeStr = try {
            val now = java.time.ZonedDateTime.now()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm:ss (z)")
            now.format(formatter)
        } catch (e: Throwable) {
            java.text.SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        }
        val temporalContext = "Current Device Date and Time: $currentDateTimeStr"

        val hasTavily = tavilySearchService != null
        val hasExa = exaSearchService != null && isExaEnabled
        val hasAnySearch = true

        val effectiveSystemPrompt = buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt)
                append("\n\n")
            }
            append("[Context: $temporalContext]")
            if (hasAnySearch) {
                append("\n[Autonomous Web Search: You have access to a web search tool to ground your answers in verified external facts. You are in charge of deciding autonomously whether to use web search.")
                append("\n- `web_search`: Searches the web for live facts, current events, official documentation, community discussions, and user reviews. Invoke with:\n<tool_call><function=web_search><parameter=query>your search query</parameter></function></tool_call>")
                append("\nIf the request does not require external search, answer directly without invoking tools. Never tell the user to use manual search commands like /search.]")
            }
        }

        // Build standard OpenAI messages array (trim history to last 30 turns if needed)
        val messagesJsonArray = JSONArray()
        if (effectiveSystemPrompt.isNotBlank()) {
            messagesJsonArray.put(JSONObject().apply {
                put("role", "system")
                put("content", effectiveSystemPrompt)
            })
        }
        val historyToInclude = if (messages.size > 30) messages.takeLast(30) else messages
        for (msg in historyToInclude) {
            val allImages = if (msg.imagesBase64.isNotEmpty()) {
                msg.imagesBase64
            } else if (!msg.imageBase64.isNullOrBlank()) {
                listOf(msg.imageBase64)
            } else {
                emptyList()
            }

            if (msg.role == "user" && allImages.isNotEmpty()) {
                val parts = JSONArray().apply {
                    val contentStr = msg.content.ifBlank { "Please describe the attached image(s)." }
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", contentStr)
                    })
                    for (b64 in allImages) {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                val mime = msg.imageMimeType ?: "image/jpeg"
                                val url = if (b64.startsWith("http://") || b64.startsWith("https://") || b64.startsWith("data:")) {
                                    b64
                                } else {
                                    "data:$mime;base64,$b64"
                                }
                                put("url", url)
                            })
                        })
                    }
                }
                messagesJsonArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", parts)
                })
            } else {
                val contentStr = msg.content.ifBlank { "..." }
                messagesJsonArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", contentStr)
                })
            }
        }

        val candidateEndpoints = getCandidateEndpoints(cleanKey, customBaseUrl)
        val candidateModels = getCandidateModelIds(model)
        val webTools = if (hasAnySearch) getWebSearchTools() else null

        var currentMessages = messagesJsonArray
        var toolExecutionRounds = 0
        val maxToolExecutionRounds = 3

        while (toolExecutionRounds <= maxToolExecutionRounds) {
            var lastErrorMsg = "Unable to connect to Xiaomi MiMo API"
            var tokenEmittedCount = 0
            var toolCallId = ""
            var toolCallName = ""
            val toolCallArgs = StringBuilder()
            val streamedContentAccumulator = StringBuilder()
            var isStreamSuccess = false
            var activeParser: ThinkAndToolParser? = null

            // Provide webTools while under maxToolExecutionRounds (and no multimodal images in conversation); in final round, suppress tools so model generates final answer
            val hasImagesInConversation = historyToInclude.any { it.imagesBase64.isNotEmpty() || !it.imageBase64.isNullOrBlank() }
            val currentWebTools = if (toolExecutionRounds < maxToolExecutionRounds && hasAnySearch && !hasImagesInConversation) webTools else null

            // Try streaming first across endpoints and models
            outerStream@ for (endpoint in candidateEndpoints) {
                for (candidateId in candidateModels) {
                    val payloads = buildCandidatePayloads(
                        candidateId = candidateId,
                        messagesJsonArray = currentMessages,
                        isThinkingEnabled = isThinkingEnabled,
                        stream = true,
                        tools = currentWebTools
                    )
                    for (payload in payloads) {
                        val thinkingInfo = payload.optJSONObject("thinking")?.optString("type") ?: "none"
                        Log.d(TAG, "Attempting stream endpoint: $endpoint with model: $candidateId, isThinkingEnabled=$isThinkingEnabled, thinking=$thinkingInfo")

                        val request = Request.Builder()
                            .url(endpoint)
                            .addHeader("Authorization", "Bearer $cleanKey")
                            .addHeader("api-key", cleanKey)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "text/event-stream")
                            .post(payload.toString().toRequestBody(jsonMediaType))
                            .build()

                        var response: okhttp3.Response? = null
                        try {
                            response = client.newCall(request).execute()
                            if (!response.isSuccessful) {
                                val code = response.code
                                val errBody = response.body?.string() ?: ""
                                response.close()
                                lastErrorMsg = "HTTP $code from $endpoint: $errBody"
                                Log.w(TAG, "Request failed: $lastErrorMsg")
                                if (code == 401 || code == 403) {
                                    break
                                }
                                continue
                            }

                            val body = response.body
                            if (body == null) {
                                response.close()
                                continue
                            }

                            val reader = BufferedReader(InputStreamReader(body.byteStream()))
                            val parser = ThinkAndToolParser(
                                onContent = {
                                    tokenEmittedCount++
                                    streamedContentAccumulator.append(it)
                                    emit(StreamEvent.Content(it))
                                },
                                onReasoning = {
                                    if (isThinkingEnabled) {
                                        tokenEmittedCount++
                                        emit(StreamEvent.Reasoning(it))
                                    }
                                }
                            )
                            activeParser = parser

                            try {
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    val currentLine = line?.trim() ?: continue
                                    if (currentLine.isEmpty() || currentLine.startsWith(":")) continue

                                    val jsonStr = if (currentLine.startsWith("data:")) {
                                        val raw = currentLine.removePrefix("data:").trim()
                                        if (raw == "[DONE]") break
                                        raw
                                    } else if (currentLine.startsWith("{") && currentLine.endsWith("}")) {
                                        currentLine
                                    } else {
                                        continue
                                    }

                                    try {
                                        val json = JSONObject(jsonStr)
                                        if (json.has("error")) {
                                            val errObj = json.optJSONObject("error")
                                            val msg = errObj?.optString("message") ?: json.optString("error")
                                            throw IllegalStateException("API error: $msg")
                                        }

                                        val choices = json.optJSONArray("choices")
                                        if (choices != null && choices.length() > 0) {
                                            val choice = choices.getJSONObject(0)
                                            val delta = choice.optJSONObject("delta")
                                            if (delta != null) {
                                                val reasoningToken = when {
                                                    !delta.isNull("reasoning_content") -> delta.optString("reasoning_content", "")
                                                    !delta.isNull("reasoning") -> delta.optString("reasoning", "")
                                                    else -> ""
                                                }
                                                if (isThinkingEnabled && reasoningToken.isNotEmpty() && reasoningToken != "null") {
                                                    tokenEmittedCount++
                                                    emit(StreamEvent.Reasoning(reasoningToken))
                                                }

                                                val deltaToolCalls = delta.optJSONArray("tool_calls")
                                                if (deltaToolCalls != null && deltaToolCalls.length() > 0) {
                                                    for (tIdx in 0 until deltaToolCalls.length()) {
                                                        val tc = deltaToolCalls.getJSONObject(tIdx)
                                                        val id = tc.optString("id", "")
                                                        if (id.isNotBlank()) toolCallId = id
                                                        val fn = tc.optJSONObject("function")
                                                        if (fn != null) {
                                                            val fnName = fn.optString("name", "")
                                                            if (fnName.isNotBlank()) toolCallName = fnName
                                                            val argsChunk = fn.optString("arguments", "")
                                                            toolCallArgs.append(argsChunk)
                                                        }
                                                    }
                                                }

                                                if (!delta.isNull("content")) {
                                                    val contentToken = delta.optString("content", "")
                                                    if (contentToken.isNotEmpty() && contentToken != "null") {
                                                        parser.feed(contentToken)
                                                    }
                                                }
                                            } else {
                                                val msg = choice.optJSONObject("message")
                                                val content = msg?.optString("content", "") ?: choice.optString("text", "")
                                                if (content.isNotEmpty() && content != "null") {
                                                    parser.feed(content)
                                                }
                                                val msgToolCalls = msg?.optJSONArray("tool_calls")
                                                if (msgToolCalls != null && msgToolCalls.length() > 0) {
                                                    val firstTc = msgToolCalls.getJSONObject(0)
                                                    toolCallId = firstTc.optString("id", "call_1")
                                                    val fn = firstTc.optJSONObject("function")
                                                    toolCallName = fn?.optString("name", "") ?: ""
                                                    toolCallArgs.append(fn?.optString("arguments", "") ?: "")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        if (e is IllegalStateException && e.message?.startsWith("API error:") == true) {
                                            throw e
                                        }
                                    }
                                }
                                parser.flush()
                            } finally {
                                reader.close()
                                response.close()
                            }

                            if (tokenEmittedCount > 0 || toolCallName.isNotBlank()) {
                                lastWorkingEndpoint = endpoint
                                isStreamSuccess = true
                                break@outerStream
                            } else {
                                Log.w(TAG, "Stream ended with 0 tokens. Testing fallback...")
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            lastErrorMsg = e.message ?: "Stream network exception"
                            Log.w(TAG, "Stream attempt error: $lastErrorMsg", e)
                        } finally {
                            try { response?.close() } catch (ignored: Throwable) {}
                        }
                    }
                }
            }

            if (!isStreamSuccess && tokenEmittedCount == 0 && toolCallName.isBlank()) {
                throw IllegalStateException(lastErrorMsg)
            }

            // Check if model autonomously invoked web search tool (via native tool calls, buffered tool call XML, or content)
            var toolCall: ExtractedToolCall? = null
            val explicitTool = toolCallName.ifBlank { null }
            if (toolCallArgs.isNotBlank()) {
                toolCall = extractToolInvocation(toolCallArgs.toString(), explicitToolName = explicitTool)
            }
            if (toolCall == null && activeParser != null) {
                toolCall = extractToolInvocation(activeParser.getToolCallContent(), explicitToolName = explicitTool)
            }
            if (toolCall == null) {
                toolCall = extractToolInvocation(streamedContentAccumulator.toString(), explicitToolName = explicitTool)
            }

            if (toolCall != null && hasAnySearch && toolExecutionRounds < maxToolExecutionRounds) {
                toolExecutionRounds++

                val statusPrefix = if (toolExecutionRounds > 1) "Gathering additional data for " else "Searching web for "
                val shortQuery = if (toolCall.query.length > 40) toolCall.query.take(37) + "..." else toolCall.query
                emit(StreamEvent.ClearContent)
                emit(StreamEvent.Status("$statusPrefix\"$shortQuery\"..."))
                Log.d(TAG, "Autonomous web search (round $toolExecutionRounds) requested for: ${toolCall.query}")

                val searchResult: String = coroutineScope {
                    suspend fun executeExaFallback(query: String, useKey: Boolean): String {
                        if (exaSearchService == null) return "No search engine available."
                        val keyToUse = if (useKey) exaApiKey else null
                        val res = exaSearchService.searchWeb(query, apiKey = keyToUse)
                        val text = res.getOrNull()?.takeIf {
                            res.isSuccess && it.isNotBlank() && it != ExaSearchService.ZERO_RESULTS_MESSAGE
                        }
                        if (text != null) return text
                        if (useKey && !keyToUse.isNullOrBlank()) {
                            val keylessRes = exaSearchService.searchWeb(query, apiKey = null)
                            val keylessText = keylessRes.getOrNull()?.takeIf {
                                keylessRes.isSuccess && it.isNotBlank() && it != ExaSearchService.ZERO_RESULTS_MESSAGE
                            }
                            if (keylessText != null) return keylessText
                        }
                        return res.getOrNull() ?: ExaSearchService.ZERO_RESULTS_MESSAGE
                    }

                    if (hasTavily && hasExa) {
                        val tavilyDeferred = async {
                            try {
                                tavilySearchService?.searchWeb(toolCall.query, apiKey = tavilyApiKey)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Result.failure(e)
                            }
                        }
                        val exaDeferred = async {
                            try {
                                exaSearchService?.searchWeb(toolCall.query, apiKey = exaApiKey)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Result.failure(e)
                            }
                        }

                        val tavilyRes = tavilyDeferred.await()
                        val exaRes = exaDeferred.await()

                        val tavilyText = tavilyRes?.getOrNull()?.takeIf {
                            tavilyRes.isSuccess && it.isNotBlank() && it != TavilySearchService.ZERO_RESULTS_MESSAGE
                        }
                        var exaText = exaRes?.getOrNull()?.takeIf {
                            exaRes.isSuccess && it.isNotBlank() && it != ExaSearchService.ZERO_RESULTS_MESSAGE
                        }

                        if (exaText == null) {
                            val mcpFallback = exaSearchService.searchWeb(toolCall.query, apiKey = null)
                            exaText = mcpFallback.getOrNull()?.takeIf {
                                mcpFallback.isSuccess && it.isNotBlank() && it != ExaSearchService.ZERO_RESULTS_MESSAGE
                            }
                        }

                        when {
                            tavilyText != null && exaText != null -> {
                                buildString {
                                    append("### Live Web & Official Sources:\n")
                                    append(tavilyText)
                                    append("\n\n---\n\n")
                                    append("### Community Discussions, Reviews & Semantic Results:\n")
                                    append(exaText)
                                }
                            }
                            exaText != null -> exaText
                            tavilyText != null -> tavilyText
                            else -> {
                                executeExaFallback(toolCall.query, useKey = false)
                            }
                        }
                    } else if (hasTavily) {
                        val tavilyRes = tavilySearchService?.searchWeb(toolCall.query, apiKey = tavilyApiKey)
                        val tavilyText = tavilyRes?.getOrNull()?.takeIf {
                            tavilyRes.isSuccess && it.isNotBlank() && it != TavilySearchService.ZERO_RESULTS_MESSAGE
                        }
                        if (tavilyText != null) {
                            tavilyText
                        } else if (isExaEnabled) {
                            executeExaFallback(toolCall.query, useKey = true)
                        } else {
                            executeExaFallback(toolCall.query, useKey = false)
                        }
                    } else if (hasExa) {
                        executeExaFallback(toolCall.query, useKey = true)
                    } else {
                        executeExaFallback(toolCall.query, useKey = false)
                    }
                }
                Log.d(TAG, "Web search retrieved ${searchResult.length} chars of data")

                val updatedMessages = JSONArray()
                for (i in 0 until currentMessages.length()) {
                    updatedMessages.put(currentMessages.getJSONObject(i))
                }

                // Append assistant's search invocation cleanly
                updatedMessages.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", "<tool_call><function=web_search><parameter=query>${toolCall.query}</parameter></function></tool_call>")
                })

                val nextInstruction = if (toolExecutionRounds < maxToolExecutionRounds) {
                    "[INSTRUCTION: Review the search results above against the user's prompt. If the retrieved evidence is complete and sufficient to answer all aspects of the request, provide your final response now without calling further tools. If crucial details are still missing or incomplete, you may invoke web_search again with a refined query to gather the remaining evidence.]"
                } else {
                    "[INSTRUCTION: Web search is now COMPLETE. Do NOT invoke any further tools or emit <tool_call> tags. Directly, accurately, and thoroughly answer the user's question using all gathered search results and your knowledge.]"
                }

                // Append search results context as user message to avoid provider HTTP 400 rejection on 'tool' role
                updatedMessages.put(JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        buildString {
                            append("[Web Search Results for \"")
                            append(toolCall.query)
                            append("\"]:\n")
                            append(searchResult)
                            append("\n\n")
                            append(nextInstruction)
                        }
                    )
                })

                currentMessages = updatedMessages
                emit(StreamEvent.ClearContent)
                emit(StreamEvent.Status("Synthesizing answer..."))
                continue
            }

            // Generation finished cleanly
            return@flow
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateTitle(
        apiKey: String,
        model: SupportedModel,
        firstUserPrompt: String,
        assistantReply: String = "",
        customBaseUrl: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || firstUserPrompt.isBlank()) return@withContext ""

        val userTurnSnippet = buildString {
            append("User: ")
            append(firstUserPrompt.trim().take(300))
            if (assistantReply.isNotBlank() && assistantReply != "...") {
                append("\nAssistant: ")
                append(assistantReply.trim().take(300))
            }
        }

        val messagesJsonArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    "You are a conversation title generator. Summarize the core topic of this exchange into a concise 2 to 4 word title. Output ONLY the raw title words without quotes, punctuation, preamble, or markdown."
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userTurnSnippet)
            })
        }

        val candidateEndpoints = getCandidateEndpoints(cleanKey, customBaseUrl)
        val candidateModelIds = getCandidateModelIds(model)
        val streamModes = listOf(false, true)

        for (endpoint in candidateEndpoints) {
            for (candidateId in candidateModelIds) {
                for (useStream in streamModes) {
                    try {
                        val requestBodyJson = JSONObject().apply {
                            put("model", candidateId)
                            put("messages", messagesJsonArray)
                            put("stream", useStream)
                            put("temperature", 0.7)
                            put("max_completion_tokens", 256)
                            put("thinking", JSONObject().apply {
                                put("type", "disabled")
                            })
                            put("extra_body", JSONObject().apply {
                                put("thinking", JSONObject().apply {
                                    put("type", "disabled")
                                })
                            })
                            put("enable_thinking", false)
                        }

                        val request = Request.Builder()
                            .url(endpoint)
                            .addHeader("Authorization", "Bearer $cleanKey")
                            .addHeader("api-key", cleanKey)
                            .addHeader("Content-Type", "application/json")
                            .apply {
                                if (useStream) addHeader("Accept", "text/event-stream")
                            }
                            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                if (response.code == 401 || response.code == 403) return@withContext ""
                                return@use
                            }
                            val bodyStr = response.body?.string()?.trim() ?: return@use
                            if (bodyStr.isEmpty()) return@use

                            val rawContent = if (bodyStr.startsWith("data:") || bodyStr.contains("\ndata:")) {
                                val sb = StringBuilder()
                                for (line in bodyStr.lineSequence()) {
                                    val trimmedLine = line.trim()
                                    if (!trimmedLine.startsWith("data:")) continue
                                    val dataPayload = trimmedLine.removePrefix("data:").trim()
                                    if (dataPayload == "[DONE]" || dataPayload.isEmpty()) continue
                                    try {
                                        val chunkJson = JSONObject(dataPayload)
                                        val delta = chunkJson.optJSONArray("choices")
                                            ?.optJSONObject(0)
                                            ?.optJSONObject("delta")
                                        if (delta != null && !delta.isNull("content")) {
                                            val piece = delta.optString("content", "")
                                            if (piece.isNotEmpty() && piece != "null") {
                                                sb.append(piece)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                sb.toString()
                            } else {
                                val json = JSONObject(bodyStr)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val firstChoice = choices.getJSONObject(0)
                                    val messageObj = firstChoice.optJSONObject("message")
                                    val deltaObj = firstChoice.optJSONObject("delta")
                                    when {
                                        messageObj != null && !messageObj.isNull("content") ->
                                            messageObj.optString("content", "")
                                        deltaObj != null && !deltaObj.isNull("content") ->
                                            deltaObj.optString("content", "")
                                        else -> firstChoice.optString("text", "")
                                    }
                                } else {
                                    ""
                                }
                            }

                            if (rawContent.isNotBlank() && rawContent != "null") {
                                val cleaned = sanitizeChatTitle(rawContent)
                                if (cleaned.isNotBlank() && !cleaned.equals("New Chat", ignoreCase = true)) {
                                    return@withContext cleaned
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                }
            }
        }
        return@withContext ""
    }

    fun sanitizeChatTitle(raw: String): String {
        var text = raw.trim()
        text = text.replace(THINK_TAG_REGEX, "")
            .replace(THOUGHT_TAG_REGEX, "")
            .replace(THINKING_TAG_REGEX, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .replace(RESIDUAL_TAG_REGEX, "")
            .trim()

        val firstLine = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: return ""
        text = firstLine

        val prefixes = listOf(
            "title:", "summary:", "topic:", "chat title:", "subject:",
            "here is a title:", "here's a title:", "here is the title:",
            "the title is:", "conversation title:", "here is a 3-4 word title:",
            "here is a 3 to 4 word title:"
        )
        for (p in prefixes) {
            if (text.startsWith(p, ignoreCase = true)) {
                text = text.substring(p.length).trim()
            }
        }

        text = text.replace("\"", "")
            .replace("'", "")
            .replace("`", "")
            .replace("#", "")
            .replace("*", "")
            .replace(":", "")
            .replace(".", "")
            .replace(",", "")
            .replace("!", "")
            .replace("?", "")
            .replace(";", "")
            .replace("\n", " ")
            .trim()

        val words = text.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        return words.take(4).joinToString(" ")
    }

    fun getCandidateEndpoints(cleanKey: String, customBaseUrl: String? = null): List<String> {
        if (!customBaseUrl.isNullOrBlank()) {
            val trimmed = customBaseUrl.trim().removeSuffix("/")
            val endpoint = if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
            return listOf(endpoint)
        }

        val baseList = if (cleanKey.startsWith("tp-") || cleanKey.startsWith("ttp-")) {
            listOf(
                TOKEN_PLAN_ENDPOINT,
                PAY_AS_YOU_GO_ENDPOINT
            )
        } else {
            listOf(
                PAY_AS_YOU_GO_ENDPOINT,
                TOKEN_PLAN_ENDPOINT
            )
        }

        val cached = lastWorkingEndpoint
        return if (cached != null && baseList.contains(cached)) {
            listOf(cached) + baseList.filter { it != cached }
        } else {
            baseList
        }
    }

    private fun getCandidateModelIds(model: SupportedModel): List<String> {
        return listOf(model.id)
    }

    private fun buildCandidatePayloads(
        candidateId: String,
        messagesJsonArray: JSONArray,
        isThinkingEnabled: Boolean,
        stream: Boolean,
        tools: JSONArray? = null
    ): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        val seenSignatures = mutableSetOf<String>()
        val hasTools = tools != null && tools.length() > 0

        fun addPayload(
            thinkingType: String?,
            withTools: Boolean
        ) {
            val maxTokens = if (isThinkingEnabled) 32768 else 8192
            val temp = if (isThinkingEnabled) 1.0 else 0.7

            val payload = JSONObject().apply {
                put("model", candidateId)
                put("messages", messagesJsonArray)
                put("stream", stream)
                put("temperature", temp)
                put("max_completion_tokens", maxTokens)

                if (thinkingType != null) {
                    put("thinking", JSONObject().apply {
                        put("type", thinkingType)
                    })
                    put("extra_body", JSONObject().apply {
                        put("thinking", JSONObject().apply {
                            put("type", thinkingType)
                        })
                    })
                    put("enable_thinking", thinkingType == "enabled")
                }

                if (withTools && tools != null && tools.length() > 0) {
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
            }
            if (seenSignatures.add(payload.toString())) {
                list.add(payload)
            }
        }

        val thinkingType = if (isThinkingEnabled) "enabled" else "disabled"
        if (hasTools) {
            addPayload(thinkingType = thinkingType, withTools = true)
        }
        addPayload(thinkingType = thinkingType, withTools = false)

        return list
    }

    data class ExtractedToolCall(
        val tool: String,
        val query: String
    )

    private fun getWebSearchTools(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "web_search")
                    put("description", "Searches the web for up-to-date facts, breaking news, company information, community discussions, forum opinions, and user reviews.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put("description", "The search query to look up on the web")
                            })
                        })
                        put("required", JSONArray().apply { put("query") })
                    })
                })
            })
        }
    }

    fun extractToolInvocation(text: String, explicitToolName: String? = null): ExtractedToolCall? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val tool = "web_search"

        PARAM_REGEX.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        TAG_REGEX.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        try {
            val json = JSONObject(trimmed)
            val q = json.optString("query", "").ifBlank { json.optString("search_query", "") }
            if (q.isNotBlank()) return ExtractedToolCall(tool, q.trim())
        } catch (_: Exception) {}

        JSON_QUERY_REGEX.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        FN_CALL_REGEX.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        BRACKET_CALL_REGEX.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        return null
    }

    companion object {
        private const val TAG = "WyrdLLM"
        const val PAY_AS_YOU_GO_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        const val TOKEN_PLAN_ENDPOINT = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val THINK_TAG_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
        private val THOUGHT_TAG_REGEX = Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE)
        private val THINKING_TAG_REGEX = Regex("<thinking>[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE)
        private val UNCLOSED_THINK_REGEX = Regex("<(?:think|thought|thinking)>[\\s\\S]*$", RegexOption.IGNORE_CASE)
        private val RESIDUAL_TAG_REGEX = Regex("</?(?:think|thought|thinking|tool_call|function_call|function|parameter)[^>]*>", RegexOption.IGNORE_CASE)
        private val PARAM_REGEX = Regex("""<parameter(?:=|\s+name=["'])query["']?>([\s\S]*?)</parameter>""", RegexOption.IGNORE_CASE)
        private val TAG_REGEX = Regex("""<(?:query|search_query)>([\s\S]*?)</(?:query|search_query)>""", RegexOption.IGNORE_CASE)
        private val JSON_QUERY_REGEX = Regex("""(?:"query"|"search_query")\s*:\s*"([^"]+)"""")
        private val FN_CALL_REGEX = Regex("""(?:call:)?(?:tavily_search|exa_search|web_search)\s*\(\s*(?:(?:query|q)\s*=\s*)?["']([^"'\n\r]+)["']\s*\)""", RegexOption.IGNORE_CASE)
        private val BRACKET_CALL_REGEX = Regex("""\[(?:ACTION:\s*)?(?:tavily_search|exa_search|web_search)[\s:\(]+["']?([^"'\]\)\n\r]+)["']?[\)\]]""", RegexOption.IGNORE_CASE)
    }
}

