package com.sirelon.sellsnap.network

import com.aallam.openai.api.exception.OpenAIServerException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.response.ResponseInput
import com.aallam.openai.api.response.ResponseInputItem
import com.aallam.openai.api.response.ResponseRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenAIEndpointTest {

    @Test
    fun proxyEndpointSendsAppCheckTokenToResponsesPath() = runTest {
        val captured = captureRequest(
            OpenAIEndpoint(
                baseUrl = OPENAI_PROXY_BASE_URL,
                bearerToken = "",
                appCheck = { "app-check-token" },
            ),
        )

        assertEquals(
            "https://europe-west1-sellsnap-6e85c.cloudfunctions.net/openai/v1/responses",
            captured.url.toString(),
        )
        assertEquals("app-check-token", captured.headers[APP_CHECK_HEADER])
    }

    @Test
    fun directEndpointSendsBearerKeyAndNoAppCheckHeader() = runTest {
        val captured = captureRequest(
            OpenAIEndpoint(
                baseUrl = OPENAI_DIRECT_BASE_URL,
                bearerToken = "sk-test",
                appCheck = null,
            ),
        )

        assertEquals("https://api.openai.com/v1/responses", captured.url.toString())
        assertEquals("Bearer sk-test", captured.headers[HttpHeaders.Authorization])
        assertNull(captured.headers[APP_CHECK_HEADER])
    }

    @Test
    fun missingAppCheckTokenSendsNoHeader() = runTest {
        val captured = captureRequest(
            OpenAIEndpoint(
                baseUrl = OPENAI_PROXY_BASE_URL,
                bearerToken = "",
                appCheck = { null },
            ),
        )

        assertNull(captured.headers[APP_CHECK_HEADER])
    }

    // A 500 is the cheapest way to get the request out without faking a full Response payload:
    // the retry strategy only retries 429, so exactly one request reaches the engine.
    private suspend fun captureRequest(endpoint: OpenAIEndpoint): HttpRequestData {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respondError(HttpStatusCode.InternalServerError)
        }
        val openAI = createOpenAI(endpoint = endpoint, engine = engine)

        assertFailsWith<OpenAIServerException> { openAI.response(request = minimalRequest()) }

        return assertNotNull(captured)
    }

    private fun minimalRequest() = ResponseRequest(
        model = ModelId("gpt-4.1"),
        input = ResponseInput(
            items = listOf(
                ResponseInputItem(
                    role = "user",
                    content = buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put("text", "hi")
                        })
                    },
                ),
            ),
        ),
    )
}
