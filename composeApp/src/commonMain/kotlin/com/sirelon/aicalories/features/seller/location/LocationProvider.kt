package com.sirelon.sellsnap.features.seller.location

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
)

/** A place the platform geocoder found for a typed name. [region] is its state or oblast. */
data class GeocodedPlace(
    val location: DeviceLocation,
    val region: String?,
)

interface LocationProvider {
    suspend fun getCurrentLocation(): DeviceLocation?

    /** False when location is switched off for the whole phone, not just for SellSnap. */
    suspend fun isLocationServicesEnabled(): Boolean

    /**
     * Places the platform geocoder finds for [query], kept only inside the country with ISO code
     * [countryCode]. The geocoder needs no location permission.
     */
    suspend fun searchPlaces(query: String, countryCode: String): List<GeocodedPlace>

    /** Opens the phone's own location switch. A no-op where the platform has no public link. */
    fun openLocationServicesSettings()
}

expect fun createLocationProvider(): LocationProvider
