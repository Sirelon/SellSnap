package com.sirelon.sellsnap.designsystem

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal fun HapticFeedback.performStepFeedback() {
    performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

internal fun HapticFeedback.performSuccessFeedback() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}
