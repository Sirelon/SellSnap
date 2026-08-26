package com.sirelon.sellsnap.features.whatsnew.model

data class Release(
    val version: String,
    val date: String,
    val changes: List<Change>,
) {
    data class Change(
        val id: String,
        val icon: String,
        val title: String,
        val summary: String,
        val detail: String? = null,
    )
}
