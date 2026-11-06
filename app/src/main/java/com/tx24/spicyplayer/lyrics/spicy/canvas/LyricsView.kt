package com.tx24.spicyplayer.lyrics.spicy.canvas

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.ScrollConfig
import com.tx24.spicyplayer.lyrics.spicy.animation.LineAnimState
import com.tx24.spicyplayer.lyrics.spicy.animation.LineSweepGeometry
import com.tx24.spicyplayer.lyrics.spicy.animation.LyricsAnimator
import com.tx24.spicyplayer.lyrics.spicy.animation.AppleMusicMotion
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.parser.LetterSynthesizer
import com.tx24.spicyplayer.ui.controls.keepsControlsHidden
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The main lyrics display component representing the split architecture.
 * Delegates text measurement to LyricsLayoutCalculator,
 * scroll physics to ScrollManager,
 * and drawing to LyricsRenderer extensions.
 *
 * @param lines The list of lyric lines to display.
 * @param currentTimeMs The current playback time in milliseconds.
 * @param onSeekWord Callback triggered when a user taps a line to seek to its start time.
 */
@Composable
fun LyricsView(
    lines: List<Line>,
    documentId: String,
    footer: LyricsFooter = LyricsFooter(),
    currentTimeMs: () -> Long,
    onSeekWord: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fontSizeScale: Float = 1.0f,
    config: RenderConfig = RenderConfig.FULL,
    lyricsType: LyricsType = LyricsType.Syllable,
    romanize: Boolean = false,
    focusAnchorFraction: Float = 0.25f,
    // When set, the active line's top (not its centre) is kept this far below the view's top,
    // ("Top" scrolling); focusAnchorFraction is then unused.
    activeLineTopPx: Float? = null,
    // Without activeLineTopPx, the active line's centre sits this far above the focus anchor.
    focusLiftPx: Float = 0f,
    // Without activeLineTopPx: the first line rests with its top this far down the view (a
    // fraction of its height) and the view never scrolls above that, so the opening lines sit
    // high until centring one needs the view to move. Null rests it at the focus anchor.
    restTopFraction: Float? = null,
    // Invoked with the raw frame-nanos at the top of this view's own animation frame, before
    // currentTimeMs() is read. Lets a caller (e.g. the playback clock smoothing in
    // SpicyLyricsPlayer) piggyback on this view's single withFrameNanos loop instead of running
    // a second, independent one — halving the Choreographer callbacks registered per lyrics
    // screen. Optional so other/future callers aren't forced to supply one.
    onFrameTick: ((Long) -> Unit)? = null,
    // Paused, the frame loop rests once nothing moves, looking in a few times a second for a seek.
    isPlaying: Boolean = true,
    /** False while none of the view shows (covered, or faded out): the frame loop rests until it does. */
    visible: () -> Boolean = { true },
    /** Shared with the screen: the scroll-to-active button and the pinned credits. */
    viewState: LyricsViewState? = null,
    /** Credits drawn pinned by the screen instead of after the lyrics. */
    pinnedFooter: PinnedFooterMode = PinnedFooterMode.Off,
    /** How far up from the bottom the lyrics are covered (pinned credits); they fade out above it. */
    maskBottomPx: () -> Float = { 0f },
    /** Scales the top and bottom edge fade; under 1 for a window too small for the full one. */
    maskScale: Float = 1f,
    translation: TranslationPresentation? = null,
    romanizationMode: RomanizationMode = RomanizationMode.Replace,
) {
    val textMeasurer = rememberTextMeasurer()
    // What is on screen: lines and their layouts, swapped together once new layouts are measured.
    // Until then the previous lyrics stay up, so a source switch mid-song (a better answer
    // arriving) replaces them in one frame instead of blanking the view while it measures.
    var measuredShown by remember { mutableStateOf<ShownLyrics?>(null) }
    val letterConfig = config.copy(wordMotionBoost = 1f, scroll = ScrollConfig())
    val measuredCache = remember(lines) { HashMap<MeasureKey, MeasuredLyrics>() }
    var measuredWidth by remember { mutableFloatStateOf(0f) }
    val fontKey = LyricsLayoutCalculator.fontKey
    val desiredKey = MeasureKey(letterConfig, measuredWidth, fontSizeScale, romanize, lyricsType, fontKey, translation, romanizationMode)
    val desired = measuredCache[desiredKey]
    val original = measuredCache[desiredKey.copy(translation = null)]
    // Translation variants never keep a previous song or an off-toggle's translated layout up.
    // While a new translation measures, what's up for this song stays up (the machine translation
    // a human one is replacing, or the plain lyrics), so the view never blanks in between.
    val shown = if (translation != null || measuredShown?.translation != null) {
        val current = measuredShown?.takeIf { translation != null && it.documentId == documentId }
        desired?.let { ShownLyrics(documentId, it.lines, it.layouts, lyricsType, footer, translation, it.romanizationLines) }
            ?: current
            ?: original?.let { ShownLyrics(documentId, it.lines, it.layouts, lyricsType, footer, null, it.romanizationLines) }
    } else measuredShown
    val shownId = shown?.documentId
    val lineLayouts = shown?.layouts.orEmpty()
    // Credits Follow the Last Singer: a song that ends on a duet line sung from the right (not
    // just a right-to-left line) takes its credits over there too.
    val footerAlignEnd = config.creditsFollowLastLine && shown != null && run {
        val last = shown.lines.indexOfLast { !it.isBackground }
        shown.lines.getOrNull(last)?.oppositeAligned == true && lineLayouts.getOrNull(last)?.isRtl == false
    }
    // The type and credits of what is shown, not of what is still being measured.
    val incomingType = lyricsType
    val incomingFooter = footer
    val lyricsType = shown?.lyricsType ?: incomingType
    val footer = shown?.footer ?: incomingFooter
    val coroutineScope = rememberCoroutineScope()

    val animator = remember(shownId) { LyricsAnimator(coroutineScope, config) }
    LaunchedEffect(config) { animator.config = config }
    val isStatic = lyricsType == LyricsType.Static

    // Keep the latest time provider without recomposing on every position tick: the frame loop
    // invokes it off-composition (inside withFrameNanos), so the changing clock never re-runs this
    // composable's body — only the Canvas redraws when the derived anim state actually changes.
    val currentTimeProvider by rememberUpdatedState(currentTimeMs)
    val linesUpdated by rememberUpdatedState(shown?.lines.orEmpty())
    val romanizationLinesUpdated by rememberUpdatedState(shown?.romanizationLines)
    val lineLayoutsUpdated by rememberUpdatedState(lineLayouts)
    val onFrameTickUpdated by rememberUpdatedState(onFrameTick)
    val isPlayingUpdated by rememberUpdatedState(isPlaying)
    val visibleUpdated by rememberUpdatedState(visible)
    val scrollConfigUpdated by rememberUpdatedState(config.scroll)

    val scrollPolicy = remember(shownId) { ScrollPolicyController() }
    // Read by the drag handler, written by the frame loop.
    val contentHeightForDrag = remember(shownId) { FloatArray(1) }
    val density = LocalDensity.current
    val scrollManager = remember(shownId) { ScrollManager().also { it.reset() } }
    scrollManager.smoothScrolling = config.scroll.smooth
    scrollManager.appleMusic = config.isAppleMusic
    scrollManager.refreshedSimple = config.isRefreshedSimple
    val refreshedUpdated by rememberUpdatedState(config.isRefreshedSimple)
    val ripple = remember(shownId, config.isRefreshedSimple) { RippleScroll() }
    val appleMusicUpdated by rememberUpdatedState(config.isAppleMusic)
    // The Apple Music style's staggered scroll: lines past the active one follow the view late.
    val scrollTrail = remember(shownId) { ScrollTrail() }
    // The frame time the late lines are drawn at while one is still behind; 0 once all have caught up.
    var trailNow by remember(shownId) { mutableLongStateOf(0L) }
    // Wakes a resting frame loop at once (a drag or tap), rather than at its next look.
    val wake = remember(shownId) { Channel<Unit>(Channel.CONFLATED) }
    // Lines that hold still are drawn once into this layer and reused (see StaticLines).
    val staticLines = remember(shownId) { StaticLines() }
    val staticLayer = rememberGraphicsLayer().apply {
        compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
    }
    val context = LocalContext.current
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        SideEffect { measuredWidth = canvasWidth }
        val canvasHeight = constraints.maxHeight.toFloat()
        // Compact fullscreen keeps the active lyric in the upper portion of the viewport.
        val centerY = activeLineTopPx ?: (ScrollPolicyController.anchorY(canvasHeight, focusAnchorFraction) - focusLiftPx)
        val alignTop = activeLineTopPx != null
        // The frame loop and tap handler outlive a change of anchor (the header shown or hidden
        // mid-song), so they read the latest one; a change also wakes a resting loop to re-anchor.
        val restTopY = if (alignTop || restTopFraction == null) 0f else minOf(0f, canvasHeight * restTopFraction - centerY)
        SideEffect { scrollManager.topY = restTopY }
        val centerYUpdated by rememberUpdatedState(centerY)
        val alignTopUpdated by rememberUpdatedState(alignTop)
        val canvasHeightUpdated by rememberUpdatedState(canvasHeight)
        // The lyrics fade out over the last 64dp; credits brought into view stay clear of it.
        val bottomFadeUpdated by rememberUpdatedState(canvasHeight * (1f - lyricsMaskStops(canvasHeight, density.density * maskScale).innerBottom))
        LaunchedEffect(centerY, alignTop) { wake.trySend(Unit) }
        val footerMetrics = remember(canvasWidth, density.density, fontSizeScale, lyricsType) {
            LyricsLayoutMetrics(canvasWidth, density.density, lyricsType, fontSizeScale)
        }
        val footerSlot = footerMetrics.contentSlot(false, false, false)
        val lineGapUpdated by rememberUpdatedState(footerMetrics.lineGapPx(config.isRefreshedSimple))
        val rowHeightUpdated by rememberUpdatedState(footerMetrics.lineHeightPx(footerMetrics.baseFontSizeSp))
        // Credits are sized from the synced lyric size whatever the lyrics type, so static lyrics
        // (drawn smaller) get the same credits.
        val creditBaseSp = remember(canvasWidth, density.density, fontSizeScale) {
            creditBaseSp(canvasWidth, density.density, fontSizeScale)
        }
        scrollManager.pxPerReferencePx = creditBaseSp * density.density / REFERENCE_LYRIC_SIZE_PX
        // The rows the screen pins are drawn there instead.
        val refinedCredits = config.refinedCredits
        // The frame loop outlives a settings change, so it reads the latest values.
        val refinedCreditsUpdated by rememberUpdatedState(refinedCredits)
        val footerLines = remember(footer, pinnedFooter, refinedCredits) {
            footer.lines().let { if (refinedCredits) refinedFooterLines(it) else it }.filterNot { pinnedFooter.pins(it.kind) }
        }
        val footerLayouts = remember(footerLines, creditBaseSp, footerSlot.widthPx, canvasWidth, fontKey, footerAlignEnd, refinedCredits) {
            measureFooterRows(footerLines, textMeasurer, creditBaseSp, density.density, footerSlot.widthPx, alignEnd = footerAlignEnd, refined = refinedCredits)
        }
        val footerHeight = footerLayouts.sumOf { (it.marginTop + it.height).toDouble() }.toFloat()
        val footerHeightUpdated by rememberUpdatedState(footerHeight)
        val avatars = rememberFooterAvatars(footerLines)
        if (viewState != null) {
            SideEffect {
                viewState.shownFooter = footer
                viewState.footerAlignEnd = footerAlignEnd
            }
            DisposableEffect(viewState) {
                onDispose {
                    viewState.shownFooter = null
                    viewState.activeLineDirection = null
                }
            }
            LaunchedEffect(viewState.scrollToActiveRequests) {
                if (viewState.scrollToActiveRequests == 0) return@LaunchedEffect
                scrollManager.returnToActive()
                wake.trySend(Unit)
            }
        }
        val viewStateUpdated by rememberUpdatedState(viewState)
        val maskBottomUpdated by rememberUpdatedState(maskBottomPx)
        // Recalculate layouts whenever the lyrics, dimensions, or font size change.
        // A newer key cancels a measurement still running, so only the latest one lands.
        LaunchedEffect(lines, letterConfig, canvasWidth, fontSizeScale, romanize, documentId, incomingType, incomingFooter, fontKey, translation, romanizationMode) {
            suspend fun measure(romanized: Boolean, presentation: TranslationPresentation? = translation): MeasuredLyrics {
                val key = MeasureKey(letterConfig, canvasWidth, fontSizeScale, romanized, incomingType, fontKey, presentation, romanizationMode)
                measuredCache[key]?.let { return it }
                return withContext(Dispatchers.Default) {
                    // Per-letter emphasis for held words (mode-dependent thresholds, romanized
                    // display). Syllable mode only; Line/Static never letter-split.
                    val translated = presentation?.displayLines(lines) ?: lines
                    val useRomanized = romanized && romanizationMode == RomanizationMode.Replace
                    val display = if (incomingType == LyricsType.Syllable) LetterSynthesizer.apply(translated, letterConfig, useRomanized) else translated
                    val originals = if (presentation?.mode == TranslationMode.Replace && incomingType == LyricsType.Syllable)
                        LetterSynthesizer.apply(lines, letterConfig, useRomanized) else if (presentation?.mode == TranslationMode.Replace) lines else display
                    MeasuredLyrics(display, if (presentation != null || romanized && romanizationMode == RomanizationMode.UnderLine) LyricsLayoutCalculator.calculatePresentationLayouts(
                        originals, display, presentation,
                        canvasWidth, textMeasurer, density.density, incomingType, fontSizeScale, romanized, letterConfig.isSimple,
                        letterConfig.wideDuetPadding,
                        if (letterConfig.isAppleMusic) AppleMusicMotion.BACKGROUND_SIZE else 0.75f,
                        romanizationMode,
                        refreshedSimple = letterConfig.isRefreshedSimple,
                    ) else LyricsLayoutCalculator.calculateLineLayouts(
                        display, canvasWidth, textMeasurer, density.density, incomingType, fontSizeScale, romanized, letterConfig.isSimple,
                        letterConfig.wideDuetPadding,
                        if (letterConfig.isAppleMusic) AppleMusicMotion.BACKGROUND_SIZE else 0.75f,
                        refreshedSimple = letterConfig.isRefreshedSimple,
                    ), romanizationLines = originals.takeIf {
                        presentation?.mode == TranslationMode.Replace && romanized && romanizationMode == RomanizationMode.UnderLine
                    })
                }.also { measuredCache[key] = it }
            }
            // What's asked for goes up first; the layouts a toggle switches to are measured after,
            // so they're ready without holding this one back.
            val measured = measure(romanize)
            measuredShown = ShownLyrics(documentId, measured.lines, measured.layouts, incomingType, incomingFooter, translation, measured.romanizationLines)
            val romanizable = lines.any { line -> line.words.any { it.romanizedText != null } }
            if (translation != null) measure(romanize, null)
            if (romanizable) measure(!romanize)
            if (translation != null && romanizable) measure(!romanize, null)
        }

        if (lineLayouts.isEmpty()) return@BoxWithConstraints

        // Per-line state belongs to the lyrics it was computed for: new lyrics start empty, as on
        // a first load, until their own frame loop's first frame, rather than drawing one frame
        // with the previous lyrics' states.
        var animStates by remember(shownId) { mutableStateOf<List<LineAnimState>>(emptyList()) }
        // Where the credits start: under the last line as it stands, a folded background line taking no room.
        var footerStart by remember(shownId) { mutableStateOf(Float.NaN) }
        // Refined Credits stay dim until the last line has been sung.
        var footerDim by remember { mutableStateOf(1f) }
        var dynamicYOffsets by remember(shownId) { mutableStateOf(FloatArray(0)) }
        var lastFrameTimeNanos by remember { mutableLongStateOf(0L) }
        val hostView = LocalView.current
        
        // The high-frequency animation loop, restarted with each new document's animator and scroll.
        LaunchedEffect(shownId, ripple) {
            // Reused per-frame scratch for the dynamic Y offsets: filled every frame but only
            // published to state when its contents actually change, so a paused/idle screen stops
            // invalidating the Canvas (a fresh FloatArray each frame was forcing a redraw via array
            // identity-equality even when nothing moved).
            var dynamicYScratch = FloatArray(0)
            var settledYScratch = FloatArray(0)
            var rippleHeights = FloatArray(0)
            var stillFrames = 0
            var lastLayouts: List<LineLayout>? = null
            var sweepGeometries: Array<LineSweepGeometry?> = arrayOfNulls(0)
            var sweepMeasured = BooleanArray(0)
            // The first frame of a document, and the first after a rest, carry no real gap.
            var resumed = true
            var frameIntervalS = nominalFrameIntervalS(hostView.display?.refreshRate)
            while (true) {
                if (!visibleUpdated()) {
                    // Out of sight, nothing to draw: it waits to show again, then picks up where
                    // the song has got to (over a second on, the scroll jumps there).
                    snapshotFlow { visibleUpdated() }.first { it }
                    resumed = true
                    stillFrames = 0
                    frameIntervalS = nominalFrameIntervalS(hostView.display?.refreshRate)
                }
                if (stillFrames >= REST_AFTER_STILL_FRAMES) {
                    withTimeoutOrNull(REST_POLL_MS) { wake.receive() }
                    resumed = true
                    frameIntervalS = nominalFrameIntervalS(hostView.display?.refreshRate)
                }
                withFrameNanos { frameTimeNanos ->
                    val previousStates = animStates
                    val previousOffsets = dynamicYOffsets
                    val previousScroll = scrollManager.animScrollY
                    onFrameTickUpdated?.invoke(frameTimeNanos)

                    val currentLayouts = lineLayoutsUpdated
                    val currentLines = linesUpdated
                    // The same lyrics laid out again (romanized, resized): seen here, on the frame
                    // that first draws the new layouts, so the scroll jumps with them.
                    val relaid = currentLayouts !== lastLayouts
                    if (relaid) {
                        rippleHeights = FloatArray(currentLayouts.size) { currentLayouts[it].height }
                        var place = -1
                        scrollTrail.places = IntArray(currentLayouts.size) { i ->
                            if (!currentLayouts[i].isBackground) place++
                            place.coerceAtLeast(0)
                        }
                    }
                    if (lastLayouts != null && relaid) scrollManager.onRelayout()
                    lastLayouts = currentLayouts
                    val currentTime = currentTimeProvider()

                    val deltaTime = frameDeltaSeconds(frameTimeNanos, lastFrameTimeNanos, frameIntervalS, resumed)
                    lastFrameTimeNanos = frameTimeNanos
                    resumed = false

                    if (currentLayouts.size == currentLines.size && currentLines.isNotEmpty()) {
                        // 1. Step the animator for visual properties (scale, opacity, glow).
                        if (relaid || sweepGeometries.size != currentLayouts.size) {
                            sweepGeometries = arrayOfNulls(currentLayouts.size)
                            sweepMeasured = BooleanArray(currentLayouts.size)
                        }
                        val sweeps = sweepGeometries
                        val measured = sweepMeasured
                        animStates = animator.animate(currentLines, currentTime, deltaTime, scrollManager.hideLineBlur, lyricsType, romanizationLinesUpdated,
                            lineGeometry = if (!refreshedUpdated) null else { index ->
                                if (index !in sweeps.indices) null else {
                                    if (!measured[index]) { sweeps[index] = currentLayouts.getOrNull(index)?.let(::sweepGeometry); measured[index] = true }
                                    sweeps[index]
                                }
                            })

                        val dimTarget = if (refinedCreditsUpdated && lyricsType != LyricsType.Static &&
                            currentTime < (currentLines.maxOfOrNull { it.endMs } ?: 0L)) 0.55f else 1f
                        if (footerDim != dimTarget) {
                            footerDim += (dimTarget - footerDim) * (deltaTime * 6f).coerceAtMost(1f)
                            if (kotlin.math.abs(dimTarget - footerDim) < 0.002f) footerDim = dimTarget
                        }

                        // 1.5 Calculate dynamic Y offsets based on interlude scales.
                        var accumulatedY = 0f
                        if (dynamicYScratch.size != currentLayouts.size) {
                            dynamicYScratch = FloatArray(currentLayouts.size)
                        }
                        val newDynamicYOffsets = dynamicYScratch

                        // Where each line settles once running interlude animations finish; the
                        // scroll aims here so it doesn't chase an opening or closing gap.
                        var settledY = 0f
                        if (settledYScratch.size != currentLayouts.size) {
                            settledYScratch = FloatArray(currentLayouts.size)
                        }
                        // Early Scroll picks its line ahead of the song, so it also has to judge
                        // which interludes are open at that time, or the target moves again when
                        // the dots really open or close.
                        val scrollTime = currentTime + scrollConfigUpdated.leadMs
                        // Where an open interlude's dots sit below its start: the middle of the gap
                        // before it plus its row, i.e. halfway between the lines around it.
                        val interludeCentre = (rowHeightUpdated - lineGapUpdated) / 2f
                        // The room a folded background line gives back: from the bottom of the row
                        // above it to the top of the next, less the usual gap when the next is a
                        // line of its own (background lines sit tight under their lead and under
                        // each other, without one). Stacked background lines then give back
                        // exactly their rows, leaving one gap between their lead and the next line.
                        fun slotOf(i: Int): Float {
                            val layout = currentLayouts[i]
                            val nextLayout = currentLayouts.getOrNull(i + 1)
                            val next = nextLayout?.yOffset ?: (layout.yOffset + layout.height + lineGapUpdated)
                            val above = currentLayouts.getOrNull(i - 1)?.let { it.yOffset + it.height } ?: layout.yOffset
                            val gap = if (nextLayout?.isBackground == true) 0f else lineGapUpdated
                            return (next - above - gap).coerceAtLeast(0f)
                        }
                        for (i in currentLayouts.indices) {
                            val layout = currentLayouts[i]
                            val state = animStates.getOrNull(i)

                            if (layout.isInterlude) {
                                val line = layout.line
                                val open = if (scrollTime >= line.startMs &&
                                    scrollTime <= line.endMs - LyricsAnimator.PRE_HIDDEN_DOT_LINE_MS) 1f else 0f
                                settledYScratch[i] = layout.yOffset + settledY + interludeCentre * open
                                settledY += rowHeightUpdated * open
                            } else {
                                settledYScratch[i] = layout.yOffset + settledY
                                // Folded background vocals give up their room. Where they're headed,
                                // not how far they've got: the scroll moves once, with the fold.
                                if (layout.isBackground) settledY -= slotOf(i) * (1f - (state?.presenceGoal ?: 1f))
                            }

                            if (layout.isInterlude) {
                                // An open interlude is one lyric row; the gap before it is already
                                // there, so it adds none after. The dots sit centred between the two
                                // lines, as far from each as two lines are from each other.
                                val scale = state?.scale?.coerceIn(0f, 1f) ?: 0f
                                newDynamicYOffsets[i] = layout.yOffset + accumulatedY + interludeCentre * scale
                                accumulatedY += rowHeightUpdated * scale
                            } else {
                                val fold = if (layout.isBackground) 1f - (state?.presence ?: 1f) else 0f
                                // Tucked up under their line as they fold.
                                newDynamicYOffsets[i] = layout.yOffset + accumulatedY -
                                    slotOf(i) * fold * AppleMusicMotion.BACKGROUND_FOLDED_SHIFT
                                accumulatedY -= slotOf(i) * fold
                            }
                        }
                        // Publish only when the offsets actually changed (i.e. an interlude is
                        // expanding/collapsing); otherwise the Canvas keeps the last array and
                        // isn't invalidated. copyOf() so the published snapshot is immutable while
                        // the scratch keeps mutating next frame.
                        if (!newDynamicYOffsets.contentEquals(dynamicYOffsets)) {
                            dynamicYOffsets = newDynamicYOffsets.copyOf()
                        }
                        currentLayouts.lastOrNull()?.let { last ->
                            val start = if (last.isBackground) last.yOffset + last.height + accumulatedY
                                else newDynamicYOffsets[currentLayouts.lastIndex] + last.height
                            if (start != footerStart) footerStart = start
                        }

                        // 2. Resolve the lead/background overlap policy, then anchor
                        // that one line: its centre at the focus point, or its top (compact mode).
                        val decision = scrollPolicy.decide(currentLines, currentTime, leadMs = scrollConfigUpdated.leadMs)
                        // A lead with no words of its own (the line is only background vocals)
                        // has no height; its background vocals stand in for it.
                        val targetIndex = decision.targetIndex?.let { index ->
                            val next = currentLayouts.getOrNull(index + 1)
                            if (currentLayouts[index].line.words.isEmpty() && next != null && next.isBackground &&
                                next.line.groupId == currentLayouts[index].line.groupId) index + 1 else index
                        }
                        var targetY: Float? = targetIndex?.let { index ->
                            // An interlude's offset is already the centre of its dots, in a row
                            // one lyric line tall.
                            val half = when {
                                alignTopUpdated -> if (currentLayouts[index].isInterlude) -interludeCentre else 0f
                                currentLayouts[index].isInterlude -> 0f
                                // A line whose translation or romanization rows make it taller
                                // than the room above the focus point (a small window) keeps its
                                // own text in view: its top stops a line gap below the edge.
                                else -> minOf(currentLayouts[index].height / 2f, (centerYUpdated - lineGapUpdated).coerceAtLeast(0f))
                            }
                            -(settledYScratch[index] + half)
                        }
                        // A run of lines with no timing of their own is sung all at once, over the gap
                        // they fill. The view holds the run's first line there, or with static
                        // auto-scroll on, moves through the run over the gap as static lyrics do the song.
                        targetIndex?.let { index ->
                            val line = currentLayouts[index].line
                            if (!line.untimed) return@let
                            val run = currentLayouts.indices.filter { i ->
                                val other = currentLayouts[i].line
                                other.untimed && other.startMs == line.startMs && !currentLayouts[i].isBackground
                            }
                            if (run.isEmpty()) return@let
                            fun anchor(i: Int) = settledYScratch[i] + if (alignTopUpdated) 0f else currentLayouts[i].height / 2f
                            val progress = if (scrollConfigUpdated.staticSongMs > 0L && line.endMs > line.startMs) {
                                ((currentTime - line.startMs).toFloat() / (line.endMs - line.startMs)).coerceIn(0f, 1f)
                            } else 0f
                            targetY = -(anchor(run.first()) + (anchor(run.last()) - anchor(run.first())) * progress)
                        }
                        // Once the song's last line has been sung, the credits scroll up far enough to be whole
                        // on screen, above whatever the pinned credits and controls cover.
                        val lastLine = currentLayouts.lastOrNull()
                        val aimed = targetY
                        if (!isStatic && aimed != null && lastLine != null && footerHeightUpdated > 0f &&
                            currentTime >= (currentLines.maxOfOrNull { it.endMs } ?: Long.MAX_VALUE)) {
                            val contentBottom = lastLine.yOffset + lastLine.height + accumulatedY + footerHeightUpdated
                            val reachable = canvasHeightUpdated - maskBottomUpdated() - bottomFadeUpdated - centerYUpdated - contentBottom
                            if (aimed > reachable) targetY = maxOf(reachable, aimed - canvasHeightUpdated * 0.4f)
                        }
                        val targetVisiblePx = targetIndex?.let { index ->
                            val top = centerYUpdated + scrollManager.animScrollY + newDynamicYOffsets[index]
                            val bottom = top + currentLayouts[index].height
                            (minOf(bottom, canvasHeightUpdated) - maxOf(top, 0f)).coerceAtLeast(0f)
                        } ?: Float.POSITIVE_INFINITY

                        // Scrolled away by hand with the line being sung out of sight: which way it lies.
                        viewStateUpdated?.let { shared ->
                            val directionIndex = if (isStatic || !scrollManager.hideLineBlur) null
                                else targetIndex ?: currentLayouts.indices.firstOrNull { i ->
                                    currentLayouts[i].line.startMs > currentTime && !currentLayouts[i].isBackground
                                } ?: currentLayouts.lastIndex
                            val direction = directionIndex?.let { index ->
                                val top = centerYUpdated + scrollManager.animScrollY + newDynamicYOffsets[index]
                                val viewBottom = canvasHeightUpdated - maskBottomUpdated()
                                when {
                                    top + currentLayouts[index].height <= 0f -> ActiveLineDirection.Above
                                    top >= viewBottom -> ActiveLineDirection.Below
                                    else -> null
                                }
                            }
                            if (shared.activeLineDirection != direction) shared.activeLineDirection = direction
                        }

                        // 3. Step the scroll spring and handle user overrides.
                        val lastLayout = currentLayouts.lastOrNull()
                        // Credits scroll into reach too, most visibly when static lyrics are scrolled by hand.
                        val totalContentHeight = (lastLayout?.yOffset ?: 0f) + (lastLayout?.height ?: 0f) + accumulatedY +
                            footerHeightUpdated

                        // Static lyrics have no timing to follow: their scrolling is the user's, or with
                        // auto-scroll, they move through at the song's pace, the point reached by the
                        // song's progress held at the focus point. The line there stands in as the
                        // target, so a hand scroll hands back once it changes.
                        var scrollIndex = targetIndex
                        var scrollVisiblePx = targetVisiblePx
                        if (isStatic) {
                            targetY = null
                            val songMs = scrollConfigUpdated.staticSongMs
                            if (songMs > 0L && currentLayouts.isNotEmpty()) {
                                val reached = (currentTime.toFloat() / songMs).coerceIn(0f, 1f) * (totalContentHeight - footerHeightUpdated)
                                targetY = -reached
                                scrollIndex = currentLayouts.indices.lastOrNull { settledYScratch[it] <= reached } ?: 0
                                scrollVisiblePx = Float.POSITIVE_INFINITY
                            }
                        }
                        contentHeightForDrag[0] = totalContentHeight
                        val beforeScroll = scrollManager.animScrollY
                        scrollManager.updateScroll(
                            deltaTime, totalContentHeight, scrollIndex.takeIf { targetY != null }, targetY,
                            snap = decision.motion == ScrollMotion.SNAP,
                            targetVisiblePx = scrollVisiblePx,
                        )
                        if (refreshedUpdated) {
                            val moving = ripple.update(beforeScroll, scrollManager.animScrollY, scrollIndex,
                                newDynamicYOffsets, rippleHeights, centerYUpdated, canvasHeightUpdated,
                                deltaTime, decision.motion == ScrollMotion.SNAP || relaid,
                                scrollManager.hideLineBlur)
                            val now = if (moving) frameTimeNanos else 0L
                            if (trailNow != now) trailNow = now
                        } else if (appleMusicUpdated) {
                            targetIndex?.let { scrollTrail.reference = it }
                            scrollTrail.record(frameTimeNanos, scrollManager.animScrollY, dynamicYOffsets)
                            val behind = !scrollManager.hideLineBlur && scrollTrail.catchingUp(frameTimeNanos)
                            val now = if (behind) frameTimeNanos else 0L
                            if (trailNow != now) trailNow = now
                        } else if (trailNow != 0L) trailNow = 0L
                    }
                    val still = !isPlayingUpdated && !scrollManager.isUserScrolling &&
                        animStates == previousStates && dynamicYOffsets === previousOffsets &&
                        scrollManager.animScrollY == previousScroll && trailNow == 0L
                    stillFrames = if (still) stillFrames + 1 else 0
                }
            }
        }

        // The sole renderer mask: transparent through 16dp, ramping to opaque at 64dp, with a
        // symmetric bottom edge. Pinned credits lift the bottom edge above them: the lyrics fade
        // out over the 48dp just above.
        val maskStops = lyricsMaskStops(canvasHeight, density.density * maskScale)
        val fadeBrush = remember(maskStops) { maskBrush(maskStops) }
        val pinnedFadePx = PINNED_FADE_DP * density.density

        // Where each credit row sits below the last line, in content space (before scrolling).
        // Shared by drawing and tapping so a tap always lands on what was drawn.
        fun footerRowTops(): List<Float> = footerRowTops(
            footerLayouts,
            lineLayouts.lastOrNull()?.let { layout ->
                footerStart.takeIf { !it.isNaN() }
                    ?: (dynamicYOffsets.getOrElse(lineLayouts.lastIndex) { layout.yOffset } + layout.height)
            } ?: 0f,
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val covered = maskBottomUpdated()
                    val brush = if (covered <= 0f) fadeBrush else maskBrush(
                        maskStops.copy(
                            innerBottom = ((size.height - covered - pinnedFadePx) / size.height).coerceIn(maskStops.innerTop, 1f),
                            outerBottom = ((size.height - covered) / size.height).coerceIn(maskStops.innerTop, 1f),
                        ),
                    )
                    drawRect(brush = brush, blendMode = BlendMode.DstIn)
                }
                .pointerInput(scrollManager) {
                    // Interaction: Dragging.
                    detectDragGestures(
                        onDragStart = { scrollManager.onDragStart(); wake.trySend(Unit) },
                        onDragEnd = { scrollManager.onDragEnd() },
                        onDragCancel = { scrollManager.onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scrollManager.onDrag(dragAmount.y, contentHeightForDrag[0])
                        }
                    )
                }
                // Tapping a credit leaves the controls as they are.
                .keepsControlsHidden(tapsOnly = true) { position ->
                    footerProfileAt(footerLayouts, footerRowTops(), position.y - (centerYUpdated + scrollManager.animScrollY) -
                        (if (refreshedUpdated) ripple.offset(lineLayouts.size) else 0f),
                        position.x, footerSlot.startPx, footerSlot.startPx + footerSlot.widthPx, footerAlignEnd) != null
                }
                .pointerInput(isStatic, footerLayouts, shown, ripple) {
                    detectTapGestures { tapOffset ->
                        val currentScrollY = scrollManager.animScrollY
                        val adjustedTapY = tapOffset.y - (centerYUpdated + currentScrollY)

                        // A credit with a profile asks the screen to open it.
                        footerProfileAt(footerLayouts, footerRowTops(), adjustedTapY -
                            (if (refreshedUpdated) ripple.offset(lineLayouts.size) else 0f),
                            tapOffset.x, footerSlot.startPx, footerSlot.startPx + footerSlot.widthPx, footerAlignEnd)?.let { line ->
                            viewState?.requestProfile(line)
                            return@detectTapGestures
                        }

                        // Tapping a line seeks to it. Static lyrics are not seekable.
                        if (isStatic) return@detectTapGestures

                        for (i in lineLayouts.indices) {
                            val layout = lineLayouts[i]
                            val layoutDynamicY = dynamicYOffsets.getOrElse(i) { layout.yOffset } +
                                (if (refreshedUpdated) ripple.offset(i) else 0f)
                            if (adjustedTapY >= layoutDynamicY && adjustedTapY <= layoutDynamicY + layout.height) {
                                if (layout.isInterlude || layout.isSongwriter) continue
                                // Folded background vocals sit under the next line: that one takes the tap.
                                if ((animStates.getOrNull(i)?.presence ?: 1f) < 0.5f) continue
                                if (layout.line.words.isNotEmpty()) {
                                    // From the first sung word, which can come after the line's own start.
                                    val start = layout.line.words.first().startMs
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onSeekWord(
                                        if (scrollConfigUpdated.seekFadeCompensation) {
                                            (start - ScrollConfig.SEEK_FADE_COMPENSATION_MS).coerceAtLeast(0L)
                                        } else start
                                    )
                                    scrollManager.onSeek()
                                    wake.trySend(Unit)
                                }
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val viewScrollOffset = centerY + scrollManager.animScrollY
            // Where a line is drawn: with the view, or a little behind it while it catches up.
            val trailTime = trailNow
            fun scrollOffsetFor(index: Int) = if (config.isRefreshedSimple) viewScrollOffset + ripple.offset(index)
                else if (trailTime == 0L) viewScrollOffset
                else centerY + scrollTrail.at(trailTime - scrollTrail.delayNanos(index))
            // A late line takes its shift from the same moment as its scroll: a fold above the active
            // line and the scroll making up for it then reach it together, not the fold first.
            fun dynamicYFor(index: Int, now: Float) = if (config.isRefreshedSimple || trailTime == 0L) now
                else scrollTrail.shiftAt(trailTime - scrollTrail.delayNanos(index), index, now)

            fun DrawScope.drawLine(drawn: DrawnLine) {
                val layout = lineLayouts[drawn.index]
                val lineAnim = drawn.anim
                val scrollOffset = drawn.scrollOffset
                val dynamicY = drawn.dynamicY
                val lineStartX = getLineStartX(layout)

                when {
                    layout.isInterlude -> drawInterludeGroup(layout, lineAnim, lineStartX, scrollOffset, dynamicY)
                    layout.line.translationReplaces -> drawTranslationText(layout, lineAnim, lineStartX, scrollOffset, dynamicY, config, lyricsType == LyricsType.Static, replacement = true)
                    lyricsType == LyricsType.Static -> drawStaticLine(layout, lineAnim, lineStartX, scrollOffset, dynamicY)
                    lyricsType == LyricsType.Line || layout.line.lineTimed -> drawLineModeLine(layout, lineAnim, lineStartX, scrollOffset, dynamicY, config)
                    // Minimal Lyrics Mode shrinks inactive lines (a CSS `scale`: paint only, no reflow)
                    // about their start edge, like line-synced lines. Background vocals that pop out
                    // fold up into their top edge, under their line.
                    lineAnim.scale != 1f -> withTransform({
                        scale(lineAnim.scale, lineAnim.scale, Offset(
                            if (layout.isRightAligned) lineStartX + layout.totalWidth else lineStartX,
                            dynamicY + scrollOffset + if (layout.isBackground && config.backgroundPop) 0f else layout.height / 2f,
                        ))
                    }) { drawStandardLine(layout, lineAnim, lineStartX, scrollOffset, dynamicY, config) }
                    else -> drawStandardLine(layout, lineAnim, lineStartX, scrollOffset, dynamicY, config)
                }
                if (!layout.line.translationReplaces && layout.supplements.isNotEmpty()) {
                    drawTranslationText(layout, lineAnim, lineStartX, scrollOffset, dynamicY, config, lyricsType == LyricsType.Static)
                }
            }

            val shownLines = ArrayList<DrawnLine>()
            val stillLines = ArrayList<DrawnLine>()
            lineLayouts.forEachIndexed { lineIdx, layout ->
                val scrollOffset = scrollOffsetFor(lineIdx)
                val lineAnim = animStates.getOrNull(lineIdx) ?: return@forEachIndexed
                val dynamicY = dynamicYFor(lineIdx, dynamicYOffsets.getOrElse(lineIdx) { layout.yOffset })

                // Optimization: Don't draw invisible lines.
                if (lineAnim.opacity <= 0.01f) return@forEachIndexed

                // Optimization: Don't draw lines off-screen.
                val lineScreenY = scrollOffset + dynamicY
                if (lineScreenY < -layout.height * 3 || lineScreenY > canvasHeight + layout.height * 3) {
                    return@forEachIndexed
                }
                val drawn = DrawnLine(lineIdx, lineAnim, scrollOffset, dynamicY)
                shownLines += drawn
                if (!staticLines.isLive(lineIdx, lineAnim)) stillLines += drawn
            }
            val stillSet = StillSet(lineLayouts, config, size.width, size.height, stillLines)
            val plan = staticLines.plan(stillSet)
            if (plan == StaticLines.Plan.Direct) {
                // In order, as each line overlaps the one before it.
                shownLines.forEach { drawLine(it) }
            } else {
                if (plan == StaticLines.Plan.Record) {
                    staticLayer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                        stillLines.forEach { drawLine(it) }
                    }
                    staticLines.recordedAs(stillSet)
                }
                drawLayer(staticLayer)
                var still = 0
                shownLines.forEach { drawn ->
                    if (still < stillLines.size && stillLines[still].index == drawn.index) still++ else drawLine(drawn)
                }
            }
            staticLines.endFrame(animStates)

            val footerScroll = scrollOffsetFor(lineLayouts.size)
            drawFooterRows(footerLayouts, footerRowTops().map { it + footerScroll }, footerSlot.startPx, footerSlot.startPx + footerSlot.widthPx, footerAlignEnd, avatars, footerDim)
        }
    }
}

private fun maskBrush(stops: LyricsMaskStops) = Brush.verticalGradient(
    0f to Color.Transparent,
    stops.outerTop to Color.Transparent,
    stops.innerTop to Color.Black,
    stops.innerBottom to Color.Black,
    stops.outerBottom to Color.Transparent,
    1f to Color.Transparent,
)

/** Pinned credits: the lyrics fade out over this much just above them. */
private const val PINNED_FADE_DP = 48f

private data class MeasureKey(
    val config: RenderConfig,
    val widthPx: Float,
    val fontSizeScale: Float,
    val romanize: Boolean,
    val type: LyricsType,
    val fontKey: String,
    val translation: TranslationPresentation? = null,
    val romanizationMode: RomanizationMode = RomanizationMode.Replace,
)

private class MeasuredLyrics(val lines: List<Line>, val layouts: List<LineLayout>, val romanizationLines: List<Line>? = null)

private class ShownLyrics(
    val documentId: String,
    val lines: List<Line>,
    val layouts: List<LineLayout>,
    val lyricsType: LyricsType,
    val footer: LyricsFooter,
    val translation: TranslationPresentation? = null,
    val romanizationLines: List<Line>? = null,
)

/** Paused and this many frames without a change, the frame loop rests. */
private const val REST_AFTER_STILL_FRAMES = 30
/** How often a resting frame loop looks for a change (a seek while paused). */
private const val REST_POLL_MS = 150L

/** The nominal frame interval in seconds for a display refresh rate; 60 Hz when it's unknown. */
internal fun nominalFrameIntervalS(refreshRate: Float?): Float =
    1f / (refreshRate?.takeIf { it >= 1f } ?: 60f)

/**
 * Seconds to advance the animation by for this frame. A late frame advances by at most two
 * nominal intervals, so a stall doesn't leap the springs by the whole gap; the first frame
 * after a rest (or with no previous frame) advances by one interval.
 */
internal fun frameDeltaSeconds(frameTimeNanos: Long, lastFrameTimeNanos: Long, nominalIntervalS: Float, resumed: Boolean): Float {
    if (resumed || lastFrameTimeNanos == 0L) return nominalIntervalS
    return ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, 2f * nominalIntervalS)
}

/** The desktop lyric size (its 3.5rem cap), in CSS px. */
private const val REFERENCE_LYRIC_SIZE_PX = 56f

/**
 * Calculates the horizontal starting position of a line based on its alignment and the presence of duets.
 */
private fun getLineStartX(
    layout: LineLayout,
): Float {
    return if (layout.isRightAligned) {
        layout.contentStartX + layout.contentWidth - layout.totalWidth
    } else {
        layout.contentStartX
    }
}
