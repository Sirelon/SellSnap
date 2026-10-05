package com.sirelon.sellsnap

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Play Integrity attests that the request comes from the Play-signed SellSnap on a genuine
 * device; the OpenAI proxy (`functions/src/index.ts`) rejects everything else.
 */
fun installAppCheckProviderFactory() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
