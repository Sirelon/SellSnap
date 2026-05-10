package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OlxRemoteErrorParser(private val json: Json) {

    fun parse(status: HttpStatusCode, payload: String): OlxApiException {
        if (payload.isBlank()) {
            return when (status) {
                HttpStatusCode.Unauthorized -> OlxApiException(
                    OlxApiError.InvalidToken("OLX rejected the current token with an empty response."),
                )

                HttpStatusCode.Forbidden -> OlxApiException(
                    OlxApiError.InsufficientScope("OLX rejected the request with an empty forbidden response."),
                )

                else -> OlxApiException(
                    OlxApiError.Unknown(
                        "OLX request failed with HTTP ${status.value} ${status.description}; response body was empty.",
                    ),
                )
            }
        }

        val oauthError = runCatching {
            json.decodeFromString(OAuthErrorPayload.serializer(), payload)
        }.getOrNull()
        if (oauthError?.error != null) {
            return when (oauthError.error) {
                "invalid_client" -> OlxApiException(
                    OlxApiError.InvalidClient(
                        oauthError.errorDescription ?: "Invalid OLX client credentials.",
                    ),
                )

                "invalid_grant" -> OlxApiException(
                    OlxApiError.InvalidGrant(
                        oauthError.errorDescription ?: "Invalid OLX grant.",
                    ),
                )

                "invalid_token" -> OlxApiException(
                    OlxApiError.InvalidToken(
                        oauthError.errorDescription ?: "Invalid OLX token.",
                    ),
                )

                "insufficient_scope" -> OlxApiException(
                    OlxApiError.InsufficientScope(
                        oauthError.errorDescription ?: "Insufficient OLX scope.",
                    ),
                )

                else -> OlxApiException(
                    OlxApiError.Unknown(
                        oauthError.errorDescription ?: "OLX request failed with ${oauthError.error}.",
                    ),
                )
            }
        }

        val apiError = runCatching {
            json.decodeFromString(ApiErrorPayload.serializer(), payload)
        }.getOrNull()

        val fieldErrors = apiError?.error?.validation.orEmpty()
        if (fieldErrors.isNotEmpty()) {
            val first = fieldErrors.first()
            val field = first.field ?: "unknown"
            val detail = first.detail ?: first.title ?: "Invalid value"
            return OlxApiException(OlxApiError.ValidationError(field, detail))
        }

        val detail = apiError?.error?.detail ?: apiError?.error?.title
        if (!detail.isNullOrBlank()) {
            return when (status) {
                HttpStatusCode.Unauthorized -> OlxApiException(OlxApiError.InvalidToken(detail))
                HttpStatusCode.Forbidden -> OlxApiException(OlxApiError.InsufficientScope(detail))
                else -> OlxApiException(OlxApiError.Unknown(detail))
            }
        }

        return when (status) {
            HttpStatusCode.Unauthorized -> OlxApiException(
                OlxApiError.InvalidToken("OLX rejected the current token."),
            )

            HttpStatusCode.Forbidden -> OlxApiException(OlxApiError.InsufficientScope())
            HttpStatusCode.TooManyRequests -> OlxApiException(OlxApiError.RateLimited())
            else -> OlxApiException(OlxApiError.Unknown("OLX request failed with HTTP ${status.value}."))
        }
    }

    @Serializable
    private class OAuthErrorPayload(
        @SerialName("error") val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    @Serializable
    private class ApiErrorPayload(
        @SerialName("error") val error: ApiErrorBody? = null,
    )

    @Serializable
    private class ApiErrorBody(
        @SerialName("status") val status: Int? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("detail") val detail: String? = null,
        @SerialName("validation") val validation: List<FieldError> = emptyList(),
    )

    @Serializable
    private class FieldError(
        @SerialName("field") val field: String? = null,
        @SerialName("detail") val detail: String? = null,
        @SerialName("title") val title: String? = null,
    )
}
