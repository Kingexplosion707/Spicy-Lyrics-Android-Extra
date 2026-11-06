package com.tx24.spicyplayer.ui.controls

import androidx.compose.ui.zIndex
import kotlin.math.abs
import com.tx24.spicyplayer.ui.components.scrollHints
import com.tx24.spicyplayer.ui.components.LocalGlassCast
import androidx.compose.runtime.CompositionLocalProvider
import com.tx24.spicyplayer.ui.components.LocalGlyphPressed
import com.tx24.spicyplayer.ui.components.ExpandGlyph
import com.tx24.spicyplayer.ui.components.LyricsManagerGlyph
import com.tx24.spicyplayer.ui.components.MoreGlyph
import com.tx24.spicyplayer.ui.components.PictureInPictureGlyph
import com.tx24.spicyplayer.ui.components.QueueGlyph
import com.tx24.spicyplayer.ui.components.QuickSettingsGlyph
import com.tx24.spicyplayer.ui.components.ResyncGlyph
import com.tx24.spicyplayer.ui.components.RotateGlyph
import com.tx24.spicyplayer.ui.components.SettingsGlyph
import com.tx24.spicyplayer.ui.components.SourceGlyph
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.components.SwapSideGlyph
import com.tx24.spicyplayer.ui.components.TranslateGlyph
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.components.SelectMenuPosition
import com.tx24.spicyplayer.ui.components.MENU_FILL
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import com.tx24.spicyplayer.ui.theme.SpicyPalette

import com.tx24.spicyplayer.ui.components.LocalUiAnimations

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.snap

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.components.keyClickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.spicy.models.MiniCredit
import com.tx24.spicyplayer.ui.nowplaying.MiniCreditLine
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.LocalBackdrop
import com.tx24.spicyplayer.ui.nowplaying.SessionCustomAction
import com.tx24.spicyplayer.ui.nowplaying.SlidingAlignment
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

/**
 * Transport state and actions for [LyricsControls].
 *
 * @param customActions the player's own buttons (shuffle, repeat, like, ...) as published in its
 * PlaybackState. Shuffle and repeat take the slots beside the skips; the rest become floating
 * buttons.
 */
class PlaybackControlsState(
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val durationMs: Long,
    /** The player's position (not the lyric clock, which carries the output delay). */
    val positionMs: () -> Long,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val customActions: List<SessionCustomAction> = emptyList(),
    val onCustomAction: (String) -> Unit = {},
    /** Re-reads the player's position, for when the lyrics have drifted. */
    val onResync: () -> Unit = {},
    val onTranslate: (() -> Unit)? = null,
    val translated: Boolean = false,
    /** A translation is on its way: the button pulses until it lands. */
    val translating: Boolean = false,
    /** Hides the settings button: while settings are open and only peeked through, another would stack on them. */
    val hideSettings: Boolean = false,
    /** Turns the screen to landscape, or back once [rotated]; null hides the button. */
    val onRotate: (() -> Unit)? = null,
    val rotated: Boolean = false,
    /** Where the lyrics come from, as one short line under the timeline; null shows none. */
    val credit: MiniCredit? = null,
)

/**
 * The lyrics screen's bottom controls, top to bottom: the timeline with its times underneath,
 * the playback row (shuffle, previous, play/pause, next, repeat) as plain glyphs, and the
 * floating glass buttons (romanize, the player's other actions, settings). Behind them, the lyrics
 * blur and darken towards the bottom, starting [SHADE_REACH] above the controls. While not
 * [interactive] (hidden), touches on the controls are swallowed.
 *
 * [shown] (0 hidden .. 1 shown) is their opacity, for the show/hide fade.
 */
@Composable
fun LyricsControls(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Shows the Lyrics Manager button when set. */
    onOpenLyricsManager: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    /** Shows the quick settings button when set. */
    onOpenQuickSettings: (() -> Unit)? = null,
    /** Shows the picture-in-picture button when set. */
    onEnterPip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    interactive: Boolean = true,
    shown: () -> Float = { 1f },
    /** Height of the controls themselves, without the shade above them, in px. */
    onControlsHeight: (Int) -> Unit = {},
    /** How much of the shade shows (1 = all), on top of [shown]. */
    shade: () -> Float = { 1f },
    /** A short landscape screen: everything in one low bar ([ControlsBar]) instead of three rows. */
    wide: Boolean = false,
    /** The cutout's insets: the controls keep clear of them, the shade runs under them to the screen's edges. */
    insets: PaddingValues = PaddingValues(0.dp),
    showResync: Boolean = true,
    showExpand: Boolean = true,
) {
    val backdrop = LocalBackdrop.current
    Box(modifier.fillMaxWidth()) {
        // The shade: blur that grows from nothing at the top of this area to full strength at the
        // controls, plus a light darkening, so white controls read over anything.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = shown() * shade() }
                .then(
                    backdrop?.let {
                        Modifier.hazeEffect(it) {
                            backgroundColor = Color.Black
                            blurRadius = SHADE_BLUR
                            // Half-size: the shade spans the screen's width under moving lyrics.
                            inputScale = HazeInputScale.Auto
                            noiseFactor = 0f
                            tints = emptyList()
                            fallbackTint = HazeTint(Color.Black.copy(alpha = 0.25f))
                            progressive = HazeProgressive.verticalGradient(
                                easing = ShadeEasing,
                                startIntensity = 0f,
                                endIntensity = 1f,
                            )
                        }
                    } ?: Modifier,
                )
                .background(ShadeGradient),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = if (wide) BAR_SHADE_REACH else SHADE_REACH)
                .padding(insets)
                .graphicsLayer { alpha = shown() }
                .onSizeChanged { onControlsHeight(it.height) }
                // The controls own their whole area, gaps and margins included: without a pointer
                // handler here, a touch between two buttons falls through to the lyrics and seeks.
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (wide) {
                ControlsBar(controls, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, shown, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip)
            } else {
                ControlsColumn(controls, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, shown, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip)
            }
            // Hidden controls swallow touches too: a touch there only brings them back, rather
            // than pressing a button nobody can see.
            if (!interactive) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown().consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ControlsColumn(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    shown: () -> Float,
    showResync: Boolean,
    showExpand: Boolean,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
) {
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }

    Column(
        modifier = Modifier
            .widthIn(max = CONTROLS_MAX_WIDTH)
            .fillMaxWidth()
            .padding(bottom = BOTTOM_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Timeline(controls, shown, Modifier.padding(horizontal = SIDE_MARGIN))
        // The transport sits in its own air, the same gap above and below it (plus the glyphs'
        // touch padding), so the three rows read as separate.
        Spacer(Modifier.height(ROW_GAP))
        PlaybackRow(controls, shuffle, repeat, Modifier.padding(horizontal = SIDE_MARGIN - SpicySpacing.S2))
        Spacer(Modifier.height(ROW_GAP))
        // A player that adds enough buttons to run off the screen gets the rest in a More menu.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S2), contentAlignment = Alignment.Center) {
            FloatingButtons(
                floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide = null, showResync = showResync, showExpand = showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                maxWidth, minSize = FLOATING_SIZE, maxSize = FLOATING_SIZE, minGap = SpicySpacing.S2, maxGap = SpicySpacing.S4,
            )
        }
    }
}

