package com.sirelon.sellsnap

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds run on emulators, where Play Integrity cannot attest anything. The debug provider
 * uses the fixed token from local.properties when one is configured (AppCheckDebugSecretRegistrar),
 * otherwise it mints a random one and logs it under its class name on first launch. Either way
 * the token must be registered in App Check (`appcheck-debug-token` skill) or the OpenAI proxy
 * answers 401.
 */
fun installAppCheckProviderFactory() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
