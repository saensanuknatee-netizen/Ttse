package com.example.tts

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.example.data.SecurePreferencesRepository
import com.example.data.cache.AudioPcmDiskCache
import com.example.model.RewriteResult
import com.example.model.SynthesisBackend
import com.example.model.SynthesisResult
import com.example.model.TtsUserSettings
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "DualTtsAudioClient"

/**
 * Neural Audio Synthesizer supporting:
 * 1. Layer 2 LRU PCM Disk Cache (<20ms playback for repeated cues)
 * 2. Google Cloud Text-to-Speech API (Chirp 3: HD & Neural2 voices with SSML)
 * 3. Gemini 3.8 Flash TTS (`gemini-3.8-flash-tts`) via Firebase AI Logic (zero-config default)
 * 4. Automatic Offline Fallback to Android's built-in TTS engine synthesized into 24kHz PCM
 */
class DualTtsAudioClient(
    private val context: Context,
    private val pcmDiskCache: AudioPcmDiskCache,
    private val securePrefs: SecurePreferencesRepository,
    private val cloudTtsApi: GoogleCloudTtsApi = GoogleCloudTtsApi.create()
) {

    private val geminiTtsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash-tts",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    suspend fun synthesizeToPcm(
        rewrite: RewriteResult,
        settings: TtsUserSettings,
        isPreviewMode: Boolean = false
    ): SynthesisResult {
        val startMs = System.currentTimeMillis()
        val voice = settings.activeVoice
        val cloudKey = securePrefs.getCloudTtsApiKey()

        val effectiveBackend = when (settings.synthesisBackend) {
            SynthesisBackend.GOOGLE_CLOUD_TTS ->
                if (cloudKey.isNotBlank()) SynthesisBackend.GOOGLE_CLOUD_TTS else SynthesisBackend.FIREBASE_GEMINI_TTS
            SynthesisBackend.FIREBASE_GEMINI_TTS -> SynthesisBackend.FIREBASE_GEMINI_TTS
            SynthesisBackend.AUTO_HYBRID ->
                if (cloudKey.isNotBlank()) SynthesisBackend.GOOGLE_CLOUD_TTS else SynthesisBackend.FIREBASE_GEMINI_TTS
        }

        // 1. Check Layer 2 LRU PCM Disk Cache (<20ms)
        val audioCacheKey = pcmDiskCache.buildKey(
            ssml = rewrite.ssml,
            voiceId = voice.id,
            pitchSemitones = settings.pitchSemitones,
            expressiveness = settings.expressiveness,
            speakingRate = settings.speakingRate,
            backendId = effectiveBackend.id
        )
        pcmDiskCache.get(audioCacheKey)?.let { cachedPcm ->
            return SynthesisResult(
                pcmBytes = cachedPcm,
                sampleRateHz = TARGET_SAMPLE_RATE,
                providerLabel = "PCM Disk Cache (LRU)",
                fromAudioCache = true,
                latencyMs = System.currentTimeMillis() - startMs
            )
        }

        // 2. Try Google Cloud TTS (Chirp 3: HD -> Neural2 fallback) when selected & key present
        if (effectiveBackend == SynthesisBackend.GOOGLE_CLOUD_TTS && cloudKey.isNotBlank()) {
            val cloudPcm = synthesizeWithGoogleCloudTts(rewrite, settings, cloudKey)
            if (cloudPcm != null && cloudPcm.isNotEmpty()) {
                pcmDiskCache.put(audioCacheKey, cloudPcm)
                return SynthesisResult(
                    pcmBytes = cloudPcm,
                    sampleRateHz = TARGET_SAMPLE_RATE,
                    providerLabel = "Google Cloud TTS (Chirp 3 / Neural2)",
                    fromAudioCache = false,
                    latencyMs = System.currentTimeMillis() - startMs
                )
            }
        }

        // 3. Try Gemini 3.8 Flash TTS via Firebase AI Logic
        val geminiPcm = synthesizeWithGeminiTts(
            rewrite = rewrite,
            settings = settings,
            timeoutMs = if (isPreviewMode) 35_000L else 6_000L
        )
        if (geminiPcm != null && geminiPcm.isNotEmpty()) {
            pcmDiskCache.put(audioCacheKey, geminiPcm)
            return SynthesisResult(
                pcmBytes = geminiPcm,
                sampleRateHz = TARGET_SAMPLE_RATE,
                providerLabel = "Gemini 3.8 Flash TTS",
                fromAudioCache = false,
                latencyMs = System.currentTimeMillis() - startMs
            )
        }

        // 4. Automatic Offline Fallback to built-in device TTS (converted to 24kHz 16-bit mono PCM)
        val offlinePcm = synthesizeWithDeviceOfflineTts(rewrite.spokenText, rewrite.detectedLanguage, settings)
        return SynthesisResult(
            pcmBytes = offlinePcm,
            sampleRateHz = TARGET_SAMPLE_RATE,
            providerLabel = "Offline Built-in TTS Fallback",
            fromAudioCache = false,
            latencyMs = System.currentTimeMillis() - startMs
        )
    }

    private suspend fun synthesizeWithGoogleCloudTts(
        rewrite: RewriteResult,
        settings: TtsUserSettings,
        apiKey: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        val voice = settings.activeVoice
        val isThai = rewrite.detectedLanguage.startsWith("th")
        val langCode = if (isThai) "th-TH" else "en-US"
        val chirpVoice = if (isThai) voice.cloudChirpThaiVoice else voice.cloudChirpEnglishVoice
        val neuralVoice = if (isThai) voice.cloudNeuralFallbackThai else voice.cloudNeuralFallbackEnglish
        val effectivePitch = (settings.pitchSemitones + voice.defaultPitchOffset).coerceIn(-6.0f, 6.0f).toDouble()

        // Attempt 1: Chirp 3: HD with plain text/SSML (Chirp 3 HD works best without pitch override)
        val chirpAttempt = runCatching {
            val req = CloudTtsRequest(
                input = CloudTtsInput(text = rewrite.spokenText),
                voice = CloudTtsVoice(languageCode = langCode, name = chirpVoice),
                audioConfig = CloudTtsAudioConfig(
                    audioEncoding = "LINEAR16",
                    sampleRateHertz = TARGET_SAMPLE_RATE,
                    speakingRate = settings.speakingRate.toDouble()
                )
            )
            val resp = cloudTtsApi.synthesize(apiKey, req)
            if (resp.isSuccessful) {
                resp.body()?.audioContent?.let { decodeBase64ToPcm24k(it) }
            } else {
                Log.w(TAG, "Chirp 3 HD returned HTTP ${resp.code()}, falling back to Neural2 SSML")
                null
            }
        }.getOrNull()

        if (chirpAttempt != null && chirpAttempt.isNotEmpty()) return@withContext chirpAttempt

        // Attempt 2: Neural2 with full bilingual SSML and pitch control
        runCatching {
            val req = CloudTtsRequest(
                input = CloudTtsInput(ssml = rewrite.ssml),
                voice = CloudTtsVoice(languageCode = langCode, name = neuralVoice),
                audioConfig = CloudTtsAudioConfig(
                    audioEncoding = "LINEAR16",
                    sampleRateHertz = TARGET_SAMPLE_RATE,
                    speakingRate = settings.speakingRate.toDouble(),
                    pitch = effectivePitch
                )
            )
            val resp = cloudTtsApi.synthesize(apiKey, req)
            if (resp.isSuccessful) {
                resp.body()?.audioContent?.let { decodeBase64ToPcm24k(it) }
            } else {
                Log.e(TAG, "Cloud TTS Neural2 failed with HTTP ${resp.code()}")
                null
            }
        }.getOrNull()
    }

    private suspend fun synthesizeWithGeminiTts(
        rewrite: RewriteResult,
        settings: TtsUserSettings,
        timeoutMs: Long
    ): ByteArray? {
        val voice = settings.activeVoice
        val pitchDescriptor = when {
            settings.pitchSemitones <= -1.2f -> "deeper, richer lower vocal pitch"
            settings.pitchSemitones >= 1.2f -> "brighter, clearer higher vocal pitch"
            else -> "balanced natural vocal pitch"
        }
        val speedDescriptor = when {
            settings.speakingRate < 0.88f -> "slightly slower, relaxed cadence"
            settings.speakingRate > 1.08f -> "brisk, crisp highway cadence"
            else -> "steady, natural navigation pace"
        }
        val prompt = """
            Speak the following navigation prompt aloud with lifelike human prosody.
            Voice persona: ${voice.displayName} (${voice.gender.name}, ${voice.geminiVoiceName} style) — ${voice.personaPrompt}
            Speaking style: ${settings.style.promptInstruction}
            Tone & delivery: $pitchDescriptor, $speedDescriptor, expressiveness ${(settings.expressiveness * 100).roundToInt()}%.
            Read ONLY the exact text below without adding extra commentary:
            ${rewrite.spokenText}
        """.trimIndent()

        return try {
            val response = withTimeout(timeoutMs) {
                geminiTtsModel.generateContent(prompt)
            }
            val audioPart = response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { it.mimeType.startsWith("audio/") }
                ?: return null

            convertAudioBytesToPcm24k(audioPart.inlineData, audioPart.mimeType)
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "Gemini TTS timed out after ${timeoutMs}ms; falling back.")
                null
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini 3.8 Flash TTS failed; using offline fallback", e)
            null
        }
    }

    /**
     * Tests the Google Cloud TTS API Key with a tiny synthesis request.
     */
    suspend fun testCloudTtsKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("ยังไม่ได้กรอก Google Cloud TTS API Key"))
        }
        val start = System.currentTimeMillis()
        try {
            val req = CloudTtsRequest(
                input = CloudTtsInput(text = "ทดสอบเสียง"),
                voice = CloudTtsVoice(languageCode = "th-TH", name = "th-TH-Neural2-C"),
                audioConfig = CloudTtsAudioConfig(audioEncoding = "LINEAR16", sampleRateHertz = TARGET_SAMPLE_RATE)
            )
            val resp = cloudTtsApi.synthesize(apiKey.trim(), req)
            val elapsed = System.currentTimeMillis() - start
            if (resp.isSuccessful && !resp.body()?.audioContent.isNullOrBlank()) {
                Result.success("Google Cloud TTS เชื่อมต่อสำเร็จ (${elapsed}ms)")
            } else {
                Result.failure(IllegalStateException("Google Cloud TTS ตอบกลับ HTTP ${resp.code()} — ตรวจสอบคีย์และสิทธิ์ API"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun synthesizeWithDeviceOfflineTts(
        text: String,
        detectedLang: String,
        settings: TtsUserSettings
    ): ByteArray = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<ByteArray>()
        val outFile = File(context.cacheDir, "offline_tts_${UUID.randomUUID()}.wav")
        var engine: TextToSpeech? = null

        try {
            val fallbackPackage = findFallbackSystemTtsPackage()
            val initDeferred = CompletableDeferred<Int>()
            val listener = TextToSpeech.OnInitListener { status ->
                if (!initDeferred.isCompleted) initDeferred.complete(status)
            }
            engine = if (fallbackPackage != null) {
                TextToSpeech(context.applicationContext, listener, fallbackPackage)
            } else {
                TextToSpeech(context.applicationContext, listener)
            }

            val initStatus = withTimeout(3_000L) { initDeferred.await() }
            if (initStatus != TextToSpeech.SUCCESS) {
                return@withContext ByteArray(0)
            }

            val locale = if (detectedLang.startsWith("th")) Locale("th", "TH") else Locale.US
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }

            val pitchMultiplier = (1.0f + (settings.pitchSemitones / 12.0f)).coerceIn(0.6f, 1.5f)
            engine.setPitch(pitchMultiplier)
            engine.setSpeechRate(settings.speakingRate.coerceIn(0.75f, 1.35f))

            val utteranceId = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && !deferred.isCompleted) {
                        val pcm = runCatching {
                            if (outFile.exists() && outFile.length() > 44) {
                                parseWavToPcm24k(outFile.readBytes())
                            } else {
                                ByteArray(0)
                            }
                        }.getOrDefault(ByteArray(0))
                        deferred.complete(pcm)
                    }
                }

                @Deprecated("Deprecated in Android API")
                override fun onError(id: String?) {
                    if (id == utteranceId && !deferred.isCompleted) deferred.complete(ByteArray(0))
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && !deferred.isCompleted) deferred.complete(ByteArray(0))
                }
            })

            val params = Bundle()
            val queued = engine.synthesizeToFile(text, params, outFile, utteranceId)
            if (queued != TextToSpeech.SUCCESS) {
                return@withContext ByteArray(0)
            }

            withTimeout(5_000L) { deferred.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Offline Android TTS fallback failed", e)
            ByteArray(0)
        } finally {
            runCatching { engine?.shutdown() }
            runCatching { outFile.delete() }
        }
    }

    /**
     * Picks a built-in system TTS engine package that is NOT AuraNav itself to prevent recursion.
     */
    private fun findFallbackSystemTtsPackage(): String? {
        return runCatching {
            val probe = TextToSpeech(context.applicationContext) {}
            val engines = probe.engines
            probe.shutdown()
            engines.firstOrNull { it.name == "com.google.android.tts" && it.name != context.packageName }?.name
                ?: engines.firstOrNull { it.name != context.packageName }?.name
        }.getOrNull()
    }

    private fun decodeBase64ToPcm24k(base64Audio: String): ByteArray {
        val rawBytes = Base64.decode(base64Audio, Base64.DEFAULT)
        return parseWavToPcm24k(rawBytes)
    }

    private fun convertAudioBytesToPcm24k(bytes: ByteArray, mimeType: String): ByteArray {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        // Raw linear 16-bit PCM (e.g., audio/L16;codec=pcm;rate=24000)
        if (lowerMime.contains("l16") || lowerMime.contains("pcm")) {
            val rateMatch = Regex("""rate=(\d+)""").find(lowerMime)
            val srcRate = rateMatch?.groupValues?.get(1)?.toIntOrNull() ?: TARGET_SAMPLE_RATE
            val leBytes = if (lowerMime.contains("l16") && !lowerMime.contains("endian=little")) {
                // Gemini audio/L16 over HTTP is typically little-endian PCM16; check WAV magic first
                if (isWavHeader(bytes)) return parseWavToPcm24k(bytes) else bytes
            } else {
                bytes
            }
            return resampleMonoPcm16(leBytes, srcRate, TARGET_SAMPLE_RATE)
        }
        if (lowerMime.contains("wav") || isWavHeader(bytes)) {
            return parseWavToPcm24k(bytes)
        }
        // Encoded audio (MP3/OGG/AAC) decoded via Android MediaExtractor + MediaCodec
        return decodeCompressedToPcm24k(bytes)
    }

    private fun isWavHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
    }

    private fun parseWavToPcm24k(bytes: ByteArray): ByteArray {
        if (!isWavHeader(bytes) || bytes.size <= 44) return bytes
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buf.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = buf.getInt(24).coerceAtLeast(8000)

        // Locate "data" chunk robustly (WAV headers can contain LIST/JUNK chunks before data)
        var offset = 12
        var dataStart = 44
        var dataSize = bytes.size - 44
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkLen = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                dataStart = offset + 8
                dataSize = chunkLen.coerceAtMost(bytes.size - dataStart).coerceAtLeast(0)
                break
            }
            offset += 8 + chunkLen + (chunkLen and 1)
        }

        val pcmRaw = bytes.copyOfRange(dataStart, (dataStart + dataSize).coerceAtMost(bytes.size))
        val monoPcm = if (channels == 2) stereoToMonoPcm16(pcmRaw) else pcmRaw
        return resampleMonoPcm16(monoPcm, sampleRate, TARGET_SAMPLE_RATE)
    }

    private fun decodeCompressedToPcm24k(encoded: ByteArray): ByteArray {
        val tmpFile = File(context.cacheDir, "gemini_audio_${UUID.randomUUID()}.bin")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            tmpFile.writeBytes(encoded)
            extractor.setDataSource(tmpFile.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrack < 0 || format == null) return ByteArray(0)
            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return ByteArray(0)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val raw = pcmOut.toByteArray()
            val mono = if (channelCount == 2) stereoToMonoPcm16(raw) else raw
            resampleMonoPcm16(mono, sampleRate, TARGET_SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decode failed", e)
            ByteArray(0)
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
            runCatching { tmpFile.delete() }
        }
    }

    private fun stereoToMonoPcm16(stereo: ByteArray): ByteArray {
        val frames = stereo.size / 4
        val out = ByteArray(frames * 2)
        val inBuf = ByteBuffer.wrap(stereo).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val l = inBuf.short.toInt()
            val r = inBuf.short.toInt()
            outBuf.putShort(((l + r) / 2).toShort())
        }
        return out
    }

    private fun resampleMonoPcm16(input: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
        if (srcRate == dstRate || input.size < 4) return input
        val srcSamples = input.size / 2
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val dstSamples = (srcSamples / ratio).toInt().coerceAtLeast(1)
        val inBuf = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteArray(dstSamples * 2)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dstSamples) {
            val pos = i * ratio
            val idx0 = pos.toInt().coerceIn(0, srcSamples - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcSamples - 1)
            val frac = pos - idx0
            val s0 = inBuf.getShort(idx0 * 2).toInt()
            val s1 = inBuf.getShort(idx1 * 2).toInt()
            val interpolated = (s0 + (s1 - s0) * frac).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outBuf.putShort(interpolated.toShort())
        }
        return out
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 24000
    }
}
