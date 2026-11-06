package com.tx24.spicyplayer.ui.components

import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.fadeOut

import androidx.compose.animation.fadeIn

import androidx.compose.animation.shrinkHorizontally

import androidx.compose.animation.expandHorizontally

import androidx.compose.animation.ExitTransition

import androidx.compose.animation.EnterTransition

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.animateContentSize

import androidx.compose.animation.core.snap

import androidx.compose.animation.core.AnimationSpec

import kotlinx.coroutines.flow.first

import androidx.compose.runtime.snapshotFlow

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.tx24.spicyplayer.update.ReleaseNotes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.haptics.withHaptic
import com.tx24.spicyplayer.ui.theme.LocalSpicyPalette
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyPalette
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect

/*
 * Pop-ups, and the pieces message cards (an update notice, a permission request) are built
 * from, so any pop-up here is a [SpicyModal] filled with
 * [SpicyModalMessage], [SpicyVersionRow], [SpicyModalNotes] and [SpicyModalButton]s.
 */

/**
 * A centred glass pop-up over a dimmed page. Opening, the page dims to 45% black,
 * the glass plate fades in and grows from 0.96, all in 220 ms; closing plays it backwards, and
 * the pop-up stays composed until that ends.
 *
 * @param visible whether it is showing; flip to false to close it.
 * @param onDismissRequest called by the close button, a tap outside, or back. Null makes the
 * pop-up one the user has to act on: no close button, and taps outside and back do nothing.
 * @param title the header's title; null leaves the header out, for pop-ups that carry their
 * own heading like an update card.
 * @param backdrop what the plate blurs; without one the plate is a darker solid.
 */
