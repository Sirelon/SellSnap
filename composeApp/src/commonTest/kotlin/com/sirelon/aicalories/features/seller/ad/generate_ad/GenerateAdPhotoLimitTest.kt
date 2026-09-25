package com.sirelon.sellsnap.features.seller.ad.generate_ad

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [splitAtPhotoLimit] is the pure logic behind [GenerateAdViewModel.onFileResult]'s running-total
 * cap - see that function's KDoc for why a fresh batch alone (bounded by the picker's own
 * `maxItems`) isn't enough to keep the grid at [com.sirelon.sellsnap.features.media.ui.MAX_PHOTOS].
 */
class GenerateAdPhotoLimitTest {

    private fun photo(name: String) = PersistedDraftPhoto(
        file = KmpFile(File(name)),
        photo = DraftPhoto(id = name, path = name, displayName = name),
    )

    // KmpFile (desktop actual) wraps java.io.File without overriding equals, so assertions reuse
    // the exact instances rather than reconstructing equivalent-looking ones to compare against.
    private val photoA = photo("a.jpg")
    private val photoB = photo("b.jpg")
    private val photoC = photo("c.jpg")

    @Test
    fun `a batch that fits under the cap is kept in full`() {
        val batch = listOf(photoA, photoB)

        val (kept, dropped) = splitAtPhotoLimit(batch, existingCount = 2, max = 8)

        assertEquals(batch, kept)
        assertEquals(emptyList(), dropped)
    }

    @Test
    fun `a batch that overflows the cap keeps only the remaining slots, in order`() {
        val batch = listOf(photoA, photoB, photoC)

        val (kept, dropped) = splitAtPhotoLimit(batch, existingCount = 6, max = 8)

        assertEquals(listOf(photoA, photoB), kept)
        assertEquals(listOf(photoC), dropped)
    }

    @Test
    fun `nothing is kept once the grid is already at the cap`() {
        val batch = listOf(photoA)

        val (kept, dropped) = splitAtPhotoLimit(batch, existingCount = 8, max = 8)

        assertEquals(emptyList(), kept)
        assertEquals(batch, dropped)
    }

    @Test
    fun `an existing count above the cap does not underflow the remaining slots`() {
        val batch = listOf(photoA)

        val (kept, dropped) = splitAtPhotoLimit(batch, existingCount = 9, max = 8)

        assertEquals(emptyList(), kept)
        assertEquals(batch, dropped)
    }
}
