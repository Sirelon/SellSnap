package com.sirelon.sellsnap.features.seller.auth.presentation

interface SellerAuthContract {

    data class SellerAuthState(
        val status: SellerAuthStatus = SellerAuthStatus.Idle,
        val errorMessage: String? = null,
    )

    enum class SellerAuthStatus {
        Idle,
        Processing,
        Authorized,
        Error,
    }

    sealed interface SellerAuthEvent {
        data object OlxAuthClicked : SellerAuthEvent
        data object ContinueAsGuestClicked : SellerAuthEvent
        data object OnTermsClicked : SellerAuthEvent
        data object OnPrivacyClicked : SellerAuthEvent
    }

    sealed interface SellerAuthEffect {
        data class LaunchOlxAuthFlow(val url: String) : SellerAuthEffect
        data object OpenHome: SellerAuthEffect
        data class LaunchBrowser(val url: String) : SellerAuthEffect
        data class ShowMessage(val message: String) : SellerAuthEffect
    }
}
