package com.sirelon.sellsnap.features.seller.auth.data.response

import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET adverts/{id}/statistics`. The OpenAPI spec's response schema points straight at the
 * statistics object and its example is unwrapped (`{"advert_views": 123, ...}`), unlike every
 * other OLX resource, which nests under `data`. Both shapes are accepted - see
 * [com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient.getAdvertStatistics], which
 * follows the same defensive parse the currencies endpoint already needed.
 */
@Serializable
internal class OlxAdvertStatisticsResponse(
    @SerialName("advert_views")
    val advertViews: Int?,

    @SerialName("phone_views")
    val phoneViews: Int?,

    @SerialName("users_observing")
    val usersObserving: Int?,
) {
    /**
     * A brand-new advert has no statistics yet, and OLX may omit a counter entirely rather than
     * send a zero. Absent and zero both mean "nobody has done this yet", so both map to 0 - the
     * "too early to tell" empty state is decided from [OlxAdvertStatistics.isEmpty], not from
     * nullability.
     */
    fun toDomain(): OlxAdvertStatistics = OlxAdvertStatistics(
        advertViews = advertViews ?: 0,
        phoneViews = phoneViews ?: 0,
        usersObserving = usersObserving ?: 0,
    )
}
