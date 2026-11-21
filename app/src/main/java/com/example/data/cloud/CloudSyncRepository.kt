package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.model.TtsUserSettings
import com.example.model.VoiceCatalog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val SETTINGS_DOC_ID = "tts_profile"

/**
 * Authenticated Firestore repository syncing AuraNav TTS preferences and saved navigation phrases.
 * Always connects to the provisioned named database via R.string.firestore_database_id.
 */
class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun observeCloudSettings(): Flow<CloudTtsSettingsDoc?> = flow {
        val uid = requireUserId()
        val path = "users/$uid/settings/$SETTINGS_DOC_ID"
        emitAll(
            db.collection("users").document(uid)
                .collection("settings").document(SETTINGS_DOC_ID)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        CloudTtsSettingsDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeSavedPhrases(): Flow<List<SavedNavigationPhraseDoc>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/customPhrases"
        emitAll(
            db.collection("users").document(uid)
                .collection("customPhrases")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedNavigationPhraseDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTtsSettings(settings: TtsUserSettings): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/settings/$SETTINGS_DOC_ID"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("settings").document(SETTINGS_DOC_ID)
            docPath = docRef.path

            val safeVoiceId = VoiceCatalog.byId(settings.voiceId).id
            val safePitch = settings.pitchSemitones.coerceIn(-3.0f, 3.0f).toDouble()
            val safeExpr = settings.expressiveness.coerceIn(0.0f, 1.0f).toDouble()
            val safeRate = settings.speakingRate.coerceIn(0.75f, 1.35f).toDouble()

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mapOf(
                    "voiceId" to safeVoiceId,
                    "styleId" to settings.style.id,
                    "aiModeId" to settings.aiMode.id,
                    "particleModeId" to settings.particleMode.id,
                    "pitchSemitones" to safePitch,
                    "expressiveness" to safeExpr,
                    "speakingRate" to safeRate,
                    "synthesisBackendId" to settings.synthesisBackend.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "voiceId" to safeVoiceId,
                    "styleId" to settings.style.id,
                    "aiModeId" to settings.aiMode.id,
                    "particleModeId" to settings.particleMode.id,
                    "pitchSemitones" to safePitch,
                    "expressiveness" to safeExpr,
                    "speakingRate" to safeRate,
                    "synthesisBackendId" to settings.synthesisBackend.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomPhrase(
        title: String,
        rawText: String,
        rewrittenText: String?,
        voiceId: String,
        localeTag: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/customPhrases"
        return try {
            val uid = requireUserId()
            val trimmedTitle = title.trim().take(80)
            val trimmedRaw = rawText.trim().take(500)
            require(trimmedTitle.isNotEmpty()) { "กรุณาระบุชื่อประโยค" }
            require(trimmedRaw.isNotEmpty()) { "กรุณาระบุข้อความนำทาง" }

            val safeLocale = when (localeTag) {
                "th-TH", "en-US", "en-GB", "th-TH-mixed" -> localeTag
                else -> "th-TH"
            }

            val docRef = db.collection("users").document(uid)
                .collection("customPhrases").document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "title" to trimmedTitle,
                "rawText" to trimmedRaw,
                "rewrittenText" to rewrittenText?.trim()?.take(700)?.takeIf { it.isNotEmpty() },
                "voiceId" to VoiceCatalog.byId(voiceId).id,
                "localeTag" to safeLocale,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomPhrase(phraseId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/customPhrases/$phraseId"
        return try {
            val uid = requireUserId()
            require(phraseId.isNotBlank()) { "Phrase ID must not be blank" }
            val docRef = db.collection("users").document(uid)
                .collection("customPhrases").document(phraseId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
