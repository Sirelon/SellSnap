package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.model.PostKind
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
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Inferred: fixture shape from https://developers.facebook.com/docs/threads/posts,
 * https://developers.facebook.com/documentation/threads/reference/publishing.md,
 * https://developers.facebook.com/documentation/threads/troubleshooting.md,
 * https://developers.facebook.com/documentation/threads/create-posts/polls,
 * https://developers.facebook.com/docs/threads/get-started/long-lived-tokens,
 * https://developers.facebook.com/documentation/threads/troubleshooting/debug-access-token.md and
 * https://developers.facebook.com/docs/threads/posts/delete-posts, fetched 2026-10-09; the insights
 * fixtures follow https://developers.facebook.com/docs/threads/insights, fetched 2026-10-10.
 * The error body and the threads list follow the standard Graph shape; the pages do not show them.
 */
class ThreadsHttpClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ThreadsHttpClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return ThreadsHttpClient(HttpClient(engine) { expectSuccess = false }, appSecret = APP_SECRET)
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.formBody(): String = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun createsATextContainerWithAFormBody() = runTest {
        val client = api { json("""{"id":"17900000000000001"}""") }

        val id = client.createContainer("1234", TOKEN, ContainerRequest(PostKind.Text, "Hello world"))

        assertEquals("17900000000000001", id)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://graph.threads.net/v1.0/1234/threads", request.url.toString())
        assertEquals("media_type=TEXT&text=Hello+world&access_token=$TOKEN", request.formBody())
    }

    @Test
    fun createsAnImageContainerWithTheImageUrl() = runTest {
        val client = api { json("""{"id":"c2"}""") }

        client.createContainer("1234", TOKEN, ContainerRequest(PostKind.Image, "Caption", imageUrl = "https://img.test/a.jpg"))

        assertEquals(
            "media_type=IMAGE&image_url=https%3A%2F%2Fimg.test%2Fa.jpg&text=Caption&access_token=$TOKEN",
            requests.single().formBody(),
        )
    }

    @Test
    fun sendsAPollAsAJsonStringFormFieldOnATextPost() = runTest {
        val client = api { json("""{"id":"c3"}""") }

        client.createContainer(
            "1234", TOKEN,
            ContainerRequest(PostKind.Poll, "Which one?", pollOptions = listOf("Yes", "No", "Maybe")),
        )

        val body = requests.single().formBody()
        assertEquals(
            "media_type=TEXT&text=Which+one%3F" +
                "&poll_attachment=%7B%22option_a%22%3A%22Yes%22%2C%22option_b%22%3A%22No%22%2C%22option_c%22%3A%22Maybe%22%7D" +
                "&access_token=$TOKEN",
            body,
        )
        assertFalse("IMAGE" in body)
    }

    @Test
    fun publishesAContainerWithCreationId() = runTest {
        val client = api { json("""{"id":"media-9"}""") }

        assertEquals("media-9", client.publish("1234", TOKEN, "c1"))

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://graph.threads.net/v1.0/1234/threads_publish", request.url.toString())
        assertEquals("creation_id=c1&access_token=$TOKEN", request.formBody())
    }

    @Test
    fun readsEveryContainerState() = runTest {
        val states = mapOf(
            "IN_PROGRESS" to ContainerState.InProgress,
            "FINISHED" to ContainerState.Finished,
            "ERROR" to ContainerState.Error,
            "EXPIRED" to ContainerState.Expired,
            "PUBLISHED" to ContainerState.Published,
        )
        for ((wire, expected) in states) {
            val client = api { json("""{"status":"$wire","id":"c1"}""") }
            assertEquals(expected, client.containerStatus("c1", TOKEN).state, wire)
        }
        val request = requests.first()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1.0/c1", request.url.encodedPath)
        assertEquals("status,error_message", request.url.parameters["fields"])
    }

    @Test
    fun keepsOnlyCodeLikeErrorMessages() = runTest {
        val code = api { json("""{"status":"ERROR","id":"c1","error_message":"FAILED_DOWNLOADING_VIDEO"}""") }
        assertEquals("FAILED_DOWNLOADING_VIDEO", code.containerStatus("c1", TOKEN).errorMessage)

        val freeText = api { json("""{"status":"ERROR","id":"c1","error_message":"see https://x.test?access_token=$TOKEN"}""") }
        assertNull(freeText.containerStatus("c1", TOKEN).errorMessage)
    }

    @Test
    fun anUnknownContainerStateIsATransientFailure() = runTest {
        val client = api { json("""{"status":"WHO_KNOWS"}""") }
        assertFailsWith<ThreadsFailure.Transient> { client.containerStatus("c1", TOKEN) }
    }

    @Test
    fun readsThePublishingLimit() = runTest {
        val client = api {
            json("""{"data":[{"quota_usage":4,"config":{"quota_total":250,"quota_duration":86400}}]}""")
        }

        val limit = client.publishingLimit("1234", TOKEN)

        assertEquals(4, limit.quotaUsage)
        assertEquals(250, limit.quotaTotal)
        assertEquals(246, limit.remaining)
        assertEquals("/v1.0/1234/threads_publishing_limit", requests.single().url.encodedPath)
    }

    @Test
    fun deletesAMediaObjectAndAcceptsTheDocumentedBodyWithATrailingComma() = runTest {
        val client = api { json("{\n  \"success\": true,\n  \"deleted_id\": \"1234567\",\n}") }

        client.delete("1234567", TOKEN)

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/v1.0/1234567", request.url.encodedPath)
        assertEquals(TOKEN, request.url.parameters["access_token"])
    }

    @Test
    fun readsDebugTokenAndMapsZeroToNone() = runTest {
        val client = api {
            json(
                """
                {"data":{"type":"USER","application":"Threads API Test App","data_access_expires_at":1754846089,
                "expires_at":1752254132,"is_valid":true,"issued_at":1747070132,
                "scopes":["threads_basic","threads_content_publish","threads_delete"],"user_id":"1234567890123456"}}
                """.trimIndent(),
            )
        }

        val info = client.debugToken(TOKEN)

        assertTrue(info.isValid)
        assertEquals(setOf("threads_basic", "threads_content_publish", "threads_delete"), info.scopes)
        assertEquals("1234567890123456", info.userId)
        assertEquals(Instant.ofEpochSecond(1752254132), info.expiresAt)
        assertEquals(Instant.ofEpochSecond(1754846089), info.dataAccessExpiresAt)
        assertEquals(Instant.ofEpochSecond(1747070132), info.issuedAt)
        val request = requests.single()
        assertEquals("/v1.0/debug_token", request.url.encodedPath)
        assertEquals(TOKEN, request.url.parameters["input_token"])

        val noExpiry = api {
            json("""{"data":{"is_valid":true,"expires_at":0,"data_access_expires_at":0,"scopes":[],"user_id":"1"}}""")
        }.debugToken(TOKEN)
        assertNull(noExpiry.expiresAt)
        assertNull(noExpiry.dataAccessExpiresAt)
    }

    @Test
    fun exchangesAShortLivedTokenOnTheUnversionedPath() = runTest {
        val client = api { json("""{"access_token":"LONG","token_type":"bearer","expires_in":5183944}""") }

        val issued = client.exchangeToken("SHORT")

        assertEquals("LONG", issued.accessToken)
        assertEquals(5183944, issued.expiresInSeconds)
        val request = requests.single()
        assertEquals("/access_token", request.url.encodedPath)
        assertEquals("th_exchange_token", request.url.parameters["grant_type"])
        assertEquals(APP_SECRET, request.url.parameters["client_secret"])
        assertEquals("SHORT", request.url.parameters["access_token"])
    }

    @Test
    fun refreshesALongLivedTokenOnTheUnversionedPath() = runTest {
        val client = api { json("""{"access_token":"NEW","token_type":"bearer","expires_in":5183944}""") }

        assertEquals("NEW", client.refreshToken("OLD").accessToken)

        val request = requests.single()
        assertEquals("/refresh_access_token", request.url.encodedPath)
        assertEquals("th_refresh_token", request.url.parameters["grant_type"])
        assertNull(request.url.parameters["client_secret"])
    }

    @Test
    fun readsMe() = runTest {
        val client = api { json("""{"id":"1234","username":"sellsnap.app"}""") }

        val me = client.me(TOKEN)

        assertEquals("1234", me.id)
        assertEquals("sellsnap.app", me.username)
        assertEquals("/v1.0/me", requests.single().url.encodedPath)
    }

    @Test
    fun readsRecentThreads() = runTest {
        val client = api {
            json(
                """{"data":[{"id":"m2","text":"Newer","timestamp":"2026-10-20T08:30:05+0000"},{"id":"m1","timestamp":"bad"}]}""",
            )
        }

        val threads = client.recentThreads("1234", TOKEN, 25)

        assertEquals(listOf("m2", "m1"), threads.map { it.id })
        assertEquals("Newer", threads[0].text)
        assertEquals(Instant.parse("2026-10-20T08:30:05Z"), threads[0].publishedAt)
        assertNull(threads[1].publishedAt)
        assertEquals("25", requests.single().url.parameters["limit"])
    }

    /**
     * Inferred: the media insights shape (`name`, `period`, `values[0].value`, `title`, `id`) from
     * https://developers.facebook.com/docs/threads/insights, fetched 2026-10-10.
     */
    @Test
    fun readsMediaInsightsAsAMapOfMetricToCount() = runTest {
        val client = api {
            json(
                """
                {"data":[
                {"name":"likes","period":"lifetime","values":[{"value":100}],"title":"Likes","id":"17900/insights/likes/lifetime"},
                {"name":"replies","period":"lifetime","values":[{"value":10}],"title":"Replies","id":"17900/insights/replies/lifetime"}
                ]}
                """.trimIndent(),
            )
        }

        val counts = client.mediaInsights("17900", TOKEN, listOf("likes", "replies", "reposts", "quotes"))

        assertEquals(mapOf("likes" to 100L, "replies" to 10L), counts)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1.0/17900/insights", request.url.encodedPath)
        assertEquals("likes,replies,reposts,quotes", request.url.parameters["metric"])
        assertEquals(TOKEN, request.url.parameters["access_token"])
    }

    @Test
    fun anEmptyMediaInsightsListIsAnEmptyMap() = runTest {
        val client = api { json("""{"data":[]}""") }
        assertEquals(emptyMap(), client.mediaInsights("17900", TOKEN, listOf("views")))
        assertEquals("views", requests.single().url.parameters["metric"])
    }

    @Test
    fun aMediaInsightsBodyWithoutADataListIsTransient() = runTest {
        assertIs<ThreadsFailure.Transient>(
            assertFailsWith<ThreadsFailure> { api { json("""{"id":"x"}""") }.mediaInsights("1", TOKEN, listOf("views")) },
        )
        assertIs<ThreadsFailure.Transient>(
            assertFailsWith<ThreadsFailure> { api { json("""{"data":[{"values":[{"value":1}]}]}""") }.mediaInsights("1", TOKEN, listOf("views")) },
        )
    }

    /**
     * Inferred: the page shows `total_value.value` only for the `views` user metric and gives no
     * `followers_count` example; the follower response is assumed to have the same shape
     * (https://developers.facebook.com/docs/threads/insights, fetched 2026-10-10).
     */
    @Test
    fun readsTheFollowersCountFromTheTotalValue() = runTest {
        val client = api {
            json("""{"data":[{"name":"followers_count","total_value":{"value":1234},"title":"followers_count","id":"1234/insights/followers_count/day"}]}""")
        }

        assertEquals(1234L, client.followersCount("1234", TOKEN))

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1.0/1234/threads_insights", request.url.encodedPath)
        assertEquals("followers_count", request.url.parameters["metric"])
        assertNull(request.url.parameters["since"])
        assertNull(request.url.parameters["until"])
    }

    @Test
    fun aFollowersResponseWithoutTheCountIsTransient() = runTest {
        assertIs<ThreadsFailure.Transient>(assertFailsWith<ThreadsFailure> { api { json("""{"data":[]}""") }.followersCount("1", TOKEN) })
    }

    @Test
    fun insightsFailuresGoThroughTheClassificationWithoutLeakingTheToken() = runTest {
        val body = """{"error":{"message":"bad $TOKEN","code":%d,"is_transient":false}}"""

        suspend fun failure(status: HttpStatusCode, code: Int, call: suspend ThreadsHttpClient.() -> Unit): ThreadsFailure =
            assertFailsWith<ThreadsFailure> { api { json(body.format(code), status) }.call() }

        val media: suspend ThreadsHttpClient.() -> Unit = { mediaInsights("1", TOKEN, listOf("views")) }
        val followers: suspend ThreadsHttpClient.() -> Unit = { followersCount("1", TOKEN) }
        val permanent = failure(HttpStatusCode.BadRequest, 100, media)
        assertIs<ThreadsFailure.Permanent>(permanent)
        assertIs<ThreadsFailure.Permanent>(failure(HttpStatusCode.Forbidden, 10, followers))
        assertIs<ThreadsFailure.RateLimited>(failure(HttpStatusCode.BadRequest, 4, media))
        assertIs<ThreadsFailure.Transient>(failure(HttpStatusCode.InternalServerError, 2, followers))
        for (f in listOf(permanent, failure(HttpStatusCode.BadRequest, 190, followers))) {
            val text = f.message.orEmpty() + f.stackTraceToString()
            assertFalse(TOKEN in text)
            assertFalse("graph.threads.net" in text)
        }
    }

    @Test
    fun mapsErrorResponsesToClassifiedFailures() = runTest {
        fun errorBody(code: Int, transient: Boolean = false) =
            """{"error":{"message":"Error validating access token with $TOKEN","type":"OAuthException","code":$code,"is_transient":$transient,"fbtrace_id":"A1"}}"""

        suspend fun failure(status: HttpStatusCode, body: String): ThreadsFailure =
            assertFailsWith<ThreadsFailure> { api { json(body, status) }.me(TOKEN) }

        assertEquals(190, assertIs<ThreadsFailure.Permanent>(failure(HttpStatusCode.BadRequest, errorBody(190))).code)
        assertEquals(368, assertIs<ThreadsFailure.Permanent>(failure(HttpStatusCode.BadRequest, errorBody(368))).code)
        assertIs<ThreadsFailure.Permanent>(failure(HttpStatusCode.NotFound, "not json"))
        assertIs<ThreadsFailure.RateLimited>(failure(HttpStatusCode.BadRequest, errorBody(613)))
        assertIs<ThreadsFailure.RateLimited>(failure(HttpStatusCode.TooManyRequests, "{}"))
        assertIs<ThreadsFailure.Transient>(failure(HttpStatusCode.InternalServerError, errorBody(2)))
        assertIs<ThreadsFailure.Transient>(failure(HttpStatusCode.BadRequest, errorBody(1, transient = true)))
    }

    @Test
    fun noFailureMessageCarriesATokenASecretOrAUrl() = runTest {
        val leakyBody = """{"error":{"message":"bad $TOKEN $APP_SECRET https://graph.threads.net/v1.0/me?access_token=$TOKEN","code":190}}"""
        val failures = listOf(
            assertFailsWith<ThreadsFailure> { api { json(leakyBody, HttpStatusCode.BadRequest) }.me(TOKEN) },
            assertFailsWith<ThreadsFailure> { api { json(leakyBody, HttpStatusCode.InternalServerError) }.me(TOKEN) },
            assertFailsWith<ThreadsFailure> { api { json(leakyBody, HttpStatusCode.TooManyRequests) }.exchangeToken(TOKEN) },
            assertFailsWith<ThreadsFailure> { api { json("garbage $TOKEN") }.me(TOKEN) },
            assertFailsWith<ThreadsFailure> {
                api { throw IOException("https://graph.threads.net/v1.0/me?access_token=$TOKEN&client_secret=$APP_SECRET") }
                    .exchangeToken(TOKEN)
            },
            assertFailsWith<ThreadsFailure> {
                api { throw IllegalStateException("boom $TOKEN $APP_SECRET") }.refreshToken(TOKEN)
            },
        )
        for (failure in failures) {
            val text = failure.message.orEmpty() + failure.toString() + failure.stackTraceToString()
            assertFalse(TOKEN in text, "token leaked: $text")
            assertFalse(APP_SECRET in text, "secret leaked: $text")
            assertFalse("graph.threads.net" in text, "url leaked: $text")
            assertNull(failure.cause)
        }
    }

    @Test
    fun aNetworkErrorIsTransient() = runTest {
        val client = api { throw IOException("connection reset") }
        assertIs<ThreadsFailure.Transient>(assertFailsWith<ThreadsFailure> { client.me(TOKEN) })
    }

    @Test
    fun anUnreadableSuccessBodyIsTransient() = runTest {
        assertIs<ThreadsFailure.Transient>(assertFailsWith<ThreadsFailure> { api { json("<html>") }.publish("1", TOKEN, "c") })
        assertIs<ThreadsFailure.Transient>(assertFailsWith<ThreadsFailure> { api { json("{}") }.publish("1", TOKEN, "c") })
    }

    private companion object {
        const val TOKEN = "THAAtoken123456"
        const val APP_SECRET = "app-secret-xyz"
    }
}
