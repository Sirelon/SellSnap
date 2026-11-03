package com.sirelon.sellsnap.features.seller.location

actual fun createLocationProvider(): LocationProvider = NoOpLocationProvider()

class NoOpLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): DeviceLocation? = null
    override suspend fun isLocationServicesEnabled(): Boolean = false
    override suspend fun searchPlaces(query: String, countryCode: String): List<GeocodedPlace> = emptyList()
    override fun openLocationServicesSettings() = Unit
}
