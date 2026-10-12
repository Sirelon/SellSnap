package com.sirelon.sellsnap.features.seller.location.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEffect
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEvent
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [districtsOf] opens the picker on that city's district step, for a publish OLX would reject
 * without a district.
 */
class LocationPickerViewModel(
    private val locationRepository: LocationRepository,
    private val analytics: Analytics,
    districtsOf: OlxLocation? = null,
) : BaseViewModel<LocationPickerState, LocationPickerEvent, LocationPickerEffect>() {

    private var searchJob: Job? = null
    private var districtsJob: Job? = null

    // A city picked from the search is saved as is when it has no districts. The [districtsOf]
    // city is the one already in use, so without districts the seller is left on the search.
    private var saveCityWithoutDistricts = false

    init {
        districtsOf?.let { loadDistricts(it, saveIfNone = false) }
    }

    override fun initialState() = LocationPickerState()

    override fun onEvent(event: LocationPickerEvent) {
        when (event) {
            is LocationPickerEvent.QueryChanged -> {
                closeDistricts()
                setState { it.copy(query = event.query) }
                search(event.query, debounce = true)
            }

            LocationPickerEvent.RetrySearch -> search(currentState().query, debounce = false)

            is LocationPickerEvent.CitySelected -> {
                // A search still pending would swap the list for its own results under the step.
                searchJob?.cancel()
                setState { it.copy(isSearching = false) }
                loadDistricts(event.city.location, saveIfNone = true)
            }

            is LocationPickerEvent.DistrictSelected -> viewModelScope.launch { choose(event.district) }

            LocationPickerEvent.BackToCitySearch -> closeDistricts()

            LocationPickerEvent.RetryDistricts -> currentState().districtCity?.let {
                loadDistricts(it, saveCityWithoutDistricts)
            }

            LocationPickerEvent.UseCurrentLocationClicked -> viewModelScope.launch {
                // Checked before the permission: with location off for the whole phone, iOS reports
                // the app permission as denied, and the "blocked for SellSnap" dialog would be wrong.
                if (locationRepository.isLocationServicesEnabled()) {
                    postEffect(LocationPickerEffect.RequestLocationPermission)
                } else {
                    reportShownError(reason = "location_services_off", recordNonFatal = false)
                    setState { it.copy(showServicesOffDialog = true) }
                }
            }

            LocationPickerEvent.DetectLocation -> viewModelScope.launch { detectLocation() }

            LocationPickerEvent.OpenServicesSettings -> {
                setState { it.copy(showServicesOffDialog = false) }
                locationRepository.openLocationServicesSettings()
            }

            LocationPickerEvent.DismissServicesOffDialog ->
                setState { it.copy(showServicesOffDialog = false) }
        }
    }

    private fun search(query: String, debounce: Boolean) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            setState {
                it.copy(isSearching = false, searchFailed = false, searchedQuery = null, results = emptyList())
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            setState { it.copy(isSearching = true, searchFailed = false) }
            try {
                val results = locationRepository.searchCities(trimmed)
                setState {
                    it.copy(isSearching = false, searchedQuery = trimmed, results = results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportShownError(reason = "city_search_failed:${e.shownErrorCause}", throwable = e)
                setState {
                    it.copy(isSearching = false, searchFailed = true, searchedQuery = null, results = emptyList())
                }
            }
        }
    }

    private fun loadDistricts(city: OlxLocation, saveIfNone: Boolean) {
        districtsJob?.cancel()
        saveCityWithoutDistricts = saveIfNone
        setState {
            it.copy(districtCity = city, districts = emptyList(), isLoadingDistricts = true, districtsFailed = false)
        }
        districtsJob = viewModelScope.launch {
            val districts = try {
                locationRepository.getCityDistricts(city)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportShownError(reason = "districts_failed:${e.shownErrorCause}", throwable = e)
                setState { it.copy(isLoadingDistricts = false, districtsFailed = true) }
                return@launch
            }
            if (districts.isNotEmpty()) {
                setState { it.copy(isLoadingDistricts = false, districts = districts) }
            } else {
                setState { it.copy(isLoadingDistricts = false, districtCity = null) }
                if (saveIfNone) choose(city)
            }
        }
    }

    private fun closeDistricts() {
        districtsJob?.cancel()
        setState {
            it.copy(districtCity = null, districts = emptyList(), isLoadingDistricts = false, districtsFailed = false)
        }
    }

    private suspend fun choose(location: OlxLocation) {
        locationRepository.saveLocation(location)
        postEffect(LocationPickerEffect.LocationChosen(location))
    }

    private suspend fun detectLocation() {
        setState { it.copy(isDetecting = true, detectFailed = false) }
        var failure: Exception? = null
        val location = try {
            locationRepository.fetchUserLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            null
        }
        if (location == null) {
            reportShownError(
                reason = if (failure == null) "location_detect_empty" else "location_detect_failed",
                throwable = failure,
            )
        }
        setState { it.copy(isDetecting = false, detectFailed = location == null) }
        if (location != null) postEffect(LocationPickerEffect.LocationChosen(location))
    }

    private fun reportShownError(reason: String, throwable: Throwable? = null, recordNonFatal: Boolean = true) {
        analytics.reportShownError(
            screen = "select_location",
            reason = reason,
            throwable = throwable,
            recordNonFatal = recordNonFatal,
        )
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
