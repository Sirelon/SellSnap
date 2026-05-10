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

private val DEFAULT_MODEL = ModelId("gpt-4.1")
private const val DEFAULT_IMAGE_DETAIL = "high"
private val NUMBER_PATTERN = Regex("""-?\d+(?:\.\d+)?""")

private const val AD_GENERATION_INSTRUCTIONS = """
You are writing a single second-hand listing for OLX Ukraine.
Write like a real private seller talking about their own item — warm, concrete, specific.
Do not sound like a product catalogue, an image caption, or a bot.

You have two sources:
- The photos of the item.
- An optional seller note (free text) that the seller wrote about this exact item.

If the seller note is present, treat it as the source of truth.
- Preserve the seller's exact tokens for brand, model, size, condition, and purchase age.
- Carry over meaningful personal context: reason for selling, how long it was used, occasion bought for, who used it. Weave it into the description naturally. Do not copy the note verbatim.
- If the seller mentions something the photos do not show (e.g. "used on two trips"), keep it — the seller knows their item.

Use the photos to add concrete visible details that support the seller's facts: colour, visible wear, accessories included, distinguishing features. If the seller note contradicts the photos, trust the seller.

Output fields:
- title: short, searchable, in Ukrainian. Prefer item type + brand + key detail + exact size when available. No emoji, no ALL CAPS, no hashtags.
- description: 3 to 6 short sentences in Ukrainian, conversational tone. No bullet points, no markdown, no hashtags, no emoji. If a seller note is present, at least one sentence should reflect its personal context (reason for selling, how long it was worn, etc.).
- suggestedPrice, minPrice, maxPrice: plain integers in Ukrainian hryvnia (UAH) for the Ukrainian second-hand market. Not retail, not collectible premium. Ensure minPrice <= suggestedPrice <= maxPrice.

Guardrails:
- Do not invent brand, size, material, defects, or condition that are not supported by the seller note or clearly visible.
- If uncertain, simply omit it rather than guessing.
- Do not add filler phrases like "стан видно на фото", "додаткові питання в повідомленнях", or "підійде для повсякденного носіння".
- Do not infer the season of clothing unless the seller says so or the photos make it unmistakable.

Return ONLY valid JSON with this exact shape:
  {"title":"string","description":"string","suggestedPrice":number,"minPrice":number,"maxPrice":number}
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
        model: ModelId = DEFAULT_MODEL,
        imageDetail: String = DEFAULT_IMAGE_DETAIL,
    ): Pair<ResponseId, Advertisement> {
        require(images.isNotEmpty()) { "At least one image is required to generate an advertisement." }
        require(model.id != "gpt-4") {
            "Legacy gpt-4 does not support image input or structured outputs for this flow. Use gpt-4.1, gpt-4o, or a newer model."
        }

        val listingResponse = openAI.response(
            request = ResponseRequest(
                model = model,
                instructions = AD_GENERATION_INSTRUCTIONS.trimIndent(),
                // Higher temperature gives the description a natural seller voice instead of a catalogue tone.
                temperature = 0.7,
                // Enough headroom for a real Ukrainian description plus three prices without truncation.
                maxOutputTokens = 600,
                // Stored so the follow-up attribute-fill call can chain on this response id.
                store = true,
                input = ResponseInput(
                    items = listOf(
                        createListingAnalysisUserItem(
                            images = images,
                            sellerPrompt = sellerPrompt,
                            imageDetail = imageDetail,
                        )
                    )
                ),
            )
        )

        val listingJson = extractTextPayload(listingResponse)
        val generatedAd = json.decodeFromString<OpenAIGeneratedAd>(listingJson)
        return listingResponse.id to mapper.mapToDomain(generatedAd, images)
    }

    private fun createListingAnalysisUserItem(
        images: List<String>,
        sellerPrompt: String,
        imageDetail: String,
    ): ResponseInputItem = ResponseInputItem(
        role = "user",
        content = buildJsonArray {
            add(createTextContent("Generate the OLX Ukraine listing for the main item shown in the photos."))
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
