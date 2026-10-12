package com.sirelon.sellsnap.features.seller.location.presentation

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.location.FakeLocationProvider
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.CitySuggestion
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEffect
import com.sirelon.sellsnap.features.seller.location.presentation.LocationPickerContract.LocationPickerEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationPickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testJson = Json { ignoreUnknownKeys = true }
    private val store = LocationStore(InMemoryOlxKeyValueStore(), testJson)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `current location with location off for the phone shows the services dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider(servicesEnabled = false))

        viewModel.onEvent(LocationPickerEvent.UseCurrentLocationClicked)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showServicesOffDialog)
    }

    @Test
    fun `current location with location on asks for the permission`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider())

        viewModel.onEvent(LocationPickerEvent.UseCurrentLocationClicked)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showServicesOffDialog)
        assertEquals(LocationPickerEffect.RequestLocationPermission, viewModel.effects.first())
    }

    @Test
    fun `picking a city saves it as the default`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider())
        val lviv = OlxLocation(cityId = 5, cityName = "Lviv", districtId = null, districtName = null)

        viewModel.onEvent(LocationPickerEvent.CitySelected(CitySuggestion(lviv, region = "Lviv Oblast")))
        advanceUntilIdle()

        assertEquals(LocationPickerEffect.LocationChosen(lviv), viewModel.effects.first())
        assertEquals(lviv, store.read())
    }

    @Test
    fun `a one-letter query does not search`() = runTest(testDispatcher) {
        val provider = FakeLocationProvider()
        val viewModel = createViewModel(provider)

        viewModel.onEvent(LocationPickerEvent.QueryChanged("L"))
        advanceUntilIdle()

        assertTrue(provider.searchQueries.isEmpty())
        assertEquals(null, viewModel.state.value.searchedQuery)
    }

    private suspend fun createViewModel(provider: FakeLocationProvider): LocationPickerViewModel {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), NoOpAnalytics())
        val repository = LocationRepository(
            locationProvider = provider,
            olxApiClient = OlxApiClient(
                httpClient = HttpClient(MockEngine { error("No OLX request expected.") }),
                json = testJson,
                errorParser = OlxRemoteErrorParser(testJson),
            ),
            locationStore = store,
            countryStore = countryStore,
        )
        return LocationPickerViewModel(repository)
    }

    private class NoOpAnalytics : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }
}
