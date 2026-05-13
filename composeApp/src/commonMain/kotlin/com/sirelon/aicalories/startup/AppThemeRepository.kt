package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.designsystem.AppThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppThemeRepository(
    private val store: AppThemePreferenceStore,
    private val applicationScope: CoroutineScope,
) {
    private val _themeMode = MutableStateFlow(AppThemeMode.System)
    private val hasManualSelection = MutableStateFlow(false)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    init {
        applicationScope.launch {
            val savedThemeMode = store.read()
            if (!hasManualSelection.value) {
                _themeMode.value = savedThemeMode
            }
        }
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        hasManualSelection.value = true
        _themeMode.value = themeMode
        applicationScope.launch {
            store.write(themeMode)
        }
    }
}
