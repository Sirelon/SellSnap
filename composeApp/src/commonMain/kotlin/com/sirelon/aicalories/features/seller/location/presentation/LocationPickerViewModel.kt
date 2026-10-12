package com.sirelon.sellsnap.features.seller.location.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEffect
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEvent
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocationPickerViewModel(
    private val locationRepository: LocationRepository,
) : BaseViewModel<LocationPickerState, LocationPickerEvent, LocationPickerEffect>() {

    private var searchJob: Job? = null

    override fun initialState() = LocationPickerState()

    override fun onEvent(event: LocationPickerEvent) {
        when (event) {
            is LocationPickerEvent.QueryChanged -> {
                setState { it.copy(query = event.query) }
                search(event.query, debounce = true)
            }

            LocationPickerEvent.RetrySearch -> search(currentState().query, debounce = false)

            is LocationPickerEvent.CitySelected -> viewModelScope.launch {
                locationRepository.saveLocation(event.city.location)
                postEffect(LocationPickerEffect.LocationChosen(event.city.location))
            }

            LocationPickerEvent.UseCurrentLocationClicked -> viewModelScope.launch {
                // Checked before the permission: with location off for the whole phone, iOS reports
                // the app permission as denied, and the "blocked for SellSnap" dialog would be wrong.
                if (locationRepository.isLocationServicesEnabled()) {
                    postEffect(LocationPickerEffect.RequestLocationPermission)
                } else {
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
                setState {
                    it.copy(isSearching = false, searchFailed = true, searchedQuery = null, results = emptyList())
                }
            }
        }
    }

    private suspend fun detectLocation() {
        setState { it.copy(isDetecting = true, detectFailed = false) }
        val location = try {
            locationRepository.fetchUserLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        setState { it.copy(isDetecting = false, detectFailed = location == null) }
        if (location != null) postEffect(LocationPickerEffect.LocationChosen(location))
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
