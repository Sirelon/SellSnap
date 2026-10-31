package com.sirelon.sellsnap.features.seller.openai

import com.aallam.openai.api.exception.OpenAIAPIException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.response.Response
import com.aallam.openai.api.response.ResponseId
import com.aallam.openai.api.response.ResponseInput
import com.aallam.openai.api.response.ResponseInputItem
import com.aallam.openai.api.response.ResponseRequest
import com.aallam.openai.client.OpenAI
import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.ad.data.GeneratedAdMapper
import com.sirelon.sellsnap.features.seller.ad.data.roundedToMarketSteps
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeInputType
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.openai.requests.OpenAIAttributeOptionRequest
import com.sirelon.sellsnap.features.seller.openai.requests.OpenAIAttributeRequest
import com.sirelon.sellsnap.features.seller.openai.requests.OpenAIAttributesRequest
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIAttributeSuggestionResponse
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIAttributeSuggestionsResponse
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIGeneratedAd
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIPolishedListing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val AD_GENERATION_MODEL_ID = "gpt-4.1"

// Bump whenever adGenerationInstructions changes, so ad-generation-log records stay attributable
// to the exact prompt that produced them.
const val AD_GENERATION_PROMPT_VERSION = "v6"

private val DEFAULT_MODEL = ModelId(AD_GENERATION_MODEL_ID)
private const val DEFAULT_IMAGE_DETAIL = "high"
private val NUMBER_PATTERN = Regex("""-?\d+(?:\.\d+)?""")

internal fun adGenerationInstructions(country: OlxCountry): String = """
You are writing a single second-hand listing for OLX ${country.nameEn}.
Write like a real private seller talking about their own item — warm, concrete, specific.
Do not sound like a product catalogue, an image caption, or a bot.
The buyer sees the photos next to your text, so describe the item itself, never the pictures of it. Never write phrases like "on the photo", "pictured", "as you can see", "the photo shows", "на фото", "na zdjęciu", "în poză", "na foto" or "на снимката". Write "The jacket has a small scuff on the left sleeve", not "On the photo you can see a scuff on the left sleeve".
Warmth comes from plain, specific language about what is actually in front of you, never from invented history.

You have two sources:
- The photos of the item.
- An optional seller note (free text) that the seller wrote about this exact item.

When the photos cannot carry a listing, say so instead of writing one. Return only:
  {"unrecognized":"<code>"}
with exactly one of these codes:
- too_blurry: too out of focus or motion-blurred to make out what the item is.
- too_dark: too dark or too blown out to make out what the item is.
- not_an_item: nothing sellable in frame — a bare wall, a floor, the sky, a person on their own.
- different_items: the photos show unrelated items rather than one item from several angles.

Refuse only when one of those plainly applies. A usable photo of an ordinary item is never a refusal, and clutter or a busy background is not `different_items` — pick the main item and write the listing.
A seller note that names the item is enough to write a listing even from poor photos: when a note names it, write the listing rather than refusing.
`different_items` is the one exception — report it even when a seller note is present, because every photo the seller uploaded is published alongside the listing.

If the seller note is present, treat it as the source of truth.
- Preserve the seller's exact tokens for brand, model, size, condition, and purchase age.
- Carry over meaningful personal context: reason for selling, how long it was used, occasion bought for, who used it. Weave it into the description naturally. Do not copy the note verbatim.
- If the seller mentions something the photos do not show (e.g. "used on two trips"), keep it — the seller knows their item.
- Every fact in the seller note must reach the listing, in the title or the description: size, brand, model, condition, defects, purchase age, what is included, and personal context. Drop a fact only when the note itself contradicts it. A seller fact always outranks a visible detail for a place in the description.

Use the photos to add concrete visible details that support the seller's facts: colour, visible wear, accessories included, distinguishing features. If the seller note contradicts the photos, trust the seller.

Output fields:
- title: short, searchable, in ${country.language}. Prefer item type + brand + key detail + exact size when available. No emoji, no ALL CAPS, no hashtags. Never put a placeholder for an unknown value in the title ("r. brak", "розмір невідомий", "size unknown", "N/A", "?"): if the size, model or colour is not visible and not in the seller note, leave it out of the title.
- description: 3 to 6 short sentences in ${country.language}, conversational tone. No bullet points, no markdown, no hashtags, no emoji. The first sentence states what the item is; it never opens with a selling verb or a stock phrase such as "Sprzedaję", "Продам", "Vând", "Vendo" or "Продавам". Fill the sentences with concrete visible detail; write fewer only when the photos and seller note truly show little, because then an accurate shorter description beats a padded one. If a seller note is present, at least one sentence should reflect its personal context (reason for selling, how long it was worn, etc.).
- suggestedPrice, minPrice, maxPrice: plain integers in ${country.currencyCode} for the ${country.nameEn} second-hand market. Not retail, not collectible premium. Ensure minPrice <= suggestedPrice <= maxPrice.

Guardrails:
- Every statement in the title and description must be supported by the seller note or clearly visible in the photos. If it is not, leave it out. This is absolute — an accurate short listing beats a fuller one containing anything you filled in yourself.
- This applies to the whole listing, not only the item. Unless the seller note states it, never mention: a city, district, region, or any pickup location; delivery, shipping, courier, or postage; payment methods; warranty, receipts, or original packaging; the reason for selling.
- With no seller note you know nothing beyond the photos. Do not write how long the item was owned or used, how often it was worn, what it was bought for, who used it, or why it is being sold. Phrases like "barely worn", "used a couple of times", or "selling because I bought another one" are inventions unless the seller wrote them.
- ${country.nameEn} is the marketplace, not a fact about this seller. Never turn it into a place the item is located or can be collected from.
- Do not invent brand, size, material, defects, or condition.
- A photo shows how an item looks, not how it feels or performs. Unless the seller note states it, never claim comfort, warmth or insulation, fit (how it fits or sits on the body), durability, smell or freshness, or that it is ideal or perfect for an occasion, activity, season or kind of person ("ideal for…").
- Do not infer the season of clothing unless the seller says so or the photos make it unmistakable.
- If uncertain, simply omit it rather than guessing.
- Do not add filler phrases that are generic placeholders — write only real content.

Return ONLY valid JSON. A listing has this exact shape:
  {"title":"string","description":"string","suggestedPrice":number,"minPrice":number,"maxPrice":number}
A refusal has this exact shape:
  {"unrecognized":"string"}
The response must start with `{` and end with `}`.
"""

