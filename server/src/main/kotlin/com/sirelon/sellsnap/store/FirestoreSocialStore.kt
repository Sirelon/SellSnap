package com.sirelon.sellsnap.store

import com.google.api.core.ApiFuture
import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.Transaction
import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.BatchAccount
import com.sirelon.sellsnap.model.SocialBatch
import com.sirelon.sellsnap.model.SocialClick
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialInstall
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.model.UsageCounters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ExecutionException

/** Firestore adapter. The Firestore client is blocking, so every call runs on [Dispatchers.IO]. */
class FirestoreSocialStore(private val firestore: Firestore) : SocialStore {
    override suspend fun account(id: String): SocialAccount? =
        accountDoc(id).get().await().takeIf { it.exists() }?.accountOrNull()

    override suspend fun accounts(): List<SocialAccount> =
        firestore.collection(ACCOUNTS).get().await().documents.mapNotNull { it.accountOrNull() }

    override suspend fun post(id: String): SocialPost? =
        postDoc(id).get().await().takeIf { it.exists() }?.postOrNull()

    override suspend fun postsOf(accountId: String): List<SocialPost> =
        firestore.collection(POSTS).whereEqualTo("accountId", accountId).get().await()
            .documents.mapNotNull { it.postOrNull() }

    override suspend fun postsWithStatus(status: PostStatus): List<SocialPost> =
        firestore.collection(POSTS).whereEqualTo("status", status.wire()).get().await()
            .documents.mapNotNull { it.postOrNull() }

    override suspend fun batch(week: String): SocialBatch? =
        batchDoc(week).get().await().takeIf { it.exists() }?.data?.let { batchFromMap(week, it) }

    override suspend fun slotMarkers(week: String): Map<String, String> =
        batchDoc(week).collection(SLOTS).get().await().documents
            .mapNotNull { doc -> doc.getString("postId")?.let { doc.id to it } }
            .toMap()

    override suspend fun <T> transaction(block: (StoreTx) -> T): T = withContext(Dispatchers.IO) {
        firestore.runTransaction { tx -> block(FirestoreTx(tx)) }.awaitBlocking()
    }

    override suspend fun claimUpdate(updateId: Long, expireAt: Instant): Boolean =
        createIfAbsent(
            firestore.collection(UPDATES).document(updateId.toString()),
            mapOf("expireAt" to expireAt.toTimestamp()),
        )

    override suspend fun recordEvent(event: SocialEvent): Boolean =
        createIfAbsent(eventDoc(event.id), event.toMap())

    override suspend fun logClick(click: SocialClick) {
        firestore.collection(CLICKS).document(click.id).set(click.toMap()).await()
    }

    override suspend fun clicksSince(since: Instant): List<SocialClick> =
        firestore.collection(CLICKS).whereGreaterThanOrEqualTo("ts", since.toTimestamp()).get().await()
            .documents.map { socialClickFromMap(it.id, it.data) }

    override suspend fun eventsSince(since: Instant): List<SocialEvent> =
        firestore.collection(EVENTS).whereGreaterThanOrEqualTo("at", since.toTimestamp()).get().await()
            .documents.mapNotNull { socialEventFromMap(it.id, it.data) }

    override suspend fun saveMetrics(metrics: PostMetrics) {
        firestore.collection(METRICS).document(metrics.id).set(metrics.toMap()).await()
    }

    override suspend fun metricsOf(accountId: String, day: String): List<PostMetrics> =
        firestore.collection(METRICS).whereEqualTo("accountId", accountId).whereEqualTo("day", day).get().await()
            .documents.map { postMetricsFromMap(it.data) }

    override suspend fun accountMetrics(accountId: String, day: String): AccountMetrics? =
        accountMetricsDoc("${accountId}_$day").get().await().takeIf { it.exists() }?.data?.let(::accountMetricsFromMap)

    override suspend fun accountMetricsOf(accountId: String): List<AccountMetrics> =
        firestore.collection(ACCOUNT_METRICS).whereEqualTo("accountId", accountId).get().await()
            .documents.map { accountMetricsFromMap(it.data) }

