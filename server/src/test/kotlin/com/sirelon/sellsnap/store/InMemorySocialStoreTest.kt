package com.sirelon.sellsnap.store

import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemorySocialStoreTest {
    private val store = InMemorySocialStore()
    private val now = Instant.parse("2026-10-20T08:30:00Z")

    @Test
    fun conditionalTransitionMovesThePostOnce() = runTest {
        store.put(testPost(status = PostStatus.Approved))

        val claimed = store.transitionPost("stt0001", PostStatus.Approved) {
            it.copy(status = PostStatus.Publishing, claimId = "claim-1", publishingClaimedAt = now)
        }

        assertEquals(PostStatus.Publishing, claimed?.status)
        assertEquals("claim-1", store.post("stt0001")?.claimId)
    }

    @Test
    fun transitionFromTheWrongStatusChangesNothing() = runTest {
        store.put(testPost(status = PostStatus.Skipped))

        val result = store.transitionPost("stt0001", PostStatus.Approved) { it.copy(status = PostStatus.Publishing) }

        assertNull(result)
        assertEquals(PostStatus.Skipped, store.post("stt0001")?.status)
        assertNull(store.transitionPost("missing", PostStatus.Approved) { it })
    }

    @Test
    fun theLoserOfARaceSeesTheWinnersStatus() = runTest {
        store.put(testPost(status = PostStatus.Approved))

        val results = (1..8).map { n ->
            async {
                store.transitionPost("stt0001", PostStatus.Approved) {
                    it.copy(status = PostStatus.Publishing, claimId = "claim-$n")
                }
            }
        }.awaitAll()

        assertEquals(1, results.count { it != null })
        assertEquals(results.single { it != null }?.claimId, store.post("stt0001")?.claimId)
    }

    @Test
    fun aTransactionCommitsAccountPostAndEventTogether() = runTest {
        store.put(testAccount())
        store.put(testPost(status = PostStatus.Approved))

        store.transaction { tx ->
            val account = assertNotNull(tx.account("staging"))
            val post = assertNotNull(tx.post("stt0001"))
            tx.putAccount(account.copy(publishedCount = account.publishedCount + 1))
            tx.putPost(post.copy(status = PostStatus.Published))
            tx.putEvent(SocialEvent("e1", "staging", EventType.Resume, now))
        }

        assertEquals(1, store.account("staging")?.publishedCount)
        assertEquals(PostStatus.Published, store.post("stt0001")?.status)
        assertEquals(setOf("e1"), store.events.keys)
    }

    @Test
    fun aFailingTransactionCommitsNothing() = runTest {
        store.put(testAccount())
        store.put(testPost(status = PostStatus.Approved))

        assertFailsWith<IllegalStateException> {
            store.transaction { tx ->
                tx.putAccount(tx.account("staging")!!.copy(approvalStreak = 5))
                tx.putPost(testPost(id = "new"))
                error("boom")
            }
        }

        assertEquals(0, store.account("staging")?.approvalStreak)
        assertNull(store.post("new"))
    }

    @Test
    fun readingAfterWritingIsRejectedLikeFirestore() = runTest {
        store.put(testPost())

        assertFailsWith<IllegalStateException> {
            store.transaction { tx ->
                tx.putPost(testPost(id = "other"))
                tx.post("stt0001")
            }
        }
        assertNull(store.post("other"))
    }

    @Test
    fun readsSeeTheCommittedStateNotTheTransactionsOwnWrites() = runTest {
        store.put(testPost(status = PostStatus.Approved))

        val seen = store.transaction { tx ->
            val before = tx.post("stt0001")
            tx.putPost(before!!.copy(status = PostStatus.Skipped))
            before.status
        }

        assertEquals(PostStatus.Approved, seen)
        assertEquals(PostStatus.Skipped, store.post("stt0001")?.status)
    }

    @Test
    fun queriesFilterByAccountAndStatus() = runTest {
        store.put(testPost(id = "a", accountId = "staging", status = PostStatus.Pending))
        store.put(testPost(id = "b", accountId = "ua", status = PostStatus.Approved))
        store.put(testPost(id = "c", accountId = "staging", status = PostStatus.Approved))

        assertEquals(setOf("a", "c"), store.postsOf("staging").map { it.id }.toSet())
        assertEquals(setOf("b", "c"), store.postsWithStatus(PostStatus.Approved).map { it.id }.toSet())
    }

    @Test
    fun claimUpdateAcceptsEachUpdateIdOnce() = runTest {
        val expireAt = now.plusSeconds(7 * 24 * 3600)

        assertTrue(store.claimUpdate(42, expireAt))
        assertFalse(store.claimUpdate(42, expireAt))
        assertTrue(store.claimUpdate(43, expireAt))
        assertEquals(expireAt, store.updates[42])
    }

    @Test
    fun recordEventKeepsTheFirstEventPerId() = runTest {
        val first = SocialEvent("alert_token_staging_20261020", "staging", EventType.Alert, now, detail = "first")
        val second = first.copy(detail = "second")

        assertTrue(store.recordEvent(first))
        assertFalse(store.recordEvent(second))
        assertEquals("first", store.events.getValue(first.id).detail)
    }
}
