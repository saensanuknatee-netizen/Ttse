package com.example.model

enum class VoiceGender(
    val thaiLabel: String,
    val primaryParticle: String,
    val warmParticle: String
) {
    FEMALE("หญิง", "ค่ะ", "นะคะ"),
    MALE("ชาย", "ครับ", "นะครับ")
}

data class VoiceProfile(
    val id: String,
    val displayName: String,
    val gender: VoiceGender,
    val thaiSubtitle: String,
    val personaPrompt: String,
    val geminiVoiceName: String,
    val cloudChirpThaiVoice: String,
    val cloudChirpEnglishVoice: String,
    val cloudNeuralFallbackThai: String,
    val cloudNeuralFallbackEnglish: String,
    val defaultPitchOffset: Float = 0f
)

object VoiceCatalog {
    val allVoices: List<VoiceProfile> = listOf(
        VoiceProfile(
            id = "orbit",
            displayName = "Orbit",
            gender = VoiceGender.FEMALE,
            thaiSubtitle = "อบอุ่น นุ่มนวล ชัดถ้อยชัดคำ (ค่าเริ่มต้น)",
            personaPrompt = "Warm, calm, articulate female navigator with balanced pacing and reassuring clarity.",
            geminiVoiceName = "Aoede",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Aoede",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Aoede",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-C",
            defaultPitchOffset = 0.0f
        ),
        VoiceProfile(
            id = "pegasus",
            displayName = "Pegasus",
            gender = VoiceGender.MALE,
            thaiSubtitle = "มั่นใจ คมชัด กระฉับกระเฉง เหมาะกับทางด่วน",
            personaPrompt = "Confident, crisp, energetic male highway co-pilot with decisive, alert delivery.",
            geminiVoiceName = "Fenrir",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Fenrir",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Fenrir",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-D",
            defaultPitchOffset = -0.5f
        ),
        VoiceProfile(
            id = "vega",
            displayName = "Vega",
            gender = VoiceGender.FEMALE,
            thaiSubtitle = "สดใส เป็นกันเอง น้ำเสียงอ่อนวัย มีชีวิตชีวา",
            personaPrompt = "Bright, friendly, youthful female navigator with lively and expressive intonation.",
            geminiVoiceName = "Leda",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Leda",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Leda",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-E",
            defaultPitchOffset = 1.0f
        ),
        VoiceProfile(
            id = "ursa",
            displayName = "Ursa",
            gender = VoiceGender.MALE,
            thaiSubtitle = "ทุ้มลึก สุขุม น่าเชื่อถือ เหมือนผู้เชี่ยวชาญ",
            personaPrompt = "Deep, reassuring, mature male voice with steady, grounded, authoritative calm.",
            geminiVoiceName = "Charon",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Charon",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Charon",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-A",
            defaultPitchOffset = -2.0f
        ),
        VoiceProfile(
            id = "nova",
            displayName = "Nova",
            gender = VoiceGender.FEMALE,
            thaiSubtitle = "อ่อนโยน เบาสบาย ผ่อนคลายสไตล์เพื่อนคุย",
            personaPrompt = "Soft, gentle, soothing female voice with relaxed conversational warmth.",
            geminiVoiceName = "Kore",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Kore",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Kore",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-H",
            defaultPitchOffset = 0.5f
        ),
        VoiceProfile(
            id = "lyra",
            displayName = "Lyra",
            gender = VoiceGender.FEMALE,
            thaiSubtitle = "คมชัด มีจังหวะ ทันสมัย สดชื่นเล็กน้อย",
            personaPrompt = "Clear, rhythmic, modern female navigator with slightly upbeat, sleek cadence.",
            geminiVoiceName = "Zephyr",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Zephyr",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Zephyr",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-F",
            defaultPitchOffset = 0.8f
        ),
        VoiceProfile(
            id = "orion",
            displayName = "Orion",
            gender = VoiceGender.MALE,
            thaiSubtitle = "นุ่มลื่น มีเสน่ห์ โทนกลาง สไตล์พอดแคสต์",
            personaPrompt = "Smooth, charismatic, mid-range male voice with natural podcast-like conversational delivery.",
            geminiVoiceName = "Orus",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Orus",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Orus",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-J",
            defaultPitchOffset = -0.8f
        ),
        VoiceProfile(
            id = "eclipse",
            displayName = "Eclipse",
            gender = VoiceGender.MALE,
            thaiSubtitle = "โทนต่ำ นิ่ง เรียบหรู ไม่รบกวนสมาธิ",
            personaPrompt = "Low, calm, minimalist male voice; cool-toned, concise, and unobtrusive.",
            geminiVoiceName = "Enceladus",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Enceladus",
            cloudChirpEnglishVoice = "en-GB-Chirp3-HD-Enceladus",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-GB-Neural2-B",
            defaultPitchOffset = -1.6f
        ),
        VoiceProfile(
            id = "dipper",
            displayName = "Dipper",
            gender = VoiceGender.MALE,
            thaiSubtitle = "โปร่ง สบายๆ เป็นกันเอง สไตล์เพื่อนร่วมทาง",
            personaPrompt = "Light, approachable, conversational male voice with friendly road-trip co-pilot vibe.",
            geminiVoiceName = "Puck",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Puck",
            cloudChirpEnglishVoice = "en-US-Chirp3-HD-Puck",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-US-Neural2-I",
            defaultPitchOffset = 0.2f
        ),
        VoiceProfile(
            id = "capella",
            displayName = "Capella",
            gender = VoiceGender.FEMALE,
            thaiSubtitle = "ชัดเจน มีพลัง เน้นชื่อถนนและทางแยกเด่นชัด",
            personaPrompt = "Expressive, poised, high-clarity female voice with crisp articulation of street names and exits.",
            geminiVoiceName = "Autonoe",
            cloudChirpThaiVoice = "th-TH-Chirp3-HD-Autonoe",
            cloudChirpEnglishVoice = "en-GB-Chirp3-HD-Autonoe",
            cloudNeuralFallbackThai = "th-TH-Neural2-C",
            cloudNeuralFallbackEnglish = "en-GB-Neural2-A",
            defaultPitchOffset = 0.4f
        )
    )

