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

val ThousandSeparatorOutputTransformation = OutputTransformation {
    val text = toString()
    if (text.isEmpty()) return@OutputTransformation
    val formatted = text.reversed().chunked(3).joinToString(" ").reversed()
    if (formatted != text) {
        replace(0, length, formatted)
    }
}
