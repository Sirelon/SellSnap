package com.sirelon.sellsnap.features.seller.ad.generation_log

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.fromDuration
import dev.gitlive.firebase.firestore.toDuration
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import org.koin.core.module.Module
import org.koin.dsl.module

private const val COLLECTION = "ad_generations"

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
    val createdAt: Timestamp = Timestamp.now(),
    val expireAt: Timestamp = Timestamp.fromDuration(Timestamp.now().toDuration() + RETENTION),
)

internal class FirebaseAdGenerationLogRepository : AdGenerationLogRepository {

    private val collection get() = Firebase.firestore.collection(COLLECTION)

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
        collection.document(docId)
            .set(AdGenerationAttemptDocument.serializer(), document) { encodeDefaults = true }
        docId
    }.getOrNull()

    override suspend fun updateVote(attemptId: String, vote: String?) {
        runCatching {
            collection.document(attemptId).updateFields {
                "vote" to vote
                "voteUpdatedAt" to Timestamp.now()
            }
        }
    }

    override suspend fun markPublished(attemptId: String, publishedAdId: String) {
        runCatching {
            collection.document(attemptId).updateFields {
                "didPublish" to true
                "publishedAdId" to publishedAdId
            }
        }
    }
}

actual val adGenerationLogModule: Module = module {
    single<AdGenerationLogRepository> { FirebaseAdGenerationLogRepository() }
}
