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

enum class PostSource { Seed, Owner }

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
    val containerId: String? = null,
    val mediaId: String? = null,
    val publishedAt: Instant? = null,
    val telegramMessageId: Long? = null,
    val editPromptMessageId: Long? = null,
    val editedByOwner: Boolean = false,
    val needsAttention: Boolean = false,
    val pairedPostId: String? = null,
    val createdAt: Instant,
)
