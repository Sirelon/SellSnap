package com.sirelon.sellsnap.features.seller.categories.domain

import kotlinx.serialization.Serializable

@Serializable
data class OlxCategory(
    val id: Int,
    val label: String,
    val parentId: Int?,
    val isLeaf: Boolean,
)