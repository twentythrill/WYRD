package com.wyrd.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nullverb_prefs")

enum class SupportedModel(val id: String, val displayName: String, val shortName: String) {
    MIMO_2_6_FLASH("mimo-v2.6-flash", "Xiaomi MiMo 2.6 Flash", "MiMo 2.6 Flash"),
    MIMO_2_6_PRO("mimo-v2.6-pro", "Xiaomi MiMo 2.6 Pro", "MiMo 2.6 Pro");

    companion object {
        fun fromId(id: String): SupportedModel {
            return when (id) {
                "mimo-v2.6-pro", "mimo-2.6-pro" -> MIMO_2_6_PRO
                "mimo-v2.6-flash", "mimo-v2.6", "mimo-2.6", "mimo-2.6-flash" -> MIMO_2_6_FLASH
                else -> entries.find { it.id == id } ?: MIMO_2_6_PRO
            }
        }
    }
}

enum class ReasoningEffort(val id: String, val displayName: String) {
    OFF("off", "Off"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High");

    companion object {
        fun fromId(id: String): ReasoningEffort {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class AppTheme(val id: String, val displayName: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(id: String): AppTheme {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
        }
    }
}

class PreferencesManager(private val context: Context) {

    private val fastCache: SharedPreferences =
        context.getSharedPreferences("wyrd_fast_cache", Context.MODE_PRIVATE)

    fun getCachedUsername(): String =
        fastCache.getString(KEY_USERNAME.name, DEFAULT_USERNAME) ?: DEFAULT_USERNAME

    fun getCachedSystemPrompt(): String =
        fastCache.getString(KEY_SYSTEM_PROMPT.name, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT

    fun getCachedActiveModel(): SupportedModel {
        val modelId = fastCache.getString(KEY_ACTIVE_MODEL.name, SupportedModel.MIMO_2_6_PRO.id)
            ?: SupportedModel.MIMO_2_6_PRO.id
        return SupportedModel.fromId(modelId)
    }

    fun getCachedThinkingEnabled(): Boolean =
        fastCache.getBoolean(KEY_THINKING_ENABLED.name, true)

    fun getCachedReasoningEffort(): ReasoningEffort {
        val effortId = fastCache.getString(KEY_REASONING_EFFORT.name, ReasoningEffort.MEDIUM.id)
            ?: ReasoningEffort.MEDIUM.id
        return ReasoningEffort.fromId(effortId)
    }

    fun getCachedAppTheme(): AppTheme {
        val themeId = fastCache.getString(KEY_APP_THEME.name, AppTheme.SYSTEM.id)
            ?: AppTheme.SYSTEM.id
        return AppTheme.fromId(themeId)
    }

    fun getCachedXiaomiMimoEnabled(): Boolean =
        fastCache.getBoolean(KEY_XIAOMI_ENABLED.name, false)

    fun getCachedOpenAiSttEnabled(): Boolean =
        fastCache.getBoolean(KEY_OPENAI_STT_ENABLED.name, false)

    fun getCachedExaSearchEnabled(): Boolean =
        fastCache.getBoolean(KEY_EXA_SEARCH_ENABLED.name, true)

    fun getCachedTavilySearchEnabled(): Boolean =
        fastCache.getBoolean(KEY_TAVILY_SEARCH_ENABLED.name, true)

    fun getCachedCustomBaseUrl(): String =
        fastCache.getString(KEY_CUSTOM_BASE_URL.name, "") ?: ""

    fun getCachedTtsEngine(): String =
        fastCache.getString(KEY_TTS_ENGINE.name, "") ?: ""

    fun getCachedTtsVoice(): String =
        fastCache.getString(KEY_TTS_VOICE.name, "") ?: ""

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.dataStore.data.collect { preferences ->
                    val editor = fastCache.edit()
                    preferences[KEY_USERNAME]?.let { editor.putString(KEY_USERNAME.name, it) }
                    preferences[KEY_SYSTEM_PROMPT]?.let { editor.putString(KEY_SYSTEM_PROMPT.name, it) }
                    preferences[KEY_ACTIVE_MODEL]?.let { editor.putString(KEY_ACTIVE_MODEL.name, it) }
                    preferences[KEY_THINKING_ENABLED]?.let { editor.putBoolean(KEY_THINKING_ENABLED.name, it) }
                    preferences[KEY_REASONING_EFFORT]?.let { editor.putString(KEY_REASONING_EFFORT.name, it) }
                    preferences[KEY_APP_THEME]?.let { editor.putString(KEY_APP_THEME.name, it) }
                    preferences[KEY_XIAOMI_ENABLED]?.let { editor.putBoolean(KEY_XIAOMI_ENABLED.name, it) }
                    preferences[KEY_OPENAI_STT_ENABLED]?.let { editor.putBoolean(KEY_OPENAI_STT_ENABLED.name, it) }
                    preferences[KEY_EXA_SEARCH_ENABLED]?.let { editor.putBoolean(KEY_EXA_SEARCH_ENABLED.name, it) }
                    preferences[KEY_TAVILY_SEARCH_ENABLED]?.let { editor.putBoolean(KEY_TAVILY_SEARCH_ENABLED.name, it) }
                    preferences[KEY_CUSTOM_BASE_URL]?.let { editor.putString(KEY_CUSTOM_BASE_URL.name, it) }
                    preferences[KEY_TTS_ENGINE]?.let { editor.putString(KEY_TTS_ENGINE.name, it) }
                    preferences[KEY_TTS_VOICE]?.let { editor.putString(KEY_TTS_VOICE.name, it) }
                    editor.apply()
                }
            } catch (_: Exception) {}
        }
    }

    val usernameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_USERNAME] ?: DEFAULT_USERNAME
    }

    val systemPromptFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
    }

    val activeModelFlow: Flow<SupportedModel> = context.dataStore.data.map { preferences ->
        val modelId = preferences[KEY_ACTIVE_MODEL] ?: SupportedModel.MIMO_2_6_PRO.id
        SupportedModel.fromId(modelId)
    }

    val isThinkingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val explicit = preferences[KEY_THINKING_ENABLED]
        if (explicit != null) {
            explicit
        } else {
            val effortId = preferences[KEY_REASONING_EFFORT]
            if (effortId != null) {
                effortId != "off"
            } else {
                true // Xiaomi MiMo models default to thinking enabled
            }
        }
    }

    val reasoningEffortFlow: Flow<ReasoningEffort> = context.dataStore.data.map { preferences ->
        val effortId = preferences[KEY_REASONING_EFFORT] ?: ReasoningEffort.MEDIUM.id
        ReasoningEffort.fromId(effortId)
    }

    val profileUpdatedAtFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_PROFILE_UPDATED_AT] ?: 0L
    }

    val isXiaomiMimoEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_XIAOMI_ENABLED] ?: false
    }

    val isOpenAiSttEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_OPENAI_STT_ENABLED] ?: false
    }

    val isExaSearchEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXA_SEARCH_ENABLED] ?: true
    }

    val isTavilySearchEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TAVILY_SEARCH_ENABLED] ?: true
    }

    val customBaseUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_BASE_URL] ?: ""
    }

    val appThemeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeId = preferences[KEY_APP_THEME] ?: AppTheme.SYSTEM.id
        AppTheme.fromId(themeId)
    }

    val ttsEngineFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_ENGINE] ?: ""
    }

    val ttsVoiceFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_VOICE] ?: ""
    }

    suspend fun setUsername(username: String, updatedAtMs: Long = System.currentTimeMillis()) {
        fastCache.edit().putString(KEY_USERNAME.name, username).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = username
            preferences[KEY_PROFILE_UPDATED_AT] = updatedAtMs
        }
    }

    suspend fun setSystemPrompt(prompt: String, updatedAtMs: Long = System.currentTimeMillis()) {
        fastCache.edit().putString(KEY_SYSTEM_PROMPT.name, prompt).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_SYSTEM_PROMPT] = prompt
            preferences[KEY_PROFILE_UPDATED_AT] = updatedAtMs
        }
    }

    suspend fun setProfileFromCloud(
        username: String,
        systemPrompt: String,
        activeModelId: String?,
        reasoningEffortId: String?,
        updatedAtMs: Long
    ) {
        val editor = fastCache.edit()
        if (username.isNotBlank()) {
            editor.putString(KEY_USERNAME.name, username)
        }
        editor.putString(KEY_SYSTEM_PROMPT.name, systemPrompt)
        if (!activeModelId.isNullOrBlank()) {
            editor.putString(KEY_ACTIVE_MODEL.name, SupportedModel.fromId(activeModelId).id)
        }
        if (!reasoningEffortId.isNullOrBlank()) {
            val effort = ReasoningEffort.fromId(reasoningEffortId)
            editor.putString(KEY_REASONING_EFFORT.name, effort.id)
            editor.putBoolean(KEY_THINKING_ENABLED.name, effort != ReasoningEffort.OFF)
        }
        editor.apply()

        context.dataStore.edit { preferences ->
            if (username.isNotBlank()) {
                preferences[KEY_USERNAME] = username
            }
            preferences[KEY_SYSTEM_PROMPT] = systemPrompt
            if (!activeModelId.isNullOrBlank()) {
                preferences[KEY_ACTIVE_MODEL] = SupportedModel.fromId(activeModelId).id
            }
            if (!reasoningEffortId.isNullOrBlank()) {
                val effort = ReasoningEffort.fromId(reasoningEffortId)
                preferences[KEY_REASONING_EFFORT] = effort.id
                preferences[KEY_THINKING_ENABLED] = (effort != ReasoningEffort.OFF)
            }
            preferences[KEY_PROFILE_UPDATED_AT] = updatedAtMs
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        fastCache.edit().putString(KEY_APP_THEME.name, theme.id).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_THEME] = theme.id
        }
    }

    suspend fun setTtsEngine(engine: String) {
        fastCache.edit().putString(KEY_TTS_ENGINE.name, engine.trim()).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_ENGINE] = engine.trim()
        }
    }

    suspend fun setTtsVoice(voice: String) {
        fastCache.edit().putString(KEY_TTS_VOICE.name, voice.trim()).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_VOICE] = voice.trim()
        }
    }

    suspend fun setActiveModel(model: SupportedModel) {
        fastCache.edit().putString(KEY_ACTIVE_MODEL.name, model.id).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_MODEL] = model.id
            preferences[KEY_PROFILE_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        val effortId = if (enabled) ReasoningEffort.MEDIUM.id else ReasoningEffort.OFF.id
        fastCache.edit()
            .putBoolean(KEY_THINKING_ENABLED.name, enabled)
            .putString(KEY_REASONING_EFFORT.name, effortId)
            .apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_THINKING_ENABLED] = enabled
            preferences[KEY_REASONING_EFFORT] = effortId
            preferences[KEY_PROFILE_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setReasoningEffort(effort: ReasoningEffort) {
        fastCache.edit()
            .putString(KEY_REASONING_EFFORT.name, effort.id)
            .putBoolean(KEY_THINKING_ENABLED.name, effort != ReasoningEffort.OFF)
            .apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_REASONING_EFFORT] = effort.id
            preferences[KEY_THINKING_ENABLED] = (effort != ReasoningEffort.OFF)
            preferences[KEY_PROFILE_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setXiaomiMimoEnabled(enabled: Boolean) {
        fastCache.edit().putBoolean(KEY_XIAOMI_ENABLED.name, enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_XIAOMI_ENABLED] = enabled
        }
    }

    suspend fun setOpenAiSttEnabled(enabled: Boolean) {
        fastCache.edit().putBoolean(KEY_OPENAI_STT_ENABLED.name, enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_OPENAI_STT_ENABLED] = enabled
        }
    }

    suspend fun setExaSearchEnabled(enabled: Boolean) {
        fastCache.edit().putBoolean(KEY_EXA_SEARCH_ENABLED.name, enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_EXA_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setTavilySearchEnabled(enabled: Boolean) {
        fastCache.edit().putBoolean(KEY_TAVILY_SEARCH_ENABLED.name, enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_TAVILY_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setCustomBaseUrl(url: String) {
        fastCache.edit().putString(KEY_CUSTOM_BASE_URL.name, url.trim()).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_BASE_URL] = url.trim()
        }
    }

    suspend fun clearAll() {
        fastCache.edit().clear().apply()
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        const val DEFAULT_USERNAME = "Human"
        const val DEFAULT_SYSTEM_PROMPT = ""

        private val KEY_USERNAME = stringPreferencesKey("user_name")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model_id")
        private val KEY_THINKING_ENABLED = booleanPreferencesKey("thinking_enabled")
        private val KEY_REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        private val KEY_PROFILE_UPDATED_AT = longPreferencesKey("profile_updated_at")
        private val KEY_XIAOMI_ENABLED = booleanPreferencesKey("is_xiaomi_mimo_enabled")
        private val KEY_OPENAI_STT_ENABLED = booleanPreferencesKey("is_openai_stt_enabled")
        private val KEY_EXA_SEARCH_ENABLED = booleanPreferencesKey("is_exa_search_enabled")
        private val KEY_TAVILY_SEARCH_ENABLED = booleanPreferencesKey("is_tavily_search_enabled")
        private val KEY_CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        private val KEY_APP_THEME = stringPreferencesKey("app_theme")
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine_package")
        private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice_name")
    }
}

