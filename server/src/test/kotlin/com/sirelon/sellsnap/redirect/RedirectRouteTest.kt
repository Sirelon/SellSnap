package com.sirelon.sellsnap.redirect

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.CountingStore
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.module
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedirectRouteTest {
    private val android = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
    private val iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
    private val desktop = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"

    private lateinit var counting: CountingStore

    private fun fixture(appleProviderToken: String? = null) =
        AgentFixture(appleProviderToken = appleProviderToken, wrapStore = { CountingStore(it).also { c -> counting = c } }).also {
            it.memory.put(testAccount())
            it.memory.put(testPost(status = PostStatus.Published))
        }

    private fun ApplicationTestBuilder.noRedirectClient() = createClient { followRedirects = false }

    @Test
    fun androidGoesToPlayWithTheExactReferrer() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val response = noRedirectClient().get("/go/stt0001") { header(HttpHeaders.UserAgent, android) }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.sirelon.sellsnap&referrer=" +
                "utm_source%3Dthreads%26utm_medium%3Dsocial%26utm_campaign%3Duk%26utm_content%3Dstt0001",
            response.headers[HttpHeaders.Location],
        )
        assertNull(response.headers[HttpHeaders.SetCookie])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun iphoneGoesToTheAppStoreWithAndWithoutProviderToken() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val plain = noRedirectClient().get("/go/stt0001") { header(HttpHeaders.UserAgent, iphone) }
        assertEquals("https://apps.apple.com/app/id6776001373?ct=stt0001", plain.headers[HttpHeaders.Location])
    }

    @Test
    fun appStoreLinkCarriesTheProviderTokenWhenSet() = testApplication {
        val fx = fixture(appleProviderToken = "123456")
        application { module(fx.graph) }
        val response = noRedirectClient().get("/go/stt0001") { header(HttpHeaders.UserAgent, iphone) }
        assertEquals("https://apps.apple.com/app/id6776001373?ct=stt0001&pt=123456", response.headers[HttpHeaders.Location])
    }

    @Test
    fun desktopAndMissingUserAgentGoToTheLandingPage() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val client = noRedirectClient()
        assertEquals(
            "https://sellsnap.test/landing",
            client.get("/go/stt0001") { header(HttpHeaders.UserAgent, desktop) }.headers[HttpHeaders.Location],
        )
        assertEquals("https://sellsnap.test/landing", client.get("/go/stt0001").headers[HttpHeaders.Location])
    }

    @Test
    fun clickLogHoldsTheDocumentFieldsOnly() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        noRedirectClient().get("/go/stt0001") { header(HttpHeaders.UserAgent, android) }
        noRedirectClient().get("/go/stt0001") { header(HttpHeaders.UserAgent, iphone) }

        val clicks = fx.memory.clicks
        assertEquals(listOf("play", "appstore"), clicks.map { it.store })
        assertEquals(listOf("android", "ios"), clicks.map { it.uaFamily })
        assertTrue(clicks.all { it.code == "stt0001" && it.ts == AgentFixture.NOW && it.countryFromIp == null })
    }

    @Test
    fun botsGoToTheLandingPageAndAreNotLogged() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val client = noRedirectClient()
        listOf(
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "meta-externalagent/1.1 (+https://developers.facebook.com/docs/sharing/webmasters/crawler)",
            "Facebot",
            "Mozilla/5.0 (compatible; Googlebot/2.1)",
            "SomeCrawler/2.0 Android",
            "Mozilla/5.0 (compatible; SpiderX) iPhone",
        ).forEach { ua ->
            val response = client.get("/go/stt0001") { header(HttpHeaders.UserAgent, ua) }
            assertEquals("https://sellsnap.test/landing", response.headers[HttpHeaders.Location], ua)
        }
        assertTrue(fx.memory.clicks.isEmpty())
        assertEquals(0, counting.reads)
    }

    @Test
    fun unknownCodeGoesToLandingNotLoggedAndTheMissIsCached() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val client = noRedirectClient()
        repeat(3) {
            val response = client.get("/go/stt9999") { header(HttpHeaders.UserAgent, android) }
            assertEquals("https://sellsnap.test/landing", response.headers[HttpHeaders.Location])
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        }
        assertTrue(fx.memory.clicks.isEmpty())
        assertEquals(1, counting.postReads)
    }

    @Test
    fun invalidCodesAreRejectedBeforeAnyRead() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val client = noRedirectClient()
        listOf("STT0001", "stt-0001", "a".repeat(31), "st%20t1", "st.t1").forEach { code ->
            val response = client.get("/go/$code") { header(HttpHeaders.UserAgent, android) }
            assertEquals("https://sellsnap.test/landing", response.headers[HttpHeaders.Location], code)
        }
        assertEquals(0, counting.reads)
        assertTrue(fx.memory.clicks.isEmpty())
    }

    @Test
    fun aKnownCodeIsReadOnce() = testApplication {
        val fx = fixture()
        application { module(fx.graph) }
        val client = noRedirectClient()
        repeat(4) { client.get("/go/stt0001") { header(HttpHeaders.UserAgent, android) } }
        assertEquals(1, counting.postReads)
        assertEquals(1, counting.accountReads)
        assertEquals(4, fx.memory.clicks.size)
    }
}