    fun byId(id: String): VoiceProfile =
        allVoices.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: allVoices.first()
}

enum class SpeakingStyle(
    val id: String,
    val thaiLabel: String,
    val shortLabel: String,
    val promptInstruction: String,
    val breakMsBase: Int
) {
    NATURAL(
        id = "natural",
        thaiLabel = "ธรรมชาติ / เพื่อนร่วมทาง",
        shortLabel = "ธรรมชาติ",
        promptInstruction = "Natural, human, relaxed co-pilot tone like a helpful friend sitting in the passenger seat.",
        breakMsBase = 160
    ),
    CALM(
        id = "calm",
        thaiLabel = "นุ่มนวล / ผ่อนคลาย",
        shortLabel = "นุ่มนวล",
        promptInstruction = "Soft, unhurried, gentle, and calming tone that reduces driving stress.",
        breakMsBase = 210
    ),
    ENERGETIC(
        id = "energetic",
        thaiLabel = "กระฉับกระเฉง / สดใส",
        shortLabel = "กระฉับกระเฉง",
        promptInstruction = "Upbeat, lively, clear, and alert tone with cheerful energy.",
        breakMsBase = 120
    ),
    PROFESSIONAL(
        id = "professional",
        thaiLabel = "สุขุม / มืออาชีพ",
        shortLabel = "สุขุม",
        promptInstruction = "Composed, crisp, polished, and concise broadcast-grade clarity.",
        breakMsBase = 140
    ),
    WARM(
        id = "warm",
        thaiLabel = "อบอุ่น / เอาใจใส่",
        shortLabel = "อบอุ่น",
        promptInstruction = "Warm, attentive, caring tone that feels thoughtful and supportive.",
        breakMsBase = 185
    );

    companion object {
        fun byId(id: String): SpeakingStyle = entries.firstOrNull { it.id == id } ?: NATURAL
    }
}

