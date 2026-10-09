package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostCodeTest {
    @Test
    fun formatsPrefixPillarLetterAndPaddedSequence() {
        assertEquals("stt0001", PostCode.format("st", Pillar.Tip, 1))
        assertEquals("pld0042", PostCode.format("pl", Pillar.Demo, 42))
        assertEquals("ukp9999", PostCode.format("uk", Pillar.Poll, 9999))
    }

    @Test
    fun sequenceBeyondFourDigitsStaysValid() {
        assertEquals("pld12345", PostCode.format("pl", Pillar.Demo, 12345))
    }

    @Test
    fun everyPillarHasADistinctLetter() {
        assertEquals(Pillar.entries.size, Pillar.entries.map { it.letter }.toSet().size)
    }

    @Test
    fun rejectsPrefixesThatBreakTheShape() {
        assertFailsWith<IllegalArgumentException> { PostCode.format("ST", Pillar.Tip, 1) }
        assertFailsWith<IllegalArgumentException> { PostCode.format("s-t", Pillar.Tip, 1) }
        assertFailsWith<IllegalArgumentException> { PostCode.format("s/t", Pillar.Tip, 1) }
    }

    @Test
    fun rejectsCodesOverThirtyCharacters() {
        assertEquals(30, PostCode.format("a".repeat(25), Pillar.Tip, 1).length)
        assertFailsWith<IllegalArgumentException> { PostCode.format("a".repeat(26), Pillar.Tip, 1) }
    }

    @Test
    fun rejectsNegativeSequence() {
        assertFailsWith<IllegalArgumentException> { PostCode.format("st", Pillar.Tip, -1) }
    }

    @Test
    fun isValidAcceptsOnlyLowercaseAlphanumericUpToThirty() {
        assertTrue(PostCode.isValid("stt0001"))
        assertTrue(PostCode.isValid("a".repeat(30)))
        assertFalse(PostCode.isValid(""))
        assertFalse(PostCode.isValid("a".repeat(31)))
        assertFalse(PostCode.isValid("STT0001"))
        assertFalse(PostCode.isValid("st t"))
        assertFalse(PostCode.isValid("st-t0001"))
        assertFalse(PostCode.isValid("../etc"))
    }
}
