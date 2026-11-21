package com.example.tts

import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.ai.AiNavigationRewriter
import com.example.ai.GeminiTextRewriter
import com.example.data.SecurePreferencesRepository
import com.example.data.cache.AudioPcmDiskCache
import com.example.data.cache.AuraNavDatabase
import com.example.model.VoiceCatalog
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AuraNavTtsService"

/**
 * System-Wide Bilingual (Thai & English) Android Text-to-Speech Engine.
 * Extends [TextToSpeechService] and implements [TextToSpeech.OnInitListener]
 * to handle system-wide speech synthesis, seamless language switching for Thai (`th-TH`)
 * and English (`en-US`, `en-GB`), and an always-warm on-device fallback engine.
 *
 * Registered via `android.intent.action.TTS_SERVICE` for Google Maps Navigation,
 * TalkBack screen readers, and all third-party Android apps.
 */
class AuraNavTtsService : TextToSpeechService(), TextToSpeech.OnInitListener {

    private lateinit var securePrefs: SecurePreferencesRepository
    private lateinit var rewriter: AiNavigationRewriter
    private lateinit var audioClient: DualTtsAudioClient

    private val stopRequested = AtomicBoolean(false)

    // Warm on-device fallback TTS engine managed via TextToSpeech.OnInitListener
    private var fallbackTtsEngine: TextToSpeech? = null
    private val isFallbackEngineReady = AtomicBoolean(false)

    @Volatile
    private var currentLanguage = arrayOf("tha", "THA", "")

