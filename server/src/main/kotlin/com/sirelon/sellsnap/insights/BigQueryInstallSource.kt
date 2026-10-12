package com.sirelon.sellsnap.insights

import com.google.auth.oauth2.GoogleCredentials
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.threads.redact
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

/** A Ktor client for the BigQuery REST API: Java engine, `expectSuccess = false`, so a failed response is read here and never thrown by Ktor. */
fun bigQueryHttpClient(engine: HttpClientEngine = Java.create()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10.seconds.inWholeMilliseconds
        requestTimeoutMillis = 90.seconds.inWholeMilliseconds
    }
}

/** An OAuth access token for BigQuery from Application Default Credentials; the service account of the Cloud Run service on Cloud Run. */
suspend fun applicationDefaultBigQueryToken(): String = withContext(Dispatchers.IO) {
    val credentials = GoogleCredentials.getApplicationDefault().createScoped(BigQueryInstallSource.SCOPE)
    credentials.refreshIfExpired()
    checkNotNull(credentials.accessToken) { "No access token" }.tokenValue
}

/**
 * [InstallSource] over one `jobs.query` call (https://cloud.google.com/bigquery/docs/reference/rest/v2/jobs/query)
 * against the Firebase Analytics export `{project}.{dataset}.events_*` and, where it exists, `events_intraday_*`.
 *
 * An install counts once per `user_pseudo_id`: the user's earliest Android `first_open` in the window, joined to the
 * user's earliest `firebase_campaign` event whose `source` is `threads`. The post code is that event's `content`
 * parameter, or `collected_traffic_source.manual_content` when the parameter is empty. Every value is bound as a
 * query parameter; only the table names, which cannot be parameters, are built into the text, and they are checked on construction.
 *
 * Streaming export is optional, so `events_intraday_*` may not exist and BigQuery answers 400 "does not match any table";
 * the query then runs once more over the daily tables alone. Any other failure, a query that does not finish within
 * [QUERY_TIMEOUT_MS], or a token failure becomes an empty result with an alert request. No token and no response text reaches the alert.
 */
