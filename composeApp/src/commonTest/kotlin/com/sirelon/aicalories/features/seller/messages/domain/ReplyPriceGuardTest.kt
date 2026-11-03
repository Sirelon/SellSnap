package com.sirelon.sellsnap.features.seller.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyPriceGuardTest {

    private fun reply(text: String, price: Long? = null) = ReplySuggestion(text = text, needsReview = false, price = price)

    @Test
    fun `a price field below the floor is dropped`() {
        val result = ReplyPriceGuard.apply(listOf(reply("Deal.", price = 1999), reply("Deal.", price = 2000)), floor = 2000)

        assertEquals(listOf(2000L), result.kept.map { it.price })
        assertEquals(1, result.dropped)
    }

    @Test
    fun `numbers in the text between half the floor and the floor are dropped, whatever the separator`() {
        val texts = listOf("I can do 1 500.", "I can do 1.500.", "I can do 1,500.", "I can do 1500.", "I can do 1 500.")

        val result = ReplyPriceGuard.apply(texts.map { reply(it) }, floor = 2000)

        assertEquals(emptyList(), result.kept)
        assertEquals(5, result.dropped)
    }

    @Test
    fun `small numbers, years and prices at or above the floor are kept`() {
        val texts = listOf("Size 42, bought in 2019.", "It fits 3 people.", "I can do 2 000.", "Asking 2500.")

        val result = ReplyPriceGuard.apply(texts.map { reply(it) }, floor = 2000)

        assertEquals(texts, result.kept.map { it.text })
        assertEquals(0, result.dropped)
    }

    @Test
    fun `without a floor nothing is dropped`() {
        val replies = listOf(reply("I can do 1 500.", price = 1500), reply("Fine, 10."))

        val result = ReplyPriceGuard.apply(replies, floor = null)

        assertEquals(replies, result.kept)
        assertEquals(0, result.dropped)
    }
}
