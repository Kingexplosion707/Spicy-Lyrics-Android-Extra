package com.tx24.spicyplayer.ui.nowplaying

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import com.tx24.spicyplayer.lyrics.spicy.models.MiniCredit
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import com.tx24.spicyplayer.ui.controls.SpicyIcons
import com.tx24.spicyplayer.ui.controls.keepsControlsHidden
import com.tx24.spicyplayer.ui.settings.ReleaseYearPosition
import androidx.compose.foundation.layout.Row

/** CSS's default `ease` timing, for a plain `transition: opacity 0.24s`. */
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** `MB_anim_enter .75s cubic-bezier(0.835, -0.008, 0.149, 0.866)`. */
private val CoverEnterEasing = CubicBezierEasing(0.835f, -0.008f, 0.149f, 0.866f)

/**
 * The compact song header: square cover on the left, lined up with the
 * lyrics, and the song name and artists centred beside it, scrolling when too long. It occupies the top
 * [CompactHeaderMetrics.barBottomPx] of the page; lyrics start at [CompactHeaderMetrics.lyricsTopPx].
 *
 * [expansion] (0..1) turns it into the full now-playing view with the lyrics hidden:
 * the cover centred and big, the song text centred under it
 * ([ExpandedHeader]). The cover moves across; the compact text fades out and the expanded text in.
 *
 * The cover's gestures: drag it sideways to skip (left for the next song, right for the
 * previous), double-tap it to play or pause.
 *
 * With [animatedCover] on, a record that has an animated cover on Apple Music loops it in place
 * of the still one.
 *
 * [releaseYear] shows the song's year before or after the artists, where set.
 *
 * With [hidden] on there is no compact bar: the expanded view fades in and out in place instead
 * of growing out of it. [interactive] off lets touches through to the lyrics.
 *
 * In landscape, [panel] takes the compact bar's place: the cover beside the lyrics with the song
 * text centred under it, at [panelLeft] (it slides when the sides swap). Expanding then only
 * moves it to the centre. [coverOverlay] is drawn over the cover and moves with it; touches it
 * leaves alone reach the cover's gestures. [belowCover] (the timeline) sits between the panel's
 * cover and its text, pushing the text down by its height. While the cover stage shows ([stage], 0..1), the
 * panel's cover and text dissolve into it, as the big cover and its text do into the portrait
 * Canvas ([immersive]).
 */
