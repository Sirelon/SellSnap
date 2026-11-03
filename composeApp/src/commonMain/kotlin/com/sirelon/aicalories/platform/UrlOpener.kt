package com.sirelon.sellsnap.platform

expect fun openUrl(url: String)

/** This platform's SellSnap store page, for "update the app" links. */
expect fun storePageUrl(): String
