package com.tx24.spicyplayer.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.WordTiming
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.lyrics.spicy.canvas.drawGlow
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.playback.LiveLyrics

/**
 * The status bar style: the line being sung, on one line sized to the status bar, filling word
 * by word as it's sung, from dim to white. Lines timed only as a whole are laid out word by word
 * ([WordTiming]). A line too long for the room slides along so the word being sung stays in
 * view. Between lines it shows ♪.
 */
@Composable
internal fun StatusBarLyrics(
    live: LiveLyrics,
    visible: () -> Boolean,
    modifier: Modifier,
    color: Color = Color.White,
    glow: Boolean = true,
    glowStrength: Float = 1f,
) {
    val lyrics = live.lyrics
    val lines = remember(lyrics) { lyrics?.let(::leadLines).orEmpty() }
    // The clock is read while drawing; only a change of line recomposes.
    val nowState = remember { androidx.compose.runtime.mutableLongStateOf(live.positionMs()) }
    val running = live.playing
    LaunchedEffect(running, lines) {
        nowState.longValue = live.positionMs()
        while (running) withFrameMillis { if (visible()) nowState.longValue = live.positionMs() }
    }
    val index by remember(lines) { androidx.compose.runtime.derivedStateOf { currentLine(lines, nowState.longValue) } }
    val line = lines.getOrNull(index)
    val text = remember(line) { line?.let(::lineText) ?: LineText(INSTRUMENTAL, emptyList()) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val font = LyricsLayoutCalculator.fontOverride
    Canvas(modifier) {
        val fontSize = with(density) { (size.height * TEXT_HEIGHT_SHARE).toSp() }
        val layout = measurer.measure(
            text.text,
            TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, fontFamily = font),
            softWrap = false,
            maxLines = 1,
        )
        val fillX = fillPosition(layout, text, nowState.longValue)
        val overflow = (layout.size.width - size.width).coerceAtLeast(0f)
        val shift = if (overflow > 0f) (fillX - size.width * FOLLOW_AT).coerceIn(0f, overflow) else 0f
        val top = (size.height - layout.size.height) / 2f
        translate(left = -shift, top = top) {
            drawText(layout, color = color.copy(alpha = DIM_ALPHA))
            if (fillX > 0f) {
                clipRect(right = fillX) {
                    // The glow as its own soft pass under the sung words.
                    if (glow) {
                        drawGlow(
                            layout, Offset.Zero,
                            Shadow(color.copy(alpha = GLOW_ALPHA), Offset.Zero, layout.size.height * GLOW_BLUR_SHARE),
                            glowStrength,
                        )
                    }
                    drawText(layout, color = color, topLeft = Offset.Zero, shadow = Shadow.None)
                }
            }
        }
    }
}

/** A line's text and where each word sits in it, with its times. */
private class LineText(val text: String, val words: List<Span>)
private class Span(val start: Int, val end: Int, val startMs: Long, val endMs: Long)

private fun leadLines(lyrics: LyricsState.Ready): List<TimedLine> {
    if (lyrics.lyricsType == LyricsType.Static) return emptyList()
    val sung = lyrics.lines.filter { it.role == LineRole.LEAD && !it.untimed && it.words.any { w -> w.text.isNotBlank() } }
    return WordTiming.spread(sung, lyrics.lyricsType).sortedBy { it.startMs }
}

/** The line being sung at [now], or -1 before the first or in a long gap. */
private fun currentLine(lines: List<TimedLine>, now: Long): Int {
    val i = lines.indexOfLast { it.startMs <= now }
    if (i < 0) return -1
    val next = lines.getOrNull(i + 1)
    val end = lines[i].words.maxOfOrNull { it.endMs } ?: lines[i].endMs
    return if (now > end + GAP_GRACE_MS && (next == null || now < next.startMs)) -1 else i
}

private fun lineText(line: TimedLine): LineText {
    val text = StringBuilder()
    val spans = ArrayList<Span>()
    for (word in line.words) {
        if (word.text.isBlank()) continue
        if (text.isNotEmpty() && !word.attached) text.append(' ')
        val start = text.length
        text.append(word.text.trim())
        spans += Span(start, text.length, word.startMs, word.endMs)
    }
    return LineText(text.toString(), spans)
}

/** How far along the line the fill has reached at [now], in px. */
private fun fillPosition(layout: TextLayoutResult, text: LineText, now: Long): Float {
    if (text.words.isEmpty()) return 0f
    var x = 0f
    for (span in text.words) {
        if (now < span.startMs) break
        val from = layout.getHorizontalPosition(span.start, true)
        val to = layout.getHorizontalPosition(span.end, true)
        val progress = ((now - span.startMs).toFloat() / (span.endMs - span.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
        x = from + (to - from) * progress
    }
    return x
}

/** The glow's strength and spread against the text's height. */
private const val GLOW_ALPHA = 0.85f
private const val GLOW_BLUR_SHARE = 0.45f
private const val INSTRUMENTAL = "♪"
/** The text's height against the status bar's. */
private const val TEXT_HEIGHT_SHARE = 0.46f
/** Words not yet sung. */
private const val DIM_ALPHA = 0.42f
/** A long line slides so the word being sung stays this far across. */
private const val FOLLOW_AT = 0.6f
/** A line counts as sung this long after its last word, so a held note doesn't flicker to ♪. */
private const val GAP_GRACE_MS = 1_500L
