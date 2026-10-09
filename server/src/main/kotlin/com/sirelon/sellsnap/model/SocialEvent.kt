package com.sirelon.sellsnap.model

import java.time.Instant

enum class EventType { Pause, Resume, ModeChange, Delete, Alert, TokenRefresh, Error }

/** [detail] must never hold a token or secret. */
data class SocialEvent(
    val id: String,
    val accountId: String,
    val type: EventType,
    val at: Instant,
    val postId: String? = null,
    val detail: String? = null,
)

data class SocialClick(
    val id: String,
    val code: String,
    val ts: Instant,
    val store: String,
    val uaFamily: String,
    val countryFromIp: String? = null,
)
