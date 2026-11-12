package com.sirelon.sellsnap.features.seller.currency.domain

import com.sirelon.sellsnap.designsystem.formatPrice
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry

data class OlxCurrency(
    val code: String,
    val label: String,
    val isDefault: Boolean,
) {
    /** The one way a price is shown to the seller. See [Companion.format] for the rule. */
    fun format(value: Float): String = format(value = value.toLong(), code = code, label = label)

    fun displayLabel(): String = displayLabel(code = code, label = label)

    companion object {
        /**
         * The label OLX gives each currency code, for prices that arrive with a code only (the
         * advert list and detail) or before OLX's currency list has loaded. Source:
         * `GET /api/partner/currencies` on olx.ua, olx.pl, olx.ro, olx.bg and olx.pt, fetched
         * 2026-11-12: UAH `грн.`, PLN `zł`, RON `lei`, BGN `лв.`, EUR `€`, USD `$`.
         */
        private val OlxLabels = mapOf(
            "UAH" to "грн.",
            "PLN" to "zł",
            "RON" to "lei",
            "BGN" to "лв.",
            "EUR" to "€",
            "USD" to "$",
        )

        private fun displayLabel(code: String, label: String): String =
            label.ifBlank { OlxLabels[code.uppercase()] ?: code.uppercase() }

        /**
         * A price as the seller reads it, the single formatter behind every price on screen
         * (preview, publish confirm, drafts, My Ads, the conversation header): `1 850 грн.` for
         * UAH, `280 zł` for PLN, `100 lei` for RON, `50 лв.` for BGN, `20 €` for EUR. The label is
         * the one OLX supplies in its currency list, else the known OLX label for [code]
         * ([OlxLabels]), else [code] itself (`5 000 KZT`); an empty [code] with no [label] leaves
         * the bare amount. Digits group by thousands with a space ([formatPrice]).
         *
         * Inferred: the label goes after the amount in every market, as sellers in each of them
         * write prices; the owner chose `1 850 грн.` for Ukraine (SIR-191, 2026-11-12).
         */
        fun format(value: Long, code: String, label: String = ""): String {
            val amount = formatPrice(value)
            val shown = displayLabel(code = code, label = label)
            return if (shown.isBlank()) amount else "$amount $shown"
        }

        val Default = OlxCurrency(
            code = "UAH",
            label = "грн.",
            isDefault = true,
        )

        /**
         * What to price in when OLX's currency list is unavailable - the request failed, or a guest
         * never made it: the country's own currency, the one the AI priced the listing in. Its
         * label comes from [OlxLabels] until OLX supplies one.
         */
        fun fallbackFor(country: OlxCountry): OlxCurrency =
            if (country.currencyCode == Default.code) {
                Default
            } else {
                OlxCurrency(code = country.currencyCode, label = "", isDefault = true)
            }
    }
}
