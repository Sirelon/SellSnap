package com.sirelon.sellsnap.features.seller.location

class FakeLocationProvider(
    private val location: DeviceLocation? = null,
    private val places: List<GeocodedPlace> = emptyList(),
    private val servicesEnabled: Boolean = true,
) : LocationProvider {
    val searchQueries = mutableListOf<Pair<String, String>>()

    override suspend fun getCurrentLocation(): DeviceLocation? = location

    override suspend fun isLocationServicesEnabled(): Boolean = servicesEnabled

    override suspend fun searchPlaces(query: String, countryCode: String): List<GeocodedPlace> {
        searchQueries += query to countryCode
        return places
    }

    override fun openLocationServicesSettings() = Unit
}
