package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.parser.LetterSynthesizer
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class RomanizationTimingTest {
    // Tests inspect synchronous word progress; line tween coroutines need no frame loop here.
    private fun animator(config: RenderConfig) = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), config)
    private val configs = listOf(RenderConfig.FULL, RenderConfig.SIMPLE,
        RenderConfig.MINIMAL,
        RenderConfig.FULL.copy(appleMusic = true))
    private val lines = listOf(
        Line(listOf(Word("ちょう", 1000, 1500, romanizedText = "chou"),
            Word("だい", 1500, 2000, isPartOfWord = true, romanizedText = "dai"),
            Word("ビーム", 2000, 4000, romanizedText = "biimu")), 1000, 4500, groupId = 1),
        Line(listOf(Word("あ", 1500, 2000, romanizedText = "a")), 1500, 4000,
            role = LineRole.BACKGROUND, groupId = 1),
    )

    @Test fun `replacement notes reuse the main words progress in every renderer style`() {
        for (config in configs) {
            val originals = LetterSynthesizer.apply(lines, config, false)
            val display = TranslationPresentation(listOf("Give me a beam", "ah"), TranslationMode.Replace).displayLines(originals)
            for (time in listOf(1750L, 1950L, 2500L, 3500L)) {
                val main = animator(config).animate(originals, time, 0.016f)
                val replacement = animator(config).animate(display, time, 0.016f, romanizationLines = originals)
                main.forEachIndexed { index, state ->
                    assertTrue(state.isActive)
                    assertEquals("${config} at $time, line $index", state.wordStates, replacement[index].wordStates)
                }
            }
        }
    }

    @Test fun `adding note timing leaves replacement line animation unchanged`() {
        for (config in configs) {
            val originals = LetterSynthesizer.apply(lines, config, false)
            val display = TranslationPresentation(listOf("Give me a beam", "ah"), TranslationMode.Replace).displayLines(originals)
            for (time in listOf(0L, 1750L, 6000L)) {
                val before = animator(config).animate(display, time, 0.016f)
                val after = animator(config).animate(display, time, 0.016f, romanizationLines = originals)
                assertEquals(before, after.map { it.copy(wordStates = emptyList()) })
                if (time != 1750L) assertTrue(after.all { it.wordStates.isEmpty() })
            }
        }
    }

    @Test fun `line synced and static notes do not acquire word timing`() {
        for (config in configs) {
            for (type in listOf(LyricsType.Line, LyricsType.Static)) {
                val display = TranslationPresentation(listOf("one", "two"), TranslationMode.Replace).displayLines(lines)
                val states = animator(config).animate(display, 1750, 0.016f, lyricsType = type, romanizationLines = lines)
                assertTrue(states.all { it.wordStates.isEmpty() })
            }
        }
    }
}
