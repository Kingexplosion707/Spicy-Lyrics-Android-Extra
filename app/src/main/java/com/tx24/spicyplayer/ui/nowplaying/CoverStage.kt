package com.tx24.spicyplayer.ui.nowplaying

import kotlin.math.PI
import kotlin.math.cos
import androidx.compose.animation.core.Easing
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The cover stage: in landscape, beside the lyrics, the cover ([picture]: the song's Spotify
 * Canvas, the record's animated loop, or the still cover) fills the panel's side of the screen
 * edge to edge, fading into the page toward the lyrics and over its top. It sits behind the
 * lyrics, translucent so the background carries through it, dimmed by a veil that deepens while
 * the controls show ([controlsShown]), and darkened along the bottom for the dock ([CoverStageDock]).
 *
 * [centred] (0..1) moves it from the panel's side to the middle of the page, where it stands in
 * for the big cover: as wide as the cover plus a feather on both sides, faded toward both.
 *
 * [shown] (0..1) fades the whole stage; [swapAlpha] and [swapX] carry it through a side swap.
 * [onRight] says which side it is on. [startInsetPx]/[endInsetPx] are the cutout's insets: the
 * picture runs under them. A canvas that won't play calls [onCanvasFailed] with its URL.
 */
@Composable
fun CoverStage(
    info: NowPlayingInfo,
    metrics: LandscapeMetrics,
    onRight: Boolean,
    startInsetPx: Float,
    endInsetPx: Float,
    picture: StagePicture,
    onCanvasFailed: (String) -> Unit,
    shown: () -> Float,
    controlsShown: () -> Float,
    swapAlpha: () -> Float,
    swapX: () -> Float,
    centred: () -> Float,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberSessionArtwork(info.artwork, info.artworkUri, maxDimension = STAGE_ARTWORK_MAX_PX, hasSession = info.hasSession)
    // The last cover stays up while the player has none to show.
    var resting by remember { mutableStateOf<SessionArtwork?>(null) }
    LaunchedEffect(artwork?.fingerprint) { if (artwork != null) resting = artwork }
    var failedMotionUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val motionQuery = MotionCoverQuery(info.artists, info.album, info.title)
    val featherPx = metrics.stageFeatherPx
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown() * swapAlpha()
                translationX = swapX()
            },
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val (left, right) = stageSpan(metrics, onRight, startInsetPx, endInsetPx, constraints.maxWidth.toFloat(), centred())
                    val width = (right - left).roundToInt().coerceIn(1, constraints.maxWidth)
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(left.roundToInt(), 0)
                    }
                }
                // Feathered toward the lyrics and over the top, on eased stops: a straight ramp
                // leaves a visible line where it reaches full strength. Centring, the outer edge
                // grows the same feather, mirrored.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val innerEdge = if (onRight) 0f else size.width
                    val outerEdge = size.width - innerEdge
                    val toward = if (onRight) 1f else -1f
                    val outerFeather = featherPx * centred()
                    val stops = FEATHER_STOPS.map { (at, a) ->
                        ((innerEdge + toward * at * featherPx) / size.width).coerceIn(0f, 1f) to Color.Black.copy(alpha = a)
                    }.toMutableList()
                    if (outerFeather >= 1f) {
                        FEATHER_STOPS.mapTo(stops) { (at, a) ->
                            ((outerEdge - toward * at * outerFeather) / size.width).coerceIn(0f, 1f) to Color.Black.copy(alpha = a)
                        }
                    }
                    stops.sortBy { it.first }
                    drawRect(Brush.horizontalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            TOP_FEATHER * 0.5f to Color.Black.copy(alpha = 0.28f),
                            TOP_FEATHER to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(Modifier.fillMaxSize().clipToBounds().graphicsLayer { alpha = STAGE_OPACITY }) {
                Crossfade(resting, animationSpec = tween(COVER_CHANGE_MS, easing = HalfCosine), label = "stageCover") { cover ->
                    if (cover != null) {
                        val bitmap = remember(cover) { cover.bitmap.asImageBitmap() }
                        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = CropAlignment)
                    }
                }
                // Canvas or animated cover, crossfading from one to the next as songs change. Both
                // cover the stage at their own shape, cropped like the still cover: faces sit in
                // the upper half of most. The feather and veil apply to them as to the cover.
                val canvasUrls = remember { mutableSetOf<String>() }
                val videoUrl = when (picture) {
                    is StagePicture.Canvas -> picture.url.also { canvasUrls += it }
                    is StagePicture.Animated -> picture.url.takeIf { it != failedMotionUrl }
                    StagePicture.Still -> null
                }
                CrossfadingMotionVideo(
                    url = videoUrl,
                    onFailed = { failed ->
                        if (failed in canvasUrls) {
                            onCanvasFailed(failed)
                        } else {
                            failedMotionUrl = failed
                            scope.launch { forgetMotionCover(context, motionQuery) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    cropFromTop = CROP_FROM_TOP,
                )
                // A black veil at 1 - b is the picture at brightness b, without a filter pass per frame.
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val brightness = 1f - (1f - CONTROLS_BRIGHTNESS) * controlsShown()
                        drawRect(Color.Black.copy(alpha = 1f - 0.8f * (0.45f + 0.55f * brightness)))
                    },
                )
            }
            // Darkens under the dock so its white text reads over bright frames, eased in over the
            // lower half: a straight ramp over a short span showed as a dark band.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.04f),
                            0.72f to Color.Black.copy(alpha = 0.11f),
                            0.84f to Color.Black.copy(alpha = 0.19f),
                            1f to Color.Black.copy(alpha = 0.26f),
                        ),
                    )
                },
            )
        }
    }
}

