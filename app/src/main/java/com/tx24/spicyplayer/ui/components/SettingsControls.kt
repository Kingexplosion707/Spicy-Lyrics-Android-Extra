package com.tx24.spicyplayer.ui.components

import androidx.compose.ui.composed
import androidx.compose.runtime.ReadOnlyComposable
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.em
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlin.math.roundToInt

/*
 * The settings screen's controls, with search built in: rows hide themselves when they don't
 * match [LocalSettingsQuery].
 */

/** What the settings search box holds; blank shows everything. */
val LocalSettingsQuery = compositionLocalOf { "" }

/** Whether the interface animates (playback buttons, lists, pop-up screens): the setting, and not low performance mode. */
val LocalUiAnimations = compositionLocalOf { true }

/**
 * Steps the settings aside so the lyrics behind them show while a setting is tried: everything
 * goes but [keep] (the peek button, or a slider's row being dragged, drawn on a [plate]).
 * [keep] is in root coordinates and outlives [end], so the kept part fades back in place.
 */
@Stable
class SettingsPeek(private val allowRows: () -> Boolean = { true }) {
    var active by mutableStateOf(false)
        private set
    var keep by mutableStateOf<Rect?>(null)
        private set
    var plate by mutableStateOf(false)
        private set
    /** Kept open by a quick tap on the peek button, until a tap on it or Back brings the settings back. */
    var latched by mutableStateOf(false)

    fun start(keep: Rect?, plate: Boolean) {
        this.keep = keep
        this.plate = plate
        latched = false
        active = true
    }

    /** For a row's control: peeks at everything but the row, unless the setting turns that off. */
    fun startRow(row: Rect?) {
        if (row != null && allowRows()) start(row, plate = true)
    }

    fun end() {
        active = false
        latched = false
    }
}

/** The open settings' peek; null outside settings, where controls don't peek. */
val LocalSettingsPeek = staticCompositionLocalOf<SettingsPeek?> { null }

/** The bounds, in root coordinates, of the settings row a control sits in. */
private val LocalRowBounds = staticCompositionLocalOf<() -> Rect?> { { null } }

/** Holds a laid-out row's bounds without recomposing anything when they change. */
private class BoundsHolder {
    var rect: Rect? = null
}

/** Whether the label or the description contains the query, ignoring case. */
fun settingMatches(query: String, vararg terms: String?): Boolean {
    val q = query.trim()
    return q.isEmpty() || terms.any { it?.contains(q, ignoreCase = true) == true }
}

/** Shows [content] only when one of [terms] matches the search. */
@Composable
fun Searchable(vararg terms: String?, content: @Composable () -> Unit) {
    if (settingMatches(LocalSettingsQuery.current, *terms)) content()
}

/**
 * A section title: a headline over its rows, after a hairline unless [divider] is off.
 * Takes no room at all when every row under it is hidden by the search.
 */
@Composable
fun SettingsSection(title: String?, modifier: Modifier = Modifier, divider: Boolean = true, content: @Composable () -> Unit) {
    CollapsingColumn(
        header = { if (title != null) SectionTitle(title, divider) },
        modifier = modifier,
        content = content,
    )
}

/** A header over [content]; both vanish when [content] lays out empty. */
@Composable
fun CollapsingColumn(header: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        contents = listOf(header, { Column { content() } }),
        modifier = modifier,
    ) { (headerMeasurables, bodyMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val body = bodyMeasurables.map { it.measure(loose) }
        val bodyHeight = body.sumOf { it.height }
        if (bodyHeight == 0) return@Layout layout(0, 0) {}
        val header = headerMeasurables.map { it.measure(loose) }
        val headerHeight = header.sumOf { it.height }
        val width = (header + body).maxOf { it.width }.coerceAtLeast(constraints.minWidth)
        layout(width, headerHeight + bodyHeight) {
            var y = 0
            (header + body).forEach { it.place(0, y); y += it.height }
        }
    }
}

@Composable
fun SectionTitle(text: String, divider: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.padding(top = SpicySpacing.S2).fillMaxWidth().height(1.dp).background(SpicyColors.Hairline))
        Text(
            text,
            style = SpicyType.Headline.copy(letterSpacing = (-0.005).em),
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = if (divider) SpicySpacing.S4 else SpicySpacing.S2, bottom = SpicySpacing.S2),
        )
    }
}

