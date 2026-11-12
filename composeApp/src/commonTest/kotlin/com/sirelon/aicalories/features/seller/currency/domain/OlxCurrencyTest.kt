package com.sirelon.sellsnap.features.seller.currency.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OlxCurrencyTest {

    @Test
    fun `uah keeps its symbol before the amount`() {
        assertEquals("₴ 1 850", OlxCurrency.Default.format(1850f))
        assertEquals("₴ 1 850", OlxCurrency.format(value = 1850L, code = "UAH"))
        assertTrue(OlxCurrency.Default.isLabelBeforeAmount)
    }

    @Test
    fun `pln ron bgn and eur put the label after the amount`() {
        assertEquals("280 zł", OlxCurrency("PLN", "zł", false).format(280f))
        assertEquals("100 lei", OlxCurrency("RON", "lei", false).format(100f))
        assertEquals("50 лв", OlxCurrency("BGN", "лв", false).format(50f))
        assertEquals("20 €", OlxCurrency("EUR", "€", false).format(20f))
        assertFalse(OlxCurrency("PLN", "zł", false).isLabelBeforeAmount)
    }

    @Test
    fun `blank label falls back to the label OLX gives the code`() {
        assertEquals("280 zł", OlxCurrency("PLN", "", true).format(280f))
        assertEquals("280 zł", OlxCurrency("PLN", "  ", true).format(280f))
        assertEquals("280 zł", OlxCurrency.format(value = 280L, code = "pln"))
        assertEquals("100 lei", OlxCurrency.format(value = 100L, code = "RON"))
        assertEquals("50 лв.", OlxCurrency.format(value = 50L, code = "BGN"))
        assertEquals("20 €", OlxCurrency.format(value = 20L, code = "EUR"))
        assertEquals("₴ 280", OlxCurrency.format(value = 280L, code = "UAH"))
    }

    @Test
    fun `position follows the code even when the label is a code`() {
        assertEquals("UAH 280", OlxCurrency.format(value = 280L, code = "UAH", label = "UAH"))
        assertEquals("1 850 EUR", OlxCurrency.format(value = 1850L, code = "EUR", label = "EUR"))
    }

    @Test
    fun `unknown currency also writes the label after the amount`() {
        assertEquals("5 000 KZT", OlxCurrency.format(value = 5000L, code = "KZT"))
    }

    @Test
    fun `large amounts group by thousands with a space`() {
        assertEquals("1 234 567 zł", OlxCurrency("PLN", "zł", false).format(1_234_567f))
        assertEquals("₴ 12 500", OlxCurrency.format(value = 12_500L, code = "UAH"))
        assertEquals("2 000 000 000 zł", OlxCurrency.format(value = 2_000_000_000L, code = "PLN", label = "zł"))
        assertEquals("999 lei", OlxCurrency.format(value = 999L, code = "RON", label = "lei"))
    }

    @Test
    fun `negative amounts read as zero and an empty currency leaves the bare amount`() {
        assertEquals("0 zł", OlxCurrency.format(value = -5L, code = "PLN", label = "zł"))
        assertEquals("1 800", OlxCurrency.format(value = 1800L, code = ""))
    }
}