@Composable
fun CompactNowPlayingHeader(
    info: NowPlayingInfo,
    metrics: CompactHeaderMetrics,
    modifier: Modifier = Modifier,
    expansion: () -> Float = { 0f },
    expanded: ExpandedHeader? = null,
    isPlaying: Boolean = true,
    onPlayPause: () -> Unit = {},
    onSkip: (TrackDirection) -> Unit = {},
    animatedCover: Boolean = false,
    hidden: Boolean = false,
    interactive: Boolean = true,
    releaseYear: ReleaseYear? = null,
    panel: ExpandedHeader? = null,
    panelLeft: () -> Float = { panel?.artLeftPx ?: 0f },
    coverOverlay: (@Composable () -> Unit)? = null,
    belowCover: (@Composable () -> Unit)? = null,
    stage: () -> Float = { 0f },
    /** Portrait, while the song's Canvas fills the screen (0..1): the big cover and its text give way to it. */
    immersive: () -> Float = { 0f },
    /** The cover hidden under something opaque: its animated cover holds its frame. */
    animatedCoverHeld: Boolean = false,
    /** False while the whole header is faded out: its names stop scrolling. */
    visible: () -> Boolean = { true },
) {
    val year = releaseYear?.label()
    val density = LocalDensity.current
    val artwork = rememberSessionArtwork(info.artwork, info.artworkUri, maxDimension = ARTWORK_MAX_PX, hasSession = info.hasSession)
    val motionQuery = MotionCoverQuery(info.artists, info.album, info.title)
    val motion = rememberMotionCover(animatedCover, motionQuery)
    // Everything moves in the layout and draw phases, so the transition doesn't recompose the header.
    fun p() = if (expanded == null) 0f else expansion()
    // Hidden, the layout stays expanded and only the opacity follows the transition.
    fun layoutP() = if (hidden && expanded != null) 1f else p()
    val restingSize = panel?.artSizePx ?: metrics.artSizePx
    fun artSize() = lerp(restingSize, expanded?.artSizePx ?: restingSize, layoutP())
    fun artLeft() = lerp(if (panel != null) panelLeft() else metrics.contentStartPx, expanded?.artLeftPx ?: 0f, layoutP())
    fun artTop() = lerp(panel?.artTopPx ?: metrics.barTopPx, expanded?.artTopPx ?: 0f, layoutP())
    val restingCorner = if (panel != null) CompactHeaderMetrics.NOWBAR_CORNER_FRACTION else CompactHeaderMetrics.ART_CORNER_FRACTION
    fun stageP() = if (panel != null) stage() else 0f
    val below = belowCover.takeIf { panel != null }
    var belowHeightPx by remember { mutableFloatStateOf(0f) }
    fun boxAlpha() = if (hidden) p() else 1f
    // The compact bar's text gives way as the cover grows.
    fun compactTextAlpha() = 1f - (p() / TEXT_SWAP).coerceIn(0f, 1f)
    // The panel's text is always there; the compact bar's gives way to it.
    fun expandedTextAlpha() = (1f - immersive()) *
        if (panel != null) 1f - stageP() else ((layoutP() - (1f - TEXT_SWAP)) / TEXT_SWAP).coerceIn(0f, 1f)
    with(density) {
        Box(modifier.fillMaxSize().graphicsLayer { alpha = boxAlpha() }) {
            HeaderArtwork(
                artwork = artwork,
                motionUrl = motion.url,
                motionSettled = motion.settled,
                motionHeld = animatedCoverHeld,
                motionQuery = motionQuery,
                direction = info.direction,
                metrics = metrics,
                cornerFraction = { lerp(restingCorner, CompactHeaderMetrics.NOWBAR_CORNER_FRACTION, layoutP()) },
                overlay = coverOverlay,
                modifier = Modifier
                    .layout { measurable, _ ->
                        val size = artSize().roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(size, size))
                        layout(size, size) { placeable.place(0, 0) }
                    }
                    .offset { IntOffset(artLeft().roundToInt(), artTop().roundToInt()) }
                    // Under the stage it fades out, growing a little; under the portrait Canvas too.
                    .graphicsLayer {
                        val s = stageP()
                        alpha = (1f - s) * (1f - immersive())
                        scaleX = 1f + 0.04f * s
                        scaleY = 1f + 0.04f * s
                    }
                    // Its gestures leave the controls as they are.
                    .then(if (interactive) Modifier.coverGestures(isPlaying, onPlayPause, onSkip).keepsControlsHidden() else Modifier),
            )
            if (!hidden && panel == null) HeaderMetadata(
                title = info.title,
                artists = info.artists,
                year = year,
                titleStyle = headerTextStyle(FontWeight.Bold, metrics.titleSizeSp, metrics.titleLineHeightSp),
                artistsStyle = headerTextStyle(FontWeight.Normal, metrics.artistsSizeSp, metrics.artistsLineHeightSp),
                titleWidthPx = metrics.textWidthPx,
                artistsWidthPx = metrics.textWidthPx,
                centered = false,
                modifier = Modifier
                    .offset { IntOffset(metrics.textStartPx.roundToInt(), metrics.barTopPx.roundToInt()) }
                    .graphicsLayer { alpha = compactTextAlpha() }
                    .height(metrics.barHeightPx.toDp()),
                active = { visible() && compactTextAlpha() > 0f },
            )
            if (below != null) {
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val width = artSize().roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .onSizeChanged { belowHeightPx = it.height.toFloat() }
                        .offset { IntOffset(artLeft().roundToInt(), (artTop() + artSize()).roundToInt()) }
                        .graphicsLayer { alpha = 1f - stageP() },
                ) { below() }
            }
            if (expanded != null) {
                HeaderMetadata(
                    title = info.title,
                    artists = info.artists,
                    year = year,
                    titleStyle = headerTextStyle(FontWeight.Bold, expanded.titleSizeSp, expanded.titleLineHeightSp),
                    artistsStyle = headerTextStyle(FontWeight.Normal, expanded.artistsSizeSp, expanded.artistsLineHeightSp),
                    // `.SongName { max-width: 85cqw }`, `.Artists { max-width: 80cqw }`.
                    titleWidthPx = expanded.artSizePx * 0.85f,
                    artistsWidthPx = expanded.artSizePx * 0.8f,
                    centered = true,
                    modifier = Modifier
                        // Under the cover wherever it is, `margin-top: 5cqw` of it; right under
                        // the timeline where that sits there.
                        .offset {
                            val size = artSize()
                            IntOffset(
                                (artLeft() + size / 2f - expanded.artSizePx / 2f).roundToInt(),
                                (artTop() + if (below != null) size + belowHeightPx else size * 1.05f).roundToInt(),
                            )
                        }
                        .graphicsLayer { alpha = expandedTextAlpha() }
                        .width(expanded.artSizePx.toDp())
                        .height(expanded.textHeightPx.toDp()),
                    active = { visible() && boxAlpha() > 0f && expandedTextAlpha() > 0f },
                )
            }
        }
    }
}

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

