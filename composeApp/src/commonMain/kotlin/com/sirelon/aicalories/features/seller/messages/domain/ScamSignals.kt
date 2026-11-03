package com.sirelon.sellsnap.features.seller.messages.domain

/**
 * On-device rules that spot a likely scam in a message a buyer sent (SIR-176). No network, no AI:
 * a message text in, the codes of the signals it matches out (empty means clean). Only received
 * messages are ever checked; the caller never passes the seller's own.
 *
 * The pattern is the classic OLX phishing one: a "buyer" says the item was paid for with OLX
 * delivery, asks the seller to confirm within hours, and sends a link or a messenger bot that
 * moves the seller off OLX to "confirm the sale" and hand over card or account data.
 *
 * Signals (a message is flagged when any matches):
 * - [MESSENGER_LINK]: a Telegram, WhatsApp or Viber link, or an `@...bot` handle.
 * - [SHORT_LINK]: a link through a URL shortener from [ShortenerDomains].
 * - [FAKE_OLX_DELIVERY]: OLX delivery mentioned together with a confirm / receive-payment wording
 *   or a deadline in hours, AND a link to somewhere other than OLX. The link is required because
 *   a real buyer who paid with OLX delivery writes "please confirm the order" with no link, and
 *   that message must stay clean.
 * - [CARD_REQUEST]: asks for the card's CVV/CVC, its expiry, or an SMS / verification / BLIK code;
 *   a card number counts only when the message also carries a link. A bare "send me your card
 *   number" is how a buyer asks where to transfer the money, so it is not a signal.
 * - [OLX_LOOKALIKE_LINK]: a link to a host that has "olx" in it but is not an official OLX
 *   domain ([OfficialOlxDomains]), such as `olx-delivery.com` or `olx.ua.confirm.xyz`.
 *
 * False positives cost more than misses here, so every list is short and every phrase needs the
 * wording of an actual request. Languages: uk, pl, ro, pt, bg, kk, en. Each list is matched
 * against the lower-cased text, so no pattern needs a case flag. A stem such as `доставк` stands
 * for every ending.
 *
 * What OLX itself says, read for this change:
 * - OLX Polska blog, "Uwaga na fałszywe linki i wiadomości służące wyłudzeniu Twoich danych i
 *   pieniędzy" (blog.olx.pl/2020/06/18/uwaga-na-phishing/): an OLX employee "NIGDY nie poprosi Cię
 *   o kliknięcie w link do «odbioru wpłaty»" and never asks for a BLIK or SMS code to confirm a
 *   transaction; a message that arrives "poza czatem OLX", for instance on WhatsApp, is a warning
 *   sign ("tylko tutaj możemy Ci pomóc"); a scammer may give false instructions on how OLX
 *   shipments work.
 * - OLX Polska blog, "Porady bezpieczeństwa OLX" (blog.olx.pl/bezpieczny-olx/): "Nie klikaj w
 *   żadne zewnętrzne linki umożliwiające płatność."
 * - OLX Ukraine, "Правила безпечних покупок" (help.olx.ua) and the OLX Trust & Safety posts
 *   (safety.olx.ua, blog.olx.ua): no card data or CVV is asked for, delivery is arranged only on
 *   the official site or in the app. Inferred: those pages could not be opened from here (DNS and
 *   Cloudflare blocked the fetch), so this part is taken from search summaries of them, not read.
 *
 * Inferred: the word lists, the shortener list and the official-domain list are SellSnap's own;
 * no OLX page publishes them. Markets other than uk and pl were not checked against a local OLX
 * safety page.
 *
 * Thread rows carry no marker: a row does not load message text, and fetching every thread's
 * messages just to mark rows is out of scope. The warning shows only inside the conversation.
 */
internal object ScamSignals {

    const val MESSENGER_LINK = "messenger_link"
    const val SHORT_LINK = "short_link"
    const val FAKE_OLX_DELIVERY = "fake_olx_delivery"
    const val CARD_REQUEST = "card_request"
    const val OLX_LOOKALIKE_LINK = "olx_lookalike_link"

    /** The OLX domains of the markets the app supports, plus the group's own; a link to these is never suspicious. */
    private val OfficialOlxDomains = listOf("olx.ua", "olx.pl", "olx.ro", "olx.pt", "olx.bg", "olx.kz", "olx.com", "olxgroup.com")

