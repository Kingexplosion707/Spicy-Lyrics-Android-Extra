package com.tx24.spicyplayer.lyrics.spicy.canvas

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.ui.controls.keepsControlsHidden
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Which credits stay on screen, pinned under the lyrics, instead of scrolling after them. */
enum class PinnedFooterMode(val label: String) {
    Off("Off"),
    /** Every credit but the writers, which still scroll. */
    NoWriters("No Writers"),
    Full("Full");

    fun pins(kind: FooterLine.Kind): Boolean = when (this) {
        Off -> false
        NoWriters -> kind != FooterLine.Kind.WRITERS
        Full -> true
    }
}

/**
 * What the lyrics screen and [LyricsView] share: which way the active line lies when it has been
 * scrolled out of view, a way to go back to it, the credits on screen, and a credit's profile
 * waiting to be opened.
 */
class LyricsViewState {
    /** Set while the user has scrolled the active line out of view; null otherwise. */
    var activeLineDirection by mutableStateOf<ActiveLineDirection?>(null)
        internal set

    /** The credits of the lyrics on screen, null while none are shown. */
    var shownFooter by mutableStateOf<LyricsFooter?>(null)
        internal set

    /** Whether those credits sit on the right, following a song that ends on a duet line sung from there. */
    var footerAlignEnd by mutableStateOf(false)
        internal set

    /** A tapped credit whose profile the screen asks about before opening; null otherwise. */
    var profileRequest by mutableStateOf<FooterLine?>(null)
        private set

    internal var scrollToActiveRequests by mutableIntStateOf(0)
        private set

    internal fun requestProfile(line: FooterLine) {
        profileRequest = line
    }

    fun dismissProfile() {
        profileRequest = null
    }

    fun scrollToActive() {
        scrollToActiveRequests++
    }
}

enum class ActiveLineDirection { Above, Below }

/** One credit row, measured. */
internal class FooterRow(
    val line: FooterLine,
    val text: TextLayoutResult,
    val alpha: Float,
    val marginTop: Float,
    val height: Float,
    val avatarSize: Float,
    val avatarGap: Float,
    /** A row of credits and people flowing on one line, in the Refined look; [text] is unused then. */
    val flow: FooterFlow? = null,
)

/** The pieces of a [FooterRow.flow], placed from the row's left edge. */
internal class FooterFlow(val pieces: List<FlowPiece>, val taps: List<FlowTap>, val width: Float)

internal class FlowPiece(
    val text: TextLayoutResult?,
    val x: Float,
    val y: Float,
    val avatarUrl: String? = null,
    val avatarSize: Float = 0f,
    /** The top of the wrapped line it's on. */
    val lineTop: Float = 0f,
)

