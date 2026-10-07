package com.sirelon.sellsnap.features.seller.settings.presentation

import com.sirelon.sellsnap.designsystem.AppThemeMode

interface SettingsContract {
    data class SettingsState(
        val themeMode: AppThemeMode = AppThemeMode.System,
        val analyticsConsentGranted: Boolean = false,
        /** Null until the first read, so the row shows no stale "Off" while it loads. */
        val notificationsEnabled: Boolean? = null,
    )

    sealed interface SettingsEvent {
        data class ThemeModeSelected(val themeMode: AppThemeMode) : SettingsEvent
        data class SetAnalyticsConsent(val enabled: Boolean) : SettingsEvent

        /** The screen came to the foreground, or the OS permission prompt was answered. */
        data object RefreshNotifications : SettingsEvent
        data class NotificationsClicked(val shouldShowRationale: Boolean) : SettingsEvent
    }

    sealed interface SettingsEffect {
        data object RequestNotificationPermission : SettingsEffect
        data object OpenAppSettings : SettingsEffect
    }
}
