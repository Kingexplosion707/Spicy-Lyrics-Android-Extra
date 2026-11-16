package com.tx24.spicyplayer.network.data.providers

import java.util.Locale

/** A line of ivLyrics' answer, as the app hands it over. Syllable texts carry the space after a word. */
data class IvLine(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val syllables: List<IvSyllable>,
    val background: Boolean,
    val singer: String?,
)

data class IvSyllable(val text: String, val startMs: Long, val endMs: Long)

/** ivLyrics' answers as the documents Spicy reads: word timing as TTML, line timing as LRC. */
object IvLyricsDocument {
    /** Word-timed TTML, background vocals as `x-bg` spans in the lead line before them, singers as agents. */
    fun ttml(lines: List<IvLine>): String? {
        if (lines.none { !it.background && it.syllables.isNotEmpty() }) return null
        val agents = LinkedHashMap<String, String>()
        val out = StringBuilder()
        out.append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" ")
            .append("xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\" itunes:timing=\"Word\"><body><div>")
        var index = 0
        while (index < lines.size) {
            val lead = lines[index++]
            if (lead.background) continue
            val backing = ArrayList<IvLine>()
            while (index < lines.size && lines[index].background) backing += lines[index++]
            val end = maxOf(lead.endMs, backing.maxOfOrNull { it.endMs } ?: 0L, lead.startMs)
            out.append("<p begin=\"").append(t(lead.startMs)).append("\" end=\"").append(t(end)).append('"')
            lead.singer?.takeIf { it.isNotBlank() }?.let { singer ->
                val agent = agents.getOrPut(singer) { "v${agents.size + 1}" }
                out.append(" ttm:agent=\"").append(agent).append('"')
            }
            out.append('>')
            if (lead.syllables.isNotEmpty()) spans(out, lead.syllables, lead.text) else out.append(xml(lead.text))
            for (line in backing) {
                if (line.syllables.isEmpty()) continue
                out.append("<span ttm:role=\"x-bg\">")
                spans(out, line.syllables, line.text)
                out.append("</span>")
            }
            out.append("</p>")
        }
        return out.append("</div></body></tt>").toString()
    }

    /** Line-timed LRC of the lead lines. */
    fun lrc(lines: List<IvLine>): String = lines.filter { !it.background && it.text.isNotBlank() }.joinToString("\n") { line ->
        val total = line.startMs.coerceAtLeast(0L) / 10
        "[%02d:%02d.%02d]%s".format(Locale.ROOT, total / 6000, total / 100 % 60, total % 100, line.text)
    }

    /**
     * The syllables as spans, a space between two where the words part. ivLyrics' sources mark
     * that differently (a trailing space, a leading one, a syllable of its own, or only in the
     * line's text), so the gap is read from all of them.
     */
    private fun spans(out: StringBuilder, syllables: List<IvSyllable>, lineText: String) {
        var at = 0
        var spaced = false
        var first = true
        for (syllable in syllables) {
            val text = syllable.text.trim()
            if (text.isEmpty()) {
                if (syllable.text.isNotEmpty()) spaced = true
                continue
            }
            val found = lineText.indexOf(text, at)
            if (found >= 0) {
                if (found > at && lineText.substring(at, found).any(Char::isWhitespace)) spaced = true
                at = found + text.length
            }
            if (syllable.text.first().isWhitespace()) spaced = true
            if (!first && spaced) out.append(' ')
            out.append("<span begin=\"").append(t(syllable.startMs)).append("\" end=\"")
                .append(t(maxOf(syllable.endMs, syllable.startMs))).append("\">").append(xml(text)).append("</span>")
            first = false
            spaced = syllable.text.last().isWhitespace()
        }
    }

    private fun t(ms: Long) = "%.3fs".format(Locale.ROOT, ms.coerceAtLeast(0L) / 1000.0)
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** ivLyrics' furigana (HTML ruby markup per line) laid onto the line's words as each word's reading. */
object FuriganaReadings {
    private val RUBY = Regex("<ruby>(.*?)<rt>(.*?)</rt>(?:<rp>.*?</rp>)*</ruby>", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]+>")

    /**
     * Each of [words]' reading: the word with its kanji read out, or null where the word has no
     * reading (no kanji in it) or the markup doesn't spell the words.
     */
    fun forWords(ruby: String, words: List<String>): List<String?> {
        if (!ruby.contains("<ruby>")) return words.map { null }
        // Each base character with what it reads as: a ruby's whole reading on its first character.
        val chars = ArrayList<Pair<Char, String>>()
        var last = 0
        fun plain(text: String) = unescape(TAG.replace(text, "")).forEach { if (!it.isWhitespace()) chars += it to it.toString() }
        for (match in RUBY.findAll(ruby)) {
            plain(ruby.substring(last, match.range.first))
            val base = unescape(TAG.replace(match.groupValues[1], "")).filterNot(Char::isWhitespace)
            val reading = unescape(TAG.replace(match.groupValues[2], "")).trim()
            base.forEachIndexed { i, c -> chars += c to if (i == 0) reading else "" }
            last = match.range.last + 1
        }
        plain(ruby.substring(last))
        var at = 0
        val out = words.map { word ->
            val reading = StringBuilder()
            var changed = false
            for (c in word) {
                if (c.isWhitespace()) continue
                val (base, read) = chars.getOrNull(at++) ?: return words.map { null }
                if (base != c) return words.map { null }
                reading.append(read)
                if (read != c.toString()) changed = true
            }
            reading.toString().takeIf { changed && it.isNotEmpty() }
        }
        return if (at == chars.size) out else words.map { null }
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
}
