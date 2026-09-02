package com.uzairansar.hermex.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@Immutable
internal data class HermexMotionScheme(
    val pressMillis: Int = 160,
    val quickStateMillis: Int = 160,
    val disclosureMillis: Int = 180,
    val composerMillis: Int = 220,
    val listMutationMillis: Int = 240,
    val scrollFollowMillis: Int = 150,
    val typingPulseMillis: Int = 900,
    val streamingRevealMillis: Int = 350,
    val streamingGraphemeStaggerMillis: Int = 12,
    val streamingMaximumLeadMillis: Int = 450,
)

@Immutable
internal data class HermexMotionPolicy(val animatorScale: Float = 1f) {
    val animationsEnabled: Boolean get() = animatorScale > 0f
    val continuousMotionEnabled: Boolean get() = animationsEnabled
    val streamingRevealEnabled: Boolean get() = animationsEnabled

    fun scaledMillis(milliseconds: Int): Int = when {
        !animationsEnabled -> 0
        animatorScale == 1f -> milliseconds
        else -> (milliseconds * animatorScale).roundToInt().coerceAtLeast(1)
    }
}

internal val LocalHermexMotionScheme = staticCompositionLocalOf { HermexMotionScheme() }
internal val LocalHermexMotionPolicy = staticCompositionLocalOf { HermexMotionPolicy() }

@Composable
internal fun rememberHermexMotionPolicy(): HermexMotionPolicy {
    val context = LocalContext.current
    var scale by remember(context) {
        mutableFloatStateOf(readAnimatorScale(context.contentResolver))
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context.contentResolver)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }.isSuccess
        onDispose {
            if (registered) {
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }
    return remember(scale) { HermexMotionPolicy(scale.coerceAtLeast(0f)) }
}

private fun readAnimatorScale(contentResolver: android.content.ContentResolver): Float = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)

internal fun <T> HermexMotionPolicy.tweenOrSnap(
    milliseconds: Int,
): FiniteAnimationSpec<T> = if (animationsEnabled) {
    tween(durationMillis = scaledMillis(milliseconds))
} else {
    snap()
}

internal fun <T> HermexMotionPolicy.springOrSnap(
    stiffness: Float = Spring.StiffnessMedium,
): AnimationSpec<T> = if (animationsEnabled) {
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = stiffness,
    )
} else {
    snap()
}
