package com.tx24.spicyplayer.ui.background

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.tx24.spicyplayer.ui.nowplaying.FADE_MS
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.tx24.spicyplayer.ui.nowplaying.STAGE_FADE_MS
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCoverStream
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * The Canvas background at [blur] percent: at 100 the cheap [CanvasAmbient]; below it the video
 * itself, filling the page, dimmed and saturated towards the ambient's grade and blurred on the GPU
 * (Android 12 and later; older phones show it sharp), down to the plain video at 0. A new song's
 * video fades in over the last one's, which stays until it's covered.
 */
@Composable
fun CanvasBackdrop(url: String, blur: Int, onFailed: () -> Unit, modifier: Modifier = Modifier, held: Boolean = false) {
    if (blur >= 100) {
        CanvasAmbient(url, onFailed, modifier, held)
        return
    }
    val share = blur.coerceIn(0, 100) / 100f
    val paint = remember(share) {
        val saturation = 1f + (AmbientImage.SATURATE - 1f) * share
        val grade = ColorMatrix().apply { setSaturation(saturation) }
        grade.postConcat(ColorMatrix().apply { setScale(AmbientImage.BRIGHTNESS, AmbientImage.BRIGHTNESS, AmbientImage.BRIGHTNESS, 1f) })
        Paint().apply { colorFilter = ColorMatrixColorFilter(grade) }
    }
    // The video fully up; while [url] differs, it's still loading over this one.
    var covered by remember { mutableStateOf(url) }
    val latestUrl by rememberUpdatedState(url)
    val scope = rememberCoroutineScope()
    var pageWidth by remember { mutableStateOf(0) }
    // Towards 100% it softens and zooms like the glow, so the last step to it is barely a change.
    val radius = with(LocalDensity.current) { (glowBlurRadiusPx(pageWidth) * share).toDp() }
    Box(modifier.onSizeChanged { pageWidth = it.width }.blur(radius, BlurredEdgeTreatment.Rectangle)) {
      Box(Modifier.fillMaxSize().graphicsLayer {
          val zoom = 1f + (AmbientImage.OVERSCAN - 1f) * share
          scaleX = zoom
          scaleY = zoom
      }) {
        for (shown in listOf(covered, url).distinct()) key(shown) {
            MotionCoverVideo(
                url = shown,
                visible = true,
                onFailed = onFailed,
                modifier = Modifier.fillMaxSize(),
                layerPaint = paint,
                cropFromTop = 0.5f,
                onFirstFrame = {
                    if (shown != covered) scope.launch {
                        delay(FADE_MS.toLong())
                        if (shown == latestUrl) covered = shown
                    }
                },
                held = held,
            )
        }
      }
    }
}

/**
 * The GPU blur radius that softens a page [pageWidthPx] wide as much as the glow does: three box
 * passes of [AmbientImage.BLUR_RADIUS] in an image [AMBIENT_WIDTH] wide make a Gaussian of
 * sqrt(3((2r+1)² - 1)/12) of its pixels, stretched over the page; Android's blur takes a radius,
 * which it reads as a sigma of 0.57735 × radius + 0.5.
 */
internal fun glowBlurRadiusPx(pageWidthPx: Int): Float {
    val span = AmbientImage.BLUR_RADIUS * 2 + 1
    val sigma = kotlin.math.sqrt(3f * (span * span - 1) / 12f) * pageWidthPx / AMBIENT_WIDTH
    return ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)
}

/**
 * The page's backdrop while the cover stage shows a Spotify Canvas: the canvas itself, drawn into
 * an image [AMBIENT_WIDTH] pixels wide, blurred and graded there and stretched over the page, so
 * the blur costs a few thousand pixels instead of a full-screen pass. The moving background fades
 * out under it. A stream that fails to play calls [onFailed]. [held] (covered by something opaque)
 * keeps the last image up and samples nothing until it shows again.
 */
