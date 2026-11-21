package com.example.ai

import com.example.data.SecurePreferencesRepository
import com.example.data.cache.AudioPcmDiskCache
import com.example.data.cache.PhraseCacheDao
import com.example.data.cache.PhraseCacheEntity
import com.example.model.AiRewriteMode
import com.example.model.RewriteResult
import com.example.model.RewriteRoutePath
import com.example.model.TtsUserSettings

/**
 * Orchestrates Hybrid Latency-Safe Routing:
 * - Fast-Path (0ms AI Delay) for urgent turns/maneuvers
 * - Room SQLite Phrase Cache (0ms) for repeated routes
 * - Gemini AI Naturalization Path (800ms timeout in real-time driving mode)
 * - Strict safety guardrail post-verification & bilingual SSML generation
 */
class AiNavigationRewriter(
    private val phraseCacheDao: PhraseCacheDao,
    private val geminiTextRewriter: GeminiTextRewriter,
    private val securePrefs: SecurePreferencesRepository
) {

    suspend fun rewriteForNavigation(
        rawText: String,
        settings: TtsUserSettings,
        localeOverride: String? = null,
        isPreviewMode: Boolean = false
    ): RewriteResult {
        val startMs = System.currentTimeMillis()
        val cleanedInput = rawText.trim()
        val voice = settings.activeVoice
        val detectedLang = localeOverride ?: NavigationSafetyValidator.detectLanguage(cleanedInput)

        if (cleanedInput.isEmpty()) {
            return RewriteResult(
                rawText = "",
                spokenText = "",
                ssml = "<speak></speak>",
                routePath = RewriteRoutePath.ORIGINAL_SSML,
                latencyMs = 0L,
                safetyVerified = true,
                particleApplied = false,
                detectedLanguage = detectedLang
            )
        }

        // 1. Mode OFF: Original text with prosody SSML only
        if (settings.aiMode == AiRewriteMode.OFF) {
            val ssml = NavigationSafetyValidator.buildBilingualSsml(
                spokenText = cleanedInput,
                detectedLanguage = detectedLang,
                style = settings.style,
                expressiveness = settings.expressiveness
            )
            return RewriteResult(
                rawText = cleanedInput,
                spokenText = cleanedInput,
                ssml = ssml,
                routePath = RewriteRoutePath.ORIGINAL_SSML,
                latencyMs = System.currentTimeMillis() - startMs,
                safetyVerified = true,
                particleApplied = false,
                detectedLanguage = detectedLang
            )
        }

        // 2. Hybrid Fast-Path (0ms AI Delay) for immediate, urgent maneuvers
        if (NavigationSafetyValidator.isUrgentManeuver(cleanedInput)) {
            val (noParticleText, _) = NavigationSafetyValidator.applyThaiParticlePolicy(
                text = cleanedInput,
                gender = voice.gender,
                particleMode = settings.particleMode,
                isUrgent = true,
                isPreviewMode = isPreviewMode
            )
            val ssml = NavigationSafetyValidator.buildBilingualSsml(
                spokenText = noParticleText,
                detectedLanguage = detectedLang,
                style = settings.style,
                expressiveness = (settings.expressiveness * 0.5f)
            )
            return RewriteResult(
                rawText = cleanedInput,
                spokenText = noParticleText,
                ssml = ssml,
                routePath = RewriteRoutePath.FAST_PATH_URGENT,
                latencyMs = System.currentTimeMillis() - startMs,
                safetyVerified = true,
                particleApplied = false,
                detectedLanguage = detectedLang
            )
        }

        // 3. Layer 1 Persistent Room AI Phrase Cache lookup (0ms repeat routes)
        val cacheKey = buildPhraseCacheKey(
            rawText = cleanedInput,
            voiceId = voice.id,
            styleId = settings.style.id,
            aiModeId = settings.aiMode.id,
            language = detectedLang,
            particleModeId = settings.particleMode.id
        )

        val cached = phraseCacheDao.getByKey(cacheKey)
        if (cached != null) {
            phraseCacheDao.recordHit(cacheKey)
            val (policyText, particleApplied) = NavigationSafetyValidator.applyThaiParticlePolicy(
                text = cached.rewrittenText,
                gender = voice.gender,
                particleMode = settings.particleMode,
                isUrgent = false,
                isPreviewMode = isPreviewMode
            )
            val ssml = NavigationSafetyValidator.buildBilingualSsml(
                spokenText = policyText,
                detectedLanguage = detectedLang,
                style = settings.style,
                expressiveness = settings.expressiveness
            )
            return RewriteResult(
                rawText = cleanedInput,
                spokenText = policyText,
                ssml = ssml,
                routePath = RewriteRoutePath.AI_CACHE_HIT,
                latencyMs = System.currentTimeMillis() - startMs,
                safetyVerified = true,
                particleApplied = particleApplied,
                detectedLanguage = detectedLang
            )
        }

        // 4. Real-Time Gemini Naturalization Path (800ms strict driving timeout; 12s interactive preview timeout)
        val timeoutMs = if (isPreviewMode) 12_000L else 800L
        val aiCandidate = geminiTextRewriter.rewriteNavigationText(
            rawText = cleanedInput,
            voice = voice,
            style = settings.style,
            aiMode = settings.aiMode,
            particleMode = settings.particleMode,
            detectedLanguage = detectedLang,
            timeoutMs = timeoutMs,
            customGeminiApiKey = securePrefs.getCustomGeminiApiKey()
        )

        val isAiSafe = aiCandidate != null &&
            NavigationSafetyValidator.verifySafetyPreserved(cleanedInput, aiCandidate)

        val (selectedText, routePath) = if (isAiSafe && aiCandidate != null) {
            aiCandidate to RewriteRoutePath.GEMINI_LIVE
        } else {
            val fallback = NavigationSafetyValidator.ruleBasedNaturalFallback(
                rawText = cleanedInput,
                gender = voice.gender,
                isCopilot = settings.aiMode == AiRewriteMode.COPILOT
            )
            val verifiedFallback = if (NavigationSafetyValidator.verifySafetyPreserved(cleanedInput, fallback)) {
                fallback
            } else {
                cleanedInput
            }
            verifiedFallback to RewriteRoutePath.RULE_FALLBACK
        }

        val (finalText, particleApplied) = NavigationSafetyValidator.applyThaiParticlePolicy(
            text = selectedText,
            gender = voice.gender,
            particleMode = settings.particleMode,
            isUrgent = false,
            isPreviewMode = isPreviewMode
        )

        val ssml = NavigationSafetyValidator.buildBilingualSsml(
            spokenText = finalText,
            detectedLanguage = detectedLang,
            style = settings.style,
            expressiveness = settings.expressiveness
        )

        // Persist AI-rewritten phrases into Room Phrase Cache
        if (routePath == RewriteRoutePath.GEMINI_LIVE) {
            phraseCacheDao.upsert(
                PhraseCacheEntity(
                    cacheKey = cacheKey,
                    rawText = cleanedInput,
                    rewrittenText = finalText,
                    rewrittenSsml = ssml,
                    voiceId = voice.id,
                    styleId = settings.style.id,
                    aiModeId = settings.aiMode.id,
                    languageTag = detectedLang,
                    particleApplied = particleApplied
                )
            )
            trimCacheIfNeeded()
        }

        return RewriteResult(
            rawText = cleanedInput,
            spokenText = finalText,
            ssml = ssml,
            routePath = routePath,
            latencyMs = System.currentTimeMillis() - startMs,
            safetyVerified = true,
            particleApplied = particleApplied,
            detectedLanguage = detectedLang
        )
    }

    private suspend fun trimCacheIfNeeded() {
        val count = phraseCacheDao.getCount()
        if (count > MAX_PHRASE_CACHE_ENTRIES) {
            phraseCacheDao.evictOldest(count - MAX_PHRASE_CACHE_ENTRIES)
        }
    }

    private fun buildPhraseCacheKey(
        rawText: String,
        voiceId: String,
        styleId: String,
        aiModeId: String,
        language: String,
        particleModeId: String
    ): String {
        return AudioPcmDiskCache.sha256("$rawText|$voiceId|$styleId|$aiModeId|$language|$particleModeId")
    }

    companion object {
        private const val MAX_PHRASE_CACHE_ENTRIES = 500
    }
}