/** The part of the transition each text takes to fade: out over the first 40%, in over the last. */
private const val TEXT_SWAP = 0.4f

internal fun headerTextStyle(weight: FontWeight, sizeSp: Float, lineHeightSp: Float) = TextStyle(
    fontFamily = LyricsLayoutCalculator.spicyFontFamily,
    // The design asks for 900, but the lyrics font stops at Bold.
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    lineHeightStyle = CssLineHeight,
)

/**
 * The cover's gestures:
 * - a sideways drag moves the whole cover with the finger, cut off at the edges of its place;
 *   let go, it always springs back into place, like lyrics bouncing back at the top of their
 *   page. Pulled past a third of the way (or flung), the song skips that way too (left: next,
 *   right: previous), and the new cover comes in with the usual cover change;
 * - a double tap plays or pauses, with a glass badge flashing what it did.
 */
private fun Modifier.coverGestures(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkip: (TrackDirection) -> Unit,
): Modifier = composed {
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestSkip by rememberUpdatedState(onSkip)
    val latestPlaying by rememberUpdatedState(isPlaying)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val swipe = remember { Animatable(0f) }
    val badge = remember { Animatable(0f) }
    // What the badge shows: what the double tap just switched to.
    var badgePlaying by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(1f) }
    val playPainter = rememberVectorPainter(SpicyIcons.Play)
    val pausePainter = rememberVectorPainter(SpicyIcons.Pause)
    this
        .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
        // The cut-off: while dragged, nothing of the cover shows past its place's sides (its
        // shadow still falls below). At rest nothing is cut, so the shadow spreads sideways too.
        .drawWithContent {
            if (swipe.value == 0f) {
                drawContent()
            } else {
                clipRect(0f, -size.height, size.width, size.height * 2f) { this@drawWithContent.drawContent() }
            }
        }
        .graphicsLayer { translationX = swipe.value }
        .drawWithContent {
            drawContent()
            val b = badge.value
            if (b <= 0f) return@drawWithContent
            // A glass disc with the glyph, popping in at 0.8 → 1 and fading.
            val radius = size.minDimension * 0.16f * (0.8f + 0.2f * b.coerceAtMost(1f))
            drawCircle(Color.Black.copy(alpha = 0.35f * b), radius, center)
            drawCircle(Color.White.copy(alpha = 0.18f * b), radius, center, style = Stroke(1.dp.toPx()))
            val painter = if (badgePlaying) playPainter else pausePainter
            val glyph = radius * 0.8f
            val aspect = painter.intrinsicSize.width / painter.intrinsicSize.height
            translate(center.x - glyph * aspect / 2f, center.y - glyph / 2f) {
                with(painter) { draw(androidx.compose.ui.geometry.Size(glyph * aspect, glyph), alpha = b) }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    haptics.performHapticFeedback(if (latestPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    badgePlaying = !latestPlaying
                    latestPlayPause()
                    scope.launch {
                        badge.snapTo(1f)
                        delay(350)
                        badge.animateTo(0f, tween(300))
                    }
                },
            )
        }
        .pointerInput(Unit) {
            val velocity = VelocityTracker()
            // Where the finger has taken the cover; the animation's value lags behind it.
            var offset = 0f
            detectHorizontalDragGestures(
                onDragStart = { velocity.resetTracking(); offset = swipe.value },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val before = offset
                    val after = (before + amount).coerceIn(-widthPx, widthPx)
                    offset = after
                    // A tick as the cover passes the skip point, either way.
                    if ((abs(before) > widthPx / 3f) != (abs(after) > widthPx / 3f)) {
                        haptics.performHapticFeedback(
                            if (abs(after) > widthPx / 3f) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick,
                        )
                    }
                    scope.launch { swipe.snapTo(after) }
                },
                onDragEnd = {
                    val vx = velocity.calculateVelocity().x
                    val fling = COVER_FLING_DP * density
                    when {
                        swipe.value < -widthPx / 3f || vx < -fling -> latestSkip(TrackDirection.Forward)
                        swipe.value > widthPx / 3f || vx > fling -> latestSkip(TrackDirection.Backward)
                    }
                    scope.launch { swipe.animateTo(0f, CoverReturn) }
                },
                onDragCancel = { scope.launch { swipe.animateTo(0f, CoverReturn) } },
            )
        }
}

