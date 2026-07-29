package com.sirelon.sellsnap.features.seller.categories.domain

import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry

/**
 * OLX root category ids are NOT shared across countries — each country runs an independent
 * category tree with its own numbering (verified live via the olx-api-verify skill, 2026-07-20:
 * e.g. "Electronics" is root id 37 in Ukraine, 99 in Poland/Romania, 632 in Bulgaria, split
 * across ids 11+25 in Portugal). Any logic that branches on a raw category id must go through
 * this per-country lookup instead of hardcoding one country's ids.
 *
 * Only roots with a confident cross-country match are mapped. Country-specific verticals with
 * no clear equivalent elsewhere stay unmapped, so they remain visible with no custom icon.
 */
internal enum class RootCategoryKind {
    // Service/vertical marketplaces excluded from this app's consumer-goods listing flow.
    REAL_ESTATE,
    JOBS,
    VEHICLES,
    BUSINESS_SERVICES,
    ANIMALS,
    DAILY_RENTAL,
    RENTAL_LEASE,

    // Kept, with a custom icon in the category picker.
    ELECTRONICS,
    FASHION,
    BABY,
    HOME_GARDEN,
    SPORT_HOBBY,
    SPARE_PARTS,
    OTHER,
    ANTIQUES,
    BEAUTY,
    CONSTRUCTION,
    MUSIC_EDUCATION,
    LEISURE,
    OTHER_SALES,
}

internal val EXCLUDED_ROOT_CATEGORY_KINDS = setOf(
    RootCategoryKind.REAL_ESTATE,
    RootCategoryKind.JOBS,
    RootCategoryKind.VEHICLES,
    RootCategoryKind.BUSINESS_SERVICES,
    RootCategoryKind.ANIMALS,
    RootCategoryKind.DAILY_RENTAL,
    RootCategoryKind.RENTAL_LEASE,
)

