package com.wyrd.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wyrd.app.MainActivity
import com.wyrd.app.R

/**
 * Foreground service that keeps WYRD active and prevents process freeze/kill
 * while generating LLM responses, executing web search tools, or streaming audio
 * when the user switches to other applications.
 *
 * If the user swipes WYRD away from the task manager (Recents), onTaskRemoved
 * triggers an immediate abort and cleanup.
 */
class WyrdBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val statusText = intent?.getStringExtra(EXTRA_STATUS) ?: "Processing request..."
                acquireWakeLock()
                val notification = buildNotification(statusText)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE_STATUS -> {
                val statusText = intent?.getStringExtra(EXTRA_STATUS) ?: "Processing request..."
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, buildNotification(statusText))
            }
            ACTION_STOP_GENERATION -> {
                Log.i(TAG, "ACTION_STOP_GENERATION triggered from notification action")
                try {
                    stopCallback?.invoke()
                } catch (e: Throwable) {
                    Log.e(TAG, "Error invoking stopCallback: ${e.message}")
                }
                stopForegroundAndSelf()
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(statusText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WyrdBackgroundService::class.java).apply {
                this.action = ACTION_STOP_GENERATION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("WYRD")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WYRD:BackgroundGenerationWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(10 * 60 * 1000L) // 10 minutes maximum safety timeout
                Log.i(TAG, "WakeLock acquired for background execution")
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock released")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
        wakeLock = null
    }

    private fun stopForegroundAndSelf() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "WYRD removed from task manager / Recents, aborting background tasks")
        try {
            stopCallback?.invoke()
        } catch (e: Throwable) {
            Log.e(TAG, "Error invoking stopCallback in onTaskRemoved: ${e.message}")
        }
        stopForegroundAndSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when WYRD generates responses or processes tools in the background"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "WyrdBackgroundService"
        const val CHANNEL_ID = "wyrd_background_channel"
        const val NOTIFICATION_ID = 4041

        const val ACTION_START = "com.wyrd.app.service.START"
        const val ACTION_UPDATE_STATUS = "com.wyrd.app.service.UPDATE_STATUS"
        const val ACTION_STOP_GENERATION = "com.wyrd.app.service.STOP_GENERATION"
        const val ACTION_STOP_SERVICE = "com.wyrd.app.service.STOP_SERVICE"

        const val EXTRA_STATUS = "extra_status"

        var stopCallback: (() -> Unit)? = null

        fun start(context: Context, status: String = "Generating response...") {
            try {
                val intent = Intent(context, WyrdBackgroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STATUS, status)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start WyrdBackgroundService: ${e.message}", e)
            }
        }

        fun updateStatus(context: Context, status: String) {
            try {
                val intent = Intent(context, WyrdBackgroundService::class.java).apply {
                    action = ACTION_UPDATE_STATUS
                    putExtra(EXTRA_STATUS, status)
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update status in WyrdBackgroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, WyrdBackgroundService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop WyrdBackgroundService: ${e.message}")
            }
        }
    }
}
