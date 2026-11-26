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
 * place of lyrics), and with word-synced lyrics `W lineStartMs word...` after each line, each word
 * `text␟startMs␟endMs␟joined` (from the line's start; joined: a syllable with no space before), for
 * the Flipper to wipe the words in as they're sung. From it: `SPICY 1` (its answer to hello) and `C play|next|prev` (a button).
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

    /** One word (or syllable) of the line being sung, for [words]. */
    data class Word(val text: String, val startMs: Long, val endMs: Long, val joined: Boolean)

    /** The most words a line can carry, and the longest message the Flipper takes. */
    const val MAX_WORDS = 32
    private const val WORDS_MAX_CHARS = 560
    private const val UNIT = '\u001f'

    /** The line's [words] with their times from [lineStartMs]; null when they won't fit a message. */
    fun words(lineStartMs: Long, words: List<Word>): String? {
        fun encode(list: List<Word>) = "W\t$lineStartMs" + list.joinToString("") { w ->
            val start = (w.startMs - lineStartMs).coerceIn(0, 65_535)
            val end = (w.endMs - lineStartMs).coerceIn(start, 65_535)
            "\t${w.text.take(27)}$UNIT$start$UNIT$end$UNIT${if (w.joined) 1 else 0}"
        } + "\n"
        if (words.isEmpty()) return null
        val first = encode(words)
        if (words.size <= MAX_WORDS && first.length <= WORDS_MAX_CHARS) return first
        // Too many syllables: whole words instead.
        val merged = mutableListOf<Word>()
        for (w in words) {
            val last = merged.lastOrNull()
            if (w.joined && last != null) merged[merged.lastIndex] = last.copy(text = last.text + w.text, endMs = w.endMs)
            else merged += w
        }
        val second = encode(merged)
        return second.takeIf { merged.size <= MAX_WORDS && it.length <= WORDS_MAX_CHARS }
    }

    /** Whether [lead] reads better by its romanization on the Flipper ([lineText]). */
    fun usesRomanization(lead: WidgetLines.Lead): Boolean =
        kept(lead.text) < 0.5f && !lead.romanized.isNullOrBlank()

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
        return if (usesRomanization(lead)) ascii(romanized!!) else ascii(own)
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
    /** The lead lines' words by start, for the word timings. */
    private var timed: Map<Long, com.tx24.spicyplayer.lyrics.TimedLine> = emptyMap()
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
            timed = snapshot.lyrics?.lines.orEmpty()
                .filter { it.role == com.tx24.spicyplayer.lyrics.spicy.models.LineRole.LEAD }
                .associateBy { it.startMs }
            lastLines = null
        }

        var words: String? = null
        val lines = when {
            snapshot.lyrics == null && !snapshot.settled -> FlipperProtocol.note("Looking for lyrics...")
            snapshot.lyrics == null || leads.isEmpty() -> FlipperProtocol.note("No lyrics for this song")
            snapshot.lyrics.lyricsType == LyricsType.Static -> FlipperProtocol.note("Unsynced lyrics")
            else -> {
                val at = WidgetLines.at(leads, snapshot.positionMs)
                if (snapshot.lyrics.lyricsType == LyricsType.Syllable && !at.current.interlude) words = wordsOf(at.current)
                FlipperProtocol.lines(
                    FlipperProtocol.lineText(at.before.lastOrNull()),
                    FlipperProtocol.lineText(at.current),
                    FlipperProtocol.lineText(at.after.firstOrNull()),
                )
            }
        }
        val linesChanged = lines != lastLines
        if (linesChanged) {
            lastLines = lines
            out += lines
            words?.let { out += it }
        }

        // The Flipper moves its progress itself; it hears the position again when playing changes,
        // the song is moved, and every few seconds anyway, which also tells it the app is still there.
        val expected = lastPositionSent + if (lastPlaying == true) nowMs - lastPlayingAt else 0L
        // The wipe follows the words, so the Flipper hears the position with each new line too.
        val jumped = kotlin.math.abs(snapshot.positionMs - expected) > DRIFT_MS
        if (snapshot.playing != lastPlaying || jumped || linesChanged || nowMs - lastPlayingAt >= PLAYING_EVERY_MS) {
            lastPlaying = snapshot.playing
            lastPlayingAt = nowMs
            lastPositionSent = snapshot.positionMs
            out += FlipperProtocol.playing(snapshot.playing, snapshot.positionMs, snapshot.durationMs)
        }
        return out
    }

    /** The words of [lead], as the Flipper writes them, with their times. */
    private fun wordsOf(lead: WidgetLines.Lead): String? {
        val line = timed[lead.startMs] ?: return null
        val romanized = FlipperProtocol.usesRomanization(lead)
        val words = line.words.mapNotNull { word ->
            val raw = if (romanized) word.romanized ?: word.text else word.text
            val text = FlipperProtocol.ascii(raw)
            if (text.isEmpty()) return@mapNotNull null
            FlipperProtocol.Word(text, word.startMs, word.endMs, joined = word.attached && !raw.first().isWhitespace())
        }
        // The first word starts a row whatever it says.
        val fixed = words.mapIndexed { i, w -> if (i == 0) w.copy(joined = false) else w }
        return FlipperProtocol.words(line.startMs, fixed)
    }

    companion object {
        const val PLAYING_EVERY_MS = 2_000L
        /** How far the Flipper's clock may drift from the song before it's told again. */
        const val DRIFT_MS = 250L
    }
}
