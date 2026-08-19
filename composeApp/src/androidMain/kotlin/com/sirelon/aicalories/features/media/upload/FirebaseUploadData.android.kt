package com.sirelon.sellsnap.features.media.upload

import dev.gitlive.firebase.storage.Data

internal actual fun firebaseUploadData(byteArray: ByteArray): Data = Data(byteArray)
