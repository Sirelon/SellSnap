package com.sirelon.sellsnap.features.seller.ad

import com.sirelon.sellsnap.features.seller.ad.preview_ad.PublishTargetAccount

/**
 * Placeholder identity rendered on the "Publish to" row under [screenshotMode] - never a real
 * seller's name/email in a store screenshot (SIR-83 F6/A7). Hardcoded on purpose: it must never
 * resolve to anything derived from a real connected account, regardless of locale.
 */
val ScreenshotPlaceholderAccount = PublishTargetAccount(
    name = "Alex Seller",
    avatarUrl = null,
    isBusiness = false,
)
