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
        // OLX returns the suggested category id as a numeric string (e.g. "85"), unlike the
        // integer ids from /categories. Kept as String and converted at the call site so parsing
        // doesn't silently rely on lenient string->int coercion.
        @SerialName("id")
        val id: String?,
    )
}
