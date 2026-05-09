package com.sirelon.sellsnap.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

private val dataStoreCache = mutableMapOf<String, DataStore<Preferences>>()

internal fun createDataStoreKeyValueStore(producePath: () -> String): KeyValueStore {
    val path = producePath()
    val dataStore = dataStoreCache.getOrPut(path) {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { path.toPath() }
        )
    }
    return DataStoreKeyValueStore(dataStore)
}

private class DataStoreKeyValueStore(
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {

    override suspend fun getString(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}
