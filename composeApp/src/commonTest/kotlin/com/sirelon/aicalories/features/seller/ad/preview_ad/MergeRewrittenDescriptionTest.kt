package com.sirelon.sellsnap.features.seller.ad.preview_ad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergeRewrittenDescriptionTest {

    @Test
    fun unchangedTextTakesThePolish() {
        assertEquals("Blue jacket.", mergeRewrittenDescription("blue jacket", "blue jacket", "Blue jacket."))
    }

    @Test
    fun textAddedAtTheEndIsKeptAfterThePolish() {
        assertEquals(
            "Blue jacket. Free delivery in Kyiv",
            mergeRewrittenDescription("blue jacket", "blue jacket free delivery in Kyiv", "Blue jacket."),
        )
    }

    @Test
    fun editInsideTheTextDropsThePolish() {
        assertNull(mergeRewrittenDescription("blue jacket", "red jacket", "Blue jacket."))
    }
}
