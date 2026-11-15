package com.tx24.spicyplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.Group
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyPalette
import kotlinx.coroutines.launch

/*
 * The floating buttons' glyphs, and the same glyphs wherever those buttons' actions show up
 * (quick settings, the Settings rows that turn the buttons on). All of them sit on one 24-unit
 * grid, stroked 2 units wide with round caps and joins, no fills, ink centred on 12,12. Parts move
 * over 0.2 to 0.3 s, easing out: a change of state slides or turns the part that shows it, and
 * what a pointer resting on the button would do happens while it's held, since phones have no
 * hover. [SpicyGlyphs] has each glyph's rest pose as a plain [ImageVector] for rows that take one.
 */

private fun nodes(path: String) = PathParser().parsePathString(path).toNodes()

private object Paths {
    val Gear = nodes("M10.07 5.69L10.48 3.38A8.75 8.75 0 0 1 13.52 3.38L13.93 5.69A6.6 6.6 0 0 1 15.1 6.17L17.02 4.83A8.75 8.75 0 0 1 19.17 6.98L17.83 8.9A6.6 6.6 0 0 1 18.31 10.07L20.62 10.48A8.75 8.75 0 0 1 20.62 13.52L18.31 13.93A6.6 6.6 0 0 1 17.83 15.1L19.17 17.02A8.75 8.75 0 0 1 17.02 19.17L15.1 17.83A6.6 6.6 0 0 1 13.93 18.31L13.52 20.62A8.75 8.75 0 0 1 10.48 20.62L10.07 18.31A6.6 6.6 0 0 1 8.9 17.83L6.98 19.17A8.75 8.75 0 0 1 4.83 17.02L6.17 15.1A6.6 6.6 0 0 1 5.69 13.93L3.38 13.52A8.75 8.75 0 0 1 3.38 10.48L5.69 10.07A6.6 6.6 0 0 1 6.17 8.9L4.83 6.98A8.75 8.75 0 0 1 6.98 4.83L8.9 6.17A6.6 6.6 0 0 1 10.07 5.69Z")
    val Hub = nodes("M14.75 12a2.75 2.75 0 1 1-5.5 0a2.75 2.75 0 1 1 5.5 0Z")

    // Four rounded corners at the edges, each with the way it nudges: outward to show the
    // cover, toward the middle (the same corners turned inside out) to go back.
    val ExpandCorners = listOf(
        nodes("M3.5 8.5V6A2.5 2.5 0 0 1 6 3.5h2.5") to Offset(-1f, -1f),
        nodes("M15.5 3.5H18A2.5 2.5 0 0 1 20.5 6v2.5") to Offset(1f, -1f),
        nodes("M20.5 15.5V18a2.5 2.5 0 0 1-2.5 2.5h-2.5") to Offset(1f, 1f),
        nodes("M8.5 20.5H6A2.5 2.5 0 0 1 3.5 18v-2.5") to Offset(-1f, 1f),
    )
    val CollapseCorners = listOf(
        nodes("M8.5 3.5V6A2.5 2.5 0 0 1 6 8.5H3.5") to Offset(1f, 1f),
        nodes("M20.5 8.5H18A2.5 2.5 0 0 1 15.5 6V3.5") to Offset(-1f, 1f),
        nodes("M15.5 20.5V18a2.5 2.5 0 0 1 2.5-2.5h2.5") to Offset(-1f, -1f),
        nodes("M3.5 15.5H6A2.5 2.5 0 0 1 8.5 18v2.5") to Offset(1f, -1f),
    )

    /** The app's screen. */
    val Screen = nodes("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z")

    // The cover on one side and the arrow to the other, no frame round them.
    val Cover = nodes("M5 8h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2Z")
    val Arrow = nodes("M14.5 12H21m-3-3 3 3-3 3")

    /** The floating window, in the screen's bottom-right corner. */
    val Window = nodes("M12.5 11h3a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-2a1.5 1.5 0 0 1 1.5-1.5Z")

