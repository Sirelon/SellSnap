package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation

val DigitOnlyInputTransformation = InputTransformation {
    val text = asCharSequence().toString()
    val digitsOnly = text.filter { it.isDigit() }
    if (digitsOnly != text) {
        replace(0, length, digitsOnly)
    }
}

/**
 * Groups digits by three with a space: "9500" shows as "9 500".
 *
 * Inserts each space (an empty-range replace) rather than replacing the whole text: Compose maps
 * the cursor through an insert exactly, but snaps a cursor inside a replaced range to one of its
 * ends, which left the cursor away from the last digit while typing.
 */
val ThousandSeparatorOutputTransformation = OutputTransformation {
    var index = length - 3
    while (index > 0) {
        replace(index, index, " ")
        index -= 3
    }
}
