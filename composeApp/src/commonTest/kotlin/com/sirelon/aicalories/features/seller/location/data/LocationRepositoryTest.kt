package com.sirelon.aicalories.features.seller.location.data

import com.sirelon.aicalories.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.aicalories.features.seller.auth.data.OlxApiClient
import com.sirelon.aicalories.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.aicalories.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.aicalories.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.aicalories.features.seller.auth.data.OlxTokenStore
import com.sirelon.aicalories.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.aicalories.features.seller.auth.data.createOlxHttpClient
import com.sirelon.aicalories.features.seller.auth.domain.OlxTokens
import com.sirelon.aicalories.features.seller.location.DeviceLocation
import com.sirelon.aicalories.features.seller.location.LocationProvider
import com.sirelon.aicalories.features.seller.location.OlxLocation
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class LocationRepositoryTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `fetchUserLocation persists valid olx location`() = runBlocking {
        val store = LocationStore(InMemoryOlxKeyValueStore(), testJson)
        val repository = LocationRepository(
            locationProvider = TestLocationProvider(DeviceLocation(latitude = 50.45, longitude = 30.52)),
            olxApiClient = createApiClient(
                MockEngine {
                    respond(
                        content = """
                            {
                              "data": [
                                {
                                  "city": {
                                    "id": 1,
                                    "region_id": 2,
                                    "name": "Kyiv",
                                    "county": null,
                                    "municipality": null,
                                    "latitude": 50.45,
                                    "longitude": 30.52
                                  },
                                  "district": {
                                    "id": 10,
                                    "city_id": 1,
                                    "name": "Pechersk",
                                    "latitude": 50.43,
                                    "longitude": 30.54
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
            locationStore = store,
        )

        val location = repository.fetchUserLocation()

        assertEquals(OlxLocation(1, "Kyiv", 10, "Pechersk"), location)
        assertEquals(location, store.read())
    }

    @Test
    fun `fetchUserLocation does not overwrite saved location when device location is missing`() = runBlocking {
        val saved = OlxLocation(1, "Kyiv", 10, "Pechersk")
        val store = LocationStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(saved)
        }
        val repository = LocationRepository(
            locationProvider = TestLocationProvider(null),
            olxApiClient = createApiClient(MockEngine { error("No OLX request expected.") }),
            locationStore = store,
        )

        val location = repository.fetchUserLocation()

        assertNull(location)
        assertEquals(saved, store.read())
    }

    private suspend fun createApiClient(engine: MockEngine): OlxApiClient {
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "active-token",
                    refreshToken = "refresh-token",
                    expiresInSeconds = 86_400,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 4_102_444_800,
                ),
            )
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        return OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                tokenStore = tokenStore,
                authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
                errorParser = errorParser,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser,
        )
    }

    private class TestLocationProvider(
        private val location: DeviceLocation?,
    ) : LocationProvider {
        override suspend fun getCurrentLocation(): DeviceLocation? = location
    }

    private class TestCredentialsProvider : OlxCredentialsProvider {
        override suspend fun getClientId(): String = "test-client-id"

        override suspend fun getClientSecret(): String = "test-client-secret"
    }
}