enum class AiRewriteMode(
    val id: String,
    val thaiLabel: String,
    val shortLabel: String,
    val description: String
) {
    OFF(
        id = "off",
        thaiLabel = "ปิด (ข้อความเดิม)",
        shortLabel = "ปิด",
        description = "อ่านตรงตามต้นฉบับ พร้อมเสริมจังหวะ SSML เท่านั้น"
    ),
    LIGHT(
        id = "light",
        thaiLabel = "ปรับคำเล็กน้อย",
        shortLabel = "ปรับเล็กน้อย",
        description = "เกลาคำพูดให้ลื่นไหลและกระชับขึ้น คงโครงสร้างเดิม"
    ),
    COPILOT(
        id = "copilot",
        thaiLabel = "เพื่อนร่วมทางธรรมชาติ (แนะนำ)",
        shortLabel = "เพื่อนร่วมทาง",
        description = "ปรับข้อความนำทางแข็งๆ ให้เป็นภาษาพูดมนุษย์ตามบุคลิกเสียง"
    );

    companion object {
        fun byId(id: String): AiRewriteMode = entries.firstOrNull { it.id == id } ?: COPILOT
    }
}

enum class ThaiParticleMode(
    val id: String,
    val thaiLabel: String,
    val description: String
) {
    SMART_CONTEXT(
        id = "smart_context",
        thaiLabel = "อัจฉริยะตามบริบท (แนะนำ)",
        description = "เติม ค่ะ/นะคะ หรือ ครับ/นะครับ เฉพาะช่วงเหมาะสม พร้อมเว้นระยะอย่างน้อย 15 วินาที และงดใช้ในคำสั่งเร่งด่วน"
    ),
    POLITE(
        id = "polite",
        thaiLabel = "สุภาพมาก",
        description = "ใช้คำลงท้ายสุภาพตามเพศของเสียงในประโยคทั่วไป ยกเว้นคำสั่งเลี้ยวฉุกเฉิน"
    ),
    OFF(
        id = "off",
        thaiLabel = "ปิดหางเสียง",
        description = "ไม่เติมหางเสียง ค่ะ/ครับ เพื่อให้สั้นและกระชับที่สุด"
    );

    companion object {
        fun byId(id: String): ThaiParticleMode = entries.firstOrNull { it.id == id } ?: SMART_CONTEXT
    }
}

enum class SynthesisBackend(
    val id: String,
    val thaiLabel: String,
    val description: String
) {
    AUTO_HYBRID(
        id = "auto_hybrid",
        thaiLabel = "อัตโนมัติ (Cloud TTS + Gemini AI)",
        description = "ใช้ Google Cloud Chirp 3 HD เมื่อมีคีย์ และสลับใช้ Gemini 3.8 Flash TTS อัตโนมัติ"
    ),
    FIREBASE_GEMINI_TTS(
        id = "firebase_gemini_tts",
        thaiLabel = "Gemini 3.8 Flash TTS (พร้อมใช้งาน)",
        description = "สังเคราะห์เสียง AI ผ่าน Firebase AI Logic โดยไม่ต้องกรอก API Key"
    ),
    GOOGLE_CLOUD_TTS(
        id = "google_cloud_tts",
        thaiLabel = "Google Cloud TTS (Chirp 3 HD / Neural2)",
        description = "เน้นความแม่นยำ SSML สูงสุดผ่าน Google Cloud API Key ของคุณ"
    );

    companion object {
        fun byId(id: String): SynthesisBackend = entries.firstOrNull { it.id == id } ?: AUTO_HYBRID
    }
}

enum class PreviewLanguage(
    val id: String,
    val label: String,
    val localeTag: String,
    val samplePrompt: String
) {
    THAI(
        id = "th",
        label = "ไทย",
        localeTag = "th-TH",
        samplePrompt = "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท"
    ),
    ENGLISH(
        id = "en",
        label = "English",
        localeTag = "en-US",
        samplePrompt = "In 500 meters, turn left onto Rama IV Road"
    ),
    MIXED(
        id = "mixed",
        label = "ไทยผสมอังกฤษ",
        localeTag = "th-TH",
        samplePrompt = "ในอีก 400 เมตร เลี้ยวซ้ายเข้าสู่ Terminal 21 Asok"
    )
}

