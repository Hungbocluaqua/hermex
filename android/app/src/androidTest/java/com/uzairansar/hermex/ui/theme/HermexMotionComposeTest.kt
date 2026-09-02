package com.uzairansar.hermex.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HermexMotionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manualClockCoversInitialIntermediateCompletedAndCancelledMotion() {
        val target = mutableFloatStateOf(0f)
        val observed = AtomicReference(0f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MotionProbe(target.floatValue, HermexMotionPolicy(1f), observed::set)
        }

        composeRule.runOnIdle { assertEquals(0f, observed.get(), 0.001f) }
        composeRule.runOnIdle { target.floatValue = 1f }
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.runOnIdle {
            assertTrue("Animation should be in flight", observed.get() > 0f && observed.get() < 1f)
            target.floatValue = 0f
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle { assertEquals(0f, observed.get(), 0.001f) }
    }

    @Test
    fun reducedMotionSnapsWithoutScaleOrMovement() {
        val target = mutableFloatStateOf(0f)
        val observed = AtomicReference(0f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MotionProbe(target.floatValue, HermexMotionPolicy(0f), observed::set)
        }

        composeRule.runOnIdle { target.floatValue = 1f }
        // A snap still commits on Compose's next frame, but never exposes an intermediate value.
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.runOnIdle { assertEquals(1f, observed.get(), 0.001f) }
    }
}

@Composable
private fun MotionProbe(
    target: Float,
    policy: HermexMotionPolicy,
    onValue: (Float) -> Unit,
) {
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = policy.tweenOrSnap(160),
        label = "motion-test-probe",
    )
    SideEffect { onValue(value) }
    Text(value.toString(), modifier = Modifier.testTag("motion_test_probe"))
}
