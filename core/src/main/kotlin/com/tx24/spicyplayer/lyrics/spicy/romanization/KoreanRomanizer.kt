package com.tx24.spicyplayer.lyrics.spicy.romanization

/**
 * Korean by sound, Revised Romanization as it is said rather than letter by letter (사랑해 →
 * saranghae, 좋아 → joa, 신라 → silla). Each block splits into jamo, and a final consonant reads
 * by what follows it, longest rule first: final + next initial + next vowel, then final + next
 * initial, then alone. When the final's rule already covers the next initial, that initial isn't
 * read again.
 */
object KoreanRomanizer : Romanizer {
    override val script = Script.KOREAN

    override fun romanize(text: String): String = charReadings(text).joinToString("")

    /**
     * One romanization per character of [text]: Hangul blocks read by sound with the blocks
     * around them as context, everything else as written. Where a final and the next initial
     * are read together, the next block's consonant starts that block, so each block reads as it
     * is sung even when the two are sung in different syllables (같|이 → ga|chi, 먹|는 →
     * meong|neun, 없|어 → eop|seo).
     */
    fun charReadings(text: String): List<String> {
        val blocks = text.map(::segment)
        var carried: String? = null
        return blocks.mapIndexed { index, block ->
            if (block.length < 2) {
                carried = null
                return@mapIndexed block
            }
            val next = blocks.getOrNull(index + 1).orEmpty()
            val piece = StringBuilder(carried ?: CHO[block.substring(0, 1)] ?: block.substring(0, 1))
            carried = null
            piece.append(JUNG[block.substring(1, 2)] ?: block.substring(1, 2))
            if (block.length == 3) {
                val last = block.substring(2, 3)
                val joined = if (next.length >= 2) JONG[last + next.substring(0, 2)] ?: JONG[last + next.substring(0, 1)] else null
                if (joined != null) {
                    // The consonant a joined final hands to the next block, as it's sung there.
                    val onset = ONSET.find(joined)?.value.orEmpty()
                    carried = onset
                    piece.append(joined.dropLast(onset.length))
                } else {
                    piece.append(JONG[last] ?: last)
                }
            }
            piece.toString()
        }
    }

    private val ONSET = Regex("(ch|ss|kk|tt|pp|jj|[a-z])$")

    /** A Hangul block as its initial, vowel and (if any) final jamo; anything else as itself. */
    private fun segment(c: Char): String {
        val idx = c.code - 0xAC00
        if (idx !in 0..11171) return c.toString()
        val sb = StringBuilder(3)
        sb.append((idx / 588 + 0x1100).toChar())
        sb.append((idx % 588 / 28 + 0x1161).toChar())
        if (idx % 28 != 0) sb.append((idx % 28 + 0x11A7).toChar())
        return sb.toString()
    }

    private val CHO = mapOf(
        "ᄀ" to "g", "ᄁ" to "kk", "ᄂ" to "n", "ᄃ" to "d", "ᄄ" to "tt", "ᄅ" to "r",
        "ᄆ" to "m", "ᄇ" to "b", "ᄈ" to "pp", "ᄉ" to "s", "ᄊ" to "ss", "ᄋ" to "",
        "ᄌ" to "j", "ᄍ" to "jj", "ᄎ" to "ch", "ᄏ" to "k", "ᄐ" to "t", "ᄑ" to "p",
        "ᄒ" to "h",
    )

    private val JUNG = mapOf(
        "ᅡ" to "a", "ᅢ" to "ae", "ᅣ" to "ya", "ᅤ" to "yae", "ᅥ" to "eo", "ᅦ" to "e",
        "ᅧ" to "yeo", "ᅨ" to "ye", "ᅩ" to "o", "ᅪ" to "wa", "ᅫ" to "wae", "ᅬ" to "oe",
        "ᅭ" to "yo", "ᅮ" to "u", "ᅯ" to "wo", "ᅰ" to "we", "ᅱ" to "wi", "ᅲ" to "yu",
        "ᅳ" to "eu", "ᅴ" to "ui", "ᅵ" to "i",
    )

