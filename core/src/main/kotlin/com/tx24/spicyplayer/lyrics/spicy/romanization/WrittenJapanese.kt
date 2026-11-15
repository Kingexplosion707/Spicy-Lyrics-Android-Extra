package com.tx24.spicyplayer.lyrics.spicy.romanization

import com.atilika.kuromoji.ipadic.Tokenizer
import java.text.Normalizer

/**
 * Japanese spelled the way romanized lyric pages write it: the dictionary reads the whole line,
 * long vowels stay as written ("kyou", "koohii"), particles are spelled as said ("wa", "e",
 * "wo"), and words are spaced with their endings kept on them ("kimi no namae wo yonde ita").
 */
internal object WrittenJapanese {

    private class Token(var surface: String, var reading: String, val pos: String, val posDetail: String, val start: Int)

    private class Piece(val text: String, val newWord: Boolean, val opens: Boolean)

    /** Kana only, for when the dictionary isn't there: kanji stay as written. */
    fun kanaOnly(text: String): String = tidy(KanaRomanizer.written(widen(toKatakana(text))))

    /**
     * One romanization per syllable, read with the whole line as context. A syllable that
     * starts a new word carries a leading space, so glued syllables still read by word.
     * [attached] says, per syllable, whether it continues the word before it; syllables that
     * don't are spaced apart for the dictionary, except between two Japanese ones: many lyrics
     * put a space after every Japanese syllable, and spaced apart each kanji would be read alone.
     *
     * A っ ending a syllable keeps the consonant it doubles there (黙っ|て → damat|te); before a
     * vowel, a pause or the line's end it is a stop and unwritten (あっ → a).
     */
    fun line(tokenizer: Tokenizer, syllables: List<String>, attached: List<Boolean>? = null): List<String> {
        val text = StringBuilder()
        val owner = ArrayList<Int>()
        val startsAt = IntArray(syllables.size)
        syllables.forEachIndexed { index, raw ->
            var body = widen(raw)
            if (index > 0) {
                val before = syllables[index - 1].trimEnd().lastOrNull()
                val after = raw.trimStart().firstOrNull()
                val spaced = text.lastOrNull()?.isWhitespace() == true || raw.firstOrNull()?.isWhitespace() == true
                if (before != null && after != null && isJapanese(before) && isJapanese(after)) {
                    while (text.isNotEmpty() && text.last().isWhitespace()) {
                        text.setLength(text.length - 1)
                        owner.removeAt(owner.lastIndex)
                    }
                    body = body.trimStart()
                } else if (!spaced && attached != null && !attached[index]) {
                    text.append(' ')
                    owner += -1
                }
            }
            startsAt[index] = text.length
            text.append(body)
            repeat(body.length) { owner += index }
        }

        val line = text.toString()
        val tokens = tokens(tokenizer, line)
        val ruby = rubyReadings(line, tokens)
        val kana = List(syllables.size) { mutableListOf<Pair<String, Boolean>>() }
        val startsWord = BooleanArray(syllables.size)
        var lastReading: String? = null

        tokens.forEachIndexed { tokenIndex, token ->
            // Punctuation never starts a word.
            val newWord = tokenIndex > 0 && token.surface.any(Char::isLetterOrDigit) && !attaches(token, tokens[tokenIndex - 1])
            if (newWord) {
                val syllable = startsAt.indexOf(token.start)
                if (syllable > 0) startsWord[syllable] = true
            }
            val readings = when {
                // A 々 the dictionary didn't read repeats the kanji before it (燦々 → san san).
                token.surface.all { it == '々' } && token.reading.firstOrNull()?.let(::isKana) != true && lastReading != null ->
                    token.surface.map { lastReading!! }
                hasJapanese(token.surface) -> charReadings(token.surface, token.reading)
                else -> token.surface.map(Char::toString)
            }.mapIndexed { i, r -> ruby[token.start + i] ?: r }
            readings.lastOrNull { it.isNotEmpty() }?.let { lastReading = it }

            val bySyllable = sortedMapOf<Int, StringBuilder>()
            readings.forEachIndexed { i, r ->
                val syllable = owner.getOrNull(token.start + i) ?: -1
                if (syllable >= 0) bySyllable.getOrPut(syllable) { StringBuilder() }.append(r)
            }
            var first = true
            for ((syllable, piece) in bySyllable) {
                if (piece.isNotBlank()) kana[syllable] += piece.toString() to (first && newWord)
                first = false
            }
        }

        val pieces = kana.flatMapIndexed { syllable, list -> list.map { Triple(syllable, it.first, it.second) } }
        val romaji = List(syllables.size) { mutableListOf<Piece>() }
        var lastVowel = ""
        pieces.forEachIndexed { k, (syllable, piece, newWord) ->
            var out = when {
                piece == "ヲ" -> "wo"
                !piece.endsWith('っ') && !piece.endsWith('ッ') -> KanaRomanizer.written(piece)
                else -> {
                    // The consonant the stop doubles, from the first sound after it.
                    val next = pieces.getOrNull(k + 1)?.let { KanaRomanizer.written(it.second) }.orEmpty()
                    val doubled = when {
                        next.startsWith("ch") -> "t"
                        next.firstOrNull()?.let { it in 'a'..'z' || it in 'A'..'Z' } == true && next.first().lowercaseChar() !in "aeioun" ->
                            next.first().toString()
                        else -> ""
                    }
                    KanaRomanizer.written(piece.trimEnd('っ', 'ッ')) + doubled
                }
            }
            // A ー in a syllable of its own holds the vowel before it (セ|ー → se|e).
            val marks = out.takeWhile { it == 'ー' }.length
            if (marks > 0) out = lastVowel.repeat(marks) + out.drop(marks)
            out = tidy(out)
            out.lastOrNull { it.isLetter() }?.lowercaseChar()?.takeIf { it in "aeiou" }?.let { lastVowel = it.toString() }
            romaji[syllable] += Piece(out, newWord, piece.trim().let { it.length == 1 && it[0] in OPENS })
        }

        var gap = false
        var afterOpening = false
        return romaji.mapIndexed { index, list ->
            val kept = list.filter { it.text.isNotEmpty() }
            // Inside one syllable (a line that isn't split, say), words still get gaps.
            val joined = buildString {
                kept.forEachIndexed { i, entry ->
                    if (i > 0 && entry.newWord && !kept[i - 1].opens) append(' ')
                    append(entry.text)
                }
            }.trim()
            if (startsWord[index]) gap = true
            if (joined.isEmpty()) return@mapIndexed ""
            val spaced = gap && !afterOpening
            gap = false
            afterOpening = kept.last().opens
            if (spaced) " $joined" else joined
        }
    }