/** Where a person in a flow can be tapped, relative to the row. */
internal class FlowTap(val line: FooterLine, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The synced lyric size L the credits are sized from, whatever the lyrics type. */
internal fun creditBaseSp(canvasWidth: Float, density: Float, fontSizeScale: Float): Float =
    LyricsLayoutMetrics(canvasWidth, density, LyricsType.Syllable, fontSizeScale).baseFontSizeSp

/**
 * Matched to a Spicy Lyrics screenshot, relative to the lyric size L: "Written by" 0.47L,
 * the rest ~0.34L (its Mixed.css), all in the lyrics font; gaps ~0.45L above the block
 * and 0.2-0.3L between rows; the avatar ~1.4x the credit text, right after the name.
 * [CREDIT_SCALE] enlarges it all a little for a phone screen.
 */
internal fun measureFooterRows(
    lines: List<FooterLine>,
    textMeasurer: TextMeasurer,
    creditBaseSp: Float,
    density: Float,
    maxWidthPx: Float,
    firstMarginTop: Boolean = true,
    alignEnd: Boolean = false,
    refined: Boolean = false,
): List<FooterRow> {
    val lyricPx = creditBaseSp * density
    return lines.mapIndexed { index, line ->
        val (size, alpha, margin) = when {
            // Refined: one quiet size, the second line a touch smaller, tones set per span below.
            refined && line.kind == FooterLine.Kind.WRITERS -> Triple(0.34f, 1f, 0.3f)
            refined -> Triple(0.34f * 0.86f, 1f, 0.3f)
            line.kind == FooterLine.Kind.WRITERS -> Triple(0.47f, 0.7f, 0.25f)
            line.kind == FooterLine.Kind.PROVIDER || line.kind == FooterLine.Kind.TEXTS -> Triple(0.34f, 0.55f, 0.25f)
            line.kind == FooterLine.Kind.NOTE -> Triple(0.35f, 0.65f, 0.3f)
            else -> Triple(0.34f, 1f, 0.2f)
        }
        val fontSp = creditBaseSp * size * CREDIT_SCALE * (if (refined) 1.17f else 1f)
        if (refined && line.kind == FooterLine.Kind.COMMUNITY) {
            return@mapIndexed measureFlowRow(
                line, textMeasurer, fontSp, density, maxWidthPx,
                marginTop = if (index > 0) fontSp * density * 0.3f else if (firstMarginTop) fontSp * density * 0.75f else 0f,
                alignEnd = alignEnd,
            )
        }
        val text = if (refined) refinedText(line) else if (line.kind == FooterLine.Kind.CONTRIBUTOR && line.label != null && line.name != null) {
            // "Made by " dimmer, then the bold, underlined "@name" (.song-info-profile-section).
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.6f))) { append("${line.label} ") }
                withStyle(SpanStyle(
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                )) { append("@${line.name}") }
            }
        } else AnnotatedString(line.text)
        val avatar = if (line.avatarUrl != null) fontSp * density * 1.4f else 0f
        val layout = textMeasurer.measure(
            text,
            TextStyle(
                fontFamily = LyricsLayoutCalculator.spicyFontFamily,
                fontSize = fontSp.sp,
                fontWeight = when {
                    refined -> if (line.kind == FooterLine.Kind.WRITERS) FontWeight.SemiBold else FontWeight.Medium
                    line.kind == FooterLine.Kind.NOTE -> FontWeight.Bold
                    else -> FontWeight.SemiBold
                },
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
            constraints = Constraints(maxWidth = (maxWidthPx - avatar).roundToInt().coerceAtLeast(1)),
        )
        FooterRow(
            line, layout, alpha,
            marginTop = when {
                refined -> if (index > 0) fontSp * density * 0.3f else if (firstMarginTop) fontSp * density * 0.75f else 0f
                index > 0 -> lyricPx * margin
                firstMarginTop -> lyricPx * 0.45f
                else -> 0f
            },
            height = maxOf(layout.size.height.toFloat(), avatar),
            avatarSize = avatar,
            avatarGap = 2f * density,
        )
    }
}

/**
 * The credits in the Refined look: community lyrics name themselves on the second line, so the
 * "Provided by" row goes, and that note reads "Community lyrics".
 */
internal fun refinedFooterLines(footer: List<FooterLine>): List<FooterLine> {
    // Translation and romanization share one row, as the people do.
    val texts = footer.filter { it.kind == FooterLine.Kind.TEXTS }
    val lines = if (texts.size < 2) footer else footer.mapNotNull { line ->
        when {
            line === texts.first() -> FooterLine(texts.joinToString(" · ") { it.text }, FooterLine.Kind.TEXTS)
            line.kind == FooterLine.Kind.TEXTS -> null
            else -> line
        }
    }
    val people = lines.filter { it.kind == FooterLine.Kind.CONTRIBUTOR }
    if (people.isEmpty()) return lines
    val community = lines.any { it.kind == FooterLine.Kind.NOTE }
    val provider = lines.firstOrNull { it.kind == FooterLine.Kind.PROVIDER && it.text.startsWith("Provided by") }
    val lead = if (community) "Community lyrics" else provider?.text?.replace(": ", " ").orEmpty()
    val flow = FooterLine(lead, FooterLine.Kind.COMMUNITY, parts = people)
    var placed = false
    return buildList {
        for (line in lines) when {
            line.kind == FooterLine.Kind.WRITERS -> add(line)
            line.kind == FooterLine.Kind.NOTE || line.kind == FooterLine.Kind.CONTRIBUTOR || line === provider -> {
                if (!placed) { add(flow); placed = true }
            }
            else -> add(line)
        }
    }
}