    /** Common URL shorteners; a link through one hides where it goes. Matched only with a path after the domain. */
    private val ShortenerDomains = listOf(
        "bit.ly", "tinyurl.com", "cutt.ly", "t.ly", "is.gd", "v.gd", "goo.su", "clck.ru",
        "rb.gy", "tiny.cc", "shorturl.at", "ow.ly", "rebrand.ly", "s.id",
    )

    /** The codes of every signal [text] matches, sorted; empty when the message looks fine. */
    fun detect(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase()
        val hosts = linkHosts(lower)
        val messenger = MessengerLink.containsMatchIn(lower)
        val lookalike = OlxLookalike.findAll(lower).any { !isOfficial(it.groupValues[1]) || it.groupValues[2].isNotEmpty() }
        val externalLink = messenger || lookalike || hosts.any { !isOfficial(it) }

        return buildList {
            if (messenger) add(MESSENGER_LINK)
            if (ShortLink.containsMatchIn(lower)) add(SHORT_LINK)
            if (isFakeOlxDelivery(lower, externalLink)) add(FAKE_OLX_DELIVERY)
            if (isCardRequest(lower, hasLink = hosts.isNotEmpty() || messenger)) add(CARD_REQUEST)
            if (lookalike) add(OLX_LOOKALIKE_LINK)
        }.sorted()
    }

    private fun isFakeOlxDelivery(lower: String, externalLink: Boolean): Boolean =
        externalLink &&
            ("olx" in lower || "олх" in lower) &&
            DeliveryWords.containsMatchIn(lower) &&
            (ConfirmWords.containsMatchIn(lower) || Deadline.containsMatchIn(lower))

    private fun isCardRequest(lower: String, hasLink: Boolean): Boolean =
        CardSecret.containsMatchIn(lower) || (hasLink && CardNumber.containsMatchIn(lower))

    private fun isOfficial(host: String): Boolean =
        OfficialOlxDomains.any { host == it || host.endsWith(".$it") }

    /** The hosts of the links in [lower]: after `http(s)://` or `www.`, or before a `/` path. */
    private fun linkHosts(lower: String): List<String> =
        LinkHost.findAll(lower).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

    // A host right after a scheme or "www.", or a bare host that is followed by a path. The
    // negative lookahead keeps "olx.ua@evil.com" from reading as host "olx.ua".
    private val LinkHost = Regex(
        """(?:https?://|www\.)((?:[a-z0-9-]+\.)+[a-z]{2,})(?![a-z0-9@-])|(?:^|[^a-z0-9.-])((?:[a-z0-9-]+\.)+[a-z]{2,})/""",
    )

    // A host with "olx" in it, bare or in a link. The prefix keeps "polx.example" from matching
    // inside a longer word; "@" is allowed before it so "me@olx-delivery.com" is seen too. Group 2
    // is a "@" right after the host: "olx.ua@evil.example" is a host called evil.example.
    private val OlxLookalike = Regex("""(?:^|[^a-z0-9.-])((?:[a-z0-9-]+\.)*[a-z0-9-]*olx[a-z0-9-]*(?:\.[a-z0-9-]+)+)(@?)""")

    // Telegram, WhatsApp and Viber links and deep links, and Telegram bot handles. A handle must
    // not sit inside an e-mail address, so "@" needs a clean character before it.
    private val MessengerLink = Regex(
        """(?:^|[^a-z0-9.-])(?:(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me|telegram\.dog|wa\.me|api\.whatsapp\.com|""" +
            """chat\.whatsapp\.com|whatsapp\.com|viber\.com|invite\.viber\.com)/|(?:viber|tg|whatsapp)://)""" +
            """|(?:^|[^a-z0-9._-])@[a-z0-9_]*bot(?![a-z0-9_]|\.[a-z])""",
    )

    private val ShortLink = Regex(
        """(?:^|[^a-z0-9.-])(?:https?://)?(?:www\.)?(?:${ShortenerDomains.joinToString("|") { Regex.escape(it) }})/[a-z0-9]""",
    )

    // Something is being delivered. uk and bg share `доставк`; kk is `жеткізу`.
    private val DeliveryWords = Regex(
        "доставк|dostaw|przesy|wysy[łl]k|livrare|livrat|colet|entrega|encomenda|envio|пратк|жеткізу|delivery|shipping|parcel",
    )

