package com.sirelon.sellsnap.brand

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The in-app "What's New" content the drafter may announce. */
interface ReleaseNotesSource {
    /** The active releases, newest first. Only the en, uk and pl texts are read. */
    suspend fun activeReleases(): List<ReleaseNotes>
}

class ReleaseNotes(val version: String, val date: String, val changes: List<ReleaseChange>)

/** [title] and [summary] by language code, for [BRAND_LANGUAGES] only. */
class ReleaseChange(val id: String, val title: Map<String, String>, val summary: Map<String, String>)

/**
 * Reads the `release-notes` collection that `scripts/publish-release-notes.mjs` writes: one document
 * per version holding `version`, `date`, `active` and `changes[]` of `{id, icon, title{lang}, summary{lang}}`.
 */
class FirestoreReleaseNotesSource(private val firestore: Firestore) : ReleaseNotesSource {
    override suspend fun activeReleases(): List<ReleaseNotes> = withContext(Dispatchers.IO) {
        firestore.collection(COLLECTION).get().get().documents
            .mapNotNull { releaseFromMap(it.id, it.data) }
            .sortedByDescending { it.date }
    }

    private companion object {
        const val COLLECTION = "release-notes"
    }
}

/** Null for an inactive release; every other field falls back to empty. A missing `active` counts as active, as in the app. */
internal fun releaseFromMap(id: String, data: Map<String, Any?>): ReleaseNotes? {
    if (data["active"] == false) return null
    val changes = (data["changes"] as List<*>?).orEmpty().mapNotNull { entry ->
        val change = entry as? Map<*, *> ?: return@mapNotNull null
        ReleaseChange(
            id = change["id"] as? String ?: return@mapNotNull null,
            title = change.texts("title"),
            summary = change.texts("summary"),
        )
    }
    return ReleaseNotes(
        version = data["version"] as? String ?: id,
        date = data["date"] as? String ?: "",
        changes = changes,
    )
}

private fun Map<*, *>.texts(key: String): Map<String, String> {
    val byLanguage = this[key] as? Map<*, *> ?: return emptyMap()
    return BRAND_LANGUAGES.mapNotNull { language -> (byLanguage[language] as? String)?.let { language to it } }.toMap()
}
