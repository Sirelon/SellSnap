package com.sirelon.sellsnap.insights

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.FakeInstallSource
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.SocialInstall
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.tick.Alerts
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallSyncTest {
    private val fx = AgentFixture()
    private val source = FakeInstallSource()
    private val sync = InstallSync(fx.store, source, Alerts(fx.store, fx.telegram, fx.clock, AgentFixture.OWNER), fx.clock)

    private fun posts() {
        fx.put(testPost("ukt0001", "ua"))
        fx.put(testPost("ukd0002", "ua"))
        fx.put(testPost("plt0001", "pl"))
    }

    @Test
    fun everyKnownCodeBecomesADocumentOfItsAccountAndDayAndUnknownCodesAreSkipped() = runTest {
        posts()
        source.rows = listOf(
            InstallRow("20261010", "ukt0001", 2),
            InstallRow("20261011", "ukt0001", 1),
            InstallRow("20261011", "plt0001", 4),
            InstallRow("20261011", "zzz0009", 5),
        )

        val outcome = sync.run()

        assertEquals(InstallSyncOutcome(written = 3, unknownCodes = 1), outcome)
        val now = fx.clock.instant()
        assertEquals(
            mapOf(
                "20261010_ukt0001" to SocialInstall("ua", "ukt0001", "20261010", 2, now),
                "20261011_ukt0001" to SocialInstall("ua", "ukt0001", "20261011", 1, now),
                "20261011_plt0001" to SocialInstall("pl", "plt0001", "20261011", 4, now),
            ),
            fx.memory.allInstalls,
        )
    }

    @Test
    fun theWindowIsTheLastFourteenDays() = runTest {
        sync.run()

        assertEquals(listOf(LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 12)), source.windows)
    }

    @Test
    fun aRerunWritesTheSameDocumentsAndALaterCountReplacesTheEarlierOne() = runTest {
        posts()
        source.rows = listOf(InstallRow("20261010", "ukt0001", 2))
        sync.run()
        val first = fx.memory.allInstalls

        sync.run()
        assertEquals(first, fx.memory.allInstalls, "the same data leaves the same documents")

        // The export delivers a late install for the same day.
        fx.clock.advance(Duration.ofDays(2))
        source.rows = listOf(InstallRow("20261010", "ukt0001", 3))
        sync.run()

        val stored = fx.memory.allInstalls.values.single()
        assertEquals(3, stored.installs)
        assertEquals(fx.clock.instant(), stored.syncedAt)
    }

    @Test
    fun aFailedReadKeepsWhatIsStoredAndAlertsOncePerDay() = runTest {
        posts()
        source.rows = listOf(InstallRow("20261010", "ukt0001", 2))
        sync.run()
        val stored = fx.memory.allInstalls

        source.rows = emptyList()
        source.alert = AlertRequest(AlertType.InstallSyncFailed, AlertRequest.INSTALL_SYNC_ACCOUNT, detail = "HTTP 403 PERMISSION_DENIED")
        sync.run()
        sync.run()

        assertEquals(stored, fx.memory.allInstalls)
        val alerts = fx.sentTexts()
        assertEquals(1, alerts.size)
        assertTrue(alerts.single().startsWith("Reading Android install attribution from BigQuery failed: HTTP 403 PERMISSION_DENIED."))
    }
}