/** The cover's way back into place: a quick spring that just settles, no wobble. */
private val CoverReturn = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)

/** A sideways fling faster than this (dp/s) skips even when the cover moved only a little. */
private const val COVER_FLING_DP = 1_000f

/**
 * Song name over artists (and the year, if shown). On a song change it fades out, swaps the text
 * at 350 ms, and fades back in 80 ms later; the year arriving later just takes its place.
 */
@Composable
internal fun HeaderMetadata(
    title: String,
    artists: String,
    year: YearLabel?,
    titleStyle: TextStyle,
    artistsStyle: TextStyle,
    titleWidthPx: Float,
    artistsWidthPx: Float,
    centered: Boolean,
    modifier: Modifier,
    /** Lined up on the right rather than the left (when not [centered]). */
    end: Boolean = false,
    /** Lines them up this way instead of by [centered] and [end]. */
    alignment: Alignment.Horizontal? = null,
    /** False while they can't be seen: long lines stop scrolling ([MarqueeLine]). */
    active: () -> Boolean = { true },
) {
    var shown by remember { mutableStateOf(ShownMetadata(title, artists, year)) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(title, artists, year) {
        val next = ShownMetadata(title, artists, year)
        if (next.title == shown.title && next.artists == shown.artists) {
            shown = next
            alpha.animateTo(1f, tween(METADATA_FADE_MS, easing = CssEase))
            return@LaunchedEffect
        }
        launch { alpha.animateTo(0f, tween(METADATA_FADE_MS, easing = CssEase)) }
        delay(350)
        shown = next
        delay(80)
        alpha.animateTo(1f, tween(METADATA_FADE_MS, easing = CssEase))
    }
    Column(
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        // Centred on the cover, compact or expanded.
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = when {
            alignment != null -> alignment
            centered -> Alignment.CenterHorizontally
            end -> Alignment.End
            else -> Alignment.Start
        },
    ) {
        MarqueeLine(shown.title, titleStyle, Color.White.copy(alpha = 0.95f), titleWidthPx, active = active)
        ArtistsRow(shown.artists, shown.year, artistsStyle, Color.White.copy(alpha = 0.7f), artistsWidthPx, active)
    }
}

private data class ShownMetadata(val title: String, val artists: String, val year: YearLabel?)

/** The year beside the artists: [before] them or after, [shown] or only holding its room. */
internal data class YearLabel(val text: String, val before: Boolean, val shown: Boolean)

/** [artists] with the year beside them as one line, for text too small to keep room for it. */
internal fun ReleaseYear?.besideArtists(artists: String): String {
    val y = this?.year?.takeIf { position != ReleaseYearPosition.Off } ?: return artists
    return if (position == ReleaseYearPosition.Left) "$y · $artists" else "$artists · $y"
}

/** The year as the artists' row shows it, or null where it's off or there's none. */
internal fun ReleaseYear.label(): YearLabel? {
    if (position == ReleaseYearPosition.Off) return null
    return when {
        year != null -> YearLabel(year, position == ReleaseYearPosition.Left, shown = true)
        // `.ArtistYear.pending { visibility: hidden }` with a stand-in year holding its room.
        pending -> YearLabel("0000", position == ReleaseYearPosition.Left, shown = false)
        else -> null
    }
}

/**
 * The artists, with the year before or after them when there is one. The year keeps still, set
 * off by a smaller, fainter middle dot; the artists scroll in the room left beside it.
 */
@Composable
private fun ArtistsRow(artists: String, year: YearLabel?, style: TextStyle, color: Color, containerWidthPx: Float, active: () -> Boolean) {
    if (year == null) {
        MarqueeLine(artists, style, color, containerWidthPx, active = active)
        return
    }
    val measurer = rememberTextMeasurer()
    val yearLayout = remember(year.text, style) { measurer.measure(year.text, style, maxLines = 1, softWrap = false) }
    // `::before/::after { content: "\00B7"; font-size: .82em; margin: 0 .55em; opacity: .78 }`.
    val dotStyle = remember(style) { style.copy(fontSize = style.fontSize * 0.82f) }
    val dotLayout = remember(dotStyle) { measurer.measure("\u00B7", dotStyle, maxLines = 1, softWrap = false) }
    val density = LocalDensity.current
    val dotMargin = with(density) { dotStyle.fontSize.toPx() * 0.55f }
    // The year sits where the artists' text would start (or end) without it.
    val outerPad = if (year.before) HeaderMarquee.spanStartPaddingPx(containerWidthPx) else HeaderMarquee.spanEndPaddingPx(containerWidthPx)
    val yearWidth = outerPad + yearLayout.size.width + dotMargin * 2 + dotLayout.size.width
    val height = maxOf(yearLayout.size.height, dotLayout.size.height)
    val yearLabel = @Composable {
        Canvas(
            Modifier
                .width(with(density) { yearWidth.toDp() })
                .height(with(density) { height.toDp() })
                .graphicsLayer { alpha = if (year.shown) 1f else 0f },
        ) {
            val dotTop = (size.height - dotLayout.size.height) / 2f
            val yearTop = (size.height - yearLayout.size.height) / 2f
            val dotColor = color.copy(alpha = color.alpha * 0.78f)
            if (year.before) {
                drawText(yearLayout, color, Offset(outerPad, yearTop))
                drawText(dotLayout, dotColor, Offset(outerPad + yearLayout.size.width + dotMargin, dotTop))
            } else {
                drawText(dotLayout, dotColor, Offset(dotMargin, dotTop))
                drawText(yearLayout, color, Offset(dotMargin * 2 + dotLayout.size.width, yearTop))
            }
        }
    }
    val room = (containerWidthPx - yearWidth).coerceAtLeast(1f)
    // `.Artists.has-year-before > span { padding-left: .5cqw }`, and the matching mask stops.
    val halfCqw = containerWidthPx * 0.005f
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (year.before) yearLabel()
        MarqueeLine(
            artists, style, color, room,
            padStart = if (year.before) halfCqw else HeaderMarquee.spanStartPaddingPx(containerWidthPx),
            padEndPx = if (year.before) HeaderMarquee.spanEndPaddingPx(containerWidthPx) else halfCqw,
            maskStartPx = containerWidthPx * (if (year.before) 0.01f else 0.02f),
            maskEndPx = containerWidthPx * (if (year.before) 0.02f else 0.01f),
            active = active,
        )
        if (!year.before) yearLabel()
    }
}