/**
 * The controls as one low bar for a short landscape screen, laid out like a desktop player bar:
 * the playback row over the timeline in the middle, the floating glass buttons on the right,
 * and the left side kept empty so the middle stays centred on the page.
 */
@Composable
private fun ControlsBar(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    shown: () -> Float,
    showResync: Boolean,
    showExpand: Boolean,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
) {
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = SIDE_MARGIN, end = SIDE_MARGIN, bottom = BAR_BOTTOM_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.width(BAR_MIDDLE_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
            PlaybackRow(controls, shuffle, repeat, Modifier, skipWidth = BAR_SKIP_WIDTH)
            Timeline(controls, shown, Modifier, showCredit = false)
        }
        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            FloatingButtons(
                floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide = null, showResync = showResync, showExpand = showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                maxWidth - SpicySpacing.S4, minSize = COVER_BUTTON_MIN, maxSize = COVER_BUTTON_MAX, minGap = COVER_BUTTON_GAP, maxGap = COVER_BUTTON_GAP,
            )
        }
    }
}

/** One floating glass button. Those with the lowest [keep] go into the More menu first. */
private class FloatingAction(
    val label: String,
    val keep: Int,
    val onClick: () -> Unit,
    /** Draws the glyph; takes the icon's size as drawn for a 48dp button and scales it. */
    val icon: @Composable (scaled: (Float) -> Dp) -> Unit,
)

/** How many buttons a row holds ([slots], the More button among them), and how big and far apart. */
internal data class ButtonFit(val slots: Int, val size: Dp, val gap: Dp)

/**
 * Fits [count] buttons into [width]: as big as fits up to [maxSize]. When they don't all fit at
 * [minSize] and [minGap], the row keeps as many as do, the last of them being the More button.
 */
internal fun fitButtons(width: Dp, count: Int, minSize: Dp, maxSize: Dp, minGap: Dp, maxGap: Dp): ButtonFit {
    val all = (width - minGap * (count - 1)) / count
    val slots = if (all >= minSize) count else ((width + minGap) / (minSize + minGap)).toInt().coerceIn(2, count)
    val size = ((width - minGap * (slots - 1)) / slots).coerceIn(minSize, maxSize)
    val gap = if (slots > 1) ((width - size * slots) / (slots - 1)).coerceIn(minGap, maxGap) else 0.dp
    return ButtonFit(slots, size, gap)
}

/** The buttons in [width], with the ones that don't fit behind a More button at the row's end. */
@Composable
private fun FloatingButtons(actions: List<FloatingAction>, width: Dp, minSize: Dp, maxSize: Dp, minGap: Dp, maxGap: Dp) {
    val fit = fitButtons(width, actions.size, minSize, maxSize, minGap, maxGap)
    val kept = if (fit.slots >= actions.size) actions.toSet()
    else actions.sortedByDescending { it.keep }.take(fit.slots - 1).toSet()
    val overflow = actions.filter { it !in kept }
    fun icon(dp: Float) = fit.size * (dp / FLOATING_SIZE.value)
    Row(horizontalArrangement = Arrangement.spacedBy(fit.gap)) {
        actions.filter { it in kept }.forEach { action ->
            GlassButton(onClick = action.onClick, contentDescription = action.label, size = fit.size) { action.icon(::icon) }
        }
        if (overflow.isNotEmpty()) MoreButton(overflow, fit.size)
    }
}