    override suspend fun claimAccountDay(metrics: AccountMetrics): Boolean =
        createIfAbsent(accountMetricsDoc(metrics.id), metrics.toMap())

    override suspend fun saveAccountMetrics(metrics: AccountMetrics) {
        accountMetricsDoc(metrics.id).set(metrics.toMap()).await()
    }

    override suspend fun saveInstall(install: SocialInstall) {
        firestore.collection(INSTALLS).document(install.id).set(install.toMap()).await()
    }

    override suspend fun installsOf(accountId: String): List<SocialInstall> =
        firestore.collection(INSTALLS).whereEqualTo("accountId", accountId).get().await()
            .documents.map { installFromMap(it.data) }

    override suspend fun addUsage(day: String, model: String, delta: UsageCounters) {
        firestore.collection(SPEND).document(day).set(usageIncrements(day, model, delta), SetOptions.merge()).await()
    }

    override suspend fun usageOf(day: String): Map<String, UsageCounters> =
        firestore.collection(SPEND).document(day).get().await().takeIf { it.exists() }?.data?.let(::usageFromMap).orEmpty()

    private suspend fun createIfAbsent(doc: DocumentReference, data: Map<String, Any?>): Boolean =
        try {
            doc.create(data).await()
            true
        } catch (e: ApiException) {
            if (e.statusCode.code == StatusCode.Code.ALREADY_EXISTS) false else throw e
        }

    private fun accountDoc(id: String) = firestore.collection(ACCOUNTS).document(id)

    private fun postDoc(id: String) = firestore.collection(POSTS).document(id)

    private fun batchDoc(week: String) = firestore.collection(BATCHES).document(week)

    private fun slotDoc(week: String, key: String) = batchDoc(week).collection(SLOTS).document(key)

    private fun eventDoc(id: String) = firestore.collection(EVENTS).document(id)

    private fun accountMetricsDoc(id: String) = firestore.collection(ACCOUNT_METRICS).document(id)

    private inner class FirestoreTx(private val tx: Transaction) : StoreTx {
        override fun account(id: String): SocialAccount? =
            tx.get(accountDoc(id)).get().takeIf { it.exists() }?.accountOrNull()

        override fun post(id: String): SocialPost? =
            tx.get(postDoc(id)).get().takeIf { it.exists() }?.postOrNull()

        override fun putAccount(account: SocialAccount) {
            tx.set(accountDoc(account.id), account.toMap())
        }

        override fun putPost(post: SocialPost) {
            tx.set(postDoc(post.id), post.toMap())
        }

        override fun putEvent(event: SocialEvent) {
            tx.set(eventDoc(event.id), event.toMap())
        }

        override fun batch(week: String): SocialBatch? =
            tx.get(batchDoc(week)).get().takeIf { it.exists() }?.data?.let { batchFromMap(week, it) }

        override fun slotMarker(week: String, key: String): String? =
            tx.get(slotDoc(week, key)).get().takeIf { it.exists() }?.getString("postId")

        override fun putBatch(batch: SocialBatch) {
            tx.set(batchDoc(batch.week), batch.toMap())
        }

        override fun putSlotMarker(week: String, key: String, postId: String) {
            tx.set(slotDoc(week, key), mapOf("postId" to postId))
        }
    }

    internal companion object {
        const val ACCOUNTS = "social_accounts"
        const val POSTS = "social_posts"
        const val EVENTS = "social_events"
        const val CLICKS = "social_clicks"
        const val UPDATES = "social_updates"
        const val METRICS = "social_metrics"
        const val ACCOUNT_METRICS = "social_account_metrics"
        const val SPEND = "social_spend"
        const val INSTALLS = "social_installs"
        const val BATCHES = "social_batches"
        const val SLOTS = "slots"
    }
}

private suspend fun <T> ApiFuture<T>.await(): T = withContext(Dispatchers.IO) { awaitBlocking() }

private fun <T> ApiFuture<T>.awaitBlocking(): T =
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

private fun DocumentSnapshot.accountOrNull(): SocialAccount? = data?.let { accountFromMap(id, it) }

private fun DocumentSnapshot.postOrNull(): SocialPost? = data?.let { postFromMap(id, it) }

