package com.sirelon.sellsnap.features.seller.settings.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsEvent
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsState
import com.sirelon.sellsnap.startup.AnalyticsConsent
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SettingsViewModel(
    private val themeRepository: AppThemeRepository,
    private val analyticsConsentRepository: AnalyticsConsentRepository,
) : BaseViewModel<SettingsState, SettingsEvent, Nothing>() {

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
        }
    }
}
