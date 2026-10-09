package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.threads.ContainerRequest
import com.sirelon.sellsnap.threads.ContainerState
import com.sirelon.sellsnap.threads.ContainerStatus
import com.sirelon.sellsnap.threads.IssuedToken
import com.sirelon.sellsnap.threads.Me
import com.sirelon.sellsnap.threads.PublishingLimit
import com.sirelon.sellsnap.threads.ThreadSummary
import com.sirelon.sellsnap.threads.ThreadsApi
import com.sirelon.sellsnap.threads.TokenInfo

/**
 * Scriptable [ThreadsApi]. Each call is recorded with the posts in [store] at that moment, so a
 * test can assert what was persisted before the call. [failNext] queues failures per method name.
 */
class FakeThreadsApi(private val store: InMemorySocialStore? = null) : ThreadsApi {
    class Call(val method: String, val args: List<Any?>, val posts: Map<String, SocialPost>)

    val calls = mutableListOf<Call>()
    val published = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    var tokenInfo = TokenInfo(
        isValid = true,
        scopes = setOf("threads_basic", "threads_content_publish", "threads_delete"),
        userId = "threads-user",
        expiresAt = null,
        dataAccessExpiresAt = null,
    )
    var me = Me(id = "threads-user", username = "sellsnap.app")
    var limit = PublishingLimit(quotaUsage = 0, quotaTotal = 250)
    var recent = listOf<ThreadSummary>()

    /** Counts handed out by [mediaInsights], per media id; a media id without an entry gets [defaultInsights]. */
    val insights = mutableMapOf<String, Map<String, Long>>()
    var defaultInsights = mapOf("views" to 100L, "likes" to 10L, "replies" to 2L, "reposts" to 1L, "quotes" to 0L)
    var followers = 1_234L

    /** Returns a failure to throw, or null; lets a test fail only the call that asks for `views`. */
    var insightsFailure: (mediaId: String, metrics: List<String>) -> Throwable? = { _, _ -> null }

    /** Statuses handed out in order; once empty every container is `Finished`. */
    val containerStatuses = ArrayDeque<ContainerStatus>()

    private val failures = mutableMapOf<String, ArrayDeque<Throwable>>()
    private var nextId = 1

    fun failNext(method: String, vararg failure: Throwable) {
        failures.getOrPut(method) { ArrayDeque() }.addAll(failure)
    }

    fun callsTo(method: String): List<Call> = calls.filter { it.method == method }

    private fun record(method: String, vararg args: Any?) {
        calls += Call(method, args.toList(), store?.allPosts.orEmpty())
        failures[method]?.removeFirstOrNull()?.let { throw it }
    }

    override suspend fun debugToken(inputToken: String): TokenInfo {
        record("debugToken", inputToken)
        return tokenInfo
    }

    override suspend fun exchangeToken(shortLivedToken: String): IssuedToken {
        record("exchangeToken", shortLivedToken)
        return IssuedToken("long-lived-token", LONG_LIVED_SECONDS)
    }

    override suspend fun refreshToken(longLivedToken: String): IssuedToken {
        record("refreshToken", longLivedToken)
        return IssuedToken("refreshed-token", LONG_LIVED_SECONDS)
    }

    override suspend fun me(token: String): Me {
        record("me", token)
        return me
    }

    override suspend fun createContainer(userId: String, token: String, request: ContainerRequest): String {
        record("createContainer", userId, token, request)
        return "container-${nextId++}"
    }

    override suspend fun containerStatus(containerId: String, token: String): ContainerStatus {
        record("containerStatus", containerId, token)
        return containerStatuses.removeFirstOrNull() ?: ContainerStatus(ContainerState.Finished)
    }

    override suspend fun publish(userId: String, token: String, containerId: String): String {
        record("publish", userId, token, containerId)
        return "media-${nextId++}".also { published += it }
    }

    override suspend fun publishingLimit(userId: String, token: String): PublishingLimit {
        record("publishingLimit", userId, token)
        return limit
    }

    override suspend fun delete(mediaId: String, token: String) {
        record("delete", mediaId, token)
        deleted += mediaId
    }

    override suspend fun recentThreads(userId: String, token: String, limit: Int): List<ThreadSummary> {
        record("recentThreads", userId, token, limit)
        return recent
    }

    override suspend fun mediaInsights(mediaId: String, token: String, metrics: List<String>): Map<String, Long> {
        record("mediaInsights", mediaId, token, metrics)
        insightsFailure(mediaId, metrics)?.let { throw it }
        return (insights[mediaId] ?: defaultInsights).filterKeys { it in metrics }
    }

    override suspend fun followersCount(userId: String, token: String): Long {
        record("followersCount", userId, token)
        return followers
    }

    private companion object {
        const val LONG_LIVED_SECONDS = 60L * 24 * 3600
    }
}
