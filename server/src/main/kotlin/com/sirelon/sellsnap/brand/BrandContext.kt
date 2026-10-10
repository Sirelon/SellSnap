package com.sirelon.sellsnap.brand

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The languages the agent writes in. */
val BRAND_LANGUAGES: List<String> = listOf("en", "uk", "pl")

/**
 * What a drafting prompt knows about SellSnap, per language: [voiceSamples] from the app's own strings,
 * the [features] the product really has (the only claims a post may make), and the one-sentence
 * [independence] statement about OLX. Generated at build time by the `generateBrandContext` Gradle task.
 */
@Serializable
class BrandContext(
    val voiceSamples: Map<String, List<VoiceSample>>,
    val features: Map<String, List<Feature>>,
    val independence: Map<String, String>,
) {
    fun voiceSamplesIn(language: String): List<VoiceSample> = voiceSamples.getValue(language)

    fun featuresIn(language: String): List<Feature> = features.getValue(language)

    companion object {
        private const val RESOURCE = "/brand-context.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BrandContext = json.decodeFromString(text)

        /** Reads the generated file from the classpath; a missing file is a build error and fails fast. */
        fun load(): BrandContext {
            val stream = checkNotNull(BrandContext::class.java.getResourceAsStream(RESOURCE)) {
                "brand-context.json is missing; run the generateBrandContext Gradle task"
            }
            return parse(stream.use { it.readBytes().decodeToString() })
        }
    }
}

/** A string from `strings.xml` under its resource [key]. */
@Serializable
class VoiceSample(val key: String, val text: String)

/** One "Why SellSnap" bullet; [id] is the bullet's position (`f1`, `f2`, ...) and is the same in every language. */
@Serializable
class Feature(val id: String, val text: String)
