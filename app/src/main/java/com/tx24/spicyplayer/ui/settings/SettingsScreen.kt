package com.tx24.spicyplayer.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.rounded.History
import com.tx24.spicyplayer.update.ChangelogState
import com.tx24.spicyplayer.update.Releases
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.staticCompositionLocalOf
import com.tx24.spicyplayer.ui.components.drawBrandMark
import com.tx24.spicyplayer.ui.components.focusLayer
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import com.tx24.spicyplayer.ui.components.rememberBrandGlyph
import com.tx24.spicyplayer.ui.components.BrandTitleSize
import com.tx24.spicyplayer.ui.components.SpicyBrandLine
import com.tx24.spicyplayer.ui.theme.LocalSpicyPalette
import androidx.compose.ui.unit.em
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.ColorLens
import com.tx24.spicyplayer.ui.components.LocalUiAnimations

import androidx.compose.animation.core.snap

import androidx.compose.animation.core.AnimationSpec

import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Album
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import com.tx24.spicyplayer.translation.TranslationLanguages
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.tx24.spicyplayer.ui.components.LocalSettingsPeek
import com.tx24.spicyplayer.ui.components.SettingsPeek
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.haptics.withHaptic
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.LocalSettingsQuery
import com.tx24.spicyplayer.ui.components.Searchable
import com.tx24.spicyplayer.ui.components.SettingsSkeleton
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicySearchBar
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.UpdateStatus
import com.tx24.spicyplayer.update.UpdateViewModel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** A page of settings; one with a [parent] opens from a group card on that page instead of the first page. */
internal enum class SettingsPage(
    val title: String,
    val parent: SettingsPage? = null,
    /** False keeps the page out of search: its content isn't settings (the changelog). */
    val searchable: Boolean = true,
) {
    ThisSong("This song"),
    Lyrics("Lyrics"),
    Translation("Romanization & Translation"),
    ScrollSync("Scroll & Sync"),
    Background("Background"),
    NowPlaying("Now Playing"),
    Controls("Controls"),
    Theme("Theme"),
    Sources("Sources"),
    Device("Device"),
    FloatingLyrics("Floating lyrics", parent = Device),
    Advanced("Advanced"),
    /** Opened from the About card. */
    Changelog("Changelog", searchable = false),
    ;

    /** "Device › Floating lyrics": the title with every page above it, for search results. */
    val path: String get() = parent?.let { "${it.path} › $title" } ?: title
}

/** Opens a page over the one showing; what [SubPageRow]s call. */
internal val LocalOpenSettingsPage = staticCompositionLocalOf<(SettingsPage) -> Unit> { {} }

/**
 * The settings, over the song's own background, blurred and dimmed, opening like the pop-ups
 * do (fade in, 0.96 → 1 scale, 220 ms).
 * The first page lists groups; each opens its own page, which slides in from the right. A page
 * can hold group cards of its own ([SubPageCard]), which open further pages the same way.
 * Back, including Android's predictive back gesture, steps out one page at a time; on the first
 * page the gesture shrinks the whole screen toward the lyrics before it closes.
 *
 * Stays composed until its closing animation ends, then calls [onClosed].
 */
