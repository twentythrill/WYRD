package com.wyrd.app.data.network

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorderHelper {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val pcmOutputStream = ByteArrayOutputStream()
    private var isRecording = false

    val isCurrentlyRecording: Boolean
        get() = isRecording

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope): Boolean {
        if (isRecording) return false

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            2048
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            pcmOutputStream.reset()
            audioRecord?.startRecording()
            isRecording = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        synchronized(pcmOutputStream) {
                            pcmOutputStream.write(buffer, 0, readBytes)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            stopRecording()
            return false
        }
    }

    fun stopRecording(): ByteArray? {
        if (!isRecording && audioRecord == null) return null
        isRecording = false

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord?.release()
        audioRecord = null

        val pcmData = synchronized(pcmOutputStream) {
            pcmOutputStream.toByteArray()
        }
        pcmOutputStream.reset()

        if (pcmData.isEmpty()) return null
        return wrapPcmInWav(pcmData, sampleRate = 16000, channels = 1, bitsPerSample = 16)
    }

    fun cancelRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord?.release()
        audioRecord = null
        synchronized(pcmOutputStream) {
            pcmOutputStream.reset()
        }
    }

    private fun wrapPcmInWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())
            putInt(totalDataLen)
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat 1 = PCM
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort()) // block align
            putShort(bitsPerSample)
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())
            putInt(totalAudioLen)
        }.array()

        val wavBuffer = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, wavBuffer, 0, 44)
        System.arraycopy(pcmData, 0, wavBuffer, 44, pcmData.size)
        return wavBuffer
    }
}
