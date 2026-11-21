package com.example.ai

import com.example.model.SpeakingStyle
import com.example.model.ThaiParticleMode
import com.example.model.VoiceGender
import java.util.Locale

/**
 * Enforces strict driving-safety guardrails, hybrid Fast-Path routing,
 * context-aware Thai particle cooldown (15s), and bilingual SSML wrapping.
 */
object NavigationSafetyValidator {

    private const val PARTICLE_COOLDOWN_MS = 15_000L

    @Volatile
    private var lastParticleAppliedAtMs: Long = 0L

    private val numberRegex = Regex("""\d+(?:[.,]\d+)?""")
    private val thaiCharRegex = Regex("""[\u0E00-\u0E7F]""")
    private val latinWordRegex = Regex("""\b[A-Za-z][A-Za-z0-9\-&'.]*(?:\s+[A-Za-z0-9\-&'.]+){0,5}\b""")

    private val urgentThaiKeywords = listOf(
        "เลี้ยวซ้าย", "เลี้ยวขวา", "ชิดซ้าย", "ชิดขวา", "กลับรถ",
        "เบี่ยงซ้าย", "เบี่ยงขวา", "ออกซ้าย", "ออกขวา", "หยุด", "ระวัง"
    )

    private val urgentEnglishKeywords = listOf(
        "turn left now", "turn right now", "keep left", "keep right",
        "make a u-turn", "u-turn now", "exit now", "take the exit now",
        "turn left", "turn right", "bear left", "bear right"
    )

    private val immediateDistanceRegex = Regex(
        """(?i)(อีก\s*(?:[1-9]\d?|100)\s*เมตร|\bin\s+(?:[1-9]\d?|100)\s*(?:m|meters|metres|ft|feet)\b)"""
    )

    private val longAheadDistanceRegex = Regex(
        """(?i)(อีก\s*(?:[2-9]\d{2,}|[1-9]\d*\s*กิโลเมตร)|\bin\s+(?:[2-9]\d{2,}\s*(?:m|meters|metres)|\d+(?:\.\d+)?\s*(?:km|kilometers|miles)))"""
    )

    /**
     * Detects immediate urgent maneuvers that MUST take the Fast-Path (0ms AI delay)
     * so the driver never misses a sharp turn or lane split.
     */
    fun isUrgentManeuver(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase(Locale.ROOT)

        // If the prompt explicitly gives a long advance distance (>= 200m or km), it's NOT immediate.
        if (longAheadDistanceRegex.containsMatchIn(trimmed)) return false

        // Immediate distance (<= 100m) is always urgent
        if (immediateDistanceRegex.containsMatchIn(trimmed)) return true

        // Short direct command without long lead-in (e.g., "เลี้ยวซ้าย", "ชิดขวา", "Turn left now")
        if (trimmed.length <= 22 && urgentThaiKeywords.any { trimmed.contains(it) }) {
            return true
        }
        if (trimmed.length <= 28 && urgentEnglishKeywords.any { lower == it || lower.startsWith("$it ") || lower.contains("now") }) {
            return true
        }
        return false
    }

    fun detectLanguage(text: String): String {
        val hasThai = thaiCharRegex.containsMatchIn(text)
        val hasLatin = latinWordRegex.containsMatchIn(text)
        return when {
            hasThai && hasLatin -> "th-TH-mixed"
            hasThai -> "th-TH"
            else -> "en-US"
        }
    }

    /**
     * Strictly verifies that AI rewriting NEVER altered numbers, distances,
     * road/exit numbers, or turn directions (ซ้าย/ขวา, left/right, กลับรถ/U-turn).
     */
    fun verifySafetyPreserved(rawText: String, candidateText: String): Boolean {
        if (candidateText.isBlank()) return false
        if (candidateText.length > (rawText.length * 2.5f + 50).toInt()) return false
        // Reject accidental markdown/code fences
        if (candidateText.contains("```") || candidateText.contains("**")) return false

        // 1. Exact numeric preservation (all distances, Soi numbers, highway numbers, exit numbers)
        val rawNumbers = numberRegex.findAll(rawText).map { it.value }.toList()
        val candidateNumbers = numberRegex.findAll(candidateText).map { it.value }.toList()
        if (rawNumbers != candidateNumbers) return false

        // 2. Turn direction preservation (Thai & English)
        val rawLower = rawText.lowercase(Locale.ROOT)
        val candLower = candidateText.lowercase(Locale.ROOT)

        if (rawText.contains("ซ้าย") && !candidateText.contains("ซ้าย")) return false
        if (rawText.contains("ขวา") && !candidateText.contains("ขวา")) return false
        if (!rawText.contains("ซ้าย") && candidateText.contains("ซ้าย")) return false
        if (!rawText.contains("ขวา") && candidateText.contains("ขวา")) return false
        if (rawText.contains("กลับรถ") && !candidateText.contains("กลับรถ")) return false

        if (Regex("""\bleft\b""").containsMatchIn(rawLower) && !Regex("""\bleft\b""").containsMatchIn(candLower)) return false
        if (Regex("""\bright\b""").containsMatchIn(rawLower) && !Regex("""\bright\b""").containsMatchIn(candLower)) return false
        if (!Regex("""\bleft\b""").containsMatchIn(rawLower) && Regex("""\bleft\b""").containsMatchIn(candLower)) return false
        if (!Regex("""\bright\b""").containsMatchIn(rawLower) && Regex("""\bright\b""").containsMatchIn(candLower)) return false

        return true
    }

