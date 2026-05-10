package com.sirelon.sellsnap.datastore

interface KeyValueStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
}

expect fun createKeyValueStore(name: String): KeyValueStore
