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
