package com.sirelon.sellsnap.features.seller.settings.presentation

import com.sirelon.sellsnap.designsystem.AppThemeMode

interface SettingsContract {
    data class SettingsState(
        val themeMode: AppThemeMode = AppThemeMode.System,
        val analyticsConsentGranted: Boolean = false,
    )

    sealed interface SettingsEvent {
        data class ThemeModeSelected(val themeMode: AppThemeMode) : SettingsEvent
        data class SetAnalyticsConsent(val enabled: Boolean) : SettingsEvent
    }
}