    // Finals, alone and before the next block's initial (and vowel). ㅇ is the silent initial, so
    // a final before it is one before a vowel; a final ㅎ there is silent (좋아 → joa), and before
    // ㄱ, ㄷ or ㅈ it aspirates them (좋다 → jota). ㄷ and ㅌ before 이 soften to j and ch (같이 → gachi).
    private val JONG = mapOf(
        "ᆨ" to "k", "ᆨᄋ" to "g", "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh",
        "ᆩ" to "kk", "ᆩᄋ" to "kg", "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh",
        "ᆪ" to "k", "ᆪᄋ" to "ks", "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch",
        "ᆫ" to "n", "ᆫᄅ" to "ll", "ᆬ" to "n", "ᆬᄋ" to "nj", "ᆬᄂ" to "nn", "ᆬᄅ" to "nn",
        "ᆬᄆ" to "nm", "ᆬᄒ" to "nch", "ᆭ" to "n", "ᆭᄋ" to "n", "ᆭᄅ" to "nn", "ᆭᄀ" to "nk",
        "ᆭᄃ" to "nt", "ᆭᄌ" to "nch", "ᆮ" to "t", "ᆮᄋ" to "d", "ᆮᄂ" to "nn", "ᆮᄅ" to "nn",
        "ᆮᄆ" to "nm", "ᆮᄒ" to "th", "ᆯ" to "l", "ᆯᄋ" to "r", "ᆯᄂ" to "ll", "ᆰ" to "k",
        "ᆰᄋ" to "lg", "ᆰᄂ" to "ngn", "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh", "ᆱ" to "m",
        "ᆱᄋ" to "lm", "ᆱᄂ" to "mn", "ᆱᄅ" to "mn", "ᆱᄆ" to "mm", "ᆱᄒ" to "lmh", "ᆲ" to "p",
        "ᆲᄋ" to "lb", "ᆲᄂ" to "mn", "ᆲᄅ" to "mn", "ᆲᄆ" to "mm", "ᆲᄒ" to "lph", "ᆳ" to "t",
        "ᆳᄋ" to "ls", "ᆳᄂ" to "nn", "ᆳᄅ" to "nn", "ᆳᄆ" to "nm", "ᆳᄒ" to "lsh", "ᆴ" to "t",
        "ᆴᄋ" to "lt", "ᆴᄂ" to "nn", "ᆴᄅ" to "nn", "ᆴᄆ" to "nm", "ᆴᄒ" to "lth", "ᆵ" to "p",
        "ᆵᄋ" to "lp", "ᆵᄂ" to "mn", "ᆵᄅ" to "mn", "ᆵᄆ" to "mm", "ᆵᄒ" to "lph", "ᆶ" to "l",
        "ᆶᄋ" to "r", "ᆶᄂ" to "ll", "ᆶᄅ" to "ll", "ᆶᄆ" to "lm", "ᆶᄒ" to "lh", "ᆶᄀ" to "lk",
        "ᆶᄃ" to "lt", "ᆶᄌ" to "lch", "ᆷ" to "m", "ᆷᄅ" to "mn", "ᆸ" to "p", "ᆸᄋ" to "b",
        "ᆸᄂ" to "mn", "ᆸᄅ" to "mn", "ᆸᄆ" to "mm", "ᆸᄒ" to "ph", "ᆹ" to "p", "ᆹᄋ" to "ps",
        "ᆹᄂ" to "mn", "ᆹᄅ" to "mn", "ᆹᄆ" to "mm", "ᆹᄒ" to "psh", "ᆺ" to "t", "ᆺᄋ" to "s",
        "ᆺᄂ" to "nn", "ᆺᄅ" to "nn", "ᆺᄆ" to "nm", "ᆺᄒ" to "sh", "ᆻ" to "t", "ᆻᄋ" to "ss",
        "ᆻᄂ" to "nn", "ᆻᄅ" to "nn", "ᆻᄆ" to "nm", "ᆻᄒ" to "th", "ᆼ" to "ng", "ᆽ" to "t",
        "ᆽᄋ" to "j", "ᆽᄂ" to "nn", "ᆽᄅ" to "nn", "ᆽᄆ" to "nm", "ᆽᄒ" to "ch", "ᆾ" to "t",
        "ᆾᄋ" to "ch", "ᆾᄂ" to "nn", "ᆾᄅ" to "nn", "ᆾᄆ" to "nm", "ᆾᄒ" to "ch", "ᆿ" to "k",
        "ᆿᄋ" to "k", "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh", "ᇀ" to "t",
        "ᇀᄋ" to "t", "ᇀᄂ" to "nn", "ᇀᄅ" to "nn", "ᇀᄆ" to "nm", "ᇀᄒ" to "th", "ᇁ" to "p",
        "ᇁᄋ" to "p", "ᇁᄂ" to "mn", "ᇁᄅ" to "mn", "ᇁᄆ" to "mm", "ᇁᄒ" to "ph", "ᇂ" to "t",
        "ᇂᄋ" to "", "ᇂᄀ" to "k", "ᇂᄃ" to "t", "ᇂᄌ" to "ch", "ᇂᄂ" to "nn", "ᇂᄅ" to "nn",
        "ᇂᄆ" to "mm", "ᇂᄒ" to "t", "\u11AE\u110B\u1175" to "j", "\u11C0\u110B\u1175" to "ch", "\u11AE\u1112\u1175" to "ch", "\u11AE\u1112\u1167" to "ch",
        "\u11B4\u110B\u1175" to "lch",
    )
}
