package com.sirelon.sellsnap.http

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.module
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramRouteTest {
    @Test
    fun wrongOrMissingSecretIsRefusedBeforeAnythingIsParsed() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        val body = messageUpdateJson(1, "hello")

        assertEquals(HttpStatusCode.Forbidden, client.postTelegram(body, secret = "wrong").status)
        assertEquals(HttpStatusCode.Forbidden, client.postTelegram(body, secret = null).status)
        assertEquals(HttpStatusCode.Forbidden, client.postTelegram("not json at all", secret = null).status)
        assertTrue(fx.memory.updates.isEmpty())
        assertTrue(fx.telegram.calls.isEmpty())
    }

    @Test
    fun aMalformedBodyGets200WithTheRightSecret() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        assertEquals(HttpStatusCode.OK, client.postTelegram("not json at all").status)
        assertEquals(HttpStatusCode.OK, client.postTelegram("""{"no_update_id":true}""").status)
        assertTrue(fx.memory.updates.isEmpty())
    }

    @Test
    fun aValidUpdateIsHandledAndUnknownFieldsAreIgnored() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        assertEquals(HttpStatusCode.OK, client.postTelegram(messageUpdateJson(7, "hello")).status)
        assertEquals(1, fx.sentTexts().size)
    }

    @Test
    fun theSameUpdateIdIsProcessedOnce() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        repeat(3) { assertEquals(HttpStatusCode.OK, client.postTelegram(messageUpdateJson(9, "hello")).status) }
        assertEquals(1, fx.telegram.callsOf<FakeTelegramApi.Call.Sent>().size)
        assertEquals(setOf(9L), fx.memory.updates.keys)
    }

    @Test
    fun aStrangerIsIgnoredButStillGets200() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        assertEquals(HttpStatusCode.OK, client.postTelegram(messageUpdateJson(3, "hello", from = 999)).status)
        assertTrue(fx.telegram.calls.isEmpty())
    }

    @Test
    fun secretComparisonAcceptsOnlyTheExactValue() {
        val secret = TelegramSecret("abc")
        assertTrue(secret.matches("abc"))
        assertTrue(!secret.matches("abd"))
        assertTrue(!secret.matches("abcd"))
        assertTrue(!secret.matches(""))
        assertTrue(!secret.matches(null))
    }
}
