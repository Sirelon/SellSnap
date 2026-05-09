package com.sirelon.sellsnap.datastore

private var androidFilesDir: String = ""

fun initAndroidKeyValueStore(filesDir: String) {
    androidFilesDir = filesDir
}

actual fun createKeyValueStore(name: String): KeyValueStore {
    require(androidFilesDir.isNotEmpty()) {
        "Call initAndroidKeyValueStore(context.filesDir.absolutePath) before creating a KeyValueStore."
    }
    return createDataStoreKeyValueStore {
        "$androidFilesDir/datastore/$name.preferences_pb"
    }
}
