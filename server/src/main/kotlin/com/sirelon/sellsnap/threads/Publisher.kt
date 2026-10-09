package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.media.ImageStore
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration as KDuration

/** Why a publish step ended without publishing and without failing for good. */
enum class DeferReason {
    AccountPaused,
    NoToken,
    LimitExhausted,

    /** The post is gone, not approved, or another publisher holds a fresh claim. */
    NotPublishable,
    ClaimedElsewhere,
    ContainerInProgress,
    RateLimited,
    TransientFailure,
}

sealed interface PublishOutcome {
    val alerts: List<AlertRequest> get() = emptyList()

    /** [mediaId] is null only when the post is live but its id could not be recovered; [needsAttention] is then set. */
    data class Published(
        val postId: String,
        val mediaId: String?,
        val needsAttention: Boolean = false,
        override val alerts: List<AlertRequest> = emptyList(),
    ) : PublishOutcome

    /** [postId] is null when the whole account was deferred. */
    data class Deferred(
        val postId: String?,
        val accountId: String,
        val reason: DeferReason,
        override val alerts: List<AlertRequest> = emptyList(),
    ) : PublishOutcome

    /** A permanent failure: the post is `failed` (when [postId] is set) and the account is paused. */
    data class Paused(
        val postId: String?,
        val accountId: String,
        val reason: String,
        override val alerts: List<AlertRequest> = emptyList(),
    ) : PublishOutcome

    /** This publisher's claim was taken over mid-flight; its remaining writes were rejected. */
    data class ClaimLost(val postId: String) : PublishOutcome
}

/**
 * Publishes approved posts to Threads. The post moves `approved -> publishing` in a transaction
 * that stores a fresh claim id; every Threads call happens outside any transaction, and every
 * later write is a transaction that still finds the same claim id on the post. A claim older than
 * [staleClaimAfter] may be taken over, and the new owner resumes from the container's state.
 */
