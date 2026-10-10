package com.sirelon.sellsnap.content

/**
 * The one place that builds a post's link URL (D15): the redirect `{baseUrl}/go/{code}`. [baseUrl]
 * is the service's public base URL from config, so pointing links elsewhere is a config change.
 */
fun postLinkUrl(baseUrl: String, code: String): String = "${baseUrl.trimEnd('/')}/go/$code"
