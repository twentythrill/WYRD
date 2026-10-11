package com.wyrd.app.data.network

import android.util.Log
import com.wyrd.app.data.preferences.ReasoningEffort
import com.wyrd.app.data.preferences.SupportedModel
import kotlinx.coroutines.Dispatchers
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
        reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
        systemPrompt: String,
        messages: List<ChatMessagePayload>,
        exaSearchService: ExaSearchService? = null,
        exaApiKey: String? = null,
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
        val hasExa = exaSearchService != null
        val hasAnySearch = hasTavily || hasExa

        val effectiveSystemPrompt = buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt)
                append("\n\n")
            }
            append("[Context: $temporalContext]")
            if (hasAnySearch) {
                append("\n[Autonomous Web Search Tools: You have access to external search engines to ground your answers in verified external facts. You are in charge of deciding autonomously whether to use web search.")
                if (hasTavily) {
                    append("\n- `tavily_search`: Real-time live web search engine. Use this when the user's question requires breaking news, current events, recent releases/updates (e.g. software versions, patch notes), live data (weather, sports scores, stock prices), or live web facts. Invoke with:\n<tool_call><function=tavily_search><parameter=query>your search query</parameter></function></tool_call>")
                }
                if (hasExa) {
                    append("\n- `exa_search`: Semantic neural search engine. Use this when the user needs forum discussions (e.g. Reddit), company employee reviews and interview feedback (e.g. Glassdoor), workplace culture sentiment, in-depth documentation, or deep research. Invoke with:\n<tool_call><function=exa_search><parameter=query>your search query</parameter></function></tool_call>")
                }
                append("\nIf the request does not require external search, answer directly without invoking tools. Never tell the user to use manual search commands like /search.]")
            }
            when (reasoningEffort) {
                ReasoningEffort.OFF -> {
                    append("\n[Reasoning: Disabled. Answer directly and immediately with zero internal monologue or thinking scratchpad.]")
                }
                ReasoningEffort.LOW -> {
                    append("\n[Reasoning Budget: Low (~1000 tokens). Strictly constrain your internal thinking/reasoning process to be brief, concise, and focused solely on core deductions. Keep internal thought under 250 words. Do not engage in lengthy internal monologues, exhaustive scenario mapping, or speculative tangents.]")
                }
                ReasoningEffort.MEDIUM -> {
                    append("\n[Reasoning Budget: Medium (~8000 tokens). Keep your internal reasoning balanced, direct, and focused on essential analysis without excessive verbosity.]")
                }
                ReasoningEffort.HIGH -> {
                    append("\n[Reasoning Budget: High (~32000 tokens). You have full allowance for thorough, deep chain-of-thought exploration, rigorous scenario analysis, and multi-perspective verification.]")
                }
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
        val webTools = if (hasAnySearch) getWebSearchTools(enableTavily = hasTavily, enableExa = hasExa) else null

        var currentMessages = messagesJsonArray
        var toolExecutionRounds = 0
        val maxToolExecutionRounds = 1

        while (toolExecutionRounds <= maxToolExecutionRounds) {
            var lastErrorMsg = "Unable to connect to Xiaomi MiMo API"
            var tokenEmittedCount = 0
            var toolCallId = ""
            var toolCallName = ""
            val toolCallArgs = StringBuilder()
            val streamedContentAccumulator = StringBuilder()
            var isStreamSuccess = false
            var activeParser: ThinkAndToolParser? = null

            // In Round 1, provide webTools if search is enabled (and no multimodal images in conversation); in Round 2+, suppress tools so model generates final answer
            val hasImagesInConversation = historyToInclude.any { it.imagesBase64.isNotEmpty() || !it.imageBase64.isNullOrBlank() }
            val currentWebTools = if (toolExecutionRounds == 0 && hasAnySearch && !hasImagesInConversation) webTools else null

            // Try streaming first across endpoints and models
            outerStream@ for (endpoint in candidateEndpoints) {
                for (candidateId in candidateModels) {
                    val payloads = buildCandidatePayloads(
                        candidateId = candidateId,
                        messagesJsonArray = currentMessages,
                        reasoningEffort = reasoningEffort,
                        stream = true,
                        tools = currentWebTools
                    )
                    for (payload in payloads) {
                        val thinkingInfo = payload.optJSONObject("thinking")?.optString("type") ?: "none"
                        val effortInfo = payload.optString("reasoning_effort", "none")
                        Log.d(TAG, "Attempting stream endpoint: $endpoint with model: $candidateId, reasoningEffort=$reasoningEffort, thinking=$thinkingInfo, effort=$effortInfo")

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
                                    if (reasoningEffort != ReasoningEffort.OFF) {
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
                                                if (reasoningEffort != ReasoningEffort.OFF && reasoningToken.isNotEmpty() && reasoningToken != "null") {
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
                            lastErrorMsg = e.message ?: "Stream network exception"
                            Log.w(TAG, "Stream attempt error: $lastErrorMsg", e)
                        } finally {
                            try { response?.close() } catch (ignored: Throwable) {}
                        }
                    }
                }
            }

            // Non-Streaming Fallback (rescues cases where SSE streaming is blocked, buffered, or dropped)
            if (!isStreamSuccess) {
                Log.d(TAG, "Initiating non-streaming fallback...")
                outerNonStream@ for (endpoint in candidateEndpoints) {
                    for (candidateId in candidateModels) {
                        val payloads = buildCandidatePayloads(
                            candidateId = candidateId,
                            messagesJsonArray = currentMessages,
                            reasoningEffort = reasoningEffort,
                            stream = false,
                            tools = currentWebTools
                        )
                        for (payload in payloads) {
                            val request = Request.Builder()
                                .url(endpoint)
                                .addHeader("Authorization", "Bearer $cleanKey")
                                .addHeader("api-key", cleanKey)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Accept", "application/json")
                                .post(payload.toString().toRequestBody(jsonMediaType))
                                .build()

                            try {
                                client.newCall(request).execute().use { resp ->
                                    if (!resp.isSuccessful) {
                                        val code = resp.code
                                        val errBody = resp.body?.string() ?: ""
                                        lastErrorMsg = "HTTP $code from $endpoint: $errBody"
                                        Log.w(TAG, "Non-streaming attempt failed: $lastErrorMsg")
                                        return@use
                                    }

                                    val bodyStr = resp.body?.string() ?: return@use
                                    val json = JSONObject(bodyStr)
                                    if (json.has("error")) {
                                        val errObj = json.optJSONObject("error")
                                        val msg = errObj?.optString("message") ?: json.optString("error")
                                        lastErrorMsg = "API error: $msg"
                                        return@use
                                    }

                                    val choices = json.optJSONArray("choices")
                                    if (choices != null && choices.length() > 0) {
                                        val choice = choices.getJSONObject(0)
                                        val msg = choice.optJSONObject("message")
                                        val reasoning = msg?.optString("reasoning_content", "") ?: ""
                                        val content = msg?.optString("content", "") ?: choice.optString("text", "")

                                        val msgToolCalls = msg?.optJSONArray("tool_calls")
                                        if (msgToolCalls != null && msgToolCalls.length() > 0) {
                                            val firstTc = msgToolCalls.getJSONObject(0)
                                            toolCallId = firstTc.optString("id", "call_1")
                                            val fn = firstTc.optJSONObject("function")
                                            toolCallName = fn?.optString("name", "") ?: ""
                                            toolCallArgs.append(fn?.optString("arguments", "") ?: "")
                                        }

                                        if (reasoningEffort != ReasoningEffort.OFF && reasoning.isNotBlank() && reasoning != "null") {
                                            emit(StreamEvent.Reasoning(reasoning))
                                        }

                                        if (content.isNotBlank() && content != "null") {
                                            val parser = ThinkAndToolParser(
                                                onContent = {
                                                    tokenEmittedCount++
                                                    streamedContentAccumulator.append(it)
                                                    emit(StreamEvent.Content(it))
                                                },
                                                onReasoning = {
                                                    if (reasoningEffort != ReasoningEffort.OFF) {
                                                        tokenEmittedCount++
                                                        emit(StreamEvent.Reasoning(it))
                                                    }
                                                }
                                            )
                                            activeParser = parser
                                            parser.feed(content)
                                            parser.flush()
                                        }

                                        lastWorkingEndpoint = endpoint
                                        isStreamSuccess = true
                                    }
                                }
                                if (isStreamSuccess) {
                                    break@outerNonStream
                                }
                            } catch (e: Exception) {
                                lastErrorMsg = e.message ?: "Non-streaming error"
                                Log.w(TAG, "Non-streaming exception: $lastErrorMsg", e)
                            }
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
                val isTavily = toolCall.tool == "tavily_search" || (toolCall.tool == "web_search" && hasTavily)
                var engineName = if (isTavily) "Tavily" else "Exa"
                val functionName = if (isTavily) "tavily_search" else "exa_search"

                emit(StreamEvent.Status(if (isTavily) "Searching live web with Tavily..." else "Searching web with Exa..."))
                Log.d(TAG, "Autonomous $engineName search requested for: ${toolCall.query}")

                val searchResult: String = if (isTavily && tavilySearchService != null) {
                    val tavilyRes = tavilySearchService.searchWeb(toolCall.query, apiKey = tavilyApiKey)
                    if (tavilyRes.isSuccess) {
                        tavilyRes.getOrNull() ?: TavilySearchService.ZERO_RESULTS_MESSAGE
                    } else if (exaSearchService != null) {
                        // Tavily failed (quota limit, expired key, network down) -> Automatic fallback to Exa!
                        Log.w(TAG, "Tavily search failed (${tavilyRes.exceptionOrNull()?.message}), falling back to Exa")
                        emit(StreamEvent.Status("Tavily unavailable, falling back to Exa..."))
                        val exaRes = exaSearchService.searchWeb(toolCall.query, apiKey = exaApiKey)
                        if (exaRes.isSuccess && exaRes.getOrNull() != ExaSearchService.ZERO_RESULTS_MESSAGE) {
                            engineName = "Exa (fallback from Tavily)"
                            exaRes.getOrNull() ?: ExaSearchService.ZERO_RESULTS_MESSAGE
                        } else {
                            exaRes.getOrNull() ?: TavilySearchService.ZERO_RESULTS_MESSAGE
                        }
                    } else {
                        TavilySearchService.ZERO_RESULTS_MESSAGE
                    }
                } else if (exaSearchService != null) {
                    val exaRes = exaSearchService.searchWeb(toolCall.query, apiKey = exaApiKey)
                    if (exaRes.isSuccess && exaRes.getOrNull() != ExaSearchService.ZERO_RESULTS_MESSAGE) {
                        exaRes.getOrNull() ?: ExaSearchService.ZERO_RESULTS_MESSAGE
                    } else if (exaRes.isFailure && tavilySearchService != null && !tavilyApiKey.isNullOrBlank()) {
                        // Exa failed (e.g. MCP down/rate limit) and Tavily key is available -> Automatic fallback to Tavily!
                        Log.w(TAG, "Exa search failed (${exaRes.exceptionOrNull()?.message}), falling back to Tavily")
                        emit(StreamEvent.Status("Exa unavailable, falling back to Tavily..."))
                        val tavilyRes = tavilySearchService.searchWeb(toolCall.query, apiKey = tavilyApiKey)
                        if (tavilyRes.isSuccess) {
                            engineName = "Tavily (fallback from Exa)"
                            tavilyRes.getOrNull() ?: TavilySearchService.ZERO_RESULTS_MESSAGE
                        } else {
                            ExaSearchService.ZERO_RESULTS_MESSAGE
                        }
                    } else {
                        exaRes.getOrNull() ?: ExaSearchService.ZERO_RESULTS_MESSAGE
                    }
                } else {
                    "No search engine available."
                }
                Log.d(TAG, "$engineName search retrieved ${searchResult.length} chars of live web data")

                val updatedMessages = JSONArray()
                for (i in 0 until currentMessages.length()) {
                    updatedMessages.put(currentMessages.getJSONObject(i))
                }

                // Append assistant's search invocation cleanly
                updatedMessages.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", "<tool_call><function=$functionName><parameter=query>${toolCall.query}</parameter></function></tool_call>")
                })

                val nextInstruction =
                    "[INSTRUCTION: Web search via $engineName is now COMPLETE. Do NOT invoke $functionName or emit any <tool_call> tags. Directly, accurately, and thoroughly answer the user's question using the search results above and your knowledge.]"

                // Append search results context as user message to avoid provider HTTP 400 rejection on 'tool' role
                updatedMessages.put(JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        buildString {
                            append("[Web Search Results (via $engineName) for \"")
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
        customBaseUrl: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val defaultWords = firstUserPrompt.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.take(4).joinToString(" ")
        val fallbackTitle = if (defaultWords.isNotBlank()) defaultWords else "New Chat"
        if (cleanKey.isBlank()) return@withContext fallbackTitle

        val candidateEndpoints = getCandidateEndpoints(cleanKey, customBaseUrl)
        val candidateModelIds = getCandidateModelIds(model)
        for (endpoint in candidateEndpoints) {
            for (candidateId in candidateModelIds) {
                try {
                    val messagesJsonArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a title generator. Return ONLY a 3 to 4 word summary title for this conversation without quotes, punctuation, preamble, or markdown. Maximum 4 words.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", firstUserPrompt.take(200))
                        })
                    }

                    val requestBodyJson = JSONObject().apply {
                        put("model", candidateId)
                        put("messages", messagesJsonArray)
                        put("stream", false)
                        put("max_tokens", 16)
                        put("temperature", 0.3)
                    }

                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("api-key", cleanKey)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val bodyStr = response.body?.string() ?: return@use
                        val json = JSONObject(bodyStr)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val messageObj = firstChoice.optJSONObject("message")
                            if (messageObj != null) {
                                val rawContent = messageObj.optString("content", "")
                                if (rawContent.isNotBlank() && rawContent != "null") {
                                    val cleaned = sanitizeChatTitle(rawContent)
                                    if (cleaned.isNotBlank()) {
                                        return@withContext cleaned
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // try next candidate
                }
            }
        }
        return@withContext fallbackTitle
    }

    fun sanitizeChatTitle(raw: String): String {
        var text = raw.trim()
        // Strip reasoning tags if present
        text = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thinking>[\\s\\S]*?</thinking>", RegexOption.IGNORE_CASE), "")
            .trim()

        // Strip common prefixes
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

        // Strip quotes, markdown, and punctuation
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

        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        // Strictly clamp to maximum 4 words (3 or 4 words)
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
        return when (model) {
            SupportedModel.MIMO_2_6_PRO -> listOf(
                "mimo-v2.6-pro",
                "mimo-v2.6-flash"
            )
            SupportedModel.MIMO_2_6_FLASH -> listOf(
                "mimo-v2.6-flash",
                "mimo-v2.6-pro"
            )
        }
    }

    private fun buildCandidatePayloads(
        candidateId: String,
        messagesJsonArray: JSONArray,
        reasoningEffort: ReasoningEffort,
        stream: Boolean,
        tools: JSONArray? = null
    ): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        val seenSignatures = mutableSetOf<String>()

        fun addPayload(
            thinkingType: String?, // "enabled", "disabled", or null
            budgetTokens: Int?,
            effort: String?,       // "low", "medium", "high", or null
            withTools: Boolean
        ) {
            val maxTokens = when (reasoningEffort) {
                ReasoningEffort.LOW -> 4096
                ReasoningEffort.MEDIUM -> 16384
                ReasoningEffort.HIGH -> 65536
                ReasoningEffort.OFF -> 4096
            }
            val payload = JSONObject().apply {
                put("model", candidateId)
                put("messages", messagesJsonArray)
                put("stream", stream)
                put("temperature", 0.7)
                put("max_completion_tokens", maxTokens)

                // 1. Xiaomi MiMo / Anthropic standard thinking object
                if (thinkingType != null) {
                    put("thinking", JSONObject().apply {
                        put("type", thinkingType)
                        if (thinkingType == "enabled" && budgetTokens != null && budgetTokens > 0) {
                            put("budget_tokens", budgetTokens)
                        }
                    })
                }

                // 2. OpenAI reasoning_effort standard ("low", "medium", "high")
                if (effort != null) {
                    put("reasoning_effort", effort)
                }

                // 3. Top-level parameters for diverse gateways (vLLM / legacy proxies)
                if (budgetTokens != null && budgetTokens > 0) {
                    put("thinking_budget", budgetTokens)
                }
                if (thinkingType == "disabled") {
                    put("enable_thinking", false)
                } else if (thinkingType == "enabled") {
                    put("enable_thinking", true)
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

        val hasTools = tools != null && tools.length() > 0

        when (reasoningEffort) {
            ReasoningEffort.OFF -> {
                // Completely bypass thinking scratchpad for direct instantaneous answers
                if (hasTools) addPayload(thinkingType = "disabled", budgetTokens = null, effort = null, withTools = true)
                addPayload(thinkingType = "disabled", budgetTokens = null, effort = null, withTools = false)

                // Fallback: omit thinking parameters entirely (standard non-thinking completion)
                if (hasTools) addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = true)
                addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = false)
            }
            ReasoningEffort.LOW -> {
                // Tier 1: Strict token budget (1024 tokens) without reasoning_effort override
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = 1024, effort = null, withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = 1024, effort = null, withTools = false)

                // Tier 2: Fallback with reasoning_effort string if gateway strictly requires it
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = 1024, effort = "low", withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = 1024, effort = "low", withTools = false)

                // Fallback: Default server behavior
                if (hasTools) addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = true)
                addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = false)
            }
            ReasoningEffort.MEDIUM -> {
                // Tier 1: Strict token budget (8192 tokens) without reasoning_effort override
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = 8192, effort = null, withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = 8192, effort = null, withTools = false)

                // Tier 2: Fallback with reasoning_effort string if gateway requires it
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = 8192, effort = "medium", withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = 8192, effort = "medium", withTools = false)

                // Fallback 1: thinking enabled without budget constraint
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = null, effort = "medium", withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = null, effort = "medium", withTools = false)

                // Fallback 2: default server behavior
                if (hasTools) addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = true)
                addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = false)
            }
            ReasoningEffort.HIGH -> {
                // Tier 1: Strict token budget (32768 tokens) without reasoning_effort override
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = 32768, effort = null, withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = 32768, effort = null, withTools = false)

                // Tier 2: Fallback with reasoning_effort string if gateway requires it
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = 32768, effort = "high", withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = 32768, effort = "high", withTools = false)

                // Fallback 1: thinking enabled without budget constraint
                if (hasTools) addPayload(thinkingType = "enabled", budgetTokens = null, effort = "high", withTools = true)
                addPayload(thinkingType = "enabled", budgetTokens = null, effort = "high", withTools = false)

                // Fallback 2: default server behavior
                if (hasTools) addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = true)
                addPayload(thinkingType = null, budgetTokens = null, effort = null, withTools = false)
            }
        }
        return list
    }

    data class ExtractedToolCall(
        val tool: String,
        val query: String
    )

    private fun getWebSearchTools(enableTavily: Boolean = true, enableExa: Boolean = true): JSONArray {
        return JSONArray().apply {
            if (enableTavily) {
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "tavily_search")
                        put("description", "Searches the live web in real time for breaking news, current events, recent updates, sports scores, weather, and up-to-the-minute facts via Tavily.")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The search query to look up on the live web via Tavily")
                                })
                            })
                            put("required", JSONArray().apply { put("query") })
                        })
                    })
                })
            }
            if (enableExa) {
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "exa_search")
                        put("description", "Searches the web semantically for forum discussions, Reddit threads, Glassdoor employee reviews, interview feedback, company culture, in-depth documentation, or deep research via Exa.")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The search query to look up on the web semantically via Exa")
                                })
                            })
                            put("required", JSONArray().apply { put("query") })
                        })
                    })
                })
            }
        }
    }

    fun extractToolInvocation(text: String, explicitToolName: String? = null): ExtractedToolCall? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        var tool = when {
            explicitToolName?.equals("exa_search", ignoreCase = true) == true || explicitToolName?.equals("exa", ignoreCase = true) == true -> "exa_search"
            explicitToolName?.equals("tavily_search", ignoreCase = true) == true || explicitToolName?.equals("tavily", ignoreCase = true) == true -> "tavily_search"
            trimmed.contains("exa_search", ignoreCase = true) || trimmed.contains("exa", ignoreCase = true) -> "exa_search"
            trimmed.contains("tavily_search", ignoreCase = true) || trimmed.contains("tavily", ignoreCase = true) -> "tavily_search"
            else -> "web_search"
        }

        // 1. XML parameter syntax: <parameter=query>...</parameter> or <parameter name="query">...</parameter>
        val paramRegex = Regex("""<parameter(?:=|\s+name=["'])query["']?>([\s\S]*?)</parameter>""", RegexOption.IGNORE_CASE)
        paramRegex.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        // 2. Direct tag syntax: <query>...</query> or <search_query>...</search_query>
        val tagRegex = Regex("""<(?:query|search_query)>([\s\S]*?)</(?:query|search_query)>""", RegexOption.IGNORE_CASE)
        tagRegex.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        // 3. JSON format: {"query": "..."} or {"search_query": "..."}
        try {
            val json = JSONObject(trimmed)
            val q = json.optString("query", "").ifBlank { json.optString("search_query", "") }
            if (q.isNotBlank()) return ExtractedToolCall(tool, q.trim())
        } catch (_: Exception) {}

        val jsonRegex = Regex("""(?:"query"|"search_query")\s*:\s*"([^"]+)"""")
        jsonRegex.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        // 4. Function call syntax: tavily_search("...") or exa_search("...") or web_search("...")
        val fnRegex = Regex("""(?:call:)?(?:tavily_search|exa_search|web_search)\s*\(\s*(?:(?:query|q)\s*=\s*)?["']([^"'\n\r]+)["']\s*\)""", RegexOption.IGNORE_CASE)
        fnRegex.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        // 5. Action bracket syntax: [tavily_search: ...] or [ACTION: exa_search("...")]
        val bracketRegex = Regex("""\[(?:ACTION:\s*)?(?:tavily_search|exa_search|web_search)[\s:\(]+["']?([^"'\]\)\n\r]+)["']?[\)\]]""", RegexOption.IGNORE_CASE)
        bracketRegex.find(trimmed)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotBlank()) return ExtractedToolCall(tool, q)
        }

        return null
    }

    fun extractToolQuery(text: String): String? = extractToolInvocation(text)?.query

    companion object {
        private const val TAG = "WyrdLLM"
        const val PAY_AS_YOU_GO_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        const val TOKEN_PLAN_ENDPOINT = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"
    }
}
