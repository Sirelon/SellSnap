package com.sirelon.sellsnap.insights

import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Request and response shapes follow https://cloud.google.com/bigquery/docs/reference/rest/v2/jobs/query (fetched 2026-10-11). */
class BigQueryInstallSourceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val from = LocalDate.of(2026, 9, 28)
    private val to = LocalDate.of(2026, 10, 12)

    private fun source(
        token: suspend () -> String = { TOKEN },
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): BigQueryInstallSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return BigQueryInstallSource(HttpClient(engine) { expectSuccess = false }, token)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyJson(): JsonObject =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private val twoRows =
        """{"jobComplete":true,"schema":{"fields":[{"name":"day"},{"name":"code"},{"name":"installs"}]},"totalRows":"2",
            "rows":[{"f":[{"v":"20261010"},{"v":"ukt0001"},{"v":"2"}]},{"f":[{"v":"20261011"},{"v":"plu0004"},{"v":"1"}]}]}"""

    @Test
    fun oneAuthorizedPostCarriesTheQueryAndEveryValueAsAParameter() = runTest {
        val result = source { json(twoRows) }.installs(from, to)

        assertNull(result.alert)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://bigquery.googleapis.com/bigquery/v2/projects/sellsnap-6e85c/queries", request.url.toString())
        assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
        assertTrue(request.url.toString().none { it == '?' }, "the token and the values are not in the URL")

        val body = request.bodyJson()
        assertFalse(body.getValue("useLegacySql").jsonPrimitive.boolean)
        assertEquals("NAMED", body.getValue("parameterMode").jsonPrimitive.content)
        val parameters = body.getValue("queryParameters").jsonArray.associate {
            val parameter = it.jsonObject
            parameter.getValue("name").jsonPrimitive.content to
                (parameter.getValue("parameterType").jsonObject.getValue("type").jsonPrimitive.content to parameter.getValue("parameterValue").jsonObject.getValue("value").jsonPrimitive.content)
        }
        assertEquals(
            mapOf(
                "start_day" to ("STRING" to "20260928"),
                "end_day" to ("STRING" to "20261012"),
                "campaign_source" to ("STRING" to "threads"),
            ),
            parameters,
        )

        val sql = body.getValue("query").jsonPrimitive.content
        assertEquals(source { json(twoRows) }.sql(includeIntraday = true), sql)
        assertTrue("`sellsnap-6e85c.analytics_538260747.events_*`" in sql)
        assertTrue("`sellsnap-6e85c.analytics_538260747.events_intraday_*`" in sql)
        assertTrue("_TABLE_SUFFIX BETWEEN @start_day AND @end_day" in sql)
        assertTrue("source = @campaign_source" in sql)
        assertTrue("event_name = 'first_open' AND platform = 'ANDROID'" in sql)
        assertTrue("event_name = 'firebase_campaign'" in sql)
        assertTrue("collected_traffic_source.manual_content" in sql)
        assertTrue("20261012" !in sql && "'threads'" !in sql, "no value is written into the query text")
    }

    @Test
    fun rowsAreParsedIntoDayCodeAndCount() = runTest {
        val result = source { json(twoRows) }.installs(from, to)

        assertEquals(listOf(InstallRow("20261010", "ukt0001", 2), InstallRow("20261011", "plu0004", 1)), result.rows)
        assertNull(result.alert)
    }

    @Test
    fun noRowsIsAnEmptyResultWithoutAnAlert() = runTest {
        val result = source { json("""{"jobComplete":true,"totalRows":"0"}""") }.installs(from, to)

        assertEquals(InstallQueryResult(emptyList()), result)
    }

    @Test
    fun aMissingIntradayTableRunsTheQueryAgainOverTheDailyTablesOnly() = runTest {
        val missing = """{"error":{"code":400,"message":"sellsnap-6e85c:analytics_538260747.events_intraday_* does not match any table.","errors":[{"reason":"invalid"}],"status":"INVALID_ARGUMENT"}}"""
        val result = source { request ->
            if (requests.size == 1) json(missing, HttpStatusCode.BadRequest) else json(twoRows)
        }.installs(from, to)

        assertEquals(2, requests.size)
        assertTrue("events_intraday_*" in requests[0].bodyJson().getValue("query").jsonPrimitive.content)
        assertFalse("events_intraday_" in requests[1].bodyJson().getValue("query").jsonPrimitive.content)
        assertEquals(2, result.rows.size)
        assertNull(result.alert)
    }

    @Test
    fun anErrorAnswerIsAnEmptyResultWithAnAlertThatHoldsStatusAndReasonOnly() = runTest {
        val denied = """{"error":{"code":403,"message":"Access Denied: Project sellsnap-6e85c: User does not have bigquery.jobs.create permission","errors":[{"reason":"accessDenied"}],"status":"PERMISSION_DENIED"}}"""

        val result = source { json(denied, HttpStatusCode.Forbidden) }.installs(from, to)

        assertEquals(1, requests.size)
        assertEquals(emptyList(), result.rows)
        assertEquals(
            AlertRequest(AlertType.InstallSyncFailed, AlertRequest.INSTALL_SYNC_ACCOUNT, detail = "HTTP 403 PERMISSION_DENIED accessDenied"),
            result.alert,
        )
    }

    @Test
    fun aQueryThatDoesNotFinishIsAnEmptyResultWithAnAlert() = runTest {
        val result = source { json("""{"jobComplete":false,"jobReference":{"jobId":"j1"}}""") }.installs(from, to)

        assertEquals(emptyList(), result.rows)
        assertEquals(AlertType.InstallSyncFailed, result.alert?.type)
        assertTrue("did not finish" in result.alert?.detail.orEmpty())
    }

    @Test
    fun aMalformedRowIsAnEmptyResultWithAnAlert() = runTest {
        val result = source { json("""{"jobComplete":true,"rows":[{"f":[{"v":"20261010"},{"v":"ukt0001"},{"v":"many"}]}]}""") }.installs(from, to)

        assertEquals(emptyList(), result.rows)
        assertEquals(AlertType.InstallSyncFailed, result.alert?.type)
    }

    @Test
    fun aNetworkFailureAndATokenFailureNeverThrowAndLeakNoText() = runTest {
        val network = source { throw IOException("connect to bigquery failed, Authorization: Bearer $TOKEN") }.installs(from, to)
        val noToken = source(token = { error("metadata server said $TOKEN") }) { json(twoRows) }.installs(from, to)

        for (result in listOf(network, noToken)) {
            assertEquals(emptyList(), result.rows)
            assertEquals(AlertType.InstallSyncFailed, result.alert?.type)
            assertFalse(TOKEN in result.alert?.detail.orEmpty())
        }
        assertEquals("the request failed (IOException)", network.alert?.detail)
        assertEquals("the request failed (IllegalStateException)", noToken.alert?.detail)
    }

    private companion object {
        const val TOKEN = "ya29.token-value-for-tests-only"
    }
}
