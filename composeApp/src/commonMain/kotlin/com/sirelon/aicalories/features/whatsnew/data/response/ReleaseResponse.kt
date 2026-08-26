package com.sirelon.sellsnap.features.whatsnew.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Unlike a typical Response class, every field here needs a decoder-level default: these
// documents are hand-edited in Firestore outside the build, so a key can be entirely absent
// (not just null) and must not fail the whole collection's decode.
@Serializable
internal class ReleaseResponse(
    @SerialName("version") val version: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("active") val active: Boolean? = null,
    @SerialName("changes") val changes: List<ReleaseChangeResponse>? = null,
)

@Serializable
internal class ReleaseChangeResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("icon") val icon: String? = null,
    // Keyed by language code ("en", "uk", ...), matching the app's supported Compose resource
    // locales. Hand-edited in Firestore, so a translation can simply be left out.
    @SerialName("title") val title: Map<String, String>? = null,
    @SerialName("summary") val summary: Map<String, String>? = null,
    @SerialName("detail") val detail: Map<String, String>? = null,
)
