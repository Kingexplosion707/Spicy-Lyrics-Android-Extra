package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.parser.LetterSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class RefreshedMotionTest {
    @Test fun emphasisRisesAndFallsBackToRest() {
        assertEquals(0f, RefreshedMotion.emphasis(0.0), 0f)
        assertEquals(1f, RefreshedMotion.emphasis(0.5), 1e-4f)
        assertEquals(0f, RefreshedMotion.emphasis(1.0), 0f)
        assertTrue(RefreshedMotion.emphasis(0.25) in 0.1f..0.99f)
    }

    @Test fun lettersSetOffInSequenceAndSpreadFromTheMiddle() {
        val strength = RefreshedMotion.waveStrength(2000, false)
        // Letter 0 is under way before letter 4 has started.
        val t = 400.0
        val first = RefreshedMotion.letter(t, 0, 0, 5, strength, false, 1f)
        val last = RefreshedMotion.letter(t, 0, 4, 5, strength, false, 1f)
        assertTrue(first.scale > last.scale)
        // At the peak the left letters move left and the right ones right.
        val peak = strength.duration / 2
        val left = RefreshedMotion.letter(peak, 0, 0, 5, strength, false, 1f)
        val right = RefreshedMotion.letter(peak + strength.duration * 0.4 * 4 / 5, 0, 4, 5, strength, false, 1f)
        assertTrue(left.x < 0f && right.x > 0f)
        assertTrue(left.y < 0f)
    }

    @Test fun letterGlowDimsWithTheFill() {
        val strength = RefreshedMotion.waveStrength(3000, false)
        val lit = RefreshedMotion.letter(1500.0, 0, 0, 4, strength, false, 1f).glow
        val dim = RefreshedMotion.letter(1500.0, 0, 0, 4, strength, false, 0f).glow
        assertEquals(0.4f, dim / lit, 1e-4f)
    }

    @Test fun backgroundFloatIsTwiceTheHeight() {
        val strength = RefreshedMotion.waveStrength(1000, false)
        // Before the swell starts only the float is moving the letter.
        val lead = RefreshedMotion.letter(-200.0, 0, 0, 4, strength, false, 0f)
        val bg = RefreshedMotion.letter(-200.0, 0, 0, 4, strength, true, 0f)
        assertEquals(lead.y * 2f, bg.y, 1e-6f)
    }

    @Test fun durationStrengthAndLastWordBoost() {
        val short = RefreshedMotion.waveStrength(500, false)
        assertEquals(1000.0, short.duration, 0.0)
        assertEquals(0.075f, short.amount, 1e-6f)
        assertEquals(0.5f * (1f / 27f), short.blur, 1e-6f)
        val last = RefreshedMotion.waveStrength(500, true)
        assertEquals(short.amount * 1.6f, last.amount, 1e-6f)
        assertEquals(1200.0, last.duration, 0.0)
        assertEquals(1.2f, RefreshedMotion.waveStrength(60000, false).amount, 0f)
        assertEquals(0.8f, RefreshedMotion.waveStrength(60000, false).blur, 0f)
    }

    @Test fun wordsFloatOnAnEasedTrackAtLeastASecondLong() {
        assertEquals(0f, RefreshedMotion.wordFloat(0.0, 0, 300), 0f)
        assertEquals(0.75f, RefreshedMotion.wordFloat(500.0, 0, 300), 1e-6f)
        assertEquals(1f, RefreshedMotion.wordFloat(1000.0, 0, 300), 0f)
        assertEquals(0.75f, RefreshedMotion.wordFloat(1000.0, 0, 2000), 1e-6f)
        assertEquals(0.01f, RefreshedMotion.wordOffset(0f), 0f)
        assertEquals(-0.033f, RefreshedMotion.wordOffset(1f), 1e-6f)
    }

    @Test fun oneEdgeRunsAlongTheLineWithoutStoppingBetweenWords() {
        val x = floatArrayOf(0f, 100f)
        val box = floatArrayOf(100f, 100f)
        val starts = longArrayOf(0, 1000)
        val ends = longArrayOf(1000, 2000)
        fun edges(t: Double) = RefreshedMotion.sweepEdges(x, box, starts, ends, 20f, t)
        // Before the line: both fully dim. The first word brings the fade in from before it.
        assertEquals(-50f, RefreshedMotion.sweepPosition(edges(-1.0)[0], 0f, 90f, 20f), 0f)
        assertEquals(-10.0, edges(0.001)[0], 0.1)
        // Midway through the first word the second is still dim; at its end the first is lit
        // and the edge carries on into the second.
        assertEquals(-50f, RefreshedMotion.sweepPosition(edges(500.0)[1], 100f, 90f, 20f), 0f)
        assertEquals(100f, RefreshedMotion.sweepPosition(edges(1000.0)[0], 0f, 90f, 20f), 0f)
        assertEquals(100.0, edges(1000.0)[0], 0.0)
        // The last word takes the fade out past the end, so everything finishes fully lit.
        assertEquals(210.0, edges(2000.0)[1], 0.0)
        assertEquals(100f, RefreshedMotion.sweepPosition(edges(5000.0)[1], 100f, 90f, 20f), 0f)
        // A word sung early never lights one still to come; one sung before follows the light.
        val early = RefreshedMotion.sweepEdges(x, box, longArrayOf(1000, 0), longArrayOf(2000, 500), 20f, 700.0)
        assertEquals(Double.NEGATIVE_INFINITY, early[0], 0.0)
    }

    @Test fun dotsFillInOrderAndBreatheWholeCyclesWithoutBounce() {
        assertEquals(0f, RefreshedMotion.dotAlpha(0.0, 9550, 0), 0f)
        assertEquals(0.57f, RefreshedMotion.dotAlpha(1500.0, 9550, 0), 1e-6f)
        assertEquals(0.22f, RefreshedMotion.dotAlpha(1500.0, 9550, 1), 1e-6f)
        assertEquals(0.92f, RefreshedMotion.dotAlpha(9000.0, 9550, 2), 1e-6f)
        assertEquals(0f, RefreshedMotion.dotAlpha(120.0, 9550, 1), 0f)
        assertEquals(1f, RefreshedMotion.dotBreath(0.0, 8900), 0f)
        assertEquals(1.16f, RefreshedMotion.dotBreath(2100.0, 8900), 1e-6f)
        assertEquals(1f, RefreshedMotion.dotBreath(8400.0, 8900), 1e-6f)
        assertEquals(1f, RefreshedMotion.dotBreath(1000.0, 3000), 0f)
    }

    @Test fun sevenGraphemeLimitOnlyAppliesToRefreshedSimple() {
        fun split(text: String, config: RenderConfig): Word = LetterSynthesizer.apply(
            listOf(Line(listOf(Word(text, 0, 2000)), 0, 2000)), config, false).single().words.single()
        val refreshed = RenderConfig.SIMPLE.copy(refreshedSimple = true)
        assertTrue(split("forever", refreshed).isLetterGroup)
        assertFalse(split("sunshine", refreshed).isLetterGroup)
        assertTrue(split("e\u0301".repeat(7), refreshed).isLetterGroup)
        assertFalse(split("e\u0301".repeat(8), refreshed).isLetterGroup)
        assertTrue(split("sunshine", RenderConfig.SIMPLE).isLetterGroup)
        assertTrue(split("sunshine", RenderConfig.FULL.copy(refreshedSimple = true)).isLetterGroup)
        val word = split("forever", refreshed)
        assertEquals(21L, word.startMs)
        assertEquals(2040L, word.endMs)
    }

    @Test fun refreshedSplitsOnlyWordsOfTwoToSevenLettersHeldASecond() {
        fun split(text: String, ms: Long) = LetterSynthesizer.apply(
            listOf(Line(listOf(Word(text, 0, ms)), 0, ms)), RenderConfig(true, false, refreshedSimple = true), false,
        ).single().words.single().isLetterGroup
        assertFalse(split("I", 3000))
        assertTrue(split("oh", 3000))
        assertTrue(split("love", 1000))
        assertFalse(split("love", 999))
    }

    @Test fun animatorKeepsTheSwellTailAndReturnsToRestAfterSeekingBack() {
        val config = RenderConfig(true, false, refreshedSimple = true)
        val lines = LetterSynthesizer.apply(listOf(Line(listOf(Word("love", 1000, 3000)), 1000, 3000)), config, false)
        fun animator() = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), config)
        val anim = animator()
        fun word(time: Long) = anim.animate(lines, time, 0.016f).single().wordStates.single()
        val before = word(0)
        assertTrue(before.letterStates.all { it.scale == 1f && it.yOffset == 0f && it.glow == 0f })
        val active = word(2500)
        assertTrue(active.letterStates.any { it.scale > 1f && it.glow > 0f })
        val tail = word(3100)
        assertEquals(ElementState.Sung, tail.state)
        assertTrue(tail.letterStates.any { it.scale > 1f && it.glow > 0f })
        val done = word(5000)
        assertTrue(done.letterStates.all { it.scale == 1f && it.yOffset == 0f && it.glow == 0f })
        assertEquals(before, word(0))
        assertEquals(active, word(2500))
        assertEquals(active, animator().animate(lines, 2500, 0.016f).single().wordStates.single())
    }


    private val sweepConfig = RenderConfig(true, false, refreshedSimple = true)

    private fun sweepWords(glued: Boolean, geometry: LineSweepGeometry?, time: Long): List<WordAnimState> {
        val line = Line(listOf(Word("ab", 0, 1000), Word("cd", 1000, 2000, isPartOfWord = glued)), 0, 2000)
        val anim = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), sweepConfig)
        return anim.animate(listOf(line), time, 0.016f, lineGeometry = geometry?.let { g -> { _: Int -> g } })
            .single().wordStates
    }

    private fun geometry(widths: FloatArray = floatArrayOf(100f, 100f), letters: Array<FloatArray?> = arrayOfNulls(2)) =
        LineSweepGeometry(widths, 20f, letters, 40f)

    @Test fun theFadeSpillsIntoTheNextWordAsTheFirstOneEnds() {
        val words = sweepWords(false, geometry(), 999)
        assertEquals(100f, words[0].gradientPosition, 0.5f)
        assertTrue(words[1].gradientPosition > -50f && words[1].gradientPosition < 100f)
    }

    @Test fun gluedPartsHaveNoSpaceBetweenThemForTheEdgeToCross() {
        val spaced = sweepWords(false, geometry(), 500)[0].gradientPosition
        val glued = sweepWords(true, geometry(), 500)[0].gradientPosition
        // The spaced word's box is wider, so the same time carries the edge further along a longer strip.
        assertTrue(glued > -50f && glued < spaced && spaced < 100f)
    }

    @Test fun lettersOfAHeldWordFillInOrderAlongTheEdge() {
        val line = LetterSynthesizer.apply(listOf(Line(listOf(Word("love", 0, 2000)), 0, 2000)), sweepConfig, false)
        val word = line.single().words.single()
        val anim = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), sweepConfig)
        val g = LineSweepGeometry(floatArrayOf(100f), 20f, arrayOf(floatArrayOf(25f, 25f, 25f, 25f)), 40f)
        val states = anim.animate(line, 1000, 0.016f, lineGeometry = { g }).single().wordStates.single().letterStates
        assertEquals(word.letters.size, states.size)
        val positions = states.map { it.gradientPosition }
        assertTrue(positions.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(positions.first() > positions.last())
    }

    @Test fun aGeometryThatDoesNotFitFallsBackToFillingWordByWord() {
        val words = sweepWords(false, geometry(widths = floatArrayOf(100f)), 500)
        assertEquals(25f, words[0].gradientPosition, 10f)
        assertEquals(-50f, words[1].gradientPosition, 0f)
    }
}