/** A glass button that opens a menu of [actions], each with its glyph and name. */
@Composable
private fun MoreButton(actions: List<FloatingAction>, size: Dp) {
    // Drives the open and close animation; the popup stays up until the close finishes.
    val menu = remember { MutableTransitionState(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        GlassButton(onClick = { menu.targetState = !menu.targetState }, contentDescription = "More", size = size) {
            MoreGlyph(Modifier.size(size * (24f / FLOATING_SIZE.value)))
        }
        if (menu.currentState || menu.targetState) {
            val margin = with(LocalDensity.current) { SpicySpacing.S2.roundToPx() }
            val position = remember(margin) { SelectMenuPosition(margin) }
            Popup(popupPositionProvider = position, onDismissRequest = { menu.targetState = false }, properties = PopupProperties(focusable = true)) {
                val origin = TransformOrigin(1f, if (position.above) 1f else 0f)
                AnimatedVisibility(
                    visibleState = menu,
                    enter = fadeIn(tween(SpicyMotion.FAST_MS)) +
                        scaleIn(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Modal), initialScale = 0.94f, transformOrigin = origin),
                    exit = fadeOut(tween(SpicyMotion.FAST_MS)) +
                        scaleOut(tween(SpicyMotion.FAST_MS), targetScale = 0.94f, transformOrigin = origin),
                ) {
                    val plate = RoundedCornerShape(SpicyRadii.Md)
                    val menuScroll = rememberScrollState()
                    Column(
                        Modifier
                            .widthIn(min = 180.dp, max = 280.dp)
                            .width(IntrinsicSize.Max)
                            .heightIn(max = 360.dp)
                            .clip(plate)
                            .background(MENU_FILL)
                            .border(1.dp, SpicyColors.HairlineStrong, plate)
                            .scrollHints(menuScroll)
                            .verticalScroll(menuScroll)
                            .padding(vertical = SpicySpacing.S1),
                    ) {
                        actions.forEach { action ->
                            MoreItem(action) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                menu.targetState = false
                                action.onClick()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreItem(action: FloatingAction, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = SpicySpacing.S1)
            .clip(RoundedCornerShape(SpicyRadii.Sm))
            .background(if (pressed) SpicyColors.TintBgPressed else Color.Transparent)
            .focusRing(RoundedCornerShape(SpicyRadii.Sm))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = SpicySpacing.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalGlyphPressed provides pressed) { action.icon { it.dp } }
        }
        Text(action.label, style = SpicyType.Body.copy(color = SpicyColors.TextPrimary))
    }
}

private fun floatingActions(
    controls: PlaybackControlsState,
    others: List<SessionCustomAction>,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    /** Shows the button that moves the landscape panel to the other side when set. */
    onSwapSide: (() -> Unit)?,
    /** Where the landscape cover is now, so the move button points the other way. */
    coverOnRight: Boolean = false,
    showResync: Boolean,
    showExpand: Boolean,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
): List<FloatingAction> = buildList {
    if (romanizeAvailable) {
        // Shows what a tap switches to: "A" to go back, the kana mark to romanize.
        add(FloatingAction(if (romanized) "Show original lyrics" else "Romanize lyrics", keep = 80, onToggleRomanize) { icon ->
            if (romanized) {
                Image(rememberVectorPainter(SpicyGlyphs.DisableRomanization), null, Modifier.size(icon(23f)))
            } else {
                Image(rememberVectorPainter(SpicyGlyphs.EnableRomanization), null, Modifier.size(icon(23f)))
            }
        })
    }
    // In groups: the lyric text (romanize, translate), the view, the app's tools, the player's own
    // buttons, then settings.
    controls.onTranslate?.let { toggle ->
        add(FloatingAction(if (controls.translated) "Turn translation off" else "Translate", keep = 78, toggle) { icon ->
            if (controls.translating) {
                // A spinning ring in the glyph's place until the translation lands.
                val turn by rememberInfiniteTransition(label = "translating").animateFloat(
                    0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "translatingTurn",
                )
                Canvas(Modifier.size(icon(22f))) {
                    drawArc(Color.White, turn, 270f, useCenter = false, style = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round))
                }
            } else {
                TranslateGlyph(Modifier.size(icon(23f)))
            }
        })
    }
    if (showResync) add(FloatingAction("Resync lyrics", keep = 30, controls.onResync) { icon ->
        ResyncGlyph(Modifier.size(icon(23f)))
    })
    if (showExpand) add(FloatingAction(if (expanded) "Show lyrics" else "Show the cover", keep = 70, onToggleExpanded) { icon ->
        ExpandGlyph(expanded, Modifier.size(icon(23f)))
    })
    if (onSwapSide != null) add(FloatingAction("Move the cover to the other side", keep = 60, onSwapSide) { icon ->
        SwapSideGlyph(coverOnRight, Modifier.size(icon(23f)))
    })
    if (onOpenLyricsManager != null) add(FloatingAction("Lyrics Manager", keep = 50, onOpenLyricsManager) { icon ->
        LyricsManagerGlyph(Modifier.size(icon(23f)))
    })
    if (onOpenSource != null) add(FloatingAction("Lyrics source", keep = 45, onOpenSource) { icon ->
        SourceGlyph(Modifier.size(icon(23f)))
    })
    if (onOpenQueue != null) add(FloatingAction("Queue", keep = 40, onOpenQueue) { icon ->
        QueueGlyph(Modifier.size(icon(23f)))
    })
    controls.onRotate?.let { rotate ->
        add(FloatingAction(if (controls.rotated) "Back to portrait" else "Turn to landscape", keep = 25, rotate) { icon ->
            RotateGlyph(controls.rotated, Modifier.size(icon(23f)))
        })
    }
    if (onEnterPip != null) add(FloatingAction("Picture-in-picture", keep = 20, onEnterPip) { icon ->
        PictureInPictureGlyph(Modifier.size(icon(23f)))
    })
    if (onOpenQuickSettings != null) add(FloatingAction("Quick settings", keep = 90, onOpenQuickSettings) { icon ->
        QuickSettingsGlyph(Modifier.size(icon(23f)))
    })
    others.forEach { action ->
        add(FloatingAction(action.name, keep = 10, { controls.onCustomAction(action.action) }) { icon ->
            ActionIcon(action, Modifier.size(icon(23f)))
        })
    }
    if (!controls.hideSettings) add(FloatingAction("Settings", keep = Int.MAX_VALUE, onOpenSettings) { icon ->
        SettingsGlyph(Modifier.size(icon(23f)))
    })
}

/**
 * All the controls over the landscape cover, the way the fullscreen now bar lays them over its
 * cover: the floating glass buttons across the top, the playback row, and the timeline along the
 * bottom (unless [timeline] is off: it sits under the cover then). While they show, the cover darkens to 55% (`--ArtworkBrightness`) and they sit at 98.5%
 * (`--ControlsOpacity`), both following [shown]. The glyphs and buttons scale with the cover so
 * everything fits in one row; touches between them reach the cover's own gestures.
 */
@Composable
fun CoverControls(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSwapSide: (() -> Unit)?,
    coverOnRight: Boolean = false,
    shown: () -> Float,
    modifier: Modifier = Modifier,
    onEnterPip: (() -> Unit)? = null,
    showResync: Boolean = true,
    showExpand: Boolean = true,
    timeline: Boolean = true,
) {
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = maxWidth
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = shown()
                    shape = RoundedCornerShape(size.width * COVER_CORNER_FRACTION)
                    clip = true
                }
                .background(Color.Black.copy(alpha = 0.45f)),
        )
        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = shown() * 0.985f }
                .padding(horizontal = side * 0.06f, vertical = side * 0.06f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloatingButtons(
                floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide, coverOnRight, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                side * 0.88f, minSize = COVER_BUTTON_MIN, maxSize = COVER_BUTTON_MAX, minGap = COVER_BUTTON_GAP, maxGap = SpicySpacing.S3,
            )
            if (timeline) {
                Spacer(Modifier.weight(1f))
                PlaybackRow(controls, shuffle, repeat, Modifier, skipWidth = coverSkipWidth(side, shuffle, repeat), fillSlots = true, dropEmptySides = true)
                Spacer(Modifier.weight(1f))
                Timeline(controls, shown, Modifier, showCredit = false)
            }
        }
        // With the timeline under the cover, the transport sits in the cover's middle.
        if (!timeline) {
            PlaybackRow(
                controls, shuffle, repeat,
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = shown() * 0.985f }
                    .padding(horizontal = side * 0.06f),
                skipWidth = coverSkipWidth(side, shuffle, repeat), fillSlots = true, dropEmptySides = true,
            )
        }
    }
}

