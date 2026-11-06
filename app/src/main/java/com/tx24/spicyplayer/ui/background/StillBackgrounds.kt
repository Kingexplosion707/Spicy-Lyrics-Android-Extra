package com.tx24.spicyplayer.ui.background

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Alignment
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverVideo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.tx24.spicyplayer.ui.nowplaying.SessionArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** `cubic-bezier(0.66, 0, 0.34, 1)` over 850 ms: the still background's change. */
private val StillChangeEasing = CubicBezierEasing(0.66f, 0f, 0.34f, 1f)
private const val STILL_CHANGE_MS = 850

/**
 * A still picture filling the page: cropped to cover it, `brightness(0.55) contrast(1.05)
 * saturate(1.7)`, blurred by [blurDp] and drawn at 1.25× so the blur's soft edges fall outside.
 * A new picture slides in from the right as the old one slides out to the left. [image] null keeps
 * the one shown (nothing, at first).
 *
 * [refined] instead fades each picture in over the one before (the first one too) and keeps the
 * picture's edges; see [RefinedStillImageBackground]. [retired] drops a load under way and takes no
 * new pictures, for a background on its way out.
 *
 * The blur needs Android 12; older phones show the picture sharp.
 */
@Composable
fun StillImageBackground(
    image: SessionArtwork?,
    blurDp: Int,
    modifier: Modifier = Modifier,
    refined: Boolean = false,
    retired: Boolean = false,
) {
    if (refined) {
        RefinedStillImageBackground(image, blurDp, modifier, retired)
        return
    }
    var current by remember { mutableStateOf<StillPicture?>(null) }
    var incoming by remember { mutableStateOf<StillPicture?>(null) }
    val slide = remember { Animatable(0f) }

    val latestImage by rememberUpdatedState(image)
    LaunchedEffect(retired) {
        if (retired) return@LaunchedEffect
        // A null in between (still looking up) doesn't interrupt a change under way; a newer
        // picture does, and the one that was sliding in counts as shown.
        snapshotFlow { latestImage }.filterNotNull().distinctUntilChangedBy { it.fingerprint }.collectLatest { next ->
            incoming?.let { current = it; incoming = null }
            slide.snapTo(0f)
            val picture = withContext(Dispatchers.Default) { StillPicture(next.fingerprint, filtered(next.bitmap).asImageBitmap()) }
            val shown = current
            if (shown == null) {
                current = picture
                return@collectLatest
            }
            if (shown.fingerprint == picture.fingerprint) return@collectLatest
            incoming = picture
            slide.animateTo(1f, tween(STILL_CHANGE_MS, easing = StillChangeEasing))
            current = picture
            incoming = null
            slide.snapTo(0f)
        }
    }

    val blurPx = with(LocalDensity.current) { blurDp.coerceAtLeast(0) * density }
    Box(modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        current?.let { StillLayer(it.bitmap, blurPx) { size -> -slide.value * size } }
        incoming?.let { StillLayer(it.bitmap, blurPx) { size -> (1f - slide.value) * size } }
    }
}

/**
 * A refined change: the new picture fades in over the old one, which holds still beneath until
 * it's gone, settling from a slight zoom as it arrives. Nothing is cropped for the blur: the
 * picture overhangs the page by twice the blur on each side, so the blur's soft rim falls outside.
 * Transparent until the first picture is in, so whatever is beneath shows through.
 */
@Composable
private fun RefinedStillImageBackground(image: SessionArtwork?, blurDp: Int, modifier: Modifier, retired: Boolean) {
    val layers = remember { mutableStateListOf<FadingPicture>() }
    // Animations and removals outlive a newer picture's arrival, so it doesn't freeze them.
    val scope = rememberCoroutineScope()
    val latestImage by rememberUpdatedState(image)
    LaunchedEffect(retired) {
        if (retired) return@LaunchedEffect
        snapshotFlow { latestImage }.filterNotNull().distinctUntilChangedBy { it.fingerprint }.collectLatest { next ->
            if (layers.lastOrNull()?.picture?.fingerprint == next.fingerprint) return@collectLatest
            val picture = withContext(Dispatchers.Default) { StillPicture(next.fingerprint, filtered(next.bitmap).asImageBitmap()) }
            val beneath = layers.toList()
            val layer = FadingPicture(picture)
            layers += layer
            scope.launch { layer.alpha.animateTo(1f, tween(LAYER_FADE_MS, easing = LayerFadeEasing)) }
            scope.launch { layer.scale.animateTo(1f, tween(LAYER_SETTLE_SCALE_MS, easing = LayerSettleEasing)) }
            scope.launch {
                delay(LAYER_SETTLE_MS.toLong())
                layers.removeAll(beneath)
            }
        }
    }

    val blurPx = with(LocalDensity.current) { blurDp.coerceAtLeast(0) * density }
    Box(modifier.fillMaxSize().clipToBounds()) {
        for (layer in layers) {
            key(layer) { RefinedStillLayer(layer, blurPx) }
        }
    }
}

