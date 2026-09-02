package com.uzairansar.hermex.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

internal enum class HermexHapticEvent {
    Tap,
    Confirm,
    Success,
    Warning,
    Cancel,
}

internal fun View.performHermexHaptic(event: HermexHapticEvent, enabled: Boolean = true) {
    if (!enabled) return
    performHapticFeedback(hermexHapticConstant(event, Build.VERSION.SDK_INT))
}

internal fun hermexHapticConstant(event: HermexHapticEvent, sdkInt: Int): Int = when (event) {
    HermexHapticEvent.Tap -> HapticFeedbackConstants.CLOCK_TICK
    HermexHapticEvent.Confirm -> HapticFeedbackConstants.KEYBOARD_TAP
    HermexHapticEvent.Success -> if (sdkInt >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    HermexHapticEvent.Warning -> if (sdkInt >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    HermexHapticEvent.Cancel -> HapticFeedbackConstants.LONG_PRESS
}
