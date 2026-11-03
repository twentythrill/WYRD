package com.wyrd.app

import android.app.Application
import com.wyrd.app.data.local.AppDatabase
import com.wyrd.app.data.network.AudioRecorderHelper
import com.wyrd.app.data.network.ExaSearchService
import com.wyrd.app.data.network.TavilySearchService
import com.wyrd.app.data.network.OpenAiSttService
import com.wyrd.app.data.network.SupabaseSyncManager
import com.wyrd.app.data.network.XiaomiMimoService
import com.wyrd.app.data.preferences.PreferencesManager
import com.wyrd.app.data.preferences.SecureKeyStorage
import com.wyrd.app.data.tts.NativeTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WyrdApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val database by lazy { AppDatabase.getInstance(this) }
    val secureKeyStorage by lazy { SecureKeyStorage(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val mimoService by lazy {
        XiaomiMimoService(
            okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build(),
            context = this
        )
    }
    val exaSearchService by lazy {
        ExaSearchService(
            okHttpClient.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        )
    }
    val tavilySearchService by lazy {
        TavilySearchService(
            okHttpClient.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        )
    }
    val openAiSttService by lazy {
        OpenAiSttService(
            okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        )
    }
    val nativeTtsManager by lazy { NativeTtsManager(this) }
    val audioRecorderHelper by lazy { AudioRecorderHelper() }
    val hapticManager by lazy { com.wyrd.app.util.HapticManager(this) }
    val supabaseSyncManager by lazy {
        SupabaseSyncManager(this, secureKeyStorage, preferencesManager, database, okHttpClient)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            try {
                val model = preferencesManager.getCachedActiveModel()
                val key = if (model.isOpenAi) {
                    secureKeyStorage.getOpenAiApiKey()
                } else {
                    secureKeyStorage.getXiaomiApiKey()
                }
                val baseUrl = preferencesManager.getCachedCustomBaseUrl()
                mimoService.prewarmConnection(key, model, baseUrl)
            } catch (_: Exception) {}
        }
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (supabaseSyncManager.isUserSignedIn()) {
                    supabaseSyncManager.syncAll()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        com.wyrd.app.service.WyrdBackgroundService.stop(this)
        nativeTtsManager.release()
        applicationScope.cancel()
    }
}