private class FadingPicture(val picture: StillPicture) {
    val alpha = Animatable(0f)
    val scale = Animatable(LAYER_SETTLE_FROM)
}

@Composable
private fun RefinedStillLayer(layer: FadingPicture, blurPx: Float) {
    Image(
        layer.picture.bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .overhang(2f * blurPx)
            .graphicsLayer {
                alpha = layer.alpha.value
                scaleX = layer.scale.value
                scaleY = layer.scale.value
                if (blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radius = cssBlurToRadius(blurPx)
                    renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                }
            },
    )
}

/** Lays the content out [px] larger than its slot on every side, centred on it. */
private fun Modifier.overhang(px: Float) = layout { measurable, constraints ->
    val extra = px.roundToInt()
    val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth + 2 * extra, constraints.maxHeight + 2 * extra),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, -extra) }
}

/** A refined layer fades in over `900ms cubic-bezier(0.45, 0, 0.25, 1)`... */
private val LayerFadeEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private const val LAYER_FADE_MS = 900
/** ...and settles from 1.035× over `1400ms cubic-bezier(0.23, 1, 0.32, 1)`. */
private val LayerSettleEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val LAYER_SETTLE_SCALE_MS = 1_400
private const val LAYER_SETTLE_FROM = 1.035f
/** The layers beneath a refined one go once it has settled. */
internal const val LAYER_SETTLE_MS = 1_500

/**
 * The record's looping animated cover over the page, graded and blurred like
 * [StillImageBackground] so it reads as the same picture, moving. It fades in once playing,
 * over the still beneath it. [refined] keeps its edges like the refined still.
 */
@Composable
fun MotionImageBackground(
    url: String,
    blurDp: Int,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    refined: Boolean = false,
    /** Covered by something opaque: the loop holds its frame. */
    held: Boolean = false,
) {
    val blurPx = with(LocalDensity.current) { blurDp.coerceAtLeast(0) * density }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        // The loop is square: cropped to cover the page, then 1.25× like the still (refined: twice
        // the blur over on each side instead).
        val side = if (refined) maxOf(maxWidth, maxHeight) + blurDp.coerceAtLeast(0).dp * 4 else maxOf(maxWidth, maxHeight) * 1.25f
        MotionCoverVideo(
            url = url,
            visible = true,
            onFailed = onFailed,
            layerPaint = StillGradePaint,
            held = held,
            modifier = Modifier
                .requiredSize(side)
                .graphicsLayer {
                    if (blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = cssBlurToRadius(blurPx)
                        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    }
                },
        )
    }
}

/**
 * [filtered]'s grade as one colour matrix, for video. Brightness first leaves nothing above 1, so
 * only contrast's slight dip below 0 goes unclamped between the steps, which doesn't show.
 */
private val StillGradePaint = Paint().apply {
    val matrix = ColorMatrix().apply { setScale(0.55f, 0.55f, 0.55f, 1f) }
    val offset = (0.5f - 0.5f * 1.05f) * 255f
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                1.05f, 0f, 0f, 0f, offset,
                0f, 1.05f, 0f, 0f, offset,
                0f, 0f, 1.05f, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    matrix.postConcat(ColorMatrix().apply { setSaturation(1.7f) })
    colorFilter = ColorMatrixColorFilter(matrix)
}

private class StillPicture(val fingerprint: Int, val bitmap: ImageBitmap)

@Composable
private fun StillLayer(bitmap: ImageBitmap, blurPx: Float, offsetX: (Float) -> Float) {
    Image(
        bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX(size.width)
                scaleX = 1.25f
                scaleY = 1.25f
                if (blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radius = cssBlurToRadius(blurPx)
                    renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                }
            },
    )
}