private fun Enum<*>.wire(): String = name.lowercase()

private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String, default: E? = null): E {
    val raw = this[key] as String?
    return enumValues<E>().firstOrNull { it.wire() == raw } ?: default ?: error("Unknown $key value")
}

private fun Instant.toTimestamp(): Timestamp = Timestamp.ofTimeSecondsAndNanos(epochSecond, nano)

private fun Map<String, Any?>.instant(key: String): Instant? =
    (this[key] as Timestamp?)?.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) }

private fun Map<String, Any?>.string(key: String): String? = this[key] as String?

private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number?)?.toInt() ?: 0

private fun Map<String, Any?>.long(key: String): Long? = (this[key] as Number?)?.toLong()

private fun Map<String, Any?>.bool(key: String): Boolean = this[key] as Boolean? ?: false

private fun Map<String, Any?>.strings(key: String): List<String> =
    (this[key] as List<*>?)?.map { it as String } ?: emptyList()

internal fun SocialAccount.toMap(): Map<String, Any?> = mapOf(
    "handle" to handle,
    "language" to language,
    "country" to country,
    "timezone" to timezone,
    "codePrefix" to codePrefix,
    "mode" to mode.wire(),
    "approvalStreak" to approvalStreak,
    "paused" to paused,
    "pausedReason" to pausedReason,
    "threadsUserId" to threadsUserId,
    "token" to token,
    "tokenExpiresAt" to tokenExpiresAt?.toTimestamp(),
    "tokenRefreshedAt" to tokenRefreshedAt?.toTimestamp(),
    "permissionsExpireAt" to permissionsExpireAt?.toTimestamp(),
    "postSeq" to postSeq,
    "publishedCount" to publishedCount,
    "batchEnabled" to batchEnabled,
)

internal fun accountFromMap(id: String, data: Map<String, Any?>): SocialAccount = SocialAccount(
    id = id,
    handle = data.string("handle").orEmpty(),
    language = data.string("language").orEmpty(),
    country = data.string("country").orEmpty(),
    timezone = data.string("timezone").orEmpty(),
    codePrefix = data.string("codePrefix").orEmpty(),
    mode = data.enum("mode", AccountMode.Approval),
    approvalStreak = data.int("approvalStreak"),
    paused = data.bool("paused"),
    pausedReason = data.string("pausedReason"),
    threadsUserId = data.string("threadsUserId"),
    token = data.string("token"),
    tokenExpiresAt = data.instant("tokenExpiresAt"),
    tokenRefreshedAt = data.instant("tokenRefreshedAt"),
    permissionsExpireAt = data.instant("permissionsExpireAt"),
    postSeq = data.int("postSeq"),
    publishedCount = data.int("publishedCount"),
    // An account written before the field existed takes part in batches.
    batchEnabled = data["batchEnabled"] as Boolean? ?: true,
)

internal fun SocialPost.toMap(): Map<String, Any?> = mapOf(
    "accountId" to accountId,
    "code" to code,
    "pillar" to pillar.wire(),
    "kind" to kind.wire(),
    "text" to text,
    "imagePaths" to imagePaths,
    "pollOptions" to pollOptions,
    "link" to link,
    "source" to source.wire(),
    "seedKey" to seedKey,
    "scheduledAt" to scheduledAt?.toTimestamp(),
    "status" to status.wire(),
    "skipReason" to skipReason,
    "claimId" to claimId,
    "publishingClaimedAt" to publishingClaimedAt?.toTimestamp(),
    "transientFailures" to transientFailures,
    "containerId" to containerId,
    "mediaId" to mediaId,
    "replyText" to replyText,
    "replyContainerId" to replyContainerId,
    "replyMediaId" to replyMediaId,
    "publishedAt" to publishedAt?.toTimestamp(),
    "telegramMessageId" to telegramMessageId,
    "editPromptMessageId" to editPromptMessageId,
    "editedByOwner" to editedByOwner,
    "needsAttention" to needsAttention,
    "replyFailed" to replyFailed,
    "autoApproved" to autoApproved,
    "pairedPostId" to pairedPostId,
    "backTranslation" to backTranslation,
    "checkNotes" to checkNotes,
    "regenerations" to regenerations,
    "regeneratingSince" to regeneratingSince?.toTimestamp(),
    "sourceRef" to sourceRef,
    "batchWeek" to batchWeek,
    "createdAt" to createdAt.toTimestamp(),
)

