@file:OptIn(ExperimentalCoroutinesApi::class)

package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.fakes.FakeImageStore
import com.sirelon.sellsnap.fakes.FakeThreadsApi
import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublisherTest {
    private val clock = MutableClock(NOW)
    private val store = InMemorySocialStore()
    private val threads = FakeThreadsApi(store)
    private var claims = 0

    init {
        store.put(testAccount().copy(token = TOKEN, threadsUserId = "u1"))
    }

    private fun publisher(api: ThreadsApi = threads, prefix: String = "claim", template: String = LINK_TEMPLATE) = Publisher(
        store, api, FakeImageStore(), clock, template,
        newClaimId = { "$prefix-${++claims}" },
    )

    private fun duePost(id: String = "stt0001") = testPost(id = id, scheduledAt = NOW.minusSeconds(60))

    private fun stalePost(containerId: String? = null, claimedAgo: Duration = Duration.ofMinutes(16)) =
        testPost(status = PostStatus.Publishing).copy(
            claimId = "old",
            publishingClaimedAt = NOW.minus(claimedAgo),
            containerId = containerId,
        )

    private suspend fun post(id: String = "stt0001") = assertNotNull(store.post(id))

    private fun account() = store.allAccounts.getValue("staging")

    /** Everything the run left behind, as one string, for secret checks. */
    private fun dump(outcomes: List<PublishOutcome>): String =
        outcomes.toString() + store.allPosts + store.events + account().copy(token = null) + threads.calls.map { it.method }

    @Test
    fun publishesADuePostAndRecordsTheOutcome() = runTest {
        store.put(duePost())

        val outcome = publisher().publishDue("staging").single()

        val published = assertIs<PublishOutcome.Published>(outcome)
        assertEquals("media-2", published.mediaId)
        val saved = post()
        assertEquals(PostStatus.Published, saved.status)
        assertEquals("media-2", saved.mediaId)
        assertEquals("container-1", saved.containerId)
        assertEquals(NOW, saved.publishedAt)
        assertEquals(0, saved.transientFailures)
        assertEquals(1, account().publishedCount)
        assertEquals(1, threads.callsTo("publishingLimit").size)
    }

    @Test
    fun savesTheContainerIdBeforeItPublishes() = runTest {
        store.put(duePost())

        publisher().publishDue("staging")

        val atPublish = threads.callsTo("publish").single().posts.getValue("stt0001")
        assertEquals("container-1", atPublish.containerId)
        assertEquals(PostStatus.Publishing, atPublish.status)
        assertEquals("claim-1", atPublish.claimId)
        assertEquals(NOW, atPublish.publishingClaimedAt)
    }

    @Test
    fun theLinkPointsAtTheWebsiteWithTheAccountLanguageAndThePostCode() = runTest {
        store.put(testAccount(id = "pl", codePrefix = "pl", language = "pl", timezone = "Europe/Warsaw").copy(token = TOKEN, threadsUserId = "u2"))
        store.put(testPost(id = "plt0001", accountId = "pl", scheduledAt = NOW.minusSeconds(60)).copy(pillar = Pillar.Update, text = "News: {link}", link = true, replyText = "Get it: {link}"))
        store.put(duePost().copy(pillar = Pillar.Update, text = "News: {link}", link = true))

        publisher(template = WEBSITE_TEMPLATE).publishDue("pl")
        publisher(template = WEBSITE_TEMPLATE).publishDue("staging")

        val texts = threads.callsTo("createContainer").map { (it.args[2] as ContainerRequest).text }
        assertTrue("https://sirelon.github.io/SellSnap/?lang=pl&p=plt0001" in texts[0], texts[0])
        assertTrue("https://sirelon.github.io/SellSnap/?lang=pl&p=plt0001" in texts[1], texts[1])
        assertTrue("https://sirelon.github.io/SellSnap/?lang=uk&p=stt0001" in texts[2], texts[2])
        assertTrue(texts.none { "/go/" in it })
    }

    @Test
    fun rendersTheLinkAndTheIndependenceLine() = runTest {
        store.put(duePost().copy(pillar = Pillar.Update, text = "News: {link}", link = true))

        publisher().publishDue("staging")

        val request = threads.callsTo("createContainer").single().args[2] as ContainerRequest
        assertEquals(ContentRules.render("News: {link}", "https://go.test/go/stt0001", "uk"), request.text)
        assertTrue("https://go.test/go/stt0001" in request.text)
        assertTrue(ContentRules.INDEPENDENCE_LINE.getValue("uk") in request.text)
    }

    @Test
    fun buildsImageAndPollContainers() = runTest {
        store.put(duePost("stt0001").copy(kind = PostKind.Image, imagePaths = listOf("social/manual/stt0001.jpg")))
        store.put(duePost("stp0002").copy(kind = PostKind.Poll, pillar = Pillar.Poll, pollOptions = listOf("A", "B")))

        publisher().publishDue("staging")

        val requests = threads.callsTo("createContainer").map { it.args[2] as ContainerRequest }
        val image = requests.single { it.kind == PostKind.Image }
        assertEquals("https://images.test/social/manual/stt0001.jpg", image.imageUrl)
        assertEquals(listOf("A", "B"), requests.single { it.kind == PostKind.Poll }.pollOptions)
    }

    @Test
    fun skipsPostsThatAreNotDue() = runTest {
        store.put(testPost(scheduledAt = NOW.plusSeconds(60)))
        store.put(testPost(id = "stt0002", scheduledAt = null))

        val outcomes = publisher().publishDue("staging")

        assertTrue(outcomes.isEmpty())
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun publishesNothingWhenTheQuotaIsExhausted() = runTest {
        store.put(duePost())
        threads.limit = PublishingLimit(quotaUsage = 250, quotaTotal = 250)

        val outcome = publisher().publishDue("staging").single()

        assertEquals(PublishOutcome.Deferred(null, "staging", DeferReason.LimitExhausted), outcome)
        assertTrue(threads.callsTo("createContainer").isEmpty())
        assertEquals(PostStatus.Approved, post().status)
    }

    @Test
    fun stopsAtTheLastQuotaSlot() = runTest {
        store.put(duePost("stt0001"))
        store.put(duePost("stt0002"))
        threads.limit = PublishingLimit(quotaUsage = 249, quotaTotal = 250)

        val outcomes = publisher().publishDue("staging")

        assertEquals(1, outcomes.count { it is PublishOutcome.Published })
        assertEquals(1, threads.published.size)
        assertEquals(DeferReason.LimitExhausted, assertIs<PublishOutcome.Deferred>(outcomes.last()).reason)
    }

    @Test
    fun aPausedAccountOrOneWithoutATokenIsSkippedWithoutAnyCall() = runTest {
        store.put(duePost())
        store.put(account().copy(paused = true))
        assertEquals(
            DeferReason.AccountPaused,
            assertIs<PublishOutcome.Deferred>(publisher().publishDue("staging").single()).reason,
        )
        store.put(account().copy(paused = false, token = null))
        assertEquals(
            DeferReason.NoToken,
            assertIs<PublishOutcome.Deferred>(publisher().publishDue("staging").single()).reason,
        )
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun publishNowIgnoresTheSlot() = runTest {
        store.put(testPost(scheduledAt = NOW.plusSeconds(3600)))

        val outcome = publisher().publishNow("stt0001")

        assertIs<PublishOutcome.Published>(outcome)
        assertEquals(PostStatus.Published, post().status)
        assertEquals(
            DeferReason.NotPublishable,
            assertIs<PublishOutcome.Deferred>(publisher().publishNow("stt0001")).reason,
        )
        assertEquals(
            DeferReason.NotPublishable,
            assertIs<PublishOutcome.Deferred>(publisher().publishNow("missing")).reason,
        )
    }

    // Claim fencing

    @Test
    fun twoConcurrentPublishersMakeOneContainerAndOnePublish() = runTest {
        store.put(duePost())
        val gate = CompletableDeferred<Unit>()
        val api = HookedThreadsApi(threads).apply { beforeLimit = { gate.await() } }
        val first = async { publisher(api, "a").publishDue("staging") }
        val second = async { publisher(api, "b").publishDue("staging") }
        runCurrent()

        gate.complete(Unit)
        val outcomes = first.await() + second.await()

        assertEquals(1, threads.callsTo("createContainer").size)
        assertEquals(1, threads.callsTo("publish").size)
        assertEquals(1, outcomes.count { it is PublishOutcome.Published })
        val loser = outcomes.filterIsInstance<PublishOutcome.Deferred>().single()
        assertEquals(DeferReason.ClaimedElsewhere, loser.reason)
        assertEquals(1, account().publishedCount)
    }

    @Test
    fun aFreshClaimIsLeftAlone() = runTest {
        store.put(stalePost(containerId = "c1", claimedAgo = Duration.ofMinutes(14)))

        assertTrue(publisher().publishDue("staging").isEmpty())
        assertEquals(
            DeferReason.NotPublishable,
            assertIs<PublishOutcome.Deferred>(publisher().publishNow("stt0001")).reason,
        )

        assertTrue(threads.calls.none { it.method != "publishingLimit" })
        assertEquals("old", post().claimId)
    }

    @Test
    fun aStaleClaimIsTakenOver() = runTest {
        store.put(stalePost())

        val outcome = publisher().publishDue("staging").single()

        assertIs<PublishOutcome.Published>(outcome)
        assertEquals("claim-1", post().claimId)
        assertEquals(PostStatus.Published, post().status)
    }

    @Test
    fun aLateWriteFromAnOldClaimIsRejected() = runTest {
        store.put(duePost())
        val gate = CompletableDeferred<Unit>()
        var creates = 0
        val api = HookedThreadsApi(threads).apply { beforeCreate = { if (creates++ == 0) gate.await() } }
        val old = async { publisher(api, "old").publishDue("staging") }
        runCurrent()
        assertEquals("old-1", post().claimId)

        clock.advance(Duration.ofMinutes(16))
        val takeover = publisher(api, "new").publishDue("staging").single()
        assertIs<PublishOutcome.Published>(takeover)

        gate.complete(Unit)
        val late = old.await().single()

        assertEquals(PublishOutcome.ClaimLost("stt0001"), late)
        val saved = post()
        assertEquals("container-1", saved.containerId)
        assertEquals("new-2", saved.claimId)
        assertEquals(PostStatus.Published, saved.status)
        assertEquals(1, threads.callsTo("publish").size)
        assertEquals(1, account().publishedCount)
    }

    @Test
    fun aLateFailureFromAnOldClaimDoesNotPauseTheAccount() = runTest {
        store.put(duePost())
        val gate = CompletableDeferred<Unit>()
        var creates = 0
        val api = HookedThreadsApi(threads).apply {
            beforeCreate = {
                if (creates++ == 0) {
                    gate.await()
                    throw ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)")
                }
            }
        }
        val old = async { publisher(api, "old").publishDue("staging") }
        runCurrent()

        clock.advance(Duration.ofMinutes(16))
        publisher(api, "new").publishDue("staging")
        gate.complete(Unit)

        assertEquals(PublishOutcome.ClaimLost("stt0001"), old.await().single())
        assertFalse(account().paused)
        assertEquals(PostStatus.Published, post().status)
    }

    // Recovery by container state

    @Test
    fun noContainerCreatesOne() = runTest {
        store.put(stalePost(containerId = null))

        publisher().publishDue("staging")

        assertEquals(1, threads.callsTo("createContainer").size)
        assertEquals("container-1", threads.callsTo("publish").single().args[2])
    }

    @Test
    fun anInProgressContainerIsLeftAfterSixtySeconds() = runTest {
        store.put(stalePost(containerId = "c1"))
        repeat(20) { threads.containerStatuses += ContainerStatus(ContainerState.InProgress) }

        val outcome = publisher().publishDue("staging").single()

        assertEquals(DeferReason.ContainerInProgress, assertIs<PublishOutcome.Deferred>(outcome).reason)
        assertEquals(7, threads.callsTo("containerStatus").size)
        assertEquals(60_000, currentTime)
        assertTrue(threads.callsTo("publish").isEmpty())
        assertTrue(threads.callsTo("createContainer").isEmpty())
        val saved = post()
        assertEquals(PostStatus.Publishing, saved.status)
        assertEquals("c1", saved.containerId)
        assertEquals(0, saved.transientFailures)
        assertFalse(account().paused)
    }

    @Test
    fun aContainerThatFinishesWithinTheWaitIsPublished() = runTest {
        store.put(stalePost(containerId = "c1"))
        threads.containerStatuses += listOf(
            ContainerStatus(ContainerState.InProgress),
            ContainerStatus(ContainerState.InProgress),
        )

        val outcome = publisher().publishDue("staging").single()

        assertIs<PublishOutcome.Published>(outcome)
        assertEquals(20_000, currentTime)
        assertEquals("c1", threads.callsTo("publish").single().args[2])
    }

    @Test
    fun aFinishedContainerIsPublishedWithoutANewOne() = runTest {
        store.put(stalePost(containerId = "c1"))

        publisher().publishDue("staging")

        assertTrue(threads.callsTo("createContainer").isEmpty())
        assertEquals("c1", threads.callsTo("publish").single().args[2])
    }

    @Test
    fun aPublishedContainerRecoversTheMediaIdFromTheRecentThreads() = runTest {
        store.put(stalePost(containerId = "c1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Published)
        threads.recent = listOf(
            ThreadSummary("other", "Something else", NOW),
            ThreadSummary("m9", "Tip text", NOW.minusSeconds(120)),
            ThreadSummary("m8", "Tip text", NOW.minusSeconds(900)),
        )

        val outcome = publisher().publishDue("staging").single()

        assertEquals("m9", assertIs<PublishOutcome.Published>(outcome).mediaId)
        assertTrue(threads.callsTo("publish").isEmpty())
        assertEquals("m9", post().mediaId)
        assertEquals(PostStatus.Published, post().status)
        assertEquals(1, account().publishedCount)
    }

    @Test
    fun aPublishedContainerWithoutAMatchNeedsAttention() = runTest {
        store.put(stalePost(containerId = "c1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Published)

        val outcome = assertIs<PublishOutcome.Published>(publisher().publishDue("staging").single())

        assertNull(outcome.mediaId)
        assertTrue(outcome.needsAttention)
        assertEquals(AlertType.PostNeedsAttention, outcome.alerts.single().type)
        val saved = post()
        assertEquals(PostStatus.Published, saved.status)
        assertTrue(saved.needsAttention)
        assertNull(saved.mediaId)
        assertEquals(EventType.Error, store.events.getValue("recovery_stt0001").type)
    }

    @Test
    fun anErroredContainerIsPermanent() = runTest {
        store.put(stalePost(containerId = "c1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Error, "UNKNOWN")

        val outcome = assertIs<PublishOutcome.Paused>(publisher().publishDue("staging").single())

        assertEquals("Threads could not process the post (UNKNOWN)", outcome.reason)
        assertEquals(PostStatus.Failed, post().status)
        assertTrue(account().paused)
        assertTrue(threads.callsTo("publish").isEmpty())
    }

    @Test
    fun anExpiredContainerIsReplacedByANewOne() = runTest {
        store.put(stalePost(containerId = "c1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Expired)

        val outcome = publisher().publishDue("staging").single()

        assertIs<PublishOutcome.Published>(outcome)
        assertEquals(1, threads.callsTo("createContainer").size)
        assertEquals("container-1", threads.callsTo("publish").single().args[2])
        assertEquals("container-1", post().containerId)
    }

    @Test
    fun aRepeatedPublishChecksTheContainerFirstSoItNeverPublishesTwice() = runTest {
        store.put(duePost())
        threads.containerStatuses += listOf(
            ContainerStatus(ContainerState.Finished),
            ContainerStatus(ContainerState.Published),
        )
        threads.recent = listOf(ThreadSummary("m9", "Tip text", NOW))
        threads.failNext("publish", ThreadsFailure.Transient("temporary"))

        val outcome = publisher().publishDue("staging").single()

        assertEquals("m9", assertIs<PublishOutcome.Published>(outcome).mediaId)
        assertEquals(1, threads.callsTo("publish").size)
        assertEquals(1_000, currentTime)
    }

    // Failure classes

    @Test
    fun aTransientFailureRetriesThenLeavesThePostPublishing() = runTest {
        store.put(duePost())
        threads.failNext("createContainer", *Array(4) { ThreadsFailure.Transient("temporary") })

        val outcome = assertIs<PublishOutcome.Deferred>(publisher().publishDue("staging").single())

        assertEquals(DeferReason.TransientFailure, outcome.reason)
        assertEquals(4, threads.callsTo("createContainer").size)
        assertEquals(7_000, currentTime)
        val saved = post()
        assertEquals(PostStatus.Publishing, saved.status)
        assertEquals(1, saved.transientFailures)
        assertFalse(account().paused)
        assertTrue(outcome.alerts.isEmpty())
    }

    @Test
    fun threeTransientTicksInARowAlertAndASuccessResetsTheCount() = runTest {
        store.put(duePost())
        val alerts = mutableListOf<List<AlertRequest>>()
        repeat(3) {
            threads.failNext("createContainer", *Array(4) { ThreadsFailure.Transient("temporary") })
            alerts += publisher().publishDue("staging").single().alerts
            clock.advance(Duration.ofMinutes(61))
        }

        assertEquals(listOf(emptyList(), emptyList(), listOf(AlertType.PublishStalled)), alerts.map { list -> list.map { it.type } })
        assertEquals(3, post().transientFailures)

        val recovered = publisher().publishDue("staging").single()

        assertIs<PublishOutcome.Published>(recovered)
        assertEquals(0, post().transientFailures)
    }

    @Test
    fun aRateLimitIsNotRetriedAndDoesNotPause() = runTest {
        store.put(duePost())
        threads.failNext("createContainer", ThreadsFailure.RateLimited("Threads rate limit reached (HTTP 400, code 613)"))

        val outcome = publisher().publishDue("staging").single()

        assertEquals(DeferReason.RateLimited, assertIs<PublishOutcome.Deferred>(outcome).reason)
        assertEquals(1, threads.callsTo("createContainer").size)
        assertEquals(0, currentTime)
        assertFalse(account().paused)
        assertEquals(0, post().transientFailures)
        assertEquals(PostStatus.Publishing, post().status)
    }

    @Test
    fun aRateLimitedQuotaReadDefersTheWholeAccount() = runTest {
        store.put(duePost())
        threads.failNext("publishingLimit", ThreadsFailure.RateLimited("Threads rate limit reached (HTTP 400, code 4)"))

        val outcome = publisher().publishDue("staging").single()

        assertEquals(PublishOutcome.Deferred(null, "staging", DeferReason.RateLimited), outcome)
        assertEquals(PostStatus.Approved, post().status)
    }

    @Test
    fun aPermanentFailurePausesTheAccountFailsThePostAndAsksForAnAlert() = runTest {
        store.put(duePost("stt0001"))
        store.put(duePost("stt0002"))
        val message = "Threads rejected the request (HTTP 400, code 190)"
        threads.failNext("createContainer", ThreadsFailure.Permanent(190, message))

        val outcomes = publisher().publishDue("staging")

        val paused = assertIs<PublishOutcome.Paused>(outcomes.single())
        assertEquals(message, paused.reason)
        assertEquals(AlertRequest(AlertType.PublishFailed, "staging", "stt0001", message), paused.alerts.single())
        assertEquals(PostStatus.Failed, post("stt0001").status)
        assertEquals(PostStatus.Approved, post("stt0002").status)
        assertEquals(1, threads.callsTo("createContainer").size)
        assertEquals(0, currentTime)
        assertTrue(account().paused)
        assertEquals(message, account().pausedReason)
        val event = store.events.getValue("pause_stt0001_claim-1")
        assertEquals(EventType.Pause, event.type)
        assertEquals("stt0001", event.postId)

        val next = publisher().publishDue("staging").single()
        assertEquals(DeferReason.AccountPaused, assertIs<PublishOutcome.Deferred>(next).reason)
        assertEquals(1, threads.callsTo("createContainer").size)
    }

    @Test
    fun aPermanentQuotaReadPausesTheAccountWithoutAPost() = runTest {
        store.put(duePost())
        threads.failNext("publishingLimit", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))

        val outcome = assertIs<PublishOutcome.Paused>(publisher().publishDue("staging").single())

        assertNull(outcome.postId)
        assertTrue(account().paused)
        assertEquals(PostStatus.Approved, post().status)
    }

    // Secrets

    @Test
    fun aReasonThatEchoesATokenIsRedactedEverywhere() = runTest {
        store.put(duePost())
        threads.failNext(
            "createContainer",
            ThreadsFailure.Permanent(190, "bad request ?access_token=$TOKEN&client_secret=$APP_SECRET"),
        )

        val outcomes = publisher().publishDue("staging")

        val text = dump(outcomes)
        assertFalse(TOKEN in text, text)
        assertFalse(APP_SECRET in text, text)
        assertTrue("access_token=[redacted]" in account().pausedReason.orEmpty())
    }

    @Test
    fun noFailurePathOverTheRealClientLeaksATokenOrSecret() = runTest {
        val leaky = """{"error":{"message":"bad $TOKEN $APP_SECRET","code":CODE}}"""
        for ((status, code) in listOf(400 to 190, 500 to 2, 400 to 613, 400 to 368)) {
            val localStore = InMemorySocialStore()
            localStore.put(testAccount().copy(token = TOKEN, threadsUserId = "u1"))
            localStore.put(duePost())
            val engine = MockEngine { request ->
                val ok = request.method == HttpMethod.Get
                val body = if (ok) """{"data":[{"quota_usage":0,"config":{"quota_total":250}}]}""" else leaky.replace("CODE", "$code")
                respond(body, if (ok) HttpStatusCode.OK else HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val api = ThreadsHttpClient(HttpClient(engine) { expectSuccess = false }, APP_SECRET)

            val outcomes = Publisher(localStore, api, FakeImageStore(), clock, LINK_TEMPLATE, newClaimId = { "c" })
                .publishDue("staging")

            val text = outcomes.toString() + localStore.allPosts + localStore.events +
                localStore.allAccounts.values.map { it.copy(token = null) }
            assertFalse(TOKEN in text, "token leaked for $status/$code: $text")
            assertFalse(APP_SECRET in text, "secret leaked for $status/$code: $text")
            assertFalse("graph.threads.net" in text, "url leaked for $status/$code: $text")
        }
    }


    // Link replies

    private val replyTemplate = "Get it: {link}"

    private fun renderedReply(code: String = "stt0001") =
        ContentRules.render(replyTemplate, "https://go.test/go/$code", "uk")

    private fun withReply(post: SocialPost) = post.copy(replyText = replyTemplate)

    /** A live post whose reply is owed; its claim is stale unless [claimedAgo] says otherwise. */
    private fun livePost(
        replyContainerId: String? = null,
        claimedAgo: Duration = Duration.ofMinutes(16),
        id: String = "stt0001",
    ) = testPost(id = id, status = PostStatus.Published).copy(
        mediaId = "m1",
        replyText = replyTemplate,
        replyContainerId = replyContainerId,
        claimId = "old",
        publishingClaimedAt = NOW.minus(claimedAgo),
    )

    @Test
    fun publishesTheReplyUnderThePostOnceThePostIsLive() = runTest {
        store.put(withReply(duePost()))

        val outcome = assertIs<PublishOutcome.Published>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Published, outcome.reply)
        val containers = threads.callsTo("createContainer").map { it.args[2] as ContainerRequest }
        assertEquals(2, containers.size)
        assertNull(containers[0].replyToId)
        assertEquals("media-2", containers[1].replyToId)
        assertEquals(PostKind.Text, containers[1].kind)
        assertEquals(renderedReply(), containers[1].text)
        assertTrue(ContentRules.INDEPENDENCE_LINE.getValue("uk") in containers[1].text)
        val saved = post()
        assertEquals(PostStatus.Published, saved.status)
        assertEquals("media-2", saved.mediaId)
        assertEquals("container-3", saved.replyContainerId)
        assertEquals("media-4", saved.replyMediaId)
        assertEquals(1, account().publishedCount)
        assertEquals(1, threads.callsTo("publishingLimit").size)
    }

    @Test
    fun savesTheReplyContainerIdBeforeItPublishesTheReply() = runTest {
        store.put(withReply(duePost()))

        publisher().publishDue("staging")

        val atReplyPublish = threads.callsTo("publish")[1].posts.getValue("stt0001")
        assertEquals("container-3", atReplyPublish.replyContainerId)
        assertNull(atReplyPublish.replyMediaId)
        assertEquals("claim-1", atReplyPublish.claimId)
        assertEquals(PostStatus.Published, atReplyPublish.status)
    }

    @Test
    fun aPostWithoutAReplyMakesNoReplyCall() = runTest {
        store.put(duePost())

        val outcome = assertIs<PublishOutcome.Published>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.None, outcome.reply)
        assertEquals(1, threads.callsTo("createContainer").size)
    }

    @Test
    fun aPostThatFailsPermanentlyGetsNoReply() = runTest {
        store.put(withReply(duePost()))
        threads.failNext("createContainer", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))

        assertIs<PublishOutcome.Paused>(publisher().publishDue("staging").single())

        assertEquals(1, threads.callsTo("createContainer").size)
        assertNull(post().replyContainerId)
    }

    @Test
    fun publishNowPublishesTheReplyInTheSameRequest() = runTest {
        store.put(withReply(testPost()))

        val outcome = assertIs<PublishOutcome.Published>(publisher().publishNow("stt0001"))

        assertEquals(ReplyState.Published, outcome.reply)
        assertEquals("media-4", post().replyMediaId)
    }

    @Test
    fun aFinishedReplyContainerIsPublishedWithoutANewOne() = runTest {
        store.put(livePost(replyContainerId = "rc1"))

        val outcome = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Published, outcome.state)
        assertTrue(threads.callsTo("createContainer").isEmpty())
        assertEquals("rc1", threads.callsTo("publish").single().args[2])
        assertTrue(threads.callsTo("publishingLimit").isEmpty(), "replies have their own quota")
        val saved = post()
        assertEquals("media-1", saved.replyMediaId)
        assertEquals(PostStatus.Published, saved.status)
        assertEquals(0, account().publishedCount)
    }

    @Test
    fun aPublishedPostWhoseReplyWasNeverStartedGetsAContainerOnALaterTick() = runTest {
        store.put(livePost())

        val outcome = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Published, outcome.state)
        val request = threads.callsTo("createContainer").single().args[2] as ContainerRequest
        assertEquals("m1", request.replyToId)
        assertEquals("container-1", post().replyContainerId)
        assertEquals("media-2", post().replyMediaId)
    }

    @Test
    fun aReplyContainerPublishedBeforeACrashRecoversItsMediaIdFromTheRecentThreads() = runTest {
        store.put(livePost(replyContainerId = "rc1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Published)
        threads.recent = listOf(
            ThreadSummary("m1", "Tip text", NOW.minusSeconds(900)),
            ThreadSummary("r9", renderedReply(), NOW.minusSeconds(120)),
        )

        val outcome = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Published, outcome.state)
        assertTrue(threads.callsTo("publish").isEmpty())
        assertEquals("r9", post().replyMediaId)
        assertFalse(post().needsAttention)
    }

    @Test
    fun aReplyPublishedBeforeACrashWithoutAMatchNeedsAttention() = runTest {
        store.put(livePost(replyContainerId = "rc1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Published)

        val outcome = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Failed, outcome.state)
        assertEquals(AlertType.ReplyFailed, outcome.alerts.single().type)
        val saved = post()
        assertTrue(saved.replyFailed)
        assertNull(saved.replyMediaId)
        assertEquals(PostStatus.Published, saved.status)
        assertFalse(account().paused)
        assertEquals(EventType.Error, store.events.getValue("recovery_reply_stt0001").type)
    }

    @Test
    fun anExpiredReplyContainerIsReplacedByANewOne() = runTest {
        store.put(livePost(replyContainerId = "rc1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Expired)

        assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        val request = threads.callsTo("createContainer").single().args[2] as ContainerRequest
        assertEquals("m1", request.replyToId)
        assertEquals("container-1", post().replyContainerId)
        assertNotNull(post().replyMediaId)
    }

    @Test
    fun aFreshReplyClaimIsLeftAlone() = runTest {
        store.put(livePost(replyContainerId = "rc1", claimedAgo = Duration.ofMinutes(1)))

        val outcomes = publisher().publishDue("staging")

        assertTrue(outcomes.isEmpty())
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun twoConcurrentTicksPublishTheReplyOnce() = runTest {
        store.put(livePost(replyContainerId = "rc1"))
        val first = async { publisher(prefix = "a").publishDue("staging") }
        val second = async { publisher(prefix = "b").publishDue("staging") }

        val outcomes = first.await() + second.await()

        assertEquals(1, threads.callsTo("publish").size)
        assertEquals(1, outcomes.size)
    }

    @Test
    fun aLateWriteFromAnOldReplyClaimIsRejected() = runTest {
        store.put(withReply(duePost()))
        val hooked = HookedThreadsApi(threads)
        hooked.beforeReplyCreate = {
            // Another publisher takes the reply claim while this one is in the Threads call.
            store.put(post().copy(claimId = "other", publishingClaimedAt = NOW))
        }

        val outcome = assertIs<PublishOutcome.Published>(publisher(hooked).publishDue("staging").single())

        assertEquals(ReplyState.Deferred, outcome.reply)
        assertNull(post().replyContainerId)
        assertEquals("other", post().claimId)
        assertTrue(threads.callsTo("publish").size == 1, "only the post was published")
    }

    @Test
    fun aPermanentReplyFailureKeepsThePostPublishedAlertsAndDoesNotPause() = runTest {
        store.put(withReply(duePost()))
        val message = "Threads rejected the request (HTTP 400, code 10)"
        threads.failNextReply(ThreadsFailure.Permanent(10, message))

        val outcome = assertIs<PublishOutcome.Published>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Failed, outcome.reply)
        assertEquals(AlertRequest(AlertType.ReplyFailed, "staging", "stt0001", message), outcome.alerts.single())
        val saved = post()
        assertEquals(PostStatus.Published, saved.status)
        assertEquals("media-2", saved.mediaId)
        assertTrue(saved.replyFailed)
        assertFalse(saved.needsAttention)
        assertNull(saved.replyMediaId)
        assertFalse(account().paused)
        assertEquals(1, account().publishedCount)
        assertEquals(EventType.Error, store.events.getValue("reply_failed_stt0001_claim-1").type)

        // The reply is given up on: the next tick makes no more calls for it.
        clock.advance(Duration.ofMinutes(61))
        assertTrue(publisher().publishDue("staging").isEmpty())
        assertEquals(2, threads.callsTo("createContainer").size)
    }

    @Test
    fun anErroredReplyContainerIsAPermanentReplyFailure() = runTest {
        store.put(livePost(replyContainerId = "rc1"))
        threads.containerStatuses += ContainerStatus(ContainerState.Error, "UNKNOWN")

        val outcome = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Failed, outcome.state)
        assertTrue(post().replyFailed)
        assertFalse(account().paused)
        assertEquals("Threads could not process the reply (UNKNOWN)", outcome.alerts.single().detail)
    }

    @Test
    fun aDraftFlaggedNeedsAttentionStillGetsItsReply() = runTest {
        // A review flag from the drafter's checks must not stop the link reply (R2 item 12a).
        store.put(livePost().copy(needsAttention = true, checkNotes = "Unsupported product claim: x"))

        val outcome = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Published, outcome.state)
        assertNotNull(post().replyMediaId)
        assertTrue(post().needsAttention)
        assertFalse(post().replyFailed)
    }

    @Test
    fun replyFailedStopsReplyRetriesWhileNeedsAttentionAloneDoesNot() = runTest {
        store.put(livePost(id = "stt0001").copy(replyFailed = true))
        store.put(livePost(id = "stt0002").copy(needsAttention = true))

        val outcomes = publisher().publishDue("staging")

        assertEquals("stt0002", assertIs<PublishOutcome.Replied>(outcomes.single()).postId)
        assertNull(post("stt0001").replyMediaId)
        assertNotNull(post("stt0002").replyMediaId)
        assertEquals(1, threads.callsTo("createContainer").size)
    }

    @Test
    fun aTransientReplyFailureIsRetriedByTheNextTick() = runTest {
        store.put(withReply(duePost()))
        threads.failNextReply(*Array(4) { ThreadsFailure.Transient("temporary") })

        val first = assertIs<PublishOutcome.Published>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Deferred, first.reply)
        assertTrue(first.alerts.isEmpty())
        val afterFirst = post()
        assertEquals(PostStatus.Published, afterFirst.status)
        assertNull(afterFirst.replyMediaId)
        assertFalse(afterFirst.needsAttention)
        assertEquals(1, afterFirst.transientFailures)
        assertFalse(account().paused)

        clock.advance(Duration.ofMinutes(61))
        val second = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Published, second.state)
        assertNotNull(post().replyMediaId)
        assertEquals(0, post().transientFailures)
    }

    @Test
    fun aRateLimitedReplyIsRetriedByTheNextTickWithoutACountOrAlert() = runTest {
        store.put(livePost())
        threads.failNextReply(ThreadsFailure.RateLimited("limit"))

        val first = assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single())

        assertEquals(ReplyState.Deferred, first.state)
        assertTrue(first.alerts.isEmpty())
        assertEquals(1, threads.callsTo("createContainer").size)
        assertEquals(0, post().transientFailures)

        clock.advance(Duration.ofMinutes(61))
        assertEquals(ReplyState.Published, assertIs<PublishOutcome.Replied>(publisher().publishDue("staging").single()).state)
    }

    @Test
    fun threeTransientReplyTicksInARowRaiseTheStalledAlert() = runTest {
        store.put(livePost())
        val alerts = mutableListOf<List<AlertType>>()
        repeat(3) {
            threads.failNextReply(*Array(4) { ThreadsFailure.Transient("temporary") })
            alerts += publisher().publishDue("staging").single().alerts.map { it.type }
            clock.advance(Duration.ofMinutes(61))
        }

        assertEquals(listOf(emptyList(), emptyList(), listOf(AlertType.PublishStalled)), alerts)
    }

    @Test
    fun aPausedAccountPublishesNoReplies() = runTest {
        store.put(livePost())
        store.put(account().copy(paused = true))

        val outcome = assertIs<PublishOutcome.Deferred>(publisher().publishDue("staging").single())

        assertEquals(DeferReason.AccountPaused, outcome.reason)
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun theReplyWaitsWhileThePostItselfIsRateLimited() = runTest {
        store.put(withReply(duePost("stt0002")))
        store.put(livePost(id = "stt0001"))
        threads.failNext("createContainer", ThreadsFailure.RateLimited("limit"))

        val outcomes = publisher().publishDue("staging")

        assertEquals(DeferReason.RateLimited, assertIs<PublishOutcome.Deferred>(outcomes.single()).reason)
        assertEquals(1, threads.callsTo("createContainer").size)
    }

    @Test
    fun aReplyOfAPostWithoutAMediaIdIsNotAttempted() = runTest {
        store.put(livePost().copy(mediaId = null, needsAttention = true))

        assertTrue(publisher().publishDue("staging").isEmpty())
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun noReplyFailurePathOverTheRealClientLeaksATokenOrSecret() = runTest {
        val leaky = """{"error":{"message":"bad $TOKEN $APP_SECRET","code":10}}"""
        val localStore = InMemorySocialStore()
        localStore.put(testAccount().copy(token = TOKEN, threadsUserId = "u1"))
        localStore.put(livePost())
        val engine = MockEngine { respond(leaky, HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json")) }
        val api = ThreadsHttpClient(HttpClient(engine) { expectSuccess = false }, APP_SECRET)

        val outcomes = Publisher(localStore, api, FakeImageStore(), clock, LINK_TEMPLATE, newClaimId = { "c" })
            .publishDue("staging")

        val text = outcomes.toString() + localStore.allPosts + localStore.events +
            localStore.allAccounts.values.map { it.copy(token = null) }
        assertFalse(TOKEN in text, text)
        assertFalse(APP_SECRET in text, text)
        assertFalse("graph.threads.net" in text, text)
        assertTrue(localStore.allPosts.getValue("stt0001").replyFailed)
    }

    private class HookedThreadsApi(private val delegate: FakeThreadsApi) : ThreadsApi by delegate {
        var beforeLimit: suspend () -> Unit = {}
        var beforeCreate: suspend () -> Unit = {}

        /** Runs before a reply container is created; `beforeCreate` runs before every container. */
        var beforeReplyCreate: suspend () -> Unit = {}

        override suspend fun publishingLimit(userId: String, token: String): PublishingLimit {
            beforeLimit()
            return delegate.publishingLimit(userId, token)
        }

        override suspend fun createContainer(userId: String, token: String, request: ContainerRequest): String {
            beforeCreate()
            if (request.replyToId != null) beforeReplyCreate()
            return delegate.createContainer(userId, token, request)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-20T09:00:00Z")
        const val TOKEN = "SECRET-TOKEN-123"
        const val LINK_TEMPLATE = "https://go.test/go/{code}"
        const val WEBSITE_TEMPLATE = "https://sirelon.github.io/SellSnap/?lang={lang}&p={code}"
        const val APP_SECRET = "APP-SECRET-456"
    }
}