class BigQueryInstallSource(
    private val client: HttpClient,
    private val accessToken: suspend () -> String = ::applicationDefaultBigQueryToken,
    private val project: String = DEFAULT_PROJECT,
    private val dataset: String = DEFAULT_DATASET,
    private val baseUrl: String = "https://bigquery.googleapis.com",
) : InstallSource {
    init {
        require(NAME.matches(project) && NAME.matches(dataset)) { "BigQuery project and dataset names have unexpected characters" }
    }

    override suspend fun installs(from: LocalDate, to: LocalDate): InstallQueryResult =
        try {
            val token = accessToken()
            val first = query(token, from, to, includeIntraday = true)
            val response = if (first.isMissingIntraday()) query(token, from, to, includeIntraday = false) else first
            parse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The exception message may hold request details, so only its class is kept.
            failed("the request failed (${e::class.simpleName})")
        }

    private class Response(val status: Int, val body: String)

    private fun Response.isMissingIntraday(): Boolean = status == 400 && "events_intraday_*" in body && "does not match any table" in body

    private suspend fun query(token: String, from: LocalDate, to: LocalDate, includeIntraday: Boolean): Response {
        val body = requestBody(from, to, includeIntraday).toString()
        val response = client.post("$baseUrl/bigquery/v2/projects/$project/queries") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(TextContent(body, ContentType.Application.Json))
        }
        return Response(response.status.value, response.bodyAsText())
    }

    private fun requestBody(from: LocalDate, to: LocalDate, includeIntraday: Boolean): JsonObject = buildJsonObject {
        put("query", sql(includeIntraday))
        put("useLegacySql", false)
        put("parameterMode", "NAMED")
        put("timeoutMs", QUERY_TIMEOUT_MS)
        put("maxResults", MAX_ROWS)
        put(
            "queryParameters",
            buildJsonArray {
                add(stringParameter("start_day", DAY.format(from)))
                add(stringParameter("end_day", DAY.format(to)))
                add(stringParameter("campaign_source", CAMPAIGN_SOURCE))
            },
        )
    }

    private fun stringParameter(name: String, value: String): JsonObject = buildJsonObject {
        put("name", name)
        put("parameterType", buildJsonObject { put("type", "STRING") })
        put("parameterValue", buildJsonObject { put("value", value) })
    }

    /** The final SQL. The daily tables only, or with the intraday tables when [includeIntraday]. */
    internal fun sql(includeIntraday: Boolean): String {
        val columns = "user_pseudo_id, event_date, event_timestamp, event_name, platform, event_params, collected_traffic_source"
        val daily = "SELECT $columns FROM `$project.$dataset.events_*` WHERE _TABLE_SUFFIX BETWEEN @start_day AND @end_day"
        val intraday = "SELECT $columns FROM `$project.$dataset.events_intraday_*` WHERE _TABLE_SUFFIX BETWEEN @start_day AND @end_day"
        val source = if (includeIntraday) "$daily\n  UNION ALL\n  $intraday" else daily
        return """
WITH events AS (
  $source
),
opens AS (
  SELECT user_pseudo_id, MIN(event_date) AS day
  FROM events
  WHERE event_name = 'first_open' AND platform = 'ANDROID'
  GROUP BY user_pseudo_id
),
campaigns AS (
  SELECT
    user_pseudo_id,
    COALESCE(
      (SELECT p.value.string_value FROM UNNEST(event_params) AS p WHERE p.key = 'source'),
      collected_traffic_source.manual_source
    ) AS source,
    COALESCE(
      (SELECT p.value.string_value FROM UNNEST(event_params) AS p WHERE p.key = 'content'),
      collected_traffic_source.manual_content
    ) AS code,
    event_timestamp
  FROM events
  WHERE event_name = 'firebase_campaign'
),
attributed AS (
  SELECT user_pseudo_id, ARRAY_AGG(code ORDER BY event_timestamp LIMIT 1)[OFFSET(0)] AS code
  FROM campaigns
  WHERE source = @campaign_source AND code IS NOT NULL AND code != ''
  GROUP BY user_pseudo_id
)
SELECT opens.day AS day, attributed.code AS code, COUNT(*) AS installs
FROM opens
JOIN attributed USING (user_pseudo_id)
GROUP BY day, code
ORDER BY day, code
""".trim()
    }

    private fun parse(response: Response): InstallQueryResult {
        if (!(response.status in 200..299)) return failed(describeError(response))
        val root = try {
            JSON.parseToJsonElement(response.body).jsonObject
        } catch (e: Exception) {
            return failed("the answer was not JSON")
        }
        if (root["jobComplete"]?.jsonPrimitive?.booleanOrNull == false) {
            return failed("the query did not finish within ${QUERY_TIMEOUT_MS / 1000} seconds")
        }
        if (root["pageToken"] != null) return failed("the answer has more than $MAX_ROWS rows")
        val rows = (root["rows"] as? JsonArray).orEmpty().map { row ->
            val cells = row.jsonObject["f"]?.jsonArray ?: return failed("a row had no cells")
            val day = cells.cell(0)
            val code = cells.cell(1)
            val installs = cells.cell(2)?.toLongOrNull()
            if (day == null || code == null || installs == null) return failed("a row had an unexpected shape")
            InstallRow(day, code, installs)
        }
        return InstallQueryResult(rows)
    }

    private fun JsonArray.cell(index: Int): String? =
        (getOrNull(index)?.jsonObject?.get("v") as? JsonPrimitive)?.contentOrNull

    /** The HTTP status and the error's `status` word and `reason` from BigQuery's error body; never the message text. */
    private fun describeError(response: Response): String {
        val error = try {
            JSON.parseToJsonElement(response.body).jsonObject["error"]?.jsonObject
        } catch (e: Exception) {
            null
        }
        val status = error?.get("status")?.jsonPrimitive?.contentOrNull
        val reason = (error?.get("errors") as? JsonArray)?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
        return listOfNotNull("HTTP ${response.status}", status, reason).joinToString(" ")
    }

    private fun failed(detail: String): InstallQueryResult =
        InstallQueryResult(
            rows = emptyList(),
            alert = AlertRequest(AlertType.InstallSyncFailed, AlertRequest.INSTALL_SYNC_ACCOUNT, detail = redact(detail)),
        )

    companion object {
        const val DEFAULT_PROJECT = "sellsnap-6e85c"
        const val DEFAULT_DATASET = "analytics_538260747"

        /** `jobs.query` accepts this read-only scope; the service account also holds `bigquery.jobUser` and `bigquery.dataViewer`. */
        const val SCOPE = "https://www.googleapis.com/auth/bigquery.readonly"

        /** The `source` of the `firebase_campaign` event that the website's Play referrer sets. */
        const val CAMPAIGN_SOURCE = "threads"

        const val QUERY_TIMEOUT_MS = 60_000L
        const val MAX_ROWS = 5_000L

        private val NAME = Regex("[A-Za-z0-9_-]{1,100}")
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
