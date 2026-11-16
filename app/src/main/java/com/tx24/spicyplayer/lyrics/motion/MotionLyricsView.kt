package com.tx24.spicyplayer.lyrics.motion

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Whether [lines] play as motion lyrics; otherwise the usual lyrics are shown. */
internal fun motionLyricsFit(enabled: Boolean, lines: List<Line>, type: LyricsType): Boolean =
    enabled && MotionPlanner.canPlay(lines, type)

/**
 * Motion lyrics ([MotionPlanner]): the song as moving type, one scene a line, in the lyrics font.
 * It fills whatever it is given, upright or wide, and plans again when that shape changes, so
 * it serves the lyrics screen, the wallpaper, the overlay and the always-on display alike.
 * It draws white; the caller tints it as it does the usual lyrics.
 */
@Composable
internal fun MotionLyricsView(
    lines: List<Line>,
    documentId: String,
    currentTimeMs: () -> Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    romanize: Boolean = false,
    visible: () -> Boolean = { true },
    /** The most frames a second it draws; 0 for every frame. */
    maxFrameRate: Float = 0f,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val font = LyricsLayoutCalculator.spicyFontFamily
    val style = remember(font) { TextStyle(fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = REFERENCE_SP.sp, color = Color.White) }
    // Each word measured once, at one size: the camera does the scaling.
    val layouts = remember(documentId, style, romanize) { HashMap<String, TextLayoutResult>() }
    // To a tenth: a few pixels' change of shape (a bar hiding) isn't worth a new plan.
    val aspect = if (size.height > 0) ((size.width.toFloat() / size.height) * 10f).roundToInt() / 10f else 0f
    val plan = remember(lines, documentId, aspect, romanize, layouts) {
        if (aspect <= 0f) {
            null
        } else {
            MotionPlanner.plan(lines, aspect.coerceIn(0.3f, 3.5f), documentId.hashCode().toLong(), romanize) { text ->
                val layout = layouts.getOrPut(text) { measurer.measure(text, style, softWrap = false, maxLines = 1) }
                layout.size.width.toFloat() / layout.size.height.coerceAtLeast(1)
            }
        }
    }

    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, maxFrameRate) {
        val gapNanos = if (maxFrameRate > 0f) (1_000_000_000L / maxFrameRate).toLong() else 0L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (visible() && now - last >= gapNanos) {
                    last = now
                    frame = now
                }
            }
            // Paused, nothing moves: look now and then for a seek.
            if (!isPlaying) delay(250L)
        }
    }

    Canvas(modifier.onSizeChanged { size = it }) {
        // Reading the frame draws again every frame.
        if (frame < 0L) return@Canvas
        val current = plan ?: return@Canvas
        if (current.scenes.isEmpty()) return@Canvas
        drawMotion(current, layouts, currentTimeMs())
    }
}

private const val REFERENCE_SP = 48f
/** A word lands this much bigger than it rests. */
private const val SLAM = 0.4f
private const val SLAM_MS = 240f
private const val APPEAR_MS = 110f
/** Sung words dim to this, so the one being sung stands out. */
private const val SUNG_ALPHA = 0.6f
private const val DIM_MS = 260f

private fun DrawScope.drawMotion(plan: MotionPlan, layouts: Map<String, TextLayoutResult>, timeMs: Long) {
    val pose = MotionCamera.poseAt(plan, timeMs)
    val pixelsPerUnit = size.width / pose.span
    val stage = plan.sceneIndexAt(timeMs)
    if (stage < 0) return
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        rotate(-pose.angle, Offset.Zero)
        scale(pixelsPerUnit, pixelsPerUnit, Offset.Zero)
        translate(-pose.x, -pose.y)
    }) {
        // The scene on stage, and the ones before it that are still fading out.
        var index = stage
        while (index >= 0 && index >= stage - 2) {
            val scene = plan.scenes[index]
            val fade = if (index == stage && timeMs < plan.fadeStartMs(index)) {
                1f
            } else {
                1f - (timeMs - plan.fadeStartMs(index)).toFloat() / MotionPlan.FADE_MS
            }
            if (fade > 0.01f) drawScene(scene, layouts, timeMs, fade.coerceAtMost(1f))
            index--
        }
    }
}

private fun DrawScope.drawScene(scene: MotionScene, layouts: Map<String, TextLayoutResult>, timeMs: Long, fade: Float) {
    val active = scene.activeIndex(timeMs)
    if (active < 0) return
    withTransform({
        translate(scene.centerX, scene.centerY)
        rotate(scene.angle, Offset.Zero)
    }) {
        val first = if (scene.solo) active else 0
        for (i in first..active) {
            val word = scene.words[i]
            val layout = layouts[word.text] ?: continue
            val age = (timeMs - word.startMs).toFloat()
            val appear = (age / APPEAR_MS).coerceIn(0f, 1f)
            val landed = (age / SLAM_MS).coerceIn(0f, 1f)
            // Out fast, settling slowly: the word hits and rests.
            val slam = 1f + SLAM * (1f - landed).let { it * it * it }
            val sung = if (i < active) SUNG_ALPHA else 1f - (1f - SUNG_ALPHA) * ((timeMs - word.endMs) / DIM_MS).coerceIn(0f, 1f)
            val alpha = fade * appear * if (scene.solo) 1f else sung
            if (alpha <= 0.01f) continue
            // The measured word is this many pixels tall; a unit is one word height at scale 1.
            val unit = word.scale / layout.size.height.coerceAtLeast(1)
            withTransform({
                translate(word.centerX, word.centerY)
                scale(unit * slam, unit * slam, Offset.Zero)
            }) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f),
                    alpha = alpha,
                )
            }
        }
    }
}
