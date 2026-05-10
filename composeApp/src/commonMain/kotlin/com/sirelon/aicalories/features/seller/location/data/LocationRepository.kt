package com.sirelon.sellsnap.features.seller.location.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.OlxLocation

class LocationRepository(
    private val locationProvider: LocationProvider,
    private val olxApiClient: OlxApiClient,
    private val locationStore: LocationStore,
) {
    suspend fun getSavedLocation(): OlxLocation? = locationStore.read()

    suspend fun fetchUserLocation(): OlxLocation? {
        val deviceLocation = locationProvider.getCurrentLocation() ?: return null

        val locations = olxApiClient.getLocations(
            latitude = deviceLocation.latitude,
            longitude = deviceLocation.longitude,
        )

        val firstValidLocation = locations.firstNotNullOfOrNull { location ->
            val cityId = location.city?.id ?: return@firstNotNullOfOrNull null
            val cityName = location.city.name ?: return@firstNotNullOfOrNull null

            OlxLocation(
                cityId = cityId,
                cityName = cityName,
                districtId = location.district?.id,
                districtName = location.district?.name,
            )
        }

        firstValidLocation?.let { locationStore.write(it) }
        return firstValidLocation
    }
}