/**
 * A settings row: label and description on the left, the control on the right, or under them when
 * [stacked]. Tapping the row runs [onClick]. Hidden when it doesn't match the search.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    stacked: Boolean = false,
    searchDescription: String? = null,
    control: @Composable () -> Unit = {},
) {
    if (!settingMatches(LocalSettingsQuery.current, label, description, searchDescription)) return
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    RowFrame(
        label, description, icon, interaction, enabled, stacked,
        modifier.then(
            if (onClick != null && enabled) {
                Modifier.focusRing(RoundedCornerShape(SpicyRadii.Sm)).clickable(interaction, indication = null) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
            } else Modifier,
        ),
        control,
    )
}

/** A [SettingRow] with a toggle; the whole row flips it. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    searchDescription: String? = null,
) {
    if (!settingMatches(LocalSettingsQuery.current, label, description, searchDescription)) return
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    RowFrame(
        label, description, icon, interaction, enabled, stacked = false,
        modifier = modifier.focusRing(RoundedCornerShape(SpicyRadii.Sm)).toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch) {
            haptics.toggled(it)
            onCheckedChange(it)
        },
    ) { SpicyToggle(checked) }
}

@Composable
private fun RowFrame(
    label: String,
    description: String?,
    icon: ImageVector?,
    interaction: MutableInteractionSource,
    enabled: Boolean,
    stacked: Boolean,
    modifier: Modifier,
    control: @Composable () -> Unit,
) {
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) SpicyColors.TintBg else Color.Transparent,
        tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard),
        label = "rowPress",
    )
    val bounds = remember { BoundsHolder() }
    val frame = modifier
        .fillMaxWidth()
        .onGloballyPositioned { bounds.rect = it.boundsInRoot() }
        .clip(RoundedCornerShape(SpicyRadii.Md))
        .background(background)
        .padding(SpicySpacing.S3)
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
    CompositionLocalProvider(LocalRowBounds provides { bounds.rect }) {
        if (stacked) {
            Column(frame, verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
                RowLabel(label, description, Modifier.fillMaxWidth(), icon)
                control()
            }
        } else {
            Row(frame, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S4)) {
                RowLabel(label, description, Modifier.weight(1f), icon)
                control()
            }
        }
    }
}

/** A row's label over its description, after an optional icon. */
@Composable
fun RowLabel(label: String, description: String?, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
        // Level with the label's first line, however long the description runs.
        if (icon != null) Icon(icon, null, tint = SpicyColors.TextSecondary, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = SpicyType.Body.copy(fontWeight = FontWeight.Medium))
            if (description != null) Text(description, style = DescriptionStyle)
        }
    }
}

/** A row's description: caption in secondary text at 85%. */
val DescriptionStyle @Composable @ReadOnlyComposable get() = SpicyType.Caption.copy(color = SpicyColors.TextSecondary.copy(alpha = 0.6f * 0.85f), lineHeight = 1.4.em)

/**
 * A toggle whose state shows through brightness alone. Off is a dark groove with a cream knob,
 * on a lit track with a white knob. Pass [onCheckedChange] only when nothing around it toggles.
 */
@Composable
fun SpicyToggle(checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal),
        label = "toggle",
    )
    val haptics = LocalHapticFeedback.current
    val click = if (onCheckedChange != null) {
        Modifier.focusRing(CircleShape).toggleable(checked, remember { MutableInteractionSource() }, indication = null, role = Role.Switch) {
            haptics.toggled(it)
            onCheckedChange(it)
        }
    } else {
        Modifier
    }
    Canvas(modifier.then(click).size(42.dp, 24.dp)) {
        val r = size.height / 2f
        val px = 1.dp.toPx()
        drawRoundRect(lerp(ToggleTrackOff, ToggleTrackOn, progress), cornerRadius = CornerRadius(r))
        drawRoundRect(
            lerp(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.24f), progress),
            topLeft = Offset(px / 2f, px / 2f),
            size = Size(size.width - px, size.height - px),
            cornerRadius = CornerRadius(r - px / 2f),
            style = Stroke(px),
        )
        val knobR = 10.dp.toPx()
        val knob = Offset(2.dp.toPx() + knobR + progress * 18.dp.toPx(), r)
        // A soft shadow all round, a touch lower: a hard ring set off below read as a second circle.
        val shadowAt = knob.copy(y = knob.y + 0.75f * px)
        val shadowR = knobR + 2.5f * px
        val shadow = Color.Black.copy(alpha = 0.24f)
        drawCircle(
            Brush.radialGradient(0f to shadow, (knobR - px) / shadowR to shadow, 1f to Color.Transparent, center = shadowAt, radius = shadowR),
            shadowR,
            shadowAt,
        )
        drawCircle(lerp(ToggleKnobOff, Color.White, progress), knobR, knob)
    }
}

