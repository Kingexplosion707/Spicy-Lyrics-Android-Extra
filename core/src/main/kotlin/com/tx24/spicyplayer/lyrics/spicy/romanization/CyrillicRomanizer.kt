package com.tx24.spicyplayer.lyrics.spicy.romanization

/**
 * Romanizes Cyrillic the way the words are usually written in Latin letters: a Russian table
 * (Ya tebya lyublyu), or the Ukrainian one for songs with letters only Ukrainian uses, where и
 * is y, і is i and г is h (Kyiv). The hard and soft signs aren't written.
 */
object CyrillicRomanizer : Romanizer {
    override val script = Script.CYRILLIC

    private val RUSSIAN = table(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    private val UKRAINIAN = RUSSIAN + table(
        'г' to "h", 'ґ' to "g", 'є' to "ie", 'и' to "y", 'і' to "i", 'ї' to "i",
        'й' to "i", 'ю' to "iu", 'я' to "ia",
    )

    /** What these letters read as at the start of a Ukrainian word. */
    private val UKRAINIAN_INITIAL = table('є' to "ye", 'ї' to "yi", 'й' to "y", 'ю' to "yu", 'я' to "ya")

    private fun table(vararg pairs: Pair<Char, String>): Map<Char, String> = buildMap {
        for ((lower, roman) in pairs) {
            put(lower, roman)
            val upper = lower.uppercaseChar()
            if (upper != lower) put(upper, roman.replaceFirstChar { it.uppercaseChar() })
        }
    }

    /** Whether [text] has letters only Ukrainian uses, which read differently there. */
    fun isUkrainian(text: String): Boolean = text.any { it in "ЄєІіЇїҐґ" }

    override fun romanize(text: String): String = romanize(text, isUkrainian(text))

    fun romanize(text: String, ukrainian: Boolean): String {
        val map = if (ukrainian) UKRAINIAN else RUSSIAN
        val sb = StringBuilder(text.length * 2)
        text.forEachIndexed { i, c ->
            val initial = ukrainian && (i == 0 || text[i - 1] !in map)
            sb.append((if (initial) UKRAINIAN_INITIAL[c] else null) ?: map[c] ?: c.toString())
        }
        return sb.toString()
    }
}
