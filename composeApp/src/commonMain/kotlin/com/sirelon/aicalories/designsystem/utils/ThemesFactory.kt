package com.sirelon.sellsnap.designsystem.utils

import androidx.compose.ui.graphics.Color
import com.sirelon.sellsnap.designsystem.AppColors

object ThemesFactory {

    fun light(): AppColors = AppColors(
        // Brand: vibrant orange centered around #E67E22, gradient from darker to lighter
        primary = Color(0xFFD0600A),           // rich dark orange (gradient start)
        primaryBright = Color(0xFFF08030),     // vibrant orange (gradient end)
        onPrimary = Color(0xFFFFFFFF),          // white for max contrast on orange
        background = Color(0xFFFFF8F2),         // warm white, barely tinted
        onBackground = Color(0xFF3A1F00),       // deep warm brown, readable
        surface = Color(0xFFFFF8F2),
        onSurface = Color(0xFF3A1F00),
        surfaceVariant = Color(0xFFFFD5AF),     // Fluid Highlight pill
        surfaceLowest = Color(0xFFFFFFFF),
        surfaceLow = Color(0xFFFFF0E0),
        surfaceHigh = Color(0xFFFFD8B0),
        secondaryContainer = Color(0xFFFFBF85),  // Cognitive Chip bg
        onSecondaryContainer = Color(0xFF5C2E00), // Cognitive Chip text
        outline = Color(0xFFD9A070),
        outlineVariant = Color(0xFFD9A070),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        success = Color(0xFF1B8E5A),
        warning = Color(0xFFD97706),
        warningVariant = Color(0xFFFBBF24),
        onSurfaceMuted = Color(0xFFAA7040),
        onSurfaceSoft = Color(0xFF7A5030),
    )

    fun dark(): AppColors = AppColors(
        primary = Color(0xFFF08030),            // vibrant orange for dark bg
        primaryBright = Color(0xFFD0600A),      // gradient endpoint (dark theme inverts direction)
        onPrimary = Color(0xFF1A0800),          // deep brown
        background = Color(0xFF1E0D00),         // deep chocolate (not pure black)
        onBackground = Color(0xFFFFEDD8),       // warm cream
        surface = Color(0xFF2A1400),
        onSurface = Color(0xFFFFEDD8),
        surfaceVariant = Color(0xFF4A2800),
        surfaceLowest = Color(0xFF160600),
        surfaceLow = Color(0xFF2A1400),
        surfaceHigh = Color(0xFF472400),
        secondaryContainer = Color(0xFF5C2E00),
        onSecondaryContainer = Color(0xFFFFBF85),
        outline = Color(0xFF7A5030),
        outlineVariant = Color(0xFF7A5030),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF680003),
        success = Color(0xFF4FD28A),
        warning = Color(0xFFF59E0B),
        warningVariant = Color(0xFFFBBF24),
        onSurfaceMuted = Color(0xFFCCA070),
        onSurfaceSoft = Color(0xFFE0B890),
    )
}