@Composable
fun SpicyModal(
    visible: Boolean,
    onDismissRequest: (() -> Unit)?,
    backdrop: HazeState?,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** The plate takes its full height and the body neither scrolls nor pads, for a page that scrolls itself. */
    fillBody: Boolean = false,
    /**
     * Shown while the pop-up opens, in place of [content], which comes in once it's open. Building
     * a heavy body in the same frames as the opening animation stutters it, as Settings found.
     */
    skeleton: (@Composable ColumnScope.() -> Unit)? = null,
    /** A screen inside the pop-up: a back arrow by the title, and Back, step out to the one before. */
    onBack: (() -> Unit)? = null,
    /** Its colours; a branded one makes the plate the brand's ramp, with the mark in its corner. */
    palette: SpicyPalette = LocalPopupPalette.current,
    /**
     * Lets a slider being dragged clear the pop-up away, all but its own row, to show the lyrics
     * behind it as in Settings ([SettingsPeek]); null keeps the pop-up up.
     */
    peek: SettingsPeek? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = remember { Animatable(0f) }
    val brand = palette.brand
    // The mark turns from -24° to -14° as the pop-up opens, slower than the plate.
    val markTurn = remember { Animatable(0f) }
    // Interface animations off: it opens and closes at once.
    val animate = LocalUiAnimations.current
    val openSpec: AnimationSpec<Float> = if (animate) tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal) else snap()
    var composed by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            launch { markTurn.animateTo(1f, if (animate) tween(MARK_TURN_MS, easing = SpicyMotion.Modal) else snap()) }
            open.animateTo(1f, openSpec)
        } else {
            open.animateTo(0f, openSpec)
            markTurn.snapTo(0f)
            composed = false
        }
    }
    if (!composed) return

    // Below the return, so each opening starts on the skeleton again.
    var settled by remember { mutableStateOf(skeleton == null) }
    val bodyFade = remember { Animatable(1f) }
    if (skeleton != null) LaunchedEffect(Unit) {
        snapshotFlow { open.value }.first { it >= 1f }
        bodyFade.snapTo(0f)
        settled = true
        if (animate) bodyFade.animateTo(1f, tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard)) else bodyFade.snapTo(1f)
    }
    val body: @Composable ColumnScope.() -> Unit = if (settled) content else skeleton!!

    if (onDismissRequest != null) BackHandler(enabled = visible) { onDismissRequest() }
    // Declared after, so it's asked first.
    if (onBack != null) BackHandler(enabled = visible) { onBack() }
    val peekAmount by animateFloatAsState(
        if (peek?.active == true) 1f else 0f,
        if (animate) tween(PEEK_MS, easing = SpicyMotion.Standard) else snap(),
        label = "modalPeek",
    )
    if (peek != null) {
        LaunchedEffect(visible) { if (!visible) peek.end() }
        BackHandler(enabled = visible && peek.active) { peek.end() }
    }
    var plateOrigin by remember { mutableStateOf(Offset.Zero) }
    // The plate's contents without its glass, for the dragged row: drawn from the whole plate, the
    // glass would cover the dark plate it sits on while peeking.
    val contentLayer = rememberGraphicsLayer()

    // Set by the plate when a touch lands on it. Marking rather than consuming: drags inside the
    // plate (a text field's scroll, say) give up on a touch that something else consumed.
    val touchOnPlate = remember { BooleanArray(1) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // The dim: rgba(0,0,0,.45) (.55 behind a branded plate), fading with the modal.
            .drawBehind { drawRect(Color.Black.copy(alpha = (if (brand != null) BRAND_OVERLAY_ALPHA else OVERLAY_ALPHA) * open.value * (1f - peekAmount))) }
            // Takes every touch, so nothing reaches the page; a tap outside the plate dismisses.
            // The plate sees its touches first and marks them, which is how this tells them apart.
            .pointerInput(onDismissRequest) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val outside = !touchOnPlate[0]
                        if (outside) event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) {
                            touchOnPlate[0] = false
                            val released = event.changes.all { it.previousPressed }
                            if (onDismissRequest != null && outside && released) onDismissRequest()
                        }
                    }
                }
            }
            // The dim covers the whole screen, bars included; the plate keeps clear of them.
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(SpicyRadii.Lg)
        val glyph = rememberBrandGlyph()
        Column(
            Modifier
                // 420px wide at most and 90% of the screen high, inside the screen's margins.
                .widthIn(max = PLATE_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = SpicySpacing.S4)
                .then(if (fillBody) Modifier.height(maxHeight * 0.9f) else Modifier.heightIn(max = maxHeight * 0.9f))
                // When what's inside changes (the skeleton giving way, a screen sliding in, results
                // arriving), the plate grows or shrinks to it rather than jumping.
                .then(if (animate && !fillBody) Modifier.animateContentSize(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) else Modifier)
                .graphicsLayer {
                    val scale = CLOSED_SCALE + (1f - CLOSED_SCALE) * open.value
                    scaleX = scale
                    scaleY = scale
                    alpha = open.value
                }
                .onGloballyPositioned { plateOrigin = it.positionInRoot() }
                // Peeking draws the plate faded out, and the dragged slider's row alone at full
                // strength, on a plate of its own.
                .drawWithContent {
                    val amount = peekAmount
                    if (amount <= 0f) return@drawWithContent drawContent()
                    // Even fully faded, so the contents are recorded for the row below.
                    fadedLayer(1f - amount) { drawContent() }
                    val keep = peek?.keep?.translate(-plateOrigin) ?: return@drawWithContent
                    val inset = SpicySpacing.S1.toPx()
                    drawRoundRect(
                        PEEK_PLATE.copy(alpha = PEEK_PLATE.alpha * amount),
                        keep.topLeft - Offset(inset, inset),
                        Size(keep.width + 2 * inset, keep.height + 2 * inset),
                        CornerRadius(SpicyRadii.Md.toPx()),
                    )
                    clipRect(keep.left, keep.top, keep.right, keep.bottom) { fadedLayer(amount) { drawLayer(contentLayer) } }
                }
                .modalShadow()
                .clip(shape)
                .then(
                    if (brand != null) {
                        // Opaque: the ramp, and the logo cropped big off the top-right corner.
                        Modifier.drawBehind {
                            drawBrandRamp(brand)
                            drawBrandMark(
                                glyph, brand.tint, alpha = 0.55f,
                                width = size.width * 0.72f, right = -size.width * 0.2f, top = -size.height * 0.26f,
                                degrees = -24f + 10f * markTurn.value,
                            )
                        }
                    } else {
                        (backdrop?.let { Modifier.hazeEffect(it, PlateMaterial) { inputScale = HazeInputScale.Auto } } ?: Modifier.background(palette.BgElevated))
                            .background(PLATE_FILL)
                    },
                )
                .plateEdges(
                    top = brand?.ink?.copy(alpha = 0.22f) ?: Color.White.copy(alpha = 0.08f),
                    ring = brand?.ink?.copy(alpha = 0.16f) ?: palette.HairlineStrong,
                )
                .drawWithContent {
                    if (peekAmount <= 0f) return@drawWithContent drawContent()
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                }
                // Touches on the plate stay on the plate, and so do a remote's arrows.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(); touchOnPlate[0] = true } } }
                .focusLayer(current = visible, ready = settled),
        ) {
          CompositionLocalProvider(LocalSpicyPalette provides palette, LocalSettingsPeek provides (peek ?: LocalSettingsPeek.current)) {
            if (title != null) ModalHeader(title, onDismissRequest, onBack, animate)
            if (fillBody) {
                Column(Modifier.weight(1f).graphicsLayer { alpha = bodyFade.value }, content = body)
            } else {
                // The body: padding 20px 24px 24px, scrolling when it runs long, and saying so.
                val bodyScroll = rememberScrollState()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .scrollHints(bodyScroll)
                        .verticalScroll(bodyScroll)
                        .padding(start = SpicySpacing.S6, end = SpicySpacing.S6, top = SpicySpacing.S5, bottom = SpicySpacing.S6)
                        .graphicsLayer { alpha = bodyFade.value },
                    verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
                    content = body,
                )
            }
          }
        }
    }
}

