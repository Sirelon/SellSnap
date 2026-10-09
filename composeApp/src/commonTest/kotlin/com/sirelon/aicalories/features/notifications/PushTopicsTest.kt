package com.sirelon.sellsnap.features.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class PushTopicsTest {

    @Test
    fun `a supported language gets its own topic`() {
        assertEquals(listOf("all", "all-uk"), pushTopicsFor("uk"))
        assertEquals(listOf("all", "all-kk"), pushTopicsFor("kk"))
    }

    @Test
    fun `russian maps to ukrainian`() {
        assertEquals(listOf("all", "all-uk"), pushTopicsFor("ru"))
    }

    @Test
    fun `an unsupported language falls back to english`() {
        assertEquals(listOf("all", "all-en"), pushTopicsFor("de"))
    }

    @Test
    fun `an unknown language falls back to english`() {
        assertEquals(listOf("all", "all-en"), pushTopicsFor(null))
    }

    @Test
    fun `the language code is case insensitive`() {
        assertEquals(listOf("all", "all-pl"), pushTopicsFor("PL"))
        assertEquals(listOf("all", "all-uk"), pushTopicsFor("RU"))
    }
}
