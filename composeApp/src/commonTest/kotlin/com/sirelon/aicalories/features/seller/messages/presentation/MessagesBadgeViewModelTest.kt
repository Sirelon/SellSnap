package com.sirelon.sellsnap.features.seller.messages.presentation

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessagesBadgeViewModelTest {

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
    fun `the badge counts unread messages in seller-side threads only`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler {
                respond(
                    threadsJson(
                        threadJson(1, 10, unread = 2),
                        threadJson(2, 11, interlocutorId = OwnUserId, unread = 7),
                        threadJson(3, 12, unread = 1),
                    ),
                    HttpStatusCode.OK,
                    jsonHeaders(),
                )
            }
        }
        val harness = messagesHarness(engine, listOf(testAccount(1)), activeIndex = 1)

        val viewModel = MessagesBadgeViewModel(harness.messagesRepository, harness.accountRepository)
        runCurrent()

        assertEquals(3, viewModel.unreadCount.value)
    }

    @Test
    fun `the badge sums every account of the country and skips one that needs a reconnect`() = runTest(testDispatcher) {
        val tokens = mutableListOf<String?>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                val token = request.headers["Authorization"]
                tokens += token
                val unread = when (token) {
                    "Bearer token-1" -> 2
                    "Bearer token-2" -> 3
                    else -> 7
                }
                respond(threadsJson(threadJson(1, 10, unread = unread)), HttpStatusCode.OK, jsonHeaders())
            }
        }
        val harness = messagesHarness(
            engine,
            listOf(
                testAccount(1),
                testAccount(2, olxUserId = 2),
                testAccount(3, olxUserId = 3, state = OlxAccountState.NeedsReconnect),
            ),
            activeIndex = 1,
        )

        val viewModel = MessagesBadgeViewModel(harness.messagesRepository, harness.accountRepository)
        runCurrent()

        assertEquals(5, viewModel.unreadCount.value)
        assertTrue("Bearer token-3" !in tokens)

        // Disconnecting an account drops its count.
        harness.accountStore.disconnect(2)
        runCurrent()
        assertEquals(2, viewModel.unreadCount.value)
    }

    @Test
    fun `no connected account means no badge`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond("", HttpStatusCode.InternalServerError) }
        }
        val harness = messagesHarness(engine, accounts = emptyList(), activeIndex = null)

        val viewModel = MessagesBadgeViewModel(harness.messagesRepository, harness.accountRepository)
        runCurrent()

        assertEquals(0, viewModel.unreadCount.value)
    }

    @Test
    fun `a failed reload keeps the last count`() = runTest(testDispatcher) {
        var fail = false
        val engine = mockEngine(testDispatcher) {
            addHandler {
                if (fail) {
                    respond("", HttpStatusCode.InternalServerError)
                } else {
                    respond(threadsJson(threadJson(1, 10, unread = 4)), HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val harness = messagesHarness(engine, listOf(testAccount(1)), activeIndex = 1)
        val viewModel = MessagesBadgeViewModel(harness.messagesRepository, harness.accountRepository)
        runCurrent()
        assertEquals(4, viewModel.unreadCount.value)

        fail = true
        viewModel.refresh()
        runCurrent()

        assertEquals(4, viewModel.unreadCount.value)
    }
}
