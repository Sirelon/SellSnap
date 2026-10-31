package com.sirelon.sellsnap.features.media

import kotlin.test.Test
import kotlin.test.assertEquals

class AppendDictatedTextTest {

    @Test
    fun emptyFieldTakesThePhraseCapitalized() {
        assertEquals("Blue jacket", appendDictatedText("", "blue jacket"))
    }

    @Test
    fun phraseAfterASentenceStartsANewOne() {
        assertEquals("Blue jacket. Worn twice", appendDictatedText("Blue jacket.", "worn twice"))
    }

    @Test
    fun phraseMidSentenceStaysLowerCase() {
        assertEquals("Blue jacket with a hood", appendDictatedText("Blue jacket", "with a hood"))
    }

    @Test
    fun trailingWhitespaceIsNotDoubled() {
        assertEquals("Blue jacket.\nWorn twice", appendDictatedText("Blue jacket.\n", "worn twice"))
    }
}
