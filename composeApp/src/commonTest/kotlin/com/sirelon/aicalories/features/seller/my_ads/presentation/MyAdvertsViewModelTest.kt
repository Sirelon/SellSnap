package com.sirelon.sellsnap.features.seller.my_ads.presentation

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.location.DeviceLocation
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertLifecycleRepository
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Covers the SIR-87 account pager: each [MyAdvertsContract.AccountPage] loads, pages, errors and
 * retries independently, addressed by its own explicit-token fetch rather than the shared
 * authorized client (see MyAdvertsRepository/SellerAccountRepository.accessTokenFor). Everything -
 * the ViewModel's own `viewModelScope` coroutines and the mock HTTP engine's dispatch - is pinned
 * to the same [StandardTestDispatcher] so tests can deterministically control exactly when a
 * request resolves relative to another ViewModel event, with no real threads or timing races
 * involved.
 */
class MyAdvertsViewModelTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `two connected accounts produce two stable pages with the active account preselected and loaded`() = runTest(testDispatcher) {
        val requestLog = mutableListOf<LoggedRequest>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                requestLog += request.toLogged()
                respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(1, 2), state.pages.map { it.localIndex })
        assertEquals(1, state.selectedLocalIndex)
        assertTrue(state.pages.first { it.localIndex == 1 }.isActiveAccount)
        assertEquals(false, state.pages.first { it.localIndex == 2 }.isActiveAccount)

        // Only the initially-selected page (the active account) fetches on screen entry - never
        // all connected accounts at once.
        assertEquals(1, requestLog.size)
        assertEquals("Bearer token-1", requestLog.single().authHeader)
    }

    @Test
    fun `selecting a second page fetches with that account's own token and leaves the first page untouched`() = runTest(testDispatcher) {
        val requestLog = mutableListOf<LoggedRequest>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                requestLog += request.toLogged()
                val id = if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") 222L else 111L
                respond(advertsJson(id), status = HttpStatusCode.OK, headers = jsonHeaders())
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()
        val page1AdvertsBeforeSwitch = viewModel.state.value.pages.first { it.localIndex == 1 }.adverts

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.selectedLocalIndex)
        assertEquals(listOf(222L), state.pages.first { it.localIndex == 2 }.adverts.map { it.id })
        assertEquals(page1AdvertsBeforeSwitch, state.pages.first { it.localIndex == 1 }.adverts)
        assertEquals("Bearer token-2", requestLog.last().authHeader)
    }

    @Test
    fun `a failure on one page does not affect another page's state`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") {
                    respond("", status = HttpStatusCode.InternalServerError)
                } else {
                    respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        // The failure branch resolves an error string via getString(), whose loader runs on
        // Compose Resources' own AsyncCache scope (real Dispatchers.Default, not this virtual
        // test dispatcher - see ResourceCaches.kt) - advanceUntilIdle() alone can't observe it.
        awaitPageSettled(viewModel, localIndex = 2)

        val page1 = viewModel.state.value.pages.first { it.localIndex == 1 }
        val page2 = viewModel.state.value.pages.first { it.localIndex == 2 }
        assertNull(page1.errorMessage)
        assertEquals(listOf(111L), page1.adverts.map { it.id })
        assertNotNull(page2.errorMessage)
        assertEquals(emptyList(), page2.adverts)
    }

    @Test
    fun `an InvalidToken response triggers exactly one forced refresh and the retry uses the refreshed token`() = runTest(testDispatcher) {
        var advertsCallCount = 0
        var refreshCallCount = 0
        val advertsAuthHeaders = mutableListOf<String?>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains(OlxConfig.authTokenPath)) {
                    refreshCallCount += 1
                    respond(refreshTokenJson("token-1-refreshed"), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    advertsCallCount += 1
                    advertsAuthHeaders += request.headers[HttpHeaders.Authorization]
                    if (advertsCallCount == 1) {
                        respond("", status = HttpStatusCode.Unauthorized)
                    } else {
                        respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }
                }
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        assertEquals(1, refreshCallCount)
        assertEquals(2, advertsCallCount)
        assertEquals(listOf<String?>("Bearer token-1", "Bearer token-1-refreshed"), advertsAuthHeaders)
        assertEquals(listOf(111L), viewModel.state.value.pages.single().adverts.map { it.id })
    }

    @Test
    fun `a terminal invalid_grant refresh failure marks only that account NeedsReconnect`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains(OlxConfig.authTokenPath)) {
                    respond("""{"error":"invalid_grant"}""", status = HttpStatusCode.BadRequest, headers = jsonHeaders())
                } else {
                    respond(advertsJson(222L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                // Already expired, so the first accessTokenFor call refreshes proactively and
                // hits the invalid_grant response above - no need to manufacture a prior 401.
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1", issuedAtEpochSeconds = 0),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        val page1 = viewModel.state.value.pages.first { it.localIndex == 1 }
        assertTrue(page1.needsReconnect)
        assertEquals(false, page1.isLoading)
        assertEquals(emptyList(), page1.adverts)

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()

        val page2 = viewModel.state.value.pages.first { it.localIndex == 2 }
        assertEquals(false, page2.needsReconnect)
        assertEquals(listOf(222L), page2.adverts.map { it.id })
    }

    @Test
    fun `an external active-account switch moves the isActiveAccount badge without changing the selected page`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders()) }
        }

        val (viewModel, harness) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()
        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()

        harness.repository.setActiveAccount("ua", 2)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.selectedLocalIndex)
        assertTrue(state.pages.first { it.localIndex == 2 }.isActiveAccount)
        assertEquals(false, state.pages.first { it.localIndex == 1 }.isActiveAccount)
    }

    @Test
    fun `disconnecting the selected account drops its page and any in-flight result for it`() = runTest(testDispatcher) {
        val staleRequestGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") {
                    // Never resolves until the test releases it below - the "in-flight" load for
                    // the page that gets disconnected out from underneath it.
                    staleRequestGate.await()
                    respond(advertsJson(222L), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }

        val (viewModel, harness) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()
        assertEquals(2, viewModel.state.value.selectedLocalIndex)

        harness.repository.disconnectAccount("ua", 2)
        runCurrent()

        val stateAfterDisconnect = viewModel.state.value
        assertEquals(listOf(1), stateAfterDisconnect.pages.map { it.localIndex })
        assertEquals(1, stateAfterDisconnect.selectedLocalIndex)

        // Let the stale request for the now-vanished page 2 finally resolve - it must be dropped
        // (updatePage is a no-op for a localIndex that no longer exists) rather than resurrecting it.
        staleRequestGate.complete(Unit)
        runCurrent()

        assertEquals(listOf(1), viewModel.state.value.pages.map { it.localIndex })
    }

    // --- test harness -----------------------------------------------------------------------

    /**
     * A failed page load resolves its error message via `getString`, which Compose Resources
     * loads through its own `AsyncCache` background scope - a real `Dispatchers.Default` job
     * entirely outside this test's virtual [StandardTestDispatcher], so [runCurrent] can never
     * observe its completion. Polls with a genuine (real-time) short delay on [Dispatchers.Default]
     * between attempts so that background job actually gets to run, bounded so a real regression
     * still fails the test instead of hanging.
     */
    private suspend fun TestScope.awaitPageSettled(viewModel: MyAdvertsViewModel, localIndex: Int, maxAttempts: Int = 200) {
        repeat(maxAttempts) {
            runCurrent()
            val page = viewModel.state.value.pages.find { it.localIndex == localIndex }
            if (page != null && !page.isLoading && !page.isLoadingMore) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        error("Page $localIndex did not settle in time")
    }

    // ----- Ad lifecycle (SIR-101/102/103/104/106) -----

    @Test
    fun `opening a live advert offers only the actions OLX accepts and loads its statistics once`() = runTest(testDispatcher) {
        val requestLog = mutableListOf<LoggedRequest>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                requestLog += request.toLogged()
                if (request.url.encodedPath.endsWith("/statistics")) {
                    respond(statisticsJson(212, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()

        val sheet = assertNotNull(viewModel.state.value.advertSheet)
        // Ukraine: OLX rejects `extend` here, so it must not appear as a button - a seller who
        // taps it would only ever get a server error back.
        assertEquals(listOf(AdvertAction.Edit, AdvertAction.Deactivate, AdvertAction.Delete), sheet.actions)
        assertTrue(sheet.extendUnavailableHere)
        assertEquals(212, assertNotNull(sheet.statistics).advertViews)
        // Statistics are fetched on open, not per row: one call for the one advert opened.
        assertEquals(1, requestLog.count { it.path.endsWith("/statistics") })
    }

    @Test
    fun `deactivating asks whether it sold and sends the answer OLX requires`() = runTest(testDispatcher) {
        val commandBodies = mutableListOf<String>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                when {
                    request.url.encodedPath.endsWith("/commands") -> {
                        commandBodies += (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    request.url.encodedPath.endsWith("/statistics") ->
                        respond(statisticsJson(1, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    // GET adverts/{id} - the row is re-read after the command rather than patched.
                    request.url.encodedPath.contains("/adverts/111") ->
                        respond(advertJson(111L, "removed_by_user"), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val outcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)
        val analytics = FakeAnalytics()
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            outcomeStore = outcomeStore,
            analytics = analytics,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Deactivate))
        runCurrent()

        // No OLX call yet: `is_success` is a required field, so the question has to be answered
        // before a take-down can even be attempted.
        assertNotNull(viewModel.state.value.soldPrompt)
        assertEquals(0, commandBodies.size)

        viewModel.onEvent(Event.SoldAnswered(isSold = true))
        runCurrent()
        assertTrue(assertNotNull(viewModel.state.value.soldPrompt).askingPrice)

        viewModel.onEvent(Event.SoldPriceSubmitted(price = 1500))
        runCurrent()

        assertEquals(1, commandBodies.size)
        assertTrue(commandBodies.single().contains("\"command\":\"deactivate\""))
        assertTrue(commandBodies.single().contains("\"is_success\":true"))

        // The outcome is what the milestone exists to collect, so it must survive the action.
        val outcome = assertNotNull(outcomeStore.outcomeFor(111L))
        assertEquals(true, outcome.isSold)
        assertEquals(1500L, outcome.achievedPrice)

        assertNull(viewModel.state.value.soldPrompt)
        assertEquals(true, analytics.paramsFor(AnalyticsEvents.ADVERT_SOLD)?.get("had_price_entered"))
        assertEquals("success", analytics.paramsFor(AnalyticsEvents.ADVERT_ACTION)?.get("result"))
    }

    @Test
    fun `answering not sold closes the listing without asking anything else`() = runTest(testDispatcher) {
        val commandBodies = mutableListOf<String>()
        // OLX keeps reporting the OLD status for a moment after accepting the command. This mock
        // never stops saying `active`, which is the case that used to leave the badge unchanged
        // and offer Deactivate a second time.
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                when {
                    request.url.encodedPath.endsWith("/commands") -> {
                        commandBodies += (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    request.url.encodedPath.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L, status = "active"), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val outcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)
        val analytics = FakeAnalytics()
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            outcomeStore = outcomeStore,
            analytics = analytics,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Deactivate))
        runCurrent()
        viewModel.onEvent(Event.SoldAnswered(isSold = false))
        runCurrent()

        // A seller closing a listing that failed gets no follow-up form.
        assertNull(viewModel.state.value.soldPrompt)
        assertTrue(commandBodies.single().contains("\"is_success\":false"))
        assertEquals(false, assertNotNull(outcomeStore.outcomeFor(111L)).isSold)
        assertNotNull(analytics.paramsFor(AnalyticsEvents.ADVERT_CLOSED_UNSOLD))

        // The row takes the status the command implies, even though OLX is still reporting
        // `active` - otherwise the badge does not change and the seller is offered Deactivate on
        // a listing they have already taken down.
        val row = viewModel.state.value.pages.single().adverts.single()
        assertEquals(AdvertStatus.RemovedByUser, row.status)
        // And so reopening the sheet offers what you do to a listing that is down.
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = row))
        runCurrent()
        assertEquals(
            listOf(AdvertAction.Edit, AdvertAction.Reactivate, AdvertAction.Delete),
            assertNotNull(viewModel.state.value.advertSheet).actions,
        )
    }

    @Test
    fun `deleting a live listing deactivates first and says so plainly when only that half lands`() = runTest(testDispatcher) {
        val calls = mutableListOf<String>()
        // The deactivate leg lands, so OLX now reports it inactive through the list; the delete
        // leg does not.
        var listStatus = "active"
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                calls += "${request.method.value} $path"
                when {
                    path.endsWith("/commands") -> {
                        listStatus = "removed_by_user"
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(5, 1, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    request.method == io.ktor.http.HttpMethod.Delete -> respond(
                        // OLX rejects a delete it does not consider valid, with its own reason.
                        """{"error":{"status":400,"title":"Invalid request","validation":[{"field":"ad","title":"Invalid status"}]}}""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders(),
                    )

                    else -> respond(advertsJson(111L, status = listStatus), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Delete))
        runCurrent()
        assertNotNull(viewModel.state.value.actionConfirm)

        viewModel.onEvent(Event.ActionConfirmed)
        runCurrent()
        // Deleting a live advert still has to answer "did it sell?", because OLX only accepts a
        // delete once the advert is inactive.
        assertNotNull(viewModel.state.value.soldPrompt)

        viewModel.onEvent(Event.SoldAnswered(isSold = false))

        // Awaited, not drained: the message is resolved through compose-resources `getString`.
        // The list refetch is kicked off after it, so drain once more for that.
        viewModel.effects.awaitEffect<MyAdvertsContract.Effect.ShowMessage>()
        advanceUntilIdle()

        assertTrue(calls.any { it.startsWith("POST") && it.endsWith("/commands") })
        assertTrue(calls.any { it.startsWith("DELETE") })
        // The listing is down but still on OLX. It must NOT be dropped from the list as if the
        // delete had succeeded - the seller has to be told the truth and be able to retry.
        assertEquals(1, viewModel.state.value.pages.single().adverts.size)
        assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.pages.single().adverts.single().status)
    }

    @Test
    fun `a rejected action quotes OLX's own reason and re-reads the list`() = runTest(testDispatcher) {
        var listReads = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> respond(
                        """{"error":{"status":400,"title":"Invalid request","validation":[{"field":"ad","title":"Ad has to be active"}]}}""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders(),
                    )

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> {
                        // Active for the load the sheet is opened from, so Deactivate is a real
                        // offered action; every read after OLX rejects it reports what the seller
                        // evidently could not see - the sheet was working from a stale status.
                        val status = if (listReads == 0) "active" else "removed_by_user"
                        listReads++
                        respond(advertsJson(111L, status = status), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }
                }
            }
        }
        val analytics = FakeAnalytics()
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            analytics = analytics,
        )
        runCurrent()
        val readsAfterFirstLoad = listReads

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Deactivate))
        runCurrent()
        viewModel.onEvent(Event.SoldAnswered(isSold = false))

        // Awaited on the state: the reason now lands inside the sheet, not as a snackbar.
        viewModel.state.first { it.advertSheet?.errorMessage != null }
        advanceUntilIdle()

        // `rejected` (rather than `failed`) is the health signal for the status-to-action mapping:
        // a pattern of these against one status means a seller is being offered a dead button.
        assertEquals("rejected", analytics.paramsFor(AnalyticsEvents.ADVERT_ACTION)?.get("result"))
        // OLX resolved the status differently from what the app believed, so the list is refetched
        // and the stale row corrected - otherwise the seller is offered the dead button again.
        assertTrue(listReads > readsAfterFirstLoad, "expected the list to be refetched")
        assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.pages.single().adverts.single().status)
        val sheet = assertNotNull(viewModel.state.value.advertSheet)
        assertNull(sheet.pendingAction)
        // The sheet was opened while the row read Active, offering Deactivate. OLX's rejection
        // means that was already stale - the sheet must show what the refetch found, not what it
        // was opened with, or the seller is offered the same dead button again.
        assertEquals(AdvertStatus.RemovedByUser, sheet.advert.status)
        assertEquals(listOf(AdvertAction.Edit, AdvertAction.Reactivate, AdvertAction.Delete), sheet.actions)
    }

    @Test
    fun `editing sends back every field OLX returned, changing only what the seller touched`() = runTest(testDispatcher) {
        var putBody: String? = null
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method == io.ktor.http.HttpMethod.Put -> {
                        putBody = (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(30, 2, 1), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") -> respond(
                        fullAdvertJson(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )

                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val analytics = FakeAnalytics()
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            analytics = analytics,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()

        val edit = assertNotNull(viewModel.state.value.advertEdit)
        // The description exists only on GET adverts/{id}; the list call never returns it.
        assertEquals("Worn twice.", edit.description)
        assertEquals(1800L, edit.priceValue)

        viewModel.onEvent(Event.EditSubmitted(title = edit.title, description = edit.description, price = 1500))
        runCurrent()

        val body = assertNotNull(putBody)
        // The price the seller typed.
        assertTrue(body.contains("\"value\":1500"), body)
        // Every field PUT requires is present, including `location`, which OLX returns nested
        // inside `contact` but requires at the top level - the mismatch that made every edit fail.
        for (required in listOf("title", "description", "category_id", "advertiser_type", "contact", "location", "attributes")) {
            assertTrue("\"$required\"" in body, "$required is required by PUT: $body")
        }
        assertTrue(body.contains("\"city_id\":1234"), body)
        // Optional settings the advert has are carried through, so an edit does not cost them.
        assertTrue(body.contains("\"code\":\"condition\""), body)
        assertTrue(body.contains("\"product_safety_regulation\""), body)
        // `location` must not also remain nested in `contact`, where the form does not model it.
        assertTrue(!body.contains("\"phone\":\"+380501112233\",\"location\""), body)
        // Response-only keys never reach PUT, and `auto_extend_enabled` is the one field
        // documented as unchanged when omitted, so it is not sent at all.
        for (absent in listOf("valid_to", "activated_at", "\"status\"", "auto_extend_enabled")) {
            assertTrue(absent !in body, "$absent must not be sent: $body")
        }

        assertEquals(true, analytics.paramsFor(AnalyticsEvents.ADVERT_EDITED)?.get("was_price_only"))
        assertNull(viewModel.state.value.advertEdit)
    }

    @Test
    fun `pricing a listing OLX returned without a price sends the account country's currency`() = runTest(testDispatcher) {
        var putBody: String? = null
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method == io.ktor.http.HttpMethod.Put -> {
                        putBody = (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    // No `price` object at all, and an explicit-null currency is the same case:
                    // OLX will not take a value without a currency, so one has to be supplied.
                    path.contains("/adverts/111") -> respond(
                        """
                        {
                          "data": {
                            "id": 111,
                            "status": "active",
                            "valid_to": "2026-09-30T10:00:00+03:00",
                            "title": "Free bookshelf",
                            "description": "Collection only.",
                            "category_id": 1234,
                            "location": { "city_id": 1234 },
                            "attributes": [ { "code": "condition", "values": ["used"] } ]
                          }
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )

                    else -> respond("""{"data":[{"id":111,"status":"active"}]}""", status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()

        val edit = assertNotNull(viewModel.state.value.advertEdit)
        assertNull(edit.priceValue)

        viewModel.onEvent(Event.EditSubmitted(title = edit.title, description = edit.description, price = 500))
        runCurrent()

        val body = assertNotNull(putBody)
        assertTrue(body.contains("\"value\":500"), body)
        // The account's country is Ukraine, so the listing must be priced in UAH rather than
        // being sent to OLX with no currency at all.
        assertTrue(body.contains("\"currency\":\"UAH\""), body)
    }

    @Test
    fun `an edit strips the delivery flag OLX reports but does not accept back`() = runTest(testDispatcher) {
        var putBody: String? = null
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method == io.ktor.http.HttpMethod.Put -> {
                        putBody = (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") -> respond(
                        """
                        {
                          "data": {
                            "id": 111,
                            "status": "active",
                            "valid_to": "2026-09-30T10:00:00+03:00",
                            "title": "Nike Air Max 90",
                            "description": "Worn twice.",
                            "category_id": 1234,
                            "location": { "city_id": 1234 },
                            "price": { "value": 1800, "currency": "UAH" },
                            "ad_delivery": {
                              "delivery_package_ids": ["pkg-1"],
                              "delivery_change_allowed": true
                            }
                          }
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )

                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()

        val edit = assertNotNull(viewModel.state.value.advertEdit)
        viewModel.onEvent(Event.EditSubmitted(title = edit.title, description = edit.description, price = 1500))
        runCurrent()

        val body = assertNotNull(putBody)
        // `delivery_change_allowed` is OLX reporting whether delivery is editable and is in
        // neither request schema, wherever OLX chooses to put it.
        assertTrue(!body.contains("delivery_change_allowed"), body)
        // The delivery setting itself still has to survive, or an edit silently drops it.
        assertTrue(body.contains("\"delivery_package_ids\":[\"pkg-1\"]"), body)
    }

    @Test
    fun `pricing a listing OLX returned with no price falls back to the account's currency`() = runTest(testDispatcher) {
        var putBody: String? = null
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method == io.ktor.http.HttpMethod.Put -> {
                        putBody = (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") -> respond(
                        // No "price" key at all - the listing was published without one.
                        """
                        {
                          "data": {
                            "id": 111,
                            "status": "active",
                            "valid_to": "2026-09-30T10:00:00+03:00",
                            "title": "Nike Air Max 90",
                            "description": "Worn twice.",
                            "category_id": 1234,
                            "location": { "city_id": 1234 }
                          }
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )

                    // Also priceless on the list, so `advert.currencyCode` is blank and the
                    // fallback has to come from the active OLX country (UA/UAH in this harness).
                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()

        val edit = assertNotNull(viewModel.state.value.advertEdit)
        viewModel.onEvent(Event.EditSubmitted(title = edit.title, description = edit.description, price = 1500))
        runCurrent()

        val body = assertNotNull(putBody)
        assertTrue(body.contains("\"price\":{\"value\":1500,\"currency\":\"UAH\"}"), body)
    }

    @Test
    fun `a listing OLX is still reviewing opens the seller's OLX listings instead of nowhere`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/statistics")) {
                    respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    // An advert under review: OLX has not given it a public URL yet.
                    respond(
                        """{"data":[{"id":111,"status":"new","url":""}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        assertEquals("", advert.url)
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.OpenOnOlxClicked)

        // Refusing to navigate left the seller with no way to reach a listing under review at
        // all. Their own OLX listings page is where it is visible, so that is where they go.
        val opened = viewModel.effects.awaitEffect<MyAdvertsContract.Effect.OpenUrl>()
        assertEquals("https://www.olx.ua/myaccount/", opened.url)
        // The sheet gets out of the way, or the browser hand-off is swallowed by its scrim.
        assertNull(viewModel.state.value.advertSheet)
    }

    @Test
    fun `every successful action refetches the list`() = runTest(testDispatcher) {
        // The whole point: an action must leave the seller looking at fresh data. Deactivate and
        // reactivate previously skipped the refetch entirely, which is why only some actions
        // appeared to refresh anything.
        var listReads = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> respond("", status = HttpStatusCode.NoContent)

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> {
                        listReads++
                        respond(advertsJson(111L, status = "outdated"), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()
        val afterFirstLoad = listReads

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        viewModel.onEvent(Event.ActionConfirmed)

        viewModel.effects.awaitEffect<MyAdvertsContract.Effect.ShowMessage>()
        advanceUntilIdle()

        assertTrue(listReads > afterFirstLoad, "reactivate must refetch the list")
    }

    @Test
    fun `a stale read cannot undo a status the command already changed`() = runTest(testDispatcher) {
        // OLX answers the command with 204 and then keeps reporting the old status for a moment.
        // The refetch must not put `active` back on a listing that was just taken down, or the
        // badge reverts and the seller is offered Deactivate again.
        var serverStatus = "active"
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> respond("", status = HttpStatusCode.NoContent)

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L, status = serverStatus), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Deactivate))
        runCurrent()
        viewModel.onEvent(Event.SoldAnswered(isSold = false))

        viewModel.effects.awaitEffect<MyAdvertsContract.Effect.ShowMessage>()
        advanceUntilIdle()

        // The refetch happened and still said `active`; the row holds the new status anyway.
        assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.pages.single().adverts.single().status)

        // Another refresh while OLX is still behind must not flip it back either.
        viewModel.onEvent(Event.RefreshClicked(1))
        advanceUntilIdle()
        assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.pages.single().adverts.single().status)

        // Once OLX catches up - even to a different status than the one anticipated - the server
        // wins and the expectation retires, so a later change on OLX's side is never masked.
        serverStatus = "outdated"
        viewModel.onEvent(Event.RefreshClicked(1))
        advanceUntilIdle()
        assertEquals(AdvertStatus.Outdated, viewModel.state.value.pages.single().adverts.single().status)
    }

    @Test
    fun `a failed action shows why inside the sheet, where the seller can actually see it`() = runTest(testDispatcher) {
        // A snackbar is rendered by the screen underneath, so while the sheet is up its scrim
        // hides it - the seller pressed a button and nothing appeared to happen at all.
        val secondAttempt = CompletableDeferred<Unit>()
        var commandsSent = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> {
                        commandsSent++
                        // The retry never resolves, so the cleared state stays observable.
                        if (commandsSent > 1) secondAttempt.await()
                        respond(
                            """{"error":{"status":400,"title":"Invalid request","validation":[{"field":"ad","title":"Ad has to be active"}]}}""",
                            status = HttpStatusCode.BadRequest,
                            headers = jsonHeaders(),
                        )
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L, status = "outdated"), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        viewModel.onEvent(Event.ActionConfirmed)

        // Awaited on the state, not drained: the reason is resolved through compose-resources
        // `getString`, which hops off the test dispatcher.
        val sheet = viewModel.state.first { it.advertSheet?.errorMessage != null }.advertSheet!!
        // OLX's own reason, on the sheet, next to the button that produced it.
        assertTrue(sheet.errorMessage!!.contains("Ad has to be active"), sheet.errorMessage!!)
        // And the sheet stays open, so the seller keeps the context to try again.
        assertNull(sheet.pendingAction)

        // Trying again clears the stale reason rather than leaving it sitting under the new
        // attempt. Held mid-flight so the assertion sees the cleared state, not the next failure.
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        viewModel.onEvent(Event.ActionConfirmed)
        runCurrent()

        val retrying = assertNotNull(viewModel.state.value.advertSheet)
        assertEquals(AdvertAction.Reactivate, retrying.pendingAction)
        assertNull(retrying.errorMessage)
    }

    @Test
    fun `an edit submits every attribute as an array, whatever shape OLX returned it in`() = runTest(testDispatcher) {
        // A real edit failed with OLX's "compound forms expect an array or NULL on submission".
        // OLX returns a single-valued attribute as a scalar `value`, but will not accept that
        // scalar back - so echoing the response verbatim, which is right for every other field,
        // is wrong for exactly this one.
        var putBody: String? = null
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method == io.ktor.http.HttpMethod.Put -> {
                        putBody = (request.body as io.ktor.http.content.TextContent).text
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") -> respond(
                        """
                        {
                          "data": {
                            "id": 111,
                            "status": "active",
                            "valid_to": "2026-09-30T10:00:00+03:00",
                            "title": "Nike Air Max 90",
                            "description": "Worn twice.",
                            "category_id": 1234,
                            "location": { "city_id": 1234 },
                            "price": { "value": 1800, "currency": "UAH" },
                            "attributes": [
                              { "code": "condition", "value": "used" },
                              { "code": "colour", "values": ["black", "white"] },
                              { "code": "empty", "value": null }
                            ]
                          }
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )

                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()
        val edit = assertNotNull(viewModel.state.value.advertEdit)
        viewModel.onEvent(Event.EditSubmitted(title = edit.title, description = edit.description, price = 1500))
        runCurrent()

        val body = assertNotNull(putBody)
        // The scalar became a one-element array of strings...
        assertTrue(body.contains("""{"code":"condition","values":["used"]}"""), body)
        // ...an array was kept...
        assertTrue(body.contains("""{"code":"colour","values":["black","white"]}"""), body)
        // ...and an attribute with no value is dropped, not sent as the string "null" -
        // `JsonNull` is itself a `JsonPrimitive`, so reading `.content` off it says "null".
        assertTrue(!body.contains("empty"), body)
        assertTrue(!body.contains("\"null\""), body)
        // The scalar key itself must not survive, or OLX sees the shape it rejected.
        assertTrue(!body.contains("\"value\":\"used\""), body)
    }

    private fun statisticsJson(views: Int, phoneViews: Int, observing: Int) =
        """{"advert_views":$views,"phone_views":$phoneViews,"users_observing":$observing}"""

    private fun advertJson(id: Long, status: String) = """{"data":{"id":$id,"status":"$status"}}"""

    /** A realistic advert, including a field the app does not model, to prove the edit echo. */
    private fun fullAdvertJson(id: Long = 111, title: String = "Nike Air Max 90", price: Long = 1800) = """
        {
          "data": {
            "id": $id,
            "status": "active",
            "url": "https://www.olx.ua/d/obyavlenie/x.html",
            "created_at": "2026-08-01T10:00:00+03:00",
            "activated_at": "2026-08-01T10:00:00+03:00",
            "valid_to": "2026-09-30T10:00:00+03:00",
            "title": "$title",
            "description": "Worn twice.",
            "category_id": 1234,
            "advertiser_type": "private",
            "contact": { "name": "Seller", "phone": "+380501112233" },
            "location": { "city_id": 1234, "district_id": 77 },
            "images": [ { "url": "https://cdn.olx.ua/a.jpg" } ],
            "price": { "value": $price, "currency": "UAH", "negotiable": false },
            "attributes": [ { "code": "condition", "values": ["used"] } ],
            "auto_extend_enabled": true,
            "product_safety_regulation": { "manufacturer": { "name": "Nike" } }
          }
        }
    """.trimIndent()

    // ----- Regressions found in review -----

    @Test
    fun `a command that landed shows on the row even when the list cannot be read`() = runTest(testDispatcher) {
        var commandsSent = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> {
                        commandsSent++
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    // The follow-up read fails. The take-down still happened, and OLX will not
                    // accept a second one - so calling this a failure would send the seller round
                    // to retry into a guaranteed "Ad has to be active".
                    path.contains("/adverts/111") ->
                        respond("", status = HttpStatusCode.InternalServerError, headers = jsonHeaders())

                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val analytics = FakeAnalytics()
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            analytics = analytics,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Deactivate))
        runCurrent()
        viewModel.onEvent(Event.SoldAnswered(isSold = false))

        // Awaited, not drained: the confirmation message is resolved through compose-resources
        // `getString`, which hops off the test dispatcher, so `runCurrent()` can return before
        // the action has finished reporting itself.
        viewModel.effects.awaitEffect<MyAdvertsContract.Effect.ShowMessage>()

        assertEquals(1, commandsSent)
        assertEquals("success", analytics.paramsFor(AnalyticsEvents.ADVERT_ACTION)?.get("result"))
        // The command landed, so the row shows it - and does so without depending on a read that
        // is failing. Reconciliation with OLX happens on the next pull-to-refresh.
        assertEquals(AdvertStatus.RemovedByUser, viewModel.state.value.pages.single().adverts.single().status)
        // Still counts as a success, so the sheet closes and the snackbar behind it is visible.
        assertNull(viewModel.state.value.advertSheet)
    }

    @Test
    fun `dismissing and reopening the sheet mid-action cannot fire the same command twice`() = runTest(testDispatcher) {
        var commandsSent = 0
        val commandGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> {
                        commandsSent++
                        commandGate.await()
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") ->
                        respond(advertJson(111L, "removed_by_user"), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L, status = "outdated"), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        // Expired advert: Reactivate confirms without going through the sold prompt.
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        viewModel.onEvent(Event.ActionConfirmed)
        runCurrent()
        assertEquals(1, commandsSent)

        // Swipe the sheet away and tap the same row again while the command is still in flight.
        // A fresh sheet must not come back with the action enabled.
        viewModel.onEvent(Event.AdvertSheetDismissed)
        runCurrent()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()

        assertEquals(AdvertAction.Reactivate, assertNotNull(viewModel.state.value.advertSheet).pendingAction)
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        assertEquals(1, commandsSent)

        commandGate.complete(Unit)
        runCurrent()
        // The sheet closes on success: leaving it open showed the seller the buttons they had
        // just pressed, which reads as nothing having happened.
        assertNull(viewModel.state.value.advertSheet)
    }

    @Test
    fun `clearing the price field is not an edit`() = runTest(testDispatcher) {
        var puts = 0
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    request.method == io.ktor.http.HttpMethod.Put -> {
                        puts++
                        respond("", status = HttpStatusCode.NoContent)
                    }

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") ->
                        respond(fullAdvertJson(), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val analytics = FakeAnalytics()
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            analytics = analytics,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()

        val edit = assertNotNull(viewModel.state.value.advertEdit)
        // OLX has no way to remove a price through this payload, so an emptied field means "leave
        // it alone" - not a price-only edit that silently changes nothing.
        viewModel.onEvent(Event.EditSubmitted(title = edit.title, description = edit.description, price = null))
        runCurrent()

        assertEquals(0, puts)
        assertNull(viewModel.state.value.advertEdit)
        assertNull(analytics.paramsFor(AnalyticsEvents.ADVERT_EDITED))
    }

    @Test
    fun `an edit load that lands after the seller moved on cannot seed another advert's form`() = runTest(testDispatcher) {
        val firstEditGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") -> {
                        firstEditGate.await()
                        respond(fullAdvertJson(title = "Advert A", price = 1800), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }

                    path.contains("/adverts/222") ->
                        respond(fullAdvertJson(id = 222, title = "Advert B", price = 900), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(
                        """{"data":[{"id":111,"status":"active"},{"id":222,"status":"active"}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val adverts = viewModel.state.value.pages.single().adverts
        val advertA = adverts.first { it.id == 111L }
        val advertB = adverts.first { it.id == 222L }

        // Start editing A, whose load hangs, then back out and edit B instead.
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advertA))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()
        viewModel.onEvent(Event.EditDismissed)
        runCurrent()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advertB))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Edit))
        runCurrent()

        assertEquals("Advert B", assertNotNull(viewModel.state.value.advertEdit).title)

        // A's load finally lands. It must be dropped: seeding B's form with A's title and
        // description would push them onto advert B on the next save.
        firstEditGate.complete(Unit)
        runCurrent()

        val edit = assertNotNull(viewModel.state.value.advertEdit)
        assertEquals(222L, edit.advert.id)
        assertEquals("Advert B", edit.title)
        assertEquals(900L, edit.priceValue)
        assertEquals(false, edit.loadFailed)
    }

    @Test
    fun `putting a listing back up clears its outcome so a later sale is still measured`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/commands") -> respond("", status = HttpStatusCode.NoContent)

                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") ->
                        respond(advertJson(111L, "active"), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L, status = "outdated"), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val outcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)
        outcomeStore.recordClosed(advertId = 111L, isSold = false, achievedPrice = null)
        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
            outcomeStore = outcomeStore,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()
        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        viewModel.onEvent(Event.ActionConfirmed)
        runCurrent()

        // Live again means no outcome. Without this, the next genuine sale of this listing would
        // be silently dropped from `advert_sold` - the one metric the milestone exists to produce.
        assertNull(assertNotNull(outcomeStore.outcomeFor(111L)).isSold)
    }

    @Test
    fun `an action on an account that needs reconnecting says so instead of offering a retry`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/statistics") ->
                        respond(statisticsJson(0, 0, 0), status = HttpStatusCode.OK, headers = jsonHeaders())

                    path.contains("/adverts/111") ->
                        respond(advertJson(111L, "outdated"), status = HttpStatusCode.OK, headers = jsonHeaders())

                    else -> respond(advertsJson(111L, status = "outdated"), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val (viewModel, harness) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        val advert = viewModel.state.value.pages.single().adverts.single()
        viewModel.onEvent(Event.AdvertClicked(localIndex = 1, advert = advert))
        runCurrent()

        // The account dies between opening the sheet and confirming the action.
        harness.accountStore.markNeedsReconnect(1)
        runCurrent()

        viewModel.onEvent(Event.ActionClicked(AdvertAction.Reactivate))
        runCurrent()
        viewModel.onEvent(Event.ActionConfirmed)

        // Awaited on the state: the message is resolved through compose-resources `getString`,
        // which hops off the test dispatcher, and it lands inside the sheet rather than as a
        // snackbar the sheet's own scrim would hide.
        val message = viewModel.state.first { it.advertSheet?.errorMessage != null }
            .advertSheet!!
            .errorMessage!!

        // "Try again in a moment" would send the seller round a loop that cannot succeed until
        // they reconnect, so the message has to name reconnecting - and must not be the publish
        // flow's wording, since the seller was closing a listing, not publishing one.
        assertTrue(message.contains("reconnecting", ignoreCase = true), "got: $message")
        assertTrue(!message.contains("publish", ignoreCase = true), "got: $message")
    }

    private data class LoggedRequest(val path: String, val authHeader: String?)

    private fun io.ktor.client.request.HttpRequestData.toLogged() =
        LoggedRequest(url.encodedPath, headers[HttpHeaders.Authorization])

    private fun mockEngine(dispatcher: CoroutineDispatcher, configure: MockEngineConfig.() -> Unit): MockEngine {
        val config = MockEngineConfig().apply(configure)
        config.dispatcher = dispatcher
        return MockEngine(config)
    }

    private fun advertsJson(id: Long, status: String = "active") =
        """{"data":[{"id":$id,"status":"$status"}]}"""

    private fun refreshTokenJson(accessToken: String) =
        """{"access_token":"$accessToken","refresh_token":"refresh-new","expires_in":86400,"token_type":"bearer","scope":"v2 read write"}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    private fun account(
        localIndex: Int,
        olxUserId: Long,
        accessToken: String,
        countryCode: String = "ua",
        // Real wall-clock issue time by default so accessTokenFor's expiry check treats the
        // token as fresh - tests that want an already-expired token (to force a proactive
        // refresh) pass issuedAtEpochSeconds = 0 explicitly.
        issuedAtEpochSeconds: Long = nowEpochSeconds(),
    ) = OlxAccountRecord(
        localIndex = localIndex,
        countryCode = countryCode,
        olxUserId = olxUserId,
        tokens = OlxTokens(
            accessToken = accessToken,
            refreshToken = "refresh-token-$localIndex",
            expiresInSeconds = 86_400,
            tokenType = "bearer",
            scope = "v2 read write",
            issuedAtEpochSeconds = issuedAtEpochSeconds,
        ),
        lastUsedAtEpochSeconds = 0,
        lastRefreshedAtEpochSeconds = 0,
        state = OlxAccountState.Usable,
        profile = null,
    )

    private suspend fun setUpViewModel(
        engine: MockEngine,
        accounts: List<OlxAccountRecord>,
        activeIndex: Int,
        countryCode: String = "ua",
        // Injectable so the lifecycle tests can assert what was recorded about a closed listing.
        outcomeStore: AdvertOutcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson),
        analytics: FakeAnalytics = FakeAnalytics(),
    ): Pair<MyAdvertsViewModel, TestHarness> {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = accounts,
                activeByCountry = mapOf(countryCode to activeIndex),
                nextLocalIndex = (accounts.maxOfOrNull { it.localIndex } ?: 0) + 1,
            ),
        )
        val harness = harness(engine, accountStore)
        val repository = MyAdvertsRepository(
            accountRepository = harness.repository,
            unauthenticatedOlxApiClient = harness.unauthenticatedOlxApiClient,
        )
        val viewModel = MyAdvertsViewModel(
            repository = repository,
            accountRepository = harness.repository,
            lifecycleRepository = AdvertLifecycleRepository(
                accountRepository = harness.repository,
                unauthenticatedOlxApiClient = harness.unauthenticatedOlxApiClient,
                myAdvertsRepository = repository,
            ),
            outcomeStore = outcomeStore,
            analytics = analytics,
        )
        return viewModel to harness
    }

    private suspend fun harness(engine: MockEngine, accountStore: OlxAccountStore): TestHarness {
        val analytics = FakeAnalytics()
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), analytics).apply { save(OlxCountry.UA) }
        val errorParser = OlxRemoteErrorParser(testJson)
        val unauthenticatedHttpClient = createOlxHttpClient(engine)
        val authorizedHttpClient = createOlxAuthorizedHttpClient(
            authRefreshClient = unauthenticatedHttpClient,
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore,
            errorParser = errorParser,
            engine = engine,
        )
        val olxApiClient = OlxApiClient(httpClient = authorizedHttpClient, json = testJson, errorParser = errorParser)
        val unauthenticatedOlxApiClient = OlxApiClient(httpClient = unauthenticatedHttpClient, json = testJson, errorParser = errorParser)
        val authRepository = OlxAuthRepository(
            httpClient = unauthenticatedHttpClient,
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore,
            authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
            redirectHandler = TestRedirectHandler(),
            guestModeStore = GuestModeStore(InMemoryOlxKeyValueStore()),
            errorParser = errorParser,
        )
        val analyticsConsentRepository = AnalyticsConsentRepository(
            store = AnalyticsConsentStore(InMemoryOlxKeyValueStore()),
            analytics = analytics,
            applicationScope = CoroutineScope(Dispatchers.Default),
        )
        val locationRepository = LocationRepository(
            locationProvider = object : LocationProvider {
                override suspend fun getCurrentLocation(): DeviceLocation? = null
            },
            olxApiClient = olxApiClient,
            locationStore = LocationStore(InMemoryOlxKeyValueStore(), testJson),
        )
        val repository = SellerAccountRepository(
            authRepository = authRepository,
            olxApiClient = olxApiClient,
            unauthenticatedOlxApiClient = unauthenticatedOlxApiClient,
            authorizedHttpClient = authorizedHttpClient,
            unauthenticatedHttpClient = unauthenticatedHttpClient,
            accountStore = accountStore,
            locationRepository = locationRepository,
            olxCountryStore = countryStore,
            draftMediaFileStore = FakeDraftMediaFileStore,
            advertOutcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson),
            analyticsConsentRepository = analyticsConsentRepository,
            errorParser = errorParser,
            analytics = analytics,
        )
        return TestHarness(repository, olxApiClient, unauthenticatedOlxApiClient, accountStore)
    }

    private data class TestHarness(
        val repository: SellerAccountRepository,
        val olxApiClient: OlxApiClient,
        val unauthenticatedOlxApiClient: OlxApiClient,
        val accountStore: OlxAccountStore,
    )

    private class TestCredentialsProvider : OlxCredentialsProvider {
        override suspend fun getClientId(): String = "test-client-id"
        override suspend fun getClientSecret(): String = "test-client-secret"
    }

    private class TestRedirectHandler : OlxRedirectHandler {
        override fun buildRedirectUri(platform: com.sirelon.sellsnap.platform.PlatformTargets): String =
            "selolxai://olx-auth/callback"

        override fun parseCallback(url: String): OlxAuthCallback {
            val parsed = io.ktor.http.Url(url)
            return OlxAuthCallback(
                code = parsed.parameters["code"],
                state = parsed.parameters["state"],
                error = parsed.parameters["error"],
                errorDescription = parsed.parameters["error_description"],
            )
        }
    }

    private class FakeAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        fun paramsFor(name: String): Map<String, Any>? = events.lastOrNull { it.first == name }?.second

        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }

    private object FakeDraftMediaFileStore : DraftMediaFileStore {
        override suspend fun persist(file: KmpFile): PersistedDraftPhoto? = null
        override fun restore(photo: DraftPhoto): KmpFile? = null
        override fun stablePath(file: KmpFile): String? = null
        override suspend fun delete(photos: List<DraftPhoto>) {}
        override suspend fun deleteAll() {}
    }
}
