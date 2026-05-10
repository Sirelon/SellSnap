package com.sirelon.sellsnap.features.seller.location.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxLocationsRootResponse(
    @SerialName("data")
    val data: List<OlxLocationResponse>?,
)


@Serializable
internal class OlxLocationResponse(
    @SerialName("city")
    val city: OlxCityResponse?,

    @SerialName("district")
    val district: OlxDistrictResponse?,
)

@Serializable
internal class OlxCityResponse(
    @SerialName("id")
    val id: Int?,

    @SerialName("region_id")
    val regionId: Int?,

    @SerialName("name")
    val name: String?,

    @SerialName("county")
    val county: String?,

    @SerialName("municipality")
    val municipality: String?,

    @SerialName("latitude")
    val latitude: Double?,

    @SerialName("longitude")
    val longitude: Double?,
)

@Serializable
internal class OlxDistrictResponse(
    @SerialName("id")
    val id: Int?,

    @SerialName("city_id")
    val cityId: Int?,

    @SerialName("name")
    val name: String?,

    @SerialName("latitude")
    val latitude: Double?,

    @SerialName("longitude")
    val longitude: Double?,
)
