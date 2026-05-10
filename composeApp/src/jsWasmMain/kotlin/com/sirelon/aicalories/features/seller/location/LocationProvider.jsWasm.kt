package com.sirelon.sellsnap.features.seller.location

actual fun createLocationProvider(): LocationProvider = NoOpLocationProvider()

class NoOpLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): DeviceLocation? = null
}
