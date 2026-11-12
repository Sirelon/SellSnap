package com.sirelon.sellsnap.features.seller.currency.domain

import com.sirelon.sellsnap.designsystem.formatPrice
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry

data class OlxCurrency(
    val code: String,
    val label: String,
    val isDefault: Boolean,
) {
    /**
     * Where the label sits relative to the amount, decided by [code], never by the device locale.
     * Also read by the price input, which places its label the same way.
     */
    val isLabelBeforeAmount: Boolean get() = isLabelBeforeAmount(code)

    /** The one way a price is shown to the seller. See [Companion.format] for the rule. */
    fun format(value: Float): String = format(value = value.toLong(), code = code, label = label)

    fun displayLabel(): String = displayLabel(code = code, label = label)

    companion object {
        /**
         * Currencies whose label is written before the amount. Every other currency writes it
         * after. Adding a code here moves that market's label, nothing else changes.
         */
        private val LabelBeforeAmount = setOf("UAH")

        private fun isLabelBeforeAmount(code: String): Boolean =
            code.uppercase() in LabelBeforeAmount

        /**
         * The label OLX gives each currency code, for prices that arrive with a code only (the
         * advert list and detail). Source: `GET /api/partner/currencies` on olx.pl, olx.ro, olx.bg,
         * olx.pt and olx.ua, fetched 2026-11-12: PLN `zł`, RON `lei`, BGN `лв.`, EUR `€`, USD `$`.
         * UAH is left out on purpose: OLX labels it `грн.`, and the app shows `₴` until the owner
         * picks the Ukrainian format (SIR-191).
         */
        private val OlxLabels = mapOf(
            "PLN" to "zł",
            "RON" to "lei",
            "BGN" to "лв.",
            "EUR" to "€",
            "USD" to "$",
        )

        private fun displayLabel(code: String, label: String): String = when {
            label.isNotBlank() -> label
            code.equals(Default.code, ignoreCase = true) -> Default.label
            else -> OlxLabels[code.uppercase()] ?: code.uppercase()
        }

        /**
         * A price as the seller reads it, the single formatter behind every price on screen
         * (preview, publish confirm, drafts, My Ads, the conversation header): `₴ 1 850` for UAH,
         * `280 zł` for PLN, `100 lei` for RON, `50 лв.` for BGN, `20 €` for EUR. The label is the
         * one OLX supplies in its currency list, else the known OLX label for [code] ([OlxLabels]),
         * else [code] itself (`5 000 KZT`); an empty [code] with no [label] leaves the bare amount.
         * Digits group by thousands with a space ([formatPrice]).
         *
         * Inferred: the position rule is the local writing convention of each market, not an OLX
         * fact. UAH keeps its label before the amount, as the app has always shown it; every other
         * currency writes the label after the amount. The position is chosen by currency [code],
         * not by locale.
         */
        fun format(value: Long, code: String, label: String = ""): String {
            val amount = formatPrice(value)
            val shown = displayLabel(code = code, label = label)
            return when {
                shown.isBlank() -> amount
                isLabelBeforeAmount(code) -> "$shown $amount"
                else -> "$amount $shown"
            }
        }

        val Default = OlxCurrency(
            code = "UAH",
            label = "₴",
            isDefault = true,
        )

        /**
         * What to price in when OLX's currency list is unavailable - the request failed, or a guest
         * never made it: the country's own currency, the one the AI priced the listing in. UAH keeps
         * its ₴ symbol; other currencies show their code until OLX supplies a label.
         */
        fun fallbackFor(country: OlxCountry): OlxCurrency =
            if (country.currencyCode == Default.code) {
                Default
            } else {
                OlxCurrency(code = country.currencyCode, label = "", isDefault = true)
            }
    }
}
