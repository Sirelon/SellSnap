package com.sirelon.sellsnap.store

import com.google.cloud.Timestamp
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreMappingTest {
    private val instant = Instant.parse("2026-10-20T08:30:00.123456789Z")

    @Test
    fun accountSurvivesAMapRoundTrip() {
        val account = testAccount().copy(
            mode = AccountMode.Auto,
            approvalStreak = 20,
            paused = true,
            pausedReason = "token",
            threadsUserId = "123",
            token = "t",
            tokenExpiresAt = instant,
            tokenRefreshedAt = instant.minusSeconds(60),
            permissionsExpireAt = instant.plusSeconds(60),
            postSeq = 7,
            publishedCount = 12,
        )

        assertEquals(account, accountFromMap(account.id, account.toMap()))
    }

    @Test
    fun postSurvivesAMapRoundTrip() {
        val post = testPost(status = PostStatus.Publishing, scheduledAt = instant).copy(
            pillar = Pillar.TitlePick,
            kind = PostKind.Poll,
            imagePaths = listOf("social/manual/stt0001.jpg"),
            pollOptions = listOf("A", "B"),
            link = true,
            source = PostSource.Owner,
            seedKey = "tip01",
            skipReason = "expired",
            claimId = "claim",
            publishingClaimedAt = instant,
            transientFailures = 2,
            containerId = "container",
            mediaId = "media",
            publishedAt = instant,
            telegramMessageId = 5_000_000_000L,
            editPromptMessageId = 6,
            editedByOwner = true,
            needsAttention = true,
            autoApproved = true,
            pairedPostId = "pair",
        )

        assertEquals(post, postFromMap(post.id, post.toMap()))
    }

    @Test
    fun postMetricsSurviveAMapRoundTripWithAndWithoutViews() {
        val metrics = PostMetrics("stt0001", "staging", "20261020", instant, views = 5_000_000_000L, likes = 10, replies = 2, reposts = 1, quotes = 0)

        assertEquals(metrics, postMetricsFromMap(metrics.toMap()))
        assertEquals("stt0001_20261020", metrics.id)
        val noViews = metrics.copy(views = null)
        assertEquals(noViews, postMetricsFromMap(noViews.toMap()))
        assertEquals(null, noViews.toMap()["views"])
    }

    @Test
    fun postMetricsStoreTheDocumentedFieldNames() {
        val map = PostMetrics("stt0001", "staging", "20261020", instant, views = 7, likes = 10, replies = 2, reposts = 1, quotes = 0).toMap()

        assertEquals(
            setOf("postId", "accountId", "day", "at", "views", "likes", "replies", "reposts", "quotes"),
            map.keys,
        )
        assertEquals("20261020", map["day"])
        assertEquals(Timestamp.ofTimeSecondsAndNanos(instant.epochSecond, instant.nano), map["at"])
    }

    @Test
    fun accountMetricsSurviveAMapRoundTrip() {
        val metrics = AccountMetrics("staging", "20261020", instant, followersCount = 1_234)

        assertEquals(metrics, accountMetricsFromMap(metrics.toMap()))
        assertEquals("staging_20261020", metrics.id)
        assertEquals(setOf("accountId", "day", "at", "followersCount"), metrics.toMap().keys)
        val unknown = metrics.copy(followersCount = null)
        assertEquals(unknown, accountMetricsFromMap(unknown.toMap()))
    }

    @Test
    fun absentFieldsReadAsDefaults() {
        val account = accountFromMap("a", mapOf("handle" to "h"))
        assertEquals(AccountMode.Approval, account.mode)
        assertEquals(0, account.publishedCount)
        assertEquals(null, account.permissionsExpireAt)
    }

    @Test
    fun storedFieldsUseLowercaseNamesAndFirestoreTimestamps() {
        val map = testPost(scheduledAt = instant).toMap()
        assertEquals("approved", map["status"])
        assertEquals("tip", map["pillar"])
        assertEquals(Timestamp.ofTimeSecondsAndNanos(instant.epochSecond, instant.nano), map["scheduledAt"])
        assertEquals("approval", testAccount().toMap()["mode"])
    }
}
