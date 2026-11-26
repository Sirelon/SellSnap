package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.serialization.KSerializer

private const val APP_DEEP_LINK_SCHEME = "sellsnap"

/** One `sellsnap://<path>` link and the [AppKey] it opens, matched by Navigation 3. */
private class AppDeepLinkRoute<T : AppKey>(val path: String, serializer: KSerializer<T>) {
    val matcher = UriDeepLinkMatcher(DeepLinkUri("$APP_DEEP_LINK_SCHEME://$path"), serializer)
}

// The screens a `sellsnap://` link opens, from an announcement button, a push `link` data key or
// the OS (Android intent filter, iOS URL scheme). A new screen is one more route; a route with
// `{argument}` placeholders fills the key's fields. Keep scripts/publish-announcement.mjs
// IN_APP_PATHS in step.
private val appDeepLinkRoutes = listOf(
    // The Profile tab, where a seller connects or reconnects OLX.
    AppDeepLinkRoute("account", AppKey.Profile.serializer()),
    AppDeepLinkRoute("my-ads", AppKey.MyAdverts.serializer()),
    AppDeepLinkRoute("messages", AppKey.Messages.serializer()),
    AppDeepLinkRoute("generate", AppKey.GenerateAd.serializer()),
)

/** A link that matched a route: the screen to open and the `destination` analytics value. */
data class AppDeepLinkMatch(val key: AppKey, val analyticsCode: String)

/**
 * The screen `sellsnap://my-ads` opens, or null for any other link, including an unknown or
 * malformed one, so an app never guesses at a destination it does not have.
 */
fun matchAppDeepLink(link: String?): AppDeepLinkMatch? {
    val request = runCatching { DeepLinkRequest(link?.trim() ?: return null) }.getOrNull() ?: return null
    return appDeepLinkRoutes.firstNotNullOfOrNull { route ->
        runCatching { route.matcher.match(request) }.getOrNull()?.let { result ->
            AppDeepLinkMatch(key = result.key, analyticsCode = route.path.replace('-', '_'))
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

    data class Pending(val match: AppDeepLinkMatch, val source: String)

    private val pendingLink = MutableStateFlow<Pending?>(null)

    val pending: StateFlow<Pending?> = pendingLink.asStateFlow()

    /**
     * Queues [link] when it is an in-app link and returns true; returns false for anything else,
     * so the caller can handle it as before. [source] is one of the `SOURCE_*` values.
     */
    fun publish(link: String, source: String): Boolean {
        val match = matchAppDeepLink(link) ?: return false
        pendingLink.value = Pending(match, source)
        return true
    }

    fun consume(): Pending? = pendingLink.getAndUpdate { null }
}
