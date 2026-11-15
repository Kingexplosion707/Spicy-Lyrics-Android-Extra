package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.animation.ElementState

internal sealed interface LyricPaintPlan {
    data object ActiveGradient : LyricPaintPlan
    data class InactiveShadow(val alpha: Float, val blurRadius: Float) : LyricPaintPlan
}

internal fun lyricPaintPlan(
    state: ElementState,
    isBackground: Boolean,
    opacity: Float,
    blurRadius: Float,
    config: RenderConfig,
    /** The NotSung colour: a word's `--gradient-alpha-end`, or a whole line's in line-synced lyrics. */
    dimAlpha: Float = config.gradientAlphaDim,
    /** Bloom: how lit the line still is (LineAnimState.lit). */
    lit: Float = 0f,
): LyricPaintPlan {
    if (state == ElementState.Active) return LyricPaintPlan.ActiveGradient
    val stateAlpha = when {
        // Bloom rests every line at one dim tone, sung or not; a sung line dims back to it.
        config.isBloom -> {
            val dim = if (isBackground) config.backgroundAlphaDim else dimAlpha
            val bright = if (isBackground) config.backgroundAlphaBright else config.gradientAlphaBright
            dim + (bright - dim) * lit
        }
        isBackground && state == ElementState.NotSung -> config.backgroundAlphaDim
        isBackground -> config.backgroundAlphaBright
        state == ElementState.NotSung -> dimAlpha
        else -> config.gradientAlphaBright
    }
    return LyricPaintPlan.InactiveShadow(
        alpha = (stateAlpha * opacity).coerceIn(0f, 1f),
        blurRadius = blurRadius.coerceAtLeast(0f),
    )
}
