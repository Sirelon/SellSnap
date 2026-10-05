package com.sirelon.sellsnap.features.seller.openai

import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the v4 guardrails into the instructions text. Whether the model honours them can only be
 * judged against real generations, not offline.
 */
class AdGenerationInstructionsTest {

    @Test
    fun promptVersionIsV4() {
        assertEquals("v4", AD_GENERATION_PROMPT_VERSION)
    }

    @Test
    fun instructionsCarryTheV4Guardrails() {
        val instructions = adGenerationInstructions(OlxCountry.PL)
        val terms = listOf(
            // Claims a photo cannot support.
            "comfort", "warmth or insulation", "fit (how it fits", "durability", "smell or freshness", "ideal for",
            // The opener rule.
            "The first sentence states what the item is", "Sprzedaję",
            // No placeholder in the title.
            "Never put a placeholder for an unknown value in the title", "r. brak",
            // Length target, and when shorter is right.
            "3 to 6 short sentences", "truly show little",
            // Earlier guardrails stay.
            "Do not invent brand, size, material, defects, or condition.",
            "Do not infer the season of clothing",
        )

        terms.forEach { term ->
            assertTrue(term in instructions, "Missing from the instructions: $term")
        }
    }
}
