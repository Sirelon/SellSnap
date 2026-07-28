package com.sirelon.sellsnap.features.seller.ad

import com.mohamedrejeb.calf.io.KmpFile

// Web has no writable cache directory, and screenshot runs only target Android/iOS.
internal actual suspend fun writeImageToCache(bytes: ByteArray, fileName: String): KmpFile? = null