    private const val OPENS = "「『（(“‘〈《【"

    /** The particles that aren't said as written. */
    private val PARTICLES = mapOf("は" to "ワ", "へ" to "エ", "を" to "ヲ")

    private fun tokens(tokenizer: Tokenizer, text: String): List<Token> {
        val tokens = tokenizer.tokenize(text).mapTo(mutableListOf()) { t ->
            val surface = t.surface
            val reading = t.reading?.takeIf { it.isNotEmpty() && it != "*" }
            val particle = if (t.partOfSpeechLevel1 == "助詞") PARTICLES[surface] else null
            Token(
                surface = surface,
                reading = when {
                    particle != null -> particle
                    !hasJapanese(surface) -> surface
                    reading != null -> toKatakana(reading)
                    surface.all(::isKana) -> toKatakana(surface)
                    else -> surface
                },
                pos = t.partOfSpeechLevel1,
                posDetail = t.partOfSpeechLevel2,
                start = t.position,
            )
        }
        // A verb or adjective ending in っ takes the next token in (言っ|て → itte).
        var i = 0
        while (i + 1 < tokens.size) {
            val t = tokens[i]
            if ((t.pos == "動詞" || t.pos == "形容詞") && t.surface.length > 1 && (t.surface.endsWith('っ') || t.surface.endsWith('ッ'))) {
                t.surface += tokens[i + 1].surface
                t.reading += tokens[i + 1].reading
                tokens.removeAt(i + 1)
                continue
            }
            i++
        }
        return tokens
    }

    private val INFLECTING = setOf("動詞", "形容詞", "助動詞")
    private val STRETCH = Regex("^[ーｰ〜～]+$")
    private val HYPHEN = Regex("^[-‐‑－]+$")
    private val VOWEL_KANA = mapOf("ア" to 'a', "イ" to 'i', "ウ" to 'u', "エ" to 'e', "オ" to 'o')

    /** A lone vowel that holds the sound before it (うるせ|え → urusee). */
    private fun stretches(token: Token, previous: Token): Boolean {
        val vowel = VOWEL_KANA[toKatakana(token.surface)] ?: return false
        if (!hasJapanese(previous.surface)) return false
        return KanaRomanizer.written(previous.reading).lastOrNull() == vowel
    }