/**
 * The cover stage's dock, laid along the bottom of a box as wide as the cover, like a video
 * player's: [metadata] (the song), the timeline with its times under it, and the transport. The
 * transport and the glass buttons (in the box's top outer corner) fade with [shown]; while they're
 * gone the song and timeline settle into the transport's place, and they rise again with the
 * controls, falling back only once those have faded. [align] lines the buttons (along the top)
 * and the song up across the box: -1 at its start, 0 in its middle, 1 at its end; it may move
 * while it plays. [onRight] orders the buttons for the right side.
 * Off [interactive], the timeline and transport leave touches to the stage, which brings the
 * controls up.
 */
@Composable
fun StageControls(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQueue: (() -> Unit)?,
    onOpenSource: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSwapSide: (() -> Unit)?,
    shown: () -> Float,
    interactive: Boolean,
    onRight: Boolean,
    align: () -> Float,
    metadata: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onEnterPip: (() -> Unit)? = null,
    showResync: Boolean = true,
    showExpand: Boolean = true,
) {
    val currentAlign by rememberUpdatedState(align)
    val sliding = remember { SlidingAlignment { currentAlign() } }
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }
    val up by remember { derivedStateOf { shown() > 0f } }
    // A critically damped 2.5 Hz spring.
    val lift by animateFloatAsState(if (up) 1f else 0f, spring(dampingRatio = 1f, stiffness = 247f), label = "dockLift")
    val gone by remember { derivedStateOf { shown() == 0f && lift == 0f } }
    var reservePx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = maxWidth
        val gutter = side * STAGE_GUTTER
        if (!gone) Box(
            Modifier
                .align(sliding)
                .padding(top = gutter + side * 0.01f, start = gutter, end = gutter)
                // Drops in from the top while the dock rises from the bottom.
                .graphicsLayer {
                    alpha = shown() * 0.985f
                    translationY = -(1f - lift) * side.toPx() * 0.015f
                },
        ) {
            // Over the cover's own picture, often bright, the usual shadow reads as a smudge.
            CompositionLocalProvider(LocalGlassCast provides STAGE_GLASS_CAST) {
                FloatingButtons(
                    floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide, onRight, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                    side - gutter * 2, minSize = COVER_BUTTON_MIN, maxSize = COVER_BUTTON_MAX, minGap = COVER_BUTTON_GAP, maxGap = SpicySpacing.S3,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = gutter, end = gutter, bottom = side * STAGE_BOTTOM),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        // Hidden, the rows settle into the transport's slot; above it, they take the
                        // touches there, so a tap on the timeline seeks instead of landing on the row.
                        .zIndex(1f)
                        .fillMaxWidth()
                        .graphicsLayer { translationY = (1f - lift) * (reservePx + side.toPx() * STAGE_GAP) },
                    horizontalAlignment = sliding,
                ) {
                    metadata()
                    // No spacer: the bar's touch height already stands it 11dp clear of the artists.
                    Timeline(controls, { 1f }, Modifier, showCredit = false)
                }
                Spacer(Modifier.height(side * STAGE_GAP))
                Box {
                    PlaybackRow(
                        controls, shuffle, repeat,
                        Modifier
                            .onSizeChanged { reservePx = it.height }
                            .graphicsLayer { alpha = shown() * 0.985f },
                        skipWidth = side * 0.15f, fillSlots = true,
                    )
                    // Hidden, the transport takes no taps; the same tap brings the controls up.
                    if (!interactive) {
                        Box(Modifier.matchParentSize().pointerInput(Unit) {
                            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                        })
                    }
                }
            }
        }
    }
}

