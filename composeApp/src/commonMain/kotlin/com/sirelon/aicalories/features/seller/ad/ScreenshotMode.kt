package com.sirelon.sellsnap.features.seller.ad

// Set to true for screenshot testing — never commit as true.
// Enables:
//  - GenerateAd seeds the 3 bundled test photos on open, so flows never drive the OS
//    photo picker (see ScreenshotPhotos.kt)
//  - PreviewAdScreen bypasses the publish confirmation and navigates straight back
//    to GenerateAd
// AI generation is NOT faked: every run makes real OpenAI calls.
internal var screenshotMode = false
