package com.wyrd.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * High-performance, tactile haptic feedback manager.
 * Provides subtle micro-haptics for typing/streaming, crisp clicks for buttons,
 * and heavy spring engagement clicks.
 */
class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastStreamTickTime = 0L

    /**
     * Ultra-subtle micro-tick for UI scrolling, pulling, and text generation.
     */
    fun tick() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(8, 30))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(8)
            }
        } catch (e: Throwable) {
            // ignore device haptic errors
        }
    }

    /**
     * Rate-limited subtle tick for streaming text generation (max 1 tick every ~65ms).
     */
    fun streamTokenTick() {
        val now = System.currentTimeMillis()
        if (now - lastStreamTickTime > 65L) {
            lastStreamTickTime = now
            tick()
        }
    }

    /**
     * Crisp tactile click for button presses and selections.
     */
    fun click() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12, 60))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    /**
     * Strong, punchy click when spring-loaded pull engages into recording.
     */
    fun heavyClick() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(26, 160))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(26)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    /**
     * Subtle double pulse for completion / success.
     */
    fun success() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 10, 50, 15)
                val amplitudes = intArrayOf(0, 50, 0, 80)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    /**
     * Firm single haptic snap on answer completion ("duuun" / snap), modeled after ChatGPT.
     * Produces a clean, authoritative single tactile strike with zero rumbling.
     */
    fun completionSnap() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    /**
     * Backward-compatible alias for completionSnap.
     */
    fun completionBuzz() = completionSnap()
}