/** A switch's feedback: [HapticFeedbackType.ToggleOn] or [HapticFeedbackType.ToggleOff]. */
fun HapticFeedback.toggled(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

private val ToggleTrackOff = Color.Black.copy(alpha = 0.42f)
private val ToggleTrackOn = Color.White.copy(alpha = 0.42f)
private val ToggleKnobOff = Color(245, 245, 245).copy(alpha = 0.78f)

private fun lerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/**
 * A tinted pill showing the chosen label with a chevron; tapped, it opens the options on a dark
 * plate under the pill (over it when there's no room below), the chosen one ticked.
 */
@Composable
fun SpicySelect(
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = options,
    enabled: Boolean = true,
    /** Options shown greyed out, which can't be picked. */
    unavailable: Set<String> = emptySet(),
) {
    val haptics = LocalHapticFeedback.current
    SelectMenu(labels.getOrElse(options.indexOf(value)) { value }, modifier, enabled, searchable = options.size >= SEARCH_FROM) { close, query ->
        val shown = matchingOptions(query, options, labels)
        for (i in shown) {
            val option = options[i]
            SelectOption(labels.getOrElse(i) { option }, selected = option == value, enabled = option !in unavailable) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                close()
                if (option != value) onChange(option)
            }
        }
        shown.size
    }
}

/**
 * [SpicySelect] for picking any number of options: each tap ticks or unticks one and the menu
 * stays open; a tap outside closes it. The pill reads [none] when nothing is picked.
 */
@Composable
fun SpicyMultiSelect(
    selected: Set<String>,
    options: List<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = options,
    none: String = "None",
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val label = when (selected.size) {
        0 -> none
        1 -> labels.getOrElse(options.indexOf(selected.single())) { selected.single() }
        else -> "${selected.size} picked"
    }
    SelectMenu(label, modifier, enabled, searchable = options.size >= SEARCH_FROM) { _, query ->
        val shown = matchingOptions(query, options, labels)
        for (i in shown) {
            val option = options[i]
            SelectOption(labels.getOrElse(i) { option }, selected = option in selected) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onChange(if (option in selected) selected - option else selected + option)
            }
        }
        shown.size
    }
}

/** Lists this long get a search field at the top of their menu. */
private const val SEARCH_FROM = 12
private val SEARCHABLE_MENU_WIDTH = 240.dp
private val MENU_MAX_HEIGHT = 360.dp

/**
 * The indices of [options] whose label or option matches [query]: every word of it starts a word
 * of either, ignoring case and accents, so "fil" finds Filipino and "tl" finds its code. A blank
 * query matches everything.
 */
internal fun matchingOptions(query: String, options: List<String>, labels: List<String>): List<Int> {
    val words = foldForSearch(query).split(' ').filter(String::isNotEmpty)
    if (words.isEmpty()) return options.indices.toList()
    return options.indices.filter { i ->
        val haystack = foldForSearch("${labels.getOrElse(i) { options[i] }} ${options[i]}").split(' ', '-', '_', '(', ')')
        words.all { word -> haystack.any { it.startsWith(word) } }
    }
}

