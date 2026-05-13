package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.designsystem.AppThemeMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AppThemePreferenceStoreTest {

    @Test
    fun readReturnsSystemWhenNoThemeWasSaved() = runBlocking {
        val store = AppThemePreferenceStore(InMemoryKeyValueStore())

        assertEquals(AppThemeMode.System, store.read())
    }

    @Test
    fun readReturnsSavedThemeMode() = runBlocking {
        val store = AppThemePreferenceStore(InMemoryKeyValueStore())

        store.write(AppThemeMode.Dark)

        assertEquals(AppThemeMode.Dark, store.read())
    }
}

private class InMemoryKeyValueStore : KeyValueStore {
    private val data = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = data[key]

    override suspend fun putString(key: String, value: String) {
        data[key] = value
    }

    override suspend fun remove(key: String) {
        data.remove(key)
    }
}
