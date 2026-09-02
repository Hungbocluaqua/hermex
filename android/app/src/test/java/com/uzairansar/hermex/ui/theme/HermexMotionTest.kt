package com.uzairansar.hermex.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermexMotionTest {
    @Test
    fun zeroAnimatorScaleDisablesNonessentialMotion() {
        val policy = HermexMotionPolicy(animatorScale = 0f)

        assertFalse(policy.animationsEnabled)
        assertFalse(policy.continuousMotionEnabled)
        assertFalse(policy.streamingRevealEnabled)
        assertEquals(0, policy.scaledMillis(220))
    }

    @Test
    fun animatorScaleAdjustsCustomMotionTiming() {
        val policy = HermexMotionPolicy(animatorScale = 1.5f)

        assertTrue(policy.animationsEnabled)
        assertEquals(330, policy.scaledMillis(220))
    }

    @Test
    fun hapticEventsUseCompatibleFallbacks() {
        assertEquals(
            HapticFeedbackConstants.KEYBOARD_TAP,
            hermexHapticConstant(HermexHapticEvent.Success, Build.VERSION_CODES.Q),
        )
        assertEquals(
            HapticFeedbackConstants.CONFIRM,
            hermexHapticConstant(HermexHapticEvent.Success, Build.VERSION_CODES.R),
        )
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            hermexHapticConstant(HermexHapticEvent.Warning, Build.VERSION_CODES.Q),
        )
    }
}
