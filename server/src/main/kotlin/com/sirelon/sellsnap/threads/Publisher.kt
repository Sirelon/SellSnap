package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.content.postLinkUrl
import com.sirelon.sellsnap.media.ImageStore
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.store.transitionPost
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

/** What became of a post's link reply in one step. */
enum class ReplyState {
    /** The post has no reply, or the reply was not attempted. */
    None,
    Published,

    /** Not published now; a later tick retries it. */
    Deferred,

    /** A permanent failure: the post stays live, is marked `needsAttention` and the owner is alerted. */
    Failed,
}

sealed interface PublishOutcome {
    val alerts: List<AlertRequest> get() = emptyList()

    /**
     * [mediaId] is null only when the post is live but its id could not be recovered; [needsAttention]
     * is then set. [reply] is the state of the post's link reply when the same run tried to publish it.
     */
    data class Published(
        val postId: String,
        val mediaId: String?,
        val needsAttention: Boolean = false,
        override val alerts: List<AlertRequest> = emptyList(),
        val reply: ReplyState = ReplyState.None,
    ) : PublishOutcome

    /** A later step for the link reply of an already published post; the post itself is untouched. */
    data class Replied(
        val postId: String,
        val accountId: String,
        val state: ReplyState,
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
 *
 * A post with a `replyText` gets a second step once it is `published`: the account publishes its own
 * reply under it. The reply uses the same claim fencing and recovery, but a reply failure never
 * fails or unpublishes the post, and a permanent one never pauses the account.
 */
class Publisher(
    private val store: SocialStore,
    private val threads: ThreadsApi,
    private val images: ImageStore,
    private val clock: Clock,
    private val linkTemplate: String,
    private val newClaimId: () -> String = { UUID.randomUUID().toString() },
    private val retryDelays: List<KDuration> = DEFAULT_RETRY_DELAYS,
    private val statusPollInterval: KDuration = 10.seconds,
    private val maxWaitPerPost: KDuration = 60.seconds,
    private val staleClaimAfter: Duration = Duration.ofMinutes(15),
) {
    /**
     * Publishes the account's due posts, recovers its stale claims, then publishes the link replies
     * that are still owed; the tick calls this.
     */
    suspend fun publishDue(accountId: String): List<PublishOutcome> {
        val account = store.account(accountId) ?: return emptyList()
        gate(account)?.let { return listOf(it) }
        val now = clock.instant()
        val posts = store.postsOf(accountId)
        val due = posts.filter { isDue(it, now) }.sortedBy { it.scheduledAt }
        val owedReplies = posts.filter(::replyOwed).sortedBy { it.publishedAt }
        val outcomes = if (due.isEmpty()) emptyList() else publishAll(account, due, requireDue = true)
        if (owedReplies.isEmpty() || outcomes.any(::blocksReplies)) return outcomes
        return outcomes + publishReplies(account, owedReplies)
    }

    /** A published post whose reply is not published and not given up on (`needsAttention` stops retries). */
    private fun replyOwed(post: SocialPost): Boolean =
        post.status == PostStatus.Published && post.replyText != null && post.replyMediaId == null &&
            post.mediaId != null && !post.needsAttention

    /** The account is paused or Threads is failing: the replies wait for a later tick. */
    private fun blocksReplies(outcome: PublishOutcome): Boolean = when (outcome) {
        is PublishOutcome.Paused -> true
        is PublishOutcome.Deferred -> outcome.reason in STOP_REASONS
        is PublishOutcome.Published -> outcome.reply == ReplyState.Deferred
        else -> false
    }

    private suspend fun publishReplies(account: SocialAccount, posts: List<SocialPost>): List<PublishOutcome> {
        val outcomes = mutableListOf<PublishOutcome>()
        for (post in posts) {
            val run = ReplyRun(account, post, newClaimId())
            if (!run.claim()) continue
            val result = run.execute()
            outcomes += PublishOutcome.Replied(post.id, account.id, result.state, result.alerts)
            if (result.stop) break
        }
        return outcomes
    }

    /** Publishes one approved post now, whatever its slot; `/post <account> now` calls this. */
    suspend fun publishNow(postId: String): PublishOutcome {
        val post = store.post(postId) ?: return notPublishable(postId, "")
        val account = store.account(post.accountId) ?: return notPublishable(postId, post.accountId)
        gate(account)?.let { return it }
        if (post.status != PostStatus.Approved && !isStale(post, clock.instant())) {
            return notPublishable(postId, account.id)
        }
        val outcome = publishAll(account, listOf(post), requireDue = false).single()
        // A permanent failure before the claim (the limit read) pauses only the account; a `now`
        // post must not stay approved, or /resume would roll it to a later slot and publish it.
        if (outcome is PublishOutcome.Paused && outcome.postId == null) {
            store.transitionPost(postId, PostStatus.Approved) { it.copy(status = PostStatus.Failed) }
            return outcome.copy(postId = postId)
        }
        return outcome
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
            val outcome = try {
                try {
                    publish()
                } catch (failure: ThreadsFailure) {
                    record(failure)
                }
            } catch (_: ClaimLostException) {
                return PublishOutcome.ClaimLost(post.id)
            }
            val replyDue = outcome is PublishOutcome.Published && outcome.mediaId != null && post.replyText != null
            return if (replyDue) publishReply(outcome as PublishOutcome.Published) else outcome
        }

        /** The post is live: publish its reply under the same claim. Its outcome never changes the post's. */
        private suspend fun publishReply(published: PublishOutcome.Published): PublishOutcome {
            val fresh = store.post(post.id) ?: return published
            val result = ReplyRun(account, fresh, claimId).execute()
            return published.copy(reply = result.state, alerts = published.alerts + result.alerts)
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

        /** Resumes from the container's state; see [resumeContainer]. */
        private suspend fun publish(): PublishOutcome = resumeContainer(
            existingId = post.containerId,
            token = token,
            what = "post",
            create = { createContainer() },
            onFinished = { publishContainer(it) },
            onPublished = { recoverPublished() },
            onWaiting = { PublishOutcome.Deferred(post.id, account.id, DeferReason.ContainerInProgress) },
        )

        private suspend fun createContainer(): String {
            val request = containerRequest()
            val id = call { threads.createContainer(userId, token, request) }
            write { it.copy(containerId = id) }
            return id
        }

        private fun containerRequest(): ContainerRequest {
            val text = ContentRules.render(post.text, postLinkUrl(linkTemplate, post.code, account.language), account.language)
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
            val rendered = ContentRules.render(post.text, postLinkUrl(linkTemplate, post.code, account.language), account.language)
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

    /**
     * Resumes a container from its state: none creates one, in progress waits up to the budget,
     * finished publishes, published recovers the media id, error is permanent, expired creates a
     * new one. [what] names the container's owner in the permanent failure's message.
     */
    private suspend fun <T> resumeContainer(
        existingId: String?,
        token: String,
        what: String,
        create: suspend () -> String,
        onFinished: suspend (containerId: String) -> T,
        onPublished: suspend () -> T,
        onWaiting: () -> T,
    ): T {
        var created = false
        var containerId = existingId ?: create().also { created = true }
        var waited = kotlin.time.Duration.ZERO
        while (true) {
            val status = withRetry(retryDelays) { threads.containerStatus(containerId, token) }
            when (status.state) {
                ContainerState.Finished -> return onFinished(containerId)
                ContainerState.Published -> return onPublished()
                ContainerState.Error -> throw ThreadsFailure.Permanent(
                    null,
                    "Threads could not process the $what (${status.errorMessage ?: "UNKNOWN"})",
                )
                ContainerState.Expired -> {
                    if (created) throw ThreadsFailure.Transient("Threads container expired right after creation")
                    containerId = create()
                    created = true
                }
                ContainerState.InProgress -> {
                    if (waited >= maxWaitPerPost) return onWaiting()
                    delay(statusPollInterval)
                    waited += statusPollInterval
                }
            }
        }
    }

    private class ReplyResult(
        val state: ReplyState,
        val alerts: List<AlertRequest> = emptyList(),
        /** Threads is rate limiting or failing: the remaining replies wait for the next tick. */
        val stop: Boolean = false,
    )

    /**
     * One post's link reply, from claim to a recorded outcome. The post is `published`, so the claim
     * lives in its `claimId`: every write is a transaction that still finds this claim id, a reply
     * not yet recorded and the post still `published`. The reply is a text container with `reply_to_id`.
     */
    private inner class ReplyRun(
        private val account: SocialAccount,
        private var post: SocialPost,
        private val claimId: String,
    ) {
        private val userId = account.threadsUserId.orEmpty()
        private val token = account.token.orEmpty()

        /** Takes the reply claim: false when the reply is no longer owed or another publisher holds a fresh claim. */
        suspend fun claim(): Boolean {
            val claimed = store.transaction { tx ->
                val current = tx.post(post.id)
                val owner = current?.let { tx.account(it.accountId) }
                val now = clock.instant()
                val claimable = current != null && owner != null && !owner.paused && owner.token != null &&
                    replyOwed(current) && claimFree(current, now)
                if (claimable) current!!.copy(claimId = claimId, publishingClaimedAt = now).also(tx::putPost) else null
            }
            if (claimed != null) post = claimed
            return claimed != null
        }

        private fun claimFree(current: SocialPost, now: Instant): Boolean =
            current.claimId == null || current.publishingClaimedAt == null ||
                !current.publishingClaimedAt.plus(staleClaimAfter).isAfter(now)

        suspend fun execute(): ReplyResult =
            try {
                try {
                    publish()
                } catch (failure: ThreadsFailure) {
                    record(failure)
                }
            } catch (_: ClaimLostException) {
                // Another publisher owns the reply now; it finishes or retries it.
                ReplyResult(ReplyState.Deferred, stop = true)
            }

        private suspend fun publish(): ReplyResult = resumeContainer(
            existingId = post.replyContainerId,
            token = token,
            what = "reply",
            create = { createContainer() },
            onFinished = { publishContainer(it) },
            onPublished = { recoverPublished() },
            onWaiting = { ReplyResult(ReplyState.Deferred) },
        )

        private fun renderedReply(): String =
            ContentRules.render(post.replyText.orEmpty(), postLinkUrl(linkTemplate, post.code, account.language), account.language)

        private suspend fun createContainer(): String {
            val replyTo = post.mediaId ?: throw ThreadsFailure.Permanent(null, "The reply has no post to answer")
            val request = ContainerRequest(PostKind.Text, renderedReply(), replyToId = replyTo)
            val id = call { threads.createContainer(userId, token, request) }
            write { it.copy(replyContainerId = id) }
            return id
        }

        private suspend fun publishContainer(containerId: String): ReplyResult {
            // As for a post: look at the container before every repeat, because it publishes only once.
            val replyId = call { attempt ->
                if (attempt > 0 && threads.containerStatus(containerId, token).state == ContainerState.Published) {
                    null
                } else {
                    threads.publish(userId, token, containerId)
                }
            }
            return if (replyId == null) recoverPublished() else recordPublished(replyId)
        }

        /**
         * The container is already published: look for the reply's media id by its text among the
         * latest threads. Inferred: the threads list may not include replies; a miss is handled below.
         */
        private suspend fun recoverPublished(): ReplyResult {
            val rendered = renderedReply()
            val recent = call { threads.recentThreads(userId, token, RECENT_THREADS) }
            val match = recent.filter { it.text == rendered && it.id != post.mediaId }
                .maxByOrNull { it.publishedAt ?: Instant.EPOCH }
            return recordPublished(match?.id)
        }

        private suspend fun recordPublished(replyId: String?): ReplyResult {
            val now = clock.instant()
            val lostId = replyId == null
            val done = store.transaction { tx ->
                val current = tx.post(post.id)
                if (!holdsClaim(current)) {
                    false
                } else {
                    tx.putPost(
                        current!!.copy(
                            replyMediaId = replyId,
                            transientFailures = 0,
                            needsAttention = current.needsAttention || lostId,
                        ),
                    )
                    if (lostId) {
                        val detail = "The reply is live but its media id could not be recovered"
                        tx.putEvent(SocialEvent("recovery_reply_${post.id}", account.id, EventType.Error, now, post.id, detail))
                    }
                    true
                }
            }
            if (!done) throw ClaimLostException()
            if (!lostId) return ReplyResult(ReplyState.Published)
            val alert = AlertRequest(AlertType.ReplyFailed, account.id, post.id, "The reply is live without a recorded media id")
            return ReplyResult(ReplyState.Failed, listOf(alert))
        }

        private suspend fun record(failure: ThreadsFailure): ReplyResult = when (failure) {
            is ThreadsFailure.RateLimited -> ReplyResult(ReplyState.Deferred, stop = true)
            is ThreadsFailure.Transient -> {
                val failures = write { it.copy(transientFailures = it.transientFailures + 1) }.transientFailures
                val alerts = if (failures == STALLED_AFTER) {
                    listOf(AlertRequest(AlertType.PublishStalled, account.id, post.id, redact(failure.message.orEmpty())))
                } else {
                    emptyList()
                }
                ReplyResult(ReplyState.Deferred, alerts, stop = true)
            }
            is ThreadsFailure.Permanent -> recordPermanent(failure)
        }

        /** The post stays live and the account keeps running; the reply is given up on and the owner told. */
        private suspend fun recordPermanent(failure: ThreadsFailure.Permanent): ReplyResult {
            val reason = redact(failure.message.orEmpty())
            val now = clock.instant()
            val done = store.transaction { tx ->
                val current = tx.post(post.id)
                if (!holdsClaim(current)) {
                    false
                } else {
                    tx.putPost(current!!.copy(needsAttention = true))
                    tx.putEvent(SocialEvent("reply_failed_${post.id}_$claimId", account.id, EventType.Error, now, post.id, reason))
                    true
                }
            }
            if (!done) throw ClaimLostException()
            return ReplyResult(ReplyState.Failed, listOf(AlertRequest(AlertType.ReplyFailed, account.id, post.id, reason)))
        }

        private fun holdsClaim(current: SocialPost?): Boolean =
            current != null && current.status == PostStatus.Published && current.claimId == claimId &&
                current.replyMediaId == null

        /** A write that succeeds only while this publisher still holds the reply claim. */
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
