package com.sirelon.sellsnap.features.seller.openai

import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.ReplySuggestion
import com.sirelon.sellsnap.features.seller.my_ads.model.ListingAttribute
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIReplySuggestionsResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplySuggestionsPromptTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `with a floor the instructions bound the price by it in the market's currency and language`() {
        val instructions = replySuggestionsInstructions(
            country = OlxCountry.PL,
            currencyCode = "PLN",
            askingPrice = 300,
            priceFloor = 250,
        )

        assertTrue(instructions.contains("OLX Poland"))
        assertTrue(instructions.contains("Write in Polish."))
        assertTrue(instructions.contains("If the buyer writes in another language, write in the buyer's language."))
        assertTrue(instructions.contains("The asking price is 300 PLN."))
        assertTrue(instructions.contains("lowest acceptable price is 250 PLN"))
        assertTrue(instructions.contains("Never offer, accept or name a price below 250 PLN."))
        assertTrue(instructions.contains("at least 250 PLN"))
        assertTrue(instructions.contains("never mention it"))
        assertTrue(instructions.contains("needs_review"))
        assertTrue(instructions.contains("""{"replies":[{"text":"string","needs_review":false,"price":null}]}"""))
    }

    @Test
    fun `without a floor the instructions hold the asking price`() {
        val instructions = replySuggestionsInstructions(
            country = OlxCountry.UA,
            currencyCode = "UAH",
            askingPrice = 1500,
            priceFloor = null,
        )

        assertTrue(instructions.contains("Write in Ukrainian."))
        assertTrue(instructions.contains("The asking price is 1500 UAH."))
        assertTrue(instructions.contains("Never offer, accept or name a price below the asking price."))
        assertFalse(instructions.contains("lowest acceptable price"))
    }

    @Test
    fun `the input has the listing and the newest twenty messages, photos as a placeholder`() {
        val messages = (1..25).map { message(it.toLong(), isSent = it % 2 == 0, text = "Line $it") } +
            message(26, isSent = false, text = "", photo = true) +
            message(27, isSent = false, text = "Two\nlines")

        val input = replySuggestionsInput(
            title = "Placeholder lamp",
            description = "Placeholder description",
            attributes = listOf(ListingAttribute("state", listOf("used"))),
            messages = messages,
        )

        assertTrue(input.contains("Listing title: Placeholder lamp"))
        assertTrue(input.contains("Placeholder description"))
        assertTrue(input.contains("- state: used"))
        assertFalse(input.contains("Line 7\n"))
        assertTrue(input.contains("Buyer: Line 9"))
        assertTrue(input.contains("Seller: Line 10"))
        assertTrue(input.contains("Buyer: [photo]"))
        assertTrue(input.contains("Buyer: Two lines"))
    }

    @Test
    fun `without listing text the input is the title and the conversation`() {
        val input = replySuggestionsInput(
            title = "Placeholder row title",
            description = null,
            attributes = emptyList(),
            messages = listOf(message(1, isSent = false, text = "Hi")),
        )

        assertFalse(input.contains("Listing description"))
        assertFalse(input.contains("Listing attributes"))
        assertTrue(input.contains("Listing title: Placeholder row title"))
    }

    @Test
    fun `the answer decodes with fields missing or extra`() {
        val response = json.decodeFromString<OpenAIReplySuggestionsResponse>(
            """{"replies":[
                {"text":" Yes, available. ","needs_review":false,"price":null,"tone":"friendly"},
                {"text":"I can do 2000.","price":2000.0},
                {"needs_review":false},
                {"text":"   "},
                {"text":"Yes, available.","needs_review":false}
            ],"extra":1}""",
        )

        assertEquals(
            listOf(
                ReplySuggestion(text = "Yes, available.", needsReview = false, price = null),
                // No needs_review: not vouched for, so it is never sent unseen.
                ReplySuggestion(text = "I can do 2000.", needsReview = true, price = 2000),
            ),
            response.toReplySuggestions(),
        )
    }

    @Test
    fun `an answer without replies decodes to none`() {
        assertEquals(emptyList(), json.decodeFromString<OpenAIReplySuggestionsResponse>("{}").toReplySuggestions())
    }

    private fun message(id: Long, isSent: Boolean, text: String, photo: Boolean = false) = OlxMessage(
        id = id,
        createdAt = "2026-01-02 10:00:00",
        isSent = isSent,
        text = text,
        attachmentUrls = if (photo) listOf("https://example.invalid/p.jpg") else emptyList(),
    )
}
