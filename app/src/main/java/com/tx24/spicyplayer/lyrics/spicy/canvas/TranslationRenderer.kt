package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.RomanizationHighlight
import com.tx24.spicyplayer.lyrics.spicy.animation.BloomMotion
import com.tx24.spicyplayer.lyrics.spicy.animation.LineAnimState

/** The rows under a line sit at this opacity, glow included. */
private const val ROW_OPACITY = 0.65f

/** Whole-line text follows its parent's state, without inventing translated word timings. */
internal fun DrawScope.drawTranslationText(
    layout: LineLayout, animation: LineAnimState, startX: Float, scroll: Float, y: Float,
    config: RenderConfig, static: Boolean, replacement: Boolean = false,
) {
    val plan = lyricPaintPlan(animation.state, animation.isBackground, animation.opacity, animation.blur, config, lit = animation.lit)
    val alpha = when {
        static -> animation.opacity
        plan is LyricPaintPlan.InactiveShadow -> plan.alpha
        else -> (if (animation.isBackground) config.backgroundAlphaBright else config.gradientAlphaBright) * animation.opacity
    }
    val blur = if (static || plan !is LyricPaintPlan.InactiveShadow) 0f else plan.blurRadius
    // Animated rows under Bloom: a row with no words of its own lights up whole, on its own tone,
    // like a line-synced line, instead of sweeping.
    val bloomRows = config.isBloom && config.animatedUnderRows && !static
    val wholeAlpha = if (!bloomRows) alpha else {
        val dim = if (animation.isBackground) config.backgroundAlphaDim else config.gradientAlphaDim
        val bright = if (animation.isBackground) config.backgroundAlphaBright else config.gradientAlphaBright
        (dim + (bright - dim) * animation.rowLit) * animation.opacity
    }
    fun draw(result: TextLayoutResult, offset: Offset, dimmer: Float, base: Float = alpha) {
        val opacity = (base * dimmer).coerceIn(0f, 1f)
        val size = with(result.layoutInput.density) { result.layoutInput.style.fontSize.toPx() }
        // Blur is a separate pass, scaled by the actual lyric font size rather than density.
        if (blur > 0f && config.distanceBlurEnabled) {
            drawText(result, color = Color.Transparent, topLeft = offset,
                shadow = Shadow(Color.White.copy(alpha = opacity), blurRadius = blur * size / 56f))
            drawText(result, color = Color.Transparent, shadow = Shadow.None, topLeft = offset)
        } else drawText(result, color = Color.White.copy(alpha = opacity), shadow = Shadow.None, topLeft = offset)
    }
    withTransform({
        scale(animation.scale, animation.scale, Offset(
            layout.pivotX(startX), y + scroll + layout.height / 2f,
        ))
    }) {
        if (replacement) layout.words.forEach { draw(it.textLayoutResult, Offset(startX, y + scroll) + it.relativeOffset, 1f) }
        layout.supplements.forEach { supplement ->
            val offset = Offset(startX, y + scroll) + supplement.offset
            val rowAnimated = animation.rowWordStates.isNotEmpty()
            if (!static && (animation.isActive || rowAnimated) && supplement.words.isNotEmpty()) {
                supplement.words.forEach words@ { word ->
                    val rowState = animation.rowWordStates.getOrNull(word.sourceWordIndex)
                    val state = rowState ?: animation.wordStates.getOrNull(word.sourceWordIndex) ?: return@words
                    val width = word.textLayoutResult.size.width.toFloat()
                    val size = with(word.textLayoutResult.layoutInput.density) { word.textLayoutResult.layoutInput.style.fontSize.toPx() }
                    val position = state.gradientPosition
                    // Bloom's fill runs from -50 to 100 behind a soft edge of its own width.
                    val edge = if (config.isBloom) BloomMotion.FADE_EM * size else 0f
                    val dim = (if (animation.isBackground) config.backgroundAlphaDim else config.gradientAlphaDim) *
                        animation.opacity * ROW_OPACITY
                    val x = offset.x + word.relativeOffset.x
                    val top = offset.y + word.relativeOffset.y
                    val bright = alpha * ROW_OPACITY
                    val highlight = animation.rowHighlight
                    val at = word.sourceWordIndex * 2
                    if (rowState != null) {
                        // The row's word is a word of its own, run through the lyrics' own springs: its scale
                        // and lift, each letter of a held one on top of that, and the glow.
                        val letter = if (word.charCount > 0) state.letterStates.getOrNull(word.charIndex) else null
                        val boost = config.wordMotionBoost
                        val height = word.textLayoutResult.size.height.toFloat()
                        val wordPivot = Offset(x - word.startXOffset + word.fullWordWidth * word.anchor, top + height / 2f)
                        val glow = letter?.glow ?: state.glow
                        // Bloom's glow is made up for the row's opacity, so it reads as strongly as the line's.
                        val glowAlpha = if (config.isBloom) glow.coerceIn(0f, 1f) * animation.opacity
                            else (glow * if (letter != null) 1.85f else 0.35f).coerceIn(0f, 1f) * ROW_OPACITY * animation.opacity
                        val shadow = if (!animation.suppressShadows && glowAlpha > 0.02f) Shadow(Color.White.copy(alpha = glowAlpha),
                            blurRadius = if (config.isBloom) (letter?.glowRadius ?: 0f) * size
                                else (4f + (if (letter != null) 12f else 2f) * glow) * size / 56f) else null
                        withTransform({
                            scale(boostedScale(state.scale, boost), boostedScale(state.scale, boost), wordPivot)
                            translate(top = boostedLift(state.yOffset, boost) * size)
                            if (letter != null) {
                                scale(boostedScale(letter.scale, boost), boostedScale(letter.scale, boost), Offset(x + width / 2f, top + height / 2f))
                                // Bloom's letter poses are in em of the letter's own text, Spring's in lyric sizes, doubled.
                                if (config.isBloom) translate(left = letter.xOffset * boost * size, top = boostedLift(letter.yOffset, boost) * size)
                                else translate(top = boostedLift(letter.yOffset, boost) * size * 2f)
                            }
                        }) {
                            if (letter != null) drawWipeText(word.textLayoutResult, x, top, width, width, 0f, letter.gradientPosition,
                                bright, dim, shadow = shadow, rtl = supplement.isRtl, fixedEdgePx = edge)
                            else drawWipeText(word.textLayoutResult, x, top, width, word.fullWordWidth, word.startXOffset, position,
                                bright, dim, shadow = shadow, rtl = supplement.isRtl, fixedEdgePx = edge)
                        }
                    } else if (config.romanizationHighlight == RomanizationHighlight.Word && highlight.size > at + 1) {
                        // A highlight, not a sweep: the word being sung is lit whole, with a glow, then
                        // settles to the line's lit colour; the words ahead stay dim.
                        val level = highlight[at]
                        val glow = highlight[at + 1]
                        val full = animation.opacity * ROW_OPACITY
                        val lit = if (level <= 1f) dim + (bright - dim) * level else bright + (full - bright) * (level - 1f)
                        val shadow = if (!animation.suppressShadows && glow > 0.02f)
                            Shadow(Color.White.copy(alpha = (0.55f * glow * ROW_OPACITY * animation.opacity).coerceIn(0f, 1f)),
                                blurRadius = (0.12f + 0.12f * glow) * size) else null
                        drawWipeText(word.textLayoutResult, x, top, width, word.fullWordWidth, word.startXOffset, 100f,
                            lit, lit, shadow = shadow, rtl = supplement.isRtl)
                    } else drawWipeText(word.textLayoutResult, x, top, width, word.fullWordWidth, word.startXOffset, position, bright, dim,
                        shadow = null, rtl = supplement.isRtl, fixedEdgePx = edge)
                }
            } else if (!static && animation.isActive && config.animatedUnderRows && !config.isBloom) {
                // A row with no word timing sweeps over its line's time, with the line's glow.
                val result = supplement.text
                val size = textSizePx(result)
                val dim = (if (animation.isBackground) 0.3f else config.lineGradientAlphaDim) * animation.opacity * ROW_OPACITY
                val glowAlpha = (animation.lineGlow * 0.5f).coerceIn(0f, 1f) * animation.opacity
                val shadow = if (!animation.suppressShadows && glowAlpha > 0.02f)
                    Shadow(Color.White.copy(alpha = glowAlpha), blurRadius = (4f + 8f * animation.lineGlow) * size / 56f) else null
                val height = result.size.height.toFloat()
                // The row takes the line's downward fill, wrapped or not, right-to-left or not.
                drawVerticalWipeText(result, offset.x, offset.y, height, height, 0f,
                    animation.lineGradientPercent, alpha * ROW_OPACITY, dim, shadow)
            } else draw(supplement.text, offset, ROW_OPACITY, wholeAlpha)
        }
    }
}
