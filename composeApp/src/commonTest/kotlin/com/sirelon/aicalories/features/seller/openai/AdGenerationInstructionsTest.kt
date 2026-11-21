package com.sirelon.sellsnap.features.seller.openai

import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the guardrails into the instructions text. Whether the model honours them can only be
 * judged against real generations, not offline.
 */
class AdGenerationInstructionsTest {

    @Test
    fun promptVersionIsV7() {
        assertEquals("v7", AD_GENERATION_PROMPT_VERSION)
    }

    @Test
    fun instructionsCarryTheGuardrails() {
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
            // The text describes the item, never the photos of it.
            "never the pictures of it", "na zdjęciu",
            // Every seller fact reaches the listing.
            "Every fact in the seller note must reach the listing",
            // A set, a lot, or one photo is never refused as different items.
            "Things sold together are one listing", "A single photo is never `different_items`",
            "a note that says the items are sold together",
            // Earlier guardrails stay.
            "Do not invent brand, size, material, defects, or condition.",
            "Do not infer the season of clothing",
        )

        terms.forEach { term ->
            assertTrue(term in instructions, "Missing from the instructions: $term")
        }
    }
}