/** CSS line-height: the extra leading is split evenly above and below the text. */
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** One `.SongName`/`.Artists` row: a single line that scrolls per [HeaderMarquee] when it overflows. */
@Composable
internal fun MarqueeLine(
    text: String,
    style: TextStyle,
    color: Color,
    containerWidthPx: Float,
    padStart: Float = HeaderMarquee.spanStartPaddingPx(containerWidthPx),
    padEndPx: Float = HeaderMarquee.spanEndPaddingPx(containerWidthPx),
    /** How far in from each side the edge fades reach while the text scrolls; null: the usual. */
    maskStartPx: Float? = null,
    maskEndPx: Float? = null,
    /** False while the line can't be seen: the scroll waits, and comes back where it would be by then. */
    active: () -> Boolean = { true },
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measurer.measure(text, style, maxLines = 1, softWrap = false) }
    val spanWidth = padStart + layout.size.width + padEndPx
    val elementWidth = minOf(spanWidth, containerWidthPx)
    val overflows = spanWidth > containerWidthPx

    var phase by remember(text) { mutableFloatStateOf(0f) }
    val activeUpdated by rememberUpdatedState(active)
    LaunchedEffect(text, overflows) {
        phase = 0f
        if (!overflows) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            if (!activeUpdated()) snapshotFlow { activeUpdated() }.first { it }
            withFrameMillis { phase = HeaderMarquee.phase(it - start) }
        }
    }

    val (maskStart, maskEnd) = if (maskStartPx != null && maskEndPx != null && elementWidth > 0f) {
        val start = (maskStartPx / elementWidth).coerceIn(0f, 1f)
        start to ((elementWidth - maskEndPx) / elementWidth).coerceIn(start, 1f)
    } else HeaderMarquee.maskStops(containerWidthPx, elementWidth)
    val density = LocalDensity.current
    Canvas(
        Modifier
            .width(with(density) { elementWidth.toDp() })
            .height(with(density) { layout.size.height.toDp() }),
    ) {
        val textLeft = padStart + HeaderMarquee.offsetPx(phase, containerWidthPx, spanWidth)
        // Cut off at the sides only: glyphs from a fallback font (CJK, emoji) can reach past the
        // line box above and below, and a full clip sliced their bottoms off.
        clipRect(0f, -size.height, size.width, size.height * 2f) {
            // The edge mask as the text's own gradient, pinned to the element while the text moves.
            val mask = Brush.horizontalGradient(
                0f to color.copy(alpha = 0f),
                maskStart to color,
                maskEnd to color,
                1f to color.copy(alpha = 0f),
                startX = -textLeft,
                endX = elementWidth - textLeft,
            )
            translate(left = textLeft) { drawText(layout, brush = mask) }
        }
    }
}

