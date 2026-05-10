package com.sirelon.sellsnap.datastore

actual fun createKeyValueStore(name: String): KeyValueStore = InMemoryKeyValueStore()

class InMemoryKeyValueStore : KeyValueStore {
    private val data = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = data[key]

    override suspend fun putString(key: String, value: String) {
        data[key] = value
    }

    override suspend fun remove(key: String) {
        data.remove(key)
    }
}