/** A credit's text with its label muted and its value (or linked name) a step brighter. */
private fun refinedText(line: FooterLine): AnnotatedString {
    val muted = SpanStyle(color = Color.White.copy(alpha = 0.55f))
    val value = SpanStyle(color = Color.White.copy(alpha = 0.7f))
    val label = line.text.substringBefore(": ", "")
    return buildAnnotatedString {
        when {
            line.kind == FooterLine.Kind.CONTRIBUTOR && line.label != null && line.name != null -> {
                withStyle(muted) { append("${line.label} ") }
                withStyle(value.copy(textDecoration = TextDecoration.Underline)) { append("@${line.name}") }
            }
            line.kind == FooterLine.Kind.WRITERS && label.isNotEmpty() -> {
                withStyle(muted) { append(label) }
                append(" ")
                withStyle(value) { append(line.text.substringAfter(": ")) }
            }
            line.kind == FooterLine.Kind.PROVIDER && label.isNotEmpty() -> {
                withStyle(muted) { append("$label ${line.text.substringAfter(": ")}") }
            }
            // "Translation from Genius · Romanization from On-device", the sources a step brighter.
            line.kind == FooterLine.Kind.TEXTS -> line.text.split(" · ").forEachIndexed { i, part ->
                if (i > 0) withStyle(muted) { append(" · ") }
                withStyle(muted) { append(part.substringBefore(": ") + " ") }
                withStyle(value) { append(part.substringAfter(": ")) }
            }
            else -> withStyle(muted) { append(line.text) }
        }
    }
}

/** "Community lyrics · Made by (avatar) @name · Uploaded by (avatar) @name", wrapping between people. */
private fun measureFlowRow(
    line: FooterLine,
    textMeasurer: TextMeasurer,
    fontSp: Float,
    density: Float,
    maxWidthPx: Float,
    marginTop: Float,
    alignEnd: Boolean,
): FooterRow {
    val style = TextStyle(
        fontFamily = LyricsLayoutCalculator.spicyFontFamily,
        fontSize = fontSp.sp,
        fontWeight = FontWeight.Medium,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
    )
    val muted = Color.White.copy(alpha = 0.55f)
    val bright = Color.White.copy(alpha = 0.7f)
    fun measure(text: String, color: Color, underline: Boolean = false) = textMeasurer.measure(
        AnnotatedString(text, SpanStyle(color = color, textDecoration = if (underline) TextDecoration.Underline else null)),
        style,
    )
    val lineHeight = measure("Ag", muted).size.height.toFloat()
    val avatar = fontSp * density * 1.15f
    val gap = fontSp * density * 0.3f
    val dotGap = fontSp * density * 0.45f
    // Between wrapped lines: room for the next line's avatar under a name's underline.
    val wrapGap = fontSp * density * 0.3f
    val dot = measure("·", muted)
    val pieces = mutableListOf<FlowPiece>()
    val taps = mutableListOf<FlowTap>()
    var x = 0f
    var y = 0f
    var widest = 0f
    var first = true
    /** Where each wrapped line ends, by its top. */
    val lineEnds = mutableMapOf<Float, Float>()

    /** Places one unit whole on the current row, or on the next if it doesn't fit. */
    fun place(width: (withDot: Boolean) -> Float, add: (x: Float, y: Float, withDot: Boolean) -> Unit) {
        var withDot = !first
        if (x > 0f && x + width(withDot) > maxWidthPx) {
            x = 0f
            y += lineHeight + wrapGap
            withDot = false
        }
        val before = x
        add(x, y, withDot)
        x = before + width(withDot)
        widest = maxOf(widest, x)
        lineEnds[y] = x
        first = false
    }
    fun dotPiece(at: Float, y: Float) = FlowPiece(dot, at + dotGap, y + (lineHeight - dot.size.height) / 2f, lineTop = y)
    val dotWidth = dot.size.width + dotGap * 2f

    if (line.text.isNotEmpty()) {
        val lead = measure(line.text, muted)
        place({ lead.size.width.toFloat() }) { px, py, _ -> pieces += FlowPiece(lead, px, py, lineTop = py) }
    }
    for (person in line.parts) {
        val role = measure(person.label.orEmpty(), muted)
        val name = measure("@${person.name.orEmpty()}", bright, underline = true)
        val hasAvatar = person.avatarUrl != null
        fun body() = role.size.width + gap + (if (hasAvatar) avatar + gap else 0f) + name.size.width
        place({ withDot -> (if (withDot) dotWidth else 0f) + body() }) { px, py, withDot ->
            var cx = px
            if (withDot) { pieces += dotPiece(cx, py); cx += dotWidth }
            val left = cx
            pieces += FlowPiece(role, cx, py, lineTop = py)
            cx += role.size.width + gap
            if (hasAvatar) {
                pieces += FlowPiece(null, cx, py + (lineHeight - avatar) / 2f, person.avatarUrl, avatar, lineTop = py)
                cx += avatar + gap
            }
            pieces += FlowPiece(name, cx, py, lineTop = py)
            if (person.profileUrl != null) taps += FlowTap(person, left, py, cx + name.size.width, py + lineHeight)
        }
    }
    // On the right, each wrapped line ends at the edge, not just the longest.
    val shift = { top: Float -> if (alignEnd) widest - (lineEnds[top] ?: widest) else 0f }
    return FooterRow(
        line, measure("", muted), 1f, marginTop, y + lineHeight, 0f, 0f,
        flow = FooterFlow(
            pieces.map { FlowPiece(it.text, it.x + shift(it.lineTop), it.y, it.avatarUrl, it.avatarSize, it.lineTop) },
            taps.map { FlowTap(it.line, it.left + shift(it.top), it.top, it.right + shift(it.top), it.bottom) },
            widest,
        ),
    )
}

