package com.sirelon.sellsnap.features.seller.categories.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxCategoriesRootResponse(
    @SerialName("data")
    val data: List<OlxCategoryResponse>?,
)

@Serializable
internal class OlxCategoryResponse(
    @SerialName("id")
    val id: Int?,

    @SerialName("name")
    val label: String?,

    @SerialName("parent_id")
    val parentId: Int?,

    @SerialName("is_leaf")
    val isLeaf: Boolean?,
)