@Composable
internal fun SettingsScreen(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    updater: UpdateViewModel,
    settings: AppSettings,
    backdrop: HazeState?,
    contentPadding: PaddingValues,
    onClosed: () -> Unit,
    onOpenLyricsManager: () -> Unit = {},
    onOpenSpotifySearch: () -> Unit = {},
    /** Told when a peek clears the settings away (true) and when they come back (false). */
    onPeekChange: (Boolean) -> Unit = {},
    /** Pages to open straight onto, already in place: the ones open when the settings were last left. */
    restorePages: List<SettingsPage> = emptyList(),
    /** Told the open pages, deepest last, whenever they change. */
    onPagesChange: (List<SettingsPage>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val palette = settings.settingsTheme.palette
    val glyph = rememberBrandGlyph()
    // Interface animations off: every move is instant.
    val animate = LocalUiAnimations.current
    val modal: AnimationSpec<Float> = if (animate) tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal) else snap()
    val open = remember { Animatable(0f) }
    // How far a back gesture on the first page has gone, and from which edge.
    val backSwipe = remember { Animatable(0f) }
    var swipeFromLeft by remember { mutableStateOf(true) }
    // The pages opened over the first one, deepest last, each with how far it has slid in
    // (0 → 1) and whether its rows have replaced its skeleton.
    val stack = remember { restorePages.map { OpenPage(it, shown = true) }.toMutableStateList() }
    val onPagesChangeUpdated by rememberUpdatedState(onPagesChange)
    LaunchedEffect(Unit) { snapshotFlow { stack.map { it.page } }.collect { onPagesChangeUpdated(it) } }
    var closing by remember { mutableStateOf(false) }
    // Clearing the settings away to see the lyrics: the peek button, or a slider being dragged.
    val peek = remember { SettingsPeek(allowRows = { settings.peekWhileAdjusting }) }
    val peekAmount by animateFloatAsState(
        if (peek.active) 1f else 0f,
        if (animate) tween(PEEK_MS, easing = SpicyMotion.Standard) else snap(),
        label = "peek",
    )
    // Moving between pages or closing ends a peek.
    LaunchedEffect(stack.size, closing) { peek.end() }
    var pagesOrigin by remember { mutableStateOf(Offset.Zero) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    // A peek with the peek button (not a slider's) clears the settings out of the way entirely: the
    // player underneath takes touches again, and once faded out the pages step off screen, so no
    // unseen row takes a tap. Only the peek button stays, to come back with.
    val peekClear = peek.active && !peek.plate
    // Only a peek kept with a tap sends the pages away: under a held finger the peek button would go
    // with them, out from under it, and the hold would never see its lift (nor buzz for it).
    val pagesAway = peekClear && peek.latched && peekAmount >= 1f
    val onPeekChangeUpdated by rememberUpdatedState(onPeekChange)
    LaunchedEffect(peekClear) { onPeekChangeUpdated(peekClear) }
    DisposableEffect(Unit) { onDispose { onPeekChangeUpdated(false) } }
    var query by rememberSaveable { mutableStateOf("") }
    val homeScroll = rememberScrollState()
    // Building a page's real rows can take a few hundred ms (more in debug builds), and an
    // animation started alongside would be over before it's seen. So a page opens with a
    // skeleton, which is quick to build, and swaps in its rows once it has settled.
    var homeReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }  // the frame that builds the screen, before timing starts
        open.animateTo(1f, modal)
        homeReady = true
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            open.animateTo(0f, modal)
            onClosed()
        }
    }
    fun show(target: SettingsPage) {
        // A second tap while the page is still sliding in, or a page already open.
        if (stack.any { it.page == target }) return
        UsageStats.count(UsageCounter.page(target.name))
        val opened = OpenPage(target)
        stack += opened
        scope.launch {
            withFrameNanos { }
            opened.shift.animateTo(1f, modal)
            opened.ready = true
        }
    }
    fun back() {
        val top = stack.lastOrNull() ?: return close()
        scope.launch {
            top.shift.animateTo(0f, modal)
            stack.remove(top)
        }
    }

    PredictiveBackHandler(enabled = !closing) { progress ->
        val top = stack.lastOrNull()
        try {
            progress.collect { event ->
                if (top != null) {
                    top.shift.snapTo(1f - event.progress * PAGE_PEEK)
                } else {
                    swipeFromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                    backSwipe.snapTo(event.progress)
                }
            }
            if (top != null) {
                top.shift.animateTo(0f, modal)
                stack.remove(top)
            } else {
                close()
            }
        } catch (e: CancellationException) {
            scope.launch { if (top != null) top.shift.animateTo(1f, modal) else backSwipe.animateTo(0f, modal) }
            throw e
        }
    }
    // Declared after, so it's asked first: back while peeking brings the settings back.
    BackHandler(enabled = peek.active) { peek.end() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenOrigin = it.positionInRoot() },
    ) {
        // Takes every touch, so nothing reaches the lyrics underneath, except while peeking. A layer
        // of its own under the pages: off the screen's own modifiers, dropping it as a peek starts
        // doesn't cancel the press on the peek button that started it.
        if (!peekClear) Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        // The pop-up dim and blur, over the whole screen.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = open.value * (1f - BACKDROP_PEEK * backSwipe.value) * (1f - peekAmount) }
                .then(
                    if (backdrop != null) {
                        Modifier.hazeEffect(backdrop, SettingsBackdrop) {
                            // Blurs a third-size copy: the backdrop is soft anyway, and a full-size
                            // blur of the whole screen every frame costs the phone about half its
                            // frame rate.
                            inputScale = HazeInputScale.Fixed(BACKDROP_INPUT_SCALE)
                        }
                    } else {
                        // Without blur (Android before 12) the page's text would still read through.
                        Modifier.background(Color.Black.copy(alpha = 0.97f))
                    },
                ),
        )
        // The brand's glass: a deep tint, its ramp, and the logo cropped big off the top-right
        // corner, fading with the dim.
        palette.brand?.let { brand ->
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = open.value * (1f - BACKDROP_PEEK * backSwipe.value) * (1f - peekAmount) }
                    .drawBehind {
                        drawRect(brand.fieldDeep.copy(alpha = 0.14f))
                        drawBrandRamp(brand, fromAlpha = 0.26f, toAlpha = 0.4f)
                        val width = minOf(MARK_WIDTH.toPx(), size.width * 0.9f)
                        drawBrandMark(
                            glyph, brand.tint, alpha = 0.4f, width = width,
                            right = -width * MARK_RIGHT, top = -width * MARK_TOP, degrees = -14f,
                        )
                    },
            )
        }
        CompositionLocalProvider(
            LocalSpicyPalette provides palette,
            LocalSettingsBackdrop provides backdrop,
            LocalOpenSettingsPage provides ::show,
            LocalSettingsPeek provides peek,
        ) {
        BoxWithConstraints(
            Modifier
                .offset { if (pagesAway) IntOffset(0, PAGES_AWAY_PX) else IntOffset.Zero }
                .fillMaxSize()
                .graphicsLayer {
                    val scale = (MODAL_CLOSED_SCALE + (1f - MODAL_CLOSED_SCALE) * open.value) *
                        (1f - (1f - BACK_GESTURE_SCALE) * backSwipe.value)
                    scaleX = scale
                    scaleY = scale
                    alpha = open.value
                    translationX = (if (swipeFromLeft) 1f else -1f) * backSwipe.value * BACK_GESTURE_SHIFT.toPx()
                }
                .onGloballyPositioned { pagesOrigin = it.positionInRoot() }
                // Peeking draws the pages faded out, and only what's kept (the peek button, or the
                // dragged slider's row on a plate of its own) at full strength.
                .drawWithContent {
                    val amount = peekAmount
                    if (amount <= 0f) return@drawWithContent drawContent()
                    if (amount < 1f) faded(1f - amount) { drawContent() }
                    val keep = peek.keep?.translate(-pagesOrigin) ?: return@drawWithContent
                    if (peek.plate) {
                        val inset = SpicySpacing.S1.toPx()
                        drawRoundRect(
                            PEEK_PLATE.copy(alpha = PEEK_PLATE.alpha * amount),
                            keep.topLeft - Offset(inset, inset),
                            Size(keep.width + 2 * inset, keep.height + 2 * inset),
                            CornerRadius(SpicyRadii.Md.toPx()),
                        )
                    }
                    clipRect(keep.left, keep.top, keep.right, keep.bottom) { faded(amount) { this@drawWithContent.drawContent() } }
                },
        ) {
            val width = constraints.maxWidth.toFloat()
            // How far the page above level [index] has slid in: it moves this one aside.
            fun covered(index: Int): Float = stack.getOrNull(index)?.shift?.value ?: 0f
            // Every page stays built under the one above it, so going back doesn't wait on rebuilding it.
            run {
                Page(
                    title = "Settings",
                    onBack = ::close,
                    contentPadding = contentPadding,
                    scroll = homeScroll,
                    ready = homeReady,
                    skeleton = { HomeSkeleton() },
                    modifier = Modifier
                        .graphicsLayer {
                            val over = covered(0)
                            translationX = -over * width * HOME_PARALLAX
                            alpha = 1f - over
                        }
                        .focusLayer(current = stack.isEmpty(), ready = homeReady),
                    header = { SpicySearchBar(query, { query = it }, Modifier.fillMaxWidth().padding(top = SpicySpacing.S4)) },
                ) {
                    if (query.isBlank()) {
                        HomeGroups(state, settings, updater, onOpen = ::show)
                    } else {
                        SearchResults(query, state, viewModel, updater, settings, onOpenLyricsManager, onOpenSpotifySearch)
                    }
                }
            }
            stack.forEachIndexed { index, opened ->
                key(opened.page) {
                    Page(
                        title = opened.page.title,
                        onBack = ::back,
                        contentPadding = contentPadding,
                        scroll = rememberScrollState(),
                        ready = opened.ready,
                        skeleton = { PageSkeleton(opened.page) },
                        modifier = Modifier
                            .graphicsLayer {
                                val shift = opened.shift.value
                                val over = covered(index + 1)
                                translationX = (1f - shift) * width * PAGE_TRAVEL - over * width * HOME_PARALLAX
                                alpha = shift * (1f - over)
                            }
                            // Keeps touches from falling through to the page underneath.
                            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                            .focusLayer(current = index == stack.lastIndex, ready = opened.ready),
                        header = { Spacer(Modifier.height(SpicySpacing.S2)) },
                    ) {
                        PageContent(opened.page, state, viewModel, updater, settings, onOpenLyricsManager, onOpenSpotifySearch)
                    }
                }
            }
        }
        // The peek button, standing in for the one on the page while the pages are away: a tap
        // on it brings the settings back.
        val keep = peek.keep
        if (pagesAway && keep != null) {
            GlassButton(
                onClick = { peek.end() },
                contentDescription = "Back to settings",
                size = 40.dp,
                modifier = Modifier.offset {
                    IntOffset((keep.left - screenOrigin.x).roundToInt(), (keep.top - screenOrigin.y).roundToInt())
                },
            ) {
                Icon(Icons.Rounded.Visibility, null, tint = SpicyColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        }
    }
}

/** The peek button: hold it to see the lyrics behind the settings, or tap it to keep them clear. */
@Composable
private fun PeekButton() {
    val peek = LocalSettingsPeek.current ?: return
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var downAt by remember { mutableLongStateOf(0L) }
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    GlassButton(
        onClick = {},
        contentDescription = "Peek at the lyrics",
        // The peek starts on the press, so that's where it buzzes; a hold ticks again as it ends.
        releaseHaptic = false,
        // A key can't be held to peek, so a press keeps the lyrics clear and the next brings the settings back.
        onKeyClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            if (peek.active) peek.end() else {
                peek.start(bounds, plate = false)
                peek.latched = true
            }
        },
        size = 40.dp,
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            // Followed to the finger's lift wherever the button goes meanwhile (it steps off
            // screen with the pages), which a press on the button itself would take as cancelled.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    downAt = SystemClock.uptimeMillis()
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    peek.start(bounds, plate = false)
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                    if (SystemClock.uptimeMillis() - downAt < PEEK_TAP_MS) {
                        peek.latched = true
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        peek.end()
                    }
                }
            },
    ) {
        Icon(Icons.Rounded.Visibility, null, tint = SpicyColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Draws [block] into a layer at [alpha]. */
private inline fun DrawScope.faded(alpha: Float, block: () -> Unit) {
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

@Composable
private fun PageContent(
    page: SettingsPage,
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    updater: UpdateViewModel,
    settings: AppSettings,
    onOpenLyricsManager: () -> Unit,
    onOpenSpotifySearch: () -> Unit,
) {
    when (page) {
        SettingsPage.ThisSong -> ThisSongContent(state, viewModel, onOpenLyricsManager, onOpenSpotifySearch)
        SettingsPage.Lyrics -> LyricsContent(state, viewModel, settings)
        SettingsPage.Translation -> RomanizationTranslationContent(state, viewModel, settings)
        SettingsPage.ScrollSync -> ScrollSyncContent(state, viewModel, settings)
        SettingsPage.Background -> BackgroundContent(settings, viewModel)
        SettingsPage.NowPlaying -> NowPlayingContent(settings, viewModel)
        SettingsPage.Controls -> ControlsContent(settings)
        SettingsPage.Theme -> ThemeContent(settings)
        SettingsPage.Sources -> SourcesContent(state, viewModel)
        SettingsPage.Device -> DeviceContent(settings)
        SettingsPage.FloatingLyrics -> FloatingLyricsContent(settings)
        SettingsPage.Advanced -> AdvancedContent(state, viewModel, updater, settings)
        SettingsPage.Changelog -> ChangelogContent(updater)
    }
}

/** A page opened over the first one: how far it has slid in, and whether its rows have replaced its skeleton. */
@Stable
private class OpenPage(val page: SettingsPage, shown: Boolean = false) {
    val shift = Animatable(if (shown) 1f else 0f)
    var ready by mutableStateOf(shown)
}

/** A scrolling page under a glass back button and its title. */
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    scroll: ScrollState,
    ready: Boolean,
    skeleton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding)
            .padding(start = SpicySpacing.S4, end = SpicySpacing.S4, top = SpicySpacing.S2, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton(onClick = onBack, contentDescription = "Back", size = 40.dp) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = SpicyColors.TextPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(SpicySpacing.S3))
            if (SpicyColors.brand != null) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpicyBrandLine()
                    Text(title, style = SpicyType.Title.copy(fontSize = BrandTitleSize, lineHeight = 1.1.em, letterSpacing = (-0.025f).em))
                }
            } else {
                Text(title, Modifier.weight(1f), style = SpicyType.Title.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.size(SpicySpacing.S3))
            PeekButton()
        }
        header()
        if (ready) {
            val animate = LocalUiAnimations.current
            val fade = remember { Animatable(if (animate) 0f else 1f) }
            LaunchedEffect(Unit) { if (animate) fade.animateTo(1f, tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard)) }
            CompositionLocalProvider(LocalPageScroll provides scroll) {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = fade.value }, content = content)
            }
        } else {
            skeleton()
        }
    }
}

