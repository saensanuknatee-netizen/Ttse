package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiNavigationRewriter
import com.example.ai.GeminiTextRewriter
import com.example.data.SecurePreferencesRepository
import com.example.data.cache.AudioPcmDiskCache
import com.example.data.cache.AuraNavDatabase
import com.example.data.cloud.CloudSyncRepository
import com.example.data.cloud.SavedNavigationPhraseDoc
import com.example.model.AiRewriteMode
import com.example.model.CacheStats
import com.example.model.PreviewLanguage
import com.example.model.RewriteResult
import com.example.model.SpeakingStyle
import com.example.model.SynthesisBackend
import com.example.model.ThaiParticleMode
import com.example.model.TtsUserSettings
import com.example.tts.DualTtsAudioClient
import com.example.tts.MicrophoneAudioRecorder
import com.example.tts.PcmAudioPlayer
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AuraNavViewModel"

sealed interface AudioPlayState {
    data object Idle : AudioPlayState
    data object Rewriting : AudioPlayState
    data object Synthesizing : AudioPlayState
    data object Playing : AudioPlayState
    data class Error(val message: String) : AudioPlayState
}

sealed interface DeepAnalysisState {
    data object Idle : DeepAnalysisState
    data object Loading : DeepAnalysisState
    data class Success(val report: String) : DeepAnalysisState
    data class Error(val message: String) : DeepAnalysisState
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Result(val success: Boolean, val summary: String) : ConnectionTestState
}

sealed interface SavedPhrasesUiState {
    data object Loading : SavedPhrasesUiState
    data class Success(val phrases: List<SavedNavigationPhraseDoc>) : SavedPhrasesUiState
    data class Error(val message: String) : SavedPhrasesUiState
}

data class AuraNavUiState(
    val draftSettings: TtsUserSettings = TtsUserSettings(),
    val savedSettings: TtsUserSettings = TtsUserSettings(),
    val isCloudSynced: Boolean = false,
    val cloudSyncError: String? = null,
    val previewLanguage: PreviewLanguage = PreviewLanguage.THAI,
    val previewState: AudioPlayState = AudioPlayState.Idle,
    val previewRewrite: RewriteResult? = null,
    val previewProviderLabel: String = "",
    val previewAudioLatencyMs: Long? = null,
    val previewFromAudioCache: Boolean = false,
    val simulatorInput: String = "ในอีก 800 เมตร เลี้ยวขวาเข้าสู่ ถนนสุขุมวิท",
    val simulatorLocaleTag: String = "th-TH",
    val simulatorPlayState: AudioPlayState = AudioPlayState.Idle,
    val simulatorRewrite: RewriteResult? = null,
    val simulatorProviderLabel: String = "",
    val simulatorAudioLatencyMs: Long? = null,
    val simulatorFromAudioCache: Boolean = false,
    val isRecordingMic: Boolean = false,
    val isTranscribingMic: Boolean = false,
    val micErrorMessage: String? = null,
    val deepAnalysisState: DeepAnalysisState = DeepAnalysisState.Idle,
    val cloudTtsKeyInput: String = "",
    val customGeminiKeyInput: String = "",
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
    val snackbarMessage: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = draftSettings != savedSettings
}

class AuraNavViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferencesRepository.getInstance(application)
    private val database = AuraNavDatabase.getInstance(application)
    private val phraseDao = database.phraseCacheDao()
    private val pcmDiskCache = AudioPcmDiskCache.getInstance(application)
    private val cloudSyncRepo = CloudSyncRepository(application)
    private val geminiRewriter = GeminiTextRewriter()
    private val navigationRewriter = AiNavigationRewriter(phraseDao, geminiRewriter, securePrefs)
    private val audioClient = DualTtsAudioClient(application, pcmDiskCache, securePrefs)
    private val audioPlayer = PcmAudioPlayer()
    private val micRecorder = MicrophoneAudioRecorder()

    private var activePlaybackJob: Job? = null
    private var activeRecordingJob: Job? = null

    private val _uiState = MutableStateFlow(
        AuraNavUiState(
            draftSettings = securePrefs.getLatestSettings(),
            savedSettings = securePrefs.getLatestSettings(),
            cloudTtsKeyInput = securePrefs.getCloudTtsApiKey(),
            customGeminiKeyInput = securePrefs.getCustomGeminiApiKey()
        )
    )
    val uiState: StateFlow<AuraNavUiState> = _uiState.asStateFlow()

    val cacheStats: StateFlow<CacheStats> = combine(
        phraseDao.observeCount(),
        pcmDiskCache.clipCount,
        pcmDiskCache.totalBytes
    ) { phraseCount, clipCount, bytes ->
        CacheStats(
            phraseCacheCount = phraseCount,
            audioClipCount = clipCount,
            audioCacheBytes = bytes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CacheStats()
    )

    // Real-time Firestore stream with mandatory two-tier CancellationException / FirebaseFirestoreException unwrapping
    val savedPhrasesState: StateFlow<SavedPhrasesUiState> = cloudSyncRepo.observeSavedPhrases()
        .map<List<SavedNavigationPhraseDoc>, SavedPhrasesUiState> { SavedPhrasesUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(SavedPhrasesUiState.Error(rootError.message ?: "ไม่สามารถซิงค์คลังประโยคจากคลาวด์ได้"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SavedPhrasesUiState.Loading
        )

    init {
        observeRemoteCloudSettings()
        refreshPreviewTextOnly()
    }

    private fun observeRemoteCloudSettings() {
        viewModelScope.launch {
            cloudSyncRepo.observeCloudSettings()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    _uiState.update {
                        it.copy(cloudSyncError = rootError.message ?: "ไม่สามารถซิงค์การตั้งค่าจากคลาวด์ได้")
                    }
                }
                .collect { remoteDoc ->
                    if (remoteDoc != null) {
                        val syncedSettings = remoteDoc.toTtsUserSettings()
                        securePrefs.saveSettings(syncedSettings)
                        _uiState.update { state ->
                            val shouldUpdateDraft = !state.hasUnsavedChanges
                            state.copy(
                                savedSettings = syncedSettings,
                                draftSettings = if (shouldUpdateDraft) syncedSettings else state.draftSettings,
                                isCloudSynced = true,
                                cloudSyncError = null
                            )
                        }
                        refreshPreviewTextOnly()
                    }
                }
        }
    }

    fun selectVoice(voiceId: String) {
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.copy(voiceId = voiceId))
        }
        refreshPreviewTextOnly()
    }

    fun updateSpeakingStyle(style: SpeakingStyle) {
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.copy(style = style))
        }
        refreshPreviewTextOnly()
    }

    fun updateAiRewriteMode(mode: AiRewriteMode) {
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.copy(aiMode = mode))
        }
        refreshPreviewTextOnly()
    }

    fun updateThaiParticleMode(mode: ThaiParticleMode) {
        val updated = _uiState.value.draftSettings.copy(particleMode = mode)
        securePrefs.saveSettings(updated)
        _uiState.update { state ->
            state.copy(draftSettings = updated, savedSettings = updated)
        }
        syncSettingsToCloud(updated, showToast = false)
        refreshPreviewTextOnly()
    }

    fun updateSynthesisBackend(backend: SynthesisBackend) {
        val updated = _uiState.value.draftSettings.copy(synthesisBackend = backend)
        securePrefs.saveSettings(updated)
        _uiState.update { state ->
            state.copy(draftSettings = updated, savedSettings = updated)
        }
        syncSettingsToCloud(updated, showToast = false)
    }

    fun updatePitch(semitones: Float) {
        val snapped = (Math.round(semitones * 2f) / 2f).coerceIn(-3.0f, 3.0f)
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.copy(pitchSemitones = snapped))
        }
    }

    fun updateExpressiveness(value: Float) {
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.copy(expressiveness = value.coerceIn(0f, 1f)))
        }
    }

    fun updateSpeakingRate(rate: Float) {
        val snapped = (Math.round(rate * 20f) / 20f).coerceIn(0.75f, 1.35f)
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.copy(speakingRate = snapped))
        }
    }

    fun selectPreviewLanguage(language: PreviewLanguage) {
        _uiState.update { it.copy(previewLanguage = language) }
        refreshPreviewTextOnly()
    }

    fun confirmSettings() {
        val draft = _uiState.value.draftSettings
        securePrefs.saveSettings(draft)
        _uiState.update {
            it.copy(
                savedSettings = draft,
                snackbarMessage = "บันทึกเสียง ${draft.activeVoice.displayName} และซิงค์ขึ้น Cloud Firestore แล้ว"
            )
        }
        syncSettingsToCloud(draft, showToast = false)
    }

    private fun syncSettingsToCloud(settings: TtsUserSettings, showToast: Boolean) {
        viewModelScope.launch {
            val result = cloudSyncRepo.saveTtsSettings(settings)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isCloudSynced = true,
                            cloudSyncError = null,
                            snackbarMessage = if (showToast) "ซิงค์การตั้งค่าขึ้นคลาวด์สำเร็จ" else it.snackbarMessage
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(cloudSyncError = err.localizedMessage ?: "บันทึกขึ้นคลาวด์ไม่สำเร็จ (บันทึกในเครื่องแล้ว)")
                    }
                }
            )
        }
    }

    fun cancelDraftChanges() {
        stopAudioPlayback()
        val saved = _uiState.value.savedSettings
        _uiState.update {
            it.copy(
                draftSettings = saved,
                snackbarMessage = "ยกเลิกการเปลี่ยนแปลงแล้ว"
            )
        }
        refreshPreviewTextOnly()
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun refreshPreviewTextOnly() {
        val state = _uiState.value
        val sample = state.previewLanguage.samplePrompt
        val settings = state.draftSettings
        viewModelScope.launch {
            try {
                val rewrite = navigationRewriter.rewriteForNavigation(
                    rawText = sample,
                    settings = settings,
                    localeOverride = state.previewLanguage.localeTag,
                    isPreviewMode = true
                )
                _uiState.update { current ->
                    if (current.draftSettings.voiceId == settings.voiceId &&
                        current.previewLanguage == state.previewLanguage
                    ) {
                        current.copy(previewRewrite = rewrite)
                    } else {
                        current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Preview rewrite refresh skipped", e)
            }
        }
    }

    fun playMainScreenPreview() {
        if (_uiState.value.previewState is AudioPlayState.Playing ||
            _uiState.value.previewState is AudioPlayState.Synthesizing ||
            _uiState.value.previewState is AudioPlayState.Rewriting
        ) {
            stopAudioPlayback()
            return
        }

        val state = _uiState.value
        val settings = state.draftSettings
        val sample = state.previewLanguage.samplePrompt
        val localeTag = state.previewLanguage.localeTag

        activePlaybackJob?.cancel()
        audioPlayer.stop()

        activePlaybackJob = viewModelScope.launch {
            _uiState.update { it.copy(previewState = AudioPlayState.Rewriting) }
            try {
                val rewrite = navigationRewriter.rewriteForNavigation(
                    rawText = sample,
                    settings = settings,
                    localeOverride = localeTag,
                    isPreviewMode = true
                )
                _uiState.update {
                    it.copy(
                        previewRewrite = rewrite,
                        previewState = AudioPlayState.Synthesizing
                    )
                }

                val synthesis = audioClient.synthesizeToPcm(
                    rewrite = rewrite,
                    settings = settings,
                    isPreviewMode = true
                )

                _uiState.update {
                    it.copy(
                        previewProviderLabel = synthesis.providerLabel,
                        previewAudioLatencyMs = synthesis.latencyMs,
                        previewFromAudioCache = synthesis.fromAudioCache
                    )
                }

                if (synthesis.pcmBytes.isEmpty()) {
                    _uiState.update {
                        it.copy(previewState = AudioPlayState.Error("ไม่สามารถสังเคราะห์เสียงได้ในขณะนี้ กรุณาตรวจสอบอินเทอร์เน็ตหรือการตั้งค่า TTS"))
                    }
                    return@launch
                }

                _uiState.update { it.copy(previewState = AudioPlayState.Playing) }
                audioPlayer.playPcm(synthesis.pcmBytes, synthesis.sampleRateHz)
                _uiState.update { it.copy(previewState = AudioPlayState.Idle) }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(previewState = AudioPlayState.Idle) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Preview playback failed", e)
                _uiState.update {
                    it.copy(previewState = AudioPlayState.Error(e.localizedMessage ?: "เกิดข้อผิดพลาดในการเล่นเสียงตัวอย่าง"))
                }
            }
        }
    }

    fun updateSimulatorInput(text: String, localeTag: String? = null) {
        _uiState.update {
            it.copy(
                simulatorInput = text,
                simulatorLocaleTag = localeTag ?: it.simulatorLocaleTag,
                micErrorMessage = null
            )
        }
    }

    fun runSimulatorSpeak(overrideText: String? = null, overrideLocale: String? = null) {
        val text = (overrideText ?: _uiState.value.simulatorInput).trim()
        if (text.isEmpty()) return
        val locale = overrideLocale ?: _uiState.value.simulatorLocaleTag
        val settings = _uiState.value.draftSettings

        if (overrideText != null) {
            _uiState.update { it.copy(simulatorInput = overrideText, simulatorLocaleTag = locale) }
        }

        activePlaybackJob?.cancel()
        audioPlayer.stop()

        activePlaybackJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    simulatorPlayState = AudioPlayState.Rewriting,
                    deepAnalysisState = DeepAnalysisState.Idle
                )
            }
            try {
                val rewrite = navigationRewriter.rewriteForNavigation(
                    rawText = text,
                    settings = settings,
                    localeOverride = null,
                    isPreviewMode = true
                )
                _uiState.update {
                    it.copy(
                        simulatorRewrite = rewrite,
                        simulatorPlayState = AudioPlayState.Synthesizing
                    )
                }

                val synthesis = audioClient.synthesizeToPcm(
                    rewrite = rewrite,
                    settings = settings,
                    isPreviewMode = true
                )
                _uiState.update {
                    it.copy(
                        simulatorProviderLabel = synthesis.providerLabel,
                        simulatorAudioLatencyMs = synthesis.latencyMs,
                        simulatorFromAudioCache = synthesis.fromAudioCache
                    )
                }

                if (synthesis.pcmBytes.isEmpty()) {
                    _uiState.update {
                        it.copy(simulatorPlayState = AudioPlayState.Error("ไม่สามารถสร้างเสียงพูดได้ กรุณาตรวจสอบเครือข่ายหรือคีย์ API"))
                    }
                    return@launch
                }

                _uiState.update { it.copy(simulatorPlayState = AudioPlayState.Playing) }
                audioPlayer.playPcm(synthesis.pcmBytes, synthesis.sampleRateHz)
                _uiState.update { it.copy(simulatorPlayState = AudioPlayState.Idle) }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(simulatorPlayState = AudioPlayState.Idle) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Simulator synthesis failed", e)
                _uiState.update {
                    it.copy(simulatorPlayState = AudioPlayState.Error(e.localizedMessage ?: "เกิดข้อผิดพลาดในการจำลองเสียง"))
                }
            }
        }
    }

    fun saveCurrentSimulatorPhraseToCloud() {
        val state = _uiState.value
        val raw = state.simulatorInput.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "กรุณากรอกข้อความก่อนบันทึก") }
            return
        }
        val title = if (raw.length > 32) "${raw.take(32)}…" else raw
        val rewritten = state.simulatorRewrite?.takeIf { it.rawText == raw }?.spokenText
        val detectedLocale = state.simulatorRewrite?.detectedLanguage ?: state.simulatorLocaleTag

        viewModelScope.launch {
            val result = cloudSyncRepo.saveCustomPhrase(
                title = title,
                rawText = raw,
                rewrittenText = rewritten,
                voiceId = state.draftSettings.voiceId,
                localeTag = detectedLocale
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(snackbarMessage = "บันทึกประโยคนำทางลง Cloud Firestore แล้ว") }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(snackbarMessage = err.localizedMessage ?: "ไม่สามารถบันทึกประโยคลงคลาวด์ได้")
                    }
                }
            )
        }
    }

    fun deleteSavedCloudPhrase(phraseId: String) {
        viewModelScope.launch {
            val result = cloudSyncRepo.deleteCustomPhrase(phraseId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(snackbarMessage = "ลบประโยคออกจากคลังคลาวด์แล้ว") }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(snackbarMessage = err.localizedMessage ?: "ไม่สามารถลบประโยคได้")
                    }
                }
            )
        }
    }

    fun stopAudioPlayback() {
        activePlaybackJob?.cancel()
        audioPlayer.stop()
        _uiState.update {
            it.copy(
                previewState = AudioPlayState.Idle,
                simulatorPlayState = AudioPlayState.Idle
            )
        }
    }

    fun toggleMicrophoneTranscription() {
        if (_uiState.value.isRecordingMic) {
            micRecorder.stopRecording()
            return
        }

        stopAudioPlayback()
        activeRecordingJob?.cancel()
        activeRecordingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRecordingMic = true,
                    isTranscribingMic = false,
                    micErrorMessage = null
                )
            }
            try {
                val wavBytes = micRecorder.recordUntilStopped()
                _uiState.update { it.copy(isRecordingMic = false, isTranscribingMic = true) }

                if (wavBytes.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isTranscribingMic = false,
                            micErrorMessage = "ไม่ได้รับสัญญาณเสียงจากไมโครโฟน กรุณาลองอีกครั้ง"
                        )
                    }
                    return@launch
                }

                val result = geminiRewriter.transcribeNavigationAudio(wavBytes)
                result.fold(
                    onSuccess = { transcript ->
                        _uiState.update {
                            it.copy(
                                isTranscribingMic = false,
                                simulatorInput = transcript,
                                snackbarMessage = "ถอดเสียงสำเร็จด้วย Gemini 3.5 Transcribe"
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isTranscribingMic = false,
                                micErrorMessage = err.localizedMessage ?: "ถอดเสียงไม่สำเร็จ"
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                micRecorder.stopRecording()
                _uiState.update { it.copy(isRecordingMic = false, isTranscribingMic = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Mic recording/transcription error", e)
                _uiState.update {
                    it.copy(
                        isRecordingMic = false,
                        isTranscribingMic = false,
                        micErrorMessage = e.localizedMessage ?: "ไม่สามารถบันทึกเสียงได้"
                    )
                }
            }
        }
    }

    fun runDeepProsodyAnalysis() {
        val state = _uiState.value
        val raw = state.simulatorInput.trim()
        if (raw.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(deepAnalysisState = DeepAnalysisState.Loading) }
            try {
                val rewrite = state.simulatorRewrite?.takeIf { it.rawText == raw }
                    ?: navigationRewriter.rewriteForNavigation(
                        rawText = raw,
                        settings = state.draftSettings,
                        isPreviewMode = true
                    ).also { r -> _uiState.update { it.copy(simulatorRewrite = r) } }

                val result = geminiRewriter.analyzeProsodyWithHighThinking(
                    rawText = rewrite.rawText,
                    rewrittenText = rewrite.spokenText,
                    ssml = rewrite.ssml,
                    voice = state.draftSettings.activeVoice,
                    style = state.draftSettings.style
                )
                result.fold(
                    onSuccess = { report ->
                        _uiState.update { it.copy(deepAnalysisState = DeepAnalysisState.Success(report)) }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                deepAnalysisState = DeepAnalysisState.Error(
                                    err.localizedMessage ?: "การวิเคราะห์เชิงลึกล้มเหลว"
                                )
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deep analysis failed", e)
                _uiState.update {
                    it.copy(deepAnalysisState = DeepAnalysisState.Error(e.localizedMessage ?: "เกิดข้อผิดพลาด"))
                }
            }
        }
    }

    fun updateCloudTtsKeyInput(value: String) {
        _uiState.update { it.copy(cloudTtsKeyInput = value) }
    }

    fun updateCustomGeminiKeyInput(value: String) {
        _uiState.update { it.copy(customGeminiKeyInput = value) }
    }

    fun saveApiKeys() {
        val state = _uiState.value
        securePrefs.saveApiKeys(state.cloudTtsKeyInput, state.customGeminiKeyInput)
        _uiState.update {
            it.copy(snackbarMessage = "บันทึกคีย์ลงใน EncryptedSharedPreferences เรียบร้อยแล้ว")
        }
    }

    fun testConnections() {
        val state = _uiState.value
        securePrefs.saveApiKeys(state.cloudTtsKeyInput, state.customGeminiKeyInput)
        viewModelScope.launch {
            _uiState.update { it.copy(connectionTestState = ConnectionTestState.Testing) }
            val geminiRes = geminiRewriter.testGeminiConnection(state.customGeminiKeyInput.trim())
            val cloudKey = state.cloudTtsKeyInput.trim()

            val messages = mutableListOf<String>()
            var allOk = true

            geminiRes.fold(
                onSuccess = { messages += "✓ $it" },
                onFailure = {
                    allOk = false
                    messages += "✗ Gemini AI: ${it.localizedMessage ?: "เชื่อมต่อไม่สำเร็จ"}"
                }
            )

            if (cloudKey.isNotBlank()) {
                val cloudRes = audioClient.testCloudTtsKey(cloudKey)
                cloudRes.fold(
                    onSuccess = { messages += "✓ $it" },
                    onFailure = {
                        allOk = false
                        messages += "✗ Cloud TTS: ${it.localizedMessage ?: "เชื่อมต่อไม่สำเร็จ"}"
                    }
                )
            } else {
                messages += "• Google Cloud TTS: ยังไม่ได้ใส่คีย์ (ใช้ Gemini 3.8 Flash TTS อัตโนมัติ)"
            }

            _uiState.update {
                it.copy(
                    connectionTestState = ConnectionTestState.Result(
                        success = allOk,
                        summary = messages.joinToString("\n")
                    )
                )
            }
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            phraseDao.clearAll()
            pcmDiskCache.clearAll()
            _uiState.update { it.copy(snackbarMessage = "ล้างแคชข้อความและเสียงทั้งหมดแล้ว") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        micRecorder.stopRecording()
    }
}
