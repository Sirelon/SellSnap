package com.sirelon.sellsnap.features.seller.messages.domain

import androidx.compose.runtime.Immutable

/**
 * One AI-suggested reply to a buyer (SIR-96). [needsReview] is the model's flag for a reply that
 * proposes or agrees to delivery, a payment method, a meeting time or place, or shares contact
 * details: such a reply is never sent with one tap. [price] is the price the reply names, in whole
 * units, or null when it names none.
 */
@Immutable
data class ReplySuggestion(
    val text: String,
    val needsReview: Boolean,
    val price: Long?,
)

/**
 * The code half of the price-floor rule the prompt also states: no suggestion may offer less than
 * the seller's private lowest price. The model is told the floor; this drops what slips through.
 */
internal object ReplyPriceGuard {

    /**
     * A run of digits, or digit groups of three joined by a space (also a no-break space), a dot
     * or a comma: `1 500`, `1.500` and `1,500` all read as 1500.
     */
    private val NumberPattern = Regex("""\d{1,3}(?:[   .,]\d{3})+|\d+""")

    /** What is left after the guard, and how many suggestions it dropped. */
    data class Result(val kept: List<ReplySuggestion>, val dropped: Int)

    /**
     * Drops a suggestion whose [ReplySuggestion.price] is below [floor], or whose text holds a
     * whole number in `[floor / 2, floor)` - a price under the floor the model did not report.
     * Smaller numbers are left alone: a size, a year or a count is not a price. Without a floor
     * nothing is dropped.
     *
     * A number the buyer wrote ([buyerText]) may be quoted when the reply also names a price at or
     * above the floor: "I can't do 200, 400 is my price" refuses the offer and counters. A reply
     * that repeats the buyer's number and names nothing higher may be accepting it, so it is still
     * dropped. The SIR-96 bench showed most lowball answers quote the offer this way.
     */
    fun apply(suggestions: List<ReplySuggestion>, floor: Long?, buyerText: String = ""): Result {
        if (floor == null || floor <= 0) return Result(suggestions, dropped = 0)
        val buyerNumbers = numbersIn(buyerText).toSet()
        val kept = suggestions.filter { suggestion ->
            val pricedBelow = suggestion.price != null && suggestion.price < floor
            val numbers = numbersIn(suggestion.text)
            val counters = numbers.any { it >= floor }
            val below = numbers.filter { it in (floor / 2) until floor }
                .filterNot { counters && it in buyerNumbers }
            !pricedBelow && below.isEmpty()
        }
        return Result(kept, dropped = suggestions.size - kept.size)
    }

    internal fun numbersIn(text: String): List<Long> =
        NumberPattern.findAll(text).mapNotNull { match ->
            match.value.filter(Char::isDigit).toLongOrNull()
        }.toList()
}