/** The open page's scroll, for content that scrolls it itself (a dragged source card at the edges). */
internal val LocalPageScroll = staticCompositionLocalOf<ScrollState?> { null }

@Composable
private fun HomeSkeleton() {
    Spacer(Modifier.height(SpicySpacing.S4))
    SettingsSkeleton(rows = 1, cards = true)
    Spacer(Modifier.height(SpicySpacing.S3))
    SettingsSkeleton(rows = 4, modifier = Modifier.outlinedCard())
}

@Composable
private fun PageSkeleton(page: SettingsPage) {
    when (page) {
        SettingsPage.ThisSong -> {
            SettingsSkeleton(rows = 1, cards = true)
            SettingsSkeleton(rows = 5)
        }
        SettingsPage.Lyrics -> SettingsSkeleton(rows = 9)
        SettingsPage.Translation -> SettingsSkeleton(rows = 6)
        SettingsPage.ScrollSync -> SettingsSkeleton(rows = 9)
        SettingsPage.Background -> SettingsSkeleton(rows = 4)
        SettingsPage.NowPlaying -> SettingsSkeleton(rows = 6)
        SettingsPage.Controls -> SettingsSkeleton(rows = 6)
        SettingsPage.Theme -> SettingsSkeleton(rows = 4)
        SettingsPage.Sources -> SettingsSkeleton(rows = 8, cards = true)
        SettingsPage.Device -> SettingsSkeleton(rows = 22)
        SettingsPage.FloatingLyrics -> SettingsSkeleton(rows = 8)
        SettingsPage.Advanced -> SettingsSkeleton(rows = 10)
        SettingsPage.Changelog -> SettingsSkeleton(rows = 6, cards = true)
    }
}

