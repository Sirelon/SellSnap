
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val sharedOptInAnnotations = listOf(
    "io.github.jan.supabase.annotations.SupabaseExperimental",
    "io.github.jan.supabase.annotations.SupabaseInternal",
    "kotlin.uuid.ExperimentalUuidApi",
)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildKonfig)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

fun resolveSecret(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        providers.gradleProperty(key).orNull
            ?: providers.environmentVariable(key).orNull
            ?: localProperties.getProperty(key)
    }

val openAIKey =
    resolveSecret("OPENAI_KEY", "openai.key")
        ?: "TODO"

val supabaseUrl =
    resolveSecret("SUPABASE_URL", "supabase.url")
        ?: "https://example.supabase.co"

val supabaseKey =
    resolveSecret("SUPABASE_KEY", "supabase.key")
        ?: "public-anon-key"

val defaultEmail =
    resolveSecret("SUPABASE_DEFAULT_EMAIL", "supabase.default.email")
        ?: "test@sirelon.org"

val defaultPassword =
    resolveSecret("SUPABASE_DEFAULT_PASSWORD", "supabase.default.password")
        ?: "testMe"

val olxClientId =
    resolveSecret("OLX_CLIENT_ID", "olx.client.id")
        ?: "olx-client-id"

val olxClientSecret =
    resolveSecret("OLX_CLIENT_SECRET", "olx.client.secret")
        ?: "olx-client-secret"

val olxScope =
    resolveSecret("OLX_SCOPE", "olx.scope")
        ?: "v2 read write"

val olxAuthBaseUrl =
    resolveSecret("OLX_AUTH_BASE_URL", "olx.auth.base.url")
        ?: "https://www.olx.ua/oauth/authorize"

val olxRedirectUri =
    resolveSecret("OLX_REDIRECT_URI", "olx.redirect.uri")
        ?: "selolxai://olx-auth/callback"

kotlin {
    androidLibrary {
        namespace = "com.sirelon.sellsnap.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    iosArm64()
    iosSimulatorArm64()
    
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    sourceSets {
        all {
            languageSettings.apply {
                sharedOptInAnnotations.forEach { optIn(it) }
            }
        }
        val iosMain by creating {
            dependsOn(getByName("commonMain"))
        }
        val iosTest by creating {
            dependsOn(getByName("commonTest"))
        }
        commonMain.dependencies {
            implementation(libs.supabase.auth)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.functions)
            implementation(libs.supabase.realtime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("iosArm64Test").dependsOn(iosTest)
        getByName("iosSimulatorArm64Test").dependsOn(iosTest)
    }
}

buildkonfig {
    packageName = "com.sirelon.sellsnap.supabase"
    exposeObjectWithName = "SupabaseConfig"

    defaultConfigs {
        buildConfigField(STRING, "SUPABASE_URL", supabaseUrl)
        buildConfigField(STRING, "OPENAI_KEY", openAIKey)
        buildConfigField(STRING, "SUPABASE_KEY", supabaseKey)
        buildConfigField(STRING, "SUPABASE_DEFAULT_EMAIL", defaultEmail)
        buildConfigField(STRING, "SUPABASE_DEFAULT_PASSWORD", defaultPassword)
        buildConfigField(STRING, "OLX_CLIENT_ID", olxClientId)
        buildConfigField(STRING, "OLX_CLIENT_SECRET", olxClientSecret)
        buildConfigField(STRING, "OLX_SCOPE", olxScope)
        buildConfigField(STRING, "OLX_AUTH_BASE_URL", olxAuthBaseUrl)
        buildConfigField(STRING, "OLX_REDIRECT_URI", olxRedirectUri)
    }
}