internal fun postFromMap(id: String, data: Map<String, Any?>): SocialPost = SocialPost(
    id = id,
    accountId = data.string("accountId").orEmpty(),
    code = data.string("code").orEmpty(),
    pillar = data.enum<Pillar>("pillar"),
    kind = data.enum<PostKind>("kind"),
    text = data.string("text").orEmpty(),
    imagePaths = data.strings("imagePaths"),
    pollOptions = data.strings("pollOptions"),
    link = data.bool("link"),
    source = data.enum<PostSource>("source"),
    seedKey = data.string("seedKey"),
    scheduledAt = data.instant("scheduledAt"),
    status = data.enum<PostStatus>("status"),
    skipReason = data.string("skipReason"),
    claimId = data.string("claimId"),
    publishingClaimedAt = data.instant("publishingClaimedAt"),
    transientFailures = data.int("transientFailures"),
    containerId = data.string("containerId"),
    mediaId = data.string("mediaId"),
    replyText = data.string("replyText"),
    replyContainerId = data.string("replyContainerId"),
    replyMediaId = data.string("replyMediaId"),
    publishedAt = data.instant("publishedAt"),
    telegramMessageId = data.long("telegramMessageId"),
    editPromptMessageId = data.long("editPromptMessageId"),
    editedByOwner = data.bool("editedByOwner"),
    needsAttention = data.bool("needsAttention"),
    replyFailed = data.bool("replyFailed"),
    autoApproved = data.bool("autoApproved"),
    pairedPostId = data.string("pairedPostId"),
    backTranslation = data.string("backTranslation"),
    checkNotes = data.string("checkNotes"),
    regenerations = data.int("regenerations"),
    regeneratingSince = data.instant("regeneratingSince"),
    sourceRef = data.string("sourceRef"),
    batchWeek = data.string("batchWeek"),
    createdAt = data.instant("createdAt") ?: Instant.EPOCH,
)

internal fun SocialEvent.toMap(): Map<String, Any?> = mapOf(
    "accountId" to accountId,
    "type" to type.wire(),
    "at" to at.toTimestamp(),
    "postId" to postId,
    "detail" to detail,
)

internal fun PostMetrics.toMap(): Map<String, Any?> = mapOf(
    "postId" to postId,
    "accountId" to accountId,
    "day" to day,
    "at" to at.toTimestamp(),
    "views" to views,
    "likes" to likes,
    "replies" to replies,
    "reposts" to reposts,
    "quotes" to quotes,
)

internal fun postMetricsFromMap(data: Map<String, Any?>): PostMetrics = PostMetrics(
    postId = data.string("postId").orEmpty(),
    accountId = data.string("accountId").orEmpty(),
    day = data.string("day").orEmpty(),
    at = data.instant("at") ?: Instant.EPOCH,
    views = data.long("views"),
    likes = data.long("likes") ?: 0,
    replies = data.long("replies") ?: 0,
    reposts = data.long("reposts") ?: 0,
    quotes = data.long("quotes") ?: 0,
)

internal fun AccountMetrics.toMap(): Map<String, Any?> = mapOf(
    "accountId" to accountId,
    "day" to day,
    "at" to at.toTimestamp(),
    "followersCount" to followersCount,
)

internal fun accountMetricsFromMap(data: Map<String, Any?>): AccountMetrics = AccountMetrics(
    accountId = data.string("accountId").orEmpty(),
    day = data.string("day").orEmpty(),
    at = data.instant("at") ?: Instant.EPOCH,
    followersCount = data.long("followersCount"),
)

/**
 * A merge-set body that increments one model's counters. [FieldValue.increment] makes each add
 * atomic on the server, and the model name is a map key, so a dot in it is not read as a path.
 */
