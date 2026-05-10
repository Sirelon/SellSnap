package com.sirelon.sellsnap.datastore

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun createKeyValueStore(name: String): KeyValueStore = createDataStoreKeyValueStore {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    requireNotNull(documentDirectory?.path) { "Could not resolve iOS document directory" } +
        "/datastore/$name.preferences_pb"
}
