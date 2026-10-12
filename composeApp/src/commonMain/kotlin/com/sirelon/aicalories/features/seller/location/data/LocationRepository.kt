package com.sirelon.sellsnap.features.seller.location.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.response.OlxLocationResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A city found by name search. [region] comes from the geocoder and tells same-named cities apart. */
data class CitySuggestion(
    val location: OlxLocation,
    val region: String?,
)

class LocationRepository(
    private val locationProvider: LocationProvider,
    private val olxApiClient: OlxApiClient,
    private val locationStore: LocationStore,
    private val countryStore: OlxCountryStore,
) {
    suspend fun getSavedLocation(): OlxLocation? = locationStore.read()

    suspend fun saveLocation(location: OlxLocation) {
        locationStore.write(location)
    }

    suspend fun clearSavedLocation() {
        locationStore.clear()
    }

    suspend fun isLocationServicesEnabled(): Boolean = locationProvider.isLocationServicesEnabled()

    fun openLocationServicesSettings() {
        locationProvider.openLocationServicesSettings()
    }

    suspend fun fetchUserLocation(): OlxLocation? {
        val deviceLocation = locationProvider.getCurrentLocation() ?: return null

        val firstValidLocation = olxApiClient.getLocations(
            latitude = deviceLocation.latitude,
            longitude = deviceLocation.longitude,
        ).firstNotNullOfOrNull { it.toOlxLocation() }

        firstValidLocation?.let { locationStore.write(it) }
        return firstValidLocation
    }

    /**
     * OLX has no city search, so the platform geocoder turns [query] into points and OLX maps each
     * point to its city. The suggestions carry no district: the one OLX returns for a point is the
     * district of the city's centre, not where the seller lives. The seller picks it from
     * [getCityDistricts] instead.
     */
    suspend fun searchCities(query: String): List<CitySuggestion> = coroutineScope {
        val country = countryStore.current
        locationProvider.searchPlaces(query = "$query, ${country.nameEn}", countryCode = country.code)
            .map { place ->
                async {
                    olxApiClient.getLocations(
                        latitude = place.location.latitude,
                        longitude = place.location.longitude,
                    ).firstNotNullOfOrNull { it.toOlxLocation() }
                        ?.let { CitySuggestion(it.copy(districtId = null, districtName = null), place.region) }
                }
            }
            .awaitAll()
            .filterNotNull()
            .distinctBy { it.location.cityId }
    }

    /**
     * The districts of [city] as locations ready to save, sorted by name; empty for a city without
     * districts. Observed on the live API (2026-10): `POST adverts` in a city with districts
     * (e.g. Kyiv) fails with a `district_id` validation error when no district is sent.
     */
    suspend fun getCityDistricts(city: OlxLocation): List<OlxLocation> =
        olxApiClient.getCityDistricts(city.cityId)
            .mapNotNull { district ->
                val id = district.id ?: return@mapNotNull null
                val name = district.name ?: return@mapNotNull null
                city.copy(districtId = id, districtName = name)
            }
            .sortedBy { it.districtName }

    private fun OlxLocationResponse.toOlxLocation(): OlxLocation? {
        val cityId = city?.id ?: return null
        val cityName = city.name ?: return null
        return OlxLocation(
            cityId = cityId,
            cityName = cityName,
            districtId = district?.id,
            districtName = district?.name,
        )
    }
}