class Publisher(
    private val store: SocialStore,
    private val threads: ThreadsApi,
    private val images: ImageStore,
    private val clock: Clock,
    publicBaseUrl: String,
    private val newClaimId: () -> String = { UUID.randomUUID().toString() },
    private val retryDelays: List<KDuration> = DEFAULT_RETRY_DELAYS,
    private val statusPollInterval: KDuration = 10.seconds,
    private val maxWaitPerPost: KDuration = 60.seconds,
    private val staleClaimAfter: Duration = Duration.ofMinutes(15),
) {
    private val baseUrl = publicBaseUrl.trimEnd('/')

    /** Publishes the account's due posts and recovers its stale claims; the tick calls this. */
    suspend fun publishDue(accountId: String): List<PublishOutcome> {
        val account = store.account(accountId) ?: return emptyList()
        gate(account)?.let { return listOf(it) }
        val now = clock.instant()
        val due = store.postsOf(accountId).filter { isDue(it, now) }.sortedBy { it.scheduledAt }
        if (due.isEmpty()) return emptyList()
        return publishAll(account, due, requireDue = true)
    }

    /** Publishes one approved post now, whatever its slot; `/post <account> now` calls this. */
    suspend fun publishNow(postId: String): PublishOutcome {
        val post = store.post(postId) ?: return notPublishable(postId, "")
        val account = store.account(post.accountId) ?: return notPublishable(postId, post.accountId)
        gate(account)?.let { return it }
        if (post.status != PostStatus.Approved && !isStale(post, clock.instant())) {
            return notPublishable(postId, account.id)
        }
        return publishAll(account, listOf(post), requireDue = false).single()
    }

    private fun notPublishable(postId: String, accountId: String) =
        PublishOutcome.Deferred(postId, accountId, DeferReason.NotPublishable)

    private fun gate(account: SocialAccount): PublishOutcome? = when {
        account.paused -> PublishOutcome.Deferred(null, account.id, DeferReason.AccountPaused)
        account.token == null || account.threadsUserId == null ->
            PublishOutcome.Deferred(null, account.id, DeferReason.NoToken)
        else -> null
    }

    private fun isStale(post: SocialPost, now: Instant): Boolean =
        post.status == PostStatus.Publishing &&
            (post.publishingClaimedAt == null || !post.publishingClaimedAt.plus(staleClaimAfter).isAfter(now))

    private fun isDue(post: SocialPost, now: Instant): Boolean =
        (post.status == PostStatus.Approved && post.scheduledAt?.isAfter(now) == false) || isStale(post, now)

    private suspend fun publishAll(
        account: SocialAccount,
        posts: List<SocialPost>,
        requireDue: Boolean,
    ): List<PublishOutcome> {
        val userId = account.threadsUserId.orEmpty()
        val token = account.token.orEmpty()
        var remaining = try {
            withRetry(retryDelays) { threads.publishingLimit(userId, token) }.remaining
        } catch (failure: ThreadsFailure) {
            return listOf(accountLevelOutcome(account, failure))
        }

        val outcomes = mutableListOf<PublishOutcome>()
        for (post in posts) {
            if (remaining <= 0) {
                outcomes += PublishOutcome.Deferred(null, account.id, DeferReason.LimitExhausted)
                break
            }
            val outcome = PostRun(account, post, newClaimId(), requireDue).execute()
            outcomes += outcome
            if (outcome is PublishOutcome.Published) remaining--
            val stop = outcome is PublishOutcome.Paused ||
                (outcome is PublishOutcome.Deferred && outcome.reason in STOP_REASONS)
            if (stop) break
        }
        return outcomes
    }

    /** A failure of the limit read: there is no post to mark, so only the account is touched. */
    private suspend fun accountLevelOutcome(account: SocialAccount, failure: ThreadsFailure): PublishOutcome =
        when (failure) {
            is ThreadsFailure.RateLimited -> PublishOutcome.Deferred(null, account.id, DeferReason.RateLimited)
            is ThreadsFailure.Transient -> PublishOutcome.Deferred(null, account.id, DeferReason.TransientFailure)
            is ThreadsFailure.Permanent -> {
                val reason = redact(failure.message.orEmpty())
                val now = clock.instant()
                store.transaction { tx ->
                    val current = tx.account(account.id)
                    if (current != null) tx.putAccount(current.copy(paused = true, pausedReason = reason))
                    tx.putEvent(SocialEvent("pause_${account.id}_${now.toEpochMilli()}", account.id, EventType.Pause, now, null, reason))
                }
                PublishOutcome.Paused(
                    postId = null,
                    accountId = account.id,
                    reason = reason,
                    alerts = listOf(AlertRequest(AlertType.PublishFailed, account.id, detail = reason)),
                )
            }
        }

    private class ClaimLostException : Exception()

    /** One claimed post, from claim to a recorded outcome. */
    private inner class PostRun(
        private val account: SocialAccount,
        private val candidate: SocialPost,
        private val claimId: String,
        private val requireDue: Boolean,
    ) {
        private val userId = account.threadsUserId.orEmpty()
        private val token = account.token.orEmpty()
        private var post: SocialPost = candidate

        suspend fun execute(): PublishOutcome {
            val claimed = claim() ?: return PublishOutcome.Deferred(candidate.id, account.id, DeferReason.ClaimedElsewhere)
            post = claimed
            return try {
                try {
                    publish()
                } catch (failure: ThreadsFailure) {
                    record(failure)
                }
            } catch (_: ClaimLostException) {
                PublishOutcome.ClaimLost(post.id)
            }
        }

        private suspend fun claim(): SocialPost? = store.transaction { tx ->
            val current = tx.post(candidate.id)
            val owner = current?.let { tx.account(it.accountId) }
            val now = clock.instant()
            val claimable = current != null && owner != null && !owner.paused && owner.token != null &&
                ((current.status == PostStatus.Approved &&
                    (!requireDue || current.scheduledAt?.isAfter(now) == false)) || isStale(current, now))
            if (!claimable) {
                null
            } else {
                current.copy(status = PostStatus.Publishing, claimId = claimId, publishingClaimedAt = now)
                    .also(tx::putPost)
            }
        }

        /**
         * Resumes from the container's state: none creates one, in progress waits up to the budget,
         * finished publishes, published recovers the media id, error is permanent, expired creates a new one.
         */
        private suspend fun publish(): PublishOutcome {
            var containerId = post.containerId
            var created = false
            if (containerId == null) {
                containerId = createContainer()
                created = true
            }
            var waited = kotlin.time.Duration.ZERO
            while (true) {
                val status = call { threads.containerStatus(containerId!!, token) }
                when (status.state) {
                    ContainerState.Finished -> return publishContainer(containerId!!)
                    ContainerState.Published -> return recoverPublished()
                    ContainerState.Error -> throw ThreadsFailure.Permanent(
                        null,
                        "Threads could not process the post (${status.errorMessage ?: "UNKNOWN"})",
                    )
                    ContainerState.Expired -> {
                        if (created) throw ThreadsFailure.Transient("Threads container expired right after creation")
                        containerId = createContainer()
                        created = true
                    }
                    ContainerState.InProgress -> {
                        if (waited >= maxWaitPerPost) {
                            return PublishOutcome.Deferred(post.id, account.id, DeferReason.ContainerInProgress)
                        }
                        delay(statusPollInterval)
                        waited += statusPollInterval
                    }
                }
            }
        }

        private suspend fun createContainer(): String {
            val request = containerRequest()
            val id = call { threads.createContainer(userId, token, request) }
            write { it.copy(containerId = id) }
            return id
        }

        private fun containerRequest(): ContainerRequest {
            val text = ContentRules.render(post.text, "$baseUrl/go/${post.code}", account.language)
            return when (post.kind) {
                PostKind.Text -> ContainerRequest(PostKind.Text, text)
                PostKind.Poll -> ContainerRequest(PostKind.Poll, text, pollOptions = post.pollOptions)
                PostKind.Image -> {
                    val path = post.imagePaths.firstOrNull()
                        ?: throw ThreadsFailure.Permanent(null, "The image post has no image")
                    ContainerRequest(PostKind.Image, text, imageUrl = images.publicUrl(path))
                }
            }
        }

        private suspend fun publishContainer(containerId: String): PublishOutcome {
            // After a transient failure the first try may have gone through, and a container can be
            // published only once; so look at the container before every repeat.
            val mediaId = call { attempt ->
                if (attempt > 0 && threads.containerStatus(containerId, token).state == ContainerState.Published) {
                    null
                } else {
                    threads.publish(userId, token, containerId)
                }
            }
            return if (mediaId == null) recoverPublished() else recordPublished(mediaId)
        }

        /** The container is already published: find the media id by the post's text among the latest threads. */
        private suspend fun recoverPublished(): PublishOutcome {
            val rendered = ContentRules.render(post.text, "$baseUrl/go/${post.code}", account.language)
            val recent = call { threads.recentThreads(userId, token, RECENT_THREADS) }
            val match = recent.filter { it.text == rendered }.maxByOrNull { it.publishedAt ?: Instant.EPOCH }
            return recordPublished(match?.id)
        }

        private suspend fun recordPublished(mediaId: String?): PublishOutcome {
            val now = clock.instant()
            val lostId = mediaId == null
            val done = store.transaction { tx ->
                val current = tx.post(post.id)
                val owner = tx.account(post.accountId)
                if (!holdsClaim(current)) {
                    false
                } else {
                    tx.putPost(
                        current!!.copy(
                            status = PostStatus.Published,
                            mediaId = mediaId,
                            publishedAt = now,
                            transientFailures = 0,
                            needsAttention = current.needsAttention || lostId,
                        ),
                    )
                    if (owner != null) tx.putAccount(owner.copy(publishedCount = owner.publishedCount + 1))
                    if (lostId) {
                        val detail = "The post is live but its media id could not be recovered"
                        tx.putEvent(SocialEvent("recovery_${post.id}", account.id, EventType.Error, now, post.id, detail))
                    }
                    true
                }
            }
            if (!done) throw ClaimLostException()
            val alerts = if (lostId) {
                listOf(AlertRequest(AlertType.PostNeedsAttention, account.id, post.id, "The post is live without a recorded media id"))
            } else {
                emptyList()
            }
            return PublishOutcome.Published(post.id, mediaId, needsAttention = lostId, alerts = alerts)
        }

        private suspend fun record(failure: ThreadsFailure): PublishOutcome = when (failure) {
            is ThreadsFailure.RateLimited ->
                PublishOutcome.Deferred(post.id, account.id, DeferReason.RateLimited)
            is ThreadsFailure.Transient -> {
                val failures = write { it.copy(transientFailures = it.transientFailures + 1) }.transientFailures
                val alerts = if (failures == STALLED_AFTER) {
                    listOf(AlertRequest(AlertType.PublishStalled, account.id, post.id, redact(failure.message.orEmpty())))
                } else {
                    emptyList()
                }
                PublishOutcome.Deferred(post.id, account.id, DeferReason.TransientFailure, alerts)
            }
            is ThreadsFailure.Permanent -> recordPermanent(failure)
        }

        private suspend fun recordPermanent(failure: ThreadsFailure.Permanent): PublishOutcome {
            val reason = redact(failure.message.orEmpty())
            val now = clock.instant()
            val done = store.transaction { tx ->
                val current = tx.post(post.id)
                val owner = tx.account(post.accountId)
                if (!holdsClaim(current)) {
                    false
                } else {
                    tx.putPost(current!!.copy(status = PostStatus.Failed))
                    if (owner != null) tx.putAccount(owner.copy(paused = true, pausedReason = reason))
                    tx.putEvent(SocialEvent("pause_${post.id}_$claimId", account.id, EventType.Pause, now, post.id, reason))
                    true
                }
            }
            if (!done) throw ClaimLostException()
            return PublishOutcome.Paused(
                postId = post.id,
                accountId = account.id,
                reason = reason,
                alerts = listOf(AlertRequest(AlertType.PublishFailed, account.id, post.id, reason)),
            )
        }

        private fun holdsClaim(current: SocialPost?): Boolean =
            current != null && current.status == PostStatus.Publishing && current.claimId == claimId

        /** A write that succeeds only while this publisher still holds the claim. */
        private suspend fun write(change: (SocialPost) -> SocialPost): SocialPost {
            val written = store.transaction { tx ->
                val current = tx.post(post.id)
                if (holdsClaim(current)) change(current!!).also(tx::putPost) else null
            } ?: throw ClaimLostException()
            post = written
            return written
        }

        private suspend fun <T> call(block: suspend (attempt: Int) -> T): T = withRetry(retryDelays, block)
    }

    private companion object {
        /** Consecutive transient failures that raise an alert. */
        const val STALLED_AFTER = 3
        const val RECENT_THREADS = 25
        val STOP_REASONS = setOf(DeferReason.RateLimited, DeferReason.TransientFailure)
    }
}
