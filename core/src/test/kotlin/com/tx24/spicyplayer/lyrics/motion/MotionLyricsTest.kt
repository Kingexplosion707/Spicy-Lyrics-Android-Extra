package com.tx24.spicyplayer.lyrics.motion

import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionLyricsTest {
    /** Every letter half as wide as it is tall. */
    private val ratio: (String) -> Float = { it.length * 0.5f }

    private fun line(startMs: Long, vararg words: String, role: LineRole = LineRole.LEAD, lineTimed: Boolean = false): Line {
        var at = startMs
        val timed = words.map { text ->
            val glued = text.startsWith("+")
            Word(text.removePrefix("+"), at, at + 300, isPartOfWord = glued).also { at += 400 }
        }
        return Line(timed, startMs, at, role = role, lineTimed = lineTimed)
    }

    private val song = listOf(
        line(1_000, "Never", "gonna", "give", "you", "up"),
        line(4_000, "Never", "gonna", "let", "you", "down"),
        line(7_000, "Never", "gonna", "run", "around", "and", "desert", "you", "for", "ever", "more"),
        line(12_000, "Ooh"),
    )

    private fun plan(aspect: Float = 0.5f, seed: Long = 7) = MotionPlanner.plan(song, aspect, seed, romanize = false, ratio = ratio)

    @Test fun `only word-synced lyrics can play`() {
        assertTrue(MotionPlanner.canPlay(song, LyricsType.Syllable))
        assertFalse(MotionPlanner.canPlay(song, LyricsType.Line))
        assertFalse(MotionPlanner.canPlay(song, LyricsType.Static))
        val byLine = song.map { it.copy(lineTimed = true) }
        assertFalse(MotionPlanner.canPlay(byLine, LyricsType.Syllable))
        assertFalse(MotionPlanner.canPlay(song.take(1), LyricsType.Syllable))
    }

    @Test fun `every lead line is a scene with all its words in time order`() {
        val plan = plan()
        assertEquals(4, plan.scenes.size)
        plan.scenes.zip(song).forEach { (scene, line) ->
            assertEquals(line.words.map { it.text }, scene.words.map { it.text })
            assertEquals(line.startMs, scene.startMs)
            assertTrue(scene.words.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        }
    }

    @Test fun `background vocals and breaks are left out`() {
        val lines = song + line(13_000, "(yeah)", role = LineRole.BACKGROUND) + line(14_000, "...", role = LineRole.INTERLUDE)
        assertEquals(4, MotionPlanner.plan(lines, 0.5f, 7, false, ratio).scenes.size)
    }

    @Test fun `glued syllables are one word`() {
        val plan = MotionPlanner.plan(listOf(line(0, "to", "+geth", "+er", "now"), line(3_000, "a", "b")), 0.5f, 1, false, ratio)
        assertEquals(listOf("together", "now"), plan.scenes[0].words.map { it.text })
        assertEquals(1_100L, plan.scenes[0].words[0].endMs)
    }

    @Test fun `a scene's words sit round its centre and inside its bounds`() {
        for (seed in 0L..30L) for (scene in plan(seed = seed).scenes) {
            val left = scene.words.minOf { it.x }
            val right = scene.words.maxOf { it.x + it.width }
            val top = scene.words.minOf { it.y }
            val bottom = scene.words.maxOf { it.y + it.scale }
            assertEquals(0f, (left + right) / 2f, 0.001f)
            assertEquals(0f, (top + bottom) / 2f, 0.001f)
            assertEquals(scene.width, right - left, 0.001f)
            assertEquals(scene.height, bottom - top, 0.001f)
        }
    }

    @Test fun `words on a row never overlap`() {
        for (seed in 0L..30L) for (scene in plan(seed = seed).scenes.filterNot { it.solo }) {
            scene.words.groupBy { it.y }.values.forEach { row ->
                row.sortedBy { it.x }.zipWithNext().forEach { (a, b) -> assertTrue(a.x + a.width <= b.x + 0.001f) }
            }
        }
    }

    @Test fun `one song plans the same every time, and no layout repeats back to back`() {
        assertEquals(plan(seed = 3).scenes, plan(seed = 3).scenes)
        for (seed in 0L..30L) plan(seed = seed).scenes.zipWithNext().forEach { (a, b) -> assertTrue(a.layout != b.layout) }
    }

    @Test fun `the view holds the whole scene, upright or wide`() {
        for (aspect in listOf(0.46f, 1f, 2.1f)) for (seed in 0L..10L) {
            val plan = plan(aspect, seed)
            for (scene in plan.scenes.filterNot { it.follow }) {
                val pose = MotionCamera.poseOf(scene, scene.startMs, aspect)
                assertTrue(pose.span >= scene.width)
                assertTrue(pose.span / aspect >= scene.height)
            }
        }
    }

    @Test fun `the camera is on a scene by its first word and turns with it`() {
        val plan = plan()
        plan.scenes.drop(1).forEach { scene ->
            // Settled once the trip is over: the same as the scene's own pose.
            val late = scene.startMs + 700
            val settled = MotionCamera.poseOf(scene, late, plan.aspect)
            val pose = MotionCamera.poseAt(plan, late)
            assertEquals(settled.x, pose.x, 0.01f)
            assertEquals(settled.angle, pose.angle, 0.01f)
        }
    }

    @Test fun `the camera moves without jumps`() {
        val plan = plan()
        var last = MotionCamera.poseAt(plan, 0)
        val reach = plan.scenes.maxOf { it.width + it.height } * 4f
        for (time in 0L..14_000L step 8) {
            val pose = MotionCamera.poseAt(plan, time)
            assertTrue("jump at $time", abs(pose.x - last.x) < reach / 8f && abs(pose.y - last.y) < reach / 8f)
            assertTrue("turn at $time", abs(pose.angle - last.angle) < 12f)
            last = pose
        }
    }

    @Test fun `scenes are found by time and fade as the next begins`() {
        val plan = plan()
        assertEquals(-1, plan.sceneIndexAt(999))
        assertEquals(0, plan.sceneIndexAt(1_000))
        assertEquals(1, plan.sceneIndexAt(6_999))
        assertEquals(3, plan.sceneIndexAt(60_000))
        assertEquals(4_000L, plan.fadeStartMs(0))
        assertEquals(plan.scenes[3].endMs + MotionPlan.LINGER_MS, plan.fadeStartMs(3))
        assertEquals(1, plan.scenes[0].activeIndex(1_450))
        assertEquals(-1, plan.scenes[0].activeIndex(0))
    }
}
