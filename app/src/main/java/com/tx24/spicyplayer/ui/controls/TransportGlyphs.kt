package com.tx24.spicyplayer.ui.controls

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.util.lerp

/**
 * The skip glyph, two triangles, moving on like Apple Music's: at [progress] 0 to 1 the front
 * triangle shrinks away ahead, the back one slides into its place, and a new one grows in behind.
 * At 0 and 1 it's the plain glyph. Drawn, not recomposed: [progress] is read while drawing.
 */
internal class SkipGlyph(private val progress: () -> Float) : Painter() {
    override val intrinsicSize = Size(WIDTH, HEIGHT)

    override fun DrawScope.onDraw() {
        val p = progress()
        val unit = size.width / WIDTH
        scale(unit, unit, pivot = Offset.Zero) {
            // At rest, the real glyph.
            if (p == 0f) {
                drawPath(GLYPH, Color.White)
                return@scale
            }
            triangle(x = lerp(0f, HALF, p), scale = 1f, alpha = 1f)
            triangle(x = HALF + LEAVE_SHIFT * p, scale = 1f - p, alpha = 1f - p)
            if (p > 0f) triangle(x = 0f, scale = p, alpha = p)
        }
    }

    /** One triangle at [x] in the glyph's 35 x 20 frame, scaled about its own centre. */
    private fun DrawScope.triangle(x: Float, scale: Float, alpha: Float) {
        if (scale <= 0f || alpha <= 0f) return
        translate(left = x) {
            scale(scale, scale, pivot = Offset(TRIANGLE_WIDTH / 2f, HEIGHT / 2f)) {
                drawPath(TRIANGLE, Color.White.copy(alpha = alpha))
            }
        }
    }

    private companion object {
        const val WIDTH = 35f
        const val HEIGHT = 20f
        const val HALF = 17.475f
        const val TRIANGLE_WIDTH = 17.9f
        /** How far the front triangle drifts while it shrinks away. */
        const val LEAVE_SHIFT = 7f
        val GLYPH: Path = PathParser().parsePathString(
            "M 19.467 19.905 C 20.008 19.905 20.463 19.746 21.005 19.426 L 33.61 12.023 C 34.533 11.482 35 10.817 35 9.993 " +
                "C 35 9.158 34.545 8.53 33.61 7.977 L 21.005 0.574 C 20.463 0.254 19.998 0.094 19.456 0.094 C 18.374 0.094 " +
                "17.475 0.917 17.475 2.418 L 17.475 9.49 C 17.315 8.898 16.873 8.408 16.135 7.977 L 3.529 0.574 C 3 0.254 " +
                "2.533 0.094 1.993 0.094 C 0.911 0.094 0 0.917 0 2.418 L 0 17.582 C 0 19.083 0.91 19.906 1.993 19.906 " +
                "C 2.533 19.906 3 19.746 3.529 19.426 L 16.135 12.023 C 16.861 11.593 17.315 11.088 17.475 10.485 L 17.475 17.582 " +
                "C 17.475 19.083 18.386 19.906 19.467 19.906 L 19.467 19.905 Z",
        ).toPath()
        /** The play triangle: the same rounded shape the skip glyph is two of. */
        val TRIANGLE: Path = PathParser().parsePathString(
            "M 1.558 20 C 2.006 20 2.381 19.838 2.874 19.561 L 16.622 11.572 C 17.527 11.053 17.894 10.65 17.894 9.997 " +
                "C 17.894 9.35 17.527 8.948 16.622 8.419 L 2.874 0.439 C 2.381 0.153 2.006 0 1.558 0 C 0.706 0 0.106 0.654 " +
                "0.106 1.694 L 0.106 18.298 C 0.106 19.346 0.706 20 1.558 20 L 1.558 20 Z",
        ).toPath()
    }
}

/**
 * Play turning into pause and back: the triangle's two halves each become a bar. [pause] runs
 * from 0 (play) to 1 (pause). Each shape keeps the size the plain glyphs are drawn at, so the
 * frame is the pause glyph's (15 x 20) and the play triangle sits in it as it always has.
 */
