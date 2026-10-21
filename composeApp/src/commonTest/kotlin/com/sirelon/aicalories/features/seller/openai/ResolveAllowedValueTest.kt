package com.sirelon.sellsnap.features.seller.openai

import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Size options captured from the live OLX attributes API for men's shirts (2026-10-22). */
class ResolveAllowedValueTest {

    private val plSizes = listOf(
        "xxxs" to "XXXS / 40", "xxs" to "XXS / 42", "xs" to "XS / 44", "s" to "S / 46", "m" to "M / 48",
        "l" to "L / 50-52", "xl" to "XL / 54-56", "xxl" to "XXL / 58", "xxxl" to "XXXL / 60",
        "4xl" to "4XL / 62", "uniwersalny" to "Uniwersalny", "others" to "Pozostałe",
    ).map { (code, label) -> OlxAttributeValue(code, label) }

    private val uaSizes = listOf(
        "xs" to "34 / XS / 42", "s" to "36 / S / 44", "m" to "38 / M / 46", "l" to "40 / L / 48",
        "xl" to "42 / XL / 50", "xxl" to "44 / XXL / 52", "xxxl" to "46 / XXXL / 54",
        "one_size" to "One size", "other" to "Інший",
    ).map { (code, label) -> OlxAttributeValue(code, label) }

    @Test
    fun everyCodeResolvesToItsOwnOption() {
        (plSizes + uaSizes).forEach { value ->
            val options = if (value in plSizes) plSizes else uaSizes
            assertEquals(value, resolveAllowedValue(options, value.code), "code ${value.code}")
        }
    }

    @Test
    fun sizeTextResolvesToTheExactSize() {
        assertEquals("xl", resolveAllowedValue(plSizes, "XL")?.code)
        assertEquals("s", resolveAllowedValue(plSizes, "S")?.code)
        assertEquals("xxl", resolveAllowedValue(uaSizes, "XXL")?.code)
        assertEquals("xl", resolveAllowedValue(plSizes, "54-56")?.code)
        assertEquals("one_size", resolveAllowedValue(uaSizes, "one size")?.code)
    }

    @Test
    fun ambiguousTextResolvesToNothing() {
        // "42" is the EU size of XL and the UA size of XS.
        assertNull(resolveAllowedValue(uaSizes, "42"))
    }

    @Test
    fun looseTextStillResolvesWhenUnique() {
        val brands = listOf(OlxAttributeValue("nike", "Nike"), OlxAttributeValue("hugo_boss", "Hugo Boss"))
        assertEquals("hugo_boss", resolveAllowedValue(brands, "Boss")?.code)
    }
}
