package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.designsystem.AppThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    @Test
    fun repositoryKeepsManualSelectionWhenInitialReadFinishesLater() = runBlocking {
        val storage = DelayedReadKeyValueStore(initialValue = AppThemeMode.Dark.name)
        val repository = AppThemeRepository(
            store = AppThemePreferenceStore(storage),
            applicationScope = this,
        )

        storage.awaitReadStarted()
        repository.setThemeMode(AppThemeMode.Light)
        storage.awaitWriteValue(AppThemeMode.Light.name)
        storage.completeRead()
        yield()

        assertEquals(AppThemeMode.Light, repository.themeMode.value)
        assertEquals(AppThemeMode.Light.name, storage.getString("theme_mode"))
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

private class DelayedReadKeyValueStore(initialValue: String?) : KeyValueStore {
    private val readStarted = CompletableDeferred<Unit>()
    private val readAllowed = CompletableDeferred<Unit>()
    private val writtenValue = CompletableDeferred<String>()
    private val data = mutableMapOf<String, String>().apply {
        if (initialValue != null) {
            put("theme_mode", initialValue)
        }
    }

    suspend fun awaitReadStarted() {
        readStarted.await()
    }

    fun completeRead() {
        readAllowed.complete(Unit)
    }

    suspend fun awaitWriteValue(value: String) {
        assertEquals(value, writtenValue.await())
    }

    override suspend fun getString(key: String): String? {
        readStarted.complete(Unit)
        readAllowed.await()
        return data[key]
    }

    override suspend fun putString(key: String, value: String) {
        data[key] = value
        writtenValue.complete(value)
    }

    override suspend fun remove(key: String) {
        data.remove(key)
    }
}