@Composable
private fun HomeGroups(state: PlayerUiState, settings: AppSettings, updater: UpdateViewModel, onOpen: (SettingsPage) -> Unit) {
    Spacer(Modifier.height(SpicySpacing.S4))
    Column(Modifier.fillMaxWidth().outlinedCard()) {
        GroupRow(Icons.Rounded.MusicNote, SettingsPage.ThisSong.title, "${state.title} · ${songSummary(state)}") {
            onOpen(SettingsPage.ThisSong)
        }
    }
    Spacer(Modifier.height(SpicySpacing.S3))
    Column(Modifier.fillMaxWidth().outlinedCard()) {
        GroupRow(
            Icons.Rounded.TextFields,
            SettingsPage.Lyrics.title,
            listOfNotNull(
                when {
                    settings.lyricsStyle == LyricsStyle.AppleMusic -> "Apple Music style"
                    settings.simpleLyricsMode && settings.minimalLyricsMode -> "Simple and Minimal"
                    settings.simpleLyricsMode -> "Simple"
                    settings.minimalLyricsMode -> "Minimal"
                    else -> null
                },
                (if (settings.originalWordMotion) "Original word motion" else "Boosted word motion")
                    .takeIf { settings.lyricsStyle == LyricsStyle.Spicy },
                "${settings.lyricsSizePortrait}% text".takeIf { settings.lyricsSizePortrait != 100 },
                when (settings.lyricsFont) {
                    LyricsFont.Default -> null
                    LyricsFont.System -> "system font"
                    LyricsFont.Custom -> settings.customFontName.ifBlank { "custom font" }
                },
                "no blur".takeIf { !settings.distanceBlur || settings.lowPerformance },
                "no glow".takeIf { !settings.glow || settings.lowPerformance },
            ).joinToString().replaceFirstChar(Char::uppercase),
        ) { onOpen(SettingsPage.Lyrics) }
        GroupDivider()
        GroupRow(Icons.Rounded.Translate, SettingsPage.Translation.title,
            "Romanization: ${settings.romanizationMode.label} · " +
                "${TranslationLanguages.name(state.translationPreferences.targetLanguage)} · " +
                if (state.translationPreferences.automatic) "Automatic" else "On demand",
        ) { onOpen(SettingsPage.Translation) }
        GroupDivider()
        GroupRow(
            Icons.Rounded.Timer,
            SettingsPage.ScrollSync.title,
            listOfNotNull(
                if (state.lyricDelayMs == 0) "No delay on ${state.outputLabel}" else "${state.lyricDelayMs.signed()} ms on ${state.outputLabel}",
                "early scroll".takeIf { settings.scrollLeadEnabled },
                "smooth scrolling".takeIf { settings.smoothScrolling },
            ).joinToString(),
        ) { onOpen(SettingsPage.ScrollSync) }
        GroupDivider()
        GroupRow(
            Icons.Rounded.Palette,
            SettingsPage.Background.title,
            listOfNotNull(
                when (settings.backgroundType) {
                    BackgroundType.Default -> "Dynamic"
                    else -> settings.backgroundType.label
                },
                "still".takeIf { settings.backgroundType.moving && (settings.staticBackground || settings.lowPerformance) },
                "Canvas where available".takeIf { settings.canvasBackground && !settings.lowPerformance },
                "animated".takeIf { settings.backgroundType == BackgroundType.CoverArt && settings.animatedBackground && !settings.lowPerformance },
                "follows the animated cover".takeIf {
                    settings.backgroundType == BackgroundType.Default && settings.backgroundFollowsMotion && settings.animatedCover &&
                        !settings.staticBackground && !settings.lowPerformance
                },
            ).joinToString(),
        ) { onOpen(SettingsPage.Background) }
        GroupDivider()
        GroupRow(
            Icons.Rounded.Album,
            SettingsPage.NowPlaying.title,
            listOfNotNull(
                if (settings.hideHeader) "Header hidden" else "${settings.headerSize.label} header",
                "animated cover".takeIf { settings.animatedCover && !settings.lowPerformance },
                (if (StageArt.Canvas in settings.stageArt) "cover stage with Canvas" else "cover stage")
                    .takeIf { settings.stageArt.isNotEmpty() && !settings.lowPerformance },
                "release year".takeIf { settings.releaseYearPosition != ReleaseYearPosition.Off },
            ).joinToString(),
        ) { onOpen(SettingsPage.NowPlaying) }
        GroupDivider()
        GroupRow(
            Icons.Rounded.TouchApp,
            SettingsPage.Controls.title,
            listOf(
                if (settings.autoHideControls) "Hide after ${settings.controlsHideDelay.label}" else "Always shown",
                "${listOf(settings.playerButtons, settings.resyncButton, settings.expandButton, settings.quickSettingsButton, settings.rotateButton, settings.pipButton, settings.lyricsManagerButton, settings.queueButton, settings.sourceButton).count { it }} of 9 extra buttons",
            ).joinToString(),
        ) { onOpen(SettingsPage.Controls) }
        GroupDivider()
        GroupRow(
            Icons.Rounded.ColorLens,
            SettingsPage.Theme.title,
            listOf(
                "${settings.settingsTheme.label} settings",
                "${settings.popupTheme.label.lowercase()} pop-ups",
                "${settings.appIcon.label.lowercase()} icon",
            ).joinToString(),
        ) { onOpen(SettingsPage.Theme) }
    }
    Spacer(Modifier.height(SpicySpacing.S3))
    Column(Modifier.fillMaxWidth().outlinedCard()) {
        GroupRow(
            Icons.Rounded.Layers,
            SettingsPage.Sources.title,
            state.sourceRanking.let { ranking ->
                "${ranking.origins.count { com.tx24.spicyplayer.network.data.LyricsSourceSettings.isOn(ranking, it.id) }} of ${ranking.origins.size} on"
            },
        ) { onOpen(SettingsPage.Sources) }
        GroupDivider()
        GroupRow(
            Icons.Rounded.Smartphone,
            SettingsPage.Device.title,
            listOfNotNull(
                if (settings.keepScreenOn) "Stays on while playing" else "Turns off as usual",
                if (settings.highRefreshRate) "full refresh rate" else "60 Hz",
                "low performance".takeIf { settings.lowPerformance },
                "haptics to the music".takeIf { settings.musicHaptics },
            ).joinToString(),
        ) { onOpen(SettingsPage.Device) }
        GroupDivider()
        GroupRow(Icons.Rounded.Tune, SettingsPage.Advanced.title, "Updates, cache and diagnostics") { onOpen(SettingsPage.Advanced) }
    }
    Spacer(Modifier.height(SpicySpacing.S6))
    AboutCard(state, updater, settings)
}

