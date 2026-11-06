package com.tx24.spicyplayer.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.tx24.spicyplayer.ui.theme.SpicyMotion

/**
 * One screen of a pop-up giving way to another: [forward] slides the new one in from the right
 * (deeper), otherwise from the left (back). A quarter of the width and a fade, so it reads as a
 * step without sweeping across the pop-up. Without [animate], an instant swap.
 */
fun <S> AnimatedContentTransitionScope<S>.screenSlide(forward: Boolean, animate: Boolean): ContentTransform {
    if (!animate) return EnterTransition.None togetherWith ExitTransition.None
    val way = if (forward) 1 else -1
    return (slideInHorizontally(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) { it / 4 * way } +
        fadeIn(tween(SpicyMotion.MODAL_MS))) togetherWith
        (slideOutHorizontally(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) { -it / 4 * way } +
            fadeOut(tween(SpicyMotion.FAST_MS))) using SizeTransform(clip = false)
}
