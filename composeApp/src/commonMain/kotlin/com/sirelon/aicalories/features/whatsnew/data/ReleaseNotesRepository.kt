package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.features.whatsnew.model.Release

interface ReleaseNotesRepository {
    /**
     * All published releases, newest first. Empty when the fetch failed or nothing is
     * published — callers render an empty state, never an error.
     */
    suspend fun getReleases(): List<Release>
}
