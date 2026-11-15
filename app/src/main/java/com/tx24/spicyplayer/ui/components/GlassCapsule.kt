package com.tx24.spicyplayer.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyGlass
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

/**
 * Whether the lyrics screen's view buttons share one glass capsule ([GlassCapsule]) rather than
 * each being its own glass circle. Off brings the separate buttons back.
 */
val LocalGroupedControls = compositionLocalOf { true }

/** The gap a capsule keeps around its buttons, as a share of the button's size. */
const val CAPSULE_PAD = 0.11f

/**
 * One frosted capsule that holds a row of [CapsuleButton]s: the backdrop blurred behind it, a light
 * fill, a hairline ring, a bright top edge and a cast shadow. The buttons inside carry no glass of
 * their own. Give the content its own padding ([CAPSULE_PAD] of the button size).
 */
@Composable
fun GlassCapsule(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalBackdrop.current
    val cast = LocalGlassCast.current
    Box(modifier) {
        if (cast > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(Modifier.matchParentSize().capsuleCast(cast))
        }
        Box(
            Modifier
                .then(
                    backdrop?.let {
                        Modifier
                            .clip(CircleShape)
                            .hazeEffect(it, SpicyGlass.capsuleMaterial) { inputScale = HazeInputScale.Auto }
                    } ?: Modifier,
                )
                .glassPill(),
        ) { content() }
    }
}

/**
 * `0 12px 28px -12px`: the capsule's own shape pulled in by the spread, blurred and moved down.
 * Needs a blur effect, so phones before Android 12 go without.
 */
private fun Modifier.capsuleCast(strength: Float): Modifier = this
    .blur(SpicyGlass.CapsuleCastBlur / 2f, BlurredEdgeTreatment.Unbounded)
    .drawWithCache {
        val inset = -SpicyGlass.CapsuleCastSpread.toPx()
        val dy = SpicyGlass.CapsuleCastOffsetY.toPx()
        val width = (size.width - inset * 2f).coerceAtLeast(0f)
        val height = (size.height - inset * 2f).coerceAtLeast(0f)
        val color = SpicyGlass.CastColor.copy(alpha = SpicyGlass.CastColor.alpha * strength)
        onDrawBehind {
            drawRoundRect(
                color,
                topLeft = Offset(inset, inset + dy),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = CornerRadius(height / 2f),
            )
        }
    }

/** The capsule's surface: a fill, a 1px ring and the bright crescent along its top edge. */
private fun Modifier.glassPill(): Modifier = drawWithCache {
    val radius = size.minDimension / 2f
    val px = 1.dp.toPx()
    fun pill(dy: Float) = Path().apply {
        addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(radius)))
    }
    val shape = pill(0f)
    val topCrescent = Path().apply { op(shape, pill(px), PathOperation.Difference) }
    onDrawBehind {
        drawPath(shape, SpicyGlass.CapsuleDim)
        drawPath(shape, SpicyGlass.CapsuleFill)
        drawRoundRect(
            SpicyGlass.CapsuleRing,
            topLeft = Offset(px / 2f, px / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - px, size.height - px),
            cornerRadius = CornerRadius(radius - px / 2f),
            style = Stroke(px),
        )
        drawPath(topCrescent, SpicyGlass.CapsuleEdgeTop)
    }
}

/**
 * A bare round button for inside a [GlassCapsule]: no glass of its own, a light disc while held,
 * scaling to 0.95. With [toggled] set it is a toggle: "on" fills it white with the glyph cut out of
 * the disc, so the capsule's frost shows through the strokes, and the glyph itself stays the same.
 */
@Composable
fun CapsuleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    toggled: Boolean? = null,
    onKeyClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnKeyClick by rememberUpdatedState(onKeyClick)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val on = toggled == true
    val wash by animateColorAsState(
        if (pressed && !on) SpicyGlass.FillPressed else Color.Transparent,
        tween(200),
        label = "capsuleWash",
    )
    val knock by animateFloatAsState(if (on) 1f else 0f, tween(240, easing = GlyphEase), label = "capsuleKnock")
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawWithContent {
                drawCircle(wash)
                drawContent()
            }
            .keyClickable(CircleShape) { (currentOnKeyClick ?: currentOnClick)() }
            .semantics {
                role = if (toggled == null) Role.Button else Role.Switch
                this.contentDescription = contentDescription
                if (toggled != null) toggleableState = ToggleableState(toggled)
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    scope.launch { scale.animateTo(PRESSED_SCALE, tween(140, easing = GlyphEase)) }
                    val up = waitForUpOrCancellation()
                    pressed = false
                    scope.launch { scale.animateTo(1f, tween(140, easing = GlyphEase)) }
                    if (up != null) {
                        up.consume()
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlyphPressed provides pressed) {
            if (toggled == null) {
                content()
            } else {
                // The glyph as it is off, fading out as the disc fills in.
                Box(Modifier.graphicsLayer { alpha = 1f - knock }, contentAlignment = Alignment.Center) { content() }
                if (knock > 0f) {
                    // The disc is its own layer, so cutting the glyph out only reaches the disc.
                    val opacity = if (pressed) SpicyGlass.KnockoutPressedOpacity else SpicyGlass.KnockoutOpacity
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                alpha = knock * opacity
                                scaleX = 0.8f + 0.2f * knock
                                scaleY = 0.8f + 0.2f * knock
                            }
                            .drawWithContent {
                                drawCircle(Color.White)
                                drawContent()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.cutOut(), contentAlignment = Alignment.Center) { content() }
                    }
                }
            }
        }
    }
}

/** Draws the content as a hole in whatever is already in the layer behind it. */
private fun Modifier.cutOut(): Modifier = drawWithCache {
    val paint = Paint().apply { blendMode = BlendMode.DstOut }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            this@onDrawWithContent.drawContent()
            canvas.restore()
        }
    }
}

private const val PRESSED_SCALE = 0.95f
