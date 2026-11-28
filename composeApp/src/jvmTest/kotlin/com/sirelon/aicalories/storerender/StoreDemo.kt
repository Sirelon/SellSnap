package com.sirelon.sellsnap.storerender

/**
 * Demo content shown inside store screenshots, per language: the listing a seller is making,
 * their other listings, buyer messages. Every person and listing is fictional (App Review 2.3.9).
 * This is seller content, not app copy: app copy comes from strings.xml through the real screens.
 */
data class StoreDemo(
    /** The optional hint typed on the new-listing screen. */
    val hint: String,
    /** The listing the AI writes from the sneaker photos. */
    val listing: DemoListing,
) {
    companion object {
        fun forLang(lang: StoreLang): StoreDemo = when (lang) {
            StoreLang.EN -> EN
            StoreLang.UK -> UK
            StoreLang.PL -> PL
            StoreLang.RO -> RO
            StoreLang.BG -> BG
            StoreLang.PT -> PT
            StoreLang.KK -> KK
        }
    }
}

data class DemoListing(
    val title: String,
    val description: String,
    val price: Int,
    val priceMin: Int,
    val priceMax: Int,
    val photos: List<String> = listOf(demoPhoto("sneakers_1"), demoPhoto("sneakers_2"), demoPhoto("sneakers_3")),
)

private val EN = StoreDemo(
    hint = "Size 42, worn a few times",
    listing = DemoListing(
        title = "New Balance 990v5 sneakers, size 42, made in USA",
        description = "Original New Balance 990v5 in dark grey suede, size EU 42 (US 8.5). " +
            "Worn only a few times, no damage, clean insoles, laces like new. " +
            "Comfortable for every day. Pickup in person or shipping.",
        price = 85, priceMin = 70, priceMax = 100,
    ),
)

private val UK = StoreDemo(
    hint = "Розмір 42, носив кілька разів",
    listing = DemoListing(
        title = "Кросівки New Balance 990v5, розмір 42, made in USA",
        description = "Оригінальні New Balance 990v5, темно-сіра замша, розмір EU 42 (US 8.5). " +
            "Взувані кілька разів, без пошкоджень, чисті устілки, шнурки як нові. " +
            "Зручні на кожен день. Самовивіз або доставка Новою поштою.",
        price = 3200, priceMin = 2700, priceMax = 3800,
    ),
)

private val PL = StoreDemo(
    hint = "Rozmiar 42, noszone kilka razy",
    listing = DemoListing(
        title = "Buty New Balance 990v5, rozmiar 42, made in USA",
        description = "Oryginalne New Balance 990v5, ciemnoszary zamsz, rozmiar EU 42 (US 8.5). " +
            "Noszone kilka razy, bez uszkodzeń, czyste wkładki, sznurówki jak nowe. " +
            "Wygodne na co dzień. Odbiór osobisty lub wysyłka.",
        price = 350, priceMin = 290, priceMax = 420,
    ),
)

private val RO = StoreDemo(
    hint = "Mărimea 42, purtați de câteva ori",
    listing = DemoListing(
        title = "Adidași New Balance 990v5, mărimea 42, made in USA",
        description = "New Balance 990v5 originali, piele întoarsă gri închis, mărimea EU 42 (US 8.5). " +
            "Purtați de câteva ori, fără defecte, branțuri curate, șireturi ca noi. " +
            "Comozi pentru fiecare zi. Predare personală sau livrare prin curier.",
        price = 420, priceMin = 350, priceMax = 500,
    ),
)

private val BG = StoreDemo(
    hint = "Номер 42, носени няколко пъти",
    listing = DemoListing(
        title = "Маратонки New Balance 990v5, номер 42, made in USA",
        description = "Оригинални New Balance 990v5, тъмносив велур, номер EU 42 (US 8.5). " +
            "Носени няколко пъти, без забележки, чисти стелки, връзки като нови. " +
            "Удобни за всеки ден. Лично предаване или изпращане с куриер.",
        price = 85, priceMin = 70, priceMax = 100,
    ),
)

private val PT = StoreDemo(
    hint = "Tamanho 42, usadas poucas vezes",
    listing = DemoListing(
        title = "Sapatilhas New Balance 990v5, tamanho 42, made in USA",
        description = "New Balance 990v5 originais, camurça cinzento-escuro, tamanho EU 42 (US 8.5). " +
            "Usadas poucas vezes, sem defeitos, palmilhas limpas, atacadores como novos. " +
            "Confortáveis para o dia a dia. Entrega em mão ou envio por CTT.",
        price = 85, priceMin = 70, priceMax = 100,
    ),
)

private val KK = StoreDemo(
    hint = "Өлшемі 42, бірнеше рет киілген",
    listing = DemoListing(
        title = "New Balance 990v5 кроссовкасы, өлшемі 42, made in USA",
        description = "Түпнұсқа New Balance 990v5, қою сұр замша, өлшемі EU 42 (US 8.5). " +
            "Бірнеше рет қана киілген, ақауы жоқ, ұлтарағы таза, бауы жаңадай. " +
            "Күнделікті киюге ыңғайлы. Өзіңіз алып кетесіз немесе жеткізу бар.",
        price = 45000, priceMin = 38000, priceMax = 52000,
    ),
)
