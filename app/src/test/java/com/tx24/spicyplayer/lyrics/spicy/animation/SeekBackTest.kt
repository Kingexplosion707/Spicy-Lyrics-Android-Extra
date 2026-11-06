package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekBackTest {
    private fun line(start: Long) = Line(listOf(Word("a", start, start + 500), Word("b", start + 500, start + 1000)), start)

    @Test
    fun `lines ahead of a backward seek are unsung again`() {
        val lines = List(4) { line(it * 1000L) }
        val animator = LyricsAnimator(CoroutineScope(Job()))
        var t = 0L
        while (t <= 4500) { animator.animate(lines, t, 0.016f); t += 16 }
        // Seek back to the first line and let a second pass.
        var last = emptyList<LineAnimState>()
        t = 100
        repeat(60) { last = animator.animate(lines, t, 0.016f); t += 16 }
        last.drop(2).forEachIndexed { i, state ->
            assertEquals("line ${i + 2} word states", ElementState.NotSung, state.wordStates.first().state)
            assertEquals("line ${i + 2} gradient", -20f, state.wordStates.first().gradientPosition, 0.01f)
        }
    }
}
