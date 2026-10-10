package com.sirelon.sellsnap.content

import java.net.URLEncoder

/** Template used when `POST_LINK_TEMPLATE` is not set: the service's own redirect. */
const val DEFAULT_POST_LINK_TEMPLATE = "{PUBLIC_BASE_URL}/go/{code}"

/** Fills `{PUBLIC_BASE_URL}` (without a trailing slash) in a configured link template. */
fun expandLinkTemplate(template: String, publicBaseUrl: String): String =
    template.replace("{PUBLIC_BASE_URL}", publicBaseUrl.trimEnd('/'))

/**
 * The one place that builds a post's link URL (D15). [template] is the configured link template with
 * `{PUBLIC_BASE_URL}` already filled; `{code}` becomes the post code and `{lang}` the account language.
 * Pointing links elsewhere is a config change.
 */
fun postLinkUrl(template: String, code: String, language: String): String =
    template.replace("{code}", code).replace("{lang}", URLEncoder.encode(language, Charsets.UTF_8))
