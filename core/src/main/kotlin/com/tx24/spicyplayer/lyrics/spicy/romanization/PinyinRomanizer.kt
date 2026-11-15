package com.tx24.spicyplayer.lyrics.spicy.romanization

/**
 * Chinese (Han → pinyin with tone marks) from the `pinyin` 4.0.0 package's dictionaries (MIT).
 * Each run of Han characters is read by word, the longest known word first, so a character with
 * several readings takes the one its word uses (行走 → xíng zǒu, 长大 → zhǎng dà); a character
 * in no known word takes its most common reading.
 *
 * `romanization/pinyin.txt` is the character dictionary, reduced to the first reading per
 * character: one line each, the character then its reading. `romanization/pinyin-phrases.txt` is
 * the word dictionary: one word per line, followed by a tab and its readings where they differ
 * from the characters' own.
 */
object PinyinRomanizer : Romanizer {
    override val script = Script.CHINESE

    private val readings: Map<Int, String>? by lazy {
        runCatching {
            buildMap {
                resource("romanization/pinyin.txt").useLines { lines ->
                    for (line in lines) {
                        if (line.isEmpty()) continue
                        val code = line.codePointAt(0)
                        put(code, line.substring(Character.charCount(code)))
                    }
                }
            }
        }.getOrNull()
    }

    /** Word -> its readings, or null when every character keeps its own. */
    private val words: Map<String, List<String>?> by lazy {
        runCatching {
            buildMap<String, List<String>?> {
                resource("romanization/pinyin-phrases.txt").useLines { lines ->
                    for (line in lines) {
                        if (line.isEmpty()) continue
                        val tab = line.indexOf('\t')
                        if (tab < 0) put(line, null) else put(line.substring(0, tab), line.substring(tab + 1).split(' '))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private const val LONGEST_WORD = 9

    private fun resource(name: String) =
        PinyinRomanizer::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8)

    override fun isAvailable(): Boolean = readings != null

    /** One reading per character of [text]: Han characters in pinyin, everything else as written. */
    fun charReadings(text: String): List<String> {
        val dict = readings ?: return text.map(Char::toString)
        val out = text.mapTo(ArrayList(text.length)) { dict[it.code] ?: it.toString() }
        var at = 0
        while (at < text.length) {
            if (dict[text[at].code] == null) {
                at++
                continue
            }
            var end = at
            while (end < text.length && dict[text[end].code] != null) end++
            // The longest known word starting here, then on from its end.
            while (at < end) {
                var length = minOf(LONGEST_WORD, end - at)
                while (length > 1 && text.substring(at, at + length) !in words) length--
                if (length > 1) words[text.substring(at, at + length)]?.forEachIndexed { i, reading -> out[at + i] = reading }
                at += length
            }
        }
        return out
    }

    /** [chars] read as [readings], Han readings spaced from each other and from the words beside them. */
    fun join(chars: String, readings: List<String>): String = buildString {
        chars.forEachIndexed { i, c ->
            if (i > 0) {
                val prev = chars[i - 1]
                if ((ScriptDetector.hasHan(c) || ScriptDetector.hasHan(prev)) && prev.isLetterOrDigit() && c.isLetterOrDigit()) append(' ')
            }
            append(readings[i])
        }
    }

    override fun romanize(text: String): String = join(text, charReadings(text))
}
