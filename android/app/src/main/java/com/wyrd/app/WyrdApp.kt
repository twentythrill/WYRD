package com.wyrd.app

import android.app.Application
import com.wyrd.app.data.local.AppDatabase
import com.wyrd.app.data.network.AudioRecorderHelper
import com.wyrd.app.data.network.ExaSearchService
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

class WyrdApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getInstance(this) }
    val secureKeyStorage by lazy { SecureKeyStorage(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val mimoService by lazy { XiaomiMimoService() }
    val exaSearchService by lazy { ExaSearchService() }
    val openAiSttService by lazy { OpenAiSttService() }
    val nativeTtsManager by lazy { NativeTtsManager(this) }
    val audioRecorderHelper by lazy { AudioRecorderHelper() }
    val hapticManager by lazy { com.wyrd.app.util.HapticManager(this) }
    val supabaseSyncManager by lazy {
        SupabaseSyncManager(this, secureKeyStorage, preferencesManager, database)
    }

    override fun onCreate() {
        super.onCreate()
        if (supabaseSyncManager.isUserSignedIn()) {
            applicationScope.launch {
                try {
                    supabaseSyncManager.syncAll()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        com.wyrd.app.service.WyrdBackgroundService.stop(this)
        nativeTtsManager.release()
        applicationScope.cancel()
    }
}
