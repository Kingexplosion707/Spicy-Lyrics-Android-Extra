package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class MinimalOverlapTest {
    private val lines = listOf(
        Line(listOf(Word("long", 0, 5000)), 0, 5000),
        Line(listOf(Word("short", 1000, 2000)), 1000, 2000),
        Line(listOf(Word("later", 6000, 7000)), 6000, 7000),
    )

    @Test fun aLineSungWhileAnEarlierOneIsStillActiveStaysVisibleAtFullSize() {
        val anim = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), RenderConfig.MINIMAL)
        val states = anim.animate(lines, 3000, 0.016f)
        assertEquals(ElementState.Sung, states[1].state)
        assertEquals(LyricsAnimator.MINIMAL_AFTER_ACTIVE_OPACITY, states[1].opacity, 1e-6f)
        assertEquals(1f, states[1].scale, 0f)
        assertEquals(0f, states[1].blur, 0f)
    }
}
