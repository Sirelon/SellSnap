package com.sirelon.sellsnap.features.seller.openai

import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.response.Response
import com.aallam.openai.api.response.ResponseId
import com.aallam.openai.api.response.ResponseInput
import com.aallam.openai.api.response.ResponseInputItem
import com.aallam.openai.api.response.ResponseRequest
import com.aallam.openai.client.OpenAI
import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.ad.data.GeneratedAdMapper
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val AD_GENERATION_MODEL_ID = "gpt-4.1"

// Bump whenever adGenerationInstructions changes, so ad-generation-log records stay attributable
// to the exact prompt that produced them.
const val AD_GENERATION_PROMPT_VERSION = "v3"

private val DEFAULT_MODEL = ModelId(AD_GENERATION_MODEL_ID)
private const val DEFAULT_IMAGE_DETAIL = "high"
private val NUMBER_PATTERN = Regex("""-?\d+(?:\.\d+)?""")

private fun adGenerationInstructions(country: OlxCountry): String = """
You are writing a single second-hand listing for OLX ${country.nameEn}.
Write like a real private seller talking about their own item — warm, concrete, specific.
Do not sound like a product catalogue, an image caption, or a bot.
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

Use the photos to add concrete visible details that support the seller's facts: colour, visible wear, accessories included, distinguishing features. If the seller note contradicts the photos, trust the seller.

Output fields:
- title: short, searchable, in ${country.language}. Prefer item type + brand + key detail + exact size when available. No emoji, no ALL CAPS, no hashtags.
- description: 3 to 6 short sentences in ${country.language}, conversational tone. No bullet points, no markdown, no hashtags, no emoji. If a seller note is present, at least one sentence should reflect its personal context (reason for selling, how long it was worn, etc.).
- suggestedPrice, minPrice, maxPrice: plain integers in ${country.currencyCode} for the ${country.nameEn} second-hand market. Not retail, not collectible premium. Ensure minPrice <= suggestedPrice <= maxPrice.

Guardrails:
- Every statement in the title and description must be supported by the seller note or clearly visible in the photos. If it is not, leave it out. This is absolute — an accurate short listing beats a fuller one containing anything you filled in yourself.
- This applies to the whole listing, not only the item. Unless the seller note states it, never mention: a city, district, region, or any pickup location; delivery, shipping, courier, or postage; payment methods; warranty, receipts, or original packaging; the reason for selling.
- With no seller note you know nothing beyond the photos. Do not write how long the item was owned or used, how often it was worn, what it was bought for, who used it, or why it is being sold. Phrases like "barely worn", "used a couple of times", or "selling because I bought another one" are inventions unless the seller wrote them.
- ${country.nameEn} is the marketplace, not a fact about this seller. Never turn it into a place the item is located or can be collected from.
- Do not invent brand, size, material, defects, or condition.
- Do not infer the season of clothing unless the seller says so or the photos make it unmistakable.
- If uncertain, simply omit it rather than guessing.
- Do not add filler phrases that are generic placeholders — write only real content.

Return ONLY valid JSON. A listing has this exact shape:
  {"title":"string","description":"string","suggestedPrice":number,"minPrice":number,"maxPrice":number}
A refusal has this exact shape:
  {"unrecognized":"string"}
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

    suspend fun analyzeThing(
        images: List<String>,
        sellerPrompt: String,
        country: OlxCountry,
        model: ModelId = DEFAULT_MODEL,
        imageDetail: String = DEFAULT_IMAGE_DETAIL,
    ): AdAnalysis {
        require(images.isNotEmpty()) { "At least one image is required to generate an advertisement." }
        require(model.id != "gpt-4") {
            "Legacy gpt-4 does not support image input or structured outputs for this flow. Use gpt-4.1, gpt-4o, or a newer model."
        }

        val listingResponse = openAI.response(
            request = ResponseRequest(
                model = model,
                instructions = adGenerationInstructions(country).trimIndent(),
                // Higher temperature gives the description a natural seller voice instead of a catalogue tone.
                temperature = 0.7,
                // Enough headroom for a real localized description plus three prices without truncation.
                maxOutputTokens = 600,
                // Stored so the follow-up attribute-fill call can chain on this response id.
                store = true,
                input = ResponseInput(
                    items = listOf(
                        createListingAnalysisUserItem(
                            images = images,
                            sellerPrompt = sellerPrompt,
                            country = country,
                            imageDetail = imageDetail,
                        )
                    )
                ),
            )
        )

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

        return AdAnalysis.Generated(listingResponse.id, mapper.mapToDomain(generatedAd, images))
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
                resolveAllowedValue(attribute, candidate)?.let(::add)
            }

        suggestion.valueText
            ?.takeIf { it.isNotBlank() }
            ?.let(::splitSuggestedValues)
            .orEmpty()
            .forEach { candidate ->
                resolveAllowedValue(attribute, candidate)?.let(::add)
            }
    }.distinctBy { it.code }

    private fun resolveAllowedValue(
        attribute: OlxAttribute,
        candidate: String,
    ): OlxAttributeValue? {
        val normalizedCandidate = normalizeForMatching(candidate)
        if (normalizedCandidate.isEmpty()) return null

        return attribute.allowedValues.firstOrNull { value ->
            val normalizedCode = normalizeForMatching(value.code)
            val normalizedLabel = normalizeForMatching(value.label)

            normalizedCandidate == normalizedCode ||
                normalizedCandidate == normalizedLabel ||
                normalizedCandidate.contains(normalizedCode) ||
                normalizedCandidate.contains(normalizedLabel) ||
                normalizedCode.contains(normalizedCandidate) ||
                normalizedLabel.contains(normalizedCandidate)
        }
    }

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

    private fun normalizeForMatching(value: String): String = buildString {
        value.lowercase().forEach { char ->
            if (char.isLetterOrDigit()) append(char)
        }
    }

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
