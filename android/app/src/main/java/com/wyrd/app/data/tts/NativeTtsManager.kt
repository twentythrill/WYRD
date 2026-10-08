package com.wyrd.app.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsPlaybackState(
    val messageId: Long? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentRange: Pair<Int, Int>? = null // (startCharIndex, endCharIndex) in full text
)

data class TtsEngineInfo(
    val packageName: String,
    val label: String
)

data class TtsVoiceInfo(
    val name: String,
    val displayName: String,
    val locale: Locale
)

class NativeTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingTasks = mutableListOf<() -> Unit>()

    private var activeEnginePackage: String? = null
    private var preferredVoiceName: String? = null

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _availableEngines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val availableEngines: StateFlow<List<TtsEngineInfo>> = _availableEngines.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    private var currentFullText: String = ""
    private var currentStartOffset: Int = 0
    private var savedOffset: Int = 0
    private var currentMessageId: Long? = null
    private var lastUtteranceId: String? = null

    private var isStreamingActive: Boolean = false
    private var isStreamingPaused: Boolean = false
    private var streamingChunkIndex: Int = 0
    private var streamingDetectedLocale: Locale? = null

    init {
        initEngine(null)
    }

    private fun initEngine(enginePackage: String?) {
        val target = enginePackage?.trim()?.takeIf { it.isNotBlank() && it != "system" }
        activeEnginePackage = target
        try {
            tts = if (target == null) {
                TextToSpeech(context.applicationContext, this)
            } else {
                TextToSpeech(context.applicationContext, this, target)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to instantiate TextToSpeech with engine $target", e)
        }
    }

    fun setEngine(enginePackage: String?) {
        val target = enginePackage?.trim()?.takeIf { it.isNotBlank() && it != "system" }
        if (target == activeEnginePackage && isInitialized && tts != null) {
            return
        }
        stop()
        try {
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.e(TAG, "Error shutting down previous TTS instance", e)
        }
        tts = null
        isInitialized = false
        initEngine(target)
    }

    fun setPreferredVoice(voiceName: String?) {
        preferredVoiceName = voiceName?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            updateAvailableEngines()
            updateAvailableVoices()
            synchronized(pendingTasks) {
                pendingTasks.forEach { it.invoke() }
                pendingTasks.clear()
            }
            Log.i(TAG, "Native TextToSpeech initialized successfully (status=$status, activeEngine: ${activeEnginePackage ?: "system default"}, OS default: ${tts?.defaultEngine})")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    private fun updateAvailableEngines() {
        try {
            val engines = tts?.engines ?: emptyList()
            val list = mutableListOf<TtsEngineInfo>()
            engines.forEach {
                list.add(TtsEngineInfo(packageName = it.name, label = it.label ?: it.name))
            }
            val knownEngines = listOf(
                TtsEngineInfo("com.samsung.SMT", "Samsung text-to-speech"),
                TtsEngineInfo("com.google.android.tts", "Speech Recognition and Synthesis from Google")
            )
            for (known in knownEngines) {
                if (list.none { it.packageName == known.packageName } && isPackageInstalled(known.packageName)) {
                    list.add(known)
                }
            }
            _availableEngines.value = list
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to query TTS engines: ${e.message}")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateAvailableVoices() {
        try {
            val voicesSet = tts?.voices ?: emptySet()
            val userLangs = setOf(Locale.getDefault().language.lowercase(Locale.ROOT), "en", "it")

            // Filter out placeholder and non-matching voices
            val filteredVoices = voicesSet
                .filter { voice ->
                    val lang = voice.locale.language.lowercase(Locale.ROOT)
                    lang in userLangs && !voice.name.endsWith("-language", ignoreCase = true)
                }

            // Deduplicate: if both -local and -network exist for the same voice, keep -local
            val deduplicated = filteredVoices.filter { voice ->
                if (voice.name.endsWith("-network", ignoreCase = true)) {
                    val localName = voice.name.removeSuffix("-network") + "-local"
                    filteredVoices.none { it.name == localName }
                } else {
                    true
                }
            }

            val list = deduplicated
                .sortedWith(compareBy({ it.locale.displayLanguage }, { it.name }))
                .map {
                    TtsVoiceInfo(
                        name = it.name,
                        displayName = formatVoiceDisplayName(it),
                        locale = it.locale
                    )
                }
            _availableVoices.value = list
            Log.i(TAG, "Available voices (${list.size}): ${list.map { "${it.displayName} [${it.name}]" }}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to query TTS voices: ${e.message}")
        }
    }

    private fun formatVoiceDisplayName(voice: Voice): String {
        val locale = voice.locale
        val langName = when (locale.country.uppercase(Locale.ROOT)) {
            "US" -> "English (US)"
            "GB" -> "English (UK)"
            "AU" -> "English (AU)"
            "IN" -> "English (IN)"
            "IT" -> "Italian"
            else -> locale.displayLanguage
        }
        val rawLower = voice.name.lowercase(Locale.ROOT)

        val cleanName = when {
            // Known Google US voices
            rawLower.contains("sfg") -> "Voice 1 (Female)"
            rawLower.contains("iob") -> "Voice 2 (Male)"
            rawLower.contains("iog") -> "Voice 3 (Female)"
            rawLower.contains("iol") -> "Voice 4 (Male)"
            rawLower.contains("iom") -> "Voice 5 (Male)"
            rawLower.contains("tpc") -> "Voice 6 (Female)"
            rawLower.contains("tpd") -> "Voice 7 (Male)"
            rawLower.contains("tpf") -> "Voice 8 (Male)"
            rawLower.contains("msm") -> "Voice 9 (Male)"

            // Known Google UK voices
            rawLower.contains("gba") -> "Voice 1 (Female)"
            rawLower.contains("gbb") -> "Voice 2 (Male)"
            rawLower.contains("gbc") -> "Voice 3 (Female)"
            rawLower.contains("gbd") -> "Voice 4 (Male)"
            rawLower.contains("gbg") -> "Voice 5 (Female)"
            rawLower.contains("rjs") -> "Voice 6 (Male)"

            // Known Google AU voices
            rawLower.contains("aua") -> "Voice 1 (Female)"
            rawLower.contains("aub") -> "Voice 2 (Male)"
            rawLower.contains("auc") -> "Voice 3 (Female)"
            rawLower.contains("aud") -> "Voice 4 (Male)"

            // Known Google IT voices
            rawLower.contains("itb") -> "Voice 1 (Female)"
            rawLower.contains("itc") -> "Voice 2 (Male)"
            rawLower.contains("itd") -> "Voice 3 (Female)"
            rawLower.contains("kda") -> "Voice 4 (Male)"

            // Generic Samsung or OEM voices
            rawLower.contains("male") && !rawLower.contains("female") -> {
                val num = Regex("male[_-]?(\\d+)", RegexOption.IGNORE_CASE).find(voice.name)?.groupValues?.getOrNull(1)
                if (num != null) "Male $num" else "Male"
            }
            rawLower.contains("female") -> {
                val num = Regex("female[_-]?(\\d+)", RegexOption.IGNORE_CASE).find(voice.name)?.groupValues?.getOrNull(1)
                if (num != null) "Female $num" else "Female"
            }
            voice.name.contains("#") -> {
                voice.name.substringAfterLast('#').replace('-', ' ').replace('_', ' ').trim()
            }
            else -> {
                val token = voice.name.substringAfterLast('-').replace('_', ' ').trim()
                if (token.equals("local", ignoreCase = true) || token.equals("network", ignoreCase = true)) {
                    val segments = voice.name.split('-')
                    if (segments.size >= 4) segments[segments.size - 2].uppercase(Locale.ROOT) else token
                } else {
                    token
                }
            }
        }
        val quality = if (voice.quality >= Voice.QUALITY_HIGH) " (HD)" else ""
        return "$langName · $cleanName$quality"
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (_playbackState.value.isPaused || isStreamingPaused) return
                _playbackState.value = _playbackState.value.copy(
                    messageId = currentMessageId,
                    isPlaying = true,
                    isPaused = false
                )
            }

            override fun onDone(utteranceId: String?) {
                if (_playbackState.value.isPaused || isStreamingPaused) return
                if (utteranceId == null || (utteranceId == lastUtteranceId && !isStreamingActive)) {
                    _playbackState.value = TtsPlaybackState()
                    currentFullText = ""
                    currentStartOffset = 0
                    savedOffset = 0
                    currentMessageId = null
                    lastUtteranceId = null
                }
            }

            override fun onError(utteranceId: String?) {
                if (_playbackState.value.isPaused || isStreamingPaused) return
                Log.e(TAG, "TTS utterance error: $utteranceId")
                if (utteranceId == null || (utteranceId == lastUtteranceId && !isStreamingActive)) {
                    _playbackState.value = TtsPlaybackState()
                    currentFullText = ""
                    currentStartOffset = 0
                    savedOffset = 0
                    currentMessageId = null
                    lastUtteranceId = null
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (_playbackState.value.isPaused || isStreamingPaused) return
                val chunkStart = utteranceId?.substringAfterLast('_')?.toIntOrNull() ?: currentStartOffset
                val globalStart = chunkStart + start
                val globalEnd = chunkStart + end
                _playbackState.value = _playbackState.value.copy(
                    messageId = currentMessageId,
                    isPlaying = true,
                    isPaused = false,
                    currentRange = Pair(globalStart, globalEnd)
                )
            }
        })
    }

    private fun configureVoiceForLanguage(locale: Locale) {
        val ttsInstance = tts ?: return
        val langResult = ttsInstance.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language $locale not fully supported, falling back to Locale.US")
            ttsInstance.language = Locale.US
        }

        // Apply explicitly selected voice if specified by user in app settings
        val targetVoiceName = preferredVoiceName
        if (!targetVoiceName.isNullOrBlank()) {
            try {
                val match = ttsInstance.voices?.find { it.name == targetVoiceName }
                if (match != null) {
                    if (match.locale.language.equals(locale.language, ignoreCase = true)) {
                        ttsInstance.voice = match
                        Log.i(TAG, "Applied explicit voice: ${match.name}")
                    } else {
                        Log.i(TAG, "Preferred voice '${match.name}' (${match.locale}) does not match text language ($locale), letting engine select localized voice")
                    }
                } else {
                    Log.w(TAG, "Preferred voice '$targetVoiceName' not found in current engine voices")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not set custom voice: ${e.message}")
            }
        } else {
            Log.i(TAG, "Using default voice for engine: ${ttsInstance.voice?.name}")
        }
    }

    fun detectLanguage(text: String, hint: Locale? = null): Locale {
        val defaultLocale = hint ?: if (Locale.getDefault().language.equals("it", ignoreCase = true)) Locale.ITALY else Locale.US
        val clean = text.lowercase(Locale.ROOT)
        val words = clean
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 }

        if (words.isEmpty()) return defaultLocale

        // Comprehensive set of Italian stopwords, articles, pronouns & characteristic vocabulary
        val italianStopwords = setOf(
            "il", "la", "lo", "i", "gli", "le", "un", "una", "uno",
            "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
            "del", "dello", "della", "dei", "degli", "delle",
            "al", "allo", "alla", "ai", "agli", "alle",
            "dal", "dallo", "dalla", "dai", "dagli", "dalle",
            "nel", "nello", "nella", "nei", "negli", "nelle",
            "sul", "sullo", "sulla", "sui", "sugli", "sulle",
            "che", "chi", "cui", "quale", "quali",
            "non", "ci", "vi", "si", "ti", "mi", "me", "te", "noi", "voi", "loro",
            "questo", "questa", "questi", "queste", "quello", "quella", "quelli", "quelle",
            "perché", "perche", "poiché", "quando", "mentre", "come", "dove", "cosa",
            "anche", "ancora", "dopo", "prima", "allora", "quindi", "invece", "però", "pero",
            "tuttavia", "inoltre", "infatti", "oppure", "ovvero", "pure", "sempre", "mai",
            "molto", "molti", "molte", "molta", "poco", "pochi", "poche", "poca",
            "tutto", "tutti", "tutte", "tutta", "troppo", "tanto", "davvero", "veramente",
            "solo", "soltanto", "senza", "grazie", "prego", "ciao", "salve", "buongiorno", "buonasera",
            "bene", "male", "meglio", "peggio", "più", "piu", "meno",
            "essere", "stato", "stata", "stati", "state", "sono", "sei", "siamo", "siete", "era", "erano",
            "avere", "avuto", "avuta", "avuti", "avute", "ho", "hai", "ha", "abbiamo", "avete", "hanno",
            "fare", "fatto", "fatta", "fatti", "fatte", "fa", "fanno", "faceva",
            "potere", "posso", "puoi", "può", "puo", "possiamo", "potete", "possono",
            "dovere", "devo", "devi", "deve", "dobbiamo", "dovete", "devono",
            "volere", "voglio", "vuoi", "vuole", "vogliamo", "volete", "vogliono",
            "dire", "detto", "dice", "dicono", "andare", "va", "vanno", "andato",
            "sapere", "so", "sai", "sa", "sappiamo", "sanno", "saputo",
            "vedere", "vede", "vediamo", "vedono", "visto",
            "certamente", "sicuramente", "esatto", "ecco", "forse", "quasi", "circa"
        )

        // Comprehensive English stopwords
        val englishStopwords = setOf(
            "the", "this", "that", "these", "those", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "having", "do", "does", "did", "doing",
            "with", "from", "they", "them", "their", "theirs", "what", "which", "who", "whom", "whose",
            "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other",
            "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "can", "will", "just", "should", "would", "could", "now", "you", "your", "yours", "we", "our",
            "ours", "he", "him", "his", "she", "her", "hers", "it", "its", "itself", "about", "into",
            "through", "during", "before", "after", "above", "below", "to", "of", "at", "by", "for",
            "there", "here", "also", "then", "first", "new", "because", "please", "thanks", "hello",
            "welcome", "sure", "well", "think", "know", "take", "make", "get", "go", "see", "come"
        )

        var englishCount = 0
        var italianCount = 0

        for (w in words) {
            if (italianStopwords.contains(w)) italianCount++
            if (englishStopwords.contains(w)) englishCount++
        }

        val itSuffixPatterns = listOf("zione", "zioni", "mento", "menti", "mente", "ità", "ivo", "iva", "oso", "osa", "ante", "ente", "iamo", "ebbe", "essero")
        val itSuffixMatches = itSuffixPatterns.count { clean.contains(it) }

        val enSuffixPatterns = listOf("ing ", "ed ", "tion", "ness", "ship", "ment ", "ly ")
        val enSuffixMatches = enSuffixPatterns.count { clean.contains(it) }

        val totalItalianScore = italianCount * 2 + itSuffixMatches
        val totalEnglishScore = englishCount * 2 + enSuffixMatches

        return when {
            totalItalianScore > totalEnglishScore -> Locale.ITALY
            totalEnglishScore > totalItalianScore -> Locale.US
            hint != null -> hint
            else -> defaultLocale
        }
    }

    fun speak(messageId: Long, text: String, startOffset: Int = 0) {
        if (!isInitialized || tts == null) {
            synchronized(pendingTasks) {
                pendingTasks.add { speak(messageId, text, startOffset) }
            }
            return
        }

        currentMessageId = messageId
        currentFullText = text
        val safeOffset = startOffset.coerceIn(0, text.length)
        currentStartOffset = safeOffset
        savedOffset = safeOffset

        if (safeOffset >= text.length) {
            stop()
            return
        }

        val chunks = createSpeechChunks(text, safeOffset, messageId)
        if (chunks.isEmpty()) {
            stop()
            return
        }

        lastUtteranceId = chunks.last().utteranceId

        val locale = detectLanguage(text)
        configureVoiceForLanguage(locale)

        _playbackState.value = TtsPlaybackState(
            messageId = messageId,
            isPlaying = true,
            isPaused = false,
            currentRange = Pair(safeOffset, safeOffset)
        )

        for (i in chunks.indices) {
            val chunk = chunks[i]
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunk.utteranceId)
            }
            tts?.speak(chunk.text, queueMode, params, chunk.utteranceId)
        }
    }

    private fun createSpeechChunks(fullText: String, startOffset: Int, messageId: Long): List<SpeechChunk> {
        val safeStart = startOffset.coerceIn(0, fullText.length)
        if (safeStart >= fullText.length) return emptyList()

        val maxChunkLen = 2000
        val chunks = mutableListOf<SpeechChunk>()
        var cursor = safeStart
        var chunkIndex = 0

        while (cursor < fullText.length) {
            val remainingLen = fullText.length - cursor
            if (remainingLen <= maxChunkLen) {
                val chunkText = fullText.substring(cursor)
                if (chunkText.isNotBlank()) {
                    val uid = "wyrd_${messageId}_${chunkIndex}_$cursor"
                    chunks.add(SpeechChunk(cursor, fullText.length, chunkText, uid))
                }
                break
            }

            // Find best split point before maxChunkLen
            val searchEnd = cursor + maxChunkLen
            val sub = fullText.substring(cursor, searchEnd)

            var splitPoint = -1
            val lastPara = sub.lastIndexOf("\n\n")
            if (lastPara > maxChunkLen / 2) {
                splitPoint = cursor + lastPara + 2
            } else {
                val lastLine = sub.lastIndexOf("\n")
                if (lastLine > maxChunkLen / 2) {
                    splitPoint = cursor + lastLine + 1
                } else {
                    val lastSentence = maxOf(
                        sub.lastIndexOf(". "),
                        sub.lastIndexOf("! "),
                        sub.lastIndexOf("? ")
                    )
                    if (lastSentence > maxChunkLen / 3) {
                        splitPoint = cursor + lastSentence + 2
                    } else {
                        val lastSpace = sub.lastIndexOf(" ")
                        splitPoint = if (lastSpace > 0) cursor + lastSpace + 1 else searchEnd
                    }
                }
            }

            val chunkText = fullText.substring(cursor, splitPoint)
            if (chunkText.isNotBlank()) {
                val uid = "wyrd_${messageId}_${chunkIndex}_$cursor"
                chunks.add(SpeechChunk(cursor, splitPoint, chunkText, uid))
                chunkIndex++
            }
            cursor = splitPoint
        }
        return chunks
    }

    fun pause() {
        if (_playbackState.value.isPlaying || isStreamingActive) {
            isStreamingPaused = true
            try {
                tts?.stop()
            } catch (_: Throwable) {}
            savedOffset = _playbackState.value.currentRange?.first ?: currentStartOffset
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = true
            )
            Log.d(TAG, "pause() invoked: savedOffset=$savedOffset, isStreamingActive=$isStreamingActive")
        }
    }

    fun resume() {
        val s = _playbackState.value
        isStreamingPaused = false
        if (s.isPaused && currentFullText.isNotEmpty()) {
            val targetId = s.messageId ?: currentMessageId ?: STREAMING_MESSAGE_ID
            speak(targetId, currentFullText, startOffset = savedOffset)
        }
    }

    fun startStreaming(messageId: Long = STREAMING_MESSAGE_ID, languageHint: Locale? = null) {
        if (!isInitialized || tts == null) {
            synchronized(pendingTasks) {
                pendingTasks.add { startStreaming(messageId, languageHint) }
            }
            return
        }
        stop()
        currentMessageId = messageId
        isStreamingActive = true
        isStreamingPaused = false
        streamingChunkIndex = 0
        streamingDetectedLocale = languageHint
        if (languageHint != null) {
            configureVoiceForLanguage(languageHint)
        }
        currentFullText = ""
        currentStartOffset = 0
        savedOffset = 0
        lastUtteranceId = null
        _playbackState.value = TtsPlaybackState(
            messageId = messageId,
            isPlaying = true,
            isPaused = false,
            currentRange = Pair(0, 0)
        )
    }

    fun enqueueStreamingChunk(chunkText: String, globalStartOffset: Int, fullTextSoFar: String = "") {
        if (!isInitialized || tts == null || chunkText.isBlank()) return

        // Continuously refine detected language with accumulated text if available
        val textToAnalyze = if (fullTextSoFar.isNotBlank()) fullTextSoFar else "$currentFullText $chunkText"
        val detected = detectLanguage(textToAnalyze, hint = streamingDetectedLocale)
        if (streamingDetectedLocale != detected) {
            streamingDetectedLocale = detected
            configureVoiceForLanguage(detected)
            Log.i(TAG, "Streaming TTS language configured to: $detected")
        }

        currentFullText = if (currentFullText.isEmpty()) chunkText else "$currentFullText $chunkText"

        // If user paused playback, DO NOT send utterance to TTS engine and DO NOT flip isPlaying to true!
        if (isStreamingPaused || _playbackState.value.isPaused) {
            _playbackState.value = _playbackState.value.copy(
                messageId = currentMessageId,
                isPlaying = false,
                isPaused = true
            )
            return
        }

        val uid = "wyrd_${currentMessageId}_${streamingChunkIndex}_$globalStartOffset"
        lastUtteranceId = uid
        val queueMode = if (streamingChunkIndex == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        streamingChunkIndex++

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid)
        }
        tts?.speak(chunkText, queueMode, params, uid)
        _playbackState.value = _playbackState.value.copy(
            messageId = currentMessageId,
            isPlaying = true,
            isPaused = false
        )
    }

    fun finishStreaming(finalMessageId: Long, fullPlainText: String) {
        isStreamingActive = false
        currentMessageId = finalMessageId
        currentFullText = fullPlainText
        if (isStreamingPaused || _playbackState.value.isPaused) {
            _playbackState.value = _playbackState.value.copy(
                messageId = finalMessageId,
                isPlaying = false,
                isPaused = true
            )
        } else if (_playbackState.value.isPlaying) {
            _playbackState.value = _playbackState.value.copy(messageId = finalMessageId)
        }
    }

    fun updateMessageId(newMessageId: Long) {
        currentMessageId = newMessageId
        if (_playbackState.value.messageId != null) {
            _playbackState.value = _playbackState.value.copy(messageId = newMessageId)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Throwable) {}
        isStreamingActive = false
        isStreamingPaused = false
        streamingChunkIndex = 0
        streamingDetectedLocale = null
        currentFullText = ""
        currentStartOffset = 0
        savedOffset = 0
        currentMessageId = null
        lastUtteranceId = null
        _playbackState.value = TtsPlaybackState()
    }

    fun seekTo(messageId: Long, text: String, charOffset: Int) {
        val wordStart = findWordStart(text, charOffset)
        stop()
        speak(messageId, text, startOffset = wordStart)
    }

    private fun findWordStart(text: String, charOffset: Int): Int {
        if (text.isEmpty()) return 0
        val safe = charOffset.coerceIn(0, text.length - 1)
        var i = safe
        while (i < text.length && text[i].isWhitespace()) {
            i++
        }
        if (i >= text.length) i = safe

        while (i > 0 && !text[i - 1].isWhitespace()) {
            i--
        }
        return i
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "NativeTtsManager"
        const val STREAMING_MESSAGE_ID = -999L
    }
}

private data class SpeechChunk(
    val globalStart: Int,
    val globalEnd: Int,
    val text: String,
    val utteranceId: String
)

