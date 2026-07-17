package com.sirelon.sellsnap.legal

/**
 * Single source of truth for SellSnap's legal / data-rights links, so the landing screen,
 * consent prompt, and Profile all point at the same hosted pages and contact address.
 */
object LegalLinks {
    const val TERMS_URL = "https://sirelon.github.io/SellSnap/terms-and-conditions/"
    const val PRIVACY_URL = "https://sirelon.github.io/SellSnap/privacy-policy/"
    const val DATA_REQUEST_MAILTO = "mailto:sasha.sirelon@gmail.com?subject=SellSnap%20data%20request"
}
