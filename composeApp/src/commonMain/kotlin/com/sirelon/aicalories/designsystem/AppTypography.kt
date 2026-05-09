package com.sirelon.sellsnap.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.inter_variable
import com.sirelon.sellsnap.generated.resources.manrope_variable
import org.jetbrains.compose.resources.Font

val ManropeFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.manrope_variable, weight = FontWeight.Medium),
        Font(Res.font.manrope_variable, weight = FontWeight.SemiBold),
    )

val InterFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.inter_variable, weight = FontWeight.Normal),
        Font(Res.font.inter_variable, weight = FontWeight.Medium),
    )

@Stable
data class AppTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val subTitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

fun appTypography(
    displayFontFamily: FontFamily = FontFamily.SansSerif,
    bodyFontFamily: FontFamily = FontFamily.SansSerif,
): AppTypography {
    val display = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp,
    )
    val headline = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    )
    val title = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )
    val subTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )
    val body = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )
    val label = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )
    val caption = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    )

    return AppTypography(
        display = display,
        headline = headline,
        title = title,
        subTitle = subTitle,
        body = body,
        label = label,
        caption = caption,
    )
}

internal fun AppTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = headline,
    displaySmall = headline,
    headlineLarge = headline,
    headlineMedium = title.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = title,
    titleLarge = title,
    titleMedium = body.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = label,
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = label.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = caption,
)