/**
 * The one-line credit: centred with the person's avatar before their name when it fits, and
 * scrolling like the header's names (without the avatar) when it doesn't.
 */
@Composable
internal fun MiniCreditLine(credit: MiniCredit, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val avatar = credit.avatarUrl?.takeIf { credit.name != null }
        val avatarDp = with(density) { (style.fontSize.toPx() * 1.2f).toDp() }
        val gap = 4.dp
        val width = remember(credit, style) {
            measurer.measure(credit.prefix, style, maxLines = 1, softWrap = false).size.width +
                (credit.name?.let { measurer.measure("@$it", style, maxLines = 1, softWrap = false).size.width } ?: 0)
        } + with(density) { (if (avatar != null) avatarDp + gap * 2 else if (credit.name != null) gap else 0.dp).toPx() }
        if (width <= constraints.maxWidth) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(credit.prefix, style = style, color = color, maxLines = 1, softWrap = false)
                if (credit.name != null) {
                    Spacer(Modifier.width(gap))
                    if (avatar != null) {
                        AsyncImage(avatar, null, Modifier.size(avatarDp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(gap))
                    }
                    Text("@${credit.name}", style = style, color = color, maxLines = 1, softWrap = false)
                }
            }
        } else {
            MarqueeLine(credit.text, style, color, constraints.maxWidth.toFloat(), padStart = 0f, padEndPx = 0f)
        }
    }
}

/**
 * The cover, with its change animation: the new cover slides in from the right casting a
 * shadow to its left, while the old one gets a masked 12px blur; after 1.1 s the new cover
 * becomes the resting one. Going back to an earlier track ([TrackDirection.Backward]) mirrors
 * it, so the cover comes in from the left.
 */
