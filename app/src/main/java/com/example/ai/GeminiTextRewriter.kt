package com.example.ai

import android.util.Log
import com.example.model.AiRewriteMode
import com.example.model.SpeakingStyle
import com.example.model.ThaiParticleMode
import com.example.model.VoiceProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiTextRewriter"

/**
 * Powered by Firebase AI Logic (`Firebase.ai`) with zero client-side key setup required:
 * - Fast Navigation Rewriter: `gemini-3.1-flash-lite` (thinkingBudget = 0 for low-latency rewriting)
 * - Audio Transcription: `gemini-3.5-transcribe` (transcribes spoken microphone phrases)
 * - Deep Prosody & Safety Audit: `gemini-4-carbon` with `ThinkingLevel.HIGH` (no maxOutputTokens)
 * Also supports an optional user-provided custom Gemini API key via EncryptedSharedPreferences.
 */
class GeminiTextRewriter {

    private val fastRewriteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.35f
                topP = 0.9f
                thinkingConfig = thinkingConfig {
                    thinkingBudget = 0
                }
            }
        )
    }

    private val transcribeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-transcribe"
        )
    }

    // High-thinking model for complex prosody, bilingual code-switching & safety analysis.
    // Intentionally omits maxOutputTokens per ThinkingLevel.HIGH requirement.
    private val deepReasoningModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-carbon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun rewriteNavigationText(
        rawText: String,
        voice: VoiceProfile,
        style: SpeakingStyle,
        aiMode: AiRewriteMode,
        particleMode: ThaiParticleMode,
        detectedLanguage: String,
        timeoutMs: Long,
        customGeminiApiKey: String = ""
    ): String? {
        val prompt = buildRewritePrompt(
            rawText = rawText,
            voice = voice,
            style = style,
            aiMode = aiMode,
            particleMode = particleMode,
            detectedLanguage = detectedLanguage
        )

        return try {
            withTimeout(timeoutMs) {
                if (customGeminiApiKey.isNotBlank()) {
                    callCustomGeminiRest(prompt, customGeminiApiKey)
                        ?: callFirebaseFastRewrite(prompt)
                } else {
                    callFirebaseFastRewrite(prompt)
                }
            }
        } catch (e: CancellationException) {
            // Note: TimeoutCancellationException is a subclass of CancellationException.
            // If we timed out specifically, let caller fall back; otherwise propagate real cancellation.
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "AI rewrite timed out after ${timeoutMs}ms; falling back safely.")
                null
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI rewrite failed", e)
            null
        }
    }

    private suspend fun callFirebaseFastRewrite(prompt: String): String? {
        val response = fastRewriteModel.generateContent(prompt)
        return sanitizeSingleLineOutput(response.text)
    }

    private suspend fun callCustomGeminiRest(prompt: String, apiKey: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.35)
                        put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                    })
                }
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val text = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                    sanitizeSingleLineOutput(text)
                }
            }.getOrNull()
        }

    /**
     * Transcribes recorded WAV audio bytes using `gemini-3.5-transcribe`.
     */
    suspend fun transcribeNavigationAudio(wavBytes: ByteArray): Result<String> {
        return try {
            val response = withTimeout(45_000L) {
                transcribeModel.generateContent(
                    content {
                        inlineData(wavBytes, "audio/wav")
                        text(
                            "Transcribe this spoken Thai or English navigation command accurately into plain text. " +
                                "Preserve all numbers, distances, street names, and mixed Thai-English landmarks verbatim. " +
                                "Output ONLY the transcribed text with no explanation or quotes."
                        )
                    }
                )
            }
            val transcript = sanitizeSingleLineOutput(response.text)
            if (!transcript.isNullOrBlank()) {
                Result.success(transcript)
            } else {
                Result.failure(IllegalStateException("ไม่พบข้อความเสียงที่ชัดเจน กรุณาลองพูดใหม่อีกครั้ง"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            Result.failure(e)
        }
    }

    /**
     * Performs a deep bilingual prosody, SSML, and driving-safety audit using
     * `gemini-4-carbon` with `ThinkingLevel.HIGH` (no maxOutputTokens).
     */
    suspend fun analyzeProsodyWithHighThinking(
        rawText: String,
        rewrittenText: String,
        ssml: String,
        voice: VoiceProfile,
        style: SpeakingStyle
    ): Result<String> {
        val auditPrompt = """
            คุณคือผู้เชี่ยวชาญด้านระบบเสียงนำทางรถยนต์ (Automotive Voice UX) และสัทศาสตร์สองภาษา (ไทย-อังกฤษ)
            โปรดวิเคราะห์ประโยคนำทางต่อไปนี้เชิงลึกเป็นภาษาไทยที่อ่านง่าย กระชับ เป็นหัวข้อชัดเจน:
            
            - ข้อความต้นฉบับจาก Google Maps: "$rawText"
            - คำพูดที่ AI ปรับใหม่: "$rewrittenText"
            - SSML ที่ส่งให้สังเคราะห์เสียง: $ssml
            - โปรไฟล์เสียง: ${voice.displayName} (${voice.gender.thaiLabel}) — ${voice.personaPrompt}
            - สไตล์การพูด: ${style.thaiLabel}
            
            โปรดวิเคราะห์ 4 ประเด็น:
            1. ความปลอดภัยในการขับขี่: ตรวจสอบตัวเลข ระยะทาง ชื่อถนน ซอย ทางออก และทิศทาง (ซ้าย/ขวา) ว่าครบถ้วน 100% หรือไม่
            2. ความเป็นธรรมชาติและบุคลิกเสียง: ประเมินจังหวะการพูด น้ำเสียง และความเหมาะสมของหางเสียงไทย
            3. การออกเสียงสองภาษา (Code-Switching & SSML): ตรวจสอบการสลับภาษาไทย-อังกฤษและจุดพักหายใจ (<break>)
            4. ข้อเสนอแนะประโยคทางเลือก 2 แบบที่เหมาะกับบุคลิก ${voice.displayName} มากยิ่งขึ้น
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                deepReasoningModel.generateContent(auditPrompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("โมเดลไม่สามารถสร้างผลการวิเคราะห์ได้ในขณะนี้"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "High-thinking prosody audit failed", e)
            Result.failure(e)
        }
    }

    /**
     * Pings Firebase AI Logic (and optional custom Gemini key) for Settings connection diagnostics.
     */
    suspend fun testGeminiConnection(customGeminiApiKey: String): Result<String> {
        return try {
            val start = System.currentTimeMillis()
            val reply = withTimeout(15_000L) {
                if (customGeminiApiKey.isNotBlank()) {
                    callCustomGeminiRest("Reply with OK only.", customGeminiApiKey)
                        ?: throw IllegalStateException("Custom Gemini API key rejected the request.")
                } else {
                    fastRewriteModel.generateContent("Reply with OK only.").text
                        ?: throw IllegalStateException("Empty response from Firebase AI Logic.")
                }
            }
            val elapsed = System.currentTimeMillis() - start
            val source = if (customGeminiApiKey.isNotBlank()) "Custom Gemini Key" else "Firebase AI Logic (gemini-3.1-flash-lite)"
            Result.success("$source เชื่อมต่อสำเร็จ (${elapsed}ms) • ${reply.trim().take(20)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRewritePrompt(
        rawText: String,
        voice: VoiceProfile,
        style: SpeakingStyle,
        aiMode: AiRewriteMode,
        particleMode: ThaiParticleMode,
        detectedLanguage: String
    ): String {
        val modeDirective = when (aiMode) {
            AiRewriteMode.LIGHT ->
                "Lightly polish awkward robotic phrasing to sound smoother while staying very concise and close to the original structure."
            AiRewriteMode.COPILOT ->
                "Rewrite robotic GPS instructions into warm, natural, human co-pilot speech tailored to the voice persona and style."
            AiRewriteMode.OFF ->
                "Return the exact original text."
        }

        val particleRule = when (particleMode) {
            ThaiParticleMode.OFF ->
                "Do NOT add Thai polite particles (ค่ะ/นะคะ/ครับ/นะครับ)."
            ThaiParticleMode.POLITE ->
                "If Thai, end naturally with '${voice.gender.primaryParticle}' or '${voice.gender.warmParticle}'."
            ThaiParticleMode.SMART_CONTEXT ->
                "If Thai and contextual (distance ahead, cruising, rerouting, arrival), you may end with '${voice.gender.warmParticle}' or '${voice.gender.primaryParticle}'. Never add particles to urgent short commands."
        }

        return """
            You are AuraNav, an in-car AI co-pilot speech rewriter for Google Maps navigation.
            Active Voice Persona: ${voice.displayName} (${voice.gender.name}) — ${voice.personaPrompt}
            Speaking Style: ${style.thaiLabel} — ${style.promptInstruction}
            Rewrite Mode: $modeDirective
            Detected Language: $detectedLanguage
            Thai Particle Policy: $particleRule

            STRICT DRIVING-SAFETY GUARDRAILS (NEVER VIOLATE):
            1. NEVER change, round, drop, or translate ANY numbers, distances (e.g. 500 เมตร, 800 เมตร, 6 กิโลเมตร), exit numbers, Soi numbers, or highway numbers.
            2. NEVER change or invert turn directions (ซ้าย/ขวา, กลับรถ, left/right, U-turn).
            3. NEVER alter road names, proper nouns, or English landmarks inside Thai text (keep exact English spelling like Terminal 21 Asok, Rama IV Road).
            4. Output ONLY one clean spoken sentence in the same language as input. No quotes, no markdown, no SSML tags, no explanations.

            Examples:
            Input: ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท
            Output (Orbit/Warm): อีกประมาณ 800 เมตร เตรียมตัวเลี้ยวขวาเข้าถนนสุขุมวิทนะคะ
            Input: ขับตรงไปตามเส้นทางอีก 6 กิโลเมตร
            Output (Orion/Co-Pilot): ช่วงนี้ขับตรงยาวๆ ไปตามเส้นทางอีก 6 กิโลเมตรนะครับ
            Input: กำลังคำนวณเส้นทางใหม่
            Output (Vega/Bright): ขอคำนวณเส้นทางใหม่ให้แป๊บนึงนะคะ
            Input: In 500 meters, turn left onto Rama IV Road
            Output (English): In about 500 meters, get ready to turn left onto Rama IV Road.

            Input: $rawText
            Output:
        """.trimIndent()
    }

    private fun sanitizeSingleLineOutput(raw: String?): String? {
        val cleaned = raw?.trim()
            ?.removePrefix("\"")?.removeSuffix("\"")
            ?.removePrefix("“")?.removeSuffix("”")
            ?.replace(Regex("""^Output[^:]*:\s*""", RegexOption.IGNORE_CASE), "")
            ?.lines()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
        return cleaned?.takeIf { it.isNotBlank() }
    }
}
