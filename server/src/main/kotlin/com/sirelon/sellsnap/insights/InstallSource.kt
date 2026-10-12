package com.sirelon.sellsnap.insights

import com.sirelon.sellsnap.threads.AlertRequest
import java.time.LocalDate

/** Installs of one post code that were first opened on [day] (`yyyymmdd`). */
data class InstallRow(val day: String, val code: String, val installs: Long)

/**
 * What one read of the attribution data came to. A failed read is an empty [rows] list and an [alert] that
 * the caller sends; a source never throws for a failed read.
 */
data class InstallQueryResult(
    val rows: List<InstallRow>,
    val alert: AlertRequest? = null,
)

/** Where Android install attribution comes from: Android installs that carry a Threads post code. */
interface InstallSource {
    /** The installs whose `first_open` falls between [from] and [to], both included, grouped by day and post code. */
    suspend fun installs(from: LocalDate, to: LocalDate): InstallQueryResult
}
