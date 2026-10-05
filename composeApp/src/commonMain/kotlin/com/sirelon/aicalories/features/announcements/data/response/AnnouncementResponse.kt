package com.sirelon.sellsnap.features.announcements.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Unlike a typical Response class, every field here needs a decoder-level default: these
// documents are hand-edited in the Firestore console, so a key can be entirely absent (not just
// null) and must not fail the decode.
//
// The content and the rules deliberately live in the SAME document. Two documents (content, then
// rules) cannot be written atomically from the console, so a device could read the content in the
// gap before its rules exist and show it to everyone. One document is one write.
@Serializable
internal class AnnouncementResponse(
    @SerialName("active") val active: Boolean? = null,
    @SerialName("content") val content: AnnouncementContentResponse? = null,
    @SerialName("rules") val rules: AnnouncementRulesResponse? = null,
)

@Serializable
internal class AnnouncementContentResponse(
    // Keyed by language code ("en", "uk", ...). Hand-edited, so a translation can be left out.
    @SerialName("title") val title: Map<String, String>? = null,
    @SerialName("body") val body: Map<String, String>? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
)

// `minVersion` and `maxVersion` are typed String on purpose: "3.10" typed as a number in the
// console is stored as 3.1 and cannot be told apart from "3.1". A number-typed value fails to
// decode, so that whole document is dropped (see the repository) rather than guessed at.
@Serializable
internal class AnnouncementRulesResponse(
    @SerialName("dismissible") val dismissible: Boolean? = null,
    @SerialName("showMode") val showMode: String? = null,
    @SerialName("minVersion") val minVersion: String? = null,
    @SerialName("maxVersion") val maxVersion: String? = null,
    @SerialName("userIds") val userIds: List<String>? = null,
    @SerialName("olxUserIds") val olxUserIds: List<Long>? = null,
    @SerialName("priority") val priority: Int? = null,
)
