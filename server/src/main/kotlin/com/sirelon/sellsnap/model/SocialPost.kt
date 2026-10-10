package com.sirelon.sellsnap.model

import java.time.Instant

enum class PostStatus { Drafted, Pending, Approved, Skipped, Publishing, Published, Failed, Deleted }

enum class PostKind { Text, Image, Poll }

/** [letter] is the pillar character inside the post code. */
enum class Pillar(val letter: Char) {
    Demo('d'),
    Tip('t'),
    Poll('p'),
    TitlePick('k'),
    Update('u'),
    Reveal('r'),
}

enum class PostSource { Seed, Owner, Drafter }

data class SocialPost(
    val id: String,
    val accountId: String,
    val code: String,
    val pillar: Pillar,
    val kind: PostKind,
    /** Template text; a `{link}` placeholder is replaced at publish time. */
    val text: String,
    val imagePaths: List<String> = emptyList(),
    val pollOptions: List<String> = emptyList(),
    /** True when [text] carries the `{link}` placeholder. */
    val link: Boolean = false,
    val source: PostSource,
    val seedKey: String? = null,
    /** Null until the post is given a slot. */
    val scheduledAt: Instant? = null,
    val status: PostStatus,
    val skipReason: String? = null,
    /** Identifies the publisher that moved the post to `publishing`; every later write is conditional on it. */
    val claimId: String? = null,
    val publishingClaimedAt: Instant? = null,
    /** Consecutive ticks that ended in a transient Threads failure; reset on success. */
    val transientFailures: Int = 0,
    val containerId: String? = null,
    val mediaId: String? = null,
    /** Template for the account's own reply under the post; may carry `{link}`. Null when the post has no reply. */
    val replyText: String? = null,
    /** The reply's media container; stored before the reply is published. */
    val replyContainerId: String? = null,
    val replyMediaId: String? = null,
    val publishedAt: Instant? = null,
    val telegramMessageId: Long? = null,
    val editPromptMessageId: Long? = null,
    val editedByOwner: Boolean = false,
    /** A review flag: the drafter's checks found a problem, or the post's media id was lost. The owner decides; it never blocks a publish. */
    val needsAttention: Boolean = false,
    /** True once the post's link reply is given up on; the publisher then stops retrying it. */
    val replyFailed: Boolean = false,
    /** True when auto mode approved the post; a Delete or `/approval` sends such posts back to `pending`. */
    val autoApproved: Boolean = false,
    val pairedPostId: String? = null,
    /** English back-translation of a Polish draft, shown to the owner in the preview. */
    val backTranslation: String? = null,
    /** What the drafter's checks found, in plain words; set together with [needsAttention] on a drafted post. */
    val checkNotes: String? = null,
    /** Times the owner tapped Regenerate on this post. */
    val regenerations: Int = 0,
    /** What the post is about, for example `release:3.3` for the update that announces version 3.3. */
    val sourceRef: String? = null,
    /** ISO week of the batch that drafted the post, for example `2026-W43`. */
    val batchWeek: String? = null,
    val createdAt: Instant,
)
