package com.sirelon.sellsnap.features.seller.messages.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.ThreadCommand
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing and wire tests for `GET threads` and `GET threads/{id}/messages`. The fixtures follow the
 * shape of the live olx.ua payloads captured for SIR-88 - `{data: [...]}` envelope, the extra keys
 * `uuid`, `thread_uuid`, `phone` and `cvs` - with invented ids and text.
 */
class MessagesApiTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `getThreads sends the bearer token, offset and limit and no other filter`() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var query: Map<String, List<String>> = emptyMap()
        var authorization: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            query = request.url.parameters.entries().associate { it.key to it.value }
            authorization = request.headers[HttpHeaders.Authorization]
            json("""{"data":[]}""")
        }

        apiClient(engine).getThreads(accessToken = "token", offset = 20, limit = 50)

        assertEquals(HttpMethod.Get, method)
        assertTrue(path!!.endsWith("/threads"), "got $path")
        assertEquals(mapOf("offset" to listOf("20"), "limit" to listOf("50")), query)
        assertEquals("Bearer token", authorization)
    }

    @Test
    fun `getThreads parses the live shape including keys the app ignores`() = runBlocking {
        val engine = MockEngine {
            json(
                """
                {"data":[
                  {"id":101,"advert_id":201,"interlocutor_id":301,"total_count":4,"unread_count":2,
                   "created_at":"2026-01-02 10:11:12","is_favourite":false,"uuid":"aaaaaaaa-0000-0000-0000-000000000001"},
                  {"id":102,"advert_id":202,"interlocutor_id":null,"total_count":1,"unread_count":0,
                   "created_at":"2026-01-03 09:00:00","is_favourite":true,"uuid":"aaaaaaaa-0000-0000-0000-000000000002"}
                ]}
                """.trimIndent(),
            )
        }

        val threads = apiClient(engine).getThreads("token", offset = 0, limit = 50)

        assertEquals(listOf(101L, 102L), threads.map { it.id })
        val first = threads.first()
        assertEquals(201L, first.advertId)
        assertEquals(301L, first.interlocutorId)
        assertEquals(2, first.unreadCount)
        assertEquals("2026-01-02 10:11:12", first.createdAt)
        assertNull(threads.last().interlocutorId)
    }

    @Test
    fun `getThreads drops a thread without an id or an advert and defaults the rest`() = runBlocking {
        val engine = MockEngine {
            json(
                """
                {"data":[
                  {"advert_id":201,"unread_count":1},
                  {"id":103},
                  {"id":104,"advert_id":204}
                ]}
                """.trimIndent(),
            )
        }

        val threads = apiClient(engine).getThreads("token", offset = 0, limit = 50)

        assertEquals(listOf(104L), threads.map { it.id })
        assertEquals(0, threads.single().unreadCount)
        assertEquals("", threads.single().createdAt)
    }

    @Test
    fun `getThreads reads an empty or missing data array as no threads`() = runBlocking {
        assertEquals(emptyList(), apiClient(MockEngine { json("""{"data":[]}""") }).getThreads("token", 0, 50))
        assertEquals(emptyList(), apiClient(MockEngine { json("""{}""") }).getThreads("token", 0, 50))
    }

    @Test
    fun `getThreadMessages keeps the order OLX returns and maps type and attachments`() = runBlocking {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            json(
                """
                {"data":[
                  {"id":1,"thread_id":101,"created_at":"2026-01-02 10:11:12","type":"received","text":"Placeholder question",
                   "is_read":false,"attachments":[],"cvs":[],"phone":"","uuid":"u1","thread_uuid":"t1"},
                  {"id":2,"thread_id":101,"created_at":"2026-01-02 10:12:00","type":"sent","text":"Placeholder answer",
                   "is_read":true,"attachments":null,"cvs":[],"phone":null,"uuid":"u2","thread_uuid":"t1"},
                  {"id":3,"thread_id":101,"created_at":"2026-01-02 10:13:00","type":"received","text":"",
                   "is_read":false,"attachments":[{"name":"photo.jpg","url":"https://example.invalid/a.jpg"},
                   {"name":"no-url.jpg"},{"name":"blank.jpg","url":" "}],"cvs":[],"phone":"","uuid":"u3","thread_uuid":"t1"}
                ]}
                """.trimIndent(),
            )
        }

        val messages = apiClient(engine).getThreadMessages("token", threadId = 101)

        assertTrue(path!!.endsWith("/threads/101/messages"), "got $path")
        assertEquals(listOf(1L, 2L, 3L), messages.map { it.id })
        assertEquals(listOf(false, true, false), messages.map { it.isSent })
        assertEquals("Placeholder question", messages.first().text)
        assertEquals(emptyList(), messages[0].attachmentUrls)
        assertEquals(emptyList(), messages[1].attachmentUrls)
        assertEquals(listOf("https://example.invalid/a.jpg"), messages[2].attachmentUrls)
    }

    @Test
    fun `getThreadMessages drops a message without an id and treats an unknown type as received`() = runBlocking {
        val engine = MockEngine {
            json(
                """
                {"data":[
                  {"text":"no id","type":"sent"},
                  {"id":7,"type":"something-new"},
                  {"id":8,"type":null,"text":null,"created_at":null}
                ]}
                """.trimIndent(),
            )
        }

        val messages = apiClient(engine).getThreadMessages("token", threadId = 101)

        assertEquals(listOf(7L, 8L), messages.map { it.id })
        assertEquals(listOf(false, false), messages.map { it.isSent })
        assertEquals("", messages.last().text)
        assertEquals("", messages.last().createdAt)
    }

    @Test
    fun `a non-2xx answer surfaces as an OlxApiException`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":{"status":404,"title":"Not Found","detail":"Placeholder detail"}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val failure = assertFailsWith<OlxApiException> { apiClient(engine).getThreadMessages("token", threadId = 9) }
        assertIs<OlxApiError>(failure.error)
        Unit
    }

    @Test
    fun `sendThreadCommand posts the spec's mark-as-read payload and nothing else`() = runBlocking {
        // developer.olx.ua swagger v2 `/threads/{threadId}/commands` has no example payload: its
        // schema is an object with a required `command` (enum mark-as-read | set-favourite) and an
        // `is_favourite` that only `set-favourite` needs. So the body is exactly the one key.
        var method: HttpMethod? = null
        var path: String? = null
        var authorization: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            authorization = request.headers[HttpHeaders.Authorization]
            body = (request.body as TextContent).text
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        apiClient(engine).sendThreadCommand("token", threadId = 5, command = ThreadCommand.MarkAsRead)

        assertEquals(HttpMethod.Post, method)
        assertTrue(path!!.endsWith("/threads/5/commands"), "got $path")
        assertEquals("Bearer token", authorization)
        assertEquals("""{"command":"mark-as-read"}""", body)
    }

    @Test
    fun `sendThreadCommand turns a refusal into a typed error`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":{"status":404,"title":"Not Found","detail":"Placeholder detail"}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val failure = assertFailsWith<OlxApiException> {
            apiClient(engine).sendThreadCommand("token", threadId = 9, command = ThreadCommand.MarkAsRead)
        }
        assertIs<OlxApiError>(failure.error)
        Unit
    }

    @Test
    fun `postThreadMessage posts the spec's text-only payload with the account's token`() = runBlocking {
        // developer.olx.ua and developer.olx.pl swagger v2 `/threads/{threadId}/messages` `post`
        // have no example payload: the schema is an object with a required `text` (string) and
        // an optional `attachments` array this app does not send. So the body is the one key,
        // and quotes and line breaks in it are JSON-escaped, not dropped.
        var method: HttpMethod? = null
        var path: String? = null
        var authorization: String? = null
        var contentType: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            authorization = request.headers[HttpHeaders.Authorization]
            contentType = request.body.contentType?.withoutParameters()?.toString()
            body = (request.body as TextContent).text
            // The spec's success: 200 with no declared body.
            respond(content = "", status = HttpStatusCode.OK)
        }

        apiClient(engine).postThreadMessage("token", threadId = 5, text = "Placeholder \"reply\"\nsecond line")

        assertEquals(HttpMethod.Post, method)
        assertTrue(path!!.endsWith("/threads/5/messages"), "got $path")
        assertEquals("Bearer token", authorization)
        assertEquals("application/json", contentType)
        assertEquals("""{"text":"Placeholder \"reply\"\nsecond line"}""", body)
    }

    @Test
    fun `postThreadMessage turns OLX's refusal into a validation error carrying its words`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":{"status":400,"title":"Invalid request","detail":"Data validation error occurred",""" +
                    """"validation":[{"field":"text","title":"Placeholder refusal","detail":"Placeholder refusal"}]}}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val failure = assertFailsWith<OlxApiException> {
            apiClient(engine).postThreadMessage("token", threadId = 5, text = "Placeholder reply")
        }
        val error = assertIs<OlxApiError.ValidationError>(failure.error)
        assertEquals("text", error.field)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.json(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun apiClient(engine: MockEngine) = OlxApiClient(
        httpClient = createOlxHttpClient(engine),
        json = testJson,
        errorParser = OlxRemoteErrorParser(testJson),
    )
}
