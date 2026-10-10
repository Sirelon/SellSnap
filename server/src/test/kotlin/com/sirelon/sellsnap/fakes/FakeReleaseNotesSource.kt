package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.brand.ReleaseNotesSource

class FakeReleaseNotesSource(private val releases: List<ReleaseNotes> = emptyList()) : ReleaseNotesSource {
    override suspend fun activeReleases(): List<ReleaseNotes> = releases
}
