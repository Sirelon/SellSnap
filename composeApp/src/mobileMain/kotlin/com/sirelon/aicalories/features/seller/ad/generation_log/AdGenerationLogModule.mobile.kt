package com.sirelon.sellsnap.features.seller.ad.generation_log

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.fromDuration
import dev.gitlive.firebase.firestore.toDuration
import dev.gitlive.firebase.installations.installations
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import org.koin.core.module.Module
import org.koin.dsl.module

private const val COLLECTION = "ad_generations"
private const val ATTEMPTS_SUBCOLLECTION = "attempts"

// Console-side TTL policy on `expireAt` deletes the doc after this window — these records hold
// user photos and generated copy, not something to keep indefinitely.
private val RETENTION = 180.days

@Serializable
private data class AdGenerationAttemptDocument(
    val sessionId: String,
    val attemptNumber: Int,
    val previousAttemptId: String?,
    val countryCode: String,
    val modelId: String,
    val promptVersion: String,
    val imagePaths: List<String>,
    val title: String,
    val description: String,
    val suggestedPrice: Float,
    val minPrice: Float,
    val maxPrice: Float,
    val vote: String? = null,
    val voteUpdatedAt: Timestamp? = null,
    val didPublish: Boolean = false,
    val publishedAdId: String? = null,
    val olxAccountId: Long? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val expireAt: Timestamp = Timestamp.fromDuration(Timestamp.now().toDuration() + RETENTION),
)

internal class FirebaseAdGenerationLogRepository : AdGenerationLogRepository {

    // Nested under the Firebase installation ID (not an OLX account) so guest-mode attempts —
    // which never have an OLX account — are grouped the same way authenticated ones are.
    private suspend fun attemptsCollection() = Firebase.firestore.collection(COLLECTION)
        .document(Firebase.installations.getId())
        .collection(ATTEMPTS_SUBCOLLECTION)

    override suspend fun logAttempt(attempt: AdGenerationAttempt): String? = runCatching {
        val docId = Uuid.random().toString()
        val document = AdGenerationAttemptDocument(
            sessionId = attempt.sessionId,
            attemptNumber = attempt.attemptNumber,
            previousAttemptId = attempt.previousAttemptId,
            countryCode = attempt.countryCode,
            modelId = attempt.modelId,
            promptVersion = attempt.promptVersion,
            imagePaths = attempt.imagePaths,
            title = attempt.title,
            description = attempt.description,
            suggestedPrice = attempt.suggestedPrice,
            minPrice = attempt.minPrice,
            maxPrice = attempt.maxPrice,
        )
        attemptsCollection().document(docId)
            .set(AdGenerationAttemptDocument.serializer(), document) { encodeDefaults = true }
        docId
    }.getOrNull()

    override suspend fun updateVote(attemptId: String, vote: String?) {
        runCatching {
            attemptsCollection().document(attemptId).updateFields {
                "vote" to vote
                "voteUpdatedAt" to Timestamp.now()
            }
        }
    }

    override suspend fun markPublished(attemptId: String, publishedAdId: String, olxAccountId: Long?) {
        runCatching {
            attemptsCollection().document(attemptId).updateFields {
                "didPublish" to true
                "publishedAdId" to publishedAdId
                "olxAccountId" to olxAccountId
            }
        }
    }
}

actual val adGenerationLogModule: Module = module {
    single<AdGenerationLogRepository> { FirebaseAdGenerationLogRepository() }
}
