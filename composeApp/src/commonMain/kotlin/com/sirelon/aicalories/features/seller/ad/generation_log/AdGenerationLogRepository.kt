package com.sirelon.sellsnap.features.seller.ad.generation_log

data class AdGenerationAttempt(
    val sessionId: String,
    val attemptNumber: Int,
    val previousAttemptId: String?,
    val countryCode: String,
    val modelId: String,
    val promptVersion: String,
    val imagePaths: List<String>,
    val sellerPrompt: String,
    val title: String,
    val description: String,
    val suggestedPrice: Float,
    val minPrice: Float,
    val maxPrice: Float,
    /**
     * Reason code when the model declined to write a listing for these photos. Set on a refusal,
     * where the listing fields are empty; null on every attempt that produced copy.
     */
    val unrecognized: String? = null,
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

    /**
     * Records the OLX advert this attempt became.
     *
     * [publishedAdUrl] is the advert URL as OLX returned it at publish time. `url` is a required
     * property of the `Advert` schema and `POST adverts` answers with the full advert model
     * (developer.olx.ua, partner_api.yaml - `Advert` schema and the "Posting Advert" section), so
     * it arrives at `new` and `limited` too, not only once the advert is `active`.
     *
     * Inferred: moderation moves `status` and leaves `url` alone, so the URL survives the
     * moderation step. A title edit is what can outdate it, since OLX advert URLs carry a title
     * slug - which is why [publishedAdId] stays the durable key and `GET adverts/{id}` is the way
     * to the current URL.
     */
    suspend fun markPublished(
        attemptId: String,
        publishedAdId: String,
        publishedAdUrl: String?,
        olxAccountId: Long?,
    )
}

object NoOpAdGenerationLogRepository : AdGenerationLogRepository {
    override suspend fun logAttempt(attempt: AdGenerationAttempt): String? = null
    override suspend fun updateVote(attemptId: String, vote: String?) = Unit
    override suspend fun markPublished(
        attemptId: String,
        publishedAdId: String,
        publishedAdUrl: String?,
        olxAccountId: Long?,
    ) = Unit
}
