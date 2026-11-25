package com.sirelon.sellsnap.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * A screen inside the app that a `sellsnap://<path>` link opens. The same link works from an
 * announcement button, a push `link` data key and the OS (Android intent filter, iOS URL scheme).
 * A new destination is one more entry here; [parse] needs no change.
 */
enum class AppDeepLink(val path: String, val destination: AppKey) {
    /** The Profile tab, where a seller connects or reconnects OLX. */
    Account("account", AppKey.Profile()),
    MyAds("my-ads", AppKey.MyAdverts),
    Messages("messages", AppKey.Messages),
    GenerateListing("generate", AppKey.GenerateAd),
    ;

    val url: String get() = "$SCHEME://$path"

    /** The `destination` analytics value. */
    val analyticsCode: String get() = path.replace('-', '_')

    companion object {
        const val SCHEME = "sellsnap"

        /**
         * `sellsnap://my-ads` -> [MyAds]. Scheme and path are case-insensitive; a trailing slash,
         * query or fragment is ignored. Null for any other link, including an unknown path, so an
         * older app never guesses at a destination it does not have.
         */
        fun parse(link: String?): AppDeepLink? {
            val trimmed = link?.trim() ?: return null
            val prefix = "$SCHEME://"
            if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
            val path = trimmed.substring(prefix.length)
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .lowercase()
            return entries.firstOrNull { it.path == path }
        }
    }
}

/**
 * Hands an in-app link from where it arrived (announcement button, push tap, OS) to
 * [com.sirelon.sellsnap.startup.AppNavigationViewModel], which opens it once startup routing is
 * done and the seller is in the main flow. Holds one link: a newer one replaces an unopened one.
 * Mirrors SharedImagesBridge.
 */
object AppDeepLinkBridge {
    /** `source` analytics values. */
    const val SOURCE_ANNOUNCEMENT = "announcement"
    const val SOURCE_PUSH = "push"
    const val SOURCE_EXTERNAL = "external"

    data class Pending(val link: AppDeepLink, val source: String)

    private val pendingLink = MutableStateFlow<Pending?>(null)

    val pending: StateFlow<Pending?> = pendingLink.asStateFlow()

    /**
     * Queues [link] when it is an in-app link and returns true; returns false for anything else,
     * so the caller can handle it as before. [source] is one of the `SOURCE_*` values.
     */
    fun publish(link: String, source: String): Boolean {
        val deepLink = AppDeepLink.parse(link) ?: return false
        pendingLink.value = Pending(deepLink, source)
        return true
    }

    fun consume(): Pending? = pendingLink.getAndUpdate { null }
}
