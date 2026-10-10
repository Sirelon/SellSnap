package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialClick
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.model.UsageCounters
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.store.StoreTx
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * In-memory [SocialStore] with Firestore's transaction rules: a transaction reads the committed
 * state, buffers its writes, commits them all or none, and rejects a read after a write.
 * The mutex serialises transactions, so a racing second one sees the first one's result.
 */
class InMemorySocialStore : SocialStore {
    private val mutex = Mutex()

    @Volatile
    private var accountMap = mapOf<String, SocialAccount>()

    @Volatile
    private var postMap = mapOf<String, SocialPost>()

    @Volatile
    private var eventMap = mapOf<String, SocialEvent>()

    @Volatile
    private var updateMap = mapOf<Long, Instant>()

    @Volatile
    private var clickList = listOf<SocialClick>()

    @Volatile
    private var metricsMap = mapOf<String, PostMetrics>()

    @Volatile
    private var accountMetricsMap = mapOf<String, AccountMetrics>()

    @Volatile
    private var usageMap = mapOf<String, Map<String, UsageCounters>>()

    val allUsage: Map<String, Map<String, UsageCounters>> get() = usageMap
    val allMetrics: Map<String, PostMetrics> get() = metricsMap
    val allAccountMetrics: Map<String, AccountMetrics> get() = accountMetricsMap
    val allAccounts: Map<String, SocialAccount> get() = accountMap
    val allPosts: Map<String, SocialPost> get() = postMap
    val events: Map<String, SocialEvent> get() = eventMap
    val updates: Map<Long, Instant> get() = updateMap
    val clicks: List<SocialClick> get() = clickList

    /** Test setup: writes outside any transaction. */
    fun put(account: SocialAccount) {
        accountMap = accountMap + (account.id to account)
    }

    /** Test setup: writes outside any transaction. */
    fun put(post: SocialPost) {
        postMap = postMap + (post.id to post)
    }

    override suspend fun account(id: String): SocialAccount? = accountMap[id]

    override suspend fun accounts(): List<SocialAccount> = accountMap.values.toList()

    override suspend fun post(id: String): SocialPost? = postMap[id]

    override suspend fun postsOf(accountId: String): List<SocialPost> =
        postMap.values.filter { it.accountId == accountId }

    override suspend fun postsWithStatus(status: PostStatus): List<SocialPost> =
        postMap.values.filter { it.status == status }

    override suspend fun <T> transaction(block: (StoreTx) -> T): T = mutex.withLock {
        val tx = MemoryTx(accountMap, postMap)
        val result = block(tx)
        accountMap = accountMap + tx.accountWrites
        postMap = postMap + tx.postWrites
        eventMap = eventMap + tx.eventWrites
        result
    }

    override suspend fun claimUpdate(updateId: Long, expireAt: Instant): Boolean = mutex.withLock {
        if (updateId in updateMap) {
            false
        } else {
            updateMap = updateMap + (updateId to expireAt)
            true
        }
    }

    override suspend fun recordEvent(event: SocialEvent): Boolean = mutex.withLock {
        if (event.id in eventMap) {
            false
        } else {
            eventMap = eventMap + (event.id to event)
            true
        }
    }

    override suspend fun logClick(click: SocialClick) = mutex.withLock {
        clickList = clickList + click
    }

    override suspend fun saveMetrics(metrics: PostMetrics) = mutex.withLock {
        metricsMap = metricsMap + (metrics.id to metrics)
    }

    override suspend fun metricsOf(accountId: String, day: String): List<PostMetrics> =
        metricsMap.values.filter { it.accountId == accountId && it.day == day }

    override suspend fun accountMetrics(accountId: String, day: String): AccountMetrics? =
        accountMetricsMap["${accountId}_$day"]

    override suspend fun claimAccountDay(metrics: AccountMetrics): Boolean = mutex.withLock {
        if (metrics.id in accountMetricsMap) {
            false
        } else {
            accountMetricsMap = accountMetricsMap + (metrics.id to metrics)
            true
        }
    }

    override suspend fun saveAccountMetrics(metrics: AccountMetrics) = mutex.withLock {
        accountMetricsMap = accountMetricsMap + (metrics.id to metrics)
    }

    override suspend fun addUsage(day: String, model: String, delta: UsageCounters) = mutex.withLock {
        val models = usageMap[day].orEmpty()
        val current = models[model] ?: UsageCounters()
        val updated = UsageCounters(
            calls = current.calls + delta.calls,
            promptTokens = current.promptTokens + delta.promptTokens,
            outputTokens = current.outputTokens + delta.outputTokens,
            thoughtsTokens = current.thoughtsTokens + delta.thoughtsTokens,
        )
        usageMap = usageMap + (day to (models + (model to updated)))
    }

    override suspend fun usageOf(day: String): Map<String, UsageCounters> = usageMap[day].orEmpty()

    private class MemoryTx(
        private val accounts: Map<String, SocialAccount>,
        private val posts: Map<String, SocialPost>,
    ) : StoreTx {
        val accountWrites = mutableMapOf<String, SocialAccount>()
        val postWrites = mutableMapOf<String, SocialPost>()
        val eventWrites = mutableMapOf<String, SocialEvent>()

        private val wrote get() = accountWrites.isNotEmpty() || postWrites.isNotEmpty() || eventWrites.isNotEmpty()

        override fun account(id: String): SocialAccount? {
            check(!wrote) { "Transaction reads must come before writes" }
            return accounts[id]
        }

        override fun post(id: String): SocialPost? {
            check(!wrote) { "Transaction reads must come before writes" }
            return posts[id]
        }

        override fun putAccount(account: SocialAccount) {
            accountWrites[account.id] = account
        }

        override fun putPost(post: SocialPost) {
            postWrites[post.id] = post
        }

        override fun putEvent(event: SocialEvent) {
            eventWrites[event.id] = event
        }
    }
}

fun testAccount(
    id: String = "staging",
    codePrefix: String = "st",
    language: String = "uk",
    timezone: String = "Europe/Kyiv",
    publishedCount: Int = 0,
) = SocialAccount(
    id = id,
    handle = "sellsnap.$id",
    language = language,
    country = "UA",
    timezone = timezone,
    codePrefix = codePrefix,
    publishedCount = publishedCount,
)

fun testPost(
    id: String = "stt0001",
    accountId: String = "staging",
    status: PostStatus = PostStatus.Approved,
    scheduledAt: Instant? = null,
) = SocialPost(
    id = id,
    accountId = accountId,
    code = id,
    pillar = Pillar.Tip,
    kind = PostKind.Text,
    text = "Tip text",
    source = PostSource.Seed,
    scheduledAt = scheduledAt,
    status = status,
    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
)
