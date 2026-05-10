package com.sirelon.sellsnap.datastore

import java.io.File

actual fun createKeyValueStore(name: String): KeyValueStore = createDataStoreKeyValueStore {
    val appDataDir = File(System.getProperty("user.home"), ".sellsnap/datastore")
    appDataDir.mkdirs()
    File(appDataDir, "$name.preferences_pb").absolutePath
}
