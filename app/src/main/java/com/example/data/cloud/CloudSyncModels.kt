package com.example.data.cloud

import androidx.annotation.Keep
import com.example.model.AiRewriteMode
import com.example.model.SpeakingStyle
import com.example.model.SynthesisBackend
import com.example.model.ThaiParticleMode
import com.example.model.TtsUserSettings
import com.example.model.VoiceCatalog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudTtsSettingsDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val voiceId: String = "orbit",
    val styleId: String = "natural",
    val aiModeId: String = "copilot",
    val particleModeId: String = "smart_context",
    val pitchSemitones: Double = 0.0,
    val expressiveness: Double = 0.65,
    val speakingRate: Double = 0.95,
    val synthesisBackendId: String = "auto_hybrid",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toTtsUserSettings(): TtsUserSettings {
        return TtsUserSettings(
            voiceId = VoiceCatalog.byId(voiceId).id,
            style = SpeakingStyle.byId(styleId),
            aiMode = AiRewriteMode.byId(aiModeId),
            particleMode = ThaiParticleMode.byId(particleModeId),
            pitchSemitones = pitchSemitones.toFloat().coerceIn(-3.0f, 3.0f),
            expressiveness = expressiveness.toFloat().coerceIn(0.0f, 1.0f),
            speakingRate = speakingRate.toFloat().coerceIn(0.75f, 1.35f),
            synthesisBackend = SynthesisBackend.byId(synthesisBackendId)
        )
    }
}

@Keep
@IgnoreExtraProperties
data class SavedNavigationPhraseDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val rawText: String = "",
    val rewrittenText: String = "",
    val voiceId: String = "orbit",
    val localeTag: String = "th-TH",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
