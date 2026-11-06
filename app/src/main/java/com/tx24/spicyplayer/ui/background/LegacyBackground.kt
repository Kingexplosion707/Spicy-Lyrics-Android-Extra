package com.tx24.spicyplayer.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The "Legacy" background: three blurred, pill-shaped copies of the cover drifting on a 45 s
 * loop, the whole thing at
 * `saturate(1.5) brightness(0.8)` over black.
 *
 * The layers, by the CSS (container W×H, percentages of it):
 * - BackCenter: 300% × 100%, right −50%, top −20%, bottom-most;
 * - Back: 200% × 100%, bottom-left;
 * - Front: 200% × 100%, top-right, on top.
 * Each is `background-size: cover`, `border-radius: 100em` and `blur(40px)`. Back and BackCenter
 * run the loop in reverse, which for a 0 → 50% → 0 keyframe loop is the same motion.
 *
 * The loop keeps running at full speed while paused, so [isPlaying] isn't taken.
 * Each layer is cut and blurred once per cover at a small size; a frame only moves them.
 */
@Composable
fun LegacyBackground(
    coverArtBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    /** False holds the loop's first frame (low performance mode). */
    animate: Boolean = true,
    /** The layers' CSS blur(), in dp. */
    blurDp: Int = LEGACY_BLUR_DP,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0) return@BoxWithConstraints
        val blurPx = blurDp.coerceAtLeast(0) * LocalDensity.current.density
        val layers by produceState<List<LegacyLayerImage>?>(null, coverArtBitmap, w, h, blurPx) {
            // Keep the old layers up until the new ones are ready.
            val cover = coverArtBitmap ?: return@produceState
            value = withContext(Dispatchers.Default) { buildLegacyLayers(cover, w, h, blurPx) }
        }

        var progress by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(animate) {
            if (!animate) {
                progress = 0f
                return@LaunchedEffect
            }
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    progress = (((now - start) / 1_000_000L) % LEGACY_LOOP_MS).toFloat() / LEGACY_LOOP_MS
                }
            }
        }

        Canvas(Modifier.matchParentSize()) {
            drawRect(Color.Black)
            val images = layers ?: return@Canvas
            val motion = legacyMotion(progress)
            for (layer in images) {
                val box = layer.box
                withTransform({
                    // translate3d(%, %) of the layer's own box, then scale about its centre.
                    translate(box.width() * motion.translateX, box.height() * motion.translateY)
                    scale(motion.scale, motion.scale, Offset(box.centerX(), box.centerY()))
                }) {
                    drawImage(
                        image = layer.image,
                        dstOffset = IntOffset(layer.drawn.left, layer.drawn.top),
                        dstSize = IntSize(layer.drawn.width(), layer.drawn.height()),
                        colorFilter = LegacyFilter,
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
    }
}

/** One frame of `@keyframes bgAnim`: translate3d(-8%, 6%) and scale 1.02 → 1.1 at the half. */
internal data class LegacyMotion(val translateX: Float, val translateY: Float, val scale: Float)

internal fun legacyMotion(progress: Float): LegacyMotion {
    val p = if (progress < 0.5f) progress * 2f else 2f - progress * 2f
    return LegacyMotion(translateX = -0.08f * p, translateY = 0.06f * p, scale = 1.02f + 0.08f * p)
}

/** A layer's box on the page (before the animation) and where its blurred image goes. */
private class LegacyLayerImage(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val box: RectF,
    val drawn: Rect,
)

/** Layer boxes in back-to-front order, as fractions of the container (left, top, width, height). */
private val LegacyBoxes = listOf(
    floatArrayOf(-1.5f, -0.2f, 3f, 1f), // BackCenter
    floatArrayOf(0f, 0f, 2f, 1f), // Back
    floatArrayOf(-1f, 0f, 2f, 1f), // Front
)

private fun buildLegacyLayers(cover: Bitmap, w: Int, h: Int, blurPx: Float): List<LegacyLayerImage> {
    // Blurred to 40px, the layers carry no detail; cut them at a height of LAYER_HEIGHT_PX.
    val scale = LAYER_HEIGHT_PX / h.toFloat()
    val sigma = blurPx * scale
    val margin = ceil(sigma * 3f).toInt()
    return LegacyBoxes.map { (l, t, bw, bh) ->
        val box = RectF(l * w, t * h, (l + bw) * w, (t + bh) * h)
        val sw = max(1, (box.width() * scale).roundToInt())
        val sh = max(1, (box.height() * scale).roundToInt())
        val bitmap = Bitmap.createBitmap(sw + margin * 2, sh + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pill = RectF(margin.toFloat(), margin.toFloat(), (margin + sw).toFloat(), (margin + sh).toFloat())
        val radius = minOf(pill.width(), pill.height()) / 2f
        canvas.clipPath(Path().apply { addRoundRect(pill, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(cover, coverSource(cover.width, cover.height, pill.width(), pill.height()), pill, Paint(Paint.FILTER_BITMAP_FLAG))
        blurPremultiplied(bitmap, sigma)
        val drawn = Rect(
            (box.left - margin / scale).roundToInt(),
            (box.top - margin / scale).roundToInt(),
            (box.right + margin / scale).roundToInt(),
            (box.bottom + margin / scale).roundToInt(),
        )
        LegacyLayerImage(bitmap.asImageBitmap(), box, drawn)
    }
}

/** `background-size: cover; background-position: center`: the part of the cover that fills [bw]×[bh]. */
internal fun coverSource(cw: Int, ch: Int, bw: Float, bh: Float): Rect {
    val fit = max(bw / cw, bh / ch)
    val sw = bw / fit
    val sh = bh / fit
    val left = (cw - sw) / 2f
    val top = (ch - sh) / 2f
    return Rect(left.roundToInt(), top.roundToInt(), (left + sw).roundToInt(), (top + sh).roundToInt())
}

/**
 * CSS `blur()` in place: a Gaussian of standard deviation [sigma], approximated by three box
 * passes, on premultiplied colour so the transparent surround fades the edge instead of
 * darkening it.
 */
private fun blurPremultiplied(bitmap: Bitmap, sigma: Float) {
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    val channels = Array(4) { FloatArray(w * h) }
    for (i in px.indices) {
        val c = px[i]
        val a = (c ushr 24) / 255f
        channels[0][i] = a
        channels[1][i] = ((c shr 16) and 0xff) / 255f * a
        channels[2][i] = ((c shr 8) and 0xff) / 255f * a
        channels[3][i] = (c and 0xff) / 255f * a
    }
    val radius = boxRadiusFor(sigma)
    if (radius > 0) for (channel in channels) boxBlur3(channel, w, h, radius)
    for (i in px.indices) {
        val a = channels[0][i].coerceIn(0f, 1f)
        if (a <= 0f) {
            px[i] = 0
            continue
        }
        fun un(v: Float) = (v / a * 255f).roundToInt().coerceIn(0, 255)
        px[i] = ((a * 255f).roundToInt() shl 24) or (un(channels[1][i]) shl 16) or
            (un(channels[2][i]) shl 8) or un(channels[3][i])
    }
    bitmap.setPixels(px, 0, w, 0, 0, w, h)
}

/** Three box passes of radius r have variance r(r+1), so r ≈ σ − ½. */
internal fun boxRadiusFor(sigma: Float): Int = (sigma - 0.5f).roundToInt().coerceAtLeast(0)

/** Three passes of a (2r+1) box, horizontally then vertically, with transparent edges. */
internal fun boxBlur3(data: FloatArray, w: Int, h: Int, r: Int) {
    val tmp = FloatArray(data.size)
    repeat(3) {
        boxPass(data, tmp, count = h, length = w, step = 1, lineStep = w, r = r)
        boxPass(tmp, data, count = w, length = h, step = w, lineStep = 1, r = r)
    }
}

private fun boxPass(src: FloatArray, dst: FloatArray, count: Int, length: Int, step: Int, lineStep: Int, r: Int) {
    val norm = 1f / (2 * r + 1)
    for (line in 0 until count) {
        val base = line * lineStep
        var sum = 0f
        for (i in 0..minOf(r, length - 1)) sum += src[base + i * step]
        for (i in 0 until length) {
            dst[base + i * step] = sum * norm
            val add = i + r + 1
            val drop = i - r
            if (add < length) sum += src[base + add * step]
            if (drop >= 0) sum -= src[base + drop * step]
        }
    }
}

/** `saturate(1.5) brightness(0.8)` as one colour matrix (the Filter Effects spec's saturate). */
private val LegacyFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(1.5f)
        timesAssign(ColorMatrix().apply { setToScale(0.8f, 0.8f, 0.8f, 1f) })
    },
)

private const val LEGACY_LOOP_MS = 45_000L
/** The layers' blur(40px). */
const val LEGACY_BLUR_DP = 40
private const val LAYER_HEIGHT_PX = 128f
