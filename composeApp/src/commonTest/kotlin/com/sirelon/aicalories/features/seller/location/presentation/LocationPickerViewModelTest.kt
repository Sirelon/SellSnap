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
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import kotlin.test.assertNull
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
    fun `picking a city without districts saves it as the default`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider(), districtsJson = NO_DISTRICTS_JSON)
        val lviv = OlxLocation(cityId = 5, cityName = "Lviv", districtId = null, districtName = null)

        viewModel.onEvent(LocationPickerEvent.CitySelected(CitySuggestion(lviv, region = "Lviv Oblast")))
        advanceUntilIdle()

        assertEquals(LocationPickerEffect.LocationChosen(lviv), viewModel.effects.first())
        assertEquals(lviv, store.read())
        assertNull(viewModel.state.value.districtCity)
    }

    @Test
    fun `picking a city with districts asks for the district before saving`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider(), districtsJson = KYIV_DISTRICTS_JSON)

        viewModel.onEvent(LocationPickerEvent.CitySelected(CitySuggestion(kyiv, region = "Kyiv")))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(kyiv, state.districtCity)
        assertEquals(listOf("Holosiivskyi", "Pecherskyi"), state.districts.map { it.districtName })
        assertNull(store.read(), "the city alone must not be saved while it needs a district")
    }

    @Test
    fun `picking a district saves the city with that district`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider(), districtsJson = KYIV_DISTRICTS_JSON)
        viewModel.onEvent(LocationPickerEvent.CitySelected(CitySuggestion(kyiv, region = "Kyiv")))
        advanceUntilIdle()

        val pechersk = viewModel.state.value.districts.single { it.districtId == 3 }
        viewModel.onEvent(LocationPickerEvent.DistrictSelected(pechersk))
        advanceUntilIdle()

        val expected = OlxLocation(cityId = 268, cityName = "Kyiv", districtId = 3, districtName = "Pecherskyi")
        assertEquals(LocationPickerEffect.LocationChosen(expected), viewModel.effects.first())
        assertEquals(expected, store.read())
    }

    @Test
    fun `opened for a city it starts on that city's district step`() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            FakeLocationProvider(),
            districtsJson = KYIV_DISTRICTS_JSON,
            districtsOf = kyiv,
        )
        advanceUntilIdle()

        assertEquals(kyiv, viewModel.state.value.districtCity)
        assertEquals(2, viewModel.state.value.districts.size)
    }

    @Test
    fun `back from the district step returns to the city search`() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeLocationProvider(), districtsJson = KYIV_DISTRICTS_JSON)
        viewModel.onEvent(LocationPickerEvent.CitySelected(CitySuggestion(kyiv, region = "Kyiv")))
        advanceUntilIdle()

        viewModel.onEvent(LocationPickerEvent.BackToCitySearch)

        assertNull(viewModel.state.value.districtCity)
        assertTrue(viewModel.state.value.districts.isEmpty())
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

    private fun createViewModel(
        provider: FakeLocationProvider,
        districtsJson: String? = null,
        districtsOf: OlxLocation? = null,
    ): LocationPickerViewModel {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), NoOpAnalytics())
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = testDispatcher
                addHandler { request ->
                    if (districtsJson != null && request.url.encodedPath.endsWith("/districts")) {
                        respond(
                            content = districtsJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        error("No OLX request expected.")
                    }
                }
            },
        )
        val repository = LocationRepository(
            locationProvider = provider,
            olxApiClient = OlxApiClient(
                httpClient = HttpClient(engine),
                json = testJson,
                errorParser = OlxRemoteErrorParser(testJson),
            ),
            locationStore = store,
            countryStore = countryStore,
        )
        return LocationPickerViewModel(repository, districtsOf)
    }

    private val kyiv = OlxLocation(cityId = 268, cityName = "Kyiv", districtId = null, districtName = null)

    private companion object {
        const val NO_DISTRICTS_JSON = """{"data":[]}"""
        const val KYIV_DISTRICTS_JSON =
            """{"data":[""" +
                """{"id":3,"city_id":268,"name":"Pecherskyi","latitude":50.42,"longitude":30.55},""" +
                """{"id":1,"city_id":268,"name":"Holosiivskyi","latitude":50.35684,"longitude":30.51985}""" +
                """]}"""
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