/** The dock's measures against the cover's width: its side gutter, bottom margin and row gap. */
private const val STAGE_GUTTER = 0.04f
private const val STAGE_BOTTOM = 0.05f
private const val STAGE_GAP = 0.025f
/** The stage's glass buttons cast no shadow: over the cover's own picture, often bright, any reads as a smudge. */
private const val STAGE_GLASS_CAST = 0f

private val SIDE_MARGIN = 28.dp
/** The bottom controls' widest, so they don't stretch across a landscape or tablet screen. */
private val CONTROLS_MAX_WIDTH = 560.dp
/** The glass buttons over the cover: as big as fits in one row, between these; the rest go in More. */
private val COVER_BUTTON_MAX = 40.dp
private val COVER_BUTTON_MIN = 32.dp
private val COVER_BUTTON_GAP = 6.dp
/** The one-row landscape bar ([ControlsBar]). */
private val BAR_MIDDLE_WIDTH = 360.dp
private val BAR_SKIP_WIDTH = 36.dp
private val BAR_BOTTOM_MARGIN = 12.dp
private val BAR_SHADE_REACH = 72.dp
/** The landscape cover's corners (`border-radius: 2cqh` against its square). */
private const val COVER_CORNER_FRACTION = 0.02f
private val ROW_GAP = 24.dp
/** The floating buttons, sized to match the transport so they don't look lost under it. */
private val FLOATING_SIZE = 48.dp
private val BOTTOM_MARGIN = 84.dp
/** How far above the controls the shade starts. */
private val SHADE_REACH = 180.dp
private val SHADE_BLUR = 24.dp
private const val SHADE_ALPHA = 0.3f

/**
 * The shade's ramp, for both blur and darkening: flat at the top so it has no visible edge where
 * it starts, steepest in the middle, flat again at full strength.
 */
private val ShadeEasing = CubicBezierEasing(0.45f, 0f, 0.35f, 1f)

/**
 * How far in from the page's left edge the portrait controls' rows start, in px, on a page
 * [pageWidthPx] wide: their side margin, inside the column they're centred in.
 */
internal fun controlsContentStartPx(pageWidthPx: Float, density: Float): Float {
    val column = minOf(pageWidthPx, CONTROLS_MAX_WIDTH.value * density)
    return (pageWidthPx - column) / 2f + SIDE_MARGIN.value * density
}

/**
 * How strong the shade's blur gets before a notice keeps above it: a fifth of [SHADE_BLUR]. Any
 * stricter and the notice sat well clear of the controls, too high to read as centred under them.
 */
private const val SHADE_SOFT_INTENSITY = 0.2f

/**
 * How much of the page's bottom [LyricsControls] keep from being read clearly, in px: the
 * controls themselves ([controlsHeightPx], as reported by `onControlsHeight`) and the part of the
 * shade above them that blurs text. A notice kept above this is sharp.
 */
internal fun controlsClearancePx(controlsHeightPx: Int, wide: Boolean, density: Float): Float {
    val reachPx = (if (wide) BAR_SHADE_REACH else SHADE_REACH).value * density
    return controlsHeightPx + shadeBlurAbovePx(reachPx, controlsHeightPx.toFloat(), ShadeEasing, SHADE_SOFT_INTENSITY)
}

/**
 * How far above the controls a shade [reachPx] tall blurs at [intensity] or more. The blur ramps
 * on [easing] from nothing at the shade's top to full at the controls' bottom, across the reach
 * and the controls together, so taller controls leave less of the reach blurred.
 */
internal fun shadeBlurAbovePx(reachPx: Float, controlsHeightPx: Float, easing: Easing, intensity: Float): Float {
    val span = reachPx + controlsHeightPx.coerceAtLeast(0f)
    if (reachPx <= 0f) return 0f
    // Where the ramp reaches [intensity], as a fraction of the span; the easing only rises.
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2f
        if (easing.transform(mid) < intensity) low = mid else high = mid
    }
    return (reachPx - low * span).coerceIn(0f, reachPx)
}

/** [ShadeEasing] sampled into stops, since a gradient only ramps linearly between stops. */
private val ShadeGradient = Brush.verticalGradient(
    *Array(9) { i ->
        val t = i / 8f
        t to Color.Black.copy(alpha = SHADE_ALPHA * ShadeEasing.transform(t))
    },
)

/** Where a player's custom action goes. Players only give a name, so this reads the name. */
internal enum class ActionKind { Shuffle, Repeat, Other }

internal val SessionCustomAction.kind: ActionKind
    get() {
        val text = "$name $action".lowercase()
        return when {
            "shuffle" in text -> ActionKind.Shuffle
            "repeat" in text || "loop" in text -> ActionKind.Repeat
            else -> ActionKind.Other
        }
    }

/** A player's own icon, in white like everything else here; its name when it sent no icon. */
@Composable
private fun ActionIcon(action: SessionCustomAction, modifier: Modifier) {
    val bitmap = action.icon
    if (bitmap != null) {
        val painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
        Image(painter, null, modifier, colorFilter = ColorFilter.tint(SpicyColors.TextPrimary))
    } else {
        Text(action.name.take(2), style = TimeStyle.copy(color = SpicyColors.TextPrimary))
    }
}

/**
 * `.PlaybackControls`: shuffle and repeat 7 units wide, skips 12, play/pause 9.25, spread across
 * the row. A slot the player has no action for stays empty so play stays centred.
 */