internal fun listingPolishInstructions(country: OlxCountry): String = """
You revise a second-hand listing for OLX ${country.nameEn}. You get the photos of the item, the listing as it stands now, the seller's original note if there was one, and the seller's instruction.

The instruction is either a change to make ("make it shorter", "mention free delivery", "it is size 42, not 44") or a whole new description the seller wrote or dictated. Treat a new description as the seller's own text: keep its facts and tidy it up, do not replace it with yours.

Description rules:
- Apply exactly what the instruction asks and keep everything else: every fact, the tone and the order, unless the instruction changes them.
- Keep the language of the current description, whatever language the instruction is written in, unless the seller asks for another language.
- A fact the seller states in the instruction is true and outranks the photos. Never add any other fact that is not visible in the photos: no brand, size, material, condition, defects, location, delivery, payment or reason for selling the seller has not written.
- Describe the item, never the pictures of it: no "on the photo", "pictured", "as you can see".
- Plain text only: no markdown, no bullet points, no hashtags and no emoji, unless the seller asks for them.

sameItem: false only when the revised listing is about a different item than the current title names - another kind of thing, or another brand or model. A corrected size, colour, condition or a different wording is still the same item.

Title: the title for the revised listing, in the language of the description. Short and searchable: item type + brand + key detail + exact size when known. No emoji, no ALL CAPS, no hashtags, no placeholder for an unknown value. When sameItem is true and the instruction does not touch what the title says, return the current title unchanged.

Prices: price the item the revised listing describes, as it looks in the photos, for the ${country.nameEn} second-hand market. Plain integers in ${country.currencyCode}. Not retail, not collectible premium. Ensure minPrice <= suggestedPrice <= maxPrice.

Return ONLY valid JSON with this exact shape:
  {"sameItem":true,"title":"string","description":"string","suggestedPrice":number,"minPrice":number,"maxPrice":number}
The response must start with `{` and end with `}`.
"""

