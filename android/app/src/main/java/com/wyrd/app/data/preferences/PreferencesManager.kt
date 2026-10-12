package com.wyrd.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    suspend fun setUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = username
        }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_THEME] = theme.id
        }
    }

    suspend fun setTtsEngine(engine: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_ENGINE] = engine.trim()
        }
    }

    suspend fun setTtsVoice(voice: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_VOICE] = voice.trim()
        }
    }

    suspend fun setActiveModel(model: SupportedModel) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_MODEL] = model.id
        }
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THINKING_ENABLED] = enabled
            preferences[KEY_REASONING_EFFORT] = if (enabled) "on" else "off"
        }
    }

    suspend fun setReasoningEffort(effort: ReasoningEffort) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REASONING_EFFORT] = effort.id
            preferences[KEY_THINKING_ENABLED] = (effort != ReasoningEffort.OFF)
        }
    }

    suspend fun setXiaomiMimoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_XIAOMI_ENABLED] = enabled
        }
    }

    suspend fun setOpenAiSttEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_OPENAI_STT_ENABLED] = enabled
        }
    }

    suspend fun setExaSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXA_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setTavilySearchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TAVILY_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun setCustomBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_BASE_URL] = url.trim()
        }
    }

    suspend fun clearAll() {
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