@Composable
private fun PlaybackRow(
    controls: PlaybackControlsState,
    shuffle: SessionCustomAction?,
    repeat: SessionCustomAction?,
    modifier: Modifier,
    skipWidth: Dp = SKIP_WIDTH,
    /** Each control gets an equal share of the row as its touch area, for rows too narrow for 48dp ones. */
    fillSlots: Boolean = false,
    /** With neither shuffle nor repeat from the player, leave out their empty slots. */
    dropEmptySides: Boolean = false,
) {
    val sides = !dropEmptySides || shuffle?.icon != null || repeat?.icon != null
    val playWidth = skipWidth * (9.25f / 12f)
    val sideWidth = skipWidth * (7f / 12f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val slot = if (fillSlots) Modifier.weight(1f).height(TOUCH_SIZE) else null
        if (sides) SideAction(shuffle, controls, sideWidth, slot)
        val animate = LocalUiAnimations.current
        val scope = rememberCoroutineScope()
        // The skips move on and play turns into pause, Apple Music's way, when animations are on.
        val previous = remember { Animatable(0f) }
        val next = remember { Animatable(0f) }
        fun moveOn(glyph: Animatable<Float, *>) = scope.launch {
            glyph.snapTo(0f)
            glyph.animateTo(1f, tween(SKIP_MOVE_MS, easing = FastOutSlowInEasing))
            glyph.snapTo(0f)
        }
        val pause by animateFloatAsState(
            if (controls.isPlaying) 1f else 0f,
            if (animate) tween(MORPH_MS, easing = FastOutSlowInEasing) else snap(),
            label = "playPause",
        )
        val previousGlyph = remember { SkipGlyph { previous.value } }
        val nextGlyph = remember { SkipGlyph { next.value } }
        val playPauseGlyph = remember { PlayPauseGlyph { pause } }
        val skip = rememberVectorPainter(SpicyIcons.TrackSkip)
        PressableGlyph(
            if (animate) previousGlyph else skip, skipWidth, SpicyIcons.TrackSkip.aspect, "Previous", rotate = true, touch = slot,
            onClick = { if (animate) moveOn(previous); controls.onPrevious() },
        )
        val playIcon = if (controls.isPlaying) SpicyIcons.Pause else SpicyIcons.Play
        PressableGlyph(
            if (animate) playPauseGlyph else rememberVectorPainter(playIcon),
            playWidth,
            if (animate) SpicyIcons.Pause.aspect else playIcon.aspect,
            if (controls.isPlaying) "Pause" else "Play",
            touch = slot,
            onClick = controls.onPlayPause,
        )
        PressableGlyph(
            if (animate) nextGlyph else skip, skipWidth, SpicyIcons.TrackSkip.aspect, "Next", touch = slot,
            onClick = { if (animate) moveOn(next); controls.onNext() },
        )
        if (sides) SideAction(repeat, controls, sideWidth, slot)
    }
}

/**
 * The skips' width over the cover: 15% of it with shuffle and repeat beside them, growing into the
 * room the player leaves when it sends only one of them (17%), or neither (21%).
 */
private fun coverSkipWidth(side: Dp, shuffle: SessionCustomAction?, repeat: SessionCustomAction?): Dp {
    val sideActions = listOfNotNull(shuffle?.icon, repeat?.icon).size
    return side * when (sideActions) {
        2 -> 0.15f
        1 -> 0.17f
        else -> 0.21f
    }
}

/** Big, like Apple Music's transport, which suits a thumb. */
private val SKIP_WIDTH = 52.dp
/** How long a skip glyph takes to move on, and play to turn into pause. */
private const val SKIP_MOVE_MS = 420
private const val MORPH_MS = 260

@Composable
private fun SideAction(action: SessionCustomAction?, controls: PlaybackControlsState, width: Dp, slot: Modifier?) {
    val bitmap = action?.icon
    if (bitmap == null) {
        Spacer(slot ?: Modifier.size(TOUCH_SIZE))
        return
    }
    val painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
    PressableGlyph(
        painter, width, 1f, action.name,
        colorFilter = ColorFilter.tint(SpicyColors.TextPrimary),
        touch = slot,
        onClick = { controls.onCustomAction(action.action) },
    )
}

private val ImageVector.aspect get() = viewportWidth / viewportHeight

/** Glyphs get a 48dp touch area however small they are drawn. */
private val TOUCH_SIZE = 48.dp

/** `transition: transform 0.175s cubic-bezier(0.37, 0, 0.63, 1)` into `.Pressed`. */
private val PressEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * A transport button: shrinks to 0.9 while held, then plays a 0.6 s bounce on release.
 */
@Composable
private fun PressableGlyph(
    painter: Painter,
    width: Dp,
    aspect: Float,
    description: String,
    rotate: Boolean = false,
    colorFilter: ColorFilter? = null,
    /** The touch area; null: at least 48dp square around the glyph. */
    touch: Modifier? = null,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // The screen recomposes on every touch; keying the gesture on a fresh lambda would cancel
    // the press before it lifts.
    val currentOnClick by rememberUpdatedState(onClick)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    Box(
        modifier = (touch ?: Modifier.size(width = maxOf(TOUCH_SIZE, width + 12.dp), height = TOUCH_SIZE))
            .keyClickable(CircleShape) { currentOnClick() }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    scope.launch { scale.animateTo(SHRINK, tween(175, easing = PressEasing)) }
                    val up = waitForUpOrCancellation()
                    scope.launch { scale.animateTo(1f, PressBounce) }
                    if (up != null) {
                        up.consume()
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = colorFilter,
            modifier = Modifier
                .width(width)
                .aspectRatio(aspect)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = if (rotate) 180f else 0f
                },
        )
    }
}

private const val SHRINK = 0.9f

