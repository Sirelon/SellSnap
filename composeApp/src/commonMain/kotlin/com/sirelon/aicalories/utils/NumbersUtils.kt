package com.sirelon.sellsnap.utils

import kotlin.math.round

fun Double?.normalizePercentage(): Double {
    this ?: return 0.0
    val percentage = if (this <= 1.0) this * 100 else this
    return percentage.coerceIn(0.0, 100.0)
}

fun Double.roundToDecimals(decimals: Int = 1): Double {
    if (decimals <= 0) {
        return round(this).toInt().toDouble()
    }
    val factor = (1..decimals).fold(1.0) { acc, _ -> acc * 10.0 }
    val rounded = round(this * factor) / factor
    return if (rounded % 1.0 == 0.0) {
        rounded
    } else {
        rounded
    }
}
