package com.sirelon.sellsnap.features.seller.categories.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxCategorySuggestionResponse(
    @SerialName("data")
    val data: List<OlxCategorySuggestionResponseItem>?,
) {
    @Serializable
    internal class OlxCategorySuggestionResponseItem(
        @SerialName("id")
        val id: Int?,
    )
}