internal fun usageIncrements(day: String, model: String, delta: UsageCounters): Map<String, Any?> = mapOf(
    "day" to day,
    "models" to mapOf(
        model to mapOf(
            "calls" to FieldValue.increment(delta.calls),
            "promptTokens" to FieldValue.increment(delta.promptTokens),
            "outputTokens" to FieldValue.increment(delta.outputTokens),
            "thoughtsTokens" to FieldValue.increment(delta.thoughtsTokens),
        ),
    ),
)

internal fun usageFromMap(data: Map<String, Any?>): Map<String, UsageCounters> =
    (data["models"] as Map<*, *>?).orEmpty().entries.associate { (model, counters) ->
        val fields = counters as Map<*, *>
        fun count(key: String) = (fields[key] as Number?)?.toLong() ?: 0L
        model as String to UsageCounters(
            calls = count("calls"),
            promptTokens = count("promptTokens"),
            outputTokens = count("outputTokens"),
            thoughtsTokens = count("thoughtsTokens"),
        )
    }

internal fun SocialBatch.toMap(): Map<String, Any?> = mapOf(
    "runId" to runId,
    "leaseUntil" to leaseUntil?.toTimestamp(),
    "startedAt" to startedAt?.toTimestamp(),
    "finishedAt" to finishedAt?.toTimestamp(),
    "lastError" to lastError,
    "digestSent" to digestSent.sorted(),
    "accounts" to accounts.mapValues { (_, account) ->
        mapOf(
            "done" to account.done,
            "slotsWritten" to account.slotsWritten,
            "failedSlots" to account.failedSlots,
        )
    },
)

internal fun batchFromMap(week: String, data: Map<String, Any?>): SocialBatch = SocialBatch(
    week = week,
    runId = data.string("runId"),
    leaseUntil = data.instant("leaseUntil"),
    startedAt = data.instant("startedAt"),
    finishedAt = data.instant("finishedAt"),
    lastError = data.string("lastError"),
    digestSent = data.strings("digestSent").toSet(),
    accounts = (data["accounts"] as Map<*, *>?).orEmpty().entries.associate { (id, value) ->
        val fields = value as Map<*, *>
        id as String to BatchAccount(
            done = fields["done"] as Boolean? ?: false,
            slotsWritten = (fields["slotsWritten"] as Number?)?.toInt() ?: 0,
            failedSlots = (fields["failedSlots"] as List<*>?)?.map { it as String } ?: emptyList(),
        )
    },
)

internal fun SocialInstall.toMap(): Map<String, Any?> = mapOf(
    "accountId" to accountId,
    "code" to code,
    "day" to day,
    "installs" to installs,
    "syncedAt" to syncedAt.toTimestamp(),
)

internal fun installFromMap(data: Map<String, Any?>): SocialInstall = SocialInstall(
    accountId = data.string("accountId").orEmpty(),
    code = data.string("code").orEmpty(),
    day = data.string("day").orEmpty(),
    installs = data.long("installs") ?: 0,
    syncedAt = data.instant("syncedAt") ?: Instant.EPOCH,
)

/** Null for an event of a type this version does not know, so a newer writer never breaks a reader. */
internal fun socialEventFromMap(id: String, data: Map<String, Any?>): SocialEvent? {
    val type = EventType.entries.firstOrNull { it.wire() == data.string("type") } ?: return null
    return SocialEvent(
        id = id,
        accountId = data.string("accountId").orEmpty(),
        type = type,
        at = data.instant("at") ?: Instant.EPOCH,
        postId = data.string("postId"),
        detail = data.string("detail"),
    )
}

internal fun socialClickFromMap(id: String, data: Map<String, Any?>): SocialClick = SocialClick(
    id = id,
    code = data.string("code").orEmpty(),
    ts = data.instant("ts") ?: Instant.EPOCH,
    store = data.string("store").orEmpty(),
    uaFamily = data.string("uaFamily").orEmpty(),
    countryFromIp = data.string("countryFromIp"),
)

internal fun SocialClick.toMap(): Map<String, Any?> = mapOf(
    "code" to code,
    "ts" to ts.toTimestamp(),
    "store" to store,
    "uaFamily" to uaFamily,
    "countryFromIp" to countryFromIp,
)
