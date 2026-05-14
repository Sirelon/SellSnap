package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.designsystem.AppThemeMode

class AppThemePreferenceStore internal constructor(
    private val storage: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("app_theme"))

    suspend fun read(): AppThemeMode =
        storage.getString(KEY_THEME_MODE)
            ?.let { savedValue -> AppThemeMode.entries.firstOrNull { it.name == savedValue } }
            ?: AppThemeMode.System

    suspend fun write(themeMode: AppThemeMode) {
        storage.putString(KEY_THEME_MODE, themeMode.name)
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
