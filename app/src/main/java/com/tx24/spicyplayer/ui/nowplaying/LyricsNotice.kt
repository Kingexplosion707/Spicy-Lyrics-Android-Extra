package com.tx24.spicyplayer.ui.nowplaying

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.haptics.withHaptic
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.settings.DISCORD_URL
import com.tx24.spicyplayer.ui.settings.openUrl
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay

/** What the notice's buttons do. The floating window passes none and shows the notice without buttons. */
internal class NoticeActions(
    val onRetry: () -> Unit,
    /** The debug report for this song, which "Copy details" puts on the clipboard with the notice's code. */
    val report: () -> String,
    val onSearchSpotify: () -> Unit,
)

/** [error]'s notice; compact (title and a short detail, no buttons) without [actions]. */
@Composable
internal fun LyricsNotice(
    error: LyricsState.Error,
    actions: NoticeActions?,
    bottomPx: () -> Float,
    modifier: Modifier = Modifier,
) = LyricsNotice(
    title = error.message,
    body = error.body,
    detail = error.detail,
    modifier = modifier,
    kind = error.kind,
    code = error.code,
    actions = actions,
    bottomPx = bottomPx,
    compact = actions == null,
)

/**
 * A notice in place of lyrics: a bold title, the explanation under it in secondary text, the
 * per-source detail as a quiet line, then pill buttons for what can be done about it (try again,
 * copy the details, ask on Discord, find the song on Spotify) and, when it's worth reporting, its
 * code in small monospace. Centred, at most 34em of body text wide, rising in over 350 ms. On a box
 * narrower than 360dp it's left-aligned with a smaller title. Text is sized off the box's width.
 * [bottomPx] is how much of the box's bottom the controls and their shade's blur cover right
 * now; the notice moves up by half of it, or far enough to clear it when the notice is tall,
 * while there's room.
 *
 * [compact], for the floating window: the title alone, stepped down until two lines fit the
 * window's height, and the detail's first sentence on one line if there's room under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricsNotice(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    detail: String? = null,
    kind: NoticeKind? = null,
    code: String? = null,
    actions: NoticeActions? = null,
    bottomPx: () -> Float = { 0f },
    compact: Boolean = false,
) {
    val animate = LocalUiAnimations.current
    val entrance = remember(title) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(entrance) { entrance.animateTo(1f, tween(350, easing = SpicyMotion.Modal)) }
    val density = LocalDensity.current
    val riseOffsetPx = with(density) { 12.dp.toPx() }
    val enter = Modifier.graphicsLayer {
        alpha = entrance.value
        translationY = (1f - entrance.value) * riseOffsetPx
    }
    if (compact) {
        CompactNotice(title, detail?.substringBefore(". ")?.trimEnd('.'), modifier.then(enter))
        return
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val narrow = maxWidth < 360.dp
        val bodySp = (maxWidth.value * 0.036f).coerceIn(14.5f, 18f)
        val titleSp = bodySp * if (narrow) 1.5f else 1.85f
        val em = with(density) { bodySp.sp.toDp() }
        val width = minOf(maxWidth - if (narrow) 40.dp else 48.dp, em * 34)
        val align = if (narrow) TextAlign.Start else TextAlign.Center
        val boxHeight = maxHeight
        val boxHeightPx = constraints.maxHeight
        var contentHeightPx by remember { mutableIntStateOf(0) }

        // Scrolls only when the notice is taller than the box (a short landscape panel).
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = boxHeight), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .width(width)
                        .onSizeChanged { contentHeightPx = it.height }
                        .graphicsLayer {
                            // Up by half the covered strip, or as far as it takes to clear the
                            // strip when the notice is tall, as far as the space above allows.
                            val room = ((boxHeightPx - contentHeightPx) / 2f).coerceAtLeast(0f)
                            val covered = bottomPx()
                            translationY = -minOf(maxOf(covered / 2f, covered - room), room)
                        }
                        .then(enter),
                    horizontalAlignment = if (narrow) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    val base = SpicyType.Body.copy(textAlign = align)
                    Text(
                        title,
                        style = base.copy(
                            fontSize = titleSp.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.025f).em,
                            lineHeight = 1.15.em,
                            lineBreak = LineBreak.Heading,
                        ),
                    )
                    if (body != null) {
                        Text(
                            body,
                            modifier = Modifier.padding(top = em * 0.85f),
                            style = base.copy(
                                fontSize = bodySp.sp,
                                lineHeight = 1.55.em,
                                color = SpicyColors.TextSecondary,
                                lineBreak = LineBreak.Paragraph,
                            ),
                        )
                    }
                    if (detail != null) {
                        Text(
                            detail,
                            modifier = Modifier.padding(top = em * 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = base.copy(fontSize = (bodySp * 0.86f).sp, lineHeight = 1.4.em, color = SpicyColors.TextTertiary),
                        )
                    }
                    if (kind != null && actions != null) {
                        NoticeButtons(kind, code, detail, actions, bodySp, em, narrow)
                    }
                    if (kind != null && kind.report) {
                        Text(
                            "Code: ${codeLabel(kind, code)}",
                            modifier = Modifier.padding(top = em),
                            style = base.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (bodySp * 0.76f).sp,
                                color = SpicyColors.TextTertiary,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeButtons(
    kind: NoticeKind,
    code: String?,
    detail: String?,
    actions: NoticeActions,
    bodySp: Float,
    em: androidx.compose.ui.unit.Dp,
    narrow: Boolean,
) {
    val context = LocalContext.current
    var copy by remember { mutableStateOf(CopyState.Idle) }
    LaunchedEffect(copy) {
        if (copy != CopyState.Idle) {
            delay(2000)
            copy = CopyState.Idle
        }
    }
    val spotify = kind == NoticeKind.NoLyrics || kind == NoticeKind.Unavailable
    if (!kind.retry && !kind.report && !spotify) return
    FlowRow(
        Modifier.padding(top = em * 1.6f),
        horizontalArrangement = Arrangement.spacedBy(em * 0.6f, if (narrow) Alignment.Start else Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(em * 0.6f),
    ) {
        if (kind.retry) {
            NoticeButton("Try again", Icons.Rounded.Refresh, actions.onRetry, primary = true, bodySp)
        }
        if (kind.report) {
            val (label, icon) = when (copy) {
                CopyState.Idle -> "Copy details" to Icons.Rounded.ContentCopy
                CopyState.Copied -> "Copied" to Icons.Rounded.Check
                CopyState.Failed -> "Couldn't copy" to Icons.Rounded.ErrorOutline
            }
            NoticeButton(label, icon, onClick = {
                val text = buildString {
                    append(actions.report())
                    appendLine()
                    appendLine()
                    appendLine("Notice: ${codeLabel(kind, code)}")
                    if (detail != null) append("  $detail")
                }.trimEnd()
                val copied = runCatching {
                    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: error("no clipboard")
                    clipboard.setPrimaryClip(ClipData.newPlainText("Spicy Lyrics Mobile debug info", text))
                }.isSuccess
                copy = if (copied) CopyState.Copied else CopyState.Failed
            }, primary = !kind.retry, bodySp)
            NoticeButton("Get help on Discord", Icons.Rounded.Forum, { openUrl(context, DISCORD_URL) }, primary = false, bodySp)
        }
        if (spotify) {
            NoticeButton("Find on Spotify", Icons.Rounded.Search, actions.onSearchSpotify, primary = false, bodySp)
        }
    }
}

private enum class CopyState { Idle, Copied, Failed }

/** The notice's kind and code as they're reported, e.g. "server-error(503)". */
private fun codeLabel(kind: NoticeKind, code: String?) = kind.slug + (code?.let { "($it)" } ?: "")

