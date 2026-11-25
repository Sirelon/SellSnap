package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.data.response.AnnouncementResponse
import com.sirelon.sellsnap.features.announcements.model.Announcement
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.navigation.AppDeepLink
import com.sirelon.sellsnap.features.whatsnew.data.FALLBACK_LANGUAGE_CODE
import com.sirelon.sellsnap.platform.PlatformTargets
import com.sirelon.sellsnap.platform.appLanguageCode

private const val SHOW_MODE_EVERY_LAUNCH = "everyLaunch"

/**
 * Null when the document is inactive, has no title in [languageCode] or English, or carries a
 * version bound that is not a dotted number list. A malformed bound drops the document rather than
 * ignoring the bound: an announcement meant for "3.2 and older" must not reach everyone because
 * someone typed "3.2.x".
 */
internal fun AnnouncementResponse.toDomain(id: String, languageCode: String): Announcement? {
    if (active != true) return null
    val title = content?.title.resolve(languageCode) ?: return null
    val minVersion = rules?.minVersion?.trim()?.takeIf { it.isNotEmpty() }
    val maxVersion = rules?.maxVersion?.trim()?.takeIf { it.isNotEmpty() }
    if (minVersion != null && parseVersion(minVersion) == null) return null
    if (maxVersion != null && parseVersion(maxVersion) == null) return null
    return Announcement(
        id = id,
        title = title,
        body = content?.body.resolve(languageCode),
        imageUrl = content?.imageUrl?.takeIf { it.isNotBlank() },
        link = content?.link.normalizedLink(),
        buttonText = content?.buttonText.resolve(languageCode),
        dismissible = rules?.dismissible ?: true,
        // Anything but "everyLaunch" - including a typo - is "once": the quieter reading.
        showMode = if (rules?.showMode == SHOW_MODE_EVERY_LAUNCH) {
            Announcement.ShowMode.EveryLaunch
        } else {
            Announcement.ShowMode.Once
        },
        minVersion = minVersion,
        maxVersion = maxVersion,
        userIds = rules?.userIds.orEmpty().toSet(),
        olxUserIds = rules?.olxUserIds.orEmpty().toSet(),
        priority = rules?.priority ?: 0,
        // An unknown value such as a typo is kept, so it matches no device: the quieter reading.
        platforms = rules?.platforms.normalizedCodes(),
        olxConnections = rules?.olxConnection.normalizedCodes(),
    )
}

private fun List<String>?.normalizedCodes(): Set<String> = orEmpty()
    .map { it.trim().lowercase() }
    .filter { it.isNotEmpty() }
    .toSet()

internal const val ANNOUNCEMENT_PLATFORM_ANDROID = "android"
internal const val ANNOUNCEMENT_PLATFORM_IOS = "ios"
internal const val ANNOUNCEMENT_PLATFORM_WEB = "web"
internal const val ANNOUNCEMENT_PLATFORM_DESKTOP = "desktop"

/** This device's code for `rules.platforms`. */
internal fun currentAnnouncementPlatform(): String = when {
    PlatformTargets.isAndroid() -> ANNOUNCEMENT_PLATFORM_ANDROID
    PlatformTargets.isIos() -> ANNOUNCEMENT_PLATFORM_IOS
    PlatformTargets.isWeb() -> ANNOUNCEMENT_PLATFORM_WEB
    else -> ANNOUNCEMENT_PLATFORM_DESKTOP
}

/** The `link` value that opens this platform's store page rather than a URL. */
internal const val ANNOUNCEMENT_LINK_STORE = "store"

/** `rules.olxConnection` values: no OLX account on the device, one that needs reconnecting, one that works. */
internal const val OLX_CONNECTION_NONE = "none"
internal const val OLX_CONNECTION_EXPIRED = "expired"
internal const val OLX_CONNECTION_CONNECTED = "connected"

/**
 * Every `rules.olxConnection` value this device has, from the states of all its OLX accounts in
 * every country. A device with one working and one expired account is both `connected` and
 * `expired`, so a reconnect reminder reaches it.
 */
