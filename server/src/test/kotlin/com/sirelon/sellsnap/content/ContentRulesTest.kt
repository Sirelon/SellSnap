package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.content.ContentRules.Violation
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentRulesTest {
    private val linkUrl = "https://agent.example.run.app/go/stt0001"

    private fun check(
        text: String = "Hello",
        kind: PostKind = PostKind.Text,
        pillar: Pillar = Pillar.Tip,
        source: PostSource = PostSource.Seed,
        options: List<String> = emptyList(),
        language: String = "uk",
        published: Int = 10,
        reply: String? = null,
    ): Set<Violation> = ContentRules.check(
        ContentRules.Candidate(text, kind, pillar, source, options, language, published, linkUrl, reply),
    )

    private fun poll(options: List<String>) = check(kind = PostKind.Poll, pillar = Pillar.Poll, options = options)

    // Individual letters of the russian alphabet that Ukrainian and Polish lack.
    private val russianLetters = listOf('ы', 'э', 'ъ', 'ё', 'Ы', 'Э', 'Ъ', 'Ё')

    // Replies

    @Test
    fun aReplyWithALinkCountsTheLinkAndTheIndependenceLineTowardTheLimit() {
        val line = ContentRules.threadsLength(ContentRules.INDEPENDENCE_LINE.getValue("uk"))
        val room = ContentRules.MAX_LENGTH - linkUrl.length - 2 - line

        assertEquals(emptySet(), check(reply = "{link}" + "a".repeat(room), source = PostSource.Owner))
        assertEquals(setOf(Violation.ReplyTooLong), check(reply = "{link}" + "a".repeat(room + 1), source = PostSource.Owner))
        // Without the placeholder no line is added, so the same length is fine.
        assertEquals(emptySet(), check(reply = "a".repeat(ContentRules.MAX_LENGTH)))
        assertEquals(setOf(Violation.ReplyTooLong), check(reply = "a".repeat(ContentRules.MAX_LENGTH + 1)))
    }

    @Test
    fun aRawUrlInAReplyIsRejectedAndLinkIsWrittenAsAPlaceholder() {
        assertEquals(setOf(Violation.ReplyRawUrl), check(reply = "Get it at https://example.com"))
        assertEquals(setOf(Violation.ReplyRawUrl), check(reply = "Get it at www.example.com"))
        assertEquals(emptySet(), check(reply = "Get it: {link}", source = PostSource.Owner))
    }

    @Test
    fun aReplyMayCarryALinkFromTheAccountsVeryFirstPost() {
        assertEquals(emptySet(), check(published = 0, reply = "Get it: {link}", source = PostSource.Owner))
        assertEquals(emptySet(), check(published = 4, reply = "Get it: {link}", pillar = Pillar.Update, source = PostSource.Seed))
        // Guardrail 12 still holds for the text.
        assertEquals(setOf(Violation.LinkTooEarly), check(text = "Get it: {link}", published = 0, source = PostSource.Owner))
    }

    @Test
    fun aReplyLinkFollowsThePillarRuleExceptForOwnerPosts() {
        assertEquals(setOf(Violation.ReplyLinkNotAllowed), check(reply = "{link}", pillar = Pillar.Tip, source = PostSource.Seed))
        assertEquals(emptySet(), check(reply = "{link}", pillar = Pillar.Update, source = PostSource.Seed))
        assertEquals(emptySet(), check(reply = "{link}", pillar = Pillar.Tip, source = PostSource.Owner))
    }

    @Test
    fun aReplyIsCheckedForEmptyAndRussianOnlyLetters() {
        assertEquals(setOf(Violation.ReplyEmpty), check(reply = "  "))
        assertEquals(setOf(Violation.ReplyRussianLetter), check(reply = russianLetters.first().toString()))
        assertEquals(emptySet(), check(reply = null))
    }

    @Test
    fun plainTextCountsOnePerCodePoint() {
        assertEquals(3, ContentRules.threadsLength("abc"))
        assertEquals(6, ContentRules.threadsLength("Привіт"))
        assertEquals(0, ContentRules.threadsLength(""))
    }

    @Test
    fun emojiCountAsTheirUtf8Bytes() {
        assertEquals(4, ContentRules.threadsLength("😀"))
        assertEquals(6, ContentRules.threadsLength("a😀b"))
        // U+2764 is 3 bytes and VS16 is 3 bytes.
        assertEquals(6, ContentRules.threadsLength("❤️"))
        // Thumbs up plus skin-tone modifier: 4 + 4.
        assertEquals(8, ContentRules.threadsLength("👍🏽"))
        // Two regional indicators: 4 + 4.
        assertEquals(8, ContentRules.threadsLength("🇺🇦"))
        // Three people joined by two ZWJ: 3 * 4 + 2 * 3.
        assertEquals(18, ContentRules.threadsLength("👨‍👩‍👧"))
    }

    @Test
    fun capIs490AfterByteCounting() {
        assertEquals(emptySet(), check(text = "a".repeat(490)))
        assertEquals(setOf(Violation.TooLong), check(text = "a".repeat(491)))
        assertEquals(emptySet(), check(text = "😀".repeat(120) + "a".repeat(10)))
        assertEquals(setOf(Violation.TooLong), check(text = "😀".repeat(120) + "a".repeat(11)))
    }

    @Test
    fun lengthIsMeasuredOnTheRenderedLinkedPost() {
        val overhead = ContentRules.threadsLength(ContentRules.render(" {link}", linkUrl, "en"))
        val fits = "a".repeat(ContentRules.MAX_LENGTH - overhead) + " {link}"
        assertEquals(emptySet(), check(text = fits, pillar = Pillar.Update, language = "en"))
        assertEquals(
            setOf(Violation.TooLong),
            check(text = "a$fits", pillar = Pillar.Update, language = "en"),
        )
    }

    @Test
    fun pollNeedsTwoToFourOptions() {
        assertEquals(setOf(Violation.PollOptionCount), poll(listOf("a")))
        assertEquals(emptySet(), poll(listOf("a", "b")))
        assertEquals(emptySet(), poll(listOf("a", "b", "c")))
        assertEquals(emptySet(), poll(listOf("a", "b", "c", "d")))
        assertEquals(setOf(Violation.PollOptionCount), poll(listOf("a", "b", "c", "d", "e")))
        assertEquals(setOf(Violation.PollOptionCount), poll(emptyList()))
    }

    @Test
    fun pollOptionsAreOneToTwentyFiveCharacters() {
        assertEquals(emptySet(), poll(listOf("a".repeat(25), "b")))
        assertEquals(setOf(Violation.PollOptionLength), poll(listOf("a".repeat(26), "b")))
        assertEquals(setOf(Violation.PollOptionLength), poll(listOf("", "b")))
        assertEquals(setOf(Violation.PollOptionLength), poll(listOf("  ", "b")))
        // Characters, not bytes: 25 emoji pass.
        assertEquals(emptySet(), poll(listOf("😀".repeat(25), "b")))
    }

    @Test
    fun pollOptionsAreIgnoredOnOtherKinds() {
        assertEquals(emptySet(), check(options = listOf("a".repeat(40))))
    }

    @Test
    fun blankTextIsRejectedExceptOnImages() {
        assertEquals(setOf(Violation.Empty), check(text = "  "))
        assertEquals(setOf(Violation.Empty), check(text = "", kind = PostKind.Poll, options = listOf("a", "b")))
        assertEquals(emptySet(), check(text = "", kind = PostKind.Image))
    }

    @Test
    fun russianOnlyLettersAreRejectedInTextAndPollOptions() {
        for (letter in russianLetters) {
            assertEquals(setOf(Violation.RussianLetter), check(text = "ab${letter}cd"), "text with $letter")
            assertEquals(setOf(Violation.RussianLetter), poll(listOf("a${letter}", "b")), "option with $letter")
        }
    }

    @Test
    fun ukrainianAndPolishLettersPass() {
        assertEquals(emptySet(), check(text = "Їжак, єнот, ґанок і Київ"))
        assertEquals(emptySet(), check(text = "Zażółć gęślą jaźń", language = "pl"))
    }

    @Test
    fun linkIsAllowedOnUpdatePillarAfterFivePosts() {
        assertEquals(emptySet(), check(text = "News {link}", pillar = Pillar.Update, published = 5))
        assertEquals(setOf(Violation.LinkNotAllowed), check(text = "News {link}", pillar = Pillar.Tip, published = 5))
        assertEquals(setOf(Violation.LinkNotAllowed), check(text = "News {link}", pillar = Pillar.Demo, published = 5))
        assertEquals(setOf(Violation.LinkNotAllowed), check(text = "News {link}", pillar = Pillar.Reveal, published = 5))
        assertEquals(setOf(Pillar.Update), ContentRules.LINK_PILLARS)
    }

    @Test
    fun ownerPostsMayCarryTheLink() {
        val owner = PostSource.Owner
        assertEquals(emptySet(), check(text = "Hi {link}", source = owner, pillar = Pillar.Tip, published = 5))
    }

    @Test
    fun noLinkInTheFirstFivePosts() {
        for (published in 0..4) {
            assertEquals(
                setOf(Violation.LinkTooEarly),
                check(text = "News {link}", pillar = Pillar.Update, published = published),
                "published=$published",
            )
            assertEquals(
                setOf(Violation.LinkTooEarly),
                check(text = "Hi {link}", source = PostSource.Owner, published = published),
                "owner, published=$published",
            )
        }
        assertEquals(emptySet(), check(text = "News {link}", pillar = Pillar.Update, published = 5))
    }

    @Test
    fun rawUrlsAreRejectedEverywhere() {
        assertEquals(setOf(Violation.RawUrl), check(text = "see https://example.com now"))
        assertEquals(setOf(Violation.RawUrl), check(text = "see HTTP://example.com"))
        assertEquals(setOf(Violation.RawUrl), check(text = "see www.example.com"))
        assertEquals(
            setOf(Violation.RawUrl),
            check(text = "News https://example.com {link}", pillar = Pillar.Update, published = 5),
        )
        assertEquals(
            setOf(Violation.RawUrl),
            check(text = "Hi https://example.com", source = PostSource.Owner),
        )
        assertEquals(setOf(Violation.RawUrl), poll(listOf("https://a.io", "b")))
    }

    @Test
    fun placeholderInPollOptionIsRejected() {
        assertEquals(setOf(Violation.LinkNotAllowed), poll(listOf("{link}", "b")))
    }

    @Test
    fun renderReplacesPlaceholderAndAppendsIndependenceLine() {
        val rendered = ContentRules.render("Try it {link}", linkUrl, "uk")
        assertEquals("Try it $linkUrl\n\n${ContentRules.INDEPENDENCE_LINE.getValue("uk")}", rendered)
        assertEquals("Plain tip", ContentRules.render("Plain tip", linkUrl, "uk"))
    }

    @Test
    fun independenceLineFollowsLanguageAndFallsBackToEnglish() {
        val english = ContentRules.INDEPENDENCE_LINE.getValue("en")
        assertTrue(ContentRules.render("{link}", linkUrl, "pl").endsWith(ContentRules.INDEPENDENCE_LINE.getValue("pl")))
        assertTrue(ContentRules.render("{link}", linkUrl, "de").endsWith(english))
        assertEquals(setOf("en", "uk", "pl"), ContentRules.INDEPENDENCE_LINE.keys)
    }

    @Test
    fun independenceLinesPassTheRussianLetterGuard() {
        for (line in ContentRules.INDEPENDENCE_LINE.values) {
            assertTrue(line.none { it in russianLetters }, line)
        }
    }

    @Test
    fun reportsEveryViolationAtOnce() {
        val violations = check(text = "a".repeat(500) + "ы https://x.io", pillar = Pillar.Tip)
        assertEquals(setOf(Violation.TooLong, Violation.RussianLetter, Violation.RawUrl), violations)
    }
}