/** A row of a grouped card that opens a page: glyph, name, a one-line summary and a chevron. */
@Composable
internal fun GroupRow(
    icon: ImageVector,
    label: String,
    summary: String,
    trailing: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) SpicyColors.TintBg else Color.Transparent,
        tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard),
        label = "groupPress",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .focusRing(RectangleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = 14.dp, vertical = SpicySpacing.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(SpicySpacing.S3))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = SpicyType.Body.copy(fontWeight = FontWeight.Medium))
            Text(summary, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(trailing, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun GroupDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SpicyColors.Hairline))
}

/**
 * A grouped card inside a page, like the first page's: [SubPageRow]s that open further pages.
 * Search lists those pages' rows on their own, so the card steps aside while searching.
 */
@Composable
internal fun SubPageCard(content: @Composable ColumnScope.() -> Unit) {
    if (LocalSettingsQuery.current.isNotBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = SpicySpacing.S2).outlinedCard(), content = content)
}

/** A [GroupRow] that opens [page] over the one showing. */
@Composable
internal fun SubPageRow(page: SettingsPage, icon: ImageVector, summary: String) {
    val open = LocalOpenSettingsPage.current
    GroupRow(icon, page.title, summary) { open(page) }
}

/**
 * The footer card, kept short: the name, version, channel and update status with a manual check,
 * then the links out two by two. The status checks quietly on its own and claims nothing until it knows.
 */
