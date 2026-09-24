package com.sirelon.sellsnap.features.seller.currency.domain

import com.sirelon.sellsnap.designsystem.formatPrice
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry

data class OlxCurrency(
    val code: String,
    val label: String,
    val isDefault: Boolean,
) {
    fun format(value: Float): String = "${displayLabel()} ${formatPrice(value)}"

    fun displayLabel(): String = label.ifBlank { code }

    companion object {
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
