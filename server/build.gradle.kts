plugins {
    alias(libs.plugins.kotlinJvm)
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

dependencies {
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
