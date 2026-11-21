package com.example.data.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Layer 2 Zero-Latency LRU Disk Cache storing decoded 24kHz 16-bit mono PCM buffers.
 * Keyed by SHA-256(ssml + voiceId + pitch + expressiveness + speed + backend).
 */
class AudioPcmDiskCache private constructor(context: Context) {

    private val cacheDir: File = File(context.applicationContext.cacheDir, "auranav_pcm_cache").apply {
        if (!exists()) mkdirs()
    }

    private val _clipCount = MutableStateFlow(0)
    val clipCount: StateFlow<Int> = _clipCount.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    init {
        refreshStatsSync()
    }

    fun buildKey(
        ssml: String,
        voiceId: String,
        pitchSemitones: Float,
        expressiveness: Float,
        speakingRate: Float,
        backendId: String
    ): String {
        val raw = "$ssml|$voiceId|${"%.1f".format(pitchSemitones)}|${"%.2f".format(expressiveness)}|${"%.2f".format(speakingRate)}|$backendId"
        return sha256(raw)
    }

    suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "$key.pcm")
        if (!file.exists() || file.length() <= 0L) return@withContext null
        // Touch mtime for LRU ordering
        file.setLastModified(System.currentTimeMillis())
        runCatching { file.readBytes() }.getOrNull()
    }

    suspend fun put(key: String, pcmBytes: ByteArray) = withContext(Dispatchers.IO) {
        if (pcmBytes.isEmpty()) return@withContext
        val file = File(cacheDir, "$key.pcm")
        val tmpFile = File(cacheDir, "$key.tmp")
        runCatching {
            tmpFile.writeBytes(pcmBytes)
            tmpFile.renameTo(file)
            enforceLruLimits()
            refreshStatsSync()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        refreshStatsSync()
    }

    private fun enforceLruLimits() {
        val files = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".pcm") }
            ?.sortedBy { it.lastModified() }
            ?: return

        var totalSize = files.sumOf { it.length() }
        var remainingCount = files.size

        for (file in files) {
            if (totalSize <= MAX_CACHE_BYTES && remainingCount <= MAX_CACHE_CLIPS) break
            val len = file.length()
            if (file.delete()) {
                totalSize -= len
                remainingCount -= 1
            }
        }
    }

    private fun refreshStatsSync() {
        val files = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".pcm") } ?: emptyArray()
        _clipCount.value = files.size
        _totalBytes.value = files.sumOf { it.length() }
    }

    companion object {
        private const val MAX_CACHE_BYTES = 40L * 1024L * 1024L // 40 MB
        private const val MAX_CACHE_CLIPS = 160

        @Volatile
        private var instance: AudioPcmDiskCache? = null

        fun getInstance(context: Context): AudioPcmDiskCache {
            return instance ?: synchronized(this) {
                instance ?: AudioPcmDiskCache(context).also { instance = it }
            }
        }

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