    // A confirm or receive-the-payment request. uk, pl, ro, pt, bg, kk, en.
    private val ConfirmWords = Regex(
        listOf(
            "підтверд", "підтвердж",
            "отрима(?:ти|ння|йте)\\s+(?:оплат|кошт|грош|платіж)",
            "potwierd", "odbierz\\s+(?:p[łl]atno|wp[łl]at|pieni)", "odbi[óo]r\\s+(?:p[łl]atno|wp[łl]at|[śs]rodk)",
            "confirm", "primi\\S*\\s+(?:plata|plată|banii|banilor)",
            "receb\\S+\\s+(?:o\\s+)?(?:pagamento|dinheiro|valor)",
            "потвърд", "получ\\S+\\s+(?:плащане|парите|сумата|средствата)",
            "растау", "растаңыз", "төлемді\\s+алу",
            "receive\\s+(?:your\\s+|the\\s+)?(?:payment|money|funds)", "claim\\s+(?:your\\s+|the\\s+)?(?:payment|money)",
        ).joinToString("|"),
    )

    // "within 3 hours" and its equivalents. A number is required: "soon" is not a deadline.
    private val Deadline = Regex(
        listOf(
            "(?:протягом|упродовж|впродовж|за)\\s*\\d+\\s*(?:год|хв)",
            "w\\s+ci[ąa]gu\\s+\\d+\\s*(?:godz|min)",
            "(?:[iî]n|timp\\s+de|p[âa]n[ăa]\\s+la)\\s*\\d+\\s*(?:ore|minute)",
            "(?:em|dentro\\s+de|no\\s+prazo\\s+de|nas\\s+pr[óo]ximas)\\s*\\d+\\s*(?:hora|minuto)",
            "(?:в\\s+рамките\\s+на|в\\s+срок\\s+(?:от|до)|до)\\s*\\d+\\s*(?:час|мин)",
            "\\d+\\s*сағат\\s*ішінде",
            "within\\s*\\d+\\s*(?:hours?|hrs?|minutes?|mins?)",
        ).joinToString("|"),
    )

    // CVV/CVC, the card's expiry, or an SMS / verification / BLIK code. The cvv token stands alone.
    private val CardSecret = Regex(
        listOf(
            "(?:^|[^a-z0-9])cv[vc]2?(?![a-z0-9])",
            "card\\s+expir(?:y|ation)|expir(?:y|ation)\\s+date|valid\\s+thru",
            "термін\\s+дії\\s+(?:вашої\\s+)?карт|строк\\s+дії\\s+(?:вашої\\s+)?карт",
            "wa[żz]no[śs]ci\\s+karty",
            "(?:expir[aă]r\\S*|valabilitat\\S*)\\s+(?:a\\s+)?(?:cardului|card)",
            "validade\\s+(?:do|de)\\s+cart[aã]o",
            "валидност\\S*\\s+на\\s+карта",
            "картаның\\s+(?:қолданылу\\s+)?мерзімі",
            "sms\\s+code|(?:verification|confirmation|security|one[- ]time)\\s+code|code\\s+from\\s+(?:the\\s+)?sms",
            "(?:sms|смс)[\\s-]*код|код\\S*\\s+(?:з|із)\\s+(?:sms|смс)|код\\S*\\s+підтвердження",
            "kod\\S*\\s+(?:z\\s+)?sms|kod\\S*\\s+blik|kod\\S*\\s+(?:weryfikacyjn|potwierdzaj)",
            "cod\\S*\\s+(?:din\\s+|prin\\s+)?sms|cod\\S*\\s+(?:de\\s+)?(?:verificare|confirmare)",
            "c[óo]digo\\S*\\s+(?:do\\s+|de\\s+|por\\s+|via\\s+)?sms|c[óo]digo\\S*\\s+de\\s+(?:verifica[çc][ãa]o|confirma[çc][ãa]o)",
            "код\\S*\\s+за\\s+(?:потвърждение|верификация)",
            "растау\\s+код",
        ).joinToString("|"),
    )

    // A card number being asked for. Counts only together with a link (see CARD_REQUEST).
    private val CardNumber = Regex(
        listOf(
            "card\\s+number",
            "номер\\S*\\s+(?:вашої\\s+)?карт",
            "numer\\S*\\s+(?:swojej\\s+|twojej\\s+)?karty",
            "num[aă]r\\S*\\s+(?:de\\s+)?card",
            "n[úu]mero\\S*\\s+do\\s+cart",
            "номер\\S*\\s+на\\s+карта",
            "карта\\S*\\s+нөмір",
        ).joinToString("|"),
    )
}
