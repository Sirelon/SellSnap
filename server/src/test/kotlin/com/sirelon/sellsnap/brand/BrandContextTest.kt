package com.sirelon.sellsnap.brand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reads the file the `generateBrandContext` Gradle task wrote into the test classpath. */
class BrandContextTest {
    private val context = BrandContext.load()
    private val rawText = BrandContext::class.java.getResourceAsStream("/brand-context.json")!!.use { it.readBytes().decodeToString() }

    @Test
    fun coversExactlyEnglishUkrainianAndPolish() {
        val expected = setOf("en", "uk", "pl")
        assertEquals(expected, context.voiceSamples.keys)
        assertEquals(expected, context.features.keys)
        assertEquals(expected, context.independence.keys)
        assertEquals(expected, BRAND_LANGUAGES.toSet())
    }

    @Test
    fun noValueHoldsLettersThatOnlyRussianUses() {
        // The letters that Russian has and Ukrainian and Polish do not, as code points: yery, e, hard sign, yo, both cases.
        val onlyRussian = Regex("[\\u044B\\u044D\\u044A\\u0451\\u042B\\u042D\\u042A\\u0401]")
        val values = BRAND_LANGUAGES.flatMap { language ->
            context.voiceSamplesIn(language).map { it.text } +
                context.featuresIn(language).map { it.text } +
                context.independence.getValue(language)
        }
        assertTrue(values.isNotEmpty())
        values.forEach { assertFalse(onlyRussian.containsMatchIn(it), it) }
    }

    @Test
    fun everyLanguageHasTheSameVoiceKeysAndFeatureIds() {
        val keys = context.voiceSamplesIn("en").map { it.key }
        assertEquals(20, keys.size)
        assertEquals(keys, context.voiceSamplesIn("uk").map { it.key })
        assertEquals(keys, context.voiceSamplesIn("pl").map { it.key })

        val ids = context.featuresIn("en").map { it.id }
        assertEquals(ids, context.featuresIn("uk").map { it.id })
        assertEquals(ids, context.featuresIn("pl").map { it.id })
    }

    @Test
    fun voiceSamplesAreTextFromTheStringsFiles() {
        val welcome = context.voiceSamplesIn("en").single { it.key == "welcome_to_sellsnap" }
        assertEquals("Hey, welcome to SellSnap 👋", welcome.text)
        BRAND_LANGUAGES.forEach { language ->
            context.voiceSamplesIn(language).forEach { assertTrue(it.text.isNotBlank(), "$language ${it.key}") }
        }
        // Android escapes are resolved: the apostrophe in "you're" is plain.
        assertFalse(context.voiceSamplesIn("en").any { "\\" in it.text })
    }

    @Test
    fun featuresAreTheWhySellSnapBulletsOfEveryLanguage() {
        BRAND_LANGUAGES.forEach { language ->
            assertTrue(context.featuresIn(language).isNotEmpty(), language)
        }
        assertEquals("No blank-page struggle — the AI writes the whole listing", context.featuresIn("en").first().text)
        assertEquals("f1", context.featuresIn("en").first().id)
        assertTrue(context.featuresIn("pl").any { "Jasny i ciemny motyw" == it.text })
    }

    @Test
    fun theAnalyticsParagraphIsAbsent() {
        val markers = listOf("Analytics are off", "Аналітика вимкнена", "Analityka jest", "YOUR DATA", "delete your local data")
        markers.forEach { assertFalse(it in rawText, it) }
        BRAND_LANGUAGES.forEach { language ->
            context.featuresIn(language).forEach {
                assertFalse(it.text.contains("nalytic", ignoreCase = true) || it.text.contains("налітик", ignoreCase = true), it.text)
            }
        }
    }

    @Test
    fun independenceIsTheFirstSentenceOnly() {
        assertEquals("SellSnap is an independent app and is not affiliated with or endorsed by OLX.", context.independence.getValue("en"))
        assertEquals("SellSnap — незалежний застосунок, не афілійований з OLX.", context.independence.getValue("uk"))
        assertEquals("SellSnap to niezależna aplikacja, niepowiązana z OLX.", context.independence.getValue("pl"))
    }
}
