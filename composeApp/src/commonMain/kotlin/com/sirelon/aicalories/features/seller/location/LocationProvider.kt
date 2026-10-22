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

/**
 * The phone has no geocoder, so city search cannot work on it. An expected device limitation, not
 * a bug: the seller still sees that search failed, but it is not reported to Crashlytics.
 */
class GeocoderUnavailableException : Exception("No geocoder on this device")

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
