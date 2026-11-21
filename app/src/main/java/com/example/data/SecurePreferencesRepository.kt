package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.AiRewriteMode
import com.example.model.SpeakingStyle
import com.example.model.SynthesisBackend
import com.example.model.ThaiParticleMode
import com.example.model.TtsUserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SecurePrefsRepo"
private const val ENCRYPTED_PREFS_NAME = "auranav_secure_prefs"
private const val SETTINGS_PREFS_NAME = "auranav_tts_settings"

private const val KEY_GEMINI_API = "gemini_api_key"
private const val KEY_CLOUD_TTS_API = "google_cloud_tts_api_key"
private const val KEY_VOICE_ID = "voice_id"
private const val KEY_STYLE_ID = "style_id"
private const val KEY_AI_MODE = "ai_mode"
private const val KEY_PARTICLE_MODE = "particle_mode"
private const val KEY_PITCH = "pitch_semitones"
private const val KEY_EXPRESSIVENESS = "expressiveness"
private const val KEY_SPEED = "speaking_rate"
private const val KEY_BACKEND = "synthesis_backend"

class SecurePreferencesRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences = createEncryptedPrefs(appContext)
    private val settingsPrefs: SharedPreferences =
        appContext.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TtsUserSettings> = _settings.asStateFlow()

    private val _cloudTtsApiKey = MutableStateFlow(encryptedPrefs.getString(KEY_CLOUD_TTS_API, "").orEmpty())
    val cloudTtsApiKey: StateFlow<String> = _cloudTtsApiKey.asStateFlow()

    private val _customGeminiApiKey = MutableStateFlow(encryptedPrefs.getString(KEY_GEMINI_API, "").orEmpty())
    val customGeminiApiKey: StateFlow<String> = _customGeminiApiKey.asStateFlow()

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = loadSettings()
    }

    init {
        settingsPrefs.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable; falling back to private prefs", e)
            context.getSharedPreferences("${ENCRYPTED_PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun loadSettings(): TtsUserSettings {
        return TtsUserSettings(
            voiceId = settingsPrefs.getString(KEY_VOICE_ID, "orbit") ?: "orbit",
            style = SpeakingStyle.byId(settingsPrefs.getString(KEY_STYLE_ID, SpeakingStyle.NATURAL.id) ?: SpeakingStyle.NATURAL.id),
            aiMode = AiRewriteMode.byId(settingsPrefs.getString(KEY_AI_MODE, AiRewriteMode.COPILOT.id) ?: AiRewriteMode.COPILOT.id),
            particleMode = ThaiParticleMode.byId(
                settingsPrefs.getString(KEY_PARTICLE_MODE, ThaiParticleMode.SMART_CONTEXT.id)
                    ?: ThaiParticleMode.SMART_CONTEXT.id
            ),
            pitchSemitones = settingsPrefs.getFloat(KEY_PITCH, 0.0f).coerceIn(-3.0f, 3.0f),
            expressiveness = settingsPrefs.getFloat(KEY_EXPRESSIVENESS, 0.65f).coerceIn(0.0f, 1.0f),
            speakingRate = settingsPrefs.getFloat(KEY_SPEED, 0.95f).coerceIn(0.75f, 1.35f),
            synthesisBackend = SynthesisBackend.byId(
                settingsPrefs.getString(KEY_BACKEND, SynthesisBackend.AUTO_HYBRID.id)
                    ?: SynthesisBackend.AUTO_HYBRID.id
            )
        )
    }

    fun getLatestSettings(): TtsUserSettings {
        val loaded = loadSettings()
        _settings.value = loaded
        return loaded
    }

    fun saveSettings(newSettings: TtsUserSettings) {
        settingsPrefs.edit()
            .putString(KEY_VOICE_ID, newSettings.voiceId)
            .putString(KEY_STYLE_ID, newSettings.style.id)
            .putString(KEY_AI_MODE, newSettings.aiMode.id)
            .putString(KEY_PARTICLE_MODE, newSettings.particleMode.id)
            .putFloat(KEY_PITCH, newSettings.pitchSemitones.coerceIn(-3.0f, 3.0f))
            .putFloat(KEY_EXPRESSIVENESS, newSettings.expressiveness.coerceIn(0.0f, 1.0f))
            .putFloat(KEY_SPEED, newSettings.speakingRate.coerceIn(0.75f, 1.35f))
            .putString(KEY_BACKEND, newSettings.synthesisBackend.id)
            .apply()
        _settings.value = newSettings
    }

    fun saveApiKeys(cloudTtsKey: String, customGeminiKey: String) {
        val trimmedCloud = cloudTtsKey.trim()
        val trimmedGemini = customGeminiKey.trim()
        encryptedPrefs.edit()
            .putString(KEY_CLOUD_TTS_API, trimmedCloud)
            .putString(KEY_GEMINI_API, trimmedGemini)
            .apply()
        _cloudTtsApiKey.value = trimmedCloud
        _customGeminiApiKey.value = trimmedGemini
    }

    fun getCloudTtsApiKey(): String = encryptedPrefs.getString(KEY_CLOUD_TTS_API, "").orEmpty()

    fun getCustomGeminiApiKey(): String = encryptedPrefs.getString(KEY_GEMINI_API, "").orEmpty()

    companion object {
        @Volatile
        private var instance: SecurePreferencesRepository? = null

        fun getInstance(context: Context): SecurePreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferencesRepository(context).also { instance = it }
            }
        }
    }
}
