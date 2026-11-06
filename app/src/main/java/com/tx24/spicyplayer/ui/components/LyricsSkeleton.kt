package com.tx24.spicyplayer.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * The loading skeleton: ragged placeholder lines where the lyrics will land, each with
 * a soft highlight sweeping across (lines further down trailing, so it reads as one diagonal
 * pass), and "Loading Lyrics" with three bouncing dots.
 *
 * The first line sits at [topPx], where the focused lyric line will, and the
 * status goes just above it rather than at the bottom left, where the controls and their blurred
 * shade would cover it. [lineSizeSp] is the lyrics' own font size.
 * [lineStartPx] and [lineMaxWidthPx] are the lyrics' column.
 */
@Composable
fun LyricsSkeleton(
    lineSizeSp: Float,
    lineStartPx: Float,
    lineMaxWidthPx: Float,
    topPx: Float,
    modifier: Modifier = Modifier,
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { elapsedMs = (it - start) / 1_000_000L }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        Canvas(
            Modifier
                .fillMaxSize()
                // For the fade mask below, drawn over the lines with DstIn.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val t = elapsedMs
            val size = lineSizeSp.sp.toPx()
            val lineHeight = size * 0.72f
            val gap = size * 0.62f
            val radius = CornerRadius(size * 0.18f)
            var y = topPx
            for ((i, widthPercent) in LineWidths.withIndex()) {
                if (y >= heightPx) break
                val width = (lineMaxWidthPx * widthPercent / 100f)
                val topLeft = Offset(lineStartPx, y)
                val barSize = Size(width, lineHeight)
                drawRoundRect(LineColor, topLeft, barSize, radius)
                // ::after: a highlight band the bar's width, translateX(-100% → 100%).
                val sweep = sweepFraction(t, delayMs = i * 60L)
                val bandLeft = lineStartPx + width * (sweep * 2f - 1f)
                clipPath(Path().apply { addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + lineHeight, radius)) }) {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.45f to SweepColor,
                            0.55f to SweepColor,
                            1f to Color.Transparent,
                            startX = bandLeft,
                            endX = bandLeft + width,
                        ),
                        Offset(bandLeft, y),
                        Size(width, lineHeight),
                    )
                }
                y += lineHeight + gap
            }
            // mask-image: #000 72%, transparent 97% (the top fade isn't needed below the header).
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 0.72f to Color.Black, 0.97f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
        SkeletonStatus(
            elapsedMs = { elapsedMs },
            modifier = Modifier
                .offset {
                    // Just above the first line, which sits where the focused lyric will.
                    val above = STATUS_HEIGHT_SP.sp.toPx() + STATUS_GAP_DP.dp.toPx()
                    androidx.compose.ui.unit.IntOffset(lineStartPx.toInt(), (topPx - above).toInt())
                }
                .height(STATUS_HEIGHT_SP.sp.let { with(density) { it.toDp() } }),
        )
    }
}

/** `.SkeletonStatus`: 0.8125rem semibold at 72% white, then `.SkeletonDots`. */
@Composable
private fun SkeletonStatus(elapsedMs: () -> Long, modifier: Modifier) {
    val fontSize = 13.sp
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "Loading Lyrics",
            style = SpicyType.Body.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.35.em,
                letterSpacing = 0.01.em,
                color = StatusColor,
            ),
        )
        val em = with(LocalDensity.current) { fontSize.toDp() }
        Spacer(Modifier.width(em * 0.3f))
        Box(Modifier.padding(bottom = em * 0.3f)) {
            Canvas(Modifier.size(width = em * (0.3f * 3 + 0.2f * 2), height = em * 0.3f)) {
                val d = size.height
                val step = d + em.toPx() * 0.2f
                for (i in 0 until 3) {
                    val (alpha, lift) = dotFrame(elapsedMs(), delayMs = i * 160L)
                    drawCircle(
                        StatusColor.copy(alpha = StatusColor.alpha * alpha),
                        radius = d / 2f,
                        center = Offset(i * step + d / 2f, d / 2f - lift * em.toPx()),
                    )
                }
            }
        }
    }
}

/** `SL_SkeletonSweep` 1.6s cubic-bezier(0.45, 0, 0.55, 1), after a positive delay. 0 → 1. */
private fun sweepFraction(elapsedMs: Long, delayMs: Long): Float {
    val local = elapsedMs - delayMs
    if (local < 0) return 0f
    return SweepEasing.transform((local % SWEEP_MS).toFloat() / SWEEP_MS)
}

/**
 * `SL_SkeletonDot`, 1.2s ease-in-out: opacity 0.25 → 1 → 0.25 and a lift of 0.18em at 40%.
 * Returns (opacity, lift in em).
 */
private fun dotFrame(elapsedMs: Long, delayMs: Long): Pair<Float, Float> {
    val local = elapsedMs - delayMs
    if (local < 0) return 0.25f to 0f
    val p = (local % DOT_MS).toFloat() / DOT_MS
    // ease-in-out runs per keyframe segment.
    val k = if (p < 0.4f) CssEaseInOut.transform(p / 0.4f) else 1f - CssEaseInOut.transform((p - 0.4f) / 0.6f)
    return (0.25f + 0.75f * k) to (0.18f * k)
}

// LINE_WIDTHS: fixed, so the placeholder reads like ragged lyric lines and never reshuffles.
private val LineWidths = intArrayOf(74, 58, 86, 49, 68, 81, 55, 72, 63, 84, 52, 77, 66, 88, 57, 70)
private val LineColor = Color.White.copy(alpha = 0.09f)
private val SweepColor = Color.White.copy(alpha = 0.1f)
private val StatusColor = Color.White.copy(alpha = 0.72f)
private val SweepEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
private val CssEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val SWEEP_MS = 1600L
/** The status row: one line of 0.8125rem at line-height 1.35. */
private const val STATUS_HEIGHT_SP = 13f * 1.35f
private const val STATUS_GAP_DP = 14f
private const val DOT_MS = 1200L
