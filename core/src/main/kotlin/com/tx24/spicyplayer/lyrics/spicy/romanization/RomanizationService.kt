package com.tx24.spicyplayer.lyrics.spicy.romanization

/**
 * Resolves the best available [Romanizer] for a script. Japanese prefers the dictionary-backed
 * [JapaneseRomanizer] (Kuromoji) when present, falling back to kana-only conversion.
 */
object Romanizers {
    fun forScript(script: Script): Romanizer? = when (script) {
        Script.JAPANESE -> JapaneseRomanizerProvider.get()
        Script.CHINESE -> PinyinRomanizer
        Script.KOREAN -> KoreanRomanizer
        Script.CYRILLIC -> CyrillicRomanizer
        Script.GREEK -> GreekRomanizer
        Script.LATIN -> null
    }
}

/**
 * On-device romanization: the
 * scripts present are detected across the whole song first (so a kanji-only syllable in a
 * Japanese song is read as Japanese, never pinyin), then each line is run through every
 * present-script romanizer whose characters it contains, in priority order. Japanese, Chinese
 * and Korean are read with the whole line as context, since syllable splits cut words apart.
 *
 * A romanization starting with a space starts a word of its own: the original's syllables are
 * often glued (Chinese and Japanese are written without spaces), while the romaji isn't.
 */
object RomanizationService {

    /**
     * [lines] of syllable texts in, the same shape out: each romanization, or null when nothing
     * changed. [attached] says, per syllable, whether it continues the word before it; without
     * it the syllables are read as they are written. [writtenJapanese] spells Japanese as lyric
     * pages write it (kyou, endings kept on their word) and capitalizes a romanized line's first
     * word; off, Japanese is spelled as said with macrons (kyō), a word per dictionary token.
     */
    fun romanize(
        lines: List<List<String>>,
        attached: List<List<Boolean>>? = null,
        writtenJapanese: Boolean = true,
    ): List<List<String?>> {
        val song = lines.joinToString("\n") { it.joinToString("") }
        val songScripts = ScriptDetector.detect(song)
        if (songScripts.isEmpty()) return lines.map { line -> line.map { null } }
        val japanese = Romanizers.forScript(Script.JAPANESE) as? JapaneseRomanizer
        val ukrainian = CyrillicRomanizer.isUkrainian(song)
        return lines.mapIndexed { l, line ->
            val glued = attached?.getOrNull(l)?.takeIf { it.size == line.size }
            var texts = line
            // The spelling as said adds no capitals, so its lines stay as they were.
            var capitalize = true
            for (script in songScripts) {
                if (texts.none { ScriptDetector.contains(script, it) }) continue
                texts = when (script) {
                    Script.JAPANESE -> when {
                        writtenJapanese && japanese != null -> japanese.romanizeLineWritten(texts, glued)
                        writtenJapanese -> texts.map { if (ScriptDetector.contains(script, it)) WrittenJapanese.kanaOnly(it) else it }
                        else -> {
                            capitalize = false
                            val romanizer = Romanizers.forScript(script)
                            (japanese?.romanizeLine(texts) ?: texts).map {
                                if (romanizer != null && ScriptDetector.contains(script, it)) romanizer.romanize(it) else it
                            }
                        }
                    }
                    Script.CHINESE -> {
                        if (!PinyinRomanizer.isAvailable()) continue
                        val original = texts
                        val readings = byCharacter(original, glued, PinyinRomanizer::charReadings)
                        original.mapIndexed { i, text ->
                            val romanized = if (ScriptDetector.contains(script, text)) PinyinRomanizer.join(text, readings[i]) else text
                            // Pinyin reads by character, so a Han syllable is a word of its own.
                            val before = original.getOrNull(i - 1)?.trimEnd()?.lastOrNull()
                            val first = text.trimStart().firstOrNull()
                            val apart = before != null && first != null && text.any(Char::isLetterOrDigit) &&
                                (ScriptDetector.hasHan(before) || ScriptDetector.hasHan(first))
                            if (apart && !romanized.startsWith(' ')) " $romanized" else romanized
                        }
                    }
                    Script.KOREAN -> {
                        val readings = byCharacter(texts, glued, KoreanRomanizer::charReadings)
                        texts.mapIndexed { i, text -> if (ScriptDetector.contains(script, text)) readings[i].joinToString("") else text }
                    }
                    Script.CYRILLIC -> texts.map { if (ScriptDetector.contains(script, it)) CyrillicRomanizer.romanize(it, ukrainian) else it }
                    Script.GREEK -> texts.map { if (ScriptDetector.contains(script, it)) GreekRomanizer.romanize(it) else it }
                    Script.LATIN -> texts
                }
            }
            if (capitalize) texts = capitalized(line, texts)
            line.indices.map { i -> texts[i].takeIf { it.trim() != line[i].trim() && it.isNotBlank() } }
        }
    }

    /**
     * Reads a whole line a character at a time (Chinese words, Korean sound changes across a
     * syllable seam), then hands each syllable the readings of its own characters. Syllables
     * that don't continue a word are spaced apart, so nothing is read across two words.
     */
    private fun byCharacter(texts: List<String>, glued: List<Boolean>?, read: (String) -> List<String>): List<List<String>> {
        val text = StringBuilder()
        val starts = IntArray(texts.size)
        texts.forEachIndexed { i, t ->
            if (i > 0 && glued != null && !glued[i] && text.lastOrNull()?.isWhitespace() != true && t.firstOrNull()?.isWhitespace() != true) text.append(' ')
            starts[i] = text.length
            text.append(t)
        }
        val readings = read(text.toString())
        return texts.mapIndexed { i, t -> readings.subList(starts[i], starts[i] + t.length) }
    }

    /** [texts] with a capital on the line's first word when that word was romanized; Latin words stay as written. */
    private fun capitalized(original: List<String>, texts: List<String>): List<String> {
        val first = original.indexOfFirst { it.any(Char::isLetter) }
        if (first < 0 || texts[first] == original[first]) return texts
        val letter = original[first].first(Char::isLetter)
        if (Character.UnicodeScript.of(letter.code) == Character.UnicodeScript.LATIN) return texts
        val at = texts[first].indexOfFirst(Char::isLetter)
        if (at < 0) return texts
        return texts.toMutableList().also {
            it[first] = texts[first].replaceRange(at, at + 1, texts[first][at].uppercaseChar().toString())
        }
    }
}
