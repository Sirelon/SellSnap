package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.AdvertCommand
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Regression tests for the advert-lifecycle endpoints added to [OlxApiClient] (SIR-98):
 * `sendAdvertCommand`, `deleteAdvert`, `getAdvertStatistics`, and `getAdvert`. All new endpoints
 * take an explicit `accessToken`, so they are exercised through the plain [createOlxHttpClient]
 * rather than the account-store-backed authorized client.
 */
class AdvertLifecycleApiTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `sendAdvertCommand puts command and is_success on the wire for deactivate`() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            body = (request.body as TextContent).text
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        apiClient(engine).sendAdvertCommand(
            accessToken = "token",
            advertId = 42,
            command = AdvertCommand.Deactivate,
            isSuccess = true,
        )

        assertEquals(HttpMethod.Post, method)
        assertTrue(path!!.endsWith("adverts/42/commands"), "expected the commands path, got $path")
        assertEquals("""{"command":"deactivate","is_success":true}""", body)
    }

    @Test
    fun `sendAdvertCommand omits is_success for activate finish and extend even when a value is passed`() = runBlocking {
        // is_success only answers OLX's "did it sell?" question, which only deactivate asks. The
        // client drops it via takeIf { command == Deactivate } - assert that holds for every other
        // command, even when a caller passes a non-null value by mistake.
        for (command in listOf(AdvertCommand.Activate, AdvertCommand.Finish, AdvertCommand.Extend)) {
            var body: String? = null
            val engine = MockEngine { request ->
                body = (request.body as TextContent).text
                respond(content = "", status = HttpStatusCode.NoContent)
            }

            apiClient(engine).sendAdvertCommand(
                accessToken = "token",
                advertId = 42,
                command = command,
                isSuccess = true,
            )

            assertEquals(
                """{"command":"${command.wireValue}"}""",
                body,
                "is_success must be omitted from the $command command body",
            )
        }
    }

    @Test
    fun `sendAdvertCommand treats a 204 empty body as success rather than a parse failure`() = runBlocking {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.NoContent) }

        val result = runCatching {
            apiClient(engine).sendAdvertCommand(accessToken = "token", advertId = 42, command = AdvertCommand.Finish)
        }

        assertTrue(result.isSuccess, "a 204 with no body must not be read as a failure: ${result.exceptionOrNull()}")
    }

    @Test
    fun `deleteAdvert issues DELETE to the advert path and succeeds on 204`() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        apiClient(engine).deleteAdvert(accessToken = "token", advertId = 42)

        assertEquals(HttpMethod.Delete, method)
        assertTrue(path!!.endsWith("adverts/42"), "expected the advert path, got $path")
    }

    @Test
    fun `deleteAdvert surfaces OLX's own reason when the advert is still active`() = runBlocking {
        // OLX rejects DELETE on an active advert with field "ad" and no "detail", only "title" -
        // OlxRemoteErrorParser must fall back to title, and the UI quotes that text verbatim.
        val engine = MockEngine {
            respond(
                content = """
                    {"error":{"status":400,"title":"Invalid request","detail":"Data validation error occurred","validation":[{"field":"ad","title":"Invalid status"}]}}
                """.trimIndent(),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val exception = assertFailsWith<OlxApiException> {
            apiClient(engine).deleteAdvert(accessToken = "token", advertId = 42)
        }

        val error = assertIs<OlxApiError.ValidationError>(exception.error)
        assertEquals("ad", error.field)
        assertEquals("Invalid status", error.fieldDetail)
    }

    @Test
    fun `sendAdvertCommand surfaces OLX's own reason when deactivating a non-active advert`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                    {"error":{"status":400,"title":"Invalid request","detail":"Data validation error occurred","validation":[{"field":"ad","title":"Ad has to be active"}]}}
                """.trimIndent(),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val exception = assertFailsWith<OlxApiException> {
            apiClient(engine).sendAdvertCommand(
                accessToken = "token",
                advertId = 42,
                command = AdvertCommand.Deactivate,
                isSuccess = false,
            )
        }

        val error = assertIs<OlxApiError.ValidationError>(exception.error)
        assertEquals("ad", error.field)
        assertEquals("Ad has to be active", error.fieldDetail)
    }

    @Test
    fun `sendAdvertCommand surfaces a 401 as InvalidToken so withAccountToken's single retry can key on it`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_token","error_description":"The access token provided is invalid"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val exception = assertFailsWith<OlxApiException> {
            apiClient(engine).sendAdvertCommand(
                accessToken = "stale-token",
                advertId = 42,
                command = AdvertCommand.Deactivate,
                isSuccess = true,
            )
        }

        assertIs<OlxApiError.InvalidToken>(exception.error)
        Unit
    }

    @Test
    fun `getAdvertStatistics parses the spec's unwrapped shape`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"advert_views":123,"phone_views":100,"users_observing":10}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val statistics = apiClient(engine).getAdvertStatistics(accessToken = "token", advertId = 42)

        assertEquals(OlxAdvertStatistics(advertViews = 123, phoneViews = 100, usersObserving = 10), statistics)
    }

    @Test
    fun `getAdvertStatistics also accepts the data-wrapped shape every other OLX resource uses`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"data":{"advert_views":123,"phone_views":100,"users_observing":10}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val statistics = apiClient(engine).getAdvertStatistics(accessToken = "token", advertId = 42)

        assertEquals(OlxAdvertStatistics(advertViews = 123, phoneViews = 100, usersObserving = 10), statistics)
    }

    @Test
    fun `getAdvertStatistics maps absent and explicit-zero counters to the same empty state, not an error`() = runBlocking {
        // A brand-new advert has no activity yet - "too early to tell" is a valid, successful
        // result, not a parse failure, whether OLX omits the counters or sends explicit zeros.
        for (payload in listOf("{}", """{"advert_views":0,"phone_views":0,"users_observing":0}""")) {
            val engine = MockEngine {
                respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

            val statistics = apiClient(engine).getAdvertStatistics(accessToken = "token", advertId = 42)

            assertEquals(0, statistics.advertViews, "payload $payload")
            assertEquals(0, statistics.phoneViews, "payload $payload")
            assertEquals(0, statistics.usersObserving, "payload $payload")
            assertTrue(statistics.isEmpty, "payload $payload must read as empty, not an error")
        }
    }

    @Test
    fun `getAdvert maps the typed fields and keeps the raw response for the edit path to reshape`() =
        runBlocking {
            // This is the guarantee behind SIR-104: an edit re-sends updatePayload verbatim, so a
            // price change cannot silently drop an attribute, a delivery setting, or a field this
            // app does not model at all (the made-up key below stands in for that last case).
            val rawAdvertJson = """
                {
                  "id": 42,
                  "status": "active",
                  "url": "https://www.olx.ua/d/uk/obyavlenie/bike-ID42.html",
                  "created_at": "2026-05-01T10:00:00+03:00",
                  "activated_at": "2026-05-01T10:05:00+03:00",
                  "valid_to": "2026-06-01T10:00:00+03:00",
                  "title": "City bike",
                  "description": "Barely used, well maintained",
                  "category_id": 12,
                  "price": {"value": 1500, "currency": "UAH", "negotiable": false},
                  "images": [
                    {"url": "https://example.com/bike.jpg"},
                    {"url": "https://example.com/bike2.jpg"}
                  ],
                  "attributes": [
                    {"code": "brand", "values": ["Trek"]},
                    {"code": "condition", "values": ["used"]}
                  ],
                  "location": {"city_id": 5, "district_id": 3},
                  "ad_delivery": {"enabled": true, "carrier": "nova_poshta"},
                  "auto_extend_enabled": true,
                  "made_up_field_the_app_does_not_model": {"nested": ["value1", 2, true]}
                }
            """.trimIndent()
            val engine = MockEngine {
                respond(
                    content = """{"data": $rawAdvertJson}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

            val snapshot = apiClient(engine).getAdvert(accessToken = "token", advertId = 42)

            assertEquals(42L, snapshot.detail.id)
            assertEquals(AdvertStatus.Active, snapshot.detail.status)
            assertEquals("City bike", snapshot.detail.title)
            assertEquals("Barely used, well maintained", snapshot.detail.description)
            assertEquals(true, snapshot.detail.autoExtendEnabled)

            // The raw response is kept whole here: the PUT body is a different shape and is
            // built from it in `AdvertLifecycleRepository`, not filtered out of it.
            assertEquals(testJson.parseToJsonElement(rawAdvertJson), snapshot.updatePayload)
        }

    private fun apiClient(engine: MockEngine): OlxApiClient {
        val errorParser = OlxRemoteErrorParser(testJson)
        return OlxApiClient(
            httpClient = createOlxHttpClient(engine),
            json = testJson,
            errorParser = errorParser,
        )
    }
    @Test
    fun `moderation reason strips the HTML OLX sends and yields plain text`() = runBlocking {
        val engine = MockEngine {
            respond(
                // Unwrapped, and HTML per the spec - the same text OLX emails the seller.
                """{"email_notification":"<p>Your advert was <b>moderated</b> because it&nbsp;promotes restricted items.</p>"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val reason = apiClient(engine).getAdvertModerationReason("token", 42L)

        // Shown inside a sentence in a bottom sheet, so the markup has to go - and OLX controls it.
        assertEquals("Your advert was moderated because it promotes restricted items.", reason)
    }

    @Test
    fun `an advert OLX has nothing to say about is not an error`() = runBlocking<Unit> {
        // A 404 is the ordinary answer for an advert that was never moderated. Treating it as a
        // failure would put an error in front of the seller for a perfectly normal listing.
        val notFound = MockEngine {
            respond(
                """{"error":{"status":404,"title":"Not found"}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertNull(apiClient(notFound).getAdvertModerationReason("token", 42L))

        // Same for an empty reason: nothing to quote means fall back to the app's own copy.
        val blank = MockEngine {
            respond(
                """{"email_notification":""}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertNull(apiClient(blank).getAdvertModerationReason("token", 42L))
    }

}