/**
 * A pill button with an icon before its label, which crossfades when either changes. Primary is
 * the accent fill; the others are the tinted, hairline-ringed secondary.
 */
@Composable
private fun NoticeButton(label: String, icon: ImageVector, onClick: () -> Unit, primary: Boolean, bodySp: Float) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "noticeButtonScale")
    val background = when {
        primary -> if (pressed) SpicyColors.AccentPressed else SpicyColors.Accent
        else -> if (pressed) SpicyColors.TintBgPressed else SpicyColors.TintBg
    }
    val color = if (primary) SpicyColors.TextOnFill else SpicyColors.TextPrimary
    val textSp = (bodySp * 0.94f).coerceAtMost(16f)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (primary) Modifier else Modifier.border(1.dp, if (pressed) SpicyColors.HairlineStrong else SpicyColors.Hairline, CircleShape),
            )
            .background(background, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = withHaptic(HapticFeedbackType.ContextClick, onClick))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = label to icon,
            transitionSpec = { fadeIn(tween(SpicyMotion.FAST_MS)) togetherWith fadeOut(tween(SpicyMotion.FAST_MS)) },
            contentAlignment = Alignment.Center,
            label = "noticeButtonLabel",
        ) { (text, glyph) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(glyph, null, Modifier.size((textSp * 1.2f).dp), tint = color)
                Spacer(Modifier.width(8.dp))
                Text(text, style = SpicyType.Body.copy(fontSize = textSp.sp, color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em))
            }
        }
    }
}

/**
 * The floating window's notice: the title in bold, as big as the window allows up to two lines,
 * and [detail] on one line under it when there's room. Centred in the window.
 */
@Composable
private fun CompactNotice(title: String, detail: String?, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val base = SpicyType.Body.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, letterSpacing = (-0.02f).em, lineHeight = 1.15.em)
        val detailBase = SpicyType.Body.copy(textAlign = TextAlign.Center, color = SpicyColors.TextSecondary)
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val gapPx = with(density) { 4.dp.toPx() }
        val (titleSp, detailSp) = remember(title, detail, widthPx, heightPx, density) {
            // Stepped down by a tenth until two lines fit the height (or the text reaches 10sp).
            var size = (maxWidth.value / 11f).coerceIn(12f, 28f)
            var titleHeight: Int
            while (true) {
                val layout = measurer.measure(
                    title, base.copy(fontSize = size.sp), overflow = TextOverflow.Ellipsis, maxLines = 2,
                    constraints = Constraints(maxWidth = widthPx),
                )
                titleHeight = layout.size.height
                if ((!layout.hasVisualOverflow && titleHeight <= heightPx) || size <= 10f) break
                size = maxOf(10f, size * 0.9f)
            }
            val detailSize = maxOf(9f, size * 0.6f)
            val detailFits = detail != null && titleHeight + gapPx + measurer.measure(
                detail, detailBase.copy(fontSize = detailSize.sp), maxLines = 1,
                constraints = Constraints(maxWidth = widthPx),
            ).size.height <= heightPx
            size to detailSize.takeIf { detailFits }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = base.copy(fontSize = titleSp.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null && detailSp != null) {
                Text(
                    detail,
                    modifier = Modifier.padding(top = 4.dp),
                    style = detailBase.copy(fontSize = detailSp.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
