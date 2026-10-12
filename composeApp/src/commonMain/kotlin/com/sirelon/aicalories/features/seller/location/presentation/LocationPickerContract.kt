package com.sirelon.sellsnap.features.seller.location.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.CitySuggestion

interface LocationPickerContract {

    @Immutable
    data class LocationPickerState(
        val query: String = "",
        val isSearching: Boolean = false,
        val searchFailed: Boolean = false,
        /** The query [results] belong to; null until a search has finished. */
        val searchedQuery: String? = null,
        val results: List<CitySuggestion> = emptyList(),
        val isDetecting: Boolean = false,
        val detectFailed: Boolean = false,
        val showServicesOffDialog: Boolean = false,
        /** The city whose districts are loading or shown; null on the city search. */
        val districtCity: OlxLocation? = null,
        /** [districtCity]'s districts. Non-empty means the picker shows the district step. */
        val districts: List<OlxLocation> = emptyList(),
        val isLoadingDistricts: Boolean = false,
        val districtsFailed: Boolean = false,
    )

    sealed interface LocationPickerEvent {
        data class QueryChanged(val query: String) : LocationPickerEvent
        data object RetrySearch : LocationPickerEvent
        data class CitySelected(val city: CitySuggestion) : LocationPickerEvent
        data class DistrictSelected(val district: OlxLocation) : LocationPickerEvent
        data object BackToCitySearch : LocationPickerEvent
        data object RetryDistricts : LocationPickerEvent
        data object UseCurrentLocationClicked : LocationPickerEvent

        /** Sent by the screen once the location permission is granted. */
        data object DetectLocation : LocationPickerEvent
        data object OpenServicesSettings : LocationPickerEvent
        data object DismissServicesOffDialog : LocationPickerEvent
    }

    sealed interface LocationPickerEffect {
        data object RequestLocationPermission : LocationPickerEffect
        data class LocationChosen(val location: OlxLocation) : LocationPickerEffect
    }
}
