package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.data.response.AnnouncementResponse
import com.sirelon.sellsnap.features.announcements.model.Announcement
import com.sirelon.sellsnap.features.whatsnew.data.FALLBACK_LANGUAGE_CODE

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
    )
}

// The app renders Ukrainian for a Russian-locale device (values-ru is a copy of values-uk), so an
// announcement is read in Ukrainian there too; otherwise it would fall through to English.
// Falls back to English when a translation is missing rather than dropping the announcement -
// content is hand-edited in Firestore and a locale won't always be filled in right away.
private fun Map<String, String>?.resolve(languageCode: String): String? {
    val code = if (languageCode == "ru") "uk" else languageCode
    return this?.get(code)?.takeIf { it.isNotBlank() }
        ?: this?.get(FALLBACK_LANGUAGE_CODE)?.takeIf { it.isNotBlank() }
}

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
 * version is inside its inclusive [minVersion, maxVersion] range, the device is targeted, and it
 * is not a `once` announcement already in [seenIds]. Highest priority wins; a tie goes to the
 * lowest id so the outcome never depends on Firestore's document order.
 *
 * Targeting: no `userIds` and no `olxUserIds` means everyone. Otherwise the device matches when
 * its [installationId] is in `userIds` OR any of its [olxUserIds] is in `olxUserIds`.
 */
internal fun selectAnnouncement(
    announcements: List<Announcement>,
    appVersion: String,
    installationId: String?,
    olxUserIds: Set<Long>,
    seenIds: Set<String>,
): Announcement? = announcements
    .filter { it.isInVersionRange(appVersion) }
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
