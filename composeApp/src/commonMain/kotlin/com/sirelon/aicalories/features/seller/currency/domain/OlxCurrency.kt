package com.sirelon.sellsnap.features.seller.currency.domain

import com.sirelon.sellsnap.designsystem.formatPrice

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
    }
}
