package com.sirelon.sellsnap.features.seller.currency.data.response

import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxCurrenciesRootResponse(
    @SerialName("data")
    val data: List<OlxCurrencyResponse>?,
)

@Serializable
internal class OlxCurrencyResponse(
    @SerialName("code")
    val code: String?,

    @SerialName("label")
    val label: String?,

    @SerialName("is_default")
    val isDefault: Boolean?,
) {
    fun toDomain(): OlxCurrency? {
        val currencyCode = code?.takeIf { it.isNotBlank() } ?: return null
        return OlxCurrency(
            code = currencyCode,
            label = label.orEmpty(),
            isDefault = isDefault == true,
        )
    }
}