private const val ATTRIBUTE_FILL_INSTRUCTIONS = """
Fill the provided OLX attributes for the same item from the previous turn.

This is form filling, not captioning. Be precise and terse.

Source priority:
  1. The seller note repeated below (exact brand, size, model, condition, purchase age).
  2. The listing title and description you just produced.
  3. The images you already analysed in the previous turn.
  4. Attribute labels and allowed option labels.

Rules:
- Return only attributes from the provided list.
- For select and multi-select attributes, return allowed option codes in `valueCodes`.
- For numeric and text attributes, return one plain value in `valueText`.
- Never replace an exact seller-provided value with an approximation. If the seller said `XL`, do not return `L-XL`, `L`, or leave it empty. If the seller said `Nike`, do not return `Adidas` or a generic "sport brand".
- If a seller-provided value matches an allowed option label semantically, return the corresponding option code. You may repeat the matched label in `valueText` as a fallback hint.
- If the value is not stated by the seller and not directly visible in the photos, leave `valueCodes` empty and `valueText` empty.
- Respect numeric min/max limits on each attribute.
- Use at most one value unless the attribute explicitly supports multiple choices.
- Never invent unsupported details.

Return ONLY valid JSON with this exact shape:
  {"attributes":[{"code":"string","valueCodes":["string"],"valueText":"string","confidence":"high|medium|low"}]}
The response must start with `{` and end with `}`.
"""

/**
 * Maps a model answer (an option code, or label text) to one allowed option. Matching runs from
 * strict to loose across all options, so an exact code wins over an earlier option whose text only
 * contains it: with first-substring matching, a correct "xl" resolved to "L / 50-52" and "s" to
 * "XXXS / 40". The loose tiers return a match only when it is unambiguous.
 */
internal fun resolveAllowedValue(
    allowedValues: List<OlxAttributeValue>,
    candidate: String,
): OlxAttributeValue? {
    val normalizedCandidate = normalizeForMatching(candidate)
    if (normalizedCandidate.isEmpty()) return null

    allowedValues.firstOrNull { normalizeForMatching(it.code) == normalizedCandidate }?.let { return it }
    allowedValues.firstOrNull { normalizeForMatching(it.label) == normalizedCandidate }?.let { return it }

    // OLX size labels combine sizing systems, e.g. "42 / XL / 50" or "L / 50-52".
    allowedValues
        .filter { value -> value.label.split('/').any { normalizeForMatching(it) == normalizedCandidate } }
        .singleOrNull()
        ?.let { return it }

    return allowedValues
        .filter { value ->
            listOf(value.code, value.label)
                .map(::normalizeForMatching)
                .filter { it.isNotEmpty() }
                .any { it.contains(normalizedCandidate) || normalizedCandidate.contains(it) }
        }
        .singleOrNull()
}

private fun normalizeForMatching(value: String): String = buildString {
    value.lowercase().forEach { char ->
        if (char.isLetterOrDigit()) append(char)
    }
}

/**
 * Why the model declined to write a listing rather than inventing one. [code] is the wire value
 * the prompt tells the model to answer with.
 */
enum class UnusablePhotoReason(val code: String) {
    TooBlurry("too_blurry"),
    TooDark("too_dark"),
    NotAnItem("not_an_item"),
    DifferentItems("different_items");

    internal companion object {
        fun from(code: String): UnusablePhotoReason? {
            val normalized = code.trim().lowercase()
            return entries.firstOrNull { it.code == normalized }
        }
    }
}

/**
 * SIR-86: a revised listing. [sameItem] is false when the seller's revision names a different
 * item; [responseId] chains the attribute fill for that item onto this turn.
 */
data class PolishedListing(
    val responseId: ResponseId,
    val sameItem: Boolean,
    val advertisement: Advertisement,
)

/** The two outcomes of reading a seller's photos. */
sealed interface AdAnalysis {
    /** [responseId] chains the attribute-fill turn onto the same thread. */
    data class Generated(val responseId: ResponseId, val advertisement: Advertisement) : AdAnalysis

    data class Unusable(val reason: UnusablePhotoReason) : AdAnalysis
}

