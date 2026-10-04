package com.sirelon.sellsnap.network

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.RetryStrategy
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val API_BASE_URL = "https://api.openai.com/v1"

class ApiTokenProvider {
    var token: String? = null
}

fun createHttpClient(tokenProvider: ApiTokenProvider): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }
        install(Logging) {
            this.level = LogLevel.INFO
        }
        install(DefaultRequest) {
            url(API_BASE_URL)
            header(HttpHeaders.Accept, ContentType.Application.Json)
            tokenProvider.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
    }

fun createRealtimeHttpClient(): HttpClient =
    HttpClient {
        install(WebSockets)
    }

/**
 * Fails a stuck OpenAI call in seconds, not the 5 minutes it used to take.
 *
 * Timeouts. Measured 2026-10-04, build 3.3, BigQuery `ad_generation_succeeded`: end-to-end p50
 * 12.4 s, model call p50 4.2 s, upload p50 4.4 s, attributes p50 3.1 s. Between 4 Sep and 3 Oct,
 * 6 of 13 failed generations were `OpenAITimeoutException` at exactly 300 s. 15 s to connect, 60 s
 * of socket silence and 120 s for the whole request are each well above the measured medians.
 *
 * Retry. The production key is capped at 30,000 tokens/min for gpt-4.1 and a listing is ~7-10k
 * input tokens, so four phones generating in the same minute get HTTP 429. openai-kotlin 4.1.0
 * `RetryStrategy` has three fields: `maxRetries` (default 3), `base` (default 2.0) and `maxDelay`
 * (default 60 s). Its HttpClient setup retries a 429 with `exponentialDelay`: `base^attempt`
 * seconds, capped at `maxDelay`, plus up to 1 s of jitter. The default 3 retries wait
 * 2 + 4 + 8 = 14 s and give up inside the 60 s window; 5 retries wait 2 + 4 + 8 + 16 + 32 = 62 s,
 * which outlasts it.
 *
 * Inferred: Ktor's retry plugin skips timeout exceptions, which is why the view model retries
 * those once itself.
 */
fun createOpenAI(tokenProvider: ApiTokenProvider): OpenAI = OpenAI(
    config = OpenAIConfig(
        token = tokenProvider.token!!,
        timeout = Timeout(
            request = 2.minutes,
            connect = 15.seconds,
            socket = 60.seconds,
        ),
        retry = RetryStrategy(maxRetries = 5, base = 2.0, maxDelay = 60.seconds),
    )
)