@Composable
fun CanvasAmbient(url: String, onFailed: () -> Unit, modifier: Modifier = Modifier, held: Boolean = false) {
    val stream = rememberMotionCoverStream(url, onFailed, held)
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    // Kept across songs, so the next canvas blends in over the last one's glow.
    val ambient = remember(pageSize) {
        if (pageSize.width <= 0 || pageSize.height <= 0) null
        else AmbientImage(AMBIENT_WIDTH, max(1, (AMBIENT_WIDTH.toFloat() * pageSize.height / pageSize.width).roundToInt()))
    }
    LaunchedEffect(stream, ambient) {
        if (ambient == null) return@LaunchedEffect
        callbackFlow {
            val stop = stream.addSampler(SAMPLE_LONG_SIDE, AMBIENT_INTERVAL_MS) { trySend(it) }
            awaitClose(stop)
        }.buffer(Channel.CONFLATED).collect { sample ->
            val pixels = withContext(Dispatchers.Default) { ambient.draw(sample.pixels, sample.width, sample.height) }
            image = Bitmap.createBitmap(pixels, ambient.width, ambient.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    // The screen fades the backdrop in as soon as it knows of a canvas, but the first frame comes
    // a moment later; it fades in itself then, rather than landing on what's beneath all at once.
    val reveal = remember { Animatable(0f) }
    val hasImage = image != null
    LaunchedEffect(hasImage) {
        if (hasImage) reveal.animateTo(1f, tween(STAGE_FADE_MS, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
    }
    Canvas(modifier.onSizeChanged { pageSize = it }.graphicsLayer { alpha = reveal.value }) {
        val frame = image ?: return@Canvas
        // Bilinear stretch: the image is already blurred, so scaling it up only softens it more.
        drawImage(frame, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
    }
}

/**
 * The backdrop's small image, [width]×[height]: each frame is drawn in covering it at 1.3×,
 * blurred ([BLUR_RADIUS], three box passes for about a 5px Gaussian), saturated 2.5× and dimmed
 * to 0.65, the moving background's grade, so lyrics contrast is unchanged. After the first,
 * each frame blends in at [BLEND], which keeps the glow from flickering with fast cuts.
 */
internal class AmbientImage(val width: Int, val height: Int) {
    private val accumulated = FloatArray(width * height * 3)
    private val frame = FloatArray(width * height * 3)
    private val scratch = FloatArray(width * height * 3)
    private var primed = false

    /** Draws a frame ([source], ARGB, [sourceWidth]×[sourceHeight]) in; returns the image as ARGB. */
    fun draw(source: IntArray, sourceWidth: Int, sourceHeight: Int): IntArray {
        cover(source, sourceWidth, sourceHeight)
        repeat(3) { boxBlur(frame, scratch, horizontal = true); boxBlur(scratch, frame, horizontal = false) }
        grade(frame)
        val keep = if (primed) 1f - BLEND else 0f
        for (i in accumulated.indices) accumulated[i] = accumulated[i] * keep + frame[i] * (1f - keep)
        primed = true
        return IntArray(width * height) { p ->
            val r = accumulated[p * 3].roundToInt().coerceIn(0, 255)
            val g = accumulated[p * 3 + 1].roundToInt().coerceIn(0, 255)
            val b = accumulated[p * 3 + 2].roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** The frame scaled to cover the image at 1.3×, centred, sampled bilinearly into [frame]. */
    private fun cover(source: IntArray, sw: Int, sh: Int) {
        val scale = max(width.toFloat() / sw, height.toFloat() / sh) * OVERSCAN
        val left = (width - sw * scale) / 2f
        val top = (height - sh * scale) / 2f
        for (y in 0 until height) for (x in 0 until width) {
            val sx = ((x + 0.5f - left) / scale - 0.5f).coerceIn(0f, sw - 1f)
            val sy = ((y + 0.5f - top) / scale - 0.5f).coerceIn(0f, sh - 1f)
            val x0 = floor(sx).toInt(); val y0 = floor(sy).toInt()
            val x1 = minOf(x0 + 1, sw - 1); val y1 = minOf(y0 + 1, sh - 1)
            val fx = sx - x0; val fy = sy - y0
            val out = (y * width + x) * 3
            for (c in 0..2) {
                val shift = 16 - c * 8
                fun at(px: Int, py: Int) = (source[py * sw + px] shr shift and 0xFF).toFloat()
                val top0 = at(x0, y0) + (at(x1, y0) - at(x0, y0)) * fx
                val bottom = at(x0, y1) + (at(x1, y1) - at(x0, y1)) * fx
                frame[out + c] = top0 + (bottom - top0) * fy
            }
        }
    }

    /** One box pass of [BLUR_RADIUS] along a row or column, edges clamped. */
    private fun boxBlur(from: FloatArray, to: FloatArray, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val span = BLUR_RADIUS * 2 + 1
        for (line in 0 until lines) {
            fun index(i: Int): Int {
                val at = i.coerceIn(0, length - 1)
                return (if (horizontal) line * width + at else at * width + line) * 3
            }
            for (c in 0..2) {
                var sum = 0f
                for (i in -BLUR_RADIUS..BLUR_RADIUS) sum += from[index(i) + c]
                for (i in 0 until length) {
                    to[index(i) + c] = sum / span
                    sum += from[index(i + BLUR_RADIUS + 1) + c] - from[index(i - BLUR_RADIUS) + c]
                }
            }
        }
    }

    /** CSS `saturate(2.5) brightness(0.65)`. */
    private fun grade(pixels: FloatArray) {
        val s = SATURATE
        for (p in 0 until width * height) {
            val i = p * 3
            val r = pixels[i]; val g = pixels[i + 1]; val b = pixels[i + 2]
            pixels[i] = ((0.213f + 0.787f * s) * r + (0.715f - 0.715f * s) * g + (0.072f - 0.072f * s) * b).coerceIn(0f, 255f) * BRIGHTNESS
            pixels[i + 1] = ((0.213f - 0.213f * s) * r + (0.715f + 0.285f * s) * g + (0.072f - 0.072f * s) * b).coerceIn(0f, 255f) * BRIGHTNESS
            pixels[i + 2] = ((0.213f - 0.213f * s) * r + (0.715f - 0.715f * s) * g + (0.072f + 0.928f * s) * b).coerceIn(0f, 255f) * BRIGHTNESS
        }
    }

    companion object {
        const val OVERSCAN = 1.3f
        const val BLUR_RADIUS = 4
        const val SATURATE = 2.5f
        const val BRIGHTNESS = 0.65f
        const val BLEND = 0.3f
    }
}

private const val AMBIENT_WIDTH = 80
/** Frames are sampled a little finer than the image, so the 1.3× cover still has detail to blur. */
private const val SAMPLE_LONG_SIDE = 96
/** Canvases run at 24 to 30 fps, so redrawing the backdrop faster buys nothing. */
private const val AMBIENT_INTERVAL_MS = 33L
