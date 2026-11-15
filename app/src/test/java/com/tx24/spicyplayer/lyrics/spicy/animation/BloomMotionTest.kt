package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.parser.LetterSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class BloomMotionTest {
    // ── Lift ────────────────────────────────────────────────────────────────────────────────

    @Test fun liftsOnAStiffSpringThatBarelyOvershootsAndStops() {
        assertEquals(0.0, BloomMotion.lift(0.0), 0.0)
        assertEquals(0.0, BloomMotion.lift(-100.0), 0.0)
        assertEquals(0.4604, BloomMotion.lift(300.0), 0.001)
        // Damping 0.93: never more than a hair past the top, and exactly there once settled.
        assertTrue((0..3000 step 10).all { BloomMotion.lift(it.toDouble()) < 1.0005 })
        assertEquals(1.0, BloomMotion.lift(5000.0), 0.0)
    }

    // ── Held words ──────────────────────────────────────────────────────────────────────────

    @Test fun aHeldWordsLettersSwellInTurnAndPushOutFromTheMiddle() {
        val widths = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        assertEquals(0, BloomMotion.flame(0.0, 2000, 4, widths).rest)
        val flame = BloomMotion.flame(500.0, 2000, 4, widths)
        assertNull(flame.rest)
        // Letters set off 200 ms apart (40% of 2 s over four letters).
        assertTrue(flame.scale(0) > flame.scale(1) && flame.scale(1) > flame.scale(2))
        assertTrue(flame.x(0) < 0f && flame.x(1) < 0f && flame.x(2) > 0f && flame.x(3) > 0f)
        // The outer letters are pushed further than the inner ones.
        assertTrue(flame.x(0) < flame.x(1))
        assertTrue(flame.y(0) < 0f && flame.glow(0) > 0f)
        // At full strength (two seconds) a letter grows by up to 14%.
        assertTrue((0..4000 step 50).all { BloomMotion.flame(it.toDouble(), 2000, 4, widths).scale(0) <= 1.14f + 1e-4f })
    }

    @Test fun aOneSecondWordOnlyGlowsAndLifts() {
        val flame = BloomMotion.flame(400.0, 1000, 3, floatArrayOf(0.5f, 0.5f, 0.5f))
        assertEquals(1f, flame.scale(0), 0f)
        assertEquals(0f, flame.x(0), 0f)
        assertTrue(flame.glow(0) > 0f && flame.y(0) < 0f)
    }

    @Test fun withoutWidthsTheLettersSwellInPlace() {
        val flame = BloomMotion.flame(500.0, 2000, 4, null)
        assertTrue(flame.scale(0) > 1f)
        assertTrue((0 until 4).all { flame.x(it) == 0f })
    }

    @Test fun onceSettledEveryLetterRestsLifted() {
        val flame = BloomMotion.flame(20_000.0, 2000, 4, null)
        assertEquals(1, flame.rest)
        assertEquals(-BloomMotion.LIFT_EM, flame.y(3), 0f)
        assertEquals(1f, flame.scale(3), 0f)
        assertEquals(0f, flame.glow(3), 0f)
    }

    @Test fun aSyllablesSliceOfTheFlameIsItsOwnLetters() {
        val whole = BloomMotion.flame(500.0, 2000, 4, null)
        val second = whole.slice(2, 2)
        assertEquals(whole.scale(2), second.scale(0), 0f)
        assertEquals(whole.scale(3), second.scale(1), 0f)
        assertEquals(0, BloomMotion.flame(0.0, 2000, 4, null).slice(2, 2).rest)
    }

    // ── The fill ────────────────────────────────────────────────────────────────────────────

    @Test fun reachKeepsTheFadeCloseOnSplitWordsAndRunsItPastARowsEnd() {
        assertEquals(0.5f, BloomMotion.reach(2, split = false, rowEnd = false), 0f)
        assertEquals(0.25f, BloomMotion.reach(5, split = false, rowEnd = false), 0f)
        assertEquals(1f, BloomMotion.reach(5, split = false, rowEnd = true), 0f)
        assertEquals(0.25f, BloomMotion.reach(2, split = true, rowEnd = true), 0f)
        assertEquals(0.12f, BloomMotion.reach(5, split = true, rowEnd = false), 0f)
    }

    private val twoWords = arrayOf<BloomMotion.Span?>(
        BloomMotion.Span(0f, 100f, 0.25f, rowStart = true, rowEnd = false),
        BloomMotion.Span(100f, 100f, 1f, rowStart = false, rowEnd = true),
    )

    private fun edges(t: Double, spans: Array<BloomMotion.Span?> = twoWords) =
        BloomMotion.sweepEdges(spans, longArrayOf(0, 1000), longArrayOf(1000, 2000), 20f, t)

    @Test fun oneEdgeCarriesTheLightFromWordToWord() {
        // The first word brings the fade in from wholly before it to a quarter-fade past its end.
        assertEquals(Double.NEGATIVE_INFINITY, edges(-1.0)[0], 0.0)
        assertEquals(42.5, edges(500.0)[0], 1e-9)
        assertEquals(95.0, edges(1000.0)[0], 1e-9)
        // The next word waits, dimming, where the first one left the light.
        assertEquals(95.0, edges(1000.0)[1], 1e-9)
        assertEquals(-43.75f, BloomMotion.gradientPosition(edges(1000.0)[1], 100f, 100f, 20f), 1e-4f)
        // It picks the light up there and takes it past the end of the row.
        assertEquals(152.5, edges(1500.0)[1], 1e-9)
        assertEquals(100f, BloomMotion.gradientPosition(edges(2000.0)[1], 100f, 100f, 20f), 0f)
        assertEquals(100f, BloomMotion.gradientPosition(edges(2000.0)[0], 0f, 100f, 20f), 0f)
    }

    @Test fun theRestOfARowLightsUpInHalfASecondOnceItsLastWordEnds() {
        val split = arrayOf<BloomMotion.Span?>(BloomMotion.Span(0f, 100f, 0.12f, rowStart = true, rowEnd = true))
        fun at(t: Double) = BloomMotion.sweepEdges(split, longArrayOf(0), longArrayOf(1000), 20f, t)[0]
        assertEquals(92.4, at(1000.0), 1e-4)
        assertEquals(101.2, at(1250.0), 1e-4)
        assertEquals(110.0, at(1500.0), 1e-6)
        assertEquals(110.0, at(3000.0), 1e-6)
    }

    @Test fun eachRowStartsDimAndALaterWordSungEarlyLightsNothingBeforeIt() {
        val rows = arrayOf<BloomMotion.Span?>(
            BloomMotion.Span(0f, 100f, 1f, rowStart = true, rowEnd = true),
            BloomMotion.Span(100f, 100f, 1f, rowStart = true, rowEnd = true),
        )
        // The first row is lit; the second, not yet sung, isn't lit by it.
        assertEquals(Double.NEGATIVE_INFINITY, edges(1000.0, rows)[1], 0.0)
        // Out of order: the second word is sung first, the first still waits in the dark.
        val early = BloomMotion.sweepEdges(twoWords, longArrayOf(1000, 0), longArrayOf(2000, 500), 20f, 700.0)
        assertEquals(Double.NEGATIVE_INFINITY, early[0], 0.0)
    }

    @Test fun keepSyllablePaceCrossesEachLetterInItsOwnTime() {
        val starts = longArrayOf(0, 100)
        val ends = longArrayOf(100, 1000)
        val x = floatArrayOf(0f, 50f)
        val w = floatArrayOf(50f, 50f)
        assertEquals(0.25, BloomMotion.pacedShare(starts, ends, x, w, 50.0)!!, 1e-9)
        assertEquals(0.5, BloomMotion.pacedShare(starts, ends, x, w, 100.0)!!, 1e-9)
        assertEquals(0.75, BloomMotion.pacedShare(starts, ends, x, w, 550.0)!!, 1e-9)
        assertNull(BloomMotion.pacedShare(starts, ends, floatArrayOf(0f), w, 50.0))
        // The share drives the edge in place of the word's even progress.
        val paced = BloomMotion.sweepEdges(twoWords, longArrayOf(0, 1000), longArrayOf(1000, 2000), 20f, 500.0) { if (it == 0) 0.9 else null }
        assertEquals(-10.0 + 105.0 * 0.9, paced[0], 1e-9)
    }

    @Test fun fillPositionsAndToneRunFromDimToLit() {
        assertEquals(-50f, BloomMotion.gradientPosition(Double.NEGATIVE_INFINITY, 0f, 50f, 20f), 0f)
        assertEquals(BloomMotion.UNLIT, BloomMotion.letterTone(-50f), 0f)
        assertEquals(BloomMotion.LIT, BloomMotion.letterTone(100f), 1e-6f)
        val (start, end) = BloomMotion.wipeStops(-50f, 100f, 22f)
        assertEquals(-0.22f, start, 1e-6f)
        assertEquals(0f, end, 1e-6f)
        assertEquals(1f, BloomMotion.wipeStops(100f, 100f, 22f).first, 1e-6f)
    }

    // ── Interlude ───────────────────────────────────────────────────────────────────────────

    @Test fun dotsFadeInFillInTurnAndTheLastIsLitAsTheGroupLeaves() {
        assertEquals(0f, BloomMotion.dotOpacity(0.0, 6000, 0), 0f)
        assertTrue(BloomMotion.dotOpacity(1000.0, 6000, 0) > BloomMotion.dotOpacity(1000.0, 6000, 1))
        assertEquals(0.94f, BloomMotion.dotOpacity(5500.0, 6000, 2), 1e-6f)
        assertEquals(0.94f, BloomMotion.dotOpacity(5500.0, 6000, 0), 1e-6f)
    }

    @Test fun theGroupBreathesInWholeCyclesThenSwellsShrinksAndFades() {
        // 6 s, closing 0.5 s before its end: it leaves from 4.75 s, one breath before that.
        assertEquals(1f, BloomMotion.dotGroup(0.0, 6000).scale, 1e-6f)
        assertEquals(1.2f, BloomMotion.dotGroup(2375.0, 6000).scale, 1e-6f)
        assertEquals(BloomMotion.DotGroup(1.2f, 1f), BloomMotion.dotGroup(5500.0, 6000))
        assertEquals(0.5f, BloomMotion.dotGroup(5750.0, 6000).scale, 1e-6f)
        assertEquals(0f, BloomMotion.dotGroup(5750.0, 6000).opacity, 1e-6f)
        // Too short for a breath: it holds still until it leaves.
        assertEquals(1f, BloomMotion.dotGroup(800.0, 3000).scale, 0f)
    }

    // ── Scroll ──────────────────────────────────────────────────────────────────────────────

    @Test fun theScrollTakesLongerAfterAPauseAndStartsNoEarlierThanTheLineAboveEnds() {
        assertEquals(BloomMotion.Scroll(480, 0), BloomMotion.scrollFor(0))
        assertEquals(BloomMotion.Scroll(615, 475), BloomMotion.scrollFor(475))
        assertEquals(BloomMotion.Scroll(750, 750), BloomMotion.scrollFor(5000))
        val lines = listOf(
            Line(listOf(Word("one", 0, 1000)), 0, 1000),
            Line(listOf(Word("ooh", 900, 1400)), 900, 1400, role = LineRole.BACKGROUND),
            Line(listOf(Word("two", 1700, 2500)), 1700, 2500),
        )
        // The background vocal ends last: 300 ms of room before the next line.
        assertEquals(BloomMotion.scrollFor(300), BloomMotion.scroll(lines, 1200.0))
        assertEquals(BloomMotion.Scroll(750, 0), BloomMotion.scroll(lines, 3000.0))
        assertEquals(listOf(0, 25, 44, 56, 63, 63), (0..5).map(BloomMotion::rowDelayMs))
    }

    // ── Letters, eligibility and the animator ───────────────────────────────────────────────

    private fun split(text: String, ms: Long, config: RenderConfig, background: Boolean = false): Word = LetterSynthesizer.apply(
        listOf(Line(listOf(Word(text, 0, ms)), 0, ms, role = if (background) LineRole.BACKGROUND else LineRole.LEAD)), config, false,
    ).single().words.single()

    @Test fun bloomSplitsOnlyWordsOfTwoToSevenLettersHeldASecond() {
        val bloom = RenderConfig.BLOOM
        assertFalse(split("I", 3000, bloom).isLetterGroup)
        assertTrue(split("oh", 3000, bloom).isLetterGroup)
        assertTrue(split("forever", 2000, bloom).isLetterGroup)
        assertFalse(split("sunshine", 2000, bloom).isLetterGroup)
        assertTrue(split("é".repeat(7), 2000, bloom).isLetterGroup)
        assertFalse(split("é".repeat(8), 2000, bloom).isLetterGroup)
        assertTrue(split("love", 1000, bloom).isLetterGroup)
        assertFalse(split("love", 999, bloom).isLetterGroup)
        assertFalse(split("love", 3000, bloom, background = true).isLetterGroup)
        // Spring has no length bounds and splits background vocals too.
        assertTrue(split("sunshine", 2000, RenderConfig.FULL).isLetterGroup)
        assertTrue(split("I", 2000, RenderConfig.FULL).isLetterGroup)
        assertTrue(split("love", 3000, RenderConfig.FULL, background = true).isLetterGroup)
    }

    @Test fun bloomsEmphasisStartsAndEndsALittleLater() {
        val word = split("forever", 2000, RenderConfig.BLOOM)
        assertEquals(21L, word.startMs)
        assertEquals(2040L, word.endMs)
        assertEquals(0L to 2000L, LetterSynthesizer.sungSpan(word, RenderConfig.BLOOM))
        val spring = split("forever", 2000, RenderConfig.FULL)
        assertEquals(0L to 1750L, spring.startMs to spring.endMs)
    }

    @Test fun aWordSungInSyllablesIsJudgedWhole() {
        val parts = listOf(Word("lo", 0, 600), Word("ve", 600, 1400, isPartOfWord = true))
        val bloom = LetterSynthesizer.apply(listOf(Line(parts, 0, 1400)), RenderConfig.BLOOM, false).single().words
        // Neither part is held a second, but the word is: both split, each into its own letters.
        assertEquals(listOf(true, true), bloom.map { it.isLetterGroup })
        assertEquals(listOf(2, 2), bloom.map { it.letters.size })
        assertEquals(listOf(0, 0), LetterSynthesizer.runStarts(bloom).toList())
        val spring = LetterSynthesizer.apply(listOf(Line(parts, 0, 1400)), RenderConfig.FULL, false).single().words
        assertEquals(listOf(false, false), spring.map { it.isLetterGroup })
    }

    private fun animator(config: RenderConfig = RenderConfig.BLOOM) = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), config)

    @Test fun animatorKeepsTheSwellTailThenSettlesAndReturnsToRestAfterSeekingBack() {
        val lines = LetterSynthesizer.apply(listOf(Line(listOf(Word("love", 1000, 3000)), 1000, 3000)), RenderConfig.BLOOM, false)
        val anim = animator()
        fun word(time: Long) = anim.animate(lines, time, 0.016f).single().wordStates.single()
        val before = word(0)
        assertTrue(before.letterStates.all { it.scale == 1f && it.yOffset == 0f && it.glow == 0f })
        val active = word(2500)
        assertTrue(active.letterStates.any { it.scale > 1f && it.glow > 0f && it.yOffset < 0f })
        val tail = word(3100)
        assertEquals(ElementState.Sung, tail.state)
        assertTrue(tail.letterStates.any { it.scale > 1f && it.glow > 0f })
        // Long after the line, the letters have settled back down to rest.
        val done = word(8000)
        assertTrue(done.letterStates.all { it.scale == 1f && it.yOffset == 0f && it.glow == 0f })
        assertEquals(before, word(0))
        assertEquals(active, word(2500))
        assertEquals(active, animator().animate(lines, 2500, 0.016f).single().wordStates.single())
        // Sung through to rest, then back: the tail plays again rather than snapping to rest.
        word(8000)
        word(0)
        assertEquals(animator().animate(lines, 3100, 0.016f).single().wordStates.single(), word(3100))
    }

    @Test fun syllablesOfOneWordShareOneSwell() {
        val parts = listOf(Word("lo", 1000, 1600), Word("ve", 1600, 3000, isPartOfWord = true))
        val lines = LetterSynthesizer.apply(listOf(Line(parts, 1000, 3000)), RenderConfig.BLOOM, false)
        val words = animator().animate(lines, 1500, 0.016f).single().wordStates
        val whole = lines.single().words
        val flame = BloomMotion.flame(1500 - BloomMotion.POSITION_SHIFT_MS - whole[0].letters.first().startMs,
            whole[1].endMs - whole[0].startMs, 4, floatArrayOf(0f, 0f, 0f, 0f))
        assertEquals(flame.scale(2), words[1].letterStates[0].scale, 1e-6f)
        assertEquals(flame.scale(0), words[0].letterStates[0].scale, 1e-6f)
    }

    @Test fun wordsLiftAsTheyStartButNeverAboveTheOneBefore() {
        // The second word is timed before the first: it may not stand up ahead of the line.
        val line = Line(listOf(Word("one", 1500, 2000), Word("two", 1000, 1400)), 1000, 2000)
        val words = animator().animate(listOf(line), 1300, 0.016f).single().wordStates
        assertEquals(0f, words[0].yOffset, 0f)
        assertEquals(0f, words[1].yOffset, 0f)
        val later = animator().animate(listOf(line), 1800, 0.016f).single().wordStates
        assertTrue(later[0].yOffset < 0f)
        assertTrue(later[1].yOffset >= later[0].yOffset)
    }

    @Test fun bloomLinesStayAtFullOpacityAndDimByTone() {
        val lines = listOf(
            Line(listOf(Word("one", 0, 1000)), 0, 1000),
            Line(listOf(Word("two", 2000, 3000)), 2000, 3000),
        )
        val states = animator().animate(lines, 2500, 0.016f)
        assertEquals(1f, states[0].opacity, 0f)
        assertEquals(1f, states[1].opacity, 0f)
        assertEquals(1f, states[1].lit, 0f)
        assertEquals(0f, animator().animate(lines, 500, 0.016f)[1].lit, 0f)
    }

    @Test fun earlySungLinesCountAsSungOnceTheirWordsEnd() {
        // Held through a short gap, the first line's time runs on to the second's start.
        val lines = listOf(
            Line(listOf(Word("one", 0, 1000)), 0, 1500),
            Line(listOf(Word("two", 1500, 2500)), 1500, 2500),
        )
        assertEquals(ElementState.Active, animator().animate(lines, 1300, 0.016f)[0].state)
        assertEquals(ElementState.Sung, animator().animate(lines, 1300, 0.016f, earlySungBefore = 1)[0].state)
    }

    private val sweepConfig = RenderConfig.BLOOM

    private fun sweepWords(glued: Boolean, geometry: LineSweepGeometry?, time: Long): List<WordAnimState> {
        val line = Line(listOf(Word("ab", 0, 1000), Word("cd", 1000, 2000, isPartOfWord = glued)), 0, 2000)
        return animator(sweepConfig).animate(listOf(line), time + BloomMotion.POSITION_SHIFT_MS.toLong(), 0.016f,
            lineGeometry = geometry?.let { g -> { _: Int -> g } }).single().wordStates
    }

    private fun geometry(widths: FloatArray = floatArrayOf(100f, 100f), letters: Array<FloatArray?> = arrayOfNulls(2)) =
        LineSweepGeometry(widths, 20f, letters, 40f, letterCounts = intArrayOf(2, 2))

    @Test fun theFadeSpillsIntoTheNextWordAsTheFirstOneEnds() {
        val words = sweepWords(false, geometry(), 999)
        assertTrue(words[0].gradientPosition > 90f)
        assertTrue(words[1].gradientPosition > -50f && words[1].gradientPosition < 0f)
    }

    @Test fun gluedPartsHaveNoSpaceBetweenThemForTheEdgeToCross() {
        val spaced = sweepWords(false, geometry(), 500)[0].gradientPosition
        val glued = sweepWords(true, geometry(), 500)[0].gradientPosition
        assertTrue(glued > -50f && spaced > -50f && glued < 100f && spaced < 100f)
        assertNotEquals(spaced, glued)
    }

    @Test fun lettersOfAHeldWordFillInOrderAlongTheEdge() {
        val line = LetterSynthesizer.apply(listOf(Line(listOf(Word("love", 0, 2000)), 0, 2000)), sweepConfig, false)
        val word = line.single().words.single()
        val g = LineSweepGeometry(floatArrayOf(100f), 20f, arrayOf(floatArrayOf(25f, 25f, 25f, 25f)), 40f, letterCounts = intArrayOf(4))
        val states = animator(sweepConfig).animate(line, 1000, 0.016f, lineGeometry = { g }).single().wordStates.single().letterStates
        assertEquals(word.letters.size, states.size)
        val positions = states.map { it.gradientPosition }
        assertTrue(positions.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(positions.first() > positions.last())
        // Each letter's glow is dimmed by how lit it is.
        assertTrue(states.last().glow <= states.first().glow)
    }

    @Test fun aGeometryThatDoesNotFitFallsBackToFillingWordByWord() {
        val words = sweepWords(false, geometry(widths = floatArrayOf(100f)), 500)
        assertEquals(25f, words[0].gradientPosition, 1f)
        assertEquals(-50f, words[1].gradientPosition, 0f)
    }
}
