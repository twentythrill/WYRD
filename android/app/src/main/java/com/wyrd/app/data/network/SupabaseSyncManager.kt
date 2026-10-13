package com.wyrd.app.data.network

import android.content.Context
import android.util.Log
import com.wyrd.app.data.local.AppDatabase
import com.wyrd.app.data.local.ChatEntity
import com.wyrd.app.data.local.MessageEntity
import com.wyrd.app.data.local.ProjectEntity
import com.wyrd.app.data.local.ProjectFileEntity
import com.wyrd.app.data.preferences.PreferencesManager
import com.wyrd.app.data.preferences.SecureKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseSyncManager(
    private val context: Context,
    private val secureKeyStorage: SecureKeyStorage,
    private val preferencesManager: PreferencesManager,
    private val database: AppDatabase
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val syncMutex = Mutex()
    private val deletedMessageSyncIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun parseIsoDate(isoStr: String?): Long {
        if (isoStr.isNullOrBlank()) return 0L
        return try {
            OffsetDateTime.parse(isoStr).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(isoStr).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun formatIsoDate(timeMs: Long): String {
        val validMs = if (timeMs <= 0L) System.currentTimeMillis() else timeMs
        return Instant.ofEpochMilli(validMs).toString()
    }

    // --- Authentication ---

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        val lowerEmail = trimmedEmail.lowercase(Locale.ROOT)

        var result = executeSignIn(trimmedEmail, password)
        if (result.isFailure && trimmedEmail != lowerEmail) {
            val retryResult = executeSignIn(lowerEmail, password)
            if (retryResult.isSuccess) {
                result = retryResult
            }
        }
        result
    }

    private suspend fun executeSignIn(cleanEmail: String, password: String): Result<String> {
        try {
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Client-Info", "wyrd-android/1.0")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            Log.d(TAG, "executeSignIn: attempting sign in for '$cleanEmail'")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "executeSignIn response: code=${response.code}, body=$body")

                if (!response.isSuccessful) {
                    val errJson = try { JSONObject(body) } catch (_: Exception) { null }
                    val desc = errJson?.optString("error_description", "")?.trim()?.ifEmpty { null }
                    val msgText = errJson?.optString("msg", "")?.trim()?.ifEmpty { null }
                    val messageText = errJson?.optString("message", "")?.trim()?.ifEmpty { null }
                    val errCode = errJson?.optString("error_code", "")?.trim()?.ifEmpty { null }

                    val rawMsg = desc ?: msgText ?: messageText ?: "Authentication failed (${response.code})"
                    val userFriendlyMsg = when (errCode) {
                        "invalid_credentials" -> "Invalid login credentials. Please check your email and password (tap the eye icon to verify spelling and case)."
                        "email_not_confirmed" -> "Email not confirmed. Please check your inbox for the Supabase confirmation link."
                        "over_email_send_rate_limit" -> "Email rate limit exceeded. Please wait a few minutes before trying again."
                        else -> rawMsg
                    }
                    return Result.failure(Exception(userFriendlyMsg))
                }

                val json = JSONObject(body)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", "")
                val user = json.getJSONObject("user")
                val userId = user.getString("id")
                val userEmail = user.optString("email", cleanEmail)

                secureKeyStorage.setSupabaseAccessToken(accessToken)
                secureKeyStorage.setSupabaseRefreshToken(refreshToken)
                secureKeyStorage.setSupabaseUserId(userId)
                secureKeyStorage.setSupabaseUserEmail(userEmail)

                // Trigger initial sync
                syncAll()

                return Result.success(userEmail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeSignIn error", e)
            return Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim()
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/signup")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Client-Info", "wyrd-android/1.0")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            Log.d(TAG, "signUp attempt for '$cleanEmail'")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "signUp response: code=${response.code}, body=$body")

                if (!response.isSuccessful) {
                    val errJson = try { JSONObject(body) } catch (_: Exception) { null }
                    val desc = errJson?.optString("error_description", "")?.trim()?.ifEmpty { null }
                    val msgText = errJson?.optString("msg", "")?.trim()?.ifEmpty { null }
                    val messageText = errJson?.optString("message", "")?.trim()?.ifEmpty { null }
                    val errCode = errJson?.optString("error_code", "")?.trim()?.ifEmpty { null }

                    val rawMsg = desc ?: msgText ?: messageText ?: "Sign up failed (${response.code})"
                    val userFriendlyMsg = when (errCode) {
                        "over_email_send_rate_limit" -> "Supabase email rate limit exceeded (3/hr limit). Please wait a few minutes before requesting another verification email."
                        "user_already_exists" -> "An account with this email already exists. Please switch to the 'Sign In' tab."
                        else -> rawMsg
                    }
                    return@withContext Result.failure(Exception(userFriendlyMsg))
                }

                val json = JSONObject(body)
                val accessToken = json.optString("access_token", "").trim()
                val user = json.optJSONObject("user")
                val userId = user?.optString("id", "")?.trim() ?: ""
                val userEmail = user?.optString("email", cleanEmail)?.trim() ?: cleanEmail

                if (accessToken.isNotBlank()) {
                    secureKeyStorage.setSupabaseAccessToken(accessToken)
                    secureKeyStorage.setSupabaseRefreshToken(json.optString("refresh_token", "").trim())
                    secureKeyStorage.setSupabaseUserId(userId)
                    secureKeyStorage.setSupabaseUserEmail(userEmail)
                    syncAll()
                    return@withContext Result.success(userEmail)
                } else {
                    return@withContext Result.failure(Exception("Account created! A confirmation email was sent to $cleanEmail. Please click the link in your email, then come back and Sign In."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "signUp error", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        secureKeyStorage.clearSupabaseAuth()
    }

    fun isUserSignedIn(): Boolean {
        return secureKeyStorage.getSupabaseAccessToken().isNotBlank() || secureKeyStorage.getSupabaseRefreshToken().isNotBlank()
    }

    fun getUserEmail(): String {
        return secureKeyStorage.getSupabaseUserEmail()
    }

    // --- JWT Expiration & Token Refresh ---

    private fun isJwtExpired(token: String): Boolean {
        if (token.isBlank()) return true
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val decodedBytes = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            val json = JSONObject(String(decodedBytes))
            val exp = json.optLong("exp", 0L)
            val nowSec = System.currentTimeMillis() / 1000
            // Refresh if expiring within 60 seconds
            nowSec >= (exp - 60)
        } catch (_: Exception) {
            true
        }
    }

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val currentToken = secureKeyStorage.getSupabaseAccessToken()
        if (currentToken.isNotBlank() && !isJwtExpired(currentToken)) {
            return@withContext currentToken
        }

        val refreshToken = secureKeyStorage.getSupabaseRefreshToken()
        if (refreshToken.isBlank()) {
            Log.w(TAG, "getValidAccessToken: No refresh token available to renew session")
            return@withContext if (currentToken.isNotBlank()) currentToken else null
        }

        Log.i(TAG, "getValidAccessToken: Access token is expired, refreshing via Supabase refresh_token...")
        val refreshResult = refreshSession(refreshToken)
        if (refreshResult.isSuccess) {
            return@withContext refreshResult.getOrNull()
        } else {
            Log.e(TAG, "getValidAccessToken: Token refresh failed", refreshResult.exceptionOrNull())
            return@withContext null
        }
    }

    private suspend fun refreshSession(refreshToken: String): Result<String> {
        try {
            val payload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Client-Info", "wyrd-android/1.0")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "refreshSession response: code=${response.code}")

                if (!response.isSuccessful) {
                    val errJson = try { JSONObject(body) } catch (_: Exception) { null }
                    val errCode = errJson?.optString("error_code", "") ?: ""
                    Log.w(TAG, "refreshSession failed: code=${response.code}, errCode=$errCode, body=$body")
                    if (errCode == "refresh_token_not_found" || response.code == 400 || response.code == 401) {
                        Log.w(TAG, "refreshSession: Token invalid/revoked, clearing local credentials")
                        signOut()
                    }
                    return Result.failure(Exception("Session expired. Please sign in again in Settings."))
                }

                val json = JSONObject(body)
                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val user = json.optJSONObject("user")
                if (user != null) {
                    val userId = user.optString("id", "")
                    if (userId.isNotBlank()) secureKeyStorage.setSupabaseUserId(userId)
                    val email = user.optString("email", "")
                    if (email.isNotBlank()) secureKeyStorage.setSupabaseUserEmail(email)
                }

                secureKeyStorage.setSupabaseAccessToken(newAccessToken)
                secureKeyStorage.setSupabaseRefreshToken(newRefreshToken)
                Log.i(TAG, "refreshSession: Successfully refreshed access token")
                return Result.success(newAccessToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshSession error", e)
            return Result.failure(e)
        }
    }

    // --- Two-Way Synchronization ---

    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "syncAll: Sync already in progress, skipping concurrent trigger")
            return@withContext Result.success(Unit)
        }
        try {
            val token = getValidAccessToken()
            val userId = secureKeyStorage.getSupabaseUserId()
            if (token.isNullOrBlank() || userId.isBlank()) {
                Log.w(TAG, "syncAll: Not authenticated or session expired")
                return@withContext Result.failure(Exception("Not authenticated with Supabase"))
            }

            // 1. Sync Profile
            syncProfile(token, userId)

            // 2. Sync Projects & Project Files
            syncProjects(token, userId)

            // 3. Sync Chats
            syncChats(token, userId)

            // 3. Sync Messages
            syncMessages(token, userId)

            // 4. Clean orphaned storage attachments
            try {
                cleanOrphanedStorageAttachments()
            } catch (e: Exception) {
                Log.w(TAG, "cleanOrphanedStorageAttachments error in syncAll", e)
            }

            Log.i(TAG, "syncAll: Two-way sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "syncAll error", e)
            Result.failure(e)
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncProfile(token: String, userId: String) {
        try {
            val getReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId&select=*")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(getReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "syncProfile get failed: ${resp.code}")
                    return
                }
                val body = resp.body?.string() ?: ""
                val arr = JSONArray(body)
                val localUsername = preferencesManager.usernameFlow.first()
                val localSystemPrompt = preferencesManager.systemPromptFlow.first()

                if (arr.length() > 0) {
                    val remoteProfile = arr.getJSONObject(0)
                    val remoteUsername = remoteProfile.optString("username", "")
                    val remoteSystemPrompt = remoteProfile.optString("system_prompt", "")

                    if (remoteUsername.isNotBlank() && remoteUsername != "Human" && localUsername == "Human") {
                        preferencesManager.setUsername(remoteUsername)
                    } else if (localUsername.isNotBlank() && localUsername != remoteUsername) {
                        updateRemoteProfile(token, userId, localUsername, localSystemPrompt)
                    }

                    if (remoteSystemPrompt.isNotBlank() && localSystemPrompt.isBlank()) {
                        preferencesManager.setSystemPrompt(remoteSystemPrompt)
                    } else if (localSystemPrompt.isNotBlank() && localSystemPrompt != remoteSystemPrompt) {
                        updateRemoteProfile(token, userId, localUsername, localSystemPrompt)
                    }
                } else {
                    updateRemoteProfile(token, userId, localUsername, localSystemPrompt)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncProfile error", e)
        }
    }

    private fun updateRemoteProfile(token: String, userId: String, username: String, systemPrompt: String) {
        try {
            val payload = JSONObject().apply {
                put("id", userId)
                put("username", username)
                put("system_prompt", systemPrompt)
                put("updated_at", formatIsoDate(System.currentTimeMillis()))
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?on_conflict=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "updateRemoteProfile failed: ${resp.code}")
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun syncProjects(token: String, userId: String) {
        val projectDao = database.projectDao()
        val projectFileDao = database.projectFileDao()

        // 1. Download remote projects
        val getReq = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/projects?select=*&order=is_pinned.desc,updated_at.desc,id.desc")
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val remoteArr: JSONArray
        try {
            val resp = client.newCall(getReq).execute()
            resp.use {
                if (!it.isSuccessful) {
                    val err = it.body?.string() ?: ""
                    Log.e(TAG, "syncProjects download failed: ${it.code} $err")
                    return
                }
                val body = it.body?.string() ?: "[]"
                remoteArr = JSONArray(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncProjects network error", e)
            return
        }

        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArr.length()) {
            val rObj = remoteArr.getJSONObject(i)
            val rId = rObj.optString("id", "")
            if (rId.isNotBlank()) {
                remoteMap[rId] = rObj
            }
        }

        // 2. Reconcile local projects
        val knownSyncedProjectIds = secureKeyStorage.getKnownSyncedProjectIds().toMutableSet()
        val pendingDeletedProjectIds = secureKeyStorage.getPendingDeletedProjectIds().toMutableSet()

        // Flush pending project deletions
        val projDeletionsToRemove = mutableSetOf<String>()
        for (delId in pendingDeletedProjectIds) {
            try {
                val req = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/projects?id=eq.$delId")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 404) {
                        projDeletionsToRemove.add(delId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to flush pending project deletion $delId", e)
            }
        }
        for (delId in projDeletionsToRemove) {
            secureKeyStorage.removePendingDeletedProjectId(delId)
            knownSyncedProjectIds.remove(delId)
        }
        val currentPendingDeletedProj = secureKeyStorage.getPendingDeletedProjectIds()

        val localProjects = projectDao.getAllProjectsList()
        val uploadArr = JSONArray()

        for (localProj in localProjects) {
            val syncId = localProj.syncId
            if (syncId.isNotBlank()) {
                if (currentPendingDeletedProj.contains(syncId)) {
                    projectDao.deleteProjectById(localProj.id)
                    projectFileDao.deleteFilesByProjectId(localProj.id)
                    database.chatDao().detachChatsFromProject(localProj.id)
                    continue
                }
                val rProj = remoteMap[syncId]
                if (rProj == null) {
                    val wasSynced = knownSyncedProjectIds.contains(syncId)
                    if (wasSynced) {
                        Log.i(TAG, "syncProjects: Project '${localProj.name}' deleted remotely. Removing locally.")
                        projectDao.deleteProjectById(localProj.id)
                        projectFileDao.deleteFilesByProjectId(localProj.id)
                        database.chatDao().detachChatsFromProject(localProj.id)
                        knownSyncedProjectIds.remove(syncId)
                    } else {
                        Log.i(TAG, "syncProjects: Local project '${localProj.name}' pending upload.")
                        uploadArr.put(JSONObject().apply {
                            put("id", syncId)
                            put("user_id", userId)
                            put("name", localProj.name)
                            put("description", localProj.description)
                            put("system_prompt", localProj.systemPrompt)
                            put("color", localProj.color)
                            put("is_pinned", localProj.isPinned)
                            put("created_at", formatIsoDate(localProj.createdAt))
                            put("updated_at", formatIsoDate(localProj.updatedAt))
                        })
                        knownSyncedProjectIds.add(syncId)
                    }
                } else {
                    knownSyncedProjectIds.add(syncId)
                    val rawUpdatedAt = parseIsoDate(rProj.optString("updated_at"))
                    val remoteUpdatedAt = if (rawUpdatedAt > 0L) rawUpdatedAt else localProj.updatedAt
                    val remoteName = rProj.optString("name", localProj.name)
                    val remoteDesc = rProj.optString("description", localProj.description)
                    val remotePrompt = rProj.optString("system_prompt", localProj.systemPrompt)
                    val remoteColor = rProj.optString("color", localProj.color)
                    val remotePinned = rProj.optBoolean("is_pinned", localProj.isPinned)

                    if (localProj.updatedAt > remoteUpdatedAt) {
                        uploadArr.put(JSONObject().apply {
                            put("id", localProj.syncId)
                            put("user_id", userId)
                            put("name", localProj.name)
                            put("description", localProj.description)
                            put("system_prompt", localProj.systemPrompt)
                            put("color", localProj.color)
                            put("is_pinned", localProj.isPinned)
                            put("created_at", formatIsoDate(localProj.createdAt))
                            put("updated_at", formatIsoDate(localProj.updatedAt))
                        })
                    } else if (remoteUpdatedAt > localProj.updatedAt || localProj.name != remoteName || localProj.description != remoteDesc || localProj.systemPrompt != remotePrompt || localProj.color != remoteColor || localProj.isPinned != remotePinned) {
                        projectDao.updateProject(
                            localProj.copy(
                                name = remoteName,
                                description = remoteDesc,
                                systemPrompt = remotePrompt,
                                color = remoteColor,
                                isPinned = remotePinned,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    }
                }
            }
        }

        // 3. Insert remote projects not present locally
        val localSyncIds = localProjects.mapNotNull { it.syncId.ifBlank { null } }.toSet()
        for ((remoteId, rProj) in remoteMap) {
            if (currentPendingDeletedProj.contains(remoteId)) continue
            knownSyncedProjectIds.add(remoteId)
            if (!localSyncIds.contains(remoteId)) {
                val name = rProj.optString("name", "Project")
                val description = rProj.optString("description", "")
                val systemPrompt = rProj.optString("system_prompt", "")
                val color = rProj.optString("color", "")
                val isPinned = rProj.optBoolean("is_pinned", false)
                val rawCreatedAt = parseIsoDate(rProj.optString("created_at"))
                val rawUpdatedAt = parseIsoDate(rProj.optString("updated_at"))
                val createdAt = if (rawCreatedAt > 0L) rawCreatedAt else System.currentTimeMillis()
                val updatedAt = if (rawUpdatedAt > 0L) rawUpdatedAt else createdAt

                projectDao.insertProject(
                    ProjectEntity(
                        name = name,
                        description = description,
                        systemPrompt = systemPrompt,
                        color = color,
                        isPinned = isPinned,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncId = remoteId
                    )
                )
            }
        }
        secureKeyStorage.setKnownSyncedProjectIds(knownSyncedProjectIds)

        // 4. Upload pending local project creations/updates
        if (uploadArr.length() > 0) {
            val uploadReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/projects?on_conflict=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(uploadArr.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(uploadReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    Log.e(TAG, "syncProjects upload failed: ${resp.code} $err")
                } else {
                    Log.d(TAG, "syncProjects uploaded ${uploadArr.length()} projects")
                }
            }
        }

        // 5. Sync Project Files
        syncProjectFiles(token, userId)
    }

    private suspend fun syncProjectFiles(token: String, userId: String) {
        val projectDao = database.projectDao()
        val projectFileDao = database.projectFileDao()

        val getReq = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/project_files?select=*&order=created_at.asc")
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val remoteArr: JSONArray
        try {
            val resp = client.newCall(getReq).execute()
            resp.use {
                if (!it.isSuccessful) return
                val body = it.body?.string() ?: "[]"
                remoteArr = JSONArray(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncProjectFiles download failed", e)
            return
        }

        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArr.length()) {
            val rObj = remoteArr.getJSONObject(i)
            val rId = rObj.optString("id", "")
            if (rId.isNotBlank()) remoteMap[rId] = rObj
        }

        val knownSyncedFileIds = secureKeyStorage.getKnownSyncedProjectFileIds().toMutableSet()
        val allLocalProjects = projectDao.getAllProjectsList()

        for (proj in allLocalProjects) {
            val localFiles = projectFileDao.getFilesByProjectList(proj.id)
            val uploadArr = JSONArray()

            for (lFile in localFiles) {
                val syncId = lFile.syncId
                if (syncId.isNotBlank()) {
                    val rFile = remoteMap[syncId]
                    if (rFile == null) {
                        val wasSynced = knownSyncedFileIds.contains(syncId)
                        val isRecentlyCreated = (System.currentTimeMillis() - lFile.createdAt) < 120_000L
                        if (wasSynced && !isRecentlyCreated) {
                            projectFileDao.deleteFileById(lFile.id)
                            knownSyncedFileIds.remove(syncId)
                        } else {
                            uploadArr.put(JSONObject().apply {
                                put("id", syncId)
                                put("project_id", proj.syncId)
                                put("user_id", userId)
                                put("name", lFile.name)
                                put("size", lFile.size)
                                put("mime_type", lFile.mimeType)
                                put("storage_path", lFile.storagePath)
                                put("text_content", lFile.textContent ?: JSONObject.NULL)
                                put("created_at", formatIsoDate(lFile.createdAt))
                            })
                            knownSyncedFileIds.add(syncId)
                        }
                    } else {
                        knownSyncedFileIds.add(syncId)
                    }
                }
            }

            if (uploadArr.length() > 0) {
                val uploadReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/project_files?on_conflict=id")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(uploadArr.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(uploadReq).execute().close()
            }
        }

        // Insert remote files that do not exist locally
        for ((remoteId, rFile) in remoteMap) {
            knownSyncedFileIds.add(remoteId)
            val existing = projectFileDao.getFileBySyncId(remoteId)
            if (existing == null) {
                val projSyncId = rFile.optString("project_id", "")
                val localProj = projectDao.getProjectBySyncId(projSyncId)
                if (localProj != null) {
                    val name = rFile.optString("name", "file")
                    val size = rFile.optLong("size", 0L)
                    val mimeType = rFile.optString("mime_type", "application/octet-stream")
                    val storagePath = rFile.optString("storage_path", "")
                    val textContent = if (rFile.isNull("text_content")) null else rFile.optString("text_content", "")
                    val rawCreatedAt = parseIsoDate(rFile.optString("created_at"))
                    val createdAt = if (rawCreatedAt > 0L) rawCreatedAt else System.currentTimeMillis()

                    projectFileDao.insertFile(
                        ProjectFileEntity(
                            syncId = remoteId,
                            projectId = localProj.id,
                            projectSyncId = projSyncId,
                            name = name,
                            size = size,
                            mimeType = mimeType,
                            storagePath = storagePath,
                            textContent = textContent,
                            createdAt = createdAt
                        )
                    )
                }
            }
        }

        secureKeyStorage.setKnownSyncedProjectFileIds(knownSyncedFileIds)
    }

    suspend fun deleteProjectRemotely(projectSyncId: String) = withContext(Dispatchers.IO) {
        secureKeyStorage.addPendingDeletedProjectId(projectSyncId)
        secureKeyStorage.removeKnownSyncedProjectId(projectSyncId)
        val token = getValidAccessToken() ?: return@withContext
        try {
            // First fetch files of this project to delete from storage
            try {
                val getFilesReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/project_files?project_id=eq.$projectSyncId&select=storage_path")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(getFilesReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        val paths = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val sp = arr.getJSONObject(i).optString("storage_path", "")
                            if (sp.isNotBlank()) paths.add(sp)
                        }
                        if (paths.isNotEmpty()) {
                            deleteStorageAttachments(paths)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean storage files for deleted project $projectSyncId", e)
            }

            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/projects?id=eq.$projectSyncId")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 404) {
                    secureKeyStorage.removePendingDeletedProjectId(projectSyncId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteProjectRemotely error", e)
        }
    }

    suspend fun deleteProjectFileRemotely(fileSyncId: String, storagePath: String) = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext
        val req = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/project_files?id=eq.$fileSyncId")
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(req).execute().close()
        if (storagePath.isNotBlank()) {
            deleteStorageAttachments(listOf(storagePath))
        }
    }

    private suspend fun syncChats(token: String, userId: String) {
        val chatDao = database.chatDao()
        val messageDao = database.messageDao()

        // 1. Download remote chats first
        val getReq = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/chats?select=*&order=is_pinned.desc,updated_at.desc,id.desc")
            .addHeader("apikey", SUPABASE_KEY)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val remoteArr: JSONArray
        try {
            val resp = client.newCall(getReq).execute()
            resp.use {
                if (!it.isSuccessful) {
                    val err = it.body?.string() ?: ""
                    Log.e(TAG, "syncChats download failed: ${it.code} $err")
                    return
                }
                val body = it.body?.string() ?: "[]"
                remoteArr = JSONArray(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncChats network error fetching remote chats", e)
            return
        }

        // Map remote chats by their id
        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArr.length()) {
            val rObj = remoteArr.getJSONObject(i)
            val rId = rObj.optString("id", "")
            if (rId.isNotBlank()) {
                remoteMap[rId] = rObj
            }
        }

        // 2. Reconcile local chats against remote
        val knownSyncedIds = secureKeyStorage.getKnownSyncedChatIds().toMutableSet()
        val pendingDeletedChatIds = secureKeyStorage.getPendingDeletedChatIds().toMutableSet()

        // Flush pending chat deletions to Supabase
        val chatDeletionsToRemove = mutableSetOf<String>()
        for (delId in pendingDeletedChatIds) {
            try {
                val req = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/chats?id=eq.$delId")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 404) {
                        chatDeletionsToRemove.add(delId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to flush pending chat deletion $delId", e)
            }
        }
        for (delId in chatDeletionsToRemove) {
            secureKeyStorage.removePendingDeletedChatId(delId)
            knownSyncedIds.remove(delId)
        }
        val currentPendingDeletedChats = secureKeyStorage.getPendingDeletedChatIds()

        val localChats = chatDao.getAllChatsList()
        val uploadArr = JSONArray()

        for (localChat in localChats) {
            val syncId = localChat.syncId
            if (syncId.isNotBlank()) {
                if (currentPendingDeletedChats.contains(syncId)) {
                    val msgs = messageDao.getMessagesListForChat(localChat.id)
                    com.wyrd.app.util.AttachmentHelper.cleanupAttachments(context, msgs)
                    chatDao.deleteChatById(localChat.id)
                    messageDao.deleteMessagesForChat(localChat.id)
                    continue
                }
                val rChat = remoteMap[syncId]
                if (rChat == null) {
                    val wasSyncedRemotely = knownSyncedIds.contains(syncId)

                    if (wasSyncedRemotely) {
                        // Chat was previously synced to remote, and was deleted on remote!
                        // Remove locally so it does NOT resurrect!
                        Log.i(TAG, "syncChats: Chat '${localChat.title}' ($syncId) was deleted remotely. Removing locally.")
                        val msgs = messageDao.getMessagesListForChat(localChat.id)
                        com.wyrd.app.util.AttachmentHelper.cleanupAttachments(context, msgs)
                        chatDao.deleteChatById(localChat.id)
                        messageDao.deleteMessagesForChat(localChat.id)
                        knownSyncedIds.remove(syncId)
                    } else {
                        // Brand new local chat -> pending upload to remote!
                        Log.i(TAG, "syncChats: Local chat '${localChat.title}' ($syncId) pending upload.")
                        uploadArr.put(JSONObject().apply {
                            put("id", syncId)
                            put("user_id", userId)
                            put("title", localChat.title)
                            put("is_pinned", localChat.isPinned)
                            if (localChat.projectSyncId.isNullOrBlank()) {
                                put("project_id", JSONObject.NULL)
                            } else {
                                put("project_id", localChat.projectSyncId)
                            }
                            put("created_at", formatIsoDate(localChat.createdAt))
                            put("updated_at", formatIsoDate(localChat.updatedAt))
                        })
                        knownSyncedIds.add(syncId)
                    }
                } else {
                    // Chat exists on both sides
                    knownSyncedIds.add(syncId)
                    val rawUpdatedAt = parseIsoDate(rChat.optString("updated_at"))
                    val remoteUpdatedAt = if (rawUpdatedAt > 0L) rawUpdatedAt else localChat.updatedAt
                    val remoteTitle = rChat.optString("title", localChat.title)
                    val remotePinned = rChat.optBoolean("is_pinned", localChat.isPinned)
                    val remoteProjectSyncId = if (rChat.isNull("project_id")) null else rChat.optString("project_id", "").ifEmpty { null }
                    
                    // Keep local project association if remote is null but local had one
                    val effectiveProjectSyncId = remoteProjectSyncId ?: localChat.projectSyncId
                    val localProjectId = if (!effectiveProjectSyncId.isNullOrBlank()) {
                        database.projectDao().getProjectBySyncId(effectiveProjectSyncId)?.id ?: localChat.projectId
                    } else localChat.projectId

                    if (localChat.updatedAt > remoteUpdatedAt) {
                        // Local has newer updates -> stage for upload
                        uploadArr.put(JSONObject().apply {
                            put("id", localChat.syncId)
                            put("user_id", userId)
                            put("title", localChat.title)
                            put("is_pinned", localChat.isPinned)
                            if (localChat.projectSyncId.isNullOrBlank()) {
                                put("project_id", JSONObject.NULL)
                            } else {
                                put("project_id", localChat.projectSyncId)
                            }
                            put("created_at", formatIsoDate(localChat.createdAt))
                            put("updated_at", formatIsoDate(localChat.updatedAt))
                        })
                    } else if (remoteUpdatedAt > localChat.updatedAt || localChat.title != remoteTitle || localChat.isPinned != remotePinned || (remoteProjectSyncId != null && localChat.projectSyncId != remoteProjectSyncId)) {
                        // Remote has newer updates -> update local SQLite
                        chatDao.updateChat(
                            localChat.copy(
                                title = remoteTitle,
                                isPinned = remotePinned,
                                updatedAt = remoteUpdatedAt,
                                projectId = localProjectId,
                                projectSyncId = effectiveProjectSyncId
                            )
                        )
                    }
                }
            } else {
                val newSyncId = UUID.randomUUID().toString()
                chatDao.updateChat(localChat.copy(syncId = newSyncId))
                uploadArr.put(JSONObject().apply {
                    put("id", newSyncId)
                    put("user_id", userId)
                    put("title", localChat.title)
                    put("is_pinned", localChat.isPinned)
                    if (localChat.projectSyncId.isNullOrBlank()) {
                        put("project_id", JSONObject.NULL)
                    } else {
                        put("project_id", localChat.projectSyncId)
                    }
                    put("created_at", formatIsoDate(localChat.createdAt))
                    put("updated_at", formatIsoDate(localChat.updatedAt))
                })
                knownSyncedIds.add(newSyncId)
            }
        }

        // 3. Insert remote chats that do not exist locally
        val localSyncIds = localChats.mapNotNull { it.syncId.ifBlank { null } }.toSet()
        for ((remoteId, rChat) in remoteMap) {
            if (currentPendingDeletedChats.contains(remoteId)) continue
            knownSyncedIds.add(remoteId)
            if (!localSyncIds.contains(remoteId)) {
                val title = rChat.optString("title", "New Chat")
                val isPinned = rChat.optBoolean("is_pinned", false)
                val rawCreatedAt = parseIsoDate(rChat.optString("created_at"))
                val rawUpdatedAt = parseIsoDate(rChat.optString("updated_at"))
                val createdAt = if (rawCreatedAt > 0L) rawCreatedAt else System.currentTimeMillis()
                val updatedAt = if (rawUpdatedAt > 0L) rawUpdatedAt else createdAt
                val remoteProjectSyncId = if (rChat.isNull("project_id")) null else rChat.optString("project_id", "").ifEmpty { null }
                val localProjectId = if (!remoteProjectSyncId.isNullOrBlank()) {
                    database.projectDao().getProjectBySyncId(remoteProjectSyncId)?.id
                } else null

                chatDao.insertChat(
                    ChatEntity(
                        title = title,
                        isPinned = isPinned,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncId = remoteId,
                        projectId = localProjectId,
                        projectSyncId = remoteProjectSyncId
                    )
                )
            }
        }

        // Save updated known synced chat IDs
        secureKeyStorage.setKnownSyncedChatIds(knownSyncedIds)

        // 4. Upload pending local updates
        if (uploadArr.length() > 0) {
            val uploadReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/chats?on_conflict=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(uploadArr.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(uploadReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    Log.e(TAG, "syncChats upload failed: ${resp.code} $err")
                } else {
                    Log.d(TAG, "syncChats uploaded ${uploadArr.length()} local chats")
                }
            }
        }

        // 5. Clean up any orphaned messages
        messageDao.deleteOrphanedMessages()

        // 6. Backfill local projectId for any chats where projectSyncId is set but projectId is null
        try {
            val chatsNeedingProjectId = chatDao.getAllChatsList().filter { !it.projectSyncId.isNullOrBlank() && it.projectId == null }
            for (c in chatsNeedingProjectId) {
                val proj = database.projectDao().getProjectBySyncId(c.projectSyncId!!)
                if (proj != null) {
                    chatDao.updateChat(c.copy(projectId = proj.id))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backfill chats projectId error", e)
        }
    }

    suspend fun deleteMessagesRemotely(
        chatSyncId: String,
        messageSyncIds: List<String>,
        fromTimestamp: Long? = null,
        inclusive: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext
        val userId = secureKeyStorage.getSupabaseUserId()
        if (userId.isBlank() || chatSyncId.isBlank()) return@withContext

        // Mark immediately so any concurrent or future sync passes ignore them
        deletedMessageSyncIds.addAll(messageSyncIds)

        try {
            // 1. Delete storage attachments if any
            if (messageSyncIds.isNotEmpty()) {
                val idsParam = messageSyncIds.joinToString(",")
                val getAttReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/messages?id=in.($idsParam)&select=attachments")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val storagePaths = mutableListOf<String>()
                try {
                    client.newCall(getAttReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string() ?: "[]")
                            for (i in 0 until arr.length()) {
                                val msgObj = arr.getJSONObject(i)
                                val attArr = msgObj.optJSONArray("attachments") ?: JSONArray()
                                for (j in 0 until attArr.length()) {
                                    val item = attArr.optJSONObject(j) ?: continue
                                    val uri = item.optString("uri", "")
                                    extractStoragePath(uri)?.let { storagePaths.add(it) }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (storagePaths.isNotEmpty()) {
                    deleteStorageAttachments(storagePaths)
                }

                // 2. Delete messages by syncId in Supabase
                val delReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/messages?id=in.($idsParam)")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                client.newCall(delReq).execute().use { resp ->
                    Log.d(TAG, "deleteMessagesRemotely by id response: ${resp.code}")
                }
            } else if (fromTimestamp != null && fromTimestamp > 0L) {
                // 3. Fallback to delete by timestamp in Supabase only if no explicit message IDs were provided
                val iso = formatIsoDate(fromTimestamp)
                val op = if (inclusive) "gte" else "gt"
                val delTimeReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/messages?chat_id=eq.$chatSyncId&created_at=$op.$iso")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                client.newCall(delTimeReq).execute().use { resp ->
                    Log.d(TAG, "deleteMessagesRemotely by time response: ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteMessagesRemotely error", e)
        }
    }

    private suspend fun syncMessages(token: String, userId: String) {
        val chatDao = database.chatDao()
        val messageDao = database.messageDao()
        val localChats = chatDao.getAllChatsList()
        val knownSynced = secureKeyStorage.getKnownSyncedChatIds()

        for (chat in localChats) {
            val syncId = chat.syncId
            if (syncId.isBlank()) continue

            // 1. Download remote messages for this chat first so we know what remote contains
            val getReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/messages?chat_id=eq.$syncId&select=*&order=created_at.asc")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val remoteMsgs = try {
                client.newCall(getReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string() ?: ""
                        Log.e(TAG, "syncMessages download failed for chat ${chat.id}: ${resp.code} $err")
                        null
                    } else {
                        val body = resp.body?.string() ?: ""
                        JSONArray(body)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "syncMessages fetch remote error for chat ${chat.id}", e)
                null
            } ?: continue

            val remoteSyncIds = HashSet<String>()
            for (i in 0 until remoteMsgs.length()) {
                remoteSyncIds.add(remoteMsgs.getJSONObject(i).getString("id"))
            }

            // 2. Upload local messages for this chat (filtering out deleted messages)
            val localMsgs = messageDao.getMessagesListForChat(chat.id)
            val uploadArr = JSONArray()
            for (msg in localMsgs) {
                if (deletedMessageSyncIds.contains(msg.syncId)) {
                    messageDao.deleteMessageById(msg.id)
                    continue
                }

                var msgSyncId = msg.syncId
                if (msgSyncId.isBlank()) {
                    msgSyncId = UUID.randomUUID().toString()
                    messageDao.insertMessage(msg.copy(syncId = msgSyncId))
                }

                val attArray = if (!msg.attachmentsJson.isNullOrBlank()) {
                    try { JSONArray(msg.attachmentsJson) } catch (_: Exception) { JSONArray() }
                } else {
                    JSONArray()
                }

                var modified = false
                var finalAttachmentUri = msg.attachmentUri
                for (j in 0 until attArray.length()) {
                    val item = attArray.optJSONObject(j) ?: continue
                    val uri = item.optString("uri", "")
                    val name = item.optString("name", "attachment")
                    if (uri.isNotBlank() && !uri.startsWith("http://") && !uri.startsWith("https://")) {
                        val publicUrl = uploadAttachmentToStorage(uri, name)
                        if (publicUrl != null) {
                            item.put("uri", publicUrl)
                            modified = true
                            if (j == 0) finalAttachmentUri = publicUrl
                        } else {
                            if (!uri.startsWith("data:")) {
                                val b64 = com.wyrd.app.util.AttachmentHelper.getBase64FromPath(uri)
                                if (!b64.isNullOrBlank()) {
                                    item.put("uri", "data:image/jpeg;base64,$b64")
                                }
                            }
                        }
                    }
                }

                if (modified) {
                    try {
                        val updated = msg.copy(
                            syncId = msgSyncId,
                            attachmentsJson = attArray.toString(),
                            attachmentUri = finalAttachmentUri
                        )
                        messageDao.insertMessage(updated)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed updating local message attachments in syncMessages", e)
                    }
                }

                uploadArr.put(JSONObject().apply {
                    put("id", msgSyncId)
                    put("chat_id", syncId)
                    put("user_id", userId)
                    put("role", msg.role)
                    put("content", msg.content)
                    if (!msg.reasoning.isNullOrBlank()) {
                        put("reasoning", msg.reasoning)
                    } else {
                        put("reasoning", JSONObject.NULL)
                    }
                    put("attachments", attArray)
                    put("created_at", formatIsoDate(msg.timestamp))
                })
            }

            if (uploadArr.length() > 0) {
                val uploadReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/messages?on_conflict=id")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(uploadArr.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(uploadReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string() ?: ""
                        Log.e(TAG, "syncMessages upload failed for chat ${chat.id}: ${resp.code} $err")
                    } else {
                        Log.d(TAG, "syncMessages uploaded ${uploadArr.length()} messages for chat ${chat.id}")
                    }
                }
            }

            // 3. Insert remote messages into Room if not present locally and not in deletedMessageSyncIds
            for (i in 0 until remoteMsgs.length()) {
                val rMsg = remoteMsgs.getJSONObject(i)
                val rMsgId = rMsg.getString("id")
                if (deletedMessageSyncIds.contains(rMsgId)) {
                    continue
                }
                val role = rMsg.getString("role")
                val content = rMsg.optString("content", "")
                val reasoning = if (rMsg.has("reasoning") && !rMsg.isNull("reasoning")) {
                    rMsg.getString("reasoning")
                } else null
                val attachmentsArr = rMsg.optJSONArray("attachments")
                val attachmentsJson = if (attachmentsArr != null && attachmentsArr.length() > 0) attachmentsArr.toString() else null
                val firstAtt = if (attachmentsArr != null && attachmentsArr.length() > 0) attachmentsArr.getJSONObject(0) else null
                val attachmentUri = firstAtt?.optString("uri")
                val attachmentType = firstAtt?.optString("type")
                val attachmentName = firstAtt?.optString("name")
                val rawTimestamp = parseIsoDate(rMsg.optString("created_at"))
                val timestamp = if (rawTimestamp > 0L) rawTimestamp else System.currentTimeMillis()

                val existingMsg = messageDao.getMessageBySyncId(rMsgId)
                if (existingMsg == null) {
                    messageDao.insertMessage(
                        MessageEntity(
                            chatId = chat.id,
                            role = role,
                            content = content,
                            reasoning = reasoning,
                            timestamp = timestamp,
                            attachmentUri = attachmentUri,
                            attachmentType = attachmentType,
                            attachmentName = attachmentName,
                            attachmentsJson = attachmentsJson,
                            syncId = rMsgId
                        )
                    )
                }
            }
        }
    }

    // --- Fast Background Push on message creation ---
    suspend fun pushMessageAsync(chatSyncId: String, message: MessageEntity) = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext
        val userId = secureKeyStorage.getSupabaseUserId()
        if (userId.isBlank() || chatSyncId.isBlank()) return@withContext

        try {
            val attArray = if (!message.attachmentsJson.isNullOrBlank()) {
                try { JSONArray(message.attachmentsJson) } catch (_: Exception) { JSONArray() }
            } else {
                JSONArray()
            }

            var modified = false
            var finalAttachmentUri = message.attachmentUri
            for (j in 0 until attArray.length()) {
                val item = attArray.optJSONObject(j) ?: continue
                val uri = item.optString("uri", "")
                val name = item.optString("name", "attachment")
                if (uri.isNotBlank() && !uri.startsWith("http://") && !uri.startsWith("https://")) {
                    val publicUrl = uploadAttachmentToStorage(uri, name)
                    if (publicUrl != null) {
                        item.put("uri", publicUrl)
                        modified = true
                        if (j == 0) finalAttachmentUri = publicUrl
                    } else {
                        // Fallback: if Storage upload failed, encode compressed image to base64 data URI
                        // so that cross-device sync renders the image immediately without breaking
                        if (!uri.startsWith("data:")) {
                            val b64 = com.wyrd.app.util.AttachmentHelper.getBase64FromPath(uri)
                            if (!b64.isNullOrBlank()) {
                                item.put("uri", "data:image/jpeg;base64,$b64")
                            }
                        }
                    }
                }
            }

            if (modified) {
                try {
                    val updated = message.copy(
                        attachmentsJson = attArray.toString(),
                        attachmentUri = finalAttachmentUri
                    )
                    database.messageDao().insertMessage(updated)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed updating local message attachments in pushMessageAsync", e)
                }
            }

            val payload = JSONObject().apply {
                put("id", message.syncId)
                put("chat_id", chatSyncId)
                put("user_id", userId)
                put("role", message.role)
                put("content", message.content)
                if (!message.reasoning.isNullOrBlank()) {
                    put("reasoning", message.reasoning)
                } else {
                    put("reasoning", JSONObject.NULL)
                }
                put("attachments", attArray)
                put("created_at", formatIsoDate(message.timestamp))
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/messages?on_conflict=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "pushMessageAsync error: ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "pushMessageAsync failed", e)
        }
    }

    // --- Remote Deletion ---
    suspend fun deleteChat(chatSyncId: String) = withContext(Dispatchers.IO) {
        secureKeyStorage.addPendingDeletedChatId(chatSyncId)
        secureKeyStorage.removeKnownSyncedChatId(chatSyncId)
        val token = getValidAccessToken() ?: return@withContext
        try {
            // 1. Fetch remote attachments to delete from Supabase Storage
            val getAttReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/messages?chat_id=eq.$chatSyncId&select=attachments")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val storagePaths = mutableListOf<String>()
            try {
                client.newCall(getAttReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val msgObj = arr.getJSONObject(i)
                            val attArr = msgObj.optJSONArray("attachments") ?: JSONArray()
                            for (j in 0 until attArr.length()) {
                                val att = attArr.getJSONObject(j)
                                val uri = att.optString("uri", "")
                                extractStoragePath(uri)?.let { storagePaths.add(it) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching attachments before chat delete", e)
            }

            if (storagePaths.isNotEmpty()) {
                deleteStorageAttachments(storagePaths)
            }

            val msgReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/messages?chat_id=eq.$chatSyncId")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            try { client.newCall(msgReq).execute().close() } catch (_: Exception) {}

            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/chats?id=eq.$chatSyncId")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                Log.d(TAG, "deleteChat remote response: ${resp.code}")
                if (resp.isSuccessful || resp.code == 404) {
                    secureKeyStorage.removePendingDeletedChatId(chatSyncId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteChat remote error", e)
        }
    }

    suspend fun deleteUnpinnedChats() = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext
        val userId = secureKeyStorage.getSupabaseUserId()
        if (userId.isBlank()) return@withContext
        try {
            val getReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/chats?is_pinned=eq.false&user_id=eq.$userId&select=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val chatIds = mutableListOf<String>()
            try {
                client.newCall(getReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val cid = arr.getJSONObject(i).getString("id")
                            chatIds.add(cid)
                            secureKeyStorage.removeKnownSyncedChatId(cid)
                        }
                    }
                }
            } catch (_: Exception) {}

            if (chatIds.isNotEmpty()) {
                val idsParam = chatIds.joinToString(",")
                val getAttReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/messages?chat_id=in.($idsParam)&select=attachments")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val storagePaths = mutableListOf<String>()
                try {
                    client.newCall(getAttReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string() ?: "[]")
                            for (i in 0 until arr.length()) {
                                val msgObj = arr.getJSONObject(i)
                                val attArr = msgObj.optJSONArray("attachments") ?: JSONArray()
                                for (j in 0 until attArr.length()) {
                                    val att = attArr.getJSONObject(j)
                                    val uri = att.optString("uri", "")
                                    extractStoragePath(uri)?.let { storagePaths.add(it) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching attachments for unpinned chats", e)
                }

                if (storagePaths.isNotEmpty()) {
                    deleteStorageAttachments(storagePaths)
                }

                for (cId in chatIds) {
                    val delMsgReq = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/messages?chat_id=eq.$cId")
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer $token")
                        .delete()
                        .build()
                    try { client.newCall(delMsgReq).execute().close() } catch (_: Exception) {}
                }
            }

            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/chats?is_pinned=eq.false&user_id=eq.$userId")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                Log.d(TAG, "deleteUnpinnedChats remote response: ${resp.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteUnpinnedChats remote error", e)
        }
    }

    // --- Supabase Storage Upload ---
    suspend fun uploadAttachmentToStorage(localFilePath: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(localFilePath)
        if (!file.exists()) return@withContext null
        val token = getValidAccessToken() ?: return@withContext null
        val userId = secureKeyStorage.getSupabaseUserId()
        if (userId.isBlank()) return@withContext null

        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val storagePath = "$userId/${System.currentTimeMillis()}_$safeName"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }

        try {
            val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
            val requestBody = file.asRequestBody(mediaType)
            val req = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/attachments/$storagePath")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val publicUrl = "$SUPABASE_URL/storage/v1/object/public/attachments/$storagePath"
                    Log.d(TAG, "Uploaded attachment to Supabase Storage: $publicUrl")
                    return@withContext publicUrl
                } else {
                    val err = resp.body?.string() ?: ""
                    Log.w(TAG, "Supabase Storage upload failed: ${resp.code} $err")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase Storage upload exception", e)
            return@withContext null
        }
    }

    suspend fun deleteStorageAttachments(storagePaths: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (storagePaths.isEmpty()) return@withContext true
        val token = getValidAccessToken() ?: return@withContext false
        try {
            val cleanPaths = storagePaths.mapNotNull { extractStoragePath(it) ?: it.ifBlank { null } }.distinct()
            if (cleanPaths.isEmpty()) return@withContext true

            val jsonBody = JSONObject().apply {
                val arr = JSONArray()
                for (p in cleanPaths) {
                    arr.put(p)
                }
                put("prefixes", arr)
            }
            val req = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/attachments")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .delete(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                Log.d(TAG, "deleteStorageAttachments code=${resp.code}")
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteStorageAttachments error", e)
            return@withContext false
        }
    }

    suspend fun cleanOrphanedStorageAttachments(): List<String> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken()
        val userId = secureKeyStorage.getSupabaseUserId()
        Log.d(TAG, "cleanOrphanedStorageAttachments: tokenPresent=${!token.isNullOrBlank()}, userId=$userId")
        if (token.isNullOrBlank() || userId.isBlank()) return@withContext emptyList()

        try {
            // 1. List objects in user's folder
            val listBody = JSONObject().apply {
                put("prefix", userId)
                put("limit", 1000)
            }
            val listReq = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/list/attachments")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .post(listBody.toString().toRequestBody(jsonMediaType))
                .build()

            val remoteFiles = mutableListOf<String>()
            client.newCall(listReq).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                Log.d(TAG, "cleanOrphanedStorageAttachments: list code=${resp.code}, body=$body")
                if (resp.isSuccessful) {
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name", "")
                        if (name.isNotBlank() && !obj.isNull("id")) {
                            remoteFiles.add("$userId/$name")
                        }
                    }
                } else {
                    Log.w(TAG, "cleanOrphanedStorageAttachments list failed: ${resp.code} $body")
                }
            }

            Log.d(TAG, "cleanOrphanedStorageAttachments: found ${remoteFiles.size} remote files in storage: $remoteFiles")
            if (remoteFiles.isEmpty()) return@withContext emptyList()

            // 2. Fetch all active message attachments for this user
            val getMsgReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/messages?user_id=eq.$userId&select=attachments")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val activePaths = mutableSetOf<String>()
            client.newCall(getMsgReq).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                Log.d(TAG, "cleanOrphanedStorageAttachments: getMessages code=${resp.code}")
                if (resp.isSuccessful) {
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val msgObj = arr.getJSONObject(i)
                        val attArr = msgObj.optJSONArray("attachments") ?: JSONArray()
                        for (j in 0 until attArr.length()) {
                            val att = attArr.getJSONObject(j)
                            val uri = att.optString("uri", "")
                            extractStoragePath(uri)?.let { activePaths.add(it) }
                        }
                    }
                } else {
                    Log.w(TAG, "cleanOrphanedStorageAttachments getMessages failed: ${resp.code} $body")
                }
            }

            // Also check local database messages
            try {
                val localMsgs = database.messageDao().getAllMessages()
                for (lm in localMsgs) {
                    extractStoragePath(lm.attachmentUri)?.let { activePaths.add(it) }
                    if (!lm.attachmentsJson.isNullOrBlank()) {
                        try {
                            val lArr = JSONArray(lm.attachmentsJson)
                            for (k in 0 until lArr.length()) {
                                val lObj = lArr.getJSONObject(k)
                                extractStoragePath(lObj.optString("uri"))?.let { activePaths.add(it) }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "cleanOrphanedStorageAttachments local check error", e)
            }

            // Whitelist remote project files in Supabase Storage
            try {
                val getProjFilesReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/project_files?select=storage_path")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(getProjFilesReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val pfObj = arr.getJSONObject(i)
                            val sp = pfObj.optString("storage_path", "")
                            extractStoragePath(sp)?.let { activePaths.add(it) } ?: if (sp.isNotBlank()) activePaths.add(sp) else Unit
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "cleanOrphanedStorageAttachments remote project_files check error", e)
            }

            // Whitelist local database project files
            try {
                val localProjFiles = database.projectFileDao().getAllFiles()
                for (lpf in localProjFiles) {
                    extractStoragePath(lpf.storagePath)?.let { activePaths.add(it) } ?: if (lpf.storagePath.isNotBlank()) activePaths.add(lpf.storagePath) else Unit
                }
            } catch (e: Exception) {
                Log.w(TAG, "cleanOrphanedStorageAttachments local project_files check error", e)
            }

            Log.d(TAG, "cleanOrphanedStorageAttachments: activePaths count=${activePaths.size}, activePaths=$activePaths")

            // 3. Find and remove orphaned files
            val orphans = remoteFiles.filter { !activePaths.contains(it) }
            Log.d(TAG, "cleanOrphanedStorageAttachments: orphans count=${orphans.size}, orphans=$orphans")
            if (orphans.isNotEmpty()) {
                Log.i(TAG, "Cleaning ${orphans.size} orphaned storage attachments: $orphans")
                val success = deleteStorageAttachments(orphans)
                Log.i(TAG, "cleanOrphanedStorageAttachments: deleteStorageAttachments result=$success")
                return@withContext orphans
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanOrphanedStorageAttachments failed", e)
        }
        return@withContext emptyList()
    }

    fun extractStoragePath(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val marker = "/attachments/"
        val idx = uri.indexOf(marker)
        if (idx != -1) {
            val path = uri.substring(idx + marker.length).substringBefore('?')
            return try { java.net.URLDecoder.decode(path, "UTF-8") } catch (_: Exception) { path }
        }
        if (uri.contains('/') && !uri.startsWith("http") && !uri.startsWith("content:") && !uri.startsWith("file:") && !uri.startsWith("data:")) {
            return uri
        }
        return null
    }

    companion object {
        private const val TAG = "SupabaseSync"
        const val SUPABASE_URL = "https://ncxbhueublthjfawkkvy.supabase.co"
        const val SUPABASE_KEY = "sb_publishable_lZa8oy7FwdDOKvv4Jrj26A_skhlS325"
    }
}