/**
 * The stage's dock: the song, timeline and transport in a box as wide as the cover, at the
 * page's outer edge beside the lyrics, or under the middle of the stage as it centres
 * ([centred]). Drawn apart from the picture so it can sit over the lyrics' layer and take
 * touches there. [shown], [swapAlpha] and [swapX] fade and move it with the stage; it keeps
 * clear of the cutout's insets.
 */
@Composable
fun CoverStageDock(
    metrics: LandscapeMetrics,
    onRight: Boolean,
    startInsetPx: Float,
    endInsetPx: Float,
    shown: () -> Float,
    swapAlpha: () -> Float,
    swapX: () -> Float,
    centred: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown() * swapAlpha()
                translationX = swapX()
            }
            .layout { measurable, constraints ->
                val width = metrics.coverSizePx.roundToInt().coerceAtMost(constraints.maxWidth)
                val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val side = if (onRight) {
                        constraints.maxWidth - endInsetPx - metrics.dockInsetPx - width
                    } else {
                        startInsetPx + metrics.dockInsetPx
                    }
                    val middle = pageCentre(metrics, startInsetPx) - width / 2f
                    placeable.place((side + (middle - side) * centred()).roundToInt(), 0)
                }
            },
        content = content,
    )
}

/** The page's middle on the screen, where the big cover sits. */
private fun pageCentre(metrics: LandscapeMetrics, startInsetPx: Float) =
    startInsetPx + metrics.centred.artLeftPx + metrics.coverSizePx / 2f

/**
 * The stage's left and right edges on a [screenWidthPx]-wide screen: from the outer screen edge
 * to past the panel beside the lyrics, or the cover plus a feather each side around the page's
 * middle, [centred] of the way between.
 */
private fun stageSpan(
    metrics: LandscapeMetrics,
    onRight: Boolean,
    startInsetPx: Float,
    endInsetPx: Float,
    screenWidthPx: Float,
    centred: Float,
): Pair<Float, Float> {
    val sideWidth = (if (onRight) endInsetPx else startInsetPx) + metrics.stageWidthPx
    val sideLeft = if (onRight) screenWidthPx - sideWidth else 0f
    val middleHalf = metrics.coverSizePx / 2f + metrics.stageFeatherPx
    val middle = pageCentre(metrics, startInsetPx)
    val left = sideLeft + (middle - middleHalf - sideLeft) * centred
    val right = sideLeft + sideWidth + (middle + middleHalf - sideLeft - sideWidth) * centred
    return left to right
}

/**
 * A horizontal alignment that slides with [bias] (-1 start, 0 centre, 1 end), read while
 * placing, so the dock's rows glide between the stage's side and its middle without recomposing.
 */
class SlidingAlignment(private val bias: () -> Float) : Alignment, Alignment.Horizontal {
    override fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int =
        BiasAlignment.Horizontal(bias()).align(size, space, layoutDirection)

    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset =
        IntOffset(align(size.width, space.width, layoutDirection), 0)
}

/**
 * The song docked over a video (the stage, the portrait Canvas): name over artists, sized as
 * [sizes]' dock caption, lined up by [alignment] as [bias] slides (-1 start, 1 end); long lines scroll. Each line carries padding under its edge
 * fade, so the rows are pulled out by it: the glyphs sit on the dock's gutter, in line with the
 * timeline below.
 */
@Composable
fun StageMetadata(
    info: NowPlayingInfo,
    sizes: ExpandedHeader,
    alignment: Alignment.Horizontal,
    bias: () -> Float,
    widthPx: Float,
    /** False while it can't be seen: long lines stop scrolling. */
    active: () -> Boolean = { true },
    /** Beside the artists, as in the header. */
    releaseYear: ReleaseYear? = null,
) {
    val title = sizes.dockTitleSizeSp
    val artists = sizes.dockArtistsSizeSp
    HeaderMetadata(
        title = info.title,
        artists = info.artists,
        year = releaseYear?.label(),
        titleStyle = headerTextStyle(FontWeight.Bold, title, title * 1.2f),
        artistsStyle = headerTextStyle(FontWeight.Normal, artists, artists * 1.35f),
        titleWidthPx = widthPx,
        artistsWidthPx = widthPx,
        centered = false,
        alignment = alignment,
        active = active,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val end = (bias() + 1f) / 2f
                val shift = end * HeaderMarquee.spanEndPaddingPx(widthPx) - (1f - end) * HeaderMarquee.spanStartPaddingPx(widthPx)
                placeable.place(shift.roundToInt(), 0)
            }
        },
    )
}

/** `object-position: 50% 40%`: faces sit in the upper half of most covers, so a centred crop cuts them. */
private const val CROP_FROM_TOP = 0.4f
private val CropAlignment = BiasAlignment(0f, CROP_FROM_TOP * 2f - 1f)


/** The stage's own opacity, so the background reads through it as one surface. */
private const val STAGE_OPACITY = 0.75f

/** How far the picture dims while the controls show. */
private const val CONTROLS_BRIGHTNESS = 0.55f

/** The top fades in over this much of the height. */
private const val TOP_FEATHER = 0.18f

/** The feather toward the lyrics: (distance from the inner edge as a share of the feather, opacity). */
private val FEATHER_STOPS = listOf(0f to 0f, 0.18f to 0.10f, 0.38f to 0.36f, 0.72f to 0.85f, 1f to 1f)

const val STAGE_FADE_MS = 900

/** A song change, on the dynamic background's own crossfade: a second, eased by half a cosine. */
private const val COVER_CHANGE_MS = 1000
private val HalfCosine = Easing { 0.5f - 0.5f * cos(PI.toFloat() * it) }
private const val STAGE_ARTWORK_MAX_PX = 1024
