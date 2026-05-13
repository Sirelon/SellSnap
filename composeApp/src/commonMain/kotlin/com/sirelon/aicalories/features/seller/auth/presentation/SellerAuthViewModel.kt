package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_olx_auth_complete_failed
import com.sirelon.sellsnap.generated.resources.error_olx_auth_prepare_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class SellerAuthViewModel(
    private val authRepository: OlxAuthRepository,
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
                // TODO: correct path
                postEffect(SellerAuthContract.SellerAuthEffect.LaunchBrowser("https:google.com"))
            }

            SellerAuthContract.SellerAuthEvent.OnTermsClicked -> {
                // TODO: correct path
                postEffect(SellerAuthContract.SellerAuthEffect.LaunchBrowser("https:google.com"))
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
                    setState {
                        it.copy(
                            status = SellerAuthContract.SellerAuthStatus.Authorized,
                            errorMessage = null,
                        )
                    }
                    postEffect(SellerAuthContract.SellerAuthEffect.OpenHome)
                }
                .onFailure { error ->
                    showError(getString(Res.string.error_olx_auth_complete_failed))
                }
        }
    }

    private fun startAuthorization() {
        viewModelScope.launch {
            runCatching { authRepository.createAuthorizationRequest() }
                .onSuccess { request ->
                    setState {
                        it.copy(
                            status = SellerAuthContract.SellerAuthStatus.Processing,
                            errorMessage = null,
                        )
                    }
                    postEffect(SellerAuthContract.SellerAuthEffect.LaunchOlxAuthFlow(request.url))
                    setState {
                        it.copy(
                            status = SellerAuthContract.SellerAuthStatus.Processing,
                        )
                    }
                }
                .onFailure { error ->
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
}
