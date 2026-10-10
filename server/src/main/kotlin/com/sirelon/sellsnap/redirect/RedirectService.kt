package com.sirelon.sellsnap.redirect

import com.sirelon.sellsnap.content.PostCode
import com.sirelon.sellsnap.model.SocialClick
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.threads.redact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** What the website reports for a post code through `POST /click`, and the `store` value it is logged under. */
enum class ClickEvent(val wire: String, val store: String) {
    View("view", "landing"),
    Play("play", "play"),
    AppStore("appstore", "appstore");

    companion object {
        fun fromWire(value: String): ClickEvent? = entries.firstOrNull { it.wire == value }
    }
}

enum class UaFamily(val key: String) {
    Android("android"),
    Ios("ios"),
    Other("other"),
}

/** Where `/go/{code}` sends a visitor. */
class StoreLinks(
    private val playPackage: String,
    private val appStoreId: String,
    private val appleProviderToken: String?,
    val landingUrl: String,
) {
    fun play(language: String, code: String): String {
        val referrer = "utm_source=threads&utm_medium=social&utm_campaign=$language&utm_content=$code"
        return "https://play.google.com/store/apps/details?id=$playPackage&referrer=${encode(referrer)}"
    }

    fun appStore(code: String): String {
        val provider = appleProviderToken?.let { "&pt=${encode(it)}" }.orEmpty()
        return "https://apps.apple.com/app/id$appStoreId?ct=$code$provider"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}

/**
 * Serves `/go/{code}` (D15) and records the visits and store taps the website reports (`POST /click`). A code is checked for shape before anything is read. Known codes and
 * misses are cached in memory, so repeated or random requests cost no more reads. Crawlers get the
 * landing page and are never logged. Clicks are logged without cookies or IP, at most
 * [clickCapPerCodePerDay] per code and UTC day on each instance.
 */
class RedirectService(
    private val store: SocialStore,
    private val links: StoreLinks,
    private val clock: Clock,
    private val clickCapPerCodePerDay: Int = DEFAULT_CLICK_CAP,
) {
    private val log = LoggerFactory.getLogger(RedirectService::class.java)

    /** Language per known code; a code's post and its account's language never change. */
    private val known = ConcurrentHashMap<String, String>()

    /** Expiry of each cached miss. */
    private val misses = ConcurrentHashMap<String, Instant>()

    private class DayCount(val day: LocalDate, var count: Int)

    private val clicks = ConcurrentHashMap<String, DayCount>()

    /** The URL to redirect to for [code] and the request's [userAgent]. Never throws except on cancellation. */
    suspend fun resolve(code: String, userAgent: String?): String {
        if (!PostCode.isValid(code)) return links.landingUrl
        if (isBot(userAgent)) return links.landingUrl
        val language = languageOf(code) ?: return links.landingUrl

        val family = classify(userAgent)
        val (target, storeName) = when (family) {
            UaFamily.Android -> links.play(language, code) to "play"
            UaFamily.Ios -> links.appStore(code) to "appstore"
            UaFamily.Other -> links.landingUrl to "landing"
        }
        logClick(code, storeName, family)
        return target
    }

    /**
     * Logs a visit or store tap that the website reports for [code] (`POST /click`). The checks run
     * in order: code shape, known code (cached), crawler, per-code cap. Never throws except on cancellation.
     */
    suspend fun report(code: String, event: ClickEvent, userAgent: String?) {
        if (!PostCode.isValid(code)) return
        languageOf(code) ?: return
        if (isBot(userAgent)) return
        logClick(code, event.store, classify(userAgent))
    }

    private suspend fun languageOf(code: String): String? {
        known[code]?.let { return it }
        val now = clock.instant()
        if (misses[code]?.isAfter(now) == true) return null
        val language = try {
            lookup(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed read is not a miss: answer with the landing page and look again next time.
            log.error("Redirect lookup failed: {}", redact(e::class.simpleName.orEmpty() + ": " + e.message.orEmpty()))
            return null
        }
        if (language != null) {
            if (known.size >= MAX_CACHED) known.clear()
            known[code] = language
        } else {
            if (misses.size >= MAX_CACHED) misses.clear()
            misses[code] = now.plus(MISS_TTL)
        }
        return language
    }

    private suspend fun lookup(code: String): String? {
        val post = store.post(code) ?: return null
        return store.account(post.accountId)?.language
    }

    private suspend fun logClick(code: String, storeName: String, family: UaFamily) {
        val now = clock.instant()
        if (!underCap(code, now)) return
        val click = SocialClick(
            id = UUID.randomUUID().toString(),
            code = code,
            ts = now,
            store = storeName,
            uaFamily = family.key,
            countryFromIp = null,
        )
        try {
            // The write is awaited inside the request: Cloud Run may throttle the CPU once the response goes out.
            withTimeoutOrNull(CLICK_WRITE_TIMEOUT) { store.logClick(click) }
                ?: log.error("Click write timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Click write failed: {}", redact(e::class.simpleName.orEmpty()))
        }
    }

    @Synchronized
    private fun underCap(code: String, now: Instant): Boolean {
        val day = now.atOffset(ZoneOffset.UTC).toLocalDate()
        val current = clicks[code]
        if (current == null || current.day != day) {
            if (clicks.size >= MAX_CACHED) clicks.clear()
            clicks[code] = DayCount(day, 1)
            return true
        }
        if (current.count >= clickCapPerCodePerDay) return false
        current.count++
        return true
    }

    companion object {
        /** Per instance, per code and UTC day. A real post draws a few dozen clicks; 500 stops a flood from filling the collection. */
        const val DEFAULT_CLICK_CAP = 500

        private const val MAX_CACHED = 10_000
        private val MISS_TTL = java.time.Duration.ofSeconds(60)
        private val CLICK_WRITE_TIMEOUT = 3.seconds

        /** Link-preview fetchers and crawlers named outright. */
        private val BOT_MARKERS = listOf(
            "facebookexternalhit", "meta-externalagent", "facebot", "googlebot", "bingbot", "applebot",
            "twitterbot", "telegrambot", "slackbot", "discordbot", "linkedinbot", "whatsapp", "yandexbot",
        )

        /**
         * Generic crawler shapes: a versioned `…bot/` token, a contact URL, or the words crawler and spider.
         * A bare "bot" is not enough: phone models such as CUBOT carry it.
         */
        private val GENERIC_BOT = Regex("bot/|crawler|spider|\\+https?://")

        fun isBot(userAgent: String?): Boolean {
            val ua = userAgent?.lowercase().orEmpty()
            return BOT_MARKERS.any { it in ua } || GENERIC_BOT.containsMatchIn(ua)
        }

        fun classify(userAgent: String?): UaFamily {
            val ua = userAgent?.lowercase().orEmpty()
            return when {
                "android" in ua -> UaFamily.Android
                "iphone" in ua || "ipad" in ua || "ipod" in ua -> UaFamily.Ios
                else -> UaFamily.Other
            }
        }
    }
}