@Composable
private fun AboutCard(state: PlayerUiState, updater: UpdateViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val update by updater.state.collectAsState()
    val openPage = LocalOpenSettingsPage.current
    LaunchedEffect(Unit) {
        updater.checkQuietly(settings.includePrereleases)
        updater.loadChangelog()
    }
    val changelog by updater.changelog.collectAsState()
    // Found or hidden as one card; inside it, everything shows.
    Searchable("About", "Version", "GitHub", "Star", "Spicy Lyrics Mobile", "Spicy Player", "Updates", "Check for updates", "Feedback", "Bug", "Report", "Feature", "Suggest", "Discord", "Changelog", "What's new", "Release notes") {
        CompositionLocalProvider(LocalSettingsQuery provides "") {
            Column(Modifier.fillMaxWidth().outlinedCard()) {
                // The app as the launcher shows it: its icon, name and version.
                Row(
                    Modifier.padding(SpicySpacing.S4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
                ) {
                    Image(
                        painterResource(settings.appIcon.cover),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(SpicyRadii.Md)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Spicy Lyrics Mobile", style = SpicyType.Headline)
                        Text(versionLine(update.status, updater.enabled), style = SpicyType.Footnote.copy(color = SpicyColors.TextSecondary))
                    }
                    if (updater.enabled) UpdateButton(update.status, onUpdate = updater::show) { updater.check(settings.includePrereleases) }
                }
                GroupDivider()
                // Stays in the app, so a chevron like the pages above, where the links below leave it.
                GroupRow(Icons.Rounded.History, SettingsPage.Changelog.title, changelogSummary(changelog)) { openPage(SettingsPage.Changelog) }
                GroupDivider()
                Column(Modifier.padding(SpicySpacing.S4), verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        FooterLink("Star on GitHub", Modifier.weight(1f)) { openUrl(context, PROJECT_URL) }
                        FooterLink("Discord", Modifier.weight(1f), DISCORD_BLURPLE) { openUrl(context, DISCORD_URL) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        // Fills in the form's version and debug info; the reporter sees both before sending.
                        FooterLink("Report a bug", Modifier.weight(1f), BUG_PINK) {
                            val debug = Uri.encode(debugReport(context, state).take(MAX_PREFILLED_DEBUG))
                            openUrl(context, "$PROJECT_URL/issues/new?template=bug_report.yml&version=${Uri.encode(BuildConfig.VERSION_NAME)}&debug=$debug")
                        }
                        FooterLink("Suggest a feature", Modifier.weight(1f)) { openUrl(context, "$PROJECT_URL/issues/new?template=feature_request.yml") }
                    }
                }
            }
        }
    }
}

/** "What's new in v0.16.1: 3 changed · 11 fixed", once the changelog has this version. */
private fun changelogSummary(state: ChangelogState): String {
    val release = (state as? ChangelogState.Loaded)?.releases
        ?.firstOrNull { Releases.compareVersions(it.version, BuildConfig.VERSION_NAME) == 0 }
        ?: return "Every release's notes"
    val counts = changeCounts(release.notes)
    return if (counts.isEmpty()) "What's new in v${release.version}" else "What's new in v${release.version}: $counts"
}

@Composable
private fun FooterLink(label: String, modifier: Modifier, brand: Color? = null, onClick: () -> Unit) {
    SpicyButton(label, onClick, modifier, horizontalPadding = 8.dp, brand = brand, external = true)
}

/** "v0.6.0 · pre-release · Latest": the status only once a check has said something. */
@Composable
private fun versionLine(status: UpdateStatus, enabled: Boolean): AnnotatedString = buildAnnotatedString {
    append("v${BuildConfig.VERSION_NAME} · ${buildChannel()}")
    if (!enabled) return@buildAnnotatedString
    val (text, color) = when (status) {
        UpdateStatus.UpToDate -> "Latest" to SpicyColors.StatusSuccess
        is UpdateStatus.Available -> "Update available" to SpicyColors.StatusWarning
        is UpdateStatus.ReadyToInstall -> "Update ready" to SpicyColors.StatusWarning
        is UpdateStatus.Failed -> "Couldn't check" to SpicyColors.TextTertiary
        else -> return@buildAnnotatedString
    }
    append(" · ")
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(text) }
}