@Composable
private fun HeaderArtwork(
    artwork: SessionArtwork?,
    motionUrl: String?,
    /** False while the next song's loop is looked up: [motionUrl] is still the last song's, fading out. */
    motionSettled: Boolean,
    /** Covered: the loop holds its frame. */
    motionHeld: Boolean,
    motionQuery: MotionCoverQuery,
    direction: TrackDirection,
    metrics: CompactHeaderMetrics,
    cornerFraction: () -> Float,
    modifier: Modifier,
    overlay: (@Composable () -> Unit)? = null,
) {
    val latestDirection by rememberUpdatedState(direction)
    var current by remember { mutableStateOf<SessionArtwork?>(null) }
    var incoming by remember { mutableStateOf<SessionArtwork?>(null) }
    val slide = remember { Animatable(0f) }
    val blur = remember { Animatable(0f) }
    // +1 slides the new cover in from the right, -1 from the left.
    var enterFrom by remember { mutableFloatStateOf(1f) }
    var failedMotionUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(artwork?.fingerprint) {
        // Keep the last cover when the player has none to show.
        val next = artwork ?: return@LaunchedEffect
        incoming = null
        slide.snapTo(0f)
        blur.snapTo(0f)
        val resting = current
        if (resting == null || resting.sameCover(next)) {
            current = next
            return@LaunchedEffect
        }
        incoming = next
        enterFrom = if (latestDirection == TrackDirection.Backward) -1f else 1f
        coroutineScope {
            launch { slide.animateTo(1f, tween(750, easing = CoverEnterEasing)) }
            launch { blur.animateTo(1f, tween(850, easing = CssEase)) }
        }
        delay(1_100L - 850L)
        current = next
        incoming = null
        slide.snapTo(0f)
        blur.snapTo(0f)
    }

    val density = LocalDensity.current
    val shadowColor = Color.Black.copy(alpha = 0.271f).toArgb()
    val shadowOffsetY = with(density) { metrics.artShadowOffsetYDp.dp.toPx() }
    val shadowRadius = cssBlurToShadowRadius(with(density) { metrics.artShadowBlurDp.dp.toPx() })
    val incomingShadowX = with(density) { metrics.incomingShadowOffsetXDp.dp.toPx() }
    val incomingShadowBlur = with(density) { metrics.incomingShadowBlurDp.dp.toPx() }
    val blurRadius = with(density) { metrics.outgoingBlurDp.dp.toPx() }

    Box(
        modifier.drawBehind {
            // Hardware canvases only draw shadow layers for shapes from API 28.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || current == null) return@drawBehind
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.TRANSPARENT
                    setShadowLayer(shadowRadius, 0f, shadowOffsetY, shadowColor)
                }
                val radius = size.width * cornerFraction()
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
            }
        },
    ) {
        // `.MediaImageContainer { border-radius: 3cqh }` against the square cover (2cqh expanded).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = RoundedCornerShape(size.width * cornerFraction())
                    clip = true
                    alpha = 0.95f
                },
        ) {
            val resting = current
            if (resting == null) {
                // A skeleton while no cover has loaded yet.
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
            } else {
                val bitmap = remember(resting) { resting.bitmap.asImageBitmap() }
                Box(Modifier.fillMaxSize()) {
                    Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (incoming != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Image(
                            bitmap,
                            null,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = blur.value
                                    renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    // `mask-image: linear-gradient(90deg, transparent, black 40%)`, clear on
                                    // the side the new cover comes from.
                                    val mask = if (enterFrom > 0f) {
                                        Brush.horizontalGradient(0f to Color.Transparent, 0.4f to Color.Black)
                                    } else {
                                        Brush.horizontalGradient(0.6f to Color.Black, 1f to Color.Transparent)
                                    }
                                    drawRect(mask, blendMode = BlendMode.DstIn)
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            if (motionUrl != null && motionUrl != failedMotionUrl) {
                MotionCoverVideo(
                    url = motionUrl,
                    // Hidden while a cover change slides in, so the old record's loop never plays
                    // over the new cover.
                    visible = incoming == null && current != null && motionSettled,
                    onFailed = {
                        failedMotionUrl = motionUrl
                        scope.launch { forgetMotionCover(context, motionQuery) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    held = motionHeld,
                )
            }
            incoming?.let { next ->
                val bitmap = remember(next) { next.bitmap.asImageBitmap() }
                Image(
                    bitmap,
                    null,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = enterFrom * (1f - slide.value) * size.width }
                        .drawBehind {
                            // Shadow on the cover's leading edge, mirrored when it comes in from the left.
                            if (enterFrom < 0f) {
                                scale(-1f, 1f, pivot = center) { leadingShadowBand(incomingShadowX, incomingShadowBlur) }
                            } else {
                                leadingShadowBand(incomingShadowX, incomingShadowBlur)
                            }
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        overlay?.invoke()
    }
}

/** Visible part of `.ti_ToImage { box-shadow: -20px 0 21px rgba(0,0,0,.14) }`: a soft band left of the cover. */
private fun DrawScope.leadingShadowBand(offsetX: Float, blur: Float) {
    val outer = offsetX - blur / 2f
    val inner = (offsetX + blur / 2f).coerceAtMost(0f)
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Transparent,
            ((inner - outer) / -outer).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.14f),
            1f to Color.Black.copy(alpha = 0.14f),
            startX = outer,
            endX = 0f,
        ),
        topLeft = Offset(outer, 0f),
        size = androidx.compose.ui.geometry.Size(-outer, size.height),
    )
}

/**
 * A CSS shadow's blur length is twice the Gaussian sigma; Android's shadow radius maps to
 * sigma = radius * 0.57735 + 0.5 (Skia's conversion), so invert that.
 */
private fun cssBlurToShadowRadius(cssBlurPx: Float): Float =
    ((cssBlurPx / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f)

private const val ARTWORK_MAX_PX = 1024
private const val METADATA_FADE_MS = 240
