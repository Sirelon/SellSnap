package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.analytics.Analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyticsConsentRepository(
    private val store: AnalyticsConsentStore,
    private val analytics: Analytics,
    private val applicationScope: CoroutineScope,
) {
    private val _consent = MutableStateFlow(AnalyticsConsent.Undecided)
    val consent: StateFlow<AnalyticsConsent> = _consent.asStateFlow()

    init {
        // Re-apply the persisted decision to the SDK on every launch. Constructing this repository
        // at startup also forces the platform Analytics impl to be created before any event fires.
        applicationScope.launch {
            val saved = store.read()
            // Only update if no explicit setConsent() call has already landed (race guard).
            _consent.update { current -> if (current == AnalyticsConsent.Undecided) saved else current }
            analytics.setAnalyticsCollectionEnabled(_consent.value == AnalyticsConsent.Granted)

            // Crash reporting always runs (anonymous, legitimate interest). Force it on to also
            // re-enable anyone who had opted out in an earlier build that exposed a toggle.
            analytics.setCrashlyticsCollectionEnabled(true)
        }
    }

    /** Fresh read used by startup routing; avoids racing the async [consent] flow init. */
    suspend fun currentConsent(): AnalyticsConsent = store.read()

    fun setConsent(granted: Boolean) {
        val value = if (granted) AnalyticsConsent.Granted else AnalyticsConsent.Denied
        _consent.value = value
        analytics.setAnalyticsCollectionEnabled(granted)
        applicationScope.launch {
            store.write(value)
        }
    }

    /** Resets analytics consent to [AnalyticsConsent.Undecided] and disables analytics. Called on data erasure. */
    fun resetConsent() {
        _consent.value = AnalyticsConsent.Undecided
        analytics.setAnalyticsCollectionEnabled(false)
        applicationScope.launch {
            store.write(AnalyticsConsent.Undecided)
        }
    }
}