/** Checks on demand; once an update is found, opens it instead. */
@Composable
private fun UpdateButton(status: UpdateStatus, onUpdate: () -> Unit, onCheck: () -> Unit) {
    when (status) {
        is UpdateStatus.Available -> SpicyButton("Update", onUpdate, brand = SpicyColors.StatusWarning)
        UpdateStatus.Checking -> SpicyButton("Checking…", {}, enabled = false)
        is UpdateStatus.Downloading -> SpicyButton("${(status.progress * 100).toInt()}%", {}, enabled = false)
        is UpdateStatus.Installing -> SpicyButton("Installing", {}, enabled = false)
        is UpdateStatus.ReadyToInstall -> SpicyButton("Install", onUpdate, brand = SpicyColors.StatusWarning)
        UpdateStatus.Idle, UpdateStatus.UpToDate, is UpdateStatus.Failed -> SpicyButton("Check for updates", onCheck)
    }
}

/**
 * Which channel this build is from: every 0.x release, and any version with a suffix ("1.0.0-beta"),
 * is published as a pre-release, the same rule the update check uses to offer pre-releases.
 */
private fun buildChannel(): String = when {
    BuildConfig.DEBUG -> "debug"
    BuildConfig.VERSION_NAME.startsWith("0.") || '-' in BuildConfig.VERSION_NAME -> "pre-release"
    else -> "stable"
}

