package com.tx24.spicyplayer.lyrics

/**
 * A background vocal is already drawn as one, so its text carries no parentheses of its own.
 * Lyrics files still put them in a background line's romanization and translation; here the pair
 * wrapping the whole text, or a lone opening or closing one at its edge, is dropped. Parentheses
 * inside the text stay.
 */
object WrappingParens {

    /** Which edges of [text] carry a wrapping parenthesis: (leading, trailing). */
    private fun edges(text: String): Pair<Boolean, Boolean> {
        val s = text.trim()
        val open = s.startsWith("(")
        val close = s.endsWith(")")
        if (open && close) {
            // "(a) b (c)" isn't wrapped: the first one closes before the end.
            var depth = 0
            for (i in 0 until s.length - 1) {
                if (s[i] == '(') depth++
                else if (s[i] == ')' && --depth == 0) return false to false
            }
            return true to true
        }
        return (open && ')' !in s) to (close && '(' !in s)
    }

    private val LEAD = Regex("^(\\s*)\\(\\s*")
    private val TRAIL = Regex("\\s*\\)(\\s*)$")
    private fun dropLead(text: String) = LEAD.replaceFirst(text, "$1")
    private fun dropTrail(text: String) = TRAIL.replaceFirst(text, "$1")

    fun strip(text: String): String {
        val (lead, trail) = edges(text)
        var out = text
        if (lead) out = dropLead(out)
        if (trail) out = dropTrail(out)
        return out.trim()
    }

    /**
     * A background line's words with the wrapping parentheses taken off their romanization, which
     * starts on the first word and ends on the last. Words with no romanization keep their text.
     */
    fun stripRomanization(words: List<TimedWord>): List<TimedWord> {
        if (words.none { it.romanized != null }) return words
        val texts = words.map { it.romanized ?: it.text }
        val (lead, trail) = edges(texts.joinToString(""))
        if (!lead && !trail) return words
        val first = texts.indexOfFirst(String::isNotBlank)
        val last = texts.indexOfLast(String::isNotBlank)
        return words.mapIndexed { i, word ->
            var roman = word.romanized ?: return@mapIndexed word
            if (lead && i == first) roman = dropLead(roman)
            if (trail && i == last) roman = dropTrail(roman)
            if (roman == word.romanized) word else word.copy(romanized = roman)
        }
    }
}