    @Volatile
    private var activeLocale: Locale = Locale("th", "TH")

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            runCatching { FirebaseApp.initializeApp(this) }
        }
        securePrefs = SecurePreferencesRepository.getInstance(applicationContext)
        val db = AuraNavDatabase.getInstance(applicationContext)
        val pcmCache = AudioPcmDiskCache.getInstance(applicationContext)
        rewriter = AiNavigationRewriter(
            phraseCacheDao = db.phraseCacheDao(),
            geminiTextRewriter = GeminiTextRewriter(),
            securePrefs = securePrefs
        )
        audioClient = DualTtsAudioClient(
            context = applicationContext,
            pcmDiskCache = pcmCache,
            securePrefs = securePrefs
        )
        initializeWarmFallbackEngine()
    }

    private fun initializeWarmFallbackEngine() {
        runCatching {
            // Bind directly to Google's built-in TTS package when available to avoid self-binding recursion
            val defaultFallbackPkg = "com.google.android.tts"
            fallbackTtsEngine = if (packageName != defaultFallbackPkg) {
                TextToSpeech(applicationContext, this, defaultFallbackPkg)
            } else {
                TextToSpeech(applicationContext, this)
            }
        }.onFailure { e ->
            Log.w(TAG, "Unable to pre-initialize warm fallback TTS engine", e)
        }
    }

    /**
     * [TextToSpeech.OnInitListener] callback invoked when the local fallback TTS instance is ready.
     * Pre-configures bilingual Thai (`th-TH`) and English (`en-US`) language switching readiness.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isFallbackEngineReady.set(true)
            switchLanguage(activeLocale)
            Log.d(TAG, "OnInitListener: Warm bilingual TTS engine initialized for ${activeLocale.toLanguageTag()}")
        } else {
            isFallbackEngineReady.set(false)
            Log.w(TAG, "OnInitListener: Warm fallback TTS initialization returned status=$status")
        }
    }

    /**
     * Dynamically switches the active synthesis language between Thai (`th-TH`) and English (`en-US` / `en-GB`).
     * Updates both the system service language state and the underlying [TextToSpeech] instance.
     */
    fun switchLanguage(targetLocale: Locale): Int {
        val lang = targetLocale.language.lowercase(Locale.ROOT)
        val country = targetLocale.country.uppercase(Locale.ROOT)

        val resolvedLocale = when {
            lang == "th" || lang == "tha" -> {
                currentLanguage = arrayOf("tha", "THA", "")
                Locale("th", "TH")
            }
            lang == "en" || lang == "eng" -> {
                if (country == "GB" || country == "GBR" || country == "UK") {
                    currentLanguage = arrayOf("eng", "GBR", "")
                    Locale.UK
                } else {
                    currentLanguage = arrayOf("eng", "USA", "")
                    Locale.US
                }
            }
            else -> return TextToSpeech.LANG_NOT_SUPPORTED
        }

        activeLocale = resolvedLocale

        if (isFallbackEngineReady.get()) {
            runCatching {
                val res = fallbackTtsEngine?.setLanguage(resolvedLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    fallbackTtsEngine?.setLanguage(Locale.US)
                }
            }
        }

        return TextToSpeech.LANG_COUNTRY_AVAILABLE
    }

    /**
     * Automatically detects whether input text is Thai, English, or mixed Thai-English
     * and switches the active synthesis language accordingly.
     */
    fun resolveAndSwitchLanguageForText(text: String, requestedLang: String?, requestedCountry: String?): String {
        val hasThaiScript = text.any { it in '\u0E00'..'\u0E7F' }
        val normReqLang = requestedLang?.lowercase(Locale.ROOT).orEmpty()
        val normReqCountry = requestedCountry?.uppercase(Locale.ROOT).orEmpty()

        return when {
            hasThaiScript -> {
                switchLanguage(Locale("th", "TH"))
                "th-TH"
            }
            normReqLang == "tha" || normReqLang == "th" -> {
                switchLanguage(Locale("th", "TH"))
                "th-TH"
            }
            normReqLang == "eng" || normReqLang == "en" -> {
                if (normReqCountry == "GBR" || normReqCountry == "GB") {
                    switchLanguage(Locale.UK)
                    "en-GB"
                } else {
                    switchLanguage(Locale.US)
                    "en-US"
                }
            }
            else -> {
                switchLanguage(Locale.US)
                "en-US"
            }
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val normalizedLang = lang?.lowercase(Locale.ROOT).orEmpty()
        val normalizedCountry = country?.uppercase(Locale.ROOT).orEmpty()
        return when (normalizedLang) {
            "tha", "th" -> {
                if (normalizedCountry.isEmpty() || normalizedCountry == "THA" || normalizedCountry == "TH") {
                    TextToSpeech.LANG_COUNTRY_AVAILABLE
                } else {
                    TextToSpeech.LANG_AVAILABLE
                }
            }
            "eng", "en" -> {
                when (normalizedCountry) {
                    "USA", "US", "GBR", "GB" -> TextToSpeech.LANG_COUNTRY_AVAILABLE
                    else -> TextToSpeech.LANG_AVAILABLE
                }
            }
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> = currentLanguage

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val availability = onIsLanguageAvailable(lang, country, variant)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            val l = lang?.lowercase(Locale.ROOT).orEmpty()
            val c = country?.uppercase(Locale.ROOT).orEmpty()
            when (l) {
                "tha", "th" -> switchLanguage(Locale("th", "TH"))
                "eng", "en" -> {
                    if (c == "GBR" || c == "GB") {
                        switchLanguage(Locale.UK)
                    } else {
                        switchLanguage(Locale.US)
                    }
                }
            }
        }
        return availability
    }

    override fun onGetVoices(): List<Voice> {
        val locales = listOf(Locale("th", "TH"), Locale.US, Locale.UK)
        val voices = mutableListOf<Voice>()
        for (profile in VoiceCatalog.allVoices) {
            for (locale in locales) {
                voices += Voice(
                    "auranav_${profile.id}_${locale.toLanguageTag()}",
                    locale,
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    setOf("ai_copilot", "bilingual", profile.gender.name.lowercase(Locale.ROOT))
                )
            }
        }
        return voices
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val activeVoiceId = if (::securePrefs.isInitialized) securePrefs.getLatestSettings().voiceId else "orbit"
        val tag = when (lang?.lowercase(Locale.ROOT)) {
            "tha", "th" -> "th-TH"
            "eng", "en" -> if (country.equals("GBR", true) || country.equals("GB", true)) "en-GB" else "en-US"
            else -> "th-TH"
        }
        return "auranav_${activeVoiceId}_$tag"
    }

    override fun onStop() {
        stopRequested.set(true)
        runCatching { fallbackTtsEngine?.stop() }
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        stopRequested.set(false)

        val rawText = request.charSequenceText?.toString()?.trim().orEmpty()
        if (rawText.isEmpty()) {
            callback.start(DualTtsAudioClient.TARGET_SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val settings = securePrefs.getLatestSettings()
        val resolvedLocaleTag = resolveAndSwitchLanguageForText(
            text = rawText,
            requestedLang = request.language,
            requestedCountry = request.country
        )

        val effectiveSettings = settings.copy(
            speakingRate = (settings.speakingRate * (request.speechRate / 100f).coerceIn(0.8f, 1.25f))
                .coerceIn(0.75f, 1.35f)
        )

        try {
            val pcmBytes = runBlocking {
                val rewrite = rewriter.rewriteForNavigation(
                    rawText = rawText,
                    settings = effectiveSettings,
                    localeOverride = resolvedLocaleTag,
                    isPreviewMode = false
                )
                Log.d(
                    TAG,
                    "Rewrite [${rewrite.routePath.name}] (${rewrite.latencyMs}ms, lang=$resolvedLocaleTag): '${rewrite.rawText}' -> '${rewrite.spokenText}'"
                )
                val synthesis = audioClient.synthesizeToPcm(
                    rewrite = rewrite,
                    settings = effectiveSettings,
                    isPreviewMode = false
                )
                if (synthesis.pcmBytes.isNotEmpty()) {
                    synthesis.pcmBytes
                } else {
                    synthesizeWithWarmFallback(
                        text = rewrite.spokenText,
                        pitchSemitones = effectiveSettings.pitchSemitones,
                        speakingRate = effectiveSettings.speakingRate
                    )
                }
            }

            if (stopRequested.get()) {
                callback.done()
                return
            }

            if (pcmBytes.isEmpty()) {
                callback.error()
                return
            }

            callback.start(DualTtsAudioClient.TARGET_SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)

            val maxChunkSize = callback.maxBufferSize.coerceAtMost(4096).coerceAtLeast(1024)
            var offset = 0
            while (offset < pcmBytes.size && !stopRequested.get()) {
                val len = minOf(maxChunkSize, pcmBytes.size - offset)
                val status = callback.audioAvailable(pcmBytes, offset, len)
                if (status == TextToSpeech.ERROR) break
                offset += len
            }
            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "System TTS synthesis failed", e)
            runCatching { callback.error() }
        }
    }

    /**
     * Synthesizes audio using the pre-initialized [TextToSpeech] instance managed by [OnInitListener]
     * if cloud/cache synthesis returns empty bytes.
     */
    private suspend fun synthesizeWithWarmFallback(
        text: String,
        pitchSemitones: Float,
        speakingRate: Float
    ): ByteArray {
        val engine = fallbackTtsEngine ?: return ByteArray(0)
        if (!isFallbackEngineReady.get()) return ByteArray(0)

        val outFile = File(cacheDir, "warm_fallback_${UUID.randomUUID()}.wav")
        val deferred = CompletableDeferred<ByteArray>()
        val utteranceId = UUID.randomUUID().toString()

        return try {
            switchLanguage(activeLocale)
            val pitchMultiplier = (1.0f + (pitchSemitones / 12.0f)).coerceIn(0.6f, 1.5f)
            engine.setPitch(pitchMultiplier)
            engine.setSpeechRate(speakingRate.coerceIn(0.75f, 1.35f))

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && !deferred.isCompleted) {
                        val pcm = runCatching {
                            if (outFile.exists() && outFile.length() > 44L) {
                                extractRawPcmFromWav(outFile.readBytes())
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

            val queued = engine.synthesizeToFile(text, Bundle(), outFile, utteranceId)
            if (queued != TextToSpeech.SUCCESS) return ByteArray(0)

            withTimeoutOrNull(4_000L) { deferred.await() } ?: ByteArray(0)
        } finally {
            runCatching { outFile.delete() }
        }
    }

    private fun extractRawPcmFromWav(wavBytes: ByteArray): ByteArray {
        if (wavBytes.size <= 44) return ByteArray(0)
        var offset = 12
        var dataStart = 44
        var dataSize = wavBytes.size - 44
        while (offset + 8 <= wavBytes.size) {
            val chunkId = String(wavBytes, offset, 4, Charsets.US_ASCII)
            val chunkLen = ByteBuffer.wrap(wavBytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                dataStart = offset + 8
                dataSize = chunkLen.coerceAtMost(wavBytes.size - dataStart).coerceAtLeast(0)
                break
            }
            offset += 8 + chunkLen + (chunkLen and 1)
        }
        return wavBytes.copyOfRange(dataStart, (dataStart + dataSize).coerceAtMost(wavBytes.size))
    }

    override fun onDestroy() {
        isFallbackEngineReady.set(false)
        runCatching {
            fallbackTtsEngine?.stop()
            fallbackTtsEngine?.shutdown()
        }
        fallbackTtsEngine = null
        super.onDestroy()
    }
}
