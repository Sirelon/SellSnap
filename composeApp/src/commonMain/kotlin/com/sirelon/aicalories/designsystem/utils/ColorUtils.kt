package com.sirelon.sellsnap.designsystem.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

fun generateRandomColor(hash: String? = null): Color {
    val h = if (hash == null) {
        (abs(hash.hashCode()) % 360).toFloat()
    } else {
        Random.nextFloat() * 360f
    }
    val s = 0.35f + Random.nextFloat() * 0.35f   // 0.35 - 0.7 → soft but still colorful
    val v = 0.75f + Random.nextFloat() * 0.2f    // 0.75 - 0.95 → not too pale

    return hsvToColor(h, s, v)
}

fun Color.opposite(): Color {
    val (h, _, _) = toHsv()

    val newH = (h + 180f) % 360f

    // Force pastel look
    val s = 0.25f
    val v = 0.9f

    return hsvToColor(newH, s, v, alpha)
}

fun Color.contrastColor(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.6f) Color.Black else Color.White
}

// --- Internal helpers ---

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue

    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min

    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta).mod(6f)
        max == g -> ((b - r) / delta) + 2f
        else -> ((r - g) / delta) + 4f
    } * 60f

    val hue = if (h < 0) h + 360f else h
    val s = if (max == 0f) 0f else delta / max
    val v = max

    return Triple(hue, s, v)
}

fun hsvToColor(
    h: Float,
    s: Float,
    v: Float,
    alpha: Float = 1f
): Color {
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = v - c

    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = r1 + m,
        green = g1 + m,
        blue = b1 + m,
        alpha = alpha
    )
}