private fun foldForSearch(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(COMBINING_MARKS, "").trim()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * The pill and its menu plate, shared by [SpicySelect] and [SpicyMultiSelect]; [items] gets a way
 * to close it and, when [searchable], what's typed in the field above them (empty otherwise).
 */
@Composable
private fun SelectMenu(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    searchable: Boolean = false,
    /** Draws the options and says how many it drew. */
    items: @Composable (close: () -> Unit, query: String) -> Int,
) {
    // Drives the open and close animation; the popup stays up until the close finishes.
    val menu = remember { MutableTransitionState(false) }
    // A tap on the pill while open lands after the popup has closed itself on touch-down;
    // without this it would open again at once.
    var closedAt by remember { mutableStateOf(0L) }
    val close = {
        if (menu.targetState) closedAt = SystemClock.uptimeMillis()
        menu.targetState = false
    }
    val open = menu.targetState
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val haptics = LocalHapticFeedback.current
    // The pill's top and bottom in the window, for the side the menu opens on.
    var pillTop by remember { mutableIntStateOf(0) }
    var pillBottom by remember { mutableIntStateOf(0) }
    Box(
        modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            pillTop = bounds.top.roundToInt()
            pillBottom = bounds.bottom.roundToInt()
        },
    ) {
        Row(
            Modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(if (pressed || open) SpicyColors.TintBgPressed else SpicyColors.TintBg)
                .border(1.dp, if (pressed || open) SpicyColors.HairlineStrong else SpicyColors.Hairline, shape)
                .focusRing(shape)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.DropdownList) {
                    if (open || SystemClock.uptimeMillis() - closedAt < REOPEN_GUARD_MS) return@clickable
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    menu.targetState = true
                }
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = SpicyType.Caption.copy(fontWeight = FontWeight.Medium))
            val turn by animateFloatAsState(if (open) 180f else 0f, tween(SpicyMotion.FAST_MS), label = "selectChevron")
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = SpicyColors.TextSecondary,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
            )
        }
        if (menu.currentState || menu.targetState) {
            val density = LocalDensity.current
            val margin = with(density) { SpicySpacing.S2.roundToPx() }
            // The keyboard, read every frame it slides, so the menu moves with it instead of
            // waiting for its own size to change.
            val keyboard = WindowInsets.ime.getBottom(density)
            // The menu gets a slot as tall as it could ever be on the roomier side of the pill,
            // and sits at the slot's end nearest the pill. Narrowed by a search, it shrinks toward
            // the pill instead of leaving it behind.
            val bottom = LocalView.current.rootView.height - keyboard
            val gap = margin / 2
            val tallest = with(density) { MENU_MAX_HEIGHT.roundToPx() }
            val roomBelow = bottom - margin - (pillBottom + gap)
            val roomAbove = pillTop - gap - margin
            val above = roomBelow < tallest && roomAbove > roomBelow
            val slot = minOf(tallest, if (above) roomAbove else roomBelow).coerceAtLeast(0)
            val position = remember(margin, keyboard, above) { SelectMenuPosition(margin, keyboard, above) }
            Popup(popupPositionProvider = position, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
                val origin = TransformOrigin(1f, if (above) 1f else 0f)
                AnimatedVisibility(
                    visibleState = menu,
                    enter = fadeIn(tween(SpicyMotion.FAST_MS)) +
                        scaleIn(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Modal), initialScale = 0.94f, transformOrigin = origin),
                    exit = fadeOut(tween(SpicyMotion.FAST_MS)) +
                        scaleOut(tween(SpicyMotion.FAST_MS), targetScale = 0.94f, transformOrigin = origin),
                ) {
                    val plate = RoundedCornerShape(SpicyRadii.Md)
                    // Starts empty each time the menu opens.
                    var query by remember { mutableStateOf("") }
                    Box(
                        Modifier
                            .height(with(density) { slot.toDp() })
                            // The slot's empty part is outside the menu: a tap there closes it.
                            .pointerInput(Unit) { detectTapGestures { close() } },
                        contentAlignment = if (above) Alignment.BottomEnd else Alignment.TopEnd,
                    ) {
                    Column(
                        Modifier
                            .widthIn(min = 180.dp, max = 280.dp)
                            // A text field has no useful intrinsic width, so a searchable menu has its own.
                            .then(if (searchable) Modifier.width(SEARCHABLE_MENU_WIDTH) else Modifier.width(IntrinsicSize.Max))
                            .heightIn(max = with(density) { slot.toDp() })
                            .clip(plate)
                            .background(MENU_FILL)
                            .border(1.dp, SpicyColors.HairlineStrong, plate)
                            // Taps on the plate between options stay on the menu.
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        // Stays put while the options scroll under it.
                        if (searchable) {
                            SpicySearchBar(
                                query,
                                { query = it },
                                Modifier.fillMaxWidth().padding(start = SpicySpacing.S2, end = SpicySpacing.S2, top = SpicySpacing.S2),
                                placeholder = "Search",
                            )
                        }
                        val listScroll = rememberScrollState()
                        Column(
                            Modifier
                                .scrollHints(listScroll)
                                .verticalScroll(listScroll)
                                .padding(vertical = SpicySpacing.S1),
                        ) {
                            val shown = items(close, query)
                            if (shown == 0) {
                                Text(
                                    "No matches",
                                    Modifier.padding(horizontal = SpicySpacing.S4, vertical = SpicySpacing.S3),
                                    style = SpicyType.Body.copy(color = SpicyColors.TextTertiary),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
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
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = SpicySpacing.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = SpicyType.Body.copy(
                color = if (selected) SpicyColors.TextPrimary else SpicyColors.TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        Box(Modifier.size(18.dp)) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = SpicyColors.TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Puts a menu under its pill, right edges lined up (the pills sit at a row's end), or over the
 * pill: always when [fixedAbove] is true, never when false, and when it doesn't fit below when
 * null ([above] then says which). Always [margin] inside the window and clear of the [keyboard].
 */
internal class SelectMenuPosition(
    private val margin: Int,
    private val keyboard: Int = 0,
    private val fixedAbove: Boolean? = null,
) : PopupPositionProvider {
    var above by mutableStateOf(fixedAbove ?: false)
        private set

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = margin / 2
        val bottom = windowSize.height - keyboard
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
        val below = anchorBounds.bottom + gap
        above = fixedAbove ?: (below + popupContentSize.height > bottom - margin &&
            anchorBounds.top - gap - popupContentSize.height >= margin)
        val y = if (above) anchorBounds.top - gap - popupContentSize.height else below
        val maxY = (bottom - popupContentSize.height - margin).coerceAtLeast(margin)
        return IntOffset(x, y.coerceIn(margin, maxY))
    }
}

internal val MENU_FILL = Color(30, 30, 34).copy(alpha = 0.98f)
private const val REOPEN_GUARD_MS = 300L

/** A flat tinted pill that shrinks to 0.97 while held. */
@Composable
fun SpicyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Narrower for a row of small buttons sharing the width. */
    horizontalPadding: androidx.compose.ui.unit.Dp = 14.dp,
    /** A platform's colour, which takes over the tint, the ring and the label. */
    brand: Color? = null,
    /** Marks a link that leaves the app with a small arrow. */
    external: Boolean = false,
) {
    val color = brand ?: SpicyColors.TextPrimary
    PressSurface(onClick, modifier, enabled, horizontalPadding, brand) {
        Text(text, style = SpicyType.Caption.copy(color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em))
        if (external) {
            Spacer(Modifier.width(6.dp))
            Image(
                rememberVectorPainter(Icons.Rounded.ArrowOutward),
                null,
                Modifier.size(12.dp).alpha(0.7f),
                colorFilter = ColorFilter.tint(color),
            )
        }
    }
}

/**
 * A value in a row of [SpicyButton]s that resets it when tapped. At its default it's plain text;
 * off it, it becomes a button with a reset mark, so the one way back sits on the value itself.
 */
@Composable
fun SpicyResettableValue(text: String, atDefault: Boolean, onReset: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val style = SpicyType.Caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    if (atDefault) {
        Box(modifier.heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
            Text(text, style = style.copy(color = SpicyColors.TextSecondary))
        }
    } else {
        PressSurface(onReset, modifier.semantics { contentDescription = "$text, tap to reset" }, enabled, horizontalPadding = 0.dp) {
            Text(text, style = style)
            Spacer(Modifier.width(SpicySpacing.S1))
            Image(
                rememberVectorPainter(Icons.Rounded.Replay),
                null,
                Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(SpicyColors.TextSecondary),
            )
        }
    }
}

/** A square [SpicyButton] around an icon, e.g. the source cards' up and down. */
@Composable
fun SpicyIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    PressSurface(onClick, modifier.size(36.dp), enabled, horizontalPadding = 0.dp, content = { content() })
}

@Composable
private fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp = 14.dp,
    brand: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "buttonScale")
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    Row(
        modifier
            .heightIn(min = 36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(
                when {
                    brand != null -> brand.copy(alpha = if (pressed) 0.24f else 0.14f)
                    pressed -> SpicyColors.TintBgPressed
                    else -> SpicyColors.TintBg
                },
            )
            .border(
                1.dp,
                when {
                    brand != null -> brand.copy(alpha = if (pressed) 0.55f else 0.35f)
                    pressed -> SpicyColors.HairlineStrong
                    else -> SpicyColors.Hairline
                },
                shape,
            )
            .focusRing(shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * A text field: tinted, hairline ring, brighter while focused.
 *
 * Built on the state-based text field, whose cursor handle scrolls a long line when dragged to
 * the edge; the older value-based one stops at what's visible. [value] still drives it: an
 * outside change replaces the text, and typing reports through [onValueChange].
 */
@Composable
fun SpicyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** The keyboard's action key becomes Search and calls this. */
    onSearch: (() -> Unit)? = null,
    /** Grows to a few lines, Enter adding one. */
    multiline: Boolean = false,
) {
    val state = rememberTextFieldState(value)
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { if (it != latestValue) latestOnValueChange(it) }
    }

    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val fieldModifier = modifier.onFocusChanged { focused = it.isFocused }
    val onKeyboardAction = onSearch?.let { search -> KeyboardActionHandler { search() } }
    val decorator = TextFieldDecorator { field ->
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .clip(shape)
                .background(if (focused) SpicyColors.TintBgPressed else SpicyColors.TintBg)
                .border(1.dp, if (focused) SpicyColors.HairlineStrong else SpicyColors.Hairline, shape)
                .padding(horizontal = 10.dp, vertical = if (multiline) 9.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
        ) {
            leading?.invoke()
            Box(Modifier.weight(1f)) {
                if (state.text.isEmpty()) Text(placeholder, style = SpicyType.Body.copy(color = SpicyColors.TextSecondary))
                field()
            }
            trailing?.invoke()
        }
    }
    val cursorBrush = SolidColor(SpicyColors.TextPrimary)
    if (password) {
        BasicSecureTextField(
            state = state,
            modifier = fieldModifier,
            textStyle = SpicyType.Body,
            onKeyboardAction = onKeyboardAction,
            cursorBrush = cursorBrush,
            decorator = decorator,
        )
    } else {
        BasicTextField(
            state = state,
            modifier = fieldModifier,
            textStyle = SpicyType.Body,
            keyboardOptions = if (onSearch != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
            onKeyboardAction = onKeyboardAction,
            lineLimits = if (multiline) TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6) else TextFieldLineLimits.SingleLine,
            cursorBrush = cursorBrush,
            decorator = decorator,
        )
    }
}

/** The search bar: the magnifier, the field, and a clear button once there's text. */
@Composable
fun SpicySearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search settings…") {
    val haptics = LocalHapticFeedback.current
    val glyph = SpicyColors.TextTertiary
    SpicyTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        leading = {
            Canvas(Modifier.size(14.dp)) {
                val s = size.width / 14f
                val stroke = Stroke(1.5f * s, cap = StrokeCap.Round)
                drawCircle(glyph, 4.5f * s, Offset(6f * s, 6f * s), style = stroke)
                drawLine(glyph, Offset(9.5f * s, 9.5f * s), Offset(13f * s, 13f * s), 1.5f * s, StrokeCap.Round)
            }
        },
        trailing = if (value.isEmpty()) null else {
            {
                Box(
                    Modifier
                        .size(28.dp)
                        .focusRing(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onValueChange("")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        val s = size.width / 10f
                        drawLine(glyph, Offset(s, s), Offset(9f * s, 9f * s), 1.5f * s, StrokeCap.Round)
                        drawLine(glyph, Offset(9f * s, s), Offset(s, 9f * s), 1.5f * s, StrokeCap.Round)
                    }
                }
            }
        },
    )
}

/**
 * A bipolar slider: a light groove with a centre tick, filled from zero out to the thumb, so
 * the fill's direction shows the sign. Under it, the value and a Reset once it's off [default].
 */
@Composable
fun SpicyBipolarSlider(
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    default: Int = 0,
    unit: String? = null,
    enabled: Boolean = true,
    /** False leaves out the value and Reset under the groove, for a control that shows them itself. */
    showValue: Boolean = true,
    /** The value as shown under the groove, in place of the number and [unit]. */
    valueText: ((Int) -> String)? = null,
) {
    val clamped = value.coerceIn(range)
    val onChangeUpdated by rememberUpdatedState(onValueChange)
    val shown by rememberUpdatedState(clamped)
    // Read through state: the drag handler below outlives this composition.
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    // A tick for each step the value moves.
    val currentOnChange = { v: Int ->
        if (v != shown) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            onChangeUpdated(v)
        }
    }
    var held by remember { mutableStateOf(false) }
    val peek by rememberUpdatedState(LocalSettingsPeek.current)
    val rowBounds by rememberUpdatedState(LocalRowBounds.current)
    val thumbScale by animateFloatAsState(if (held) 0.92f else 1f, tween(120), label = "thumb")
    val span = (range.last - range.first).coerceAtLeast(1)
    val thumbR = 8.dp
    fun valueAt(x: Float, width: Float, thumbPx: Float): Int {
        val f = ((x - thumbPx) / (width - 2f * thumbPx)).coerceIn(0f, 1f)
        val raw = range.first + f * span
        return ((raw / step).roundToInt() * step).coerceIn(range)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        val colors = SpicyColors
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                // A remote's or keyboard's left and right step it; up and down move on.
                .focusRing(RoundedCornerShape(SpicyRadii.Sm))
                .onKeyEvent { event ->
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -step
                        Key.DirectionRight -> step
                        else -> return@onKeyEvent false
                    }
                    if (enabled && event.type == KeyEventType.KeyDown) currentOnChange((shown + delta).coerceIn(range))
                    enabled
                }
                .focusable(enabled)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { currentOnChange(valueAt(it.x, size.width.toFloat(), thumbR.toPx())) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        held = true
                        // Clears the rest of the settings away while dragging, so the change shows on the lyrics.
                        peek?.startRow(rowBounds())
                        val width = size.width.toFloat()
                        val thumbPx = thumbR.toPx()
                        try {
                            currentOnChange(valueAt(slop.position.x, width, thumbPx))
                            horizontalDrag(slop.id) {
                                it.consume()
                                currentOnChange(valueAt(it.position.x, width, thumbPx))
                            }
                        } finally {
                            held = false
                            peek?.end()
                        }
                    }
                },
        ) {
            val thumbPx = thumbR.toPx()
            fun posFor(f: Float) = thumbPx + f * (size.width - 2f * thumbPx)
            val cy = size.height / 2f
            val trackH = 4.dp.toPx()
            val zeroF = ((if (range.first < 0 && range.last > 0) 0 else range.first) - range.first) / span.toFloat()
            val f = (clamped - range.first) / span.toFloat()
            drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, cy - trackH / 2f), Size(size.width, trackH), CornerRadius(trackH / 2f))
            val from = posFor(minOf(zeroF, f))
            val to = posFor(maxOf(zeroF, f))
            drawRoundRect(colors.Accent, Offset(from, cy - trackH / 2f), Size(to - from, trackH), CornerRadius(trackH / 2f))
            val tickH = 12.dp.toPx()
            drawRoundRect(
                colors.HairlineStrong,
                Offset(posFor(zeroF) - 1.dp.toPx(), cy - tickH / 2f),
                Size(2.dp.toPx(), tickH),
                CornerRadius(1.dp.toPx()),
            )
            val thumb = Offset(posFor(f), cy)
            val r = thumbPx * thumbScale
            drawCircle(Color.Black.copy(alpha = 0.3f), r + 2.dp.toPx(), thumb.copy(y = thumb.y + 2.dp.toPx()))
            drawCircle(Color.White, r, thumb)
        }
        if (showValue) Row(Modifier.fillMaxWidth().heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            val sign = if (range.first < 0 && clamped > 0) "+" else ""
            Text(
                valueText?.invoke(clamped) ?: "$sign$clamped${unit?.let { " $it" }.orEmpty()}",
                style = SpicyType.Caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f),
            )
            if (clamped != default && enabled) {
                Text(
                    "Reset",
                    style = SpicyType.Caption.copy(fontWeight = FontWeight.Medium, color = SpicyColors.TextSecondary),
                    modifier = Modifier
                        .focusRing(RoundedCornerShape(SpicyRadii.Sm))
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onValueChange(default)
                        }
                        .padding(vertical = SpicySpacing.S1, horizontal = SpicySpacing.S1),
                )
            }
        }
    }
}

/** A quiet outlined card: hairline ring, large radius. */
fun Modifier.outlinedCard(tinted: Boolean = false): Modifier = composed {
    val shape = RoundedCornerShape(SpicyRadii.Lg)
    clip(shape)
        .then(if (tinted) Modifier.background(SpicyColors.TintBg) else Modifier)
        .border(1.dp, SpicyColors.Hairline, shape)
}

/** A disabled row: label and control at 45%. */
const val DISABLED_ALPHA = 0.45f