    /** Two arcs chasing each other round the middle; a half turn draws them the same again. */
    val Resync = nodes(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8M21 3v5h-5" +
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16M8 16H3v5",
    )

    // A lyric sheet in front of another.
    val BackSheet = nodes("M8.5 3H17a2.5 2.5 0 0 1 2.5 2.5v11")
    val FrontSheet = nodes(
        "M6.5 7.5h6A2.5 2.5 0 0 1 15 10v8.5a2.5 2.5 0 0 1-2.5 2.5h-6A2.5 2.5 0 0 1 4 18.5V10a2.5 2.5 0 0 1 2.5-2.5Z" +
            "M7.5 12.5h4M7.5 16.5h4",
    )

    // 文 and A, one path each.
    val Han = nodes("M7 3v2.5M2.5 5.5h9M9.5 5.5c-.5 3-2.5 5.5-6 7M5 5.5c1 3 3 5.5 6.5 7")
    val Latin = nodes("M12.5 21 17 10l4.5 11M14 17.5h6")

    // A list of sources, one of them picked.
    val SourceOthers = nodes("M6 6a.5 .5 0 1 1-1 0a.5 .5 0 1 1 1 0ZM6 18a.5 .5 0 1 1-1 0a.5 .5 0 1 1 1 0ZM11.5 6h9M11.5 18h9")
    val SourceChosen = nodes("M8 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0ZM11.5 12h9")

    // The songs to come, and the play mark for the one up next.
    val QueueLines = nodes("M3.5 6h8.5M3.5 12h8.5M3.5 18h17")
    val QueuePlay = nodes("M16 4.5v7l5-3.5Z")

    // A phone, and the two arcs that turn it.
    val Phone = nodes("M10.5 5.5h3a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-3A2.5 2.5 0 0 1 8 16V8a2.5 2.5 0 0 1 2.5-2.5Z")
    val TurnArcs = nodes(
        "M14.46 2.82A9.5 9.5 0 0 1 21.18 9.54M19.54 8.39 21.18 9.54 22.03 7.73" +
            "M9.54 21.18A9.5 9.5 0 0 1 2.82 14.46M4.46 15.61 2.82 14.46 1.97 16.27",
    )

    val MoreDots = listOf(
        nodes("M7 12a1 1 0 1 1-2 0a1 1 0 1 1 2 0Z") to -1f,
        nodes("M13 12a1 1 0 1 1-2 0a1 1 0 1 1 2 0Z") to 0f,
        nodes("M19 12a1 1 0 1 1-2 0a1 1 0 1 1 2 0Z") to 1f,
    )

    /**
     * Two slider tracks with their knobs at [top] and [bottom] along x. Each track stops short of
     * its knob, so the ring reads as a knob rather than a bead threaded on a line.
     */
    fun sliders(top: Float, bottom: Float): List<PathNode> = PathData {
        track(8f, top)
        track(16f, bottom)
    }

    private fun PathBuilder.track(y: Float, knob: Float) {
        moveTo(3f, y); lineTo(knob - 4.5f, y)
        moveTo(knob + 4.5f, y); lineTo(21f, y)
        moveTo(knob + 2f, y)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = knob - 2f, y1 = y)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = knob + 2f, y1 = y)
        close()
    }

    const val TOP_KNOB = 8.5f
    const val BOTTOM_KNOB = 15.5f
}

// Each romanize button is one glyph, the script a tap switches to: a kana takes the lyrics back to
// their own script, and a Latin A under a macron, the mark romanized text is full of, romanizes them.
private const val KANA = "M6.5 8h10M11.5 4.5c-.6 4.5-.3 10.5 1 15" +
    "M16 11c-1.5 4.25-4.75 7.5-7.5 7.5-1.5 0-2.5-1-2.5-2.5 0-2.5 3-4.5 7-4.5 3.5 0 5.75 1.75 5.75 4.25 0 2-1.5 3.5-4.25 4"
