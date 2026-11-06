package com.tx24.spicyplayer.ui.nowplaying

import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice.pick
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice.wantsAnimated
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice.wantsCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageArtChoiceTest {
    private val canvas = "https://canvaz.scdn.co/a.mp4"
    private val motion = "https://mvod.itunes.apple.com/a.m3u8"
    private val found = CanvasLookup.Found(canvas)

    private val all = StageArt.entries.toSet()
    private val videos = setOf(StageArt.Canvas, StageArt.AnimatedCover)

    @Test
    fun `canvas first, then the animated cover, then the still cover`() {
        assertEquals(StagePicture.Canvas(canvas), pick(all, true, false, found, motion))
        assertEquals(StagePicture.Animated(motion), pick(all, true, false, CanvasLookup.None, motion))
        assertEquals(StagePicture.Still, pick(all, true, false, CanvasLookup.None, null))
        // Animated cover first: the canvas only fills in once the record is known to have none.
        assertEquals(StagePicture.Animated(motion), pick(all, true, false, found, motion, animatedFirst = true))
        assertEquals(StagePicture.Still, pick(all, true, false, found, null, animatedFirst = true, motionPending = true))
        assertEquals(StagePicture.Canvas(canvas), pick(all, true, false, found, null, animatedFirst = true))
    }

    @Test
    fun `nothing picked that the song has is no stage`() {
        assertNull(pick(videos, true, false, CanvasLookup.None, null))
        assertNull(pick(setOf(StageArt.Canvas), true, false, CanvasLookup.None, motion))
        assertNull(pick(emptySet(), true, false, found, motion))
        assertEquals(StagePicture.Canvas(canvas), pick(setOf(StageArt.Canvas), true, false, found, motion))
    }

    @Test
    fun `while the canvas is looked up, only the still cover may hold the stage`() {
        assertEquals(StagePicture.Still, pick(all, true, false, CanvasLookup.Pending, motion))
        assertNull(pick(videos, true, false, CanvasLookup.Pending, motion))
    }

    @Test
    fun `the default is the canvas, else the animated cover, never the still one`() {
        val default = StageArt.DEFAULT
        assertEquals(StagePicture.Canvas(canvas), pick(default, true, false, found, motion))
        assertEquals(StagePicture.Animated(motion), pick(default, true, false, CanvasLookup.None, motion))
        assertNull(pick(default, true, false, CanvasLookup.None, null))
    }

    @Test
    fun `the old cover stage switch is the animated cover, else the still one`() {
        val old = StageArt.COVER_STAGE_ON
        assertEquals(StagePicture.Animated(motion), pick(old, true, false, found, motion))
        assertEquals(StagePicture.Animated(motion), pick(old, true, false, CanvasLookup.Pending, motion))
        assertEquals(StagePicture.Still, pick(old, true, false, CanvasLookup.None, null))
    }

    @Test
    fun `cover art alone ignores both videos`() {
        assertEquals(StagePicture.Still, pick(setOf(StageArt.CoverArt), true, false, found, motion))
        assertEquals(StagePicture.Still, pick(setOf(StageArt.CoverArt), true, false, CanvasLookup.Pending, motion))
    }

    @Test
    fun `the animated cover needs Animated Cover on, the canvas doesn't`() {
        assertEquals(StagePicture.Canvas(canvas), pick(all, false, false, found, motion))
        assertEquals(StagePicture.Still, pick(all, false, false, CanvasLookup.None, motion))
        assertNull(pick(setOf(StageArt.AnimatedCover), false, false, found, motion))
    }

    @Test
    fun `low performance mode keeps both videos off`() {
        for (lookup in listOf(found, CanvasLookup.None, CanvasLookup.Pending)) {
            assertEquals("$lookup", StagePicture.Still, pick(all, true, true, lookup, motion))
        }
        assertFalse(wantsCanvas(all, lowPerformance = true))
        assertFalse(wantsAnimated(all, animatedCover = true, lowPerformance = true))
    }

    @Test
    fun `lookups run only for what's picked`() {
        assertTrue(wantsCanvas(setOf(StageArt.Canvas), false))
        assertTrue(wantsCanvas(StageArt.DEFAULT, false))
        assertFalse(wantsCanvas(StageArt.COVER_STAGE_ON, false))
        assertTrue(wantsAnimated(setOf(StageArt.AnimatedCover), true, false))
        assertFalse(wantsAnimated(setOf(StageArt.Canvas, StageArt.CoverArt), true, false))
        assertFalse(wantsAnimated(setOf(StageArt.AnimatedCover), false, false))
    }

    @Test
    fun `stored in list order, unknown names dropped`() {
        assertEquals("Canvas,CoverArt", StageArt.store(setOf(StageArt.CoverArt, StageArt.Canvas)))
        assertEquals(setOf(StageArt.Canvas, StageArt.CoverArt), StageArt.parse("CoverArt, Canvas,Hologram"))
        assertEquals(emptySet<StageArt>(), StageArt.parse(""))
    }

    @Test
    fun `a video covers its box at its own shape`() {
        // A portrait canvas on a wide stage: as wide as the stage, taller than it.
        assertEquals(800 to 1423, coverSize(800, 900, 9f / 16f))
        // On a narrow, tall one: as tall as the stage.
        assertEquals(563 to 1000, coverSize(300, 1000, 9f / 16f))
        // A square loop: a square as big as the longer side.
        assertEquals(900 to 900, coverSize(600, 900, 1f))
        assertEquals(1200 to 1200, coverSize(1200, 700, 1f))
    }
}
