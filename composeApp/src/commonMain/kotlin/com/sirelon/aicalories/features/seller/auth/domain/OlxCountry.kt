package com.sirelon.sellsnap.features.seller.auth.domain

import com.sirelon.sellsnap.platform.getDeviceCountryCode

data class OlxCountry(
    val code: String,
    val flag: String,
    val nameEn: String,
    val nameNative: String,
    val domain: String,
) {
    val apiBaseUrl: String get() = "https://www.$domain/api/partner/"
    val authBaseUrl: String get() = "https://www.$domain/oauth/authorize/"

    companion object {
        val PT = OlxCountry("pt", "🇵🇹", "Portugal",   "Portugal",   "olx.pt")
        val RO = OlxCountry("ro", "🇷🇴", "Romania",    "România",    "olx.ro")
        val PL = OlxCountry("pl", "🇵🇱", "Poland",     "Polska",     "olx.pl")
        val UA = OlxCountry("ua", "🇺🇦", "Ukraine",    "Україна",    "olx.ua")
        val BG = OlxCountry("bg", "🇧🇬", "Bulgaria",   "България",   "olx.bg")
        val KZ = OlxCountry("kz", "🇰🇿", "Kazakhstan", "Қазақстан",  "olx.kz")

        val all = listOf(PT, RO, PL, UA, BG, KZ)

        fun fromCode(code: String?) = all.find { it.code == code?.lowercase() }

        fun defaultForLocale(): OlxCountry = fromCode(getDeviceCountryCode()) ?: UA
    }
}