private const val MACRON_A = "M6 20.5 12 7.5l6 13M8.5 16h7M8 3.5h8"

internal val GlyphEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Each glyph's rest pose, for rows and menus that take a plain [ImageVector]. */
internal object SpicyGlyphs {
    val Resync = glyph(Paths.Resync)
    val Expand = glyph(*Paths.ExpandCorners.map { it.first }.toTypedArray())
    val QuickSettings = glyph(Paths.sliders(Paths.TOP_KNOB, Paths.BOTTOM_KNOB))
    val Rotate = glyph(Paths.Phone, Paths.TurnArcs)
    val PictureInPicture = glyph(Paths.Screen, Paths.Window)
    val LyricsManager = glyph(Paths.BackSheet, Paths.FrontSheet)
    val Queue = glyph(Paths.QueueLines, Paths.QueuePlay)
    val Source = glyph(Paths.SourceOthers, Paths.SourceChosen)
    val DisableRomanization = glyph(nodes(KANA))
    val EnableRomanization = glyph(nodes(MACRON_A))

    private fun glyph(vararg paths: List<PathNode>): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { path ->
                    addPath(
                        path,
                        stroke = SolidColor(SpicyPalette.Neutral.TextPrimary),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}

@Composable
private fun lineGlyph(content: @Composable (Float, Float) -> Unit) =
    rememberVectorPainter(24.dp, 24.dp, 24f, 24f, content = content)

@Composable
private fun LinePath(nodes: List<PathNode>) = Path(
    nodes,
    stroke = SolidColor(SpicyPalette.Neutral.TextPrimary),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
)

/** 0 at rest, 1 while the button is held, over [ms]. */
@Composable
private fun heldProgress(ms: Int, label: String): Float {
    val held by animateFloatAsState(if (LocalGlyphPressed.current) 1f else 0f, tween(ms, easing = GlyphEase), label = label)
    return held
}

/** The gear, turning an eighth while its button is held. */
@Composable
internal fun SettingsGlyph(modifier: Modifier) {
    val turn = 45f * heldProgress(300, "gear")
    Image(lineGlyph { _, _ ->
        Group(rotation = turn, pivotX = 12f, pivotY = 12f) { LinePath(Paths.Gear) }
        LinePath(Paths.Hub)
    }, null, modifier)
}

/** Corners out to show the cover, in to go back; each nudges its own way while held. */
@Composable
internal fun ExpandGlyph(expanded: Boolean, modifier: Modifier) {
    val nudge = heldProgress(200, "corners")
    val corners = if (expanded) Paths.CollapseCorners else Paths.ExpandCorners
    Image(lineGlyph { _, _ ->
        corners.forEach { (nodes, way) ->
            Group(translationX = way.x * nudge, translationY = way.y * nudge) { LinePath(nodes) }
        }
    }, null, modifier)
}

/**
 * The cover on the side it's on now and an arrow to the other: when the cover moves, it slides
 * across and the arrow turns to point back.
 */
@Composable
internal fun SwapSideGlyph(coverOnRight: Boolean, modifier: Modifier) {
    val side by animateFloatAsState(if (coverOnRight) 1f else 0f, tween(250, easing = GlyphEase), label = "swapSide")
    Image(lineGlyph { _, _ ->
        Group(translationX = 10f * side) { LinePath(Paths.Cover) }
        Group(rotation = 180f * side, pivotX = 17.75f, pivotY = 12f, translationX = -11.5f * side) { LinePath(Paths.Arrow) }
    }, null, modifier)
}

/**
 * The arcs make a half turn each time the button goes down. A half turn lands on the same
 * drawing, so they only ever turn forward, and a quick tap still finishes its turn.
 */
@Composable
internal fun ResyncGlyph(modifier: Modifier) {
    val pressed = LocalGlyphPressed.current
    val turn = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pressed) {
        if (pressed) scope.launch { turn.animateTo(turn.targetValue + 180f, tween(300, easing = GlyphEase)) }
    }
    Image(lineGlyph { _, _ ->
        Group(rotation = turn.value % 360f, pivotX = 12f, pivotY = 12f) { LinePath(Paths.Resync) }
    }, null, modifier)
}

/** 文 and A, each leaning a unit toward the other while held. */
@Composable
internal fun TranslateGlyph(modifier: Modifier) {
    val lean = heldProgress(200, "translate")
    Image(lineGlyph { _, _ ->
        Group(translationX = lean, translationY = lean) { LinePath(Paths.Han) }
        Group(translationX = -lean, translationY = -lean) { LinePath(Paths.Latin) }
    }, null, modifier)
}

/** The front lyric sheet slides a unit out of the stack while held. */
@Composable
internal fun LyricsManagerGlyph(modifier: Modifier) {
    val pull = heldProgress(200, "lyricsManager")
    Image(lineGlyph { _, _ ->
        LinePath(Paths.BackSheet)
        Group(translationX = -pull, translationY = pull) { LinePath(Paths.FrontSheet) }
    }, null, modifier)
}

/** The list of sources; the picked one steps a unit forward while held. */
@Composable
internal fun SourceGlyph(modifier: Modifier) {
    val step = heldProgress(200, "source")
    Image(lineGlyph { _, _ ->
        LinePath(Paths.SourceOthers)
        Group(translationX = step) { LinePath(Paths.SourceChosen) }
    }, null, modifier)
}

/** The songs to come; the play mark nudges a unit forward while held. */
@Composable
internal fun QueueGlyph(modifier: Modifier) {
    val nudge = heldProgress(200, "queue")
    Image(lineGlyph { _, _ ->
        LinePath(Paths.QueueLines)
        Group(translationX = nudge) { LinePath(Paths.QueuePlay) }
    }, null, modifier)
}

/** The screen with the floating window in its corner, which nudges further into it while held. */
@Composable
internal fun PictureInPictureGlyph(modifier: Modifier) {
    val nudge = heldProgress(200, "pip")
    Image(lineGlyph { _, _ ->
        LinePath(Paths.Screen)
        Group(translationX = nudge, translationY = nudge) { LinePath(Paths.Window) }
    }, null, modifier)
}

/** Two sliders whose knobs slide toward each other while held. */
@Composable
internal fun QuickSettingsGlyph(modifier: Modifier) {
    val slide = 2f * heldProgress(250, "sliders")
    Image(lineGlyph { _, _ ->
        LinePath(Paths.sliders(Paths.TOP_KNOB + slide, Paths.BOTTOM_KNOB - slide))
    }, null, modifier)
}

/**
 * The phone stands upright, or lies on its side once the lyrics are turned to landscape, and
 * turns a quarter between the two. While held it leans a little toward the way it'll turn.
 */
@Composable
internal fun RotateGlyph(rotated: Boolean, modifier: Modifier) {
    val side by animateFloatAsState(if (rotated) 90f else 0f, tween(300, easing = GlyphEase), label = "rotate")
    val lean = 12f * heldProgress(200, "rotateLean") * (if (rotated) -1f else 1f)
    Image(lineGlyph { _, _ ->
        LinePath(Paths.TurnArcs)
        Group(rotation = side + lean, pivotX = 12f, pivotY = 12f) { LinePath(Paths.Phone) }
    }, null, modifier)
}

/** Three dots, the outer two stepping a unit apart while held. */
@Composable
internal fun MoreGlyph(modifier: Modifier) {
    val spread = heldProgress(200, "more")
    Image(lineGlyph { _, _ ->
        Paths.MoreDots.forEach { (nodes, way) ->
            Group(translationX = way * spread) { LinePath(nodes) }
        }
    }, null, modifier)
}
