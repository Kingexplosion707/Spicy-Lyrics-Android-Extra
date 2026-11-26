package com.tx24.spicyplayer.flipper

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.widget.WidgetLines
import java.text.Normalizer

/**
 * What goes to and comes from the Spicy Lyrics app on a Flipper Zero: text, one message per line,
 * fields split by tabs.
 *
 * To the Flipper: `?` (hello), `S title artist` (the song), `L before line after` (the lines
 * around now), `P playing positionMs durationMs` (about once a second) and `N text` (a note in
 * place of lyrics). From it: `SPICY 1` (its answer to hello) and `C play|next|prev` (a button).
 *
 * The Flipper's fonts are ASCII, so text is folded to it ([ascii]); a line that would lose most of
 * itself goes by its romanization instead.
 *
 * Plain Kotlin, no Android.
 */
internal object FlipperProtocol {
    const val HELLO = "?\n"
    const val ANSWER = "SPICY"
    /** The longest text a field carries; the Flipper cuts what it can't show anyway. */
    private const val FIELD_MAX = 150

    enum class Command { PlayPause, Next, Previous }

    fun isAnswer(line: String) = line.trim().startsWith(ANSWER)

    fun command(line: String): Command? {
        val parts = line.trim().split('\t')
        if (parts.size < 2 || parts[0] != "C") return null
        return when (parts[1]) {
            "play" -> Command.PlayPause
            "next" -> Command.Next
            "prev" -> Command.Previous
            else -> null
        }
    }

    fun song(title: String, artist: String) = message("S", ascii(title), ascii(artist))
    fun lines(before: String, line: String, after: String) = message("L", before, line, after)
    fun playing(playing: Boolean, positionMs: Long, durationMs: Long) =
        "P\t${if (playing) 1 else 0}\t${positionMs.coerceAtLeast(0)}\t${durationMs.coerceAtLeast(0)}\n"
    fun note(text: String) = message("N", ascii(text))

    private fun message(type: String, vararg fields: String) =
        (listOf(type) + fields.map { it.take(FIELD_MAX) }).joinToString("\t", postfix = "\n")

    private val MARKS = Regex("\\p{M}+")
    private val SPACES = Regex("\\s+")
    private val REPLACEMENTS = mapOf(
        '‘' to "'", '’' to "'", '‚' to "'", '′' to "'", '“' to "\"", '”' to "\"", '„' to "\"",
        '–' to "-", '—' to "-", '―' to "-", '…' to "...", '•' to "*", '·' to "*", '♪' to "*", '♫' to "*",
        'ß' to "ss", 'æ' to "ae", 'Æ' to "AE", 'ø' to "o", 'Ø' to "O", 'œ' to "oe", 'Œ' to "OE",
        'ł' to "l", 'Ł' to "L", 'đ' to "d", 'Đ' to "D", 'þ' to "th", 'Þ' to "Th", 'ı' to "i",
        '¡' to "!", '¿' to "?", '«' to "\"", '»' to "\"",
    )

    /** [text] in printable ASCII: accents dropped, quotes and dashes plain, anything else left out. */
    fun ascii(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "")
        val out = StringBuilder(folded.length)
        for (c in folded) {
            when {
                c == '\t' || c == '\n' || c == '\r' -> out.append(' ')
                c.code in 0x20..0x7E -> out.append(c)
                else -> REPLACEMENTS[c]?.let(out::append)
            }
        }
        return out.toString().replace(SPACES, " ").trim()
    }

    /** How much of [text]'s letters and digits survive [ascii]. */
    private fun kept(text: String): Float {
        val letters = text.count { it.isLetterOrDigit() }
        if (letters == 0) return 1f
        return ascii(text).count { it.isLetterOrDigit() }.toFloat() / letters
    }

    /** A line as the Flipper writes it: its romanization when the line itself won't fold to ASCII. */
    fun lineText(lead: WidgetLines.Lead?): String {
        if (lead == null) return ""
        if (lead.interlude) return "* * *"
        val own = lead.text
        val romanized = lead.romanized
        return if (kept(own) < 0.5f && !romanized.isNullOrBlank()) ascii(romanized) else ascii(own)
    }
}

/**
 * Turns the song as it plays into the messages for the Flipper, sending each only when it
 * changes, and the position when it moves unexpectedly and every few seconds ([PLAYING_EVERY_MS]).
 */
internal class FlipperFeed {
    /** What the app knows now. [positionMs] is the lyric position, as the lyrics screen reads it. */
    data class Snapshot(
        val title: String?,
        val artist: String,
        val durationMs: Long,
        val playing: Boolean,
        val positionMs: Long,
        val lyrics: LyricsState.Ready?,
        /** The lookup is done: null [lyrics] means there are none. */
        val settled: Boolean,
    )

    private var songKey: String? = null
    private var lyricsFor: LyricsState.Ready? = null
    private var leads: List<WidgetLines.Lead> = emptyList()
    private var lastLines: String? = null
    private var lastPlaying: Boolean? = null
    private var lastPlayingAt = 0L
    private var lastPositionSent = 0L

    /** Sends everything again on the next [messages], for a Flipper that just connected. */
    fun reset() {
        songKey = null
        lastLines = null
        lastPlaying = null
    }

    /** The messages to send now, given [snapshot] at [nowMs] (a clock). */
    fun messages(snapshot: Snapshot, nowMs: Long): List<String> {
        val out = mutableListOf<String>()
        val title = snapshot.title
        if (title == null) {
            if (songKey != "") {
                songKey = ""
                out += FlipperProtocol.song("Nothing playing", "")
                out += FlipperProtocol.note("Play something")
                lastLines = null
            }
            return out
        }
        val key = "$title\u0000${snapshot.artist}"
        if (key != songKey) {
            songKey = key
            lastLines = null
            out += FlipperProtocol.song(title, snapshot.artist)
        }
        if (snapshot.lyrics !== lyricsFor) {
            lyricsFor = snapshot.lyrics
            leads = snapshot.lyrics?.let { WidgetLines.leads(it.lines) }.orEmpty()
            lastLines = null
        }

        val lines = when {
            snapshot.lyrics == null && !snapshot.settled -> FlipperProtocol.note("Looking for lyrics...")
            snapshot.lyrics == null || leads.isEmpty() -> FlipperProtocol.note("No lyrics for this song")
            snapshot.lyrics.lyricsType == LyricsType.Static -> FlipperProtocol.note("Unsynced lyrics")
            else -> {
                val at = WidgetLines.at(leads, snapshot.positionMs)
                FlipperProtocol.lines(
                    FlipperProtocol.lineText(at.before.lastOrNull()),
                    FlipperProtocol.lineText(at.current),
                    FlipperProtocol.lineText(at.after.firstOrNull()),
                )
            }
        }
        if (lines != lastLines) {
            lastLines = lines
            out += lines
        }

        // The Flipper moves its progress itself; it hears the position again when playing changes,
        // the song is moved, and every few seconds anyway, which also tells it the app is still there.
        val expected = lastPositionSent + if (lastPlaying == true) nowMs - lastPlayingAt else 0L
        val jumped = kotlin.math.abs(snapshot.positionMs - expected) > 1_500L
        if (snapshot.playing != lastPlaying || jumped || nowMs - lastPlayingAt >= PLAYING_EVERY_MS) {
            lastPlaying = snapshot.playing
            lastPlayingAt = nowMs
            lastPositionSent = snapshot.positionMs
            out += FlipperProtocol.playing(snapshot.playing, snapshot.positionMs, snapshot.durationMs)
        }
        return out
    }

    companion object {
        const val PLAYING_EVERY_MS = 3_000L
    }
}
