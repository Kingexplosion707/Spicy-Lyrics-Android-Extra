package com.tx24.spicyplayer.lyrics

import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType

/** The lyrics on screen as text: plain, or as LRC with the timing they have. */
object LyricsExport {
    /** One line per sung line, background vocals in brackets; instrumental breaks leave a blank line. */
    fun plainText(lines: List<TimedLine>): String = buildString {
        for (line in lines) {
            if (line.role == LineRole.INTERLUDE) {
                if (isNotEmpty() && !endsWith("\n\n")) append('\n')
                continue
            }
            val text = text(line)
            if (text.isEmpty()) continue
            append(if (line.role == LineRole.BACKGROUND) "($text)" else text).append('\n')
        }
    }.trim()

    /**
     * LRC: a tag per line, and inside word-timed lines a tag before each word (the enhanced
     * form, which reads back word by word). Lyrics without timing come out as plain text.
     */
    fun lrc(lines: List<TimedLine>, type: LyricsType, title: String, artist: String, album: String, durationMs: Long): String {
        if (type == LyricsType.Static) return plainText(lines)
        return buildString {
            if (title.isNotBlank()) append("[ti:").append(title).append("]\n")
            if (artist.isNotBlank()) append("[ar:").append(artist).append("]\n")
            if (album.isNotBlank()) append("[al:").append(album).append("]\n")
            if (durationMs > 0) append("[length:").append(durationMs / 60_000).append(':').append("%02d".format(durationMs / 1000 % 60)).append("]\n")
            for (line in lines) {
                if (line.role == LineRole.INTERLUDE || line.untimed) continue
                val text = text(line)
                if (text.isEmpty()) continue
                append('[').append(stamp(line.startMs)).append(']')
                val wordTimed = type == LyricsType.Syllable && !line.lineTimed && line.words.size > 1
                if (!wordTimed) {
                    append(if (line.role == LineRole.BACKGROUND) "($text)" else text)
                } else {
                    for ((index, word) in line.words.withIndex()) {
                        if (index > 0 && !word.attached) append(' ')
                        append('<').append(stamp(word.startMs)).append('>').append(word.text.trim())
                    }
                    append('<').append(stamp(line.words.last().endMs)).append('>')
                }
                append('\n')
            }
        }.trimEnd() + "\n"
    }

    /** "Artist - Title.lrc", without the characters a file name can't hold. */
    fun fileName(title: String, artist: String): String {
        val name = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "lyrics" }
        return name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120) + ".lrc"
    }

    private fun text(line: TimedLine): String =
        line.words.joinToString("") { word -> if (word.attached) word.text else " " + word.text }.trim().replace(Regex("\\s+"), " ")

    /** `mm:ss.xx`, as LRC writes a time. */
    internal fun stamp(ms: Long): String {
        val at = ms.coerceAtLeast(0)
        return "%02d:%02d.%02d".format(at / 60_000, at / 1000 % 60, at % 1000 / 10)
    }
}
