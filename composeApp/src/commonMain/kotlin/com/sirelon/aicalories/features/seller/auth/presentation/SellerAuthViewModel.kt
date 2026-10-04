package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.analyticsReason
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_olx_auth_complete_failed
import com.sirelon.sellsnap.generated.resources.error_olx_auth_prepare_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class SellerAuthViewModel(
    private val authRepository: OlxAuthRepository,
    // SIR-83: first-connect must land in the multi-account store, so this now goes through
    // SellerAccountRepository.completeAuthorization (which persists) rather than calling
    // OlxAuthRepository.completeAuthorization directly - that method no longer persists anything.
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
    private val olxCountryStore: OlxCountryStore,
) : BaseViewModel<SellerAuthContract.SellerAuthState, SellerAuthContract.SellerAuthEvent, SellerAuthContract.SellerAuthEffect>() {

    // Set when auth_started is logged; auth_abandoned reports the time since then.
    private var authStartedAt: TimeMark? = null

    override fun initialState(): SellerAuthContract.SellerAuthState =
        SellerAuthContract.SellerAuthState()

    override fun onEvent(event: SellerAuthContract.SellerAuthEvent) {
        when (event) {
            SellerAuthContract.SellerAuthEvent.OlxAuthClicked ->
                postEffect(SellerAuthContract.SellerAuthEffect.NavigateToCountryPicker)

            // Stored on tap, not only on Continue: a seller who picks a country and then backs
            // out, or closes the OLX login and picks "Not now", keeps the market they chose -
            // guest listings are written and priced for it (owner, 2026-09-25).
            is SellerAuthContract.SellerAuthEvent.CountrySelected -> {
                viewModelScope.launch { olxCountryStore.save(event.country) }
            }

            is SellerAuthContract.SellerAuthEvent.CountryConfirmed -> {
                viewModelScope.launch {
                    olxCountryStore.save(event.country)
                    startAuthorization()
                }
            }

            SellerAuthContract.SellerAuthEvent.ContinueAsGuestClicked -> {
                viewModelScope.launch {
                    authRepository.enterGuestMode()
                    postEffect(SellerAuthContract.SellerAuthEffect.OpenHome)
                }
            }

            SellerAuthContract.SellerAuthEvent.OnPrivacyClicked -> {
                postEffect(SellerAuthContract.SellerAuthEffect.LaunchBrowser(PRIVACY_POLICY_URL))
            }

            SellerAuthContract.SellerAuthEvent.OnTermsClicked -> {
                postEffect(SellerAuthContract.SellerAuthEffect.LaunchBrowser(TERMS_AND_CONDITIONS_URL))
            }

            is SellerAuthContract.SellerAuthEvent.OlxAuthDismissed -> {
                analytics.logEvent(
                    AnalyticsEvents.AUTH_ABANDONED,
                    authParams(reason = event.reason.analyticsValue) + abandonDuration(),
                )
                // First-connect only (this VM backs the landing/onboarding flow, never Profile's
                // add-account). Guest mode is offered, not entered: switching the moment the login
                // closed read as the app deciding for the seller (owner, 2026-09-25). Never
                // relaunch login automatically - OLX bans accounts after repeated failures.
                setState {
                    it.copy(
                        status = SellerAuthContract.SellerAuthStatus.Idle,
                        errorMessage = null,
                        showLoginClosedSheet = true,
                    )
                }
            }

            SellerAuthContract.SellerAuthEvent.LoginClosedGuestChosen -> {
                setState { it.copy(showLoginClosedSheet = false) }
                viewModelScope.launch {
                    authRepository.enterGuestMode(showConnectLaterHint = true)
                    postEffect(SellerAuthContract.SellerAuthEffect.OpenHome)
                }
            }

            SellerAuthContract.SellerAuthEvent.LoginClosedSheetDismissed -> {
                setState { it.copy(showLoginClosedSheet = false) }
            }
        }
    }

    fun onCallbackReceived(callbackUrl: String) {
        viewModelScope.launch {
            setState {
                it.copy(
                    status = SellerAuthContract.SellerAuthStatus.Processing,
                    errorMessage = null,
                )
            }

            accountRepository.completeAuthorization(callbackUrl)
                .onSuccess {
                    analytics.logEvent(AnalyticsEvents.AUTH_COMPLETED)
                    setState {
                        it.copy(
                            status = SellerAuthContract.SellerAuthStatus.Authorized,
                            errorMessage = null,
                        )
                    }
                    postEffect(SellerAuthContract.SellerAuthEffect.OpenHome)
                }
                .onFailure { error ->
                    analytics.logEvent(AnalyticsEvents.AUTH_FAILED, authParams(reason = error.authFailureReason()))
                    showError(getString(Res.string.error_olx_auth_complete_failed))
                }
        }
    }

    private suspend fun startAuthorization() {
        analytics.logEvent(AnalyticsEvents.AUTH_STARTED)
        authStartedAt = TimeSource.Monotonic.markNow()
        setState {
            it.copy(
                status = SellerAuthContract.SellerAuthStatus.Processing,
                errorMessage = null,
            )
        }
        runCatching { authRepository.createAuthorizationRequest() }
            .onSuccess { request ->
                setState {
                    it.copy(
                        status = SellerAuthContract.SellerAuthStatus.Idle,
                        errorMessage = null,
                    )
                }
                postEffect(SellerAuthContract.SellerAuthEffect.LaunchOlxAuthFlow(request.url))
            }
            .onFailure {
                analytics.logEvent(AnalyticsEvents.AUTH_FAILED, authParams(reason = "prepare_failed"))
                showError(getString(Res.string.error_olx_auth_prepare_failed))
            }
    }

    private fun authParams(reason: String): Map<String, Any> =
        mapOf("reason" to reason, "country" to olxCountryStore.current.code)

    private fun abandonDuration(): Map<String, Any> =
        authStartedAt?.let { mapOf("duration_ms" to it.elapsedNow().inWholeMilliseconds) }.orEmpty()

    private fun Throwable.authFailureReason(): String =
        (this as? OlxApiException)?.error?.analyticsReason ?: "unknown"

    private fun showError(message: String) {
        viewModelScope.launch {
            setState {
                it.copy(
                    status = SellerAuthContract.SellerAuthStatus.Error,
                    errorMessage = message,
                )
            }
            postEffect(SellerAuthContract.SellerAuthEffect.ShowMessage(message))
        }
    }

    private companion object {
        // If app data flows change (OLX scope, Supabase, location, camera, new SDKs),
        // update these pages before the next release.
        const val TERMS_AND_CONDITIONS_URL = "https://sirelon.github.io/SellSnap/terms-and-conditions/"
        const val PRIVACY_POLICY_URL = "https://sirelon.github.io/SellSnap/privacy-policy/"
    }
}