internal val ROOT_CATEGORY_KINDS_BY_COUNTRY: Map<OlxCountry, Map<Int, RootCategoryKind>> = mapOf(
    OlxCountry.UA to mapOf(
        1 to RootCategoryKind.REAL_ESTATE,
        1532 to RootCategoryKind.VEHICLES,
        6 to RootCategoryKind.JOBS,
        35 to RootCategoryKind.ANIMALS,
        7 to RootCategoryKind.BUSINESS_SERVICES,
        3709 to RootCategoryKind.DAILY_RENTAL,
        3428 to RootCategoryKind.RENTAL_LEASE,
        37 to RootCategoryKind.ELECTRONICS,
        891 to RootCategoryKind.FASHION,
        36 to RootCategoryKind.BABY,
        899 to RootCategoryKind.HOME_GARDEN,
        903 to RootCategoryKind.SPORT_HOBBY,
        3 to RootCategoryKind.SPARE_PARTS,
        3901 to RootCategoryKind.OTHER,
    ),
    OlxCountry.PL to mapOf(
        3 to RootCategoryKind.REAL_ESTATE,
        5 to RootCategoryKind.VEHICLES,
        4 to RootCategoryKind.JOBS,
        103 to RootCategoryKind.ANIMALS,
        4269 to RootCategoryKind.BUSINESS_SERVICES,
        4371 to RootCategoryKind.BUSINESS_SERVICES,
        757 to RootCategoryKind.BUSINESS_SERVICES, // Rolnictwo (agriculture) - commercial/industrial, like UA's excluded Business&Services
        1816 to RootCategoryKind.DAILY_RENTAL,
        3018 to RootCategoryKind.RENTAL_LEASE,
        99 to RootCategoryKind.ELECTRONICS,
        87 to RootCategoryKind.FASHION,
        88 to RootCategoryKind.BABY,
        628 to RootCategoryKind.HOME_GARDEN,
        767 to RootCategoryKind.SPORT_HOBBY,
        4042 to RootCategoryKind.ANTIQUES, // Antyki i Kolekcje
        3647 to RootCategoryKind.BEAUTY, // Zdrowie i Uroda
        5216 to RootCategoryKind.CONSTRUCTION, // Budowa i Remont
        751 to RootCategoryKind.MUSIC_EDUCATION, // Muzyka i Edukacja
    ),
    OlxCountry.PT to mapOf(
        16 to RootCategoryKind.REAL_ESTATE,
        362 to RootCategoryKind.VEHICLES,
        190 to RootCategoryKind.JOBS,
        10 to RootCategoryKind.ANIMALS,
        191 to RootCategoryKind.BUSINESS_SERVICES,
        4918 to RootCategoryKind.BUSINESS_SERVICES, // Equipamentos e Ferramentas (professional equipment/tools)
        4800 to RootCategoryKind.BUSINESS_SERVICES, // Agricultura - commercial/industrial, like UA's excluded Business&Services
        // Portugal has no separate daily-rental/tourism or rental-lease root today.
        11 to RootCategoryKind.ELECTRONICS,
        25 to RootCategoryKind.ELECTRONICS,
        14 to RootCategoryKind.FASHION,
        99 to RootCategoryKind.BABY,
        13 to RootCategoryKind.HOME_GARDEN,
        12 to RootCategoryKind.SPORT_HOBBY,
        5478 to RootCategoryKind.SPARE_PARTS, // Peças e acessórios - same concept as UA's kept Spare Parts root
        26 to RootCategoryKind.LEISURE, // Lazer
        185 to RootCategoryKind.OTHER_SALES, // Outras Vendas
    ),
    OlxCountry.RO to mapOf(
        3 to RootCategoryKind.REAL_ESTATE,
        5 to RootCategoryKind.VEHICLES,
        4 to RootCategoryKind.JOBS,
        103 to RootCategoryKind.ANIMALS,
        619 to RootCategoryKind.BUSINESS_SERVICES,
        2687 to RootCategoryKind.BUSINESS_SERVICES, // Echipamente profesionale si vanzare companii (professional equipment/company sales)
        1183 to RootCategoryKind.BUSINESS_SERVICES, // Agro si industrie - commercial/industrial, like UA's excluded Business&Services
        2666 to RootCategoryKind.DAILY_RENTAL,
        2667 to RootCategoryKind.RENTAL_LEASE,
        99 to RootCategoryKind.ELECTRONICS,
        87 to RootCategoryKind.FASHION,
        88 to RootCategoryKind.BABY,
        628 to RootCategoryKind.HOME_GARDEN,
        97 to RootCategoryKind.SPORT_HOBBY,
        1639 to RootCategoryKind.SPARE_PARTS, // Piese auto - same concept as UA's kept Spare Parts root
    ),
    OlxCountry.BG to mapOf(
        368 to RootCategoryKind.REAL_ESTATE,
        360 to RootCategoryKind.VEHICLES,
        606 to RootCategoryKind.JOBS,
        339 to RootCategoryKind.ANIMALS,
        555 to RootCategoryKind.BUSINESS_SERVICES,
        1012 to RootCategoryKind.BUSINESS_SERVICES, // Машини, инструменти, бизнес оборудване (machines/tools/business equipment)
        545 to RootCategoryKind.DAILY_RENTAL,
        // Bulgaria has no separate rental-lease root today.
        632 to RootCategoryKind.ELECTRONICS,
        655 to RootCategoryKind.FASHION,
        139 to RootCategoryKind.BABY,
        262 to RootCategoryKind.HOME_GARDEN,
        618 to RootCategoryKind.SPORT_HOBBY,
        1625 to RootCategoryKind.SPARE_PARTS, // Авточасти, аксесоари - same concept as UA's kept Spare Parts root
        1609 to RootCategoryKind.ANTIQUES, // Антики и колекции
    ),
)

internal fun excludedRootCategoryIds(country: OlxCountry): Set<Int> =
    ROOT_CATEGORY_KINDS_BY_COUNTRY[country]
        .orEmpty()
        .filterValues { it in EXCLUDED_ROOT_CATEGORY_KINDS }
        .keys

internal fun rootCategoryKind(country: OlxCountry, categoryId: Int): RootCategoryKind? =
    ROOT_CATEGORY_KINDS_BY_COUNTRY[country]?.get(categoryId)
