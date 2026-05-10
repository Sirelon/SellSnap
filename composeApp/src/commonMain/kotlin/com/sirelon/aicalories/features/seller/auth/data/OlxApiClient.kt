package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.response.PostAdvertRootResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxUserResponse
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributeResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributesResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategoriesRootResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategoryResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategorySuggestionResponse
import com.sirelon.sellsnap.features.seller.location.data.response.OlxLocationResponse
import com.sirelon.sellsnap.features.seller.location.data.response.OlxLocationsRootResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OlxApiClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val errorParser: OlxRemoteErrorParser,
) {

    suspend fun getAuthenticatedUser(): OlxUser {
        val response = httpClient.get("users/me")
        response.ensureSuccess()

        return response.decodeBody<OlxUserResponse>("authenticated user").toDomain()
    }

    internal suspend fun loadCategories(): List<OlxCategoryResponse> {
        val response = httpClient.get("categories")
        response.ensureSuccess()

        return response.decodeBody<OlxCategoriesRootResponse>("categories").data.orEmpty()
    }

    suspend fun loadCategorySuggestionId(query: String): Int? {
        val response = httpClient.get("categories/suggestion") {
            parameter("q", query)
        }
        response.ensureSuccess()

        return response.decodeBody<OlxCategorySuggestionResponse>("category suggestion")
            .data
            .orEmpty()
            .firstOrNull()
            ?.id
    }

    internal suspend fun loadAttributes(categoryId: Int): List<OlxAttributeResponse> {
        val response = httpClient.get("categories/$categoryId/attributes")
        response.ensureSuccess()

        return response.decodeBody<OlxAttributesResponse>("category attributes").data.orEmpty()
    }

    internal suspend fun getLocations(latitude: Double, longitude: Double): List<OlxLocationResponse> {
        val response = httpClient.get("locations") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
        }
        response.ensureSuccess()

        return response.decodeBody<OlxLocationsRootResponse>("locations").data.orEmpty()
    }

    internal suspend fun postAdvert(request: PostAdvertRequest): PostAdvertResult {
        val response = httpClient.post("adverts") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.ensureSuccess()

        val body = response.decodeBody<PostAdvertRootResponse>("advert publish")
        val advert = body.data ?: throw missingResponseData("advert publish", "data")
        val advertId = advert.id ?: throw missingResponseData("advert publish", "data.id")

        return PostAdvertResult(
            id = advertId,
            status = AdvertStatus.from(advert.status ?: ""),
            url = advert.url,
        )
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (!status.isSuccess()) {
            throw errorParser.parse(status, bodyAsText())
        }
    }

    private suspend inline fun <reified T> HttpResponse.decodeBody(operation: String): T {
        val payload = bodyAsText()
        if (payload.isBlank()) {
            throw missingResponseData(operation, "body")
        }

        return try {
            json.decodeFromString(payload)
        } catch (exception: SerializationException) {
            throw OlxApiException(
                OlxApiError.Unknown("Could not parse OLX response for $operation."),
            )
        }
    }

    private fun missingResponseData(operation: String, field: String): OlxApiException =
        OlxApiException(
            OlxApiError.Unknown("OLX returned an empty or incomplete response for $operation: missing $field."),
        )
}
