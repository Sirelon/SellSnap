package com.sirelon.sellsnap.features.seller.auth.domain

import com.sirelon.sellsnap.platform.getDeviceCountryCode

data class OlxCountry(
    val code: String,
    val flag: String,
    val nameEn: String,
    val nameNative: String,
    val domain: String,
    val clientId: String,
    val clientSecret: String,
    val language: String,
    val currencyCode: String,
) {
    val apiBaseUrl: String get() = "https://www.$domain/api/partner/"
    val authBaseUrl: String get() = "https://www.$domain/oauth/authorize/"

    /**
     * Whether `POST adverts/{id}/commands` accepts the `extend` command here. OLX documents the
     * command as unavailable in Ukraine and Portugal, so offering a renew action there would put
     * a button in front of the seller that can only return an error - see SIR-100 and
     * `SPIKE-SIR-100-auto-extend.md`.
     */
    val supportsExtendCommand: Boolean get() = code != "ua" && code != "pt"

    /**
     * The step an AI-suggested [price] in [currencyCode] is rounded to, so the same item lands on
     * the same round number from one generation to the next. The band is picked by the price
     * before rounding.
     *
     * Inferred: these steps are SellSnap's own convention for what a private seller would type,
     * not an OLX rule. Bulgaria prices in EUR, so it takes the EUR steps. A currency without a
     * step here is left as the model wrote it.
     */
    fun priceRoundingStep(price: Float): Int = when (currencyCode) {
        "PLN", "RON" -> if (price < 100f) 5 else 10
        "UAH" -> if (price < 1_000f) 50 else 100
        "EUR" -> if (price < 50f) 1 else 5
        else -> 1
    }

    // Redact credentials so they never leak through logs, analytics, or crash reports.
    override fun toString(): String =
        "OlxCountry(code=$code, domain=$domain, language=$language, currencyCode=$currencyCode)"

    companion object {
        val PT = OlxCountry("pt", "🇵🇹", "Portugal",   "Portugal",   "olx.pt",  clientId = "201248",  clientSecret = "lss3rMOnf8RGDQMkCYuzq3ipE3WuTBa2u1o53kWlwFYtBuXP", language = "Portuguese", currencyCode = "EUR")
        val RO = OlxCountry("ro", "🇷🇴", "Romania",    "România",    "olx.ro",  clientId = "200864",  clientSecret = "DVq0A30wixRWs70OZhguxgtQ8rVltcsvfQfD84YW0oPLzZC8",  language = "Romanian",   currencyCode = "RON")
        val PL = OlxCountry("pl", "🇵🇱", "Poland",     "Polska",     "olx.pl",  clientId = "203018", clientSecret = "22f2Mk6pSqJsBtKWsKhxe7UKCLJcHfUsoOuNCR08GT7rvcqM",   language = "Polish",     currencyCode = "PLN")
        val UA = OlxCountry("ua", "🇺🇦", "Ukraine",    "Україна",    "olx.ua",  clientId = "202504",  clientSecret = "HrYHpyqOxmviAjajemibiPgIIg8u20Sru0QeOOG59ISXltJW",  language = "Ukrainian",  currencyCode = "UAH")
        // Bulgaria uses the euro since 2026-01-01; OLX.bg lists EUR as its default currency.
        val BG = OlxCountry("bg", "🇧🇬", "Bulgaria",   "България",   "olx.bg",  clientId = "200500",  clientSecret = "BRldA1nMMFJsT4taqqEW9htNkPNcECt9FQzmJ984mfzIj8bP",  language = "Bulgarian",  currencyCode = "EUR")
        val KZ = OlxCountry("kz", "🇰🇿", "Kazakhstan", "Қазақстан",  "olx.kz",  clientId = "",        clientSecret = "",                                                    language = "Russian",    currencyCode = "KZT")

        val all = listOf(PT, RO, PL, UA, BG, KZ).filter { it.clientId.isNotEmpty() && it.clientSecret.isNotEmpty() }

        fun fromCode(code: String?) = all.find { it.code == code?.lowercase() }

        fun defaultForLocale(): OlxCountry = fromCode(getDeviceCountryCode()) ?: UA
    }
}
