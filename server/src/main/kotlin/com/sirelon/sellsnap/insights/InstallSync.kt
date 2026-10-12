package com.sirelon.sellsnap.insights

import com.sirelon.sellsnap.model.SocialInstall
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.tick.Alerts
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.ZoneOffset

/** What one sync did. */
data class InstallSyncOutcome(
    /** `social_installs` documents written. */
    val written: Int = 0,
    /** Rows whose post code matches no post; they are left out. */
    val unknownCodes: Int = 0,
)

/**
 * Copies Android install attribution from an [InstallSource] into `social_installs/{yyyymmdd}_{code}`. Each
 * document holds the count that the source reports for that install day and post code, so a run over the same
 * window writes the same documents again (idempotent), and a later run, which sees installs that the export
 * delivered late, raises a count. The window is the last [WINDOW_DAYS] days. The `accountId` comes from the post that
 * the code names; a code that names no post is skipped. A failed read keeps what is stored, and its alert goes to the owner once a day.
 */
class InstallSync(
    private val store: SocialStore,
    private val source: InstallSource,
    private val alerts: Alerts,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(InstallSync::class.java)

    suspend fun run(): InstallSyncOutcome {
        val now = clock.instant()
        val to = now.atOffset(ZoneOffset.UTC).toLocalDate()
        val result = source.installs(to.minusDays(WINDOW_DAYS), to)
        result.alert?.let { alert ->
            try {
                alerts.sendOnce(alert)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("The install sync alert was not delivered: {}", e::class.simpleName)
            }
        }

        var written = 0
        var unknown = 0
        for (row in result.rows) {
            val post = store.post(row.code)
            if (post == null) {
                unknown++
                continue
            }
            store.saveInstall(SocialInstall(post.accountId, post.code, row.day, row.installs, now))
            written++
        }
        return InstallSyncOutcome(written, unknown)
    }

    private companion object {
        const val WINDOW_DAYS = 14L
    }
}
