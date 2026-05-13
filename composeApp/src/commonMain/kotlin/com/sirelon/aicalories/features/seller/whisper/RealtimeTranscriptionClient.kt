package com.sirelon.sellsnap.features.seller.whisper

import com.sirelon.sellsnap.network.ApiTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

private const val REALTIME_HOST = "api.openai.com"
private const val REALTIME_PATH = "/v1/realtime"
sealed interface RealtimeTranscriptionEvent {
    data object Connected : RealtimeTranscriptionEvent
    data class Delta(val text: String) : RealtimeTranscriptionEvent
    data class Completed(val text: String) : RealtimeTranscriptionEvent
    data class Error(val reason: RealtimeTranscriptionErrorReason) : RealtimeTranscriptionEvent
}

enum class RealtimeTranscriptionErrorReason {
    MissingApiKey,
    LiveTranscriptionFailed,
    ServerError,
}

class RealtimeTranscriptionClient(
    private val httpClient: HttpClient,
    private val tokenProvider: ApiTokenProvider,
    private val json: Json,
) {

    fun transcribe(audioChunks: Flow<ByteArray>): Flow<RealtimeTranscriptionEvent> = callbackFlow {
        val events = this@callbackFlow
        val token = tokenProvider.token
        if (token.isNullOrBlank()) {
            trySend(RealtimeTranscriptionEvent.Error(RealtimeTranscriptionErrorReason.MissingApiKey))
            close()
            return@callbackFlow
        }

        val socketJob = launch {
            runCatching {
                httpClient.webSocket(
                    method = HttpMethod.Get,
                    request = {
                        url {
                            protocol = URLProtocol.WSS
                            host = REALTIME_HOST
                            encodedPath = REALTIME_PATH
                            parameters.append("intent", "transcription")
                        }
                        header(HttpHeaders.Authorization, "Bearer $token")
                    },
                ) {
                    send(sessionUpdateEvent())
                    trySend(RealtimeTranscriptionEvent.Connected)

                    val audioJob = launch {
                        audioChunks.collect { chunk ->
                            send(audioAppendEvent(chunk))
                        }
                        send(inputAudioCommitEvent())
                    }

                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val shouldClose = events.handleServerEvent(text)
                        if (shouldClose) break
                    }

                    audioJob.cancelAndJoin()
                    withTimeoutOrNull(1.seconds) {
                        close()
                    }
                }
            }.onFailure {
                trySend(RealtimeTranscriptionEvent.Error(RealtimeTranscriptionErrorReason.LiveTranscriptionFailed))
            }
            close()
        }

        awaitClose {
            socketJob.cancel()
        }
    }

    private fun ProducerScope<RealtimeTranscriptionEvent>.handleServerEvent(
        text: String,
    ): Boolean {
        val event = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return false

        return when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (delta.isNotBlank()) {
                    trySend(RealtimeTranscriptionEvent.Delta(delta))
                }
                false
            }

            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = event["transcript"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (transcript.isNotBlank()) {
                    trySend(RealtimeTranscriptionEvent.Completed(transcript))
                }
                false
            }

            "error" -> {
                trySend(RealtimeTranscriptionEvent.Error(RealtimeTranscriptionErrorReason.ServerError))
                true
            }

            else -> false
        }
    }
}

private fun sessionUpdateEvent(): String =
    buildJsonObject {
        put("type", "session.update")
        put(
            "session",
            buildJsonObject {
                put("type", "transcription")
                put(
                    "audio",
                    buildJsonObject {
                        put(
                            "input",
                            buildJsonObject {
                                put(
                                    "format",
                                    buildJsonObject {
                                        put("type", "audio/pcm")
                                        put("rate", 24_000)
                                    },
                                )
                                put(
                                    "transcription",
                                    buildJsonObject {
                                        put("model", InspectionTranscriptionProfile.model)
                                        put("prompt", InspectionTranscriptionProfile.prompt)
                                    },
                                )
                                put("turn_detection", null)
                            },
                        )
                    },
                )
            },
        )
    }.toString()

@OptIn(ExperimentalEncodingApi::class)
private fun audioAppendEvent(chunk: ByteArray): String =
    buildJsonObject {
        put("type", "input_audio_buffer.append")
        put("audio", Base64.encode(chunk))
    }.toString()

private fun inputAudioCommitEvent(): String =
    buildJsonObject {
        put("type", "input_audio_buffer.commit")
    }.toString()