    /**
     * Endings that stay on the word before: verb and adjective endings (yonde, ita, ikou), て/で,
     * and suffixes. A copula after a noun stays its own word (suki da).
     */
    private fun attaches(token: Token, previous: Token): Boolean =
        (token.pos == "助動詞" && previous.pos in INFLECTING) ||
            (token.pos == "助詞" && token.posDetail == "接続助詞") ||
            token.posDetail == "接尾" ||
            // Casual ん (やってんの) is said as part of the word before.
            token.surface == "ん" || token.surface == "ン" ||
            // A ー on its own stretches the sound before it (せー → see).
            STRETCH.matches(token.surface) ||
            // The dictionary reads a hyphen as a token of its own; the word after it stays on it (doki-doki).
            HYPHEN.matches(previous.surface) ||
            stretches(token, previous)

    /**
     * A reading in sounds, the units shared out between kanji: small kana, ー, ン and ッ stay
     * with the kana before them, so a kanji never starts with one (千里眼 → セン|リ|ガン).
     */
    private fun morae(reading: String): List<String> = buildList {
        for (c in reading) {
            if (isEmpty() || c !in "ャュョァィゥェォヮーンッ") add(c.toString()) else this[lastIndex] += c
        }
    }

    /**
     * Shares a reading between characters by mora, the extra mora going to the last (名前 →
     * ナ|マエ); with fewer morae than characters the first ones get them.
     */
    private fun share(reading: String, count: Int): List<String> {
        val parts = morae(reading)
        fun cut(i: Int): Int {
            val at = i.toDouble() * parts.size / count
            return if (parts.size < count) Math.round(at).toInt() else at.toInt()
        }
        return List(count) { i -> parts.subList(cut(i), cut(i + 1)).joinToString("") }
    }

    private val RUNS = Regex("[぀-ヿ]+|[^぀-ヿ]+")

    /**
     * One kana reading per character of a word. Kana read as themselves, and each run of kanji
     * takes the reading between them (走り出す → ハシ|リ|ダ|ス); a run of several kanji shares
     * its part by mora.
     */
    fun charReadings(surface: String, reading: String): List<String> {
        val runs = RUNS.findAll(surface).map { it.value }.toList()
        val pattern = Regex(runs.joinToString("", "^", "$") { run ->
            if (isKana(run[0])) "(" + Regex.escape(toKatakana(run)) + ")" else "(.+?)"
        })
        // No clean alignment (an unusual reading): share it over the whole word.
        val match = pattern.find(reading) ?: return share(reading, surface.length)
        return runs.flatMapIndexed { i, run ->
            if (isKana(run[0])) toKatakana(run).map(Char::toString) else share(match.groupValues[i + 1], run.length)
        }
    }

    private val RUBY = Regex("[(（]([ぁ-ゖァ-ー]+)[)）]")

    /**
     * Character index -> reading for kanji read by the kana in brackets after them (傾(かぶ)けば):
     * the word's kanji and the brackets read nothing, the kana read themselves.
     */
    private fun rubyReadings(text: String, tokens: List<Token>): Map<Int, String> = buildMap {
        for (m in RUBY.findAll(text)) {
            val open = m.range.first
            val word = tokens.lastOrNull { it.start + it.surface.length == open } ?: continue
            val kanji = word.surface.takeLastWhile(::isKanji).length
            if (kanji == 0) continue
            for (i in open - kanji until open) put(i, "")
            for (i in m.range) put(i, if (i == open || i == m.range.last) "" else toKatakana(text[i]).toString())
        }
    }

    /** Half-width katakana (ｿﾝﾅ) as the dictionary knows it, one character for one. */
    private fun widen(text: String): String {
        if (text.none { it in '｡'..'ﾟ' }) return text
        return String(CharArray(text.length) { i ->
            val c = text[i]
            if (c in '｡'..'ﾟ') Normalizer.normalize(c.toString(), Normalizer.Form.NFKC).singleOrNull() ?: c else c
        })
    }

    private fun tidy(romaji: String) = romaji.replace('『', '"').replace('』', '"').replace('〜', '~')

    private fun isKana(c: Char) = c in '぀'..'ヿ'
    private fun isKanji(c: Char) = c in '一'..'鿏' || c in '豈'..'﫿' || c in '㐀'..'䶿' || c == '々'
    private fun isJapanese(c: Char) = isKana(c) || isKanji(c)
    private fun hasJapanese(s: String) = s.any(::isJapanese)
    private fun toKatakana(c: Char) = if (c.code in 0x3041..0x3096) (c.code + 0x60).toChar() else c
    private fun toKatakana(s: String) = String(CharArray(s.length) { toKatakana(s[it]) })
}