/** `@keyframes pressAnimation`: scale(1 - ShrinkDelta * k) with CSS's default `ease` per step. */
private val PressBounce = keyframes {
    val delta = 1f - SHRINK
    durationMillis = 600
    (1f - delta * 1f) at 0 using CssEase
    (1f - delta * -0.32f) at 96 using CssEase
    (1f - delta * 0.13f) at 168 using CssEase
    (1f - delta * -0.05f) at 264 using CssEase
    (1f - delta * 0.02f) at 354 using CssEase
    (1f - delta * -0.01f) at 438 using CssEase
    1f at 528 using CssEase
}

/** Times: weight 500, 60% white, tabular figures. */
private val TimeStyle = TextStyle(
    fontFamily = LyricsLayoutCalculator.spicyFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    fontFeatureSettings = "tnum",
    color = SpicyPalette.Neutral.TextSecondary,
)

/**
 * The glass timeline: a frosted capsule whose white fill is the progress,
 * thickening while dragged. Seeks once, on release, because MediaSession seeks are slow and
 * seeking along the drag would make the lyrics jump about. Elapsed and total time sit underneath.
 */
@Composable
private fun Timeline(controls: PlaybackControlsState, shown: () -> Float, modifier: Modifier, showCredit: Boolean = true) =
    TimelineRow(controls, shown, modifier, showCredit = showCredit)

/** The controls' timeline on its own, for a dock that shows it while the controls are hidden. */
@Composable
internal fun DockTimeline(controls: PlaybackControlsState, modifier: Modifier = Modifier) = TimelineRow(controls, { 1f }, modifier)

/**
 * The landscape panel's timeline under the cover, between the cover and the song (`.Header >
 * .Timeline`), always showing: the times beside the bar rather than under it, everything sized
 * against the cover ([coverSizePx]) and the page's height ([pageHeightPx]).
 */
@Composable
fun PanelTimeline(controls: PlaybackControlsState, coverSizePx: Float, pageHeightPx: Float, timeSizeSp: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    with(density) {
        // `padding: 4.5cqw 1cqw 3cqw`, the bar `1.7cqh` (`2.3cqh` engaged) of the page.
        TimelineRow(
            controls, { 1f },
            modifier.padding(start = (coverSizePx * 0.01f).toDp(), end = (coverSizePx * 0.01f).toDp(), top = (coverSizePx * 0.045f).toDp(), bottom = (coverSizePx * 0.03f).toDp()),
            beside = PanelTimes(
                style = TimeStyle.copy(fontSize = timeSizeSp.sp),
                barMargin = (coverSizePx * 0.015f).toDp(),
                barHeight = (pageHeightPx * 0.017f).toDp(),
                barHeightDragging = (pageHeightPx * 0.023f).toDp(),
            ),
        )
    }
}

/** How [PanelTimeline] lays out its times and bar. */
private class PanelTimes(val style: TextStyle, val barMargin: Dp, val barHeight: Dp, val barHeightDragging: Dp)

@Composable
private fun TimelineRow(controls: PlaybackControlsState, shown: () -> Float, modifier: Modifier, beside: PanelTimes? = null, showCredit: Boolean = true) {
    val current by rememberUpdatedState(controls)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var positionMs by remember { mutableLongStateOf(controls.positionMs()) }
    val shownUpdated by rememberUpdatedState(shown)
    LaunchedEffect(controls.positionMs, controls.isPlaying) {
        positionMs = controls.positionMs()
        while (true) {
            if (!controls.isPlaying) {
                // Paused, the bar only moves on a seek: a few reads a second, no frames, catch one
                // from the player (a seek from here reads at once).
                delay(HIDDEN_UPDATE_MS)
                positionMs = controls.positionMs()
                continue
            }
            // Faded out, nobody sees the bar move: a few updates a second keep it close.
            if (shownUpdated() == 0f) delay(HIDDEN_UPDATE_MS)
            withFrameMillis { positionMs = controls.positionMs() }
        }
    }
    // Seeks, and shows where to at once rather than at the next read.
    fun seek(toMs: Long) {
        current.onSeek(toMs)
        positionMs = current.positionMs()
    }
    val duration = controls.durationMs.coerceAtLeast(0L)
    val durationUpdated by rememberUpdatedState(duration)
    // Read only where it is drawn, so the moving position redraws the bar without recomposing.
    fun shownMs() = dragFraction?.let { (it * durationUpdated).toLong() } ?: positionMs
    val elapsedText by remember { derivedStateOf { formatClock(shownMs()) } }

    val barHeight = beside?.barHeight ?: BAR_HEIGHT
    val barHeightDragging = beside?.barHeightDragging ?: BAR_HEIGHT_DRAGGING
    val thickness = remember(barHeight) { Animatable(barHeight.value) }
    LaunchedEffect(dragFraction != null, barHeight) {
        // `transition: height 0.28s cubic-bezier(0.34, 1.56, 0.64, 1)`.
        thickness.animateTo(
            if (dragFraction != null) barHeightDragging.value else barHeight.value,
            tween(280, easing = SpicyMotion.Overshoot),
        )
    }
    val seekable = controls.canSeek && duration > 0L

    @Composable
    fun Bar(barModifier: Modifier) {
        Canvas(
            barModifier
                .height(BAR_TOUCH_HEIGHT)
                // A remote's or keyboard's left and right skip 10 s; up and down move on.
                .focusRing(RoundedCornerShape(SpicyRadii.Sm))
                .onKeyEvent { event ->
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -KEY_SEEK_MS
                        Key.DirectionRight -> KEY_SEEK_MS
                        else -> return@onKeyEvent false
                    }
                    if (seekable && event.type == KeyEventType.KeyDown) {
                        seek((current.positionMs() + delta).coerceIn(0L, duration))
                    }
                    seekable
                }
                .focusable(seekable)
                .pointerInput(seekable, duration) {
                    if (!seekable) return@pointerInput
                    // A tap seeks where it went down. Only sideways movement makes it a drag: the
                    // cover stage's dock rises as the same tap brings the controls up, moving the
                    // bar under a finger that hasn't moved, which a plain tap detector would cancel.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var tapped = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) break
                            if (!change.pressed) {
                                tapped = true
                                break
                            }
                        }
                        if (tapped) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            seek((down.position.x / size.width * duration).toLong().coerceIn(0L, duration))
                        }
                    }
                }
                .pointerInput(seekable, duration) {
                    if (!seekable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            dragFraction = (it.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            dragFraction?.let { seek((it * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            val barHeight = thickness.value.dp.toPx()
            val top = (size.height - barHeight) / 2f
            val radius = CornerRadius(barHeight / 2f)
            // `--material-regular-bg: rgba(0,0,0,.1)` with the capsule's 1px white inner edge.
            drawRoundRect(Color.Black.copy(alpha = 0.1f), Offset(0f, top), Size(size.width, barHeight), radius)
            drawRoundRect(
                Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, barHeight), radius,
                style = Stroke(width = 1.dp.toPx()),
            )
            // The fill is a scaleX of a full-width white bar, so it keeps the capsule's rounded clip.
            val capsule = Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + barHeight, radius)) }
            val progress = if (duration > 0L) (shownMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
            clipPath(capsule) {
                drawRect(Color.White, Offset(0f, top), Size(size.width * progress, barHeight))
            }
        }
    }

    if (beside == null) {
        Column(modifier.fillMaxWidth()) {
            Bar(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(elapsedText, style = TimeStyle, maxLines = 1)
                // The source sits between the two times, on their line.
                controls.credit?.takeIf { showCredit }?.let { credit ->
                    MiniCreditLine(
                        credit,
                        TimeStyle.copy(fontSize = 11.sp),
                        SpicyPalette.Neutral.TextSecondary.copy(alpha = 0.8f),
                        Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                }
                Text(formatClock(duration), style = TimeStyle, maxLines = 1)
            }
        }
    } else {
        // `.Time { min-width: 5ch }`, the elapsed time right-aligned against the bar.
        val timeWidth = (beside.style.fontSize.value * 2.75f).dp
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(elapsedText, style = beside.style.copy(textAlign = TextAlign.End), maxLines = 1, modifier = Modifier.widthIn(min = timeWidth))
            Bar(
                Modifier
                    .weight(1f)
                    .padding(horizontal = beside.barMargin)
                    // Only the bar's own (engaged) height takes room; the touch band spills over
                    // the padding around it.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        val height = beside.barHeightDragging.roundToPx()
                        layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
                    },
            )
            Text(formatClock(duration), style = beside.style, maxLines = 1, modifier = Modifier.widthIn(min = timeWidth))
        }
    }
}