internal fun olxConnectionsOf(accountStates: List<OlxAccountState>): Set<String> =
    if (accountStates.isEmpty()) {
        setOf(OLX_CONNECTION_NONE)
    } else {
        accountStates.mapTo(mutableSetOf()) { state ->
            when (state) {
                OlxAccountState.Usable -> OLX_CONNECTION_CONNECTED
                OlxAccountState.NeedsReconnect -> OLX_CONNECTION_EXPIRED
            }
        }
    }

/** The `link_type` analytics value of a normalized link: `store`, `in_app` or `url`. */
internal fun announcementLinkType(link: String): String = when {
    link == ANNOUNCEMENT_LINK_STORE -> "store"
    AppDeepLink.parse(link) != null -> "in_app"
    else -> "url"
}

// Anything that is not the store keyword, an https URL or an in-app link this version knows is
// dropped rather than opened: a typo in the console must not hand the browser a bare word.
private fun String?.normalizedLink(): String? {
    val link = this?.trim() ?: return null
    if (link == ANNOUNCEMENT_LINK_STORE || link.startsWith("https://")) return link
    return AppDeepLink.parse(link)?.url
}

// Falls back to English when a translation is missing rather than dropping the announcement -
// content is hand-edited in Firestore and a locale won't always be filled in right away.
private fun Map<String, String>?.resolve(languageCode: String): String? =
    this?.get(appLanguageCode(languageCode))?.takeIf { it.isNotBlank() }
        ?: this?.get(FALLBACK_LANGUAGE_CODE)?.takeIf { it.isNotBlank() }

/** "3.10" -> [3, 10]. Null when any dot-separated segment is not a non-negative integer. */
internal fun parseVersion(version: String): List<Int>? {
    val segments = version.trim().split('.').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } }
    return if (segments.any { it == null }) null else segments.filterNotNull()
}

/**
 * Numeric comparison per dot segment, missing segments count as 0: "3.10" > "3.9" and
 * "3.3" == "3.3.0". Null when either side is malformed.
 */
internal fun compareVersions(a: String, b: String): Int? {
    val left = parseVersion(a) ?: return null
    val right = parseVersion(b) ?: return null
    for (index in 0 until maxOf(left.size, right.size)) {
        val diff = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
        if (diff != 0) return diff
    }
    return 0
}

/**
 * The one announcement to show on this launch, or null. An announcement is eligible when the app
 * version is inside its inclusive [minVersion, maxVersion] range, its `platforms` are empty or
 * contain [platform], its `olxConnection` is empty or shares a value with [olxConnections], the
 * device is targeted, and it is not a `once` announcement already in [seenIds]. Highest priority wins; a tie goes to the lowest id so the outcome never depends on
 * Firestore's document order.
 *
 * Targeting: no `userIds` and no `olxUserIds` means everyone. Otherwise the device matches when
 * its [installationId] is in `userIds` OR any of its [olxUserIds] is in `olxUserIds`.
 */
internal fun selectAnnouncement(
    announcements: List<Announcement>,
    appVersion: String,
    platform: String,
    installationId: String?,
    olxUserIds: Set<Long>,
    olxConnections: Set<String>,
    seenIds: Set<String>,
): Announcement? = announcements
    .filter { it.isInVersionRange(appVersion) }
    .filter { it.platforms.isEmpty() || platform in it.platforms }
    .filter { it.olxConnections.isEmpty() || olxConnections.any { state -> state in it.olxConnections } }
    .filter { it.targets(installationId, olxUserIds) }
    .filterNot { it.showMode == Announcement.ShowMode.Once && it.id in seenIds }
    .sortedWith(compareByDescending<Announcement> { it.priority }.thenBy { it.id })
    .firstOrNull()

private fun Announcement.isInVersionRange(appVersion: String): Boolean {
    // Fail closed: a bound we cannot compare against hides the announcement.
    if (minVersion != null && (compareVersions(appVersion, minVersion) ?: return false) < 0) return false
    if (maxVersion != null && (compareVersions(appVersion, maxVersion) ?: return false) > 0) return false
    return true
}

private fun Announcement.targets(installationId: String?, olxUserIds: Set<Long>): Boolean {
    if (userIds.isEmpty() && this.olxUserIds.isEmpty()) return true
    return (installationId != null && installationId in userIds) ||
        olxUserIds.any { it in this.olxUserIds }
}
