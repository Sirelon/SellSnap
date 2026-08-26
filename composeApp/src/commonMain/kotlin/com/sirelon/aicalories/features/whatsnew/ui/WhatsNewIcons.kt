package com.sirelon.sellsnap.features.whatsnew.ui

import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_gift
import com.sirelon.sellsnap.generated.resources.ic_sparkles
import com.sirelon.sellsnap.generated.resources.ic_trending_up
import com.sirelon.sellsnap.generated.resources.ic_upload
import com.sirelon.sellsnap.generated.resources.ic_wand_sparkles
import com.sirelon.sellsnap.generated.resources.ic_wrench
import org.jetbrains.compose.resources.DrawableResource

// Content is authored remotely in Firestore, independent of app releases — an icon key a build
// doesn't recognize yet must fall back to a default rather than crash.
internal fun releaseChangeIcon(name: String): DrawableResource = when (name) {
    "feature" -> Res.drawable.ic_sparkles
    "improvement" -> Res.drawable.ic_trending_up
    "fix" -> Res.drawable.ic_wrench
    "gift" -> Res.drawable.ic_gift
    "upload" -> Res.drawable.ic_upload
    "magic" -> Res.drawable.ic_wand_sparkles
    else -> Res.drawable.ic_sparkles
}
