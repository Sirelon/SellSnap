package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.features.whatsnew.data.response.ReleaseChangeResponse
import com.sirelon.sellsnap.features.whatsnew.data.response.ReleaseResponse
import com.sirelon.sellsnap.features.whatsnew.model.Release

internal const val FALLBACK_LANGUAGE_CODE = "en"

internal fun ReleaseResponse.toDomain(languageCode: String): Release? {
    if (active == false) return null
    val version = version?.takeIf { it.isNotBlank() } ?: return null
    val date = date?.takeIf { it.isNotBlank() } ?: return null
    val mappedChanges = changes.orEmpty().mapNotNull { it.toDomain(languageCode) }
    if (mappedChanges.isEmpty()) return null
    return Release(version = version, date = date, changes = mappedChanges)
}

private fun ReleaseChangeResponse.toDomain(languageCode: String): Release.Change? {
    val title = title.resolve(languageCode) ?: return null
    return Release.Change(
        id = id?.takeIf { it.isNotBlank() } ?: title,
        icon = icon.orEmpty(),
        title = title,
        summary = summary.resolve(languageCode).orEmpty(),
        detail = detail.resolve(languageCode),
    )
}

// Falls back to English when a translation is missing, rather than dropping the change —
// content is hand-edited in Firestore and a locale won't always be filled in right away.
private fun Map<String, String>?.resolve(languageCode: String): String? =
    this?.get(languageCode)?.takeIf { it.isNotBlank() }
        ?: this?.get(FALLBACK_LANGUAGE_CODE)?.takeIf { it.isNotBlank() }