/** The header: title, close button, hairline underneath. */
@Composable
private fun ModalHeader(title: String, onDismissRequest: (() -> Unit)?, onBack: (() -> Unit)?, animate: Boolean) {
    // Kept while the arrow leaves, so its last tap still goes somewhere.
    var lastBack by remember { mutableStateOf(onBack) }
    if (onBack != null) lastBack = onBack
    val backStart by animateDpAsState(
        if (onBack != null) SpicySpacing.S3 else SpicySpacing.S6,
        if (animate) tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal) else snap(),
        label = "headerStart",
    )
    val hairline = SpicyColors.Hairline
    val branded = SpicyColors.brand != null
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val px = 1.dp.toPx()
                drawRect(hairline, Offset(0f, size.height - px), Size(size.width, px))
            }
            .padding(start = backStart, end = SpicySpacing.S4, top = SpicySpacing.S5, bottom = SpicySpacing.S4)
            .heightIn(min = 32.dp),
        verticalAlignment = if (branded) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        AnimatedVisibility(
            visible = onBack != null,
            enter = if (animate) fadeIn(tween(SpicyMotion.MODAL_MS)) + expandHorizontally(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) else EnterTransition.None,
            exit = if (animate) fadeOut(tween(SpicyMotion.FAST_MS)) + shrinkHorizontally(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) else ExitTransition.None,
        ) {
            BackButton { lastBack?.invoke() }
        }
        if (branded) {
            Column(Modifier.weight(1f).padding(top = SpicySpacing.S1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpicyBrandLine()
                Text(title, style = SpicyType.Title.copy(fontSize = BrandTitleSize, lineHeight = 1.1.em, letterSpacing = (-0.025f).em))
            }
        } else Text(
            title,
            modifier = Modifier.weight(1f),
            style = SpicyType.Title.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01f).em),
        )
        if (onDismissRequest != null) CloseButton(onDismissRequest)
    }
}

/** The close button: an X in secondary text, a tinted pill while held. */
/** A chevron to step back, drawn like [CloseButton]'s cross. */
@Composable
private fun BackButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val primary = SpicyColors.TextPrimary
    val secondary = SpicyColors.TextSecondary
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "backScale")
    Box(
        Modifier
            .size(TAP_MIN)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (pressed) SpicyColors.TintBgPressed else Color.Transparent)
            .focusRing(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .semantics { contentDescription = "Back" }
            .drawBehind {
                val half = 9.dp.toPx() * 0.9f
                val c = center
                val color = if (pressed) primary else secondary
                val width = 1.6.dp.toPx()
                val tip = Offset(c.x - half * 0.5f, c.y)
                drawLine(color, Offset(c.x + half * 0.5f, c.y - half), tip, width, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x + half * 0.5f, c.y + half), tip, width, cap = StrokeCap.Round)
            },
    )
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val primary = SpicyColors.TextPrimary
    val secondary = SpicyColors.TextSecondary
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "closeScale")
    Box(
        Modifier
            .size(TAP_MIN)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (pressed) SpicyColors.TintBgPressed else Color.Transparent)
            .focusRing(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .semantics { contentDescription = "Close" }
            .drawBehind {
                // The close glyph: two strokes corner to corner, 18px across.
                val half = 9.dp.toPx() * 0.9f
                val c = center
                val color = if (pressed) primary else secondary
                val width = 1.6.dp.toPx()
                drawLine(color, Offset(c.x - half, c.y - half), Offset(c.x + half, c.y + half), width)
                drawLine(color, Offset(c.x + half, c.y - half), Offset(c.x - half, c.y + half), width)
            },
    )
}