internal class PlayPauseGlyph(private val pause: () -> Float) : Painter() {
    override val intrinsicSize = Size(PAUSE_WIDTH, HEIGHT)
    private val paint = Paint()
    private val path = Path()

    override fun DrawScope.onDraw() {
        val t = pause().coerceIn(0f, 1f)
        val w = size.width
        // The pause glyph fills the frame; the play glyph is drawn at the same width, so smaller.
        val pauseUnit = w / PAUSE_WIDTH
        val playUnit = w / PLAY_WIDTH
        val playTop = (size.height - HEIGHT * playUnit) / 2f
        // At rest, the real glyphs: the morph's halves would show a seam down the triangle.
        if (t == 0f) {
            translate(top = playTop) { scale(playUnit, playUnit, pivot = Offset.Zero) { drawPath(PLAY_PATH, Color.White) } }
            return
        }
        if (t == 1f) {
            scale(pauseUnit, pauseUnit, pivot = Offset.Zero) { drawPath(PAUSE_PATH, Color.White) }
            return
        }
        paint.color = Color.White
        paint.pathEffect = PathEffect.cornerPathEffect(CORNER * lerp(playUnit, pauseUnit, t))
        drawIntoCanvas { canvas ->
            for (half in 0..1) {
                path.reset()
                for (corner in 0..3) {
                    val play = PLAY[half][corner]
                    val bar = PAUSE[half][corner]
                    val x = lerp(play.x * playUnit, bar.x * pauseUnit, t)
                    val y = lerp(playTop + play.y * playUnit, bar.y * pauseUnit, t)
                    if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private companion object {
        const val PLAY_WIDTH = 18f
        const val PAUSE_WIDTH = 15f
        const val HEIGHT = 20f
        const val CORNER = 1.6f
        val PLAY_PATH: Path = PathParser().parsePathString(
            "M 1.558 20 C 2.006 20 2.381 19.838 2.874 19.561 L 16.622 11.572 C 17.527 11.053 17.894 10.65 17.894 9.997 " +
                "C 17.894 9.35 17.527 8.948 16.622 8.419 L 2.874 0.439 C 2.381 0.153 2.006 0 1.558 0 C 0.706 0 0.106 0.654 " +
                "0.106 1.694 L 0.106 18.298 C 0.106 19.346 0.706 20 1.558 20 L 1.558 20 Z",
        ).toPath()
        val PAUSE_PATH: Path = PathParser().parsePathString(
            "M 4.427 19.963 C 5.513 19.963 6.06 19.416 6.06 18.33 L 6.06 1.66 C 6.06 0.545 5.513 0.037 4.427 0.037 L 1.633 0.037 " +
                "C 0.548 0.037 0 0.575 0 1.66 L 0 18.331 C -0.009 19.416 0.538 19.963 1.633 19.963 L 4.427 19.963 Z M 13.377 19.963 " +
                "C 14.462 19.963 15 19.416 15 18.33 L 15 1.66 C 15 0.545 14.462 0.037 13.376 0.037 L 10.573 0.037 C 9.487 0.037 " +
                "8.949 0.575 8.949 1.66 L 8.949 18.331 C 8.949 19.416 9.487 19.963 10.573 19.963 L 13.376 19.963 L 13.377 19.963 Z",
        ).toPath()
        /**
         * The triangle split down the middle, each half as four corners: top left, top right,
         * bottom right, bottom left. The left half reaches a little past the middle, so the two
         * overlap and no seam shows while they move.
         */
        val PLAY = arrayOf(
            arrayOf(Offset(0.1f, 0f), Offset(9.6f, 5.4f), Offset(9.6f, 14.6f), Offset(0.1f, 20f)),
            arrayOf(Offset(9f, 5.1f), Offset(17.9f, 10f), Offset(17.9f, 10f), Offset(9f, 14.9f)),
        )
        /** The two bars, corner for corner with the halves above. */
        val PAUSE = arrayOf(
            arrayOf(Offset(0f, 0f), Offset(6.06f, 0f), Offset(6.06f, 20f), Offset(0f, 20f)),
            arrayOf(Offset(8.95f, 0f), Offset(15f, 0f), Offset(15f, 20f), Offset(8.95f, 20f)),
        )
    }
}
