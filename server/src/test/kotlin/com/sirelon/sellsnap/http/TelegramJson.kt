package com.sirelon.sellsnap.http

import com.sirelon.sellsnap.AgentFixture
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

fun callbackUpdateJson(updateId: Long, data: String, from: Long = AgentFixture.OWNER, messageId: Long = 1000): String =
    """{"update_id":$updateId,"callback_query":{"id":"cb-$updateId","from":{"id":$from},""" +
        """"message":{"message_id":$messageId,"chat":{"id":${AgentFixture.OWNER}}},"data":"$data"}}"""

fun messageUpdateJson(updateId: Long, text: String, from: Long = AgentFixture.OWNER): String =
    """{"update_id":$updateId,"message":{"message_id":${updateId + 500},"chat":{"id":${AgentFixture.OWNER}},""" +
        """"from":{"id":$from},"text":"$text","unknown_field":true}}"""

suspend fun HttpClient.postTelegram(body: String, secret: String? = AgentFixture.HOOK_SECRET): HttpResponse =
    post("/telegram") {
        if (secret != null) header(TelegramSecret.HEADER, secret)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

suspend fun HttpClient.postScheduler(path: String, token: String? = "scheduler-token"): HttpResponse =
    post(path) {
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
    }
