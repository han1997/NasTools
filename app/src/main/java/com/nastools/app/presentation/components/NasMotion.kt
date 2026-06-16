package com.nastools.app.presentation.components

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

object NasMotion {
    const val Quick = 140
    const val Standard = 220
    const val Emphasis = 320
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

@Composable
fun rememberNasMotionEnabled(): Boolean {
    return remember {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
}

fun <T> nasMotionSpec(
    enabled: Boolean,
    durationMillis: Int = NasMotion.Standard
): FiniteAnimationSpec<T> {
    return if (enabled) {
        tween(durationMillis = durationMillis, easing = NasMotion.EaseOut)
    } else {
        snap()
    }
}

fun Modifier.nasAnimateContentSize(enabled: Boolean): Modifier {
    return if (enabled) {
        animateContentSize(animationSpec = nasMotionSpec<IntSize>(enabled, NasMotion.Standard))
    } else {
        this
    }
}
