package com.wyrd.app.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AudioTrackPlayer {
    private var currentAudioTrack: AudioTrack? = null
    private val isStopped = AtomicBoolean(false)
    private var currentSampleRate: Int = 24000

    @Synchronized
    fun stop() {
        isStopped.set(true)
        try {
            currentAudioTrack?.pause()
            currentAudioTrack?.flush()
            currentAudioTrack?.stop()
        } catch (e: Throwable) {
            // ignore
        } finally {
            try {
                currentAudioTrack?.release()
            } catch (e: Throwable) {
                // ignore
            }
            currentAudioTrack = null
        }
    }

    val isPlaying: Boolean
        get() = try {
            currentAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
        } catch (e: Throwable) {
            false
        }

    fun isStopped(): Boolean = isStopped.get()

    @Synchronized
    fun prepareStream(sampleRate: Int): Boolean {
        stop()
        isStopped.set(false)
        currentSampleRate = sampleRate

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            // 3.0 s buffer capacity (4 bytes per sample in PCM_FLOAT) absorbs
            // synthesis bursts without startup delay or playback underflows.
            val bufferSize = if (minBufferSize > 0) {
                maxOf(minBufferSize * 4, sampleRate * 4 * 3)
            } else {
                sampleRate * 4 * 3
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            currentAudioTrack = track
            track.play()
            return true
        } catch (t: Throwable) {
            Log.e("AudioTrackPlayer", "Failed to create AudioTrack", t)
            return false
        }
    }

    fun writeChunk(samples: FloatArray): Int {
        if (isStopped.get()) return -1
        val track = currentAudioTrack ?: return -1
        if (samples.isEmpty()) return 0

        var totalWritten = 0
        while (totalWritten < samples.size && !isStopped.get()) {
            val count = samples.size - totalWritten
            try {
                val written = track.write(samples, totalWritten, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) {
                    Log.e("AudioTrackPlayer", "Error writing audio chunk: $written")
                    break
                }
                totalWritten += written
            } catch (t: Throwable) {
                Log.e("AudioTrackPlayer", "Exception writing audio chunk", t)
                break
            }
        }
        return totalWritten
    }

    suspend fun waitUntilPlaybackCompleted(totalSamplesWritten: Long) = withContext(Dispatchers.Default) {
        val track = currentAudioTrack ?: return@withContext
        if (totalSamplesWritten <= 0) return@withContext

        while (isActive && !isStopped.get()) {
            val head = try {
                track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            } catch (e: Throwable) {
                break
            }
            if (head >= totalSamplesWritten) {
                break
            }
            val remainingFrames = totalSamplesWritten - head
            val waitMs = (remainingFrames * 1000L / currentSampleRate).coerceIn(20L, 200L)
            delay(waitMs)
        }
    }
}
