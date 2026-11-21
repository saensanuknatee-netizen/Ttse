package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.CacheStats
import com.example.model.SimulatorPresets
import com.example.model.SynthesisBackend
import com.example.model.ThaiParticleMode
import com.example.ui.AudioPlayState
import com.example.ui.AuraNavUiState
import com.example.ui.ConnectionTestState
import com.example.ui.DeepAnalysisState
import com.example.ui.SavedPhrasesUiState
import com.example.ui.theme.AuraErrorContainer
import com.example.ui.theme.AuraSuccessContainer
import com.example.ui.theme.AuraSuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorSettingsScreen(
    uiState: AuraNavUiState,
    cacheStats: CacheStats,
    savedPhrasesState: SavedPhrasesUiState,
    userEmail: String?,
    userDisplayName: String?,
    onNavigateBack: () -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    onUpdateSimulatorInput: (String, String?) -> Unit,
    onRunSimulatorSpeak: (String?, String?) -> Unit,
    onStopAudio: () -> Unit,
    onToggleMicTranscribe: () -> Unit,
    onRunDeepAnalysis: () -> Unit,
    onSavePhraseToCloud: () -> Unit,
    onDeleteSavedPhrase: (String) -> Unit,
    onSelectParticleMode: (ThaiParticleMode) -> Unit,
    onSelectBackend: (SynthesisBackend) -> Unit,
    onUpdateCloudKey: (String) -> Unit,
    onUpdateGeminiKey: (String) -> Unit,
    onSaveApiKeys: () -> Unit,
    onTestConnections: () -> Unit,
    onClearCaches: () -> Unit,
    onSignOut: () -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val deniedMessage = stringResource(R.string.msg_mic_permission_denied)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMicTranscribe()
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar(deniedMessage) }
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onSnackbarConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_simulator_settings),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. One-tap System TTS / Google Maps Activation Card
                item(key = "system_tts_hero") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Navigation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "ใช้งานร่วมกับ Google Maps และทั้งระบบ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.subtitle_system_tts_settings),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Button(
                                onClick = onOpenSystemTtsSettings,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("open_system_tts_settings_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_open_system_tts_settings),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                // 2. Interactive Google Maps Test Console + Mic Transcribe + High-Thinking Deep Audit + Save to Cloud
                item(key = "simulator_console") {
                    SimulatorConsoleCard(
                        uiState = uiState,
                        onUpdateInput = onUpdateSimulatorInput,
                        onSimulate = onRunSimulatorSpeak,
                        onStop = onStopAudio,
                        onMicClick = {
                            if (uiState.isRecordingMic) {
                                onToggleMicTranscribe()
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    onToggleMicTranscribe()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onDeepAnalysis = onRunDeepAnalysis,
                        onSavePhraseToCloud = onSavePhraseToCloud
                    )
                }

                // 3. Cloud-Synced Saved Navigation Phrases (Firestore)
                item(key = "cloud_saved_phrases") {
                    SavedCloudPhrasesCard(
                        savedPhrasesState = savedPhrasesState,
                        onReplayPhrase = { raw, locale -> onRunSimulatorSpeak(raw, locale) },
                        onDeletePhrase = onDeleteSavedPhrase
                    )
                }

                // 4. Smart Thai Particle Mode
                item(key = "thai_particle_mode") {
                    ThaiParticleModeCard(
                        selectedMode = uiState.draftSettings.particleMode,
                        onSelectMode = onSelectParticleMode
                    )
                }

                // 5. Engine Backend & Encrypted API Key Setup + Connection Test
                item(key = "engine_api_keys") {
                    EngineAndApiKeyCard(
                        uiState = uiState,
                        onSelectBackend = onSelectBackend,
                        onUpdateCloudKey = onUpdateCloudKey,
                        onUpdateGeminiKey = onUpdateGeminiKey,
                        onSaveKeys = onSaveApiKeys,
                        onTestConnections = onTestConnections
                    )
                }

                // 6. Dual-Layer Zero-Latency Cache Monitor
                item(key = "cache_monitor") {
                    DualLayerCacheCard(
                        cacheStats = cacheStats,
                        onClearCaches = onClearCaches
                    )
                }

                // 7. Google Account & Cloud Firestore Sync Status
                item(key = "account_cloud_sync") {
                    CloudAccountCard(
                        userDisplayName = userDisplayName,
                        userEmail = userEmail,
                        isCloudSynced = uiState.isCloudSynced,
                        cloudSyncError = uiState.cloudSyncError,
                        onSignOut = onSignOut
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SimulatorConsoleCard(
    uiState: AuraNavUiState,
    onUpdateInput: (String, String?) -> Unit,
    onSimulate: (String?, String?) -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    onDeepAnalysis: () -> Unit,
    onSavePhraseToCloud: () -> Unit
) {
    val isBusy = uiState.simulatorPlayState is AudioPlayState.Rewriting ||
        uiState.simulatorPlayState is AudioPlayState.Synthesizing
    val isPlaying = uiState.simulatorPlayState is AudioPlayState.Playing

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.section_simulator),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SimulatorPresets.all, key = { it.title }) { preset ->
                    FilterChip(
                        selected = uiState.simulatorInput == preset.rawText,
                        onClick = { onSimulate(preset.rawText, preset.localeTag) },
                        label = { Text(preset.title, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.simulatorInput,
                    onValueChange = { onUpdateInput(it, null) },
                    placeholder = { Text(stringResource(R.string.hint_custom_phrase)) },
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulator_phrase_input")
                )

                Surface(
                    shape = CircleShape,
                    color = if (uiState.isRecordingMic) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMicClick)
                        .testTag("mic_transcribe_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (uiState.isTranscribingMic) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.isRecordingMic) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.cd_mic_transcribe),
                                tint = if (uiState.isRecordingMic) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.isRecordingMic) {
                Text(
                    text = "กำลังบันทึกเสียงพูด… กดปุ่มหยุดเพื่อถอดข้อความด้วย Gemini 3.5 Transcribe",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (uiState.isTranscribingMic) {
                Text(
                    text = "กำลังถอดเสียงด้วย Gemini 3.5 Transcribe…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            uiState.micErrorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { if (isPlaying || isBusy) onStop() else onSimulate(null, null) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("simulate_speak_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("กำลังประมวลผล…", style = MaterialTheme.typography.labelMedium)
                    } else if (isPlaying) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_stop_voice), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_simulate_speak), style = MaterialTheme.typography.labelMedium)
                    }
                }

                FilledTonalButton(
                    onClick = onDeepAnalysis,
                    enabled = uiState.deepAnalysisState !is DeepAnalysisState.Loading,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("deep_thinking_audit_button")
                ) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("วิเคราะห์เชิงลึก", style = MaterialTheme.typography.labelMedium)
                }
            }

            OutlinedButton(
                onClick = onSavePhraseToCloud,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_phrase_cloud_button")
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("บันทึกประโยคนี้ลงคลาวด์ (Cloud Firestore)", style = MaterialTheme.typography.labelMedium)
            }

            if (uiState.simulatorPlayState is AudioPlayState.Error) {
                Text(
                    text = uiState.simulatorPlayState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.simulatorRewrite?.let { res ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AuraSuccessContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = AuraSuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${res.routePath.thaiBadge} • ${res.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AuraSuccessGreen
                                    )
                                }
                            }
                            if (uiState.simulatorProviderLabel.isNotBlank()) {
                                Text(
                                    text = "${uiState.simulatorProviderLabel} (${uiState.simulatorAudioLatencyMs ?: 0}ms)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "คำพูดที่ AI ปรับใหม่ (${uiState.draftSettings.activeVoice.displayName}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = res.spokenText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "SSML Output:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = res.ssml,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp)
                        )
                    }
                }
            }

            when (val analysis = uiState.deepAnalysisState) {
                is DeepAnalysisState.Idle -> Unit
                is DeepAnalysisState.Loading -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Gemini 4 Carbon (ThinkingLevel.HIGH) กำลังวิเคราะห์สัทศาสตร์และความปลอดภัย…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                is DeepAnalysisState.Success -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ผลการวิเคราะห์เชิงลึก (Gemini 4 Carbon • High Thinking)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = analysis.report,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is DeepAnalysisState.Error -> {
                    Text(
                        text = analysis.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedCloudPhrasesCard(
    savedPhrasesState: SavedPhrasesUiState,
    onReplayPhrase: (String, String) -> Unit,
    onDeletePhrase: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "คลังประโยคนำทางบนคลาวด์ (Cloud Firestore)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            when (savedPhrasesState) {
                is SavedPhrasesUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("กำลังซิงค์คลังประโยค…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is SavedPhrasesUiState.Error -> {
                    Text(
                        text = savedPhrasesState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is SavedPhrasesUiState.Success -> {
                    if (savedPhrasesState.phrases.isEmpty()) {
                        Text(
                            text = "ยังไม่มีประโยคที่บันทึก กด \"บันทึกประโยคนี้ลงคลาวด์\" ด้านบนเพื่อซิงค์ข้ามอุปกรณ์",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        savedPhrasesState.phrases.forEach { phrase ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onReplayPhrase(phrase.rawText, phrase.localeTag) }
                                    .testTag("saved_cloud_phrase_${phrase.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = phrase.rawText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (phrase.rewrittenText.isNotBlank()) {
                                            Text(
                                                text = "AI: ${phrase.rewrittenText}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeletePhrase(phrase.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "ลบประโยคที่บันทึก",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThaiParticleModeCard(
    selectedMode: ThaiParticleMode,
    onSelectMode: (ThaiParticleMode) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.section_thai_particles),
                style = MaterialTheme.typography.titleMedium
            )
            ThaiParticleMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("particle_mode_${mode.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == selectedMode,
                        onClick = { onSelectMode(mode) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.thaiLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineAndApiKeyCard(
    uiState: AuraNavUiState,
    onSelectBackend: (SynthesisBackend) -> Unit,
    onUpdateCloudKey: (String) -> Unit,
    onUpdateGeminiKey: (String) -> Unit,
    onSaveKeys: () -> Unit,
    onTestConnections: () -> Unit
) {
    var showKeys by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.section_engine_api),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = AuraSuccessContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = AuraSuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Firebase AI Logic พร้อมใช้งานทันทีโดยไม่ต้องกรอก Gemini Key (กรอก Cloud TTS Key ด้านล่างเพื่อเปิดใช้เสียง Chirp 3: HD & Neural2)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AuraSuccessGreen
                    )
                }
            }

            SynthesisBackend.entries.forEach { backend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectBackend(backend) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = backend == uiState.draftSettings.synthesisBackend,
                        onClick = { onSelectBackend(backend) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(backend.thaiLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(backend.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation()

            OutlinedTextField(
                value = uiState.cloudTtsKeyInput,
                onValueChange = onUpdateCloudKey,
                label = { Text(stringResource(R.string.label_cloud_tts_key)) },
                singleLine = true,
                visualTransformation = visualTransformation,
                trailingIcon = {
                    IconButton(onClick = { showKeys = !showKeys }) {
                        Icon(
                            imageVector = if (showKeys) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_key_visibility)
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_tts_api_key_input")
            )

            OutlinedTextField(
                value = uiState.customGeminiKeyInput,
                onValueChange = onUpdateGeminiKey,
                label = { Text(stringResource(R.string.label_gemini_key_optional)) },
                singleLine = true,
                visualTransformation = visualTransformation,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_gemini_api_key_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveKeys,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("save_api_keys_button")
                ) {
                    Text(stringResource(R.string.btn_save_keys), style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onTestConnections,
                    enabled = uiState.connectionTestState !is ConnectionTestState.Testing,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("test_connection_button")
                ) {
                    if (uiState.connectionTestState is ConnectionTestState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.btn_test_connection), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (uiState.connectionTestState is ConnectionTestState.Result) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (uiState.connectionTestState.success) AuraSuccessContainer else AuraErrorContainer
                ) {
                    Text(
                        text = uiState.connectionTestState.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.connectionTestState.success) AuraSuccessGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DualLayerCacheCard(
    cacheStats: CacheStats,
    onClearCaches: () -> Unit
) {
    val sizeKb = cacheStats.audioCacheBytes / 1024.0
    val sizeLabel = if (sizeKb >= 1024.0) "%.2f MB".format(sizeKb / 1024.0) else "%.1f KB".format(sizeKb)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.section_cache_status),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Layer 1: AI Phrase (Room)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${cacheStats.phraseCacheCount} ประโยค", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("0ms สำหรับเส้นทางเดิม", style = MaterialTheme.typography.labelSmall, color = AuraSuccessGreen)
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Layer 2: Audio PCM (LRU)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${cacheStats.audioClipCount} คลิป ($sizeLabel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("< 20ms เล่นเสียงทันที", style = MaterialTheme.typography.labelSmall, color = AuraSuccessGreen)
                    }
                }
            }

            OutlinedButton(
                onClick = onClearCaches,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("clear_cache_button")
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_clear_cache), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CloudAccountCard(
    userDisplayName: String?,
    userEmail: String?,
    isCloudSynced: Boolean,
    cloudSyncError: String?,
    onSignOut: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userDisplayName?.takeIf { it.isNotBlank() } ?: "บัญชี Google ของคุณ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userEmail ?: "เชื่อมต่อ Firebase Authentication แล้ว",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AuraSuccessContainer
                ) {
                    Text(
                        text = if (isCloudSynced) "ซิงค์แล้ว" else "ออนไลน์",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraSuccessGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            cloudSyncError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ออกจากระบบ (Sign Out)", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
