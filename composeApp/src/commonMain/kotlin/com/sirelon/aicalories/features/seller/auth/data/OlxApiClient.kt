package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertResponseOnlyKeys
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxAdvertDetailResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxAdvertDetailRootResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxAdvertStatisticsResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxAdvertsRootResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxModerationReasonResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.PostAdvertRootResponse
import com.sirelon.sellsnap.features.seller.auth.data.response.OlxUserRootResponse
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvert
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributeResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributesResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategoriesRootResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategoryResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategorySuggestionResponse
import com.sirelon.sellsnap.features.seller.currency.data.response.OlxCurrenciesRootResponse
import com.sirelon.sellsnap.features.seller.currency.data.response.OlxCurrencyResponse
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.location.data.response.OlxLocationResponse
import com.sirelon.sellsnap.features.seller.location.data.response.OlxLocationsRootResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class OlxApiClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val errorParser: OlxRemoteErrorParser,
) {

    suspend fun getAuthenticatedUser(): OlxUser {
        val response = httpClient.get("users/me")
        response.ensureSuccess()

        val user = response.decodeBody<OlxUserRootResponse>("authenticated user").data
            ?: throw missingResponseData("authenticated user", "data")
        return user.toDomain()
    }

    /**
     * Same call, with an explicit bearer token instead of relying on this client's own Auth
     * plugin. Used during add-account/reconnect (SIR-83): at that point the freshly exchanged
     * token is deliberately not in the account store yet, since whether it's a duplicate isn't
     * known until this call returns, so it can't be resolved via the authorized client's
     * account-store-backed [io.ktor.client.plugins.auth.providers.bearer] provider.
     */
    suspend fun getAuthenticatedUser(accessToken: String): OlxUser {
        val response = httpClient.get("users/me") { bearerAuth(accessToken) }
        response.ensureSuccess()

        val user = response.decodeBody<OlxUserRootResponse>("authenticated user").data
            ?: throw missingResponseData("authenticated user", "data")
        return user.toDomain()
    }

    suspend fun getCurrentUserAdverts(offset: Int, limit: Int): List<OlxAdvert> {
        val response = httpClient.get("adverts") {
            parameter("offset", offset)
            parameter("limit", limit)
        }
        response.ensureSuccess()

        return response.decodeBody<OlxAdvertsRootResponse>("user adverts")
            .data
            .orEmpty()
            .mapNotNull { it.toDomain() }
    }

    /**
     * Same call, with an explicit bearer token instead of relying on this client's own Auth
     * plugin. Used by the My Ads account pager (SIR-87): each page fetches with its own account's
     * token via [com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository]'s
     * per-account token access, since the shared authorized client only ever serves whichever
     * account is globally active.
     */
    suspend fun getCurrentUserAdverts(accessToken: String, offset: Int, limit: Int): List<OlxAdvert> {
        val response = httpClient.get("adverts") {
            bearerAuth(accessToken)
            parameter("offset", offset)
            parameter("limit", limit)
        }
        response.ensureSuccess()

        return response.decodeBody<OlxAdvertsRootResponse>("user adverts")
            .data
            .orEmpty()
            .mapNotNull { it.toDomain() }
    }

    /**
     * `GET adverts/{id}` (SIR-98). Returns the typed view plus the raw `data` object with the
     * response-only keys stripped, so an edit can echo every untouched field back to
     * `PUT adverts/{id}` exactly as OLX sent it - see [AdvertEditSnapshot].
     */
    internal suspend fun getAdvert(accessToken: String, advertId: Long): AdvertEditSnapshot {
        val response = httpClient.get("adverts/$advertId") { bearerAuth(accessToken) }
        response.ensureSuccess()

        val raw = response.decodeBody<OlxAdvertDetailRootResponse>("advert").data
            ?: throw missingResponseData("advert", "data")
        val detail = json.decodeFromJsonElement<OlxAdvertDetailResponse>(raw).toDomain()
            ?: throw missingResponseData("advert", "data.id")

        return AdvertEditSnapshot(
            detail = detail,
            updatePayload = JsonObject(raw.filterKeys { it !in AdvertResponseOnlyKeys }),
        )
    }

    /**
     * `PUT adverts/{id}` (SIR-104). Takes the whole create payload as raw JSON rather than a
     * typed request: the body is [AdvertEditSnapshot.updatePayload] with the edited fields
     * replaced, so fields this app does not model cannot be dropped by an edit.
     *
     * That echo is not defensive over-engineering. The spec singles out `auto_extend_enabled` as
     * the one field PUT leaves unchanged when omitted, which is indirect evidence that everything
     * else omitted - `courier`, `ad_delivery`, `product_safety_regulation` - is reset instead. A
     * typed request built from app state would silently clear them.
     *
     * PUT applies the same validation rules as create, so an edit can plausibly be rejected for
     * reasons the original publish passed (a category's attribute requirements having changed
     * since). Whether OLX also re-runs moderation on an edit is unverified - see
     * `SPIKE-SIR-99-advert-edit-round-trip.md` risks (d) and (e).
     *
     * The response body is deliberately not parsed. The status code is the whole result, and the
     * caller re-reads the row with [getAdvert] anyway - so parsing here would add a way for an
     * edit that OLX accepted to be reported as a failure, over a field nobody reads.
     */
    internal suspend fun putAdvert(accessToken: String, advertId: Long, body: JsonObject) {
        val response = httpClient.put("adverts/$advertId") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        response.ensureSuccess()
    }

    /**
     * `POST adverts/{id}/commands` (SIR-98). Success is 204 with no body, so there is nothing to
     * decode - the status code is the whole result. Every documented failure (deleting an active
     * advert, deactivating an inactive one, `extend` in a market that rejects it) arrives as a
     * 400 validation payload and is turned into a typed error by [OlxRemoteErrorParser].
     */
    internal suspend fun sendAdvertCommand(
        accessToken: String,
        advertId: Long,
        command: AdvertCommand,
        isSuccess: Boolean? = null,
    ) {
        val response = httpClient.post("adverts/$advertId/commands") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                AdvertCommandRequest(
                    command = command.wireValue,
                    isSuccess = isSuccess.takeIf { command == AdvertCommand.Deactivate },
                ),
            )
        }
        response.ensureSuccess()
    }

    /**
     * `DELETE adverts/{id}` (SIR-98). 204 on success. OLX rejects this while the advert is still
     * active, with `field: "ad", title: "Invalid status"` - the caller is responsible for
     * deactivating first, and for telling the seller when only that half succeeded.
     */
    internal suspend fun deleteAdvert(accessToken: String, advertId: Long) {
        val response = httpClient.delete("adverts/$advertId") { bearerAuth(accessToken) }
        response.ensureSuccess()
    }

    /**
     * `GET adverts/{id}/moderation-reason`. OLX's own words for why an advert is in the state it
     * is in, or null when it has none - a 404 is a perfectly ordinary answer here and must not
     * surface as an error.
     *
     * Whether OLX answers at all is itself the useful signal: the status vocabulary has no
     * documented per-status meaning, so an advert that returns a reason is one OLX considers
     * moderated, whatever its status string happens to be called.
     */
    internal suspend fun getAdvertModerationReason(accessToken: String, advertId: Long): String? {
        val response = httpClient.get("adverts/$advertId/moderation-reason") { bearerAuth(accessToken) }
        if (response.status == HttpStatusCode.NotFound) return null
        response.ensureSuccess()

        val payload = response.bodyAsText()
        if (payload.isBlank()) return null

        return try {
            json.decodeFromString<OlxModerationReasonResponse>(payload).toDomain()
        } catch (exception: SerializationException) {
            // Diagnostic text only. An unparseable reason is not worth failing the sheet over.
            null
        }
    }

    /**
     * `GET adverts/{id}/statistics` (SIR-98). The spec's example is unwrapped
     * (`{"advert_views": 123, ...}`) while every other OLX resource nests under `data`; both are
     * accepted, the same way [loadCurrencies] tolerates a bare array or a wrapped object.
     */
    internal suspend fun getAdvertStatistics(accessToken: String, advertId: Long): OlxAdvertStatistics {
        val response = httpClient.get("adverts/$advertId/statistics") { bearerAuth(accessToken) }
        response.ensureSuccess()

        val payload = response.bodyAsText()
        if (payload.isBlank()) {
            throw missingResponseData("advert statistics", "body")
        }

        val statistics = try {
            val element = json.parseToJsonElement(payload)
            val wrapped = (element as? JsonObject)?.get("data")
            if (wrapped != null) {
                json.decodeFromJsonElement<OlxAdvertStatisticsResponse>(wrapped)
            } else {
                json.decodeFromJsonElement<OlxAdvertStatisticsResponse>(element)
            }
        } catch (exception: SerializationException) {
            throw OlxApiException(
                OlxApiError.Unknown("Could not parse OLX response for advert statistics."),
            )
        }

        return statistics.toDomain()
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
            ?.toIntOrNull()
    }

    internal suspend fun loadAttributes(categoryId: Int): List<OlxAttributeResponse> {
        val response = httpClient.get("categories/$categoryId/attributes")
        response.ensureSuccess()

        return response.decodeBody<OlxAttributesResponse>("category attributes").data.orEmpty()
    }

    suspend fun loadCurrencies(): List<OlxCurrency> {
        val response = httpClient.get("currencies")
        response.ensureSuccess()

        val payload = response.bodyAsText()
        if (payload.isBlank()) {
            throw missingResponseData("currencies", "body")
        }

        val currencyResponses = try {
            val element = json.parseToJsonElement(payload)
            when (element) {
                is JsonArray -> json.decodeFromJsonElement<List<OlxCurrencyResponse>>(element)
                else -> json.decodeFromJsonElement<OlxCurrenciesRootResponse>(element).data.orEmpty()
            }
        } catch (exception: SerializationException) {
            throw OlxApiException(
                OlxApiError.Unknown("Could not parse OLX response for currencies."),
            )
        }

        return currencyResponses.mapNotNull { it.toDomain() }
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
