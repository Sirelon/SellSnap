package com.sirelon.sellsnap.features.media.upload

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

internal actual fun firebaseUploadData(byteArray: ByteArray): Data = Data(byteArray.toNSData())

private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = size.toULong(),
        )
    }