/** Where each row starts, the first one [startY] down. */
internal fun footerRowTops(rows: List<FooterRow>, startY: Float): List<Float> {
    var y = startY
    return rows.map { row -> (y + row.marginTop).also { y = it + row.height } }
}

/**
 * Draws the rows from [startX], or, with [alignEnd], each flush against [endX] (the name, then
 * its avatar, ending at the slot's right edge).
 */
internal fun DrawScope.drawFooterRows(
    rows: List<FooterRow>,
    tops: List<Float>,
    startX: Float,
    endX: Float,
    alignEnd: Boolean,
    avatars: Map<String, ImageBitmap>,
    dim: Float = 1f,
) {
    rows.zip(tops).forEach { (row, y) ->
        val flow = row.flow
        if (flow != null) {
            val left = if (alignEnd) endX - flow.width else startX
            for (piece in flow.pieces) {
                piece.text?.let { drawText(it, color = Color.White, alpha = dim, topLeft = Offset(left + piece.x, y + piece.y)) }
                piece.avatarUrl?.let { avatars[it] }?.let { avatar ->
                    val ax = left + piece.x
                    val ay = y + piece.y
                    clipPath(Path().apply { addOval(Rect(ax, ay, ax + piece.avatarSize, ay + piece.avatarSize)) }) {
                        drawImage(
                            avatar,
                            alpha = dim,
                            dstOffset = IntOffset(ax.roundToInt(), ay.roundToInt()),
                            dstSize = IntSize(piece.avatarSize.roundToInt(), piece.avatarSize.roundToInt()),
                        )
                    }
                }
            }
            return@forEach
        }
        val avatarWidth = if (row.line.avatarUrl != null) row.avatarGap + row.avatarSize else 0f
        val x = if (alignEnd) endX - row.text.size.width - avatarWidth else startX
        val textHeight = row.text.size.height.toFloat()
        drawText(
            textLayoutResult = row.text,
            color = Color.White,
            alpha = row.alpha * dim,
            topLeft = Offset(x, y + (row.height - textHeight) / 2f),
        )
        // The avatar follows the name, a 24px circle.
        row.line.avatarUrl?.let { avatars[it] }?.let { avatar ->
            val ax = x + row.text.size.width + row.avatarGap
            val ay = y + (row.height - row.avatarSize) / 2f
            clipPath(Path().apply { addOval(Rect(ax, ay, ax + row.avatarSize, ay + row.avatarSize)) }) {
                drawImage(
                    avatar,
                    alpha = dim,
                    dstOffset = IntOffset(ax.roundToInt(), ay.roundToInt()),
                    dstSize = IntSize(row.avatarSize.roundToInt(), row.avatarSize.roundToInt()),
                )
            }
        }
    }
}

