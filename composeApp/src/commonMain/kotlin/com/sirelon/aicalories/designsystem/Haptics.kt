package com.sirelon.sellsnap.designsystem

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal fun HapticFeedback.performStepFeedback() {
    performHapticFeedback(HapticFeedbackType.ToggleOn)
}

internal fun HapticFeedback.performSuccessFeedback() {
    performHapticFeedback(HapticFeedbackType.Confirm)
}

internal fun HapticFeedback.performErrorFeedback() {
    performHapticFeedback(HapticFeedbackType.Reject)
}
