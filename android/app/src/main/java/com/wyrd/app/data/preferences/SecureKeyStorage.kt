package com.wyrd.app.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import java.io.File

class SecureKeyStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefName = if (File(context.applicationInfo.dataDir, "shared_prefs/nullverb_secure_keys.xml").exists()) {
        "nullverb_secure_keys"
    } else {
        "wyrd_secure_keys"
    }

    private val sharedPreferences = try {
        createPrefs(context, prefName)
    } catch (e: Throwable) {
        try {
            createPrefs(context, prefName)
        } catch (_: Throwable) {
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        }
    }

    private fun createPrefs(ctx: Context, name: String) = EncryptedSharedPreferences.create(
        ctx,
        name,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getXiaomiApiKey(): String {
        return sharedPreferences.getString(KEY_XIAOMI_API_KEY, "") ?: ""
    }

    fun setXiaomiApiKey(key: String) {
        sharedPreferences.edit().putString(KEY_XIAOMI_API_KEY, key.trim()).apply()
    }

    fun getOpenAiApiKey(): String {
        return sharedPreferences.getString(KEY_OPENAI_API_KEY, "") ?: ""
    }

    fun setOpenAiApiKey(key: String) {
        sharedPreferences.edit().putString(KEY_OPENAI_API_KEY, key.trim()).apply()
    }

    fun getExaApiKey(): String {
        return sharedPreferences.getString(KEY_EXA_API_KEY, "") ?: ""
    }

    fun setExaApiKey(key: String) {
        sharedPreferences.edit().putString(KEY_EXA_API_KEY, key.trim()).apply()
    }

    fun getTavilyApiKey(): String {
        return sharedPreferences.getString(KEY_TAVILY_API_KEY, "") ?: ""
    }

    fun setTavilyApiKey(key: String) {
        sharedPreferences.edit().putString(KEY_TAVILY_API_KEY, key.trim()).apply()
    }

    fun clearAll() {
        sharedPreferences.edit().clear().commit()
    }

    fun getSupabaseAccessToken(): String {
        return sharedPreferences.getString(KEY_SUPABASE_ACCESS_TOKEN, "") ?: ""
    }

    fun setSupabaseAccessToken(token: String) {
        sharedPreferences.edit().putString(KEY_SUPABASE_ACCESS_TOKEN, token.trim()).apply()
    }

    fun getSupabaseRefreshToken(): String {
        return sharedPreferences.getString(KEY_SUPABASE_REFRESH_TOKEN, "") ?: ""
    }

    fun setSupabaseRefreshToken(token: String) {
        sharedPreferences.edit().putString(KEY_SUPABASE_REFRESH_TOKEN, token.trim()).apply()
    }

    fun getSupabaseUserEmail(): String {
        return sharedPreferences.getString(KEY_SUPABASE_USER_EMAIL, "") ?: ""
    }

    fun setSupabaseUserEmail(email: String) {
        sharedPreferences.edit().putString(KEY_SUPABASE_USER_EMAIL, email.trim()).apply()
    }

    fun getSupabaseUserId(): String {
        return sharedPreferences.getString(KEY_SUPABASE_USER_ID, "") ?: ""
    }

    fun setSupabaseUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_SUPABASE_USER_ID, userId.trim()).apply()
    }

    fun getKnownSyncedChatIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SYNCED_CHAT_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setKnownSyncedChatIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_SYNCED_CHAT_IDS, HashSet(ids)).apply()
    }

    fun addKnownSyncedChatId(id: String) {
        val current = getKnownSyncedChatIds().toMutableSet()
        if (current.add(id)) {
            setKnownSyncedChatIds(current)
        }
    }

    fun removeKnownSyncedChatId(id: String) {
        val current = getKnownSyncedChatIds().toMutableSet()
        if (current.remove(id)) {
            setKnownSyncedChatIds(current)
        }
    }

    fun getKnownSyncedProjectIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SYNCED_PROJECT_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setKnownSyncedProjectIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_SYNCED_PROJECT_IDS, HashSet(ids)).apply()
    }

    fun addKnownSyncedProjectId(id: String) {
        val current = getKnownSyncedProjectIds().toMutableSet()
        if (current.add(id)) {
            setKnownSyncedProjectIds(current)
        }
    }

    fun removeKnownSyncedProjectId(id: String) {
        val current = getKnownSyncedProjectIds().toMutableSet()
        if (current.remove(id)) {
            setKnownSyncedProjectIds(current)
        }
    }

    fun getKnownSyncedProjectFileIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SYNCED_PROJECT_FILE_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setKnownSyncedProjectFileIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_SYNCED_PROJECT_FILE_IDS, HashSet(ids)).apply()
    }

    fun getPendingDeletedChatIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_PENDING_DELETED_CHAT_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setPendingDeletedChatIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_PENDING_DELETED_CHAT_IDS, HashSet(ids)).apply()
    }

    fun addPendingDeletedChatId(id: String) {
        val current = getPendingDeletedChatIds().toMutableSet()
        if (current.add(id)) {
            setPendingDeletedChatIds(current)
        }
    }

    fun removePendingDeletedChatId(id: String) {
        val current = getPendingDeletedChatIds().toMutableSet()
        if (current.remove(id)) {
            setPendingDeletedChatIds(current)
        }
    }

    fun getPendingDeletedProjectIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_PENDING_DELETED_PROJECT_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setPendingDeletedProjectIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_PENDING_DELETED_PROJECT_IDS, HashSet(ids)).apply()
    }

    fun addPendingDeletedProjectId(id: String) {
        val current = getPendingDeletedProjectIds().toMutableSet()
        if (current.add(id)) {
            setPendingDeletedProjectIds(current)
        }
    }

    fun removePendingDeletedProjectId(id: String) {
        val current = getPendingDeletedProjectIds().toMutableSet()
        if (current.remove(id)) {
            setPendingDeletedProjectIds(current)
        }
    }

    fun getKnownSyncedMessageIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SYNCED_MESSAGE_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setKnownSyncedMessageIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_SYNCED_MESSAGE_IDS, HashSet(ids)).apply()
    }

    fun getPendingDeletedMessageIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_PENDING_DELETED_MESSAGE_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun setPendingDeletedMessageIds(ids: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_PENDING_DELETED_MESSAGE_IDS, HashSet(ids)).apply()
    }

    fun addPendingDeletedMessageIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val current = getPendingDeletedMessageIds().toMutableSet()
        if (current.addAll(ids)) {
            setPendingDeletedMessageIds(current)
        }
        val synced = getKnownSyncedMessageIds().toMutableSet()
        if (synced.removeAll(ids.toSet())) {
            setKnownSyncedMessageIds(synced)
        }
    }

    fun clearSupabaseAuth() {
        sharedPreferences.edit()
            .remove(KEY_SUPABASE_ACCESS_TOKEN)
            .remove(KEY_SUPABASE_REFRESH_TOKEN)
            .remove(KEY_SUPABASE_USER_EMAIL)
            .remove(KEY_SUPABASE_USER_ID)
            .remove(KEY_SYNCED_CHAT_IDS)
            .remove(KEY_SYNCED_MESSAGE_IDS)
            .remove(KEY_SYNCED_PROJECT_IDS)
            .remove(KEY_SYNCED_PROJECT_FILE_IDS)
            .remove(KEY_PENDING_DELETED_CHAT_IDS)
            .remove(KEY_PENDING_DELETED_MESSAGE_IDS)
            .remove(KEY_PENDING_DELETED_PROJECT_IDS)
            .apply()
    }

    companion object {
        private const val KEY_XIAOMI_API_KEY = "xiaomi_mimo_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_stt_api_key"
        private const val KEY_EXA_API_KEY = "exa_search_api_key"
        private const val KEY_TAVILY_API_KEY = "tavily_search_api_key"
        private const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"
        private const val KEY_SUPABASE_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_SUPABASE_USER_EMAIL = "supabase_user_email"
        private const val KEY_SUPABASE_USER_ID = "supabase_user_id"
        private const val KEY_SYNCED_CHAT_IDS = "supabase_synced_chat_ids"
        private const val KEY_SYNCED_MESSAGE_IDS = "supabase_synced_message_ids"
        private const val KEY_SYNCED_PROJECT_IDS = "supabase_synced_project_ids"
        private const val KEY_SYNCED_PROJECT_FILE_IDS = "supabase_synced_project_file_ids"
        private const val KEY_PENDING_DELETED_CHAT_IDS = "supabase_pending_deleted_chat_ids"
        private const val KEY_PENDING_DELETED_MESSAGE_IDS = "supabase_pending_deleted_message_ids"
        private const val KEY_PENDING_DELETED_PROJECT_IDS = "supabase_pending_deleted_project_ids"
    }
}


