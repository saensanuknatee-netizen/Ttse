package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.ui.AuraNavViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.SimulatorSettingsScreen
import com.example.ui.screens.VoiceSelectorScreen
import com.example.ui.theme.AuraNavTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

const val ROUTE_VOICE_SELECTOR = "voice_selector"
const val ROUTE_SIMULATOR_SETTINGS = "simulator_settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        // STEP 3: Render Auth-Gated Material 3 Compose UI
        setContent {
            AuraNavTheme {
                AppContent(
                    onOpenSystemTtsSettings = { openSystemTtsSettings(this) }
                )
            }
        }
    }

    private fun openSystemTtsSettings(context: Context) {
        val ttsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(ttsIntent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    onOpenSystemTtsSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val signedInUser = currentUser
    if (signedInUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = signedInUser.uid
        // Key by uid so ViewModel and Firestore snapshot listeners are cleanly scoped per user
        key(uid) {
            AuraNavApp(
                currentUserId = uid,
                userEmail = signedInUser.email,
                userDisplayName = signedInUser.displayName,
                onOpenSystemTtsSettings = onOpenSystemTtsSettings,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun AuraNavApp(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
    onOpenSystemTtsSettings: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AuraNavViewModel = viewModel(key = currentUserId)
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val savedPhrasesState by viewModel.savedPhrasesState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_VOICE_SELECTOR
    ) {
        composable(ROUTE_VOICE_SELECTOR) {
            VoiceSelectorScreen(
                uiState = uiState,
                cacheStats = cacheStats,
                onSelectVoice = viewModel::selectVoice,
                onSelectStyle = viewModel::updateSpeakingStyle,
                onSelectAiMode = viewModel::updateAiRewriteMode,
                onPitchChange = viewModel::updatePitch,
                onExpressivenessChange = viewModel::updateExpressiveness,
                onSpeedChange = viewModel::updateSpeakingRate,
                onSelectPreviewLanguage = viewModel::selectPreviewLanguage,
                onPlayPreview = viewModel::playMainScreenPreview,
                onCancel = viewModel::cancelDraftChanges,
                onConfirm = viewModel::confirmSettings,
                onOpenSimulatorSettings = { navController.navigate(ROUTE_SIMULATOR_SETTINGS) },
                onOpenSystemTtsSettings = onOpenSystemTtsSettings,
                onSnackbarConsumed = viewModel::consumeSnackbar
            )
        }

        composable(ROUTE_SIMULATOR_SETTINGS) {
            SimulatorSettingsScreen(
                uiState = uiState,
                cacheStats = cacheStats,
                savedPhrasesState = savedPhrasesState,
                userEmail = userEmail,
                userDisplayName = userDisplayName,
                onNavigateBack = { navController.popBackStack() },
                onOpenSystemTtsSettings = onOpenSystemTtsSettings,
                onUpdateSimulatorInput = viewModel::updateSimulatorInput,
                onRunSimulatorSpeak = viewModel::runSimulatorSpeak,
                onStopAudio = viewModel::stopAudioPlayback,
                onToggleMicTranscribe = viewModel::toggleMicrophoneTranscription,
                onRunDeepAnalysis = viewModel::runDeepProsodyAnalysis,
                onSavePhraseToCloud = viewModel::saveCurrentSimulatorPhraseToCloud,
                onDeleteSavedPhrase = viewModel::deleteSavedCloudPhrase,
                onSelectParticleMode = viewModel::updateThaiParticleMode,
                onSelectBackend = viewModel::updateSynthesisBackend,
                onUpdateCloudKey = viewModel::updateCloudTtsKeyInput,
                onUpdateGeminiKey = viewModel::updateCustomGeminiKeyInput,
                onSaveApiKeys = viewModel::saveApiKeys,
                onTestConnections = viewModel::testConnections,
                onClearCaches = viewModel::clearAllCaches,
                onSignOut = {
                    viewModel.stopAudioPlayback()
                    onSignOut()
                },
                onSnackbarConsumed = viewModel::consumeSnackbar
            )
        }
    }
}