    /**
     * Context-aware Thai particle enforcement:
     * - Never on urgent commands
     * - Enforces 15-second cooldown in SMART_CONTEXT mode (unless simulator bypasses cooldown)
     * - Matches active VoiceGender (ค่ะ/นะคะ for Female, ครับ/นะครับ for Male)
     */
    fun applyThaiParticlePolicy(
        text: String,
        gender: VoiceGender,
        particleMode: ThaiParticleMode,
        isUrgent: Boolean,
        isPreviewMode: Boolean = false
    ): Pair<String, Boolean> {
        if (!thaiCharRegex.containsMatchIn(text)) return text to false

        val stripped = stripTrailingThaiParticles(text)

        if (isUrgent || particleMode == ThaiParticleMode.OFF) {
            return stripped to false
        }

        val now = System.currentTimeMillis()
        val cooldownPassed = isPreviewMode || (now - lastParticleAppliedAtMs >= PARTICLE_COOLDOWN_MS)

        val shouldApply = when (particleMode) {
            ThaiParticleMode.OFF -> false
            ThaiParticleMode.POLITE -> true
            ThaiParticleMode.SMART_CONTEXT -> cooldownPassed && isContextualForParticle(stripped)
        }

        if (!shouldApply) {
            return stripped to false
        }

        val hasExistingGenderParticle = hasMatchingGenderParticle(text, gender)
        val result = if (hasExistingGenderParticle) {
            text.trim()
        } else {
            val particle = if (particleMode == ThaiParticleMode.POLITE) {
                gender.primaryParticle
            } else {
                gender.warmParticle
            }
            "$stripped$particle"
        }

        if (!isPreviewMode) {
            lastParticleAppliedAtMs = now
        }
        return result to true
    }

    private fun isContextualForParticle(text: String): Boolean {
        val contextualHints = listOf(
            "กิโลเมตร", "เมตร", "คำนวณ", "เส้นทาง", "จุดหมาย", "ถึง",
            "เตรียมตัว", "ขับตรง", "ยินดีต้อนรับ", "สวัสดี", "ช่วงนี้"
        )
        return text.length >= 16 || contextualHints.any { text.contains(it) }
    }

    private fun hasMatchingGenderParticle(text: String, gender: VoiceGender): Boolean {
        val trimmed = text.trim()
        return when (gender) {
            VoiceGender.FEMALE -> trimmed.endsWith("นะคะ") || trimmed.endsWith("ค่ะ") || trimmed.endsWith("คะ")
            VoiceGender.MALE -> trimmed.endsWith("นะครับ") || trimmed.endsWith("ครับ")
        }
    }

    private fun stripTrailingThaiParticles(text: String): String {
        return text.trim()
            .replace(Regex("""\s*(นะครับ|นะคะ|ครับ|ค่ะ|นะค่ะ|คะ)$"""), "")
            .trim()
    }

    /**
     * Wraps rewritten speech in valid SSML:
     * - Escapes XML characters
     * - Wraps embedded English proper nouns inside Thai text with <lang xml:lang="en-US">
     * - Inserts expressiveness-scaled <break time="...ms"/> pauses
     */
    fun buildBilingualSsml(
        spokenText: String,
        detectedLanguage: String,
        style: SpeakingStyle,
        expressiveness: Float
    ): String {
        val escaped = escapeXml(spokenText.trim())
        val breakMs = (style.breakMsBase * (0.6f + expressiveness * 0.8f)).toInt().coerceIn(80, 320)

        var processed = if (detectedLanguage.startsWith("th")) {
            // Wrap embedded English proper nouns/landmarks inside Thai text for native pronunciation
            latinWordRegex.replace(escaped) { match ->
                """<lang xml:lang="en-US">${match.value}</lang>"""
            }
        } else {
            escaped
        }

        // Insert natural micro-pauses after distance announcements and commas when expressiveness > 0.2
        if (expressiveness >= 0.2f) {
            processed = processed
                .replace(Regex("""(เมตร|กิโลเมตร)\s+"""), """$1 <break time="${breakMs}ms"/> """)
                .replace(Regex("""(meters|metres|kilometers|miles),\s*"""), """$1, <break time="${breakMs}ms"/> """)
        }

        return "<speak>$processed</speak>"
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /**
     * Deterministic, safety-guaranteed rule-based fallback if offline or LLM exceeds 800ms timeout.
     */
    fun ruleBasedNaturalFallback(
        rawText: String,
        gender: VoiceGender,
        isCopilot: Boolean
    ): String {
        val trimmed = rawText.trim()
        if (!isCopilot) return trimmed

        return when {
            trimmed.contains("กำลังคำนวณเส้นทางใหม่") ->
                "ขอคำนวณเส้นทางใหม่ให้สักครู่นะ${gender.primaryParticle}"
            trimmed.startsWith("ในอีก ") && trimmed.contains("เลี้ยว") ->
                trimmed.replaceFirst("ในอีก ", "อีกประมาณ ")
                    .replace("เลี้ยว", "เตรียมตัวเลี้ยว")
            trimmed.startsWith("ขับตรงไปตามเส้นทางอีก ") ->
                trimmed.replaceFirst("ขับตรงไปตามเส้นทางอีก ", "ช่วงนี้ขับตรงยาวๆ ไปตามเส้นทางอีก ")
            trimmed.contains("จุดหมายของคุณ") ->
                trimmed.replace("จุดหมายของคุณ", "ใกล้ถึงจุดหมายแล้ว จุดหมาย")
            trimmed.startsWith("In ", ignoreCase = true) && trimmed.contains("turn ", ignoreCase = true) ->
                trimmed.replaceFirst(Regex("""^In\s+""", RegexOption.IGNORE_CASE), "In about ")
                    .replaceFirst(Regex("""turn\s+""", RegexOption.IGNORE_CASE), "get ready to turn ")
            else -> trimmed
        }
    }
}
