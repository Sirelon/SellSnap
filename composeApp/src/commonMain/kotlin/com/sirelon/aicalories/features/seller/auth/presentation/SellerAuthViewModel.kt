package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_olx_auth_complete_failed
import com.sirelon.sellsnap.generated.resources.error_olx_auth_prepare_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class SellerAuthViewModel(
    private val authRepository: OlxAuthRepository,
    private val analytics: Analytics,
) : BaseViewModel<SellerAuthContract.SellerAuthState, SellerAuthContract.SellerAuthEvent, SellerAuthContract.SellerAuthEffect>() {

    override fun initialState(): SellerAuthContract.SellerAuthState =
        SellerAuthContract.SellerAuthState()

    override fun onEvent(event: SellerAuthContract.SellerAuthEvent) {
        when (event) {
            SellerAuthContract.SellerAuthEvent.OlxAuthClicked -> startAuthorization()
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

            SellerAuthContract.SellerAuthEvent.OlxAuthDismissed -> {
                setState {
                    it.copy(
                        status = SellerAuthContract.SellerAuthStatus.Idle,
                        errorMessage = null,
                    )
                }
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

            authRepository.completeAuthorization(callbackUrl)
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
                    analytics.logEvent(AnalyticsEvents.AUTH_FAILED)
                    showError(getString(Res.string.error_olx_auth_complete_failed))
                }
        }
    }

    private fun startAuthorization() {
        analytics.logEvent(AnalyticsEvents.AUTH_STARTED)
        viewModelScope.launch {
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
                .onFailure { error ->
                    analytics.logEvent(AnalyticsEvents.AUTH_FAILED)
                    showError(getString(Res.string.error_olx_auth_prepare_failed))
                }
        }
    }

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
