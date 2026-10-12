package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.insights.InstallQueryResult
import com.sirelon.sellsnap.insights.InstallRow
import com.sirelon.sellsnap.insights.InstallSource
import com.sirelon.sellsnap.threads.AlertRequest
import java.time.LocalDate

/** Answers [rows] (and [alert]) and records the window of every call. */
class FakeInstallSource(
    var rows: List<InstallRow> = emptyList(),
    var alert: AlertRequest? = null,
) : InstallSource {
    val windows = mutableListOf<Pair<LocalDate, LocalDate>>()

    override suspend fun installs(from: LocalDate, to: LocalDate): InstallQueryResult {
        windows += from to to
        return InstallQueryResult(rows, alert)
    }
}
