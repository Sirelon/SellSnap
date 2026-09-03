package com.sirelon.sellsnap.features.seller.ad.generation_log

data class AdGenerationAttempt(
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
)

/**
 * Logs one document per generated-description attempt (never overwritten), chained via
 * [AdGenerationAttempt.previousAttemptId], so regenerations stay visible as history instead of
 * replacing each other — this is the raw case data prompt tuning needs, which aggregate
 * analytics events can't provide.
 *
 * Implementations must swallow their own failures: this is diagnostic logging and must never
 * break the ad-creation flow.
 */
interface AdGenerationLogRepository {
    /** Returns the new document id, or null if the write failed. */
    suspend fun logAttempt(attempt: AdGenerationAttempt): String?
    suspend fun updateVote(attemptId: String, vote: String?)
    suspend fun markPublished(attemptId: String, publishedAdId: String, olxAccountId: Long?)
}

object NoOpAdGenerationLogRepository : AdGenerationLogRepository {
    override suspend fun logAttempt(attempt: AdGenerationAttempt): String? = null
    override suspend fun updateVote(attemptId: String, vote: String?) = Unit
    override suspend fun markPublished(attemptId: String, publishedAdId: String, olxAccountId: Long?) = Unit
}