data class TtsUserSettings(
    val voiceId: String = "orbit",
    val style: SpeakingStyle = SpeakingStyle.NATURAL,
    val aiMode: AiRewriteMode = AiRewriteMode.COPILOT,
    val particleMode: ThaiParticleMode = ThaiParticleMode.SMART_CONTEXT,
    val pitchSemitones: Float = 0.0f,
    val expressiveness: Float = 0.65f,
    val speakingRate: Float = 0.95f,
    val synthesisBackend: SynthesisBackend = SynthesisBackend.AUTO_HYBRID
) {
    val activeVoice: VoiceProfile
        get() = VoiceCatalog.byId(voiceId)
}

enum class RewriteRoutePath(val thaiBadge: String) {
    FAST_PATH_URGENT("Fast-Path 0ms (เร่งด่วน)"),
    AI_CACHE_HIT("AI Cache 0ms"),
    GEMINI_LIVE("Gemini AI Live"),
    ORIGINAL_SSML("ข้อความเดิม + SSML"),
    RULE_FALLBACK("Rule Fallback (ปลอดภัย)")
}

data class RewriteResult(
    val rawText: String,
    val spokenText: String,
    val ssml: String,
    val routePath: RewriteRoutePath,
    val latencyMs: Long,
    val safetyVerified: Boolean,
    val particleApplied: Boolean,
    val detectedLanguage: String
)

data class SynthesisResult(
    val pcmBytes: ByteArray,
    val sampleRateHz: Int = 24000,
    val providerLabel: String,
    val fromAudioCache: Boolean,
    val latencyMs: Long
)

data class CacheStats(
    val phraseCacheCount: Int = 0,
    val audioClipCount: Int = 0,
    val audioCacheBytes: Long = 0L
)

data class SimulatorPreset(
    val title: String,
    val categoryTag: String,
    val rawText: String,
    val localeTag: String
)

object SimulatorPresets {
    val all: List<SimulatorPreset> = listOf(
        SimulatorPreset(
            title = "เตรียมเลี้ยวเข้าถนนใหญ่ (800ม.)",
            categoryTag = "AI Co-Pilot",
            rawText = "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท",
            localeTag = "th-TH"
        ),
        SimulatorPreset(
            title = "ทางตรงยาวบนทางด่วน (6 กม.)",
            categoryTag = "Cruising",
            rawText = "ขับตรงไปตามเส้นทางอีก 6 กิโลเมตร",
            localeTag = "th-TH"
        ),
        SimulatorPreset(
            title = "เลี้ยวซ้ายทันที (เร่งด่วน 0ms)",
            categoryTag = "Fast-Path",
            rawText = "เลี้ยวซ้าย",
            localeTag = "th-TH"
        ),
        SimulatorPreset(
            title = "คำนวณเส้นทางใหม่",
            categoryTag = "Rerouting",
            rawText = "กำลังคำนวณเส้นทางใหม่",
            localeTag = "th-TH"
        ),
        SimulatorPreset(
            title = "ไทยผสมชื่อสถานที่อังกฤษ",
            categoryTag = "Bilingual",
            rawText = "ในอีก 400 เมตร เลี้ยวซ้ายเข้าสู่ Terminal 21 Asok",
            localeTag = "th-TH"
        ),
        SimulatorPreset(
            title = "English Turn Prompt",
            categoryTag = "English",
            rawText = "In 500 meters, turn left onto Rama IV Road",
            localeTag = "en-US"
        ),
        SimulatorPreset(
            title = "ใกล้ถึงจุดหมาย",
            categoryTag = "Arrival",
            rawText = "จุดหมายของคุณอยู่ทางด้านซ้ายมือ",
            localeTag = "th-TH"
        )
    )
}