/**
 * A message card: an icon in a tinted disc, a headline and a description, centred.
 * [icon] is optional.
 */
@Composable
fun SpicyModalMessage(
    title: String,
    description: String?,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SpicyColors.TintBg)
                    .border(1.dp, SpicyColors.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
        Text(title, style = SpicyModalTitleStyle.copy(textAlign = TextAlign.Center))
        if (description != null) {
            Text(
                description,
                modifier = Modifier.widthIn(max = 352.dp),
                style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.55.em, textAlign = TextAlign.Center),
            )
        }
    }
}

/** `.uc-title` and `.uc-subtitle`, left-aligned, for a card-style pop-up with its own heading. */
@Composable
fun SpicyModalHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S1)) {
        if (SpicyColors.brand != null) {
            SpicyBrandLine(Modifier.padding(bottom = 6.dp))
            Text(title, style = SpicyModalTitleStyle.copy(fontSize = BrandTitleSize, fontWeight = FontWeight.Bold, lineHeight = 1.1.em, letterSpacing = (-0.025f).em))
        } else Text(title, style = SpicyModalTitleStyle)
        if (subtitle != null) {
            Text(subtitle, style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.45.em))
        }
    }
}

/** `.uc-title`: 1.2rem semibold. */
val SpicyModalTitleStyle @Composable @ReadOnlyComposable get() = SpicyType.Title.copy(fontSize = 19.2.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em, letterSpacing = (-0.015f).em)

/** `.uc-divider`. */
@Composable
fun SpicyModalDivider() {
    Box(Modifier.padding(vertical = SpicySpacing.S1).fillMaxWidth().height(1.dp).background(SpicyColors.Hairline))
}

/** `.uc-version-row`: "from -> to" on a tinted strip, the new version brighter. */
@Composable
fun SpicyVersionRow(from: String?, to: String?) {
    Row(
        Modifier
            .padding(top = SpicySpacing.S1, bottom = SpicySpacing.S2)
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpicyRadii.Md))
            .background(SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, RoundedCornerShape(SpicyRadii.Md))
            .padding(horizontal = SpicySpacing.S4, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val version = SpicyType.Body.copy(
            fontSize = 16.8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.01f).em,
            fontFeatureSettings = "tnum",
        )
        if (from != null) Text(from, style = version.copy(color = SpicyColors.TextSecondary))
        if (from != null && to != null) Text("->", style = version.copy(color = SpicyColors.TextTertiary))
        if (to != null) Text(to, style = version)
    }
}

/**
 * `.uc-patch-notes`: the release's notes ([ReleaseNotesView]), or [status] (loading, failed,
 * nothing new) in their place. A long release scrolls on its own, at most [NOTES_MAX_HEIGHT]
 * tall, so the buttons under it stay in reach.
 */
@Composable
fun SpicyModalNotes(notes: ReleaseNotes, status: String? = null) {
    if (status != null || notes.isEmpty) {
        Text(status ?: "", style = SpicyType.Body.copy(color = SpicyColors.TextSecondary, lineHeight = 1.5.em))
        return
    }
    val scroll = rememberScrollState()
    ReleaseNotesView(notes, Modifier.heightIn(max = NOTES_MAX_HEIGHT).scrollHints(scroll).verticalScroll(scroll))
}

private val NOTES_MAX_HEIGHT = 320.dp

