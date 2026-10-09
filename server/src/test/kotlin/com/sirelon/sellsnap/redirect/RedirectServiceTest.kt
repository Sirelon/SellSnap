package com.sirelon.sellsnap.redirect

import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RedirectServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val store = InMemorySocialStore().also {
        it.put(testAccount())
        it.put(testPost())
        it.put(testPost(id = "stt0002"))
    }
    private val links = StoreLinks("com.sirelon.sellsnap", "6776001373", null, "https://sellsnap.test/landing")
    private val android = "Mozilla/5.0 (Linux; Android 14)"

    @Test
    fun clicksAreCappedPerCodePerDay() = runTest {
        val service = RedirectService(store, links, clock, clickCapPerCodePerDay = 3)
        repeat(5) { service.resolve("stt0001", android) }
        repeat(2) { service.resolve("stt0002", android) }
        assertEquals(3, store.clicks.count { it.code == "stt0001" })
        assertEquals(2, store.clicks.count { it.code == "stt0002" })

        clock.advance(Duration.ofDays(1))
        service.resolve("stt0001", android)
        assertEquals(4, store.clicks.count { it.code == "stt0001" })
    }

    @Test
    fun aCappedClickStillRedirects() = runTest {
        val service = RedirectService(store, links, clock, clickCapPerCodePerDay = 1)
        val first = service.resolve("stt0001", android)
        val second = service.resolve("stt0001", android)
        assertEquals(first, second)
    }

    @Test
    fun aMissIsLookedUpAgainAfterItsTimeToLive() = runTest {
        val service = RedirectService(store, links, clock)
        assertEquals(links.landingUrl, service.resolve("stt0003", android))
        store.put(testPost(id = "stt0003"))
        assertEquals(links.landingUrl, service.resolve("stt0003", android))
        clock.advance(Duration.ofSeconds(61))
        assertEquals(links.play("uk", "stt0003"), service.resolve("stt0003", android))
    }

    @Test
    fun crawlersAreBotsAndPhoneModelsAreNot() {
        val bots = listOf(
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
            "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X) (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "TelegramBot (like TwitterBot)",
            "WhatsApp/2.23.20.0 A",
            "SomeCrawler/1.0",
        )
        bots.forEach { assertEquals(true, RedirectService.isBot(it), it) }
        val people = listOf(
            "Mozilla/5.0 (Linux; Android 10; CUBOT X30 Build/QP1A.190711.020) Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) Mobile/15E148 Barcelona 350.0",
            "Mozilla/5.0 (Linux; Android 14; SM-S911B) Chrome/129.0 Mobile Safari/537.36",
        )
        people.forEach { assertEquals(false, RedirectService.isBot(it), it) }
    }

    @Test
    fun userAgentFamilies() {
        assertEquals(UaFamily.Android, RedirectService.classify("Mozilla/5.0 (Linux; Android 14)"))
        assertEquals(UaFamily.Ios, RedirectService.classify("Mozilla/5.0 (iPad; CPU OS 17_0)"))
        assertEquals(UaFamily.Other, RedirectService.classify("Mozilla/5.0 (X11; Linux x86_64)"))
        assertEquals(UaFamily.Other, RedirectService.classify(null))
    }
}