private val DISCORD_BLURPLE = Color(88, 101, 242)
private val BUG_PINK = Color(255, 94, 138)

/** Every page's rows at once, each hiding unless it matches the search. */
@Composable
private fun SearchResults(query: String, state: PlayerUiState, viewModel: ExternalPlaybackViewModel, updater: UpdateViewModel, settings: AppSettings, onOpenLyricsManager: () -> Unit, onOpenSpotifySearch: () -> Unit) {
    CompositionLocalProvider(LocalSettingsQuery provides query) {
        EmptyOr(
            content = {
                Column(Modifier.fillMaxWidth()) {
                    SettingsPage.entries.filter { it.searchable }.forEach { page ->
                        SettingsSection(page.path) { PageContent(page, state, viewModel, updater, settings, onOpenLyricsManager, onOpenSpotifySearch) }
                    }
                    AboutCardSpacer(state, updater, settings)
                }
            },
            empty = {
                Text(
                    "No settings match “${query.trim()}”",
                    style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = SpicySpacing.S6),
                )
            },
        )
    }
}

@Composable
private fun AboutCardSpacer(state: PlayerUiState, updater: UpdateViewModel, settings: AppSettings) {
    Searchable("About", "Version", "GitHub", "Star", "Spicy Lyrics Mobile", "Spicy Player", "Updates", "Check for updates", "Feedback", "Bug", "Report", "Feature", "Suggest", "Discord", "Changelog", "What's new", "Release notes") { Spacer(Modifier.height(SpicySpacing.S4)) }
    AboutCard(state, updater, settings)
}

/** [content], or [empty] in its place when [content] lays out with no height. */
@Composable
private fun EmptyOr(content: @Composable () -> Unit, empty: @Composable () -> Unit) {
    Layout(contents = listOf(content, empty)) { (contentMeasurables, emptyMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val placeables = contentMeasurables.map { it.measure(loose) }.takeIf { list -> list.sumOf { it.height } > 0 }
            ?: emptyMeasurables.map { it.measure(loose) }
        layout(constraints.maxWidth, placeables.sumOf { it.height }) {
            var y = 0
            placeables.forEach { it.place(0, y); y += it.height }
        }
    }
}

internal fun Int.signed(): String = if (this > 0) "+$this" else toString()
internal fun Long.signed(): String = if (this > 0) "+$this" else toString()

/** Behind settings, the page dims to 45% black over a strong blur of the lyrics. */
/** What the settings pages are over: the lyrics, blurred as [SettingsBackdrop]. Null without blur. */
internal val LocalSettingsBackdrop = staticCompositionLocalOf<HazeState?> { null }

internal val SettingsBackdrop = HazeStyle(
    backgroundColor = Color.Black,
    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.45f))),
    blurRadius = 32.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(Color.Black.copy(alpha = 0.8f)),
)

internal const val BACKDROP_INPUT_SCALE = 1f / 3f

/** The scale a pop-up opens from and closes to. */
private const val MODAL_CLOSED_SCALE = 0.96f

/** Material's predictive back for a full screen: down to 90%, nudged 8dp away from the edge. */
private const val BACK_GESTURE_SCALE = 0.9f
private val BACK_GESTURE_SHIFT = 8.dp

/** The mark over the themed backdrop: 420px across, 130px off the right edge and 150px off the top. */
private val MARK_WIDTH = 420.dp
private const val MARK_RIGHT = 130f / 420f
private const val MARK_TOP = 150f / 420f

/** How much the lyrics show through while a back gesture closes settings. */
private const val BACKDROP_PEEK = 0.35f

/** How far a group's page moves when it slides in, as a share of the width. */
private const val PAGE_TRAVEL = 0.3f
private const val HOME_PARALLAX = 0.15f

/** How far a back gesture pulls a group's page toward the first page before letting go. */
private const val PAGE_PEEK = 0.5f

/** How long the settings take to clear away for a peek, and to come back. */
private const val PEEK_MS = 250
/** A press on the peek button shorter than this holds the peek open instead of ending it on release. */
private const val PEEK_TAP_MS = 250L
/** Far enough down that the pages, stepped away while peeking, are off any screen. */
private const val PAGES_AWAY_PX = 100_000
/** The plate a dragged slider's row sits on while the rest of the settings are cleared away. */
private val PEEK_PLATE = Color(0xE0141414)

private const val PROJECT_URL = "https://github.com/spicylyrics/mobile"
// Keeps the prefilled form link well under the length GitHub accepts.
private const val MAX_PREFILLED_DEBUG = 3000

// The server invite, not the thread link: a thread link only opens for people already in the server.
internal const val DISCORD_URL = "https://discord.com/invite/uqgXU5wh8j"

internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
