package com.sirelon.sellsnap

import com.google.firebase.appcheck.debug.InternalDebugSecretProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar

/**
 * Feeds the debug App Check provider a fixed secret instead of the random one it would otherwise
 * mint and store in app data. The provider asks for an optional [InternalDebugSecretProvider]
 * component, the hook Firebase's own debug-testing artifact uses; this registrar supplies one
 * backed by `APP_CHECK_DEBUG_TOKEN` from local.properties (see androidApp/build.gradle.kts).
 * With no token configured it returns null and the provider falls back to its random one.
 */
class AppCheckDebugSecretRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> = listOf(
        Component.builder(InternalDebugSecretProvider::class.java)
            .factory { FixedDebugSecretProvider() }
            .build(),
    )
}

private class FixedDebugSecretProvider : InternalDebugSecretProvider {
    override fun getDebugSecret(): String? = BuildConfig.APP_CHECK_DEBUG_TOKEN.ifBlank { null }
}
