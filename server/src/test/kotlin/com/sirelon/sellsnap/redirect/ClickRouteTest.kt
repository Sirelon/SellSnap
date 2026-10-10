package com.sirelon.sellsnap.redirect

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.CountingStore
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClickRouteTest {
    private val android = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
    private val iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
    private val desktop = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"

    private lateinit var counting: CountingStore

    private fun fixture() =
        AgentFixture(wrapStore = { CountingStore(it).also { c -> counting = c } }).also {
            it.memory.put(testAccount())
            it.memory.put(testPost(status = PostStatus.Published))
        }

    private suspend fun ApplicationTestBuilder.click(body: String, ua: String? = desktop): HttpResponse =
        client.post("/click") {
            contentType(ContentType.Text.Plain)
            ua?.let { header(HttpHeaders.UserAgent, it) }
            setBody(body)
        }

    private fun body(code: String, event: String) = """{"p":"$code","e":"$event"}"""

    @Test
    fun aVisitIsLoggedAsLandingWithTheUserAgentFamily() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }

        val response = click(body("stt0001", "view"), android)

        assertEquals(HttpStatusCode.NoContent, response.status)
        val click = fx.memory.clicks.single()
        assertEquals(listOf("stt0001", "landing", "android"), listOf(click.code, click.store, click.uaFamily))
        assertEquals(AgentFixture.NOW, click.ts)
        assertNull(click.countryFromIp)
    }

    @Test
    fun storeTapsAreLoggedUnderTheirStore() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }

        click(body("stt0001", "play"), android)
        click(body("stt0001", "appstore"), iphone)
        click(body("stt0001", "play"), desktop)

        assertEquals(listOf("play", "appstore", "play"), fx.memory.clicks.map { it.store })
        assertEquals(listOf("android", "ios", "other"), fx.memory.clicks.map { it.uaFamily })
    }

    @Test
    fun everyAnswerIs204WithNoStoreNoCookieAndTheWebsiteOrigin() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }

        listOf(
            body("stt0001", "view"),
            body("stt9999", "view"),
            body("STT-1", "view"),
            body("stt0001", "nonsense"),
            "not json",
            "[]",
            """{"p":1,"e":"view"}""",
            "",
            "x".repeat(5000),
        ).forEach { text ->
            val response = click(text)
            assertEquals(HttpStatusCode.NoContent, response.status, text.take(40))
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl], text.take(40))
            assertEquals("https://sirelon.github.io", response.headers[HttpHeaders.AccessControlAllowOrigin], text.take(40))
            assertNull(response.headers[HttpHeaders.SetCookie], text.take(40))
            assertEquals("", response.bodyAsText(), text.take(40))
        }
    }

    @Test
    fun invalidShapeUnknownCodeAndBotsAreNotLogged() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }

        click(body("STT0001", "view"))
        click(body("st-0001", "view"))
        click(body("a".repeat(31), "view"))
        assertEquals(0, counting.reads, "a bad code shape is rejected before any read")

        click(body("stt9999", "view"), android)
        click(body("stt9999", "play"), android)
        listOf(
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "Mozilla/5.0 (compatible; Googlebot/2.1)",
            "SomeCrawler/2.0 Android",
        ).forEach { click(body("stt0001", "view"), it) }
        click(body("stt0001", "nonsense"), android)
        click("not json", android)

        assertTrue(fx.memory.clicks.isEmpty())
    }

    @Test
    fun aKnownCodeIsReadOnceAndAMissIsCached() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }

        repeat(3) { click(body("stt0001", "view"), android) }
        repeat(3) { click(body("stt9999", "view"), android) }

        assertEquals(2, counting.postReads)
        assertEquals(1, counting.accountReads)
        assertEquals(3, fx.memory.clicks.size)
    }

    @Test
    fun aCodeIsCappedPerDayAcrossViewsAndTaps() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }

        repeat(RedirectService.DEFAULT_CLICK_CAP + 10) { click(body("stt0001", if (it % 2 == 0) "view" else "play"), android) }

        assertEquals(RedirectService.DEFAULT_CLICK_CAP, fx.memory.clicks.size)
        fx.clock.advance(java.time.Duration.ofDays(1))
        click(body("stt0001", "view"), android)
        assertEquals(RedirectService.DEFAULT_CLICK_CAP + 1, fx.memory.clicks.size)
    }
}