/** The row at [y] with a profile to open, if any. */
internal fun footerProfileAt(
    rows: List<FooterRow>,
    tops: List<Float>,
    y: Float,
    x: Float = Float.NaN,
    startX: Float = 0f,
    endX: Float = 0f,
    alignEnd: Boolean = false,
): FooterLine? {
    for ((row, top) in rows.zip(tops)) {
        if (y !in top..(top + row.height)) continue
        val flow = row.flow
        if (flow == null) {
            if (row.line.profileUrl != null) return row.line
            continue
        }
        if (x.isNaN()) continue
        val left = if (alignEnd) endX - flow.width else startX
        flow.taps.firstOrNull { x in (left + it.left - 8f)..(left + it.right + 8f) && y in (top + it.top)..(top + it.bottom) }
            ?.let { return it.line }
    }
    return null
}

@Composable
internal fun rememberFooterAvatars(lines: List<FooterLine>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val urls = remember(lines) { lines.flatMap { it.parts + it }.mapNotNull(FooterLine::avatarUrl).distinct() }
    val avatars by produceState(emptyMap<String, ImageBitmap>(), urls) {
        value = urls.mapNotNull { url -> loadAvatar(context, url)?.let { url to it } }.toMap()
    }
    return avatars
}

/**
 * The pinned credits: the rows [mode] pins, from the lyrics on screen, drawn as they are after
 * the lyrics but held in place. [onHeight] reports how tall they are (0 with nothing to show).
 * Their links only take taps while [tappable].
 */
@Composable
fun PinnedLyricsFooter(
    state: LyricsViewState,
    mode: PinnedFooterMode,
    fontSizeScale: Float,
    tappable: Boolean,
    refined: Boolean = false,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val footer = state.shownFooter
    val lines = remember(footer, mode, refined) {
        footer?.lines().orEmpty().let { if (refined) refinedFooterLines(it) else it }.filter { mode.pins(it.kind) }
    }
    val textMeasurer = rememberTextMeasurer()
    val avatars = rememberFooterAvatars(lines)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current.density
        val width = constraints.maxWidth.toFloat()
        val slot = LyricsLayoutMetrics(width, density, LyricsType.Syllable, fontSizeScale).contentSlot(false, false, false)
        val baseSp = creditBaseSp(width, density, fontSizeScale)
        val alignEnd = state.footerAlignEnd
        val rows = remember(lines, baseSp, slot.widthPx, LyricsLayoutCalculator.fontKey, alignEnd, refined) {
            measureFooterRows(lines, textMeasurer, baseSp, density, slot.widthPx, firstMarginTop = false, alignEnd = alignEnd, refined = refined)
        }
        val tops = remember(rows) { footerRowTops(rows, 0f) }
        val height = rows.sumOf { (it.marginTop + it.height).toDouble() }.toFloat()
        SideEffect { onHeight(height.roundToInt()) }
        if (rows.isEmpty()) return@BoxWithConstraints
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { height.toDp() })
                .then(
                    if (!tappable) Modifier else Modifier
                        .keepsControlsHidden(tapsOnly = true) {
                            footerProfileAt(rows, tops, it.y, it.x, slot.startPx, slot.startPx + slot.widthPx, alignEnd) != null
                        }
                        .pointerInput(rows) {
                            detectTapGestures { tap ->
                                footerProfileAt(rows, tops, tap.y, tap.x, slot.startPx, slot.startPx + slot.widthPx, alignEnd)?.let(state::requestProfile)
                            }
                        },
                ),
        ) {
            drawFooterRows(rows, tops, slot.startPx, slot.startPx + slot.widthPx, alignEnd, avatars)
        }
    }
}

/** Scales the credits up from their desktop proportions, for a phone screen. */
private const val CREDIT_SCALE = 1.15f

private suspend fun loadAvatar(context: Context, url: String): ImageBitmap? = try {
    val request = ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()
    (Coil.imageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
