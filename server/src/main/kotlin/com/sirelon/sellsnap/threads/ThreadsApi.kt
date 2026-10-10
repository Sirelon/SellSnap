package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.model.PostKind
import java.time.Instant

/**
 * Threads Graph API port. Every method throws [ThreadsFailure] on failure, with a fixed message
 * that never contains a token or secret.
 */
interface ThreadsApi {
    suspend fun debugToken(inputToken: String): TokenInfo

    /** Exchanges a short-lived token for a long-lived one. */
    suspend fun exchangeToken(shortLivedToken: String): IssuedToken

    /** Refreshes a long-lived token that is at least 24 hours old. */
    suspend fun refreshToken(longLivedToken: String): IssuedToken

    suspend fun me(token: String): Me

    /** Creates a media container and returns its id. */
    suspend fun createContainer(userId: String, token: String, request: ContainerRequest): String

    suspend fun containerStatus(containerId: String, token: String): ContainerStatus

    /** Publishes a finished container and returns the media id. */
    suspend fun publish(userId: String, token: String, containerId: String): String

    suspend fun publishingLimit(userId: String, token: String): PublishingLimit

    suspend fun delete(mediaId: String, token: String)

    /** The account's most recent posts, newest first; used to recover a lost media id. */
    suspend fun recentThreads(userId: String, token: String, limit: Int): List<ThreadSummary>

    /**
     * Lifetime counts for [metrics] of one post, keyed by metric name. A metric the response does
     * not carry is absent from the map. Needs the `threads_manage_insights` permission.
     */
    suspend fun mediaInsights(mediaId: String, token: String, metrics: List<String>): Map<String, Long>

    /** The account's current follower count; the API has no history, so only a daily snapshot keeps one. */
    suspend fun followersCount(userId: String, token: String): Long
}

class TokenInfo(
    val isValid: Boolean,
    val scopes: Set<String>,
    val userId: String?,
    val expiresAt: Instant?,
    /** End of the private-profile permission grant; null when the token has none. */
    val dataAccessExpiresAt: Instant?,
    /** When the token was issued; a long-lived token younger than 24 hours cannot be refreshed yet. */
    val issuedAt: Instant? = null,
)

class IssuedToken(val accessToken: String, val expiresInSeconds: Long)

class Me(val id: String, val username: String)

/**
 * [imageUrl] applies to images, [pollOptions] to polls; polls are text-only posts. [replyToId] makes
 * the container a reply under that media id; a reply is always a text post.
 */
class ContainerRequest(
    val kind: PostKind,
    val text: String,
    val imageUrl: String? = null,
    val pollOptions: List<String> = emptyList(),
    val replyToId: String? = null,
)

enum class ContainerState { InProgress, Finished, Error, Expired, Published }

class ContainerStatus(val state: ContainerState, val errorMessage: String? = null)

class PublishingLimit(val quotaUsage: Int, val quotaTotal: Int) {
    val remaining: Int get() = (quotaTotal - quotaUsage).coerceAtLeast(0)
}

class ThreadSummary(val id: String, val text: String?, val publishedAt: Instant?)

/** How the caller must react to a failed call. */
sealed class ThreadsFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Network error, 5xx or `is_transient`: retry with backoff. */
    class Transient(message: String, cause: Throwable? = null) : ThreadsFailure(message, cause)

    /** Rate limit (codes 4, 17, 32, 613): do not retry now, try again on a later tick, no pause. */
    class RateLimited(message: String) : ThreadsFailure(message)

    /** Token error (190), policy block or any other 4xx: stop, pause the account and alert. */
    class Permanent(val code: Int?, message: String) : ThreadsFailure(message)
}