private const val HIDDEN_UPDATE_MS = 250L
private val BAR_HEIGHT = 10.dp
private val BAR_HEIGHT_DRAGGING = 14.dp
private val BAR_TOUCH_HEIGHT = 32.dp

internal fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** The controls' glyphs, in white. */
internal object SpicyIcons {
    val TrackSkip = icon(
        35f, 20f,
        "M 19.467 19.905 C 20.008 19.905 20.463 19.746 21.005 19.426 L 33.61 12.023 C 34.533 11.482 35 10.817 35 9.993 C 35 9.158 34.545 8.53 33.61 7.977 L 21.005 0.574 C 20.463 0.254 19.998 0.094 19.456 0.094 C 18.374 0.094 17.475 0.917 17.475 2.418 L 17.475 9.49 C 17.315 8.898 16.873 8.408 16.135 7.977 L 3.529 0.574 C 3 0.254 2.533 0.094 1.993 0.094 C 0.911 0.094 0 0.917 0 2.418 L 0 17.582 C 0 19.083 0.91 19.906 1.993 19.906 C 2.533 19.906 3 19.746 3.529 19.426 L 16.135 12.023 C 16.861 11.593 17.315 11.088 17.475 10.485 L 17.475 17.582 C 17.475 19.083 18.386 19.906 19.467 19.906 L 19.467 19.905 Z",
    )
    val Play = icon(
        18f, 20f,
        "M 1.558 20 C 2.006 20 2.381 19.838 2.874 19.561 L 16.622 11.572 C 17.527 11.053 17.894 10.65 17.894 9.997 C 17.894 9.35 17.527 8.948 16.622 8.419 L 2.874 0.439 C 2.381 0.153 2.006 0 1.558 0 C 0.706 0 0.106 0.654 0.106 1.694 L 0.106 18.298 C 0.106 19.346 0.706 20 1.558 20 L 1.558 20 Z",
    )
    val Pause = icon(
        15f, 20f,
        "M 4.427 19.963 C 5.513 19.963 6.06 19.416 6.06 18.33 L 6.06 1.66 C 6.06 0.545 5.513 0.037 4.427 0.037 L 1.633 0.037 C 0.548 0.037 0 0.575 0 1.66 L 0 18.331 C -0.009 19.416 0.538 19.963 1.633 19.963 L 4.427 19.963 Z M 13.377 19.963 C 14.462 19.963 15 19.416 15 18.33 L 15 1.66 C 15 0.545 14.462 0.037 13.376 0.037 L 10.573 0.037 C 9.487 0.037 8.949 0.575 8.949 1.66 L 8.949 18.331 C 8.949 19.416 9.487 19.963 10.573 19.963 L 13.376 19.963 L 13.377 19.963 Z",
    )
    private fun icon(width: Float, height: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = width.dp, defaultHeight = height.dp, viewportWidth = width, viewportHeight = height)
            .apply { paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.White)) } }
            .build()
}

/** How far the timeline skips for a press of left or right. */
private const val KEY_SEEK_MS = 10_000L
