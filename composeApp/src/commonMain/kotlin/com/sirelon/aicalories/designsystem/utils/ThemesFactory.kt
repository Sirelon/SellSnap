package com.sirelon.sellsnap.designsystem.utils

import androidx.compose.ui.graphics.Color
import com.sirelon.sellsnap.designsystem.AppColors

object ThemesFactory {

    // SellSnap — Teal scheme (the August 2026 "3A / Expressive" redesign).
    // Values lifted 1:1 from the design system's tokens/colors.css.

    fun light(): AppColors = AppColors(
        primary = Color(0xFF0F766E),            // --brand-primary (teal-60)
        primaryBright = Color(0xFF0F766E),       // no gradient in the redesign; same as primary
        onPrimary = Color(0xFFFFFFFF),           // --brand-on-primary
        background = Color(0xFFF5FBFA),          // --surface-background (teal-05)
        onBackground = Color(0xFF10201E),        // --text-primary (ink-100)
        surface = Color(0xFFF5FBFA),
        onSurface = Color(0xFF10201E),
        surfaceVariant = Color(0xFFCCE8E4),      // --surface-container-high (teal-20)
        surfaceLowest = Color(0xFFFFFFFF),       // --surface-field
        surfaceLow = Color(0xFFE6F2F0),          // --surface-container (teal-10)
        surfaceHigh = Color(0xFFCCE8E4),         // --surface-container-high (teal-20)
        secondaryContainer = Color(0xFFCCE8E4),  // --brand-container
        onSecondaryContainer = Color(0xFF0B4F4A), // --brand-on-container (teal-70)
        outline = Color(0x1410201E),             // --border-subtle rgba(16,32,30,0.08)
        outlineVariant = Color(0x1410201E),
        error = Color(0xFFBA1A1A),               // --status-error (unchanged)
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF1B8E5A),             // --status-success (unchanged)
        warning = Color(0xFFD97706),             // --status-warning (unchanged)
        warningVariant = Color(0xFFFBBF24),
        onSurfaceMuted = Color(0xFF4E635F),      // --text-muted (ink-60)
        onSurfaceSoft = Color(0xFF86A09B),       // --text-placeholder (ink-40)
    )

    fun dark(): AppColors = AppColors(
        primary = Color(0xFF5ED3C4),             // --brand-primary
        primaryBright = Color(0xFF5ED3C4),
        onPrimary = Color(0xFF06322D),           // --brand-on-primary
        background = Color(0xFF0D1615),          // --surface-background
        onBackground = Color(0xFFDDEDEA),        // --text-primary
        surface = Color(0xFF0D1615),
        onSurface = Color(0xFFDDEDEA),
        surfaceVariant = Color(0xFF17403B),      // --surface-container-high
        surfaceLowest = Color(0xFF1B2A28),       // --surface-field
        surfaceLow = Color(0xFF152220),          // --surface-container
        surfaceHigh = Color(0xFF17403B),         // --surface-container-high
        secondaryContainer = Color(0xFF17403B),  // --brand-container
        onSecondaryContainer = Color(0xFF7FE3D5), // --brand-on-container
        outline = Color(0x1ADDEDEA),             // --border-subtle rgba(221,237,234,0.10)
        outlineVariant = Color(0x1ADDEDEA),
        error = Color(0xFFFFB4AB),               // --status-error
        onError = Color(0xFF680003),
        success = Color(0xFF4FD28A),             // --status-success
        warning = Color(0xFFF59E0B),             // --status-warning
        warningVariant = Color(0xFFFBBF24),
        onSurfaceMuted = Color(0xFF93AAA6),      // --text-muted
        onSurfaceSoft = Color(0xFF6C8480),       // --text-placeholder
    )
}
