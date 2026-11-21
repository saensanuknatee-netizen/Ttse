package com.example.tts

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures 16kHz 16-bit mono PCM audio from the device microphone and encodes a valid WAV file
 * for transcription via `gemini-3.5-transcribe`.
 */
class MicrophoneAudioRecorder {

    private val isRecording = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    suspend fun recordUntilStopped(): ByteArray = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(3200)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuf * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext ByteArray(0)
        }

        val pcmStream = ByteArrayOutputStream()
        val buffer = ByteArray(minBuf)
        isRecording.set(true)

        try {
            recorder.startRecording()
            val maxBytes = sampleRate * 2 * 15 // Cap at 15 seconds for safety
            while (isRecording.get() && isActive && pcmStream.size() < maxBytes) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pcmStream.write(buffer, 0, read)
                }
            }
        } finally {
            isRecording.set(false)
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }

        val pcmData = pcmStream.toByteArray()
        if (pcmData.isEmpty()) ByteArray(0) else wrapPcmAsWav(pcmData, sampleRate, 1, 16)
    }

    fun stopRecording() {
        isRecording.set(false)
    }

    private fun wrapPcmAsWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM fmt chunk size
            putShort(1) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }
        return header.array() + pcmData
    }
}
