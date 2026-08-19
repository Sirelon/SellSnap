import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}

// Force patched versions of transitive npm dev-tooling deps (webpack-dev-server chain)
// flagged by Dependabot in kotlin-js-store/*.lock. These packages are build-time only
// (JS/Wasm dev server), not shipped to users, but we still pin to the fixed releases
// so the pinning survives future `kotlinUpgradeYarnLock` runs.
plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply {
        resolution("ws", "8.21.0")
        resolution("js-yaml", "4.3.1")
        resolution("glob", "10.5.0")
        resolution("node-forge", "1.4.0")
        resolution("qs", "6.15.2")
        resolution("diff", "8.0.3")
        resolution("lodash", "4.18.0")
        resolution("webpack", "5.104.1")
        resolution("ajv", "8.18.0")
        resolution("minimatch", "9.0.7")
        resolution("serialize-javascript", "7.0.5")
        resolution("flatted", "3.4.2")
        resolution("socket.io-parser", "4.2.7")
        resolution("picomatch", "2.3.2")
        resolution("brace-expansion", "1.1.17")
        resolution("path-to-regexp", "0.1.13")
        resolution("follow-redirects", "1.16.0")
        resolution("webpack-dev-server", "5.2.6")
        resolution("uuid", "11.1.1")
        resolution("tmp", "0.2.6")
        resolution("shell-quote", "1.9.0")
        resolution("launch-editor", "2.14.1")
        resolution("http-proxy-middleware", "2.0.10")
        resolution("websocket-driver", "0.7.5")
        resolution("engine.io", "6.6.7")
        resolution("fast-uri", "3.1.5")
        resolution("body-parser", "1.20.6")
    }
}

plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootExtension>().apply {
        resolution("ws", "8.21.0")
    }
}
