package com.sirelon.sellsnap.features.seller.profile.presentation

import com.sirelon.sellsnap.designsystem.AppThemeMode
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.location.OlxLocation

interface ProfileContract {
    data class ProfileState(
        val isLoading: Boolean = true,
        val isAuthenticating: Boolean = false,
        val isLocationLoading: Boolean = false,
        val user: OlxUser? = null,
        val location: OlxLocation? = null,
        val themeMode: AppThemeMode = AppThemeMode.System,
        val errorMessage: String? = null,
    ) {
        val isGuest: Boolean
            get() = user == null
    }

    sealed interface ProfileEvent {
        data object LoginClicked : ProfileEvent
        data object LogoutClicked : ProfileEvent
        data object ChangeLocationClicked : ProfileEvent
        data object RefreshClicked : ProfileEvent
        data class ThemeModeSelected(val themeMode: AppThemeMode) : ProfileEvent
    }

    sealed interface ProfileEffect {
        data class LaunchOlxAuthFlow(val url: String) : ProfileEffect
        data class ShowMessage(val message: String) : ProfileEffect
        data object NavigateToLanding : ProfileEffect
    }
}
