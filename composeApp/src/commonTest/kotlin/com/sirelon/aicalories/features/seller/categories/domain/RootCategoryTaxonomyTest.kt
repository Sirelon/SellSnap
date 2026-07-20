package com.sirelon.sellsnap.features.seller.categories.domain

import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression coverage for the per-country root category mapping introduced after discovering
 * that OLX category ids are NOT shared across countries (verified live via the olx-api-verify
 * skill, 2026-07-20). Before this mapping existed, the excluded-verticals filter and category
 * icons were keyed off raw Ukrainian ids and silently applied to every other country.
 */
class RootCategoryTaxonomyTest {

    @Test
    fun `ukraine keeps its original excluded verticals and icons`() {
        assertEquals(
            setOf(1, 1532, 6, 35, 7, 3709, 3428),
            excludedRootCategoryIds(OlxCountry.UA),
        )
        assertEquals(RootCategoryKind.ELECTRONICS, rootCategoryKind(OlxCountry.UA, 37))
        assertEquals(RootCategoryKind.FASHION, rootCategoryKind(OlxCountry.UA, 891))
        assertEquals(RootCategoryKind.HOME_GARDEN, rootCategoryKind(OlxCountry.UA, 899))
        assertEquals(RootCategoryKind.BABY, rootCategoryKind(OlxCountry.UA, 36))
        assertEquals(RootCategoryKind.SPORT_HOBBY, rootCategoryKind(OlxCountry.UA, 903))
        assertEquals(RootCategoryKind.SPARE_PARTS, rootCategoryKind(OlxCountry.UA, 3))
        assertEquals(RootCategoryKind.OTHER, rootCategoryKind(OlxCountry.UA, 3901))
    }

    @Test
    fun `poland excludes its own root ids, not ukraine's`() {
        // Poland's real ids for real estate/vehicles/jobs/animals/business differ entirely from UA's.
        // Agriculture (757) is folded into BUSINESS_SERVICES, matching UA's excluded Business&Services vertical.
        assertEquals(
            setOf(3, 5, 4, 103, 4269, 4371, 757, 1816, 3018),
            excludedRootCategoryIds(OlxCountry.PL),
        )
        // Ukraine's excluded ids mean unrelated things in Poland's tree and must not be excluded there.
        assertEquals(
            emptySet<Int>(),
            excludedRootCategoryIds(OlxCountry.PL).intersect(setOf(1, 1532, 6, 35, 7)),
        )

        assertEquals(RootCategoryKind.ELECTRONICS, rootCategoryKind(OlxCountry.PL, 99))
        assertEquals(RootCategoryKind.FASHION, rootCategoryKind(OlxCountry.PL, 87))
        assertEquals(RootCategoryKind.BABY, rootCategoryKind(OlxCountry.PL, 88))
        assertEquals(RootCategoryKind.HOME_GARDEN, rootCategoryKind(OlxCountry.PL, 628))
        assertEquals(RootCategoryKind.SPORT_HOBBY, rootCategoryKind(OlxCountry.PL, 767))
        // Poland's id 37 is unrelated to UA's Electronics root and must not resolve to anything.
        assertNull(rootCategoryKind(OlxCountry.PL, 37))
    }

    @Test
    fun `portugal maps two electronics roots and reuses the spare-parts icon`() {
        assertEquals(RootCategoryKind.ELECTRONICS, rootCategoryKind(OlxCountry.PT, 11))
        assertEquals(RootCategoryKind.ELECTRONICS, rootCategoryKind(OlxCountry.PT, 25))
        // Equipment&Tools (4918) and Agriculture (4800) fold into BUSINESS_SERVICES, matching UA's policy.
        assertEquals(
            setOf(16, 362, 190, 10, 191, 4918, 4800),
            excludedRootCategoryIds(OlxCountry.PT),
        )
        // Peças e acessórios is the same concept as UA's kept Spare Parts root - reuses its icon, not a new one.
        assertEquals(RootCategoryKind.SPARE_PARTS, rootCategoryKind(OlxCountry.PT, 5478))
    }

    @Test
    fun `romania and bulgaria resolve their own confident roots and reuse the spare-parts icon`() {
        // Professional equipment (2687) and Agro/industry (1183) fold into BUSINESS_SERVICES.
        assertEquals(
            setOf(3, 5, 4, 103, 619, 2687, 1183, 2666, 2667),
            excludedRootCategoryIds(OlxCountry.RO),
        )
        assertEquals(RootCategoryKind.SPORT_HOBBY, rootCategoryKind(OlxCountry.RO, 97))
        assertEquals(RootCategoryKind.SPARE_PARTS, rootCategoryKind(OlxCountry.RO, 1639))

        // Business equipment/machinery (1012) folds into BUSINESS_SERVICES.
        assertEquals(
            setOf(368, 360, 606, 339, 555, 1012, 545),
            excludedRootCategoryIds(OlxCountry.BG),
        )
        assertEquals(RootCategoryKind.ELECTRONICS, rootCategoryKind(OlxCountry.BG, 632))
        assertEquals(RootCategoryKind.SPARE_PARTS, rootCategoryKind(OlxCountry.BG, 1625))
    }

    @Test
    fun `unmapped country-specific verticals resolve to no kind, not a guess`() {
        // Poland's Health&Beauty/Antiques roots have no confident UA equivalent - stay visible, no icon.
        assertNull(rootCategoryKind(OlxCountry.PL, 3647)) // Zdrowie i Uroda (Health & Beauty)
        assertNull(rootCategoryKind(OlxCountry.PL, 4042)) // Antyki i Kolekcje (Antiques)
    }

    @Test
    fun `country with no live categories support falls back to no exclusions and no icons`() {
        assertEquals(emptySet<Int>(), excludedRootCategoryIds(OlxCountry.KZ))
        assertNull(rootCategoryKind(OlxCountry.KZ, 37))
    }
}
