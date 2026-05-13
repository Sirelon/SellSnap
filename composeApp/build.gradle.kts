
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val composeOptInAnnotations = listOf(
    "androidx.compose.foundation.layout.ExperimentalLayoutApi",
    "androidx.compose.material3.ExperimentalMaterial3Api",
    "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
    "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
    "androidx.compose.ui.ExperimentalComposeUiApi",
    "com.mohamedrejeb.calf.core.InternalCalfApi",
    "com.mohamedrejeb.calf.permissions.ExperimentalPermissionsApi",
    "kotlin.time.ExperimentalTime",
    "kotlin.uuid.ExperimentalUuidApi",
    "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "kotlinx.cinterop.ExperimentalForeignApi",
)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

compose {
    resources {
        publicResClass = true
        packageOfResClass = "com.sirelon.sellsnap.generated.resources"
        generateResClass = always
    }
}

if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

kotlin {
    android {
        namespace = "com.sirelon.sellsnap.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
            jvmTarget.set(JvmTarget.JVM_11)
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOptions["bundleId"] = "com.sirelon.sellsnap"
        }
    }
    
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        commonMain {
            // No need to explicitly add this, it's the default
        }
        all {
            languageSettings.apply {
                composeOptInAnnotations.forEach { optIn(it) }
            }
        }
        val dataStoreMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.datastore.preferences)
            }
        }
        val jsWasmMain by creating {
            dependsOn(getByName("commonMain"))
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        val iosMain by creating {
            dependsOn(dataStoreMain)
        }

        jvmMain.get().dependsOn(dataStoreMain)
        androidMain.get().dependsOn(dataStoreMain)
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.play.services.location)
        }
        commonMain.dependencies {
            implementation(libs.supabase.compose.auth)
            implementation(libs.supabase.compose.auth.ui)
            implementation(libs.supabase.coil3.integration)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            api(libs.compose.components.resources)
            implementation(libs.compose.preview)
            implementation(libs.material3)
            implementation(libs.material3.adaptive)
            implementation(libs.material3.adaptive.navigation.suite)
            implementation(libs.material.icons.extended)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelSavedState)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.navigation3)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(projects.shared)
            implementation(libs.androidx.navigation3.runtime)

            implementation(libs.navigation3.ui)
//
//            implementation(libs.androidx.navigation3.ui)
//            implementation(libs.androidx.navigation3.viewmodel)

            implementation(libs.calf.filepicker)
            implementation(libs.calf.filepicker.coil)
            implementation(libs.calf.permissions)
            implementation(libs.calf.permissions.location)
            implementation(libs.calf.permissions.microphone)
            implementation("com.aallam.openai:openai-client:4.1.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.java)
            implementation(libs.kotlinx.coroutines.swing)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("jsMain").dependsOn(jsWasmMain)
        getByName("wasmJsMain").dependsOn(jsWasmMain)
    }
}

compose.desktop {
    application {
        mainClass = "com.sirelon.sellsnap.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.sirelon.sellsnap"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.png"))
            }
        }
    }
}
