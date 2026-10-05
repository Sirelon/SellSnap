package com.sirelon.sellsnap

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds run on emulators, where Play Integrity cannot attest anything. The debug provider
 * prints `Enter this debug secret into the allow list in the Firebase Console for your project:
 * <token>` in logcat on first launch; register that token under Firebase console > App Check >
 * Apps > SellSnap Android > Manage debug tokens, or the OpenAI proxy answers 401.
 */
fun installAppCheckProviderFactory() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
