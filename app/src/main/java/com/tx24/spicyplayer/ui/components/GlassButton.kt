package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyGlass
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

/**
 * The inside of a glass button on a circle: a translucent fill, a 1px ring, and bright top
 * and bottom highlights (the `inset 0 ±1px 0` shadows, which show as thin crescents). [fill]
 * changes when pressed. Pair with [glassCast] outside any clip.
 */
fun Modifier.glassCircle(fill: () -> Color = { SpicyGlass.Fill }): Modifier = drawWithCache {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val px = 1.dp.toPx()
    val circle = Path().apply { addOval(Rect(center, radius)) }
    fun shifted(dy: Float) = Path().apply { addOval(Rect(center.copy(y = center.y + dy), radius)) }
    val topCrescent = Path().apply { op(circle, shifted(px), PathOperation.Difference) }
    val bottomCrescent = Path().apply { op(circle, shifted(-px), PathOperation.Difference) }
    onDrawBehind {
        drawCircle(fill(), radius = radius, center = center)
        drawCircle(SpicyGlass.EdgeRing, radius = radius - px / 2f, center = center, style = Stroke(px))
        drawPath(topCrescent, SpicyGlass.EdgeTop)
        drawPath(bottomCrescent, SpicyGlass.EdgeBottom)
    }
}

/** `.ViewControl`'s cast shadow, `0 10px 24px -8px`: a disc shrunk by the spread, blurred, moved down. */
fun Modifier.glassCast(strength: Float = 1f): Modifier = drawWithCache {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f + SpicyGlass.CastOffsetY.toPx())
    val castRadius = radius + SpicyGlass.CastSpread.toPx()
    val sigma = SpicyGlass.CastBlur.toPx() / 2f
    val outer = castRadius + sigma * 2f
    val color = SpicyGlass.CastColor.copy(alpha = SpicyGlass.CastColor.alpha * strength)
    val cast = Brush.radialGradient(
        0f to color,
        ((castRadius - sigma) / outer).coerceAtLeast(0f) to color,
        (castRadius / outer) to color.copy(alpha = color.alpha / 2f),
        1f to Color.Transparent,
        center = center,
        radius = outer,
    )
    onDrawBehind { drawCircle(cast, radius = outer, center = center) }
}

/**
 * A round glass button: [size] across, blurring the [LocalBackdrop] behind it, scaling to 0.94
 * while held with a springy easing, and brightening its fill.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    /** Told when a finger goes down (true) and when it lifts or slides off (false), before [onClick]. */
    onPressChange: ((Boolean) -> Unit)? = null,
    /** Off for a button that gives its own feedback, like one that acts as it's pressed. */
    releaseHaptic: Boolean = true,
    /** What a remote's, keyboard's or gamepad's press does, for a button whose touch handling is its own. */
    onKeyClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val currentOnPressChange by rememberUpdatedState(onPressChange)
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val backdrop = LocalBackdrop.current
    // The screen recomposes on every touch; keying the gesture on a fresh lambda would cancel
    // the press before it lifts.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnKeyClick by rememberUpdatedState(onKeyClick)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val fill by animateColorAsState(
        if (pressed) SpicyGlass.FillPressed else SpicyGlass.Fill,
        tween(300, easing = SpicyMotion.Standard),
        label = "glassFill",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .glassCast(LocalGlassCast.current)
            .then(
                backdrop?.let {
                    Modifier
                        .clip(CircleShape)
                        .hazeEffect(it, SpicyGlass.material) {
                            // A third-size copy, like every glass surface: after a 12px blur it
                            // looks the same, and each button re-renders what's behind it every frame.
                            inputScale = HazeInputScale.Auto
                        }
                } ?: Modifier,
            )
            .glassCircle { fill }
            .keyClickable(CircleShape) { (currentOnKeyClick ?: currentOnClick)() }
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    currentOnPressChange?.invoke(true)
                    scope.launch { scale.animateTo(PRESSED_SCALE, tween(280, easing = SpicyMotion.Overshoot)) }
                    val up = waitForUpOrCancellation()
                    pressed = false
                    currentOnPressChange?.invoke(false)
                    scope.launch { scale.animateTo(1f, tween(280, easing = SpicyMotion.Overshoot)) }
                    if (up != null) {
                        up.consume()
                        if (releaseHaptic) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalGlyphPressed provides pressed) { content() } }
}

/** How dark glass buttons' shadows are drawn, 1 the usual: less over a bright picture, where it shows as a smudge. */
val LocalGlassCast = compositionLocalOf { 1f }

/** Whether the button a glyph sits on is held down, for glyphs with a motion of their own. */
val LocalGlyphPressed = compositionLocalOf { false }

/** `.ViewControl:active { scale: 0.94 }`. */
private const val PRESSED_SCALE = 0.94f
