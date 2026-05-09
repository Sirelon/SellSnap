package com.sirelon.sellsnap.designsystem

fun formatPrice(value: Float): String {
    val intValue = value.toLong().coerceAtLeast(0L)
    return intValue.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}
