package com.sirelon.sellsnap.designsystem

fun formatPrice(value: Float): String = formatPrice(value.toLong())

/** [value] grouped by thousands with a space: `1850` as `1 850`. A negative value reads as 0. */
fun formatPrice(value: Long): String =
    value.coerceAtLeast(0L)
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
