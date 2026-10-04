package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketPriceRoundingTest {

    private fun ad(min: Float, suggested: Float, max: Float) = Advertisement(
        title = "t",
        description = "d",
        images = emptyList(),
        suggestedPrice = suggested,
        minPrice = min,
        maxPrice = max,
    )

    private fun assertRounded(
        country: OlxCountry,
        raw: Triple<Float, Float, Float>,
        expected: Triple<Float, Float, Float>,
    ) {
        val rounded = ad(raw.first, raw.second, raw.third).roundedToMarketSteps(country)
        assertEquals(expected, Triple(rounded.minPrice, rounded.suggestedPrice, rounded.maxPrice))
    }

    @Test
    fun plnRoundsByFiveBelowOneHundred() =
        assertRounded(OlxCountry.PL, Triple(62f, 78f, 93f), Triple(60f, 80f, 95f))

    @Test
    fun plnRoundsByTenFromOneHundred() =
        assertRounded(OlxCountry.PL, Triple(212f, 347f, 488f), Triple(210f, 350f, 490f))

    @Test
    fun uahRoundsByFiftyBelowOneThousand() =
        assertRounded(OlxCountry.UA, Triple(420f, 680f, 960f), Triple(400f, 700f, 1_000f))

    @Test
    fun uahRoundsByOneHundredFromOneThousand() =
        assertRounded(OlxCountry.UA, Triple(1_240f, 1_870f, 2_410f), Triple(1_200f, 1_900f, 2_500f))

    @Test
    fun ronRoundsByFiveBelowOneHundred() =
        assertRounded(OlxCountry.RO, Triple(41f, 57f, 72f), Triple(40f, 55f, 75f))

    @Test
    fun ronRoundsByTenFromOneHundred() =
        assertRounded(OlxCountry.RO, Triple(133f, 186f, 241f), Triple(130f, 190f, 250f))

    @Test
    fun eurRoundsByOneBelowFifty() =
        assertRounded(OlxCountry.PT, Triple(12.6f, 18.5f, 27.2f), Triple(12f, 19f, 28f))

    @Test
    fun eurRoundsByFiveFromFifty() =
        assertRounded(OlxCountry.PT, Triple(52f, 87f, 113f), Triple(50f, 85f, 115f))

    @Test
    fun bulgariaPricesInEuroAndTakesTheEuroSteps() {
        assertRounded(OlxCountry.BG, Triple(12.6f, 18.5f, 27.2f), Triple(12f, 19f, 28f))
        assertRounded(OlxCountry.BG, Triple(52f, 87f, 113f), Triple(50f, 85f, 115f))
    }

    @Test
    fun bandIsPickedPerPriceAcrossTheBoundary() =
        assertRounded(OlxCountry.PL, Triple(96f, 103f, 148f), Triple(95f, 100f, 150f))

    @Test
    fun suggestedPriceRoundsHalfUp() =
        assertRounded(OlxCountry.UA, Triple(100f, 125f, 200f), Triple(100f, 150f, 200f))

    @Test
    fun alreadyRoundPricesStayPut() =
        assertRounded(OlxCountry.PL, Triple(100f, 350f, 500f), Triple(100f, 350f, 500f))

    @Test
    fun aPositivePriceNeverRoundsToZero() =
        assertRounded(OlxCountry.PL, Triple(1f, 2f, 3f), Triple(5f, 5f, 5f))

    @Test
    fun currencyWithoutAStepIsLeftAsWritten() =
        assertRounded(OlxCountry.KZ, Triple(4_210f, 5_330f, 6_470f), Triple(4_210f, 5_330f, 6_470f))

    @Test
    fun orderingHoldsAndTheRangeOnlyWidens() {
        val random = Random(seed = 115)
        OlxCountry.all.forEach { country ->
            repeat(2_000) {
                val (min, suggested, max) = List(3) { random.nextInt(1, 5_000).toFloat() }.sorted()
                val rounded = ad(min, suggested, max).roundedToMarketSteps(country)
                val context = "${country.code} ($min, $suggested, $max) -> " +
                    "(${rounded.minPrice}, ${rounded.suggestedPrice}, ${rounded.maxPrice})"

                assertTrue(rounded.minPrice <= rounded.suggestedPrice, context)
                assertTrue(rounded.suggestedPrice <= rounded.maxPrice, context)
                assertTrue(rounded.maxPrice >= max, context)
                // Down, unless that would reach zero and the minimum is lifted to one step.
                assertTrue(
                    rounded.minPrice <= min || rounded.minPrice == country.priceRoundingStep(min).toFloat(),
                    context,
                )
            }
        }
    }
}
