package com.sirelon.sellsnap.features.seller.categories.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class CategoriesRepositoryTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `country change between two loads resolves against the new country's tree`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics())
        countryStore.save(OlxCountry.UA)
        val categoryRequestsByHost = mutableMapOf<String, Int>()
        val engine = MockEngine { request ->
            val host = request.url.host
            val body = when {
                request.url.encodedPath.endsWith("categories/suggestion") ->
                    """{"data":[{"id":"$PL_CATEGORY_ID"}]}"""
                request.url.encodedPath.endsWith("categories") -> {
                    categoryRequestsByHost[host] = (categoryRequestsByHost[host] ?: 0) + 1
                    when (host) {
                        "www.olx.ua" -> """{"data":[{"id":$UA_CATEGORY_ID,"name":"UA","parent_id":0,"is_leaf":true}]}"""
                        "www.olx.pl" -> """{"data":[{"id":$PL_CATEGORY_ID,"name":"PL","parent_id":0,"is_leaf":true}]}"""
                        else -> error("Unexpected host $host")
                    }
                }
                else -> error("Unexpected request ${request.url}")
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        val repository = CategoriesRepository(
            olxApiClient = OlxApiClient(httpClient = createOlxHttpClient(engine), json = testJson, errorParser = errorParser),
            mapper = CategoriesMapper(),
            scope = CoroutineScope(Dispatchers.Default),
            countryStore = countryStore,
        )

        assertEquals(listOf(UA_CATEGORY_ID), repository.loadCategories().first().map { it.id })

        countryStore.save(OlxCountry.PL)

        assertEquals(PL_CATEGORY_ID, repository.categorySuggestion("Sneakers").first().id)
        assertEquals(mapOf("www.olx.ua" to 1, "www.olx.pl" to 1), categoryRequestsByHost)
    }

    private class FakeAnalytics : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }

    private companion object {
        const val UA_CATEGORY_ID = 101
        const val PL_CATEGORY_ID = 202
    }
}
