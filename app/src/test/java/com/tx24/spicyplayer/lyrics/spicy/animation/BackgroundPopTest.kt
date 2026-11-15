package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundPopTest {
    private val lines = listOf(
        Line(listOf(Word("lead", 0, 1000)), 0, 1000, groupId = 1),
        Line(listOf(Word("echo", 2000, 3000)), 2000, 3000, role = LineRole.BACKGROUND, groupId = 1),
    )

    private fun presence(config: RenderConfig, time: Long) =
        LyricsAnimator(CoroutineScope(Job().apply { cancel() }), config).animate(lines, time, 0.016f)[1].presence

    @Test fun backgroundLineIsTuckedAwayUntilItsLeadOrItselfIsSung() {
        val on = RenderConfig.FULL.copy(backgroundPop = true)
        assertEquals(1f, presence(on, 500), 0f)
        assertEquals(0f, presence(on, 1500), 0f)
        assertEquals(1f, presence(on, 2500), 0f)
    }

    @Test fun offByDefault() {
        assertEquals(1f, presence(RenderConfig.FULL, 1500), 0f)
    }
}