/** The update card's buttons: `.btn-primary`, `.btn-secondary`, `.btn-quiet`. */
enum class SpicyButtonStyle { Primary, Secondary, Quiet }

/**
 * An update-card button: rounded 8dp, semibold body text, shrinking to 0.97 while held.
 * [fill] stretches it across its row, like the migration card's.
 */
@Composable
fun SpicyModalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SpicyButtonStyle = SpicyButtonStyle.Secondary,
    fill: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "modalButtonScale")
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val (background, color) = when (style) {
        SpicyButtonStyle.Primary -> (if (pressed) SpicyColors.AccentPressed else SpicyColors.Accent) to SpicyColors.TextOnFill
        SpicyButtonStyle.Secondary -> (if (pressed) SpicyColors.TintBgPressed else SpicyColors.TintBg) to SpicyColors.TextPrimary
        SpicyButtonStyle.Quiet -> (if (pressed) SpicyColors.TintBg else Color.Transparent) to
            (if (pressed) SpicyColors.TextPrimary else SpicyColors.TextSecondary)
    }
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .then(
                if (style == SpicyButtonStyle.Secondary) {
                    Modifier.border(1.dp, if (pressed) SpicyColors.HairlineStrong else SpicyColors.Hairline, shape)
                } else Modifier,
            )
            .focusRing(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = if (style == SpicyButtonStyle.Quiet) 14.dp else 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SpicyType.Body.copy(color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em))
    }
}

/** `.uc-actions`: buttons end-aligned, wrapping onto a new line when they run out of room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpicyModalActions(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
    ) { content() }
}

/** Space between a pop-up's message and its buttons. */
@Composable
fun SpicyModalGap() = Spacer(Modifier.height(SpicySpacing.S2))

/** The plate: `rgba(22,22,22,.55)` over `blur(40px) saturate(1.5)`. */
private val PLATE_FILL = Color(22, 22, 22).copy(alpha = 0.55f)
private val PlateMaterial = HazeStyle(
    backgroundColor = Color.Black,
    tints = emptyList(),
    blurRadius = 40.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(Color(22, 22, 22).copy(alpha = 0.9f)),
)

/** `inset 0 1px 0 [top], inset 0 0 0 1px [ring]`. */
private fun Modifier.plateEdges(top: Color, ring: Color): Modifier = drawBehind {
    val px = 1.dp.toPx()
    val radius = CornerRadius(SpicyRadii.Lg.toPx())
    clipRect(bottom = px * 2f) {
        drawRoundRect(top, cornerRadius = radius, topLeft = Offset(0f, px), size = size)
    }
    drawRoundRect(
        ring,
        topLeft = Offset(px / 2f, px / 2f),
        size = Size(size.width - px, size.height - px),
        cornerRadius = CornerRadius(radius.x - px / 2f),
        style = Stroke(px),
    )
}

/**
 * `0 24px 64px -12px rgba(0,0,0,.45)` as an Android shadow layer: the plate shrunk by the spread,
 * pushed down, blurred. Hardware canvases only draw shadow layers for shapes from API 28.
 */
private fun Modifier.modalShadow(): Modifier = drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@drawBehind
    val spread = 12.dp.toPx()
    // A CSS blur length is twice the Gaussian sigma; Skia's sigma = radius * 0.57735 + 0.5.
    val radius = ((32.dp.toPx() - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val corner = SpicyRadii.Lg.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(radius, 0f, 24.dp.toPx(), Color.Black.copy(alpha = 0.45f).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(spread, spread, size.width - spread, size.height - spread, corner, corner, paint)
    }
}

private const val OVERLAY_ALPHA = 0.45f
private const val BRAND_OVERLAY_ALPHA = 0.55f
private const val MARK_TURN_MS = 700
private const val CLOSED_SCALE = 0.96f
private val PLATE_WIDTH = 420.dp
private val TAP_MIN = 40.dp

/** Draws [block] at [alpha] as one layer, so overlapping parts don't show through each other. */
private inline fun DrawScope.fadedLayer(alpha: Float, block: () -> Unit) {
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

/** How long the pop-up takes to clear away for a peek, and to come back. */
private const val PEEK_MS = 250
/** The plate a dragged slider's row sits on while the rest of the pop-up is cleared away. */
private val PEEK_PLATE = Color(0xE0141414)