class OpenAIClient(
    private val openAI: OpenAI,
    private val json: Json,
    private val compactJson: Json,
) {

    private val mapper = GeneratedAdMapper()

    suspend fun fillAdditionalInfo(
        previousResponseId: ResponseId,
        attributes: List<OlxAttribute>,
        sellerPrompt: String,
        model: ModelId = DEFAULT_MODEL,
    ): Map<String, List<OlxAttributeValue>> {
        if (attributes.isEmpty()) return emptyMap()

        val response = openAI.response(
            request = ResponseRequest(
                model = model,
                // Continues the same thread as the listing turn so the model still "remembers" the images and draft.
                previousResponseId = previousResponseId,
                instructions = ATTRIBUTE_FILL_INSTRUCTIONS.trimIndent(),
                // Form filling is mechanical — we want stable, non-creative picks.
                temperature = 0.0,
                maxOutputTokens = attributeOutputTokenLimit(attributes.size),
                store = false,
                input = ResponseInput(
                    items = buildList {
                        sellerPrompt.trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { prompt ->
                                add(createTextUserResponseItem(buildSellerNoteBlock(prompt)))
                            }
                        add(createTextUserResponseItem(buildAttributeFillPrompt(attributes)))
                    }
                ),
            )
        )

        val jsonString = extractTextPayload(response)
        val suggestions = json.decodeFromString<OpenAIAttributeSuggestionsResponse>(jsonString)
        return mapAttributeSuggestions(attributes, suggestions)
    }

    /**
     * Writes the listing from [images]. With [previousResponseId] it is a regeneration chained onto
     * that stored turn: the photos are already in the conversation and are not sent again - see
     * [chainedOrFresh].
     */
    suspend fun analyzeThing(
        images: List<String>,
        sellerPrompt: String,
        country: OlxCountry,
        previousResponseId: ResponseId? = null,
        model: ModelId = DEFAULT_MODEL,
        imageDetail: String = DEFAULT_IMAGE_DETAIL,
    ): AdAnalysis {
        require(images.isNotEmpty()) { "At least one image is required to generate an advertisement." }
        require(model.id != "gpt-4") {
            "Legacy gpt-4 does not support image input or structured outputs for this flow. Use gpt-4.1, gpt-4o, or a newer model."
        }

        val listingResponse = chainedOrFresh(previousResponseId) { chainedTo ->
            openAI.response(
                request = ResponseRequest(
                    model = model,
                    previousResponseId = chainedTo,
                    instructions = adGenerationInstructions(country).trimIndent(),
                    // Low so the same item gets the same price from one generation to the next: at 0.7
                    // prices for one item drifted up to 46% apart (SIR-116). The opener rule in the
                    // instructions now carries the variety in voice that the higher temperature gave.
                    temperature = 0.3,
                    // Polish and Ukrainian tokenize at roughly twice the English rate, so a six-sentence
                    // description plus title and three prices needs this much headroom; a 600 cap was hit in production.
                    maxOutputTokens = 1000,
                    // Stored so the attribute fill, a polish and a regeneration can chain onto it.
                    store = true,
                    input = ResponseInput(
                        items = listOf(
                            if (chainedTo != null) {
                                createTextUserResponseItem(
                                    buildString {
                                        append("Write the OLX ${country.nameEn} listing for the same item again: a fresh version, ")
                                        append("by the same rules. Return the same JSON shape.")
                                        sellerPrompt.trim().takeIf { it.isNotEmpty() }?.let {
                                            appendLine()
                                            append(buildSellerNoteBlock(it))
                                        }
                                    }
                                )
                            } else {
                                createListingAnalysisUserItem(
                                    images = images,
                                    sellerPrompt = sellerPrompt,
                                    country = country,
                                    imageDetail = imageDetail,
                                )
                            }
                        )
                    ),
                )
            )
        }

        val listingJson = extractTextPayload(listingResponse)
        val generatedAd = json.decodeFromString<OpenAIGeneratedAd>(listingJson)

        val refusalCode = generatedAd.unrecognized?.trim()?.takeIf { it.isNotEmpty() }
        if (refusalCode != null) {
            // A refusal we cannot name has no message to show, and mapping it to a listing would
            // put the placeholder title back in front of the seller - the thing this branch exists
            // to stop. Fail loudly instead, so the unknown code surfaces.
            val reason = UnusablePhotoReason.from(refusalCode)
                ?: error("Ad generation refused with an unknown code: " + refusalCode)
            return AdAnalysis.Unusable(reason)
        }

        return AdAnalysis.Generated(
            responseId = listingResponse.id,
            advertisement = mapper.mapToDomain(generatedAd, images).roundedToMarketSteps(country),
        )
    }

    /**
     * SIR-86: revises the listing the way the seller's [instruction] asks, and prices it again.
     * With [previousResponseId] it is a turn in the listing's own conversation, which already
     * holds the photos and the seller's note, so neither is sent again - see [chainedOrFresh].
     * Stored, so the next polish, a regeneration and the attribute fill for a different item can
     * chain onto it. When the answer has no usable price, the listing keeps [currentPrices]: the
     * text change is what the seller asked for, and failing it over a missing price loses it.
     */
    suspend fun polishListing(
        images: List<String>,
        title: String,
        description: String,
        currentPrices: PriceRange,
        instruction: String,
        sellerPrompt: String,
        country: OlxCountry,
        previousResponseId: ResponseId? = null,
        model: ModelId = DEFAULT_MODEL,
        imageDetail: String = DEFAULT_IMAGE_DETAIL,
    ): PolishedListing {
        val polishPrompt = buildPolishPrompt(title, description, instruction)
        val response = chainedOrFresh(previousResponseId) { chainedTo ->
            openAI.response(
                request = ResponseRequest(
                    model = model,
                    previousResponseId = chainedTo,
                    // A chained turn keeps the listing turn's instructions, so the conversation
                    // starts the same and its earlier part, photos included, can be billed at the
                    // cached rate; the polish rules then travel in the message.
                    instructions = if (chainedTo != null) {
                        adGenerationInstructions(country).trimIndent()
                    } else {
                        listingPolishInstructions(country).trimIndent()
                    },
                    // Same as the listing turn: low, so the same item gets the same price (SIR-116).
                    temperature = 0.3,
                    // The description field holds up to 9000 characters; Polish and Ukrainian run at
                    // roughly two characters per token.
                    maxOutputTokens = 4500,
                    store = true,
                    input = ResponseInput(
                        items = listOf(
                            if (chainedTo != null) {
                                createTextUserResponseItem(
                                    "Revise the listing you wrote. For this turn these rules replace the " +
                                        "output rules above.\n\n" +
                                        listingPolishInstructions(country).trimIndent() + "\n\n" + polishPrompt
                                )
                            } else {
                                ResponseInputItem(
                                    role = "user",
                                    content = buildJsonArray {
                                        add(createTextContent(polishPrompt))
                                        sellerPrompt.trim()
                                            .takeIf { it.isNotEmpty() }
                                            ?.let { add(createTextContent(buildSellerNoteBlock(it))) }
                                        images.forEach { add(createImageContent(it, imageDetail)) }
                                    },
                                )
                            }
                        )
                    ),
                )
            )
        }
        val polished = json.decodeFromString<OpenAIPolishedListing>(extractTextPayload(response))
        val hasPrice = (polished.suggestedPrice ?: 0f) > 0f
        val advertisement = mapper.mapToDomain(
            OpenAIGeneratedAd(
                title = polished.title,
                description = polished.description,
                suggestedPrice = if (hasPrice) polished.suggestedPrice else currentPrices.suggested,
                minPrice = if (hasPrice) polished.minPrice else currentPrices.min,
                maxPrice = if (hasPrice) polished.maxPrice else currentPrices.max,
            ),
            images,
        ).roundedToMarketSteps(country)
        return PolishedListing(
            responseId = response.id,
            sameItem = polished.sameItem,
            advertisement = advertisement,
        )
    }

    /** The listing's prices before a polish; see [polishListing]. */
    class PriceRange(val suggested: Float, val min: Float, val max: Float)

    /**
     * Runs [request] chained onto [previousResponseId], or fresh with the photos when there is no
     * id. Chaining does not make the earlier turns free - OpenAI bills every input token of the
     * chain again ("Even when using `previous_response_id`, all previous input tokens for
     * responses in the chain are billed as input tokens",
     * developers.openai.com/api/docs/guides/conversation-state) - but a request that starts like
     * the one before it is billed at the cached-input rate for that part, images included
     * (developers.openai.com/api/docs/guides/prompt-caching). OpenAI keeps a stored response for
     * 30 days (same conversation-state guide), so an older draft's id is refused with a 4xx and
     * the request runs once more with the photos.
     */
    private suspend fun <T> chainedOrFresh(
        previousResponseId: ResponseId?,
        request: suspend (chainedTo: ResponseId?) -> T,
    ): T {
        if (previousResponseId == null) return request(null)
        return try {
            request(previousResponseId)
        } catch (error: OpenAIAPIException) {
            if (error.statusCode != 400 && error.statusCode != 404) throw error
            request(null)
        }
    }

    private fun buildPolishPrompt(title: String, description: String, instruction: String) = buildString {
        appendLine("Current title:")
        appendLine("<<<")
        appendLine(title)
        appendLine(">>>")
        appendLine("Current description:")
        appendLine("<<<")
        appendLine(description)
        appendLine(">>>")
        appendLine("Seller's instruction:")
        appendLine("<<<")
        appendLine(instruction)
        append(">>>")
    }

    private fun createListingAnalysisUserItem(
        images: List<String>,
        sellerPrompt: String,
        country: OlxCountry,
        imageDetail: String,
    ): ResponseInputItem = ResponseInputItem(
        role = "user",
        content = buildJsonArray {
            add(createTextContent("Generate the OLX ${country.nameEn} listing for the main item shown in the photos."))
            sellerPrompt.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { prompt ->
                    add(createTextContent(buildSellerNoteBlock(prompt)))
                }
            images.forEach { imageUrl ->
                add(createImageContent(imageUrl, imageDetail))
            }
        }
    )

    private fun createTextUserResponseItem(text: String): ResponseInputItem = ResponseInputItem(
        role = "user",
        content = buildJsonArray {
            add(createTextContent(text))
        }
    )

    private fun buildSellerNoteBlock(sellerPrompt: String): String = buildString {
        appendLine("Seller note — treat as the source of truth. Preserve exact brand, size, model, condition, and purchase-age wording, and carry over personal context (reason for selling, how long it was used, occasion).")
        appendLine("<<<")
        appendLine(sellerPrompt)
        append(">>>")
    }

    private fun createTextContent(text: String) = buildJsonObject {
        put("type", "input_text")
        put("text", text)
    }

    private fun createImageContent(
        imageUrl: String,
        imageDetail: String,
    ) = buildJsonObject {
        put("type", "input_image")
        put("image_url", imageUrl)
        // "high" gives better recognition when later turns depend on precise item understanding.
        put("detail", imageDetail)
    }

    private fun buildAttributeFillPrompt(attributes: List<OlxAttribute>): String = buildString {
        appendLine("Fill these OLX attributes for the same item you just listed.")
        appendLine("Available OLX attributes and allowed options:")
        // Send a compact, model-friendly schema instead of the raw OLX transport payload.
        append("Attributes JSON: ")
        append(compactJson.encodeToString(OpenAIAttributesRequest(attributes.map(::toAttributeRequest))))
    }

    private fun toAttributeRequest(attribute: OlxAttribute): OpenAIAttributeRequest =
        OpenAIAttributeRequest(
            code = attribute.code,
            label = attribute.label,
            type = attribute.inputType.toOpenAIType(),
            required = true.takeIf { attribute.validationRules.required },
            options = attribute.allowedValues
                .takeIf { it.isNotEmpty() }
                ?.map { value ->
                    OpenAIAttributeOptionRequest(
                        code = value.code,
                        label = value.label,
                    )
                },
            min = attribute.validationRules.min,
            max = attribute.validationRules.max,
            unit = attribute.unit.takeIf { it.isNotBlank() },
        )

    private fun extractTextPayload(response: Response): String {
        response.error?.message
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                error("OpenAI request failed: $message")
            }

        if (response.status == "incomplete") {
            error("OpenAI returned an incomplete response: ${response.incompleteDetails ?: "no details"}")
        }

        response.output
            .asSequence()
            .flatMap { it.content.orEmpty().asSequence() }
            .mapNotNull { it.refusal }
            .firstOrNull()
            ?.let { refusal ->
                error("OpenAI refused to generate the advertisement: $refusal")
            }

        val payload = response.outputText
            ?: response.output
                .asSequence()
                .flatMap { it.content.orEmpty().asSequence() }
                .mapNotNull { it.text }
                .joinToString(separator = "\n")

        val sanitizedPayload = sanitizeJsonPayload(payload)
        if (sanitizedPayload.isBlank()) {
            error("OpenAI returned an empty advertisement payload.")
        }
        return sanitizedPayload
    }

    private fun attributeOutputTokenLimit(attributeCount: Int): Int =
        (attributeCount * 40).coerceIn(200, 1200)

    private fun mapAttributeSuggestions(
        attributes: List<OlxAttribute>,
        suggestions: OpenAIAttributeSuggestionsResponse,
    ): Map<String, List<OlxAttributeValue>> {
        val attributesByCode = attributes.associateBy { it.code }

        return suggestions.attributes
            .orEmpty()
            .mapNotNull { suggestion ->
                val code = suggestion.code ?: return@mapNotNull null
                val attribute = attributesByCode[code] ?: return@mapNotNull null
                code to mapSuggestedValues(attribute, suggestion)
            }
            .toMap()
    }

    private fun mapSuggestedValues(
        attribute: OlxAttribute,
        suggestion: OpenAIAttributeSuggestionResponse,
    ): List<OlxAttributeValue> = when (attribute.inputType) {
        AttributeInputType.SingleSelect -> resolveSuggestedOptionValues(attribute, suggestion)
            .take(1)

        AttributeInputType.MultiSelect -> resolveSuggestedOptionValues(attribute, suggestion)

        AttributeInputType.NumericInput -> extractNumericValue(suggestion.valueText)
            ?.takeIf { value ->
                val number = value.toDoubleOrNull() ?: return@takeIf false
                val min = attribute.validationRules.min
                val max = attribute.validationRules.max
                (min == null || number >= min) && (max == null || number <= max)
            }
            ?.let { listOf(OlxAttributeValue(code = attribute.code, label = it)) }
            .orEmpty()

        AttributeInputType.TextInput -> suggestion.valueText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { listOf(OlxAttributeValue(code = attribute.code, label = it)) }
            .orEmpty()
    }

    private fun resolveSuggestedOptionValues(
        attribute: OlxAttribute,
        suggestion: OpenAIAttributeSuggestionResponse,
    ): List<OlxAttributeValue> = buildList {
        suggestion.valueCodes
            .orEmpty()
            .forEach { candidate ->
                resolveAllowedValue(attribute.allowedValues, candidate)?.let(::add)
            }

        suggestion.valueText
            ?.takeIf { it.isNotBlank() }
            ?.let(::splitSuggestedValues)
            .orEmpty()
            .forEach { candidate ->
                resolveAllowedValue(attribute.allowedValues, candidate)?.let(::add)
            }
    }.distinctBy { it.code }

    private fun splitSuggestedValues(valueText: String): List<String> = valueText
        .split(",", ";", "/", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf(valueText.trim()) }

    private fun extractNumericValue(valueText: String?): String? = valueText
        ?.replace(',', '.')
        ?.let { numericText ->
            NUMBER_PATTERN.find(numericText)?.value
        }
        ?.takeIf { it.isNotBlank() }

    private fun AttributeInputType.toOpenAIType(): String = when (this) {
        AttributeInputType.SingleSelect -> "single_select"
        AttributeInputType.MultiSelect -> "multi_select"
        AttributeInputType.NumericInput -> "number"
        AttributeInputType.TextInput -> "text"
    }

    private fun sanitizeJsonPayload(payload: String): String {
        val trimmed = payload.trim()
        return when {
            trimmed.startsWith("```json") -> trimmed.removePrefix("```json").removeSuffix("```").trim()
            trimmed.startsWith("```") -> trimmed.removePrefix("```").removeSuffix("```").trim()
            else -> trimmed
        }
    }
}
