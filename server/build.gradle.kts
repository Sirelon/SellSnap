plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.sirelon.sellsnap"
version = "1.0.0"
application {
    mainClass.set("com.sirelon.sellsnap.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=$isDevelopment",
        "-XX:MaxRAMPercentage=75",
    )
}

kotlin {
    jvmToolchain(21)
}

// Seeds accounts and tips into Firestore with Application Default Credentials.
tasks.register<JavaExec>("loadSeed") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.sirelon.sellsnap.seed.SeedLoaderKt")
}

// The brand context the drafter prompts with: voice samples from the app strings, the feature list and
// the independence line from the store listing. It reads exactly these four files, read-only, and
// names each language folder; it never scans values-*. Output: brand-context.json on the classpath root.
val brandVoiceLanguages = listOf("en", "uk", "pl")
val brandStringFiles = mapOf(
    "en" to "composeApp/src/commonMain/composeResources/values/strings.xml",
    "uk" to "composeApp/src/commonMain/composeResources/values-uk/strings.xml",
    "pl" to "composeApp/src/commonMain/composeResources/values-pl/strings.xml",
)
val brandListingFile = "store/copy/store-listing.md"

// Playful onboarding, empty-state and photo-tip strings; "go live" wording stays out.
val brandVoiceKeys = listOf(
    "welcome_to_sellsnap",
    "welcome_subtitle",
    "turn_stuff_into_olx_listings",
    "snap_photo_ad_desc",
    "onboarding_step1_title",
    "onboarding_step1_subtitle",
    "onboarding_step2_title",
    "onboarding_step2_subtitle",
    "onboarding_step3_title",
    "onboarding_step3_subtitle",
    "my_ads_empty_title",
    "my_ads_empty_description",
    "ai_processing_tip_good_lighting",
    "ai_processing_tip_keep_background_clean",
    "ai_processing_tip_honesty_sells",
    "ai_processing_tip_reply_fast",
    "ai_processing_tip_wipe_item",
    "ai_processing_tip_daylight",
    "ai_processing_tip_scale_reference",
    "ai_processing_tip_capture_details",
)

// Section header and "WHY SELLSNAP" bullet-block header of each language in store-listing.md.
val brandListingSections = mapOf(
    "en" to Pair("## 1. English", "WHY SELLSNAP"),
    "uk" to Pair("## 2. Ukrainian", "ЧОМУ SELLSNAP"),
    "pl" to Pair("## 3. Polish", "DLACZEGO SELLSNAP"),
)

val generateBrandContext = tasks.register("generateBrandContext") {
    group = "build"
    description = "Writes brand-context.json from the app strings and the store listing."
    val outputDir = layout.buildDirectory.dir("generated/brand")
    val stringInputs = brandStringFiles.mapValues { (_, path) -> rootProject.file(path) }
    val listingInput = rootProject.file(brandListingFile)
    inputs.files(stringInputs.values, listingInput)
    outputs.dir(outputDir)

    doLast {
        fun unescapeAndroid(raw: String): String =
            Regex("""\\(u[0-9a-fA-F]{4}|.)""").replace(raw) { match ->
                val code = match.groupValues[1]
                when {
                    code.startsWith("u") && code.length == 5 -> code.substring(1).toInt(16).toChar().toString()
                    code == "n" -> "\n"
                    code == "t" -> "\t"
                    else -> code
                }
            }

        fun readStrings(file: File): Map<String, String> {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            return (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to unescapeAndroid(node.textContent.trim())
            }
        }

        fun jsonString(value: String): String = buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

        val listing = listingInput.readLines()

        // The first fenced block after the section's "**Description:**" line.
        fun descriptionBlock(language: String): List<String> {
            val header = brandListingSections.getValue(language).first
            val start = listing.indexOfFirst { it.startsWith(header) }
            if (start < 0) throw GradleException("store-listing.md has no section '$header'")
            val end = listing.drop(start + 1).indexOfFirst { it.startsWith("## ") }.let { if (it < 0) listing.size else start + 1 + it }
            val section = listing.subList(start, end)
            val open = section.indexOfFirst { it.trim() == "**Description:**" }
                .let { d -> if (d < 0) -1 else section.drop(d).indexOfFirst { it.startsWith("```") } + d }
            if (open < 0) throw GradleException("store-listing.md section '$header' has no description block")
            val close = section.drop(open + 1).indexOfFirst { it.startsWith("```") }
            if (close < 0) throw GradleException("store-listing.md section '$header' has an unclosed description block")
            return section.subList(open + 1, open + 1 + close)
        }

        val voice = brandVoiceLanguages.associateWith { language ->
            val strings = readStrings(stringInputs.getValue(language))
            brandVoiceKeys.map { key ->
                key to (strings[key] ?: throw GradleException("strings.xml ($language) has no key '$key'"))
            }
        }

        val features = brandVoiceLanguages.associateWith { language ->
            val block = descriptionBlock(language)
            val whyHeader = brandListingSections.getValue(language).second
            val at = block.indexOfFirst { it.trim() == whyHeader }
            if (at < 0) throw GradleException("store-listing.md ($language) has no '$whyHeader' block")
            val bullets = block.drop(at + 1).takeWhile { it.startsWith("• ") }.map { it.removePrefix("• ").trim() }
            if (bullets.isEmpty()) throw GradleException("store-listing.md ($language) has no feature bullets")
            bullets
        }

        val independence = brandVoiceLanguages.associateWith { language ->
            val last = descriptionBlock(language).last { it.isNotBlank() }
            val sentence = Regex("""^.*?\.(?=\s|$)""").find(last)?.value
            if (sentence == null || !sentence.startsWith("SellSnap")) {
                throw GradleException("store-listing.md ($language) does not end with the independence line")
            }
            sentence
        }

        val json = buildString {
            append("{\n  \"voiceSamples\": {\n")
            append(
                brandVoiceLanguages.joinToString(",\n") { language ->
                    "    ${jsonString(language)}: [\n" +
                        voice.getValue(language).joinToString(",\n") { (key, text) ->
                            "      {\"key\": ${jsonString(key)}, \"text\": ${jsonString(text)}}"
                        } + "\n    ]"
                },
            )
            append("\n  },\n  \"features\": {\n")
            append(
                brandVoiceLanguages.joinToString(",\n") { language ->
                    "    ${jsonString(language)}: [\n" +
                        features.getValue(language).mapIndexed { index, text ->
                            "      {\"id\": ${jsonString("f${index + 1}")}, \"text\": ${jsonString(text)}}"
                        }.joinToString(",\n") + "\n    ]"
                },
            )
            append("\n  },\n  \"independence\": {\n")
            append(
                brandVoiceLanguages.joinToString(",\n") { language ->
                    "    ${jsonString(language)}: ${jsonString(independence.getValue(language))}"
                },
            )
            append("\n  }\n}\n")
        }

        val target = outputDir.get().asFile
        target.mkdirs()
        File(target, "brand-context.json").writeText(json, Charsets.UTF_8)
    }
}

sourceSets.main {
    resources.srcDir(generateBrandContext)
}

dependencies {
    implementation(platform(libs.google.cloud.bom))
    implementation(libs.google.cloud.firestore)
    implementation(libs.google.cloud.storage)
    implementation(libs.google.auth.oauth2)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.testJunit)
}
