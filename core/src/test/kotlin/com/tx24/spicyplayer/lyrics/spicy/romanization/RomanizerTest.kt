package com.tx24.spicyplayer.lyrics.spicy.romanization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizerTest {
    @Test fun koreanRevisedRomanization() {
        assertEquals("annyeonghaseyo", KoreanRomanizer.romanize("안녕하세요"))
    }

    @Test fun cyrillicAndGreek() {
        assertEquals("privet", CyrillicRomanizer.romanize("привет"))
        assertEquals("kalimera", GreekRomanizer.romanize("καλημέρα"))
    }

    @Test fun kanaHandlesYouonAndSokuon() {
        assertEquals("kya", KanaRomanizer.romanize("きゃ"))
        assertEquals("kitto", KanaRomanizer.romanize("きっと"))
    }

    @Test fun kanjiOnlyWordInJapaneseSongIsNotPinyin() {
        // "心" alone reads as Chinese; the song's kana makes it Japanese.
        val out = RomanizationService.romanize(listOf(listOf("君と"), listOf("心"))).map { it.single() }
        assertTrue(out[1] != null && out[1] != "xin")
    }

    @Test fun hanOnlySongIsPinyin() {
        assertEquals(listOf(listOf("Xīn")), RomanizationService.romanize(listOf(listOf("心"))))
    }

    @Test fun latinIsLeftAlone() {
        assertNull(RomanizationService.romanize(listOf(listOf("hello"))).single().single())
    }

    @Test fun composesEveryPresentScript() {
        val out = RomanizationService.romanize(listOf(listOf("Привет κόσμος"))).single().single()
        assertTrue(out != null && out.none { it in 'Ͱ'..'ԯ' })
    }

    @Test fun syllableSplitJapaneseUsesLineContext() {
        // Some lyrics split 段々堕ちてく one character per syllable; read alone, 々 and 堕 stay unromanized.
        val syllables = listOf("段", "々", "堕", "ち", "て", "く", "I", "Q", "ク", "ソ", "ワ", "ロ")
        val out = RomanizationService.romanize(listOf(syllables), writtenJapanese = false).single()
        // 段々 / 堕ち / て / く: a syllable that starts a word carries a leading space.
        assertEquals(listOf("dan", "dan", " o", "chi", " te", " ku"), out.take(6))
        // Written, the endings stay on their word.
        val written = RomanizationService.romanize(listOf(syllables)).single()
        assertEquals(listOf("Dan", "dan", " o", "chi", "te", " ku"), written.take(6))
        assertTrue(out.none { it != null && it.any { c -> c in '぀'..'鿿' } })
    }

    @Test fun koreanReadsBySound() {
        mapOf(
            "사랑해" to "saranghae", "좋아" to "joa", "없어" to "eopseo", "한국어 노래" to "hangugeo norae",
            "밤하늘" to "bamhaneul", "괜찮아" to "gwaenchana", "꽃잎" to "kkochip", "신라" to "silla", "같이" to "gachi",
        ).forEach { (hangul, roman) -> assertEquals(hangul, roman, KoreanRomanizer.romanize(hangul)) }
    }

    @Test fun chineseReadsByWord() {
        mapOf(
            "你好 world" to "nǐ hǎo world", "了" to "le", "的" to "de", "长" to "cháng",
            "还" to "huán", "着" to "zháo", "為" to "wéi",
            // A character with several readings takes its word's.
            "行走" to "xíng zǒu", "长大" to "zhǎng dà", "重复" to "chóng fù",
        ).forEach { (han, roman) -> assertEquals(han, roman, PinyinRomanizer.romanize(han)) }
    }

    @Test fun japaneseReadsPronunciationLikeKuroshiro() {
        // kuroshiro "spaced" romaji: particles by sound, long vowels as macrons, n' before vowels.
        assertEquals("watashi wa", JapaneseRomanizer.romanize("私は"))
        assertEquals("tōkyō e", JapaneseRomanizer.romanize("東京へ"))
        assertEquals("kin'en", KanaRomanizer.romanize("きんえん"))
        assertEquals("shimbun", KanaRomanizer.romanize("しんぶん"))
        assertEquals("matcha", KanaRomanizer.romanize("まっちゃ"))
    }

    @Test fun lineSyllablesKeepGeminationAndLongVowels() {
        // The っ's consonant goes where the っ is sung.
        assertEquals(listOf("kit", "to"), JapaneseRomanizer.romanizeLine(listOf("きっ", "と")))
        assertEquals(listOf("ki", "tto"), JapaneseRomanizer.romanizeLine(listOf("き", "っと")))
    }

    @Test fun syllableSeamsKeepEverySound() {
        fun line(vararg syllables: String) = JapaneseRomanizer.romanizeLine(syllables.toList()).map(String::trim)
        assertEquals(listOf("damat", "te iro"), line("黙っ", "ていろ"))
        assertEquals(listOf("hak", "ka"), line("発", "火"))
        assertEquals(listOf("mat", "cha"), line("待っ", "ちゃ"))
        // Before a vowel or the end of the line, a っ is a stop and unwritten.
        assertEquals(listOf("ma", "ik", "ka"), line("まっ", "いっ", "か"))
        assertEquals(listOf("a"), line("あっ"))
        // 々 repeats the kanji before it; a bracketed reading is the kanji's, said once.
        assertEquals(listOf("san", "san"), line("燦", "々"))
        assertEquals(listOf("itan", "kabu", "ke ba"), line("異端", "傾(かぶ)", "けば"))
    }

    private fun written(vararg syllables: String, attached: List<Boolean>? = null) =
        RomanizationService.romanize(listOf(syllables.toList()), attached?.let(::listOf)).single().map { it.orEmpty() }

    /** As [written], in a song whose other lines have kana, so its kanji read as Japanese. */
    private fun japanese(vararg syllables: String, attached: List<Boolean>? = null) =
        RomanizationService.romanize(listOf(syllables.toList(), listOf("の")), attached?.let { listOf(it, listOf(false)) })
            .first().map { it.orEmpty() }

    @Test fun japaneseIsSpelledAsWritten() {
        assertEquals(listOf("Watashi wa"), written("私は"))
        assertEquals(listOf("Toukyou e"), written("東京へ"))
        assertEquals(listOf("Koohii"), written("コーヒー"))
        // Endings stay on their word, and を is wo.
        assertEquals(listOf("Kimi no namae wo yonde ita"), written("君の名前を呼んでいた"))
        // The spelling as said is still there.
        assertEquals("tōkyō e", RomanizationService.romanize(listOf(listOf("東京へ")), writtenJapanese = false).single().single())
    }

    @Test fun spacedJapaneseSyllablesReadAsOneLine() {
        // A space after every syllable must not make the dictionary read each kanji alone.
        assertEquals(listOf("Se", "kai"), japanese("世 ", "界").map(String::trim))
        assertEquals(listOf("Se", "kai"), japanese("世", "界", attached = listOf(false, false)).map(String::trim))
        assertEquals(listOf("Gen", "jitsu"), japanese("現 ", "実").map(String::trim))
    }

    @Test fun hyphenatedJapaneseStaysJoined() {
        assertEquals(listOf("Doki-doki"), written("ドキ-ドキ"))
        assertEquals(listOf("Doki-", "doki"), written("ドキ-", "ドキ"))
    }

    @Test fun japaneseSignsAreWrittenOut() {
        assertEquals(listOf("Sonna koto"), written("ｿﾝﾅこと"))
        assertEquals(listOf("\"Ai\""), japanese("『愛』"))
    }

    @Test fun koreanReadsAcrossSyllableSeams() {
        assertEquals(listOf("Ga", "chi"), written("같", "이", attached = listOf(false, true)))
        assertEquals(listOf("Meong", "neun"), written("먹", "는", attached = listOf(false, true)))
        // Not across two words.
        assertEquals(listOf("Meok", "neun"), written("먹", "는", attached = listOf(false, false)))
    }

    @Test fun cyrillicPicksItsTable() {
        assertEquals(listOf("Ya tebya lyublyu"), written("Я тебя люблю"))
        assertEquals(listOf("Kyiv"), written("Київ"))
        assertEquals("shchi", CyrillicRomanizer.romanize("щи"))
    }

    @Test fun onlyRomanizedFirstWordsAreCapitalized() {
        assertEquals(listOf("", " ai"), japanese("Oh", "愛", attached = listOf(false, false)))
    }
}
