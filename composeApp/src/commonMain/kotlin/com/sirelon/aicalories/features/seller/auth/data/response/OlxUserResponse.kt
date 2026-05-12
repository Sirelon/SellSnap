package com.sirelon.sellsnap.features.seller.auth.data.response

import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxUserRootResponse(
    @SerialName("data")
    val data: OlxUserResponse?,
)

@Serializable
internal class OlxUserResponse(
    @SerialName("id")
    val id: Long?,

    @SerialName("email")
    val email: String?,

    @SerialName("status")
    val status: String?,

    @SerialName("name")
    val name: String?,

    @SerialName("phone")
    val phone: String?,

    @SerialName("created_at")
    val createdAt: String?,

    @SerialName("last_login_at")
    val lastLoginAt: String?,

    @SerialName("avatar")
    val avatar: String?,

    @SerialName("is_business")
    val isBusiness: Boolean?,
) {
    fun toDomain(): OlxUser {
        return OlxUser(
            id = id ?: 0L,
            email = email.orEmpty(),
            status = status.orEmpty(),
            name = name.orEmpty(),
            phone = phone.orEmpty(),
            createdAt = createdAt.orEmpty(),
            lastLoginAt = lastLoginAt.orEmpty(),
            avatar = avatar,
            isBusiness = isBusiness ?: false,
        )
    }
}
