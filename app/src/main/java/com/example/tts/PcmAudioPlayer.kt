package com.example.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-latency 24kHz 16-bit mono PCM AudioTrack player for in-app voice previewing.
 */
class PcmAudioPlayer {

    private val stopRequested = AtomicBoolean(false)

    @Volatile
    private var activeTrack: AudioTrack? = null

    suspend fun playPcm(pcmBytes: ByteArray, sampleRateHz: Int = 24000) = withContext(Dispatchers.IO) {
        if (pcmBytes.isEmpty()) return@withContext
        stop()
        stopRequested.set(false)

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        try {
            track.play()
            var offset = 0
            val chunkSize = 4096
            while (offset < pcmBytes.size && !stopRequested.get() && currentCoroutineContext().isActive) {
                val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, toWrite)
                if (written <= 0) break
                offset += written
            }
            if (!stopRequested.get()) {
                track.stop()
            }
        } finally {
            runCatching { track.release() }
            if (activeTrack === track) activeTrack = null
        }
    }

    fun stop() {
        stopRequested.set(true)
        activeTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
            }
        }
    }
}