/** CSS blur lengths are Gaussian sigmas; Android's blur radius maps to sigma = radius × 0.57735 + 0.5. */
private fun cssBlurToRadius(sigmaPx: Float) = ((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.1f)

/**
 * `brightness(0.55) contrast(1.05) saturate(1.7)`, each step clamped to 0..1 as the browser's
 * filter chain does, on a copy no bigger than 1280 px.
 */
private fun filtered(source: Bitmap): Bitmap {
    val longest = maxOf(source.width, source.height)
    val scaled = if (longest <= MAX_STILL_PX) source else Bitmap.createScaledBitmap(
        source,
        (source.width * MAX_STILL_PX.toFloat() / longest).toInt().coerceAtLeast(1),
        (source.height * MAX_STILL_PX.toFloat() / longest).toInt().coerceAtLeast(1),
        true,
    )
    val w = scaled.width
    val h = scaled.height
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val p = pixels[i]
        var r = ((p shr 16) and 0xFF) / 255f
        var g = ((p shr 8) and 0xFF) / 255f
        var b = (p and 0xFF) / 255f
        r = (r * 0.55f).coerceIn(0f, 1f); g = (g * 0.55f).coerceIn(0f, 1f); b = (b * 0.55f).coerceIn(0f, 1f)
        r = ((r - 0.5f) * 1.05f + 0.5f).coerceIn(0f, 1f)
        g = ((g - 0.5f) * 1.05f + 0.5f).coerceIn(0f, 1f)
        b = ((b - 0.5f) * 1.05f + 0.5f).coerceIn(0f, 1f)
        // The filter spec's saturate matrix.
        val s = 1.7f
        val nr = (0.213f + 0.787f * s) * r + (0.715f - 0.715f * s) * g + (0.072f - 0.072f * s) * b
        val ng = (0.213f - 0.213f * s) * r + (0.715f + 0.285f * s) * g + (0.072f - 0.072f * s) * b
        val nb = (0.213f - 0.213f * s) * r + (0.715f - 0.715f * s) * g + (0.072f + 0.928f * s) * b
        pixels[i] = (0xFF shl 24) or
            ((nr.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or
            ((ng.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or
            (nb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private const val MAX_STILL_PX = 1280

/**
 * The cover's colours as a plain page: its lighter shade over all of it, its darker shade over
 * the top 90% fading out downwards, and a light black shade rising from the bottom. The colours
 * move to a new song's over 1.5 s; with no cover the page is black.
 *
 * [refined] fills the page with the cover's deepest shade under its darker one, so white lyrics
 * always read, and a new song's colours fade in over the old ones as a whole. [retired] takes no
 * new cover.
 */
@Composable
fun ColorBackground(cover: SessionArtwork?, modifier: Modifier = Modifier, refined: Boolean = false, retired: Boolean = false) {
    val colors by produceState<CoverColors?>(null, cover?.fingerprint, retired) {
        if (retired) return@produceState
        val bitmap = cover?.bitmap ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val pixels = IntArray(48 * 48)
            small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            CoverColors.of(pixels)
        }
    }
    if (refined) {
        RefinedColorBackground(colors, modifier)
        return
    }
    val base by animateColorAsState(colors?.let { Color(it.minContrast) } ?: Color.Black, tween(COLOR_CHANGE_MS), label = "colorBase")
    val top by animateColorAsState(colors?.let { Color(it.highContrast) } ?: Color.Black, tween(COLOR_CHANGE_MS), label = "colorTop")
    Canvas(modifier.fillMaxSize()) {
        drawShades(base, top)
        drawBottomShade()
    }
}

/** Each new set of colours fades in over the ones before as one layer; those go once it's in. */
@Composable
private fun RefinedColorBackground(colors: CoverColors?, modifier: Modifier) {
    val layers = remember { mutableStateListOf<FadingColors>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(colors) {
        val next = colors ?: return@LaunchedEffect
        if (layers.lastOrNull()?.colors == next) return@LaunchedEffect
        val beneath = layers.toList()
        val layer = FadingColors(next)
        layers += layer
        scope.launch {
            layer.alpha.animateTo(1f, tween(COLOR_CHANGE_MS, easing = CssEase))
            layers.removeAll(beneath)
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        for (layer in layers) {
            key(layer) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha.value }) {
                    drawShades(Color(layer.colors.higherContrast), Color(layer.colors.highContrast))
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawBottomShade() }
    }
}

private class FadingColors(val colors: CoverColors) {
    val alpha = Animatable(0f)
}

/** [base] over the page, and [top] over its top 90% fading out downwards. */
private fun DrawScope.drawShades(base: Color, top: Color) {
    drawRect(base)
    // `height: 90%`, `mask-image: linear-gradient(to top, transparent, black 145%)`.
    val band = size.height * 0.9f
    drawRect(
        Brush.verticalGradient(
            0f to top.copy(alpha = top.alpha * (1f / 1.45f)),
            1f to top.copy(alpha = 0f),
            startY = 0f,
            endY = band,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, band),
    )
}

/** `opacity: 0.3; linear-gradient(to top, black, transparent)`. */
private fun DrawScope.drawBottomShade() {
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f)))
}

private const val COLOR_CHANGE_MS = 1_500
/** CSS's default `ease`, which a colour transition runs on. */
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
