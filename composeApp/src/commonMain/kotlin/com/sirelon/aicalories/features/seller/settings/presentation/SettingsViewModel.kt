package com.sirelon.sellsnap.features.seller.settings.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsRepository
import com.sirelon.sellsnap.features.notifications.presentation.NotificationsTapAction
import com.sirelon.sellsnap.features.notifications.presentation.notificationsTapAction
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsEffect
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsEvent
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsState
import com.sirelon.sellsnap.platform.PlatformTargets
import com.sirelon.sellsnap.startup.AnalyticsConsent
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themeRepository: AppThemeRepository,
    private val analyticsConsentRepository: AnalyticsConsentRepository,
    private val notificationsRepository: PushNotificationsRepository,
    private val isAndroid: Boolean = PlatformTargets.isAndroid(),
) : BaseViewModel<SettingsState, SettingsEvent, SettingsEffect>() {

    init {
        themeRepository
            .themeMode
            .onEach { themeMode ->
                setState { it.copy(themeMode = themeMode) }
            }
            .launchIn(viewModelScope)

        analyticsConsentRepository
            .consent
            .onEach { consent ->
                setState { it.copy(analyticsConsentGranted = consent == AnalyticsConsent.Granted) }
            }
            .launchIn(viewModelScope)
    }

    override fun initialState(): SettingsState = SettingsState()

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ThemeModeSelected -> themeRepository.setThemeMode(event.themeMode)
            is SettingsEvent.SetAnalyticsConsent -> analyticsConsentRepository.setConsent(event.enabled)
            SettingsEvent.RefreshNotifications -> refreshNotifications()
            is SettingsEvent.NotificationsClicked -> onNotificationsClicked(event.shouldShowRationale)
        }
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            val enabled = notificationsRepository.refreshPermission()
            setState { it.copy(notificationsEnabled = enabled) }
        }
    }

    private fun onNotificationsClicked(shouldShowRationale: Boolean) {
        viewModelScope.launch {
            val action = notificationsTapAction(
                enabled = currentState().notificationsEnabled == true,
                osCanPrompt = notificationsRepository.canShowSystemPrompt(),
                isAndroid = isAndroid,
                wasRequested = notificationsRepository.wasPermissionRequested(),
                shouldShowRationale = shouldShowRationale,
            )
            when (action) {
                NotificationsTapAction.RequestPermission -> {
                    notificationsRepository.markPermissionRequested()
                    postEffect(SettingsEffect.RequestNotificationPermission)
                }
                NotificationsTapAction.OpenAppSettings -> postEffect(SettingsEffect.OpenAppSettings)
            }
        }
    }
}
