package com.tx24.spicyplayer.lyrics.motion

import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Motion lyrics: word-synced lyrics as moving type. Each line is a scene of big words set out in
 * one of a few compositions; the words land as they are sung, and a camera travels from scene to
 * scene, turning with the type. This file plans it (where every word sits, where the camera is at
 * any moment) with no drawing in it, so it runs the same for every surface and in plain tests.
 *
 * Sizes are in "units": one unit is the height of a word at scale 1.
 */

/** How a scene sets out its words. */
enum class MotionLayout {
    /** Rows stretched to one width, so short rows are huge and long rows small. */
    Poster,
    /** Rows of one size, centred. */
    Column,
    /** Rows stepping down and to the right. */
    Stairs,
    /** Big and small rows, pushed to alternate sides. */
    Zigzag,
    /** One word at a time, filling the view. */
    Hero,
    /** One long row the camera reads along. */
    Strip,
}

/** A word in its scene: its top-left corner from the scene's centre, and its size. */
data class MotionWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val x: Float,
    val y: Float,
    val scale: Float,
    val width: Float,
) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + scale / 2f
}

/** One line's composition, placed in the world at ([centerX], [centerY]) and turned by [angle] degrees. */
data class MotionScene(
    val layout: MotionLayout,
    val startMs: Long,
    val endMs: Long,
    val words: List<MotionWord>,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angle: Float,
) {
    /** Only the word being sung shows. */
    val solo: Boolean get() = layout == MotionLayout.Hero
    /** The camera stays close and moves with the word being sung. */
    val follow: Boolean get() = layout == MotionLayout.Strip

    /** The word being sung at [timeMs] (the last one begun), or -1 before the first. */
    fun activeIndex(timeMs: Long): Int {
        var index = -1
        for (i in words.indices) if (words[i].startMs <= timeMs) index = i else break
        return index
    }
}

/** Where the camera looks: the world point in the middle of the view, how many units the view is wide, and its turn. */
data class MotionPose(val x: Float, val y: Float, val span: Float, val angle: Float)

class MotionPlan(val scenes: List<MotionScene>, val aspect: Float) {
    /** The scene on stage at [timeMs]: the last one begun, or -1 before the first. */
    fun sceneIndexAt(timeMs: Long): Int {
        var low = 0
        var high = scenes.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (scenes[mid].startMs <= timeMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    /** When scene [index] starts to fade: as the next one begins, or a moment after its own end. */
    fun fadeStartMs(index: Int): Long {
        val scene = scenes[index]
        val next = scenes.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
        return min(next, scene.endMs + LINGER_MS)
    }

    companion object {
        const val LINGER_MS = 1_500L
        const val FADE_MS = 450L
    }
}

object MotionPlanner {
    private const val WORD_GAP = 0.28f

    /**
     * Whether [lines] can play as motion lyrics: only word-synced lyrics can, since the words must
     * land with the singing. A song timed by the line, or mostly so, is turned down.
     */
    fun canPlay(lines: List<Line>, type: LyricsType): Boolean {
        if (type != LyricsType.Syllable) return false
        val lead = lines.filter { it.role == LineRole.LEAD && it.words.isNotEmpty() }
        if (lead.size < 2) return false
        val timed = lead.count { !it.lineTimed && !it.untimed && !it.translationReplaces }
        return timed * 10 >= lead.size * 7
    }

    /**
     * Plans [lines] for a view [aspect] wide for each unit of height. [ratio] gives a word's width
     * for a height of one unit in the font it will be drawn in. [seed] varies the compositions
     * from song to song while keeping one song the same every time.
     */
    fun plan(lines: List<Line>, aspect: Float, seed: Long, romanize: Boolean, ratio: (String) -> Float): MotionPlan {
        val random = Random(seed)
        val targetChars = when {
            aspect < 0.8f -> 9
            aspect < 1.3f -> 13
            else -> 18
        }
        val scenes = ArrayList<MotionScene>()
        var lastLayout: MotionLayout? = null
        var x = 0f
        var y = 0f
        var angle = 0f
        var lastDirection = -1
        var lastRadius = 0f
        for (line in lines) {
            if (line.role != LineRole.LEAD) continue
            val tokens = tokens(line, romanize)
            if (tokens.isEmpty()) continue
            val layout = pickLayout(tokens.size, lastLayout, random)
            lastLayout = layout
            val placed = centre(place(layout, tokens, targetChars, aspect, ratio))
            val width = placed.maxOf { it.x + it.width } - placed.minOf { it.x }
            val height = placed.maxOf { it.y + it.scale } - placed.minOf { it.y }
            val radius = hypot(width, height) / 2f
            if (scenes.isNotEmpty()) {
                // Travel somewhere new each time, never straight back the way the camera came.
                var direction = random.nextInt(8)
                if (lastDirection >= 0 && direction == (lastDirection + 4) % 8) direction = (direction + 1 + random.nextInt(3)) % 8
                lastDirection = direction
                val heading = direction * PI / 4.0
                val distance = (lastRadius + radius) * 1.15f + 1f
                x += (cos(heading) * distance).toFloat()
                y += (sin(heading) * distance).toFloat()
                angle += TURNS[random.nextInt(TURNS.size)]
            }
            lastRadius = radius
            scenes += MotionScene(
                layout = layout,
                startMs = tokens.first().startMs,
                endMs = max(line.endMs, tokens.last().endMs),
                words = placed,
                centerX = x,
                centerY = y,
                width = width,
                height = height,
                angle = angle,
            )
        }
        return MotionPlan(scenes, aspect)
    }

    /** Mostly a straight cut; now and then a tilt or a quarter turn, which the camera follows. */
    private val TURNS = floatArrayOf(0f, 0f, 0f, 0f, 8f, -8f, 14f, -14f, 90f, -90f)

    private class Token(val text: String, val startMs: Long, val endMs: Long)

    /** The line's whole words: syllables glued to the one before are joined to it. */
    private fun tokens(line: Line, romanize: Boolean): List<Token> {
        val out = ArrayList<Token>()
        for (word in line.words) {
            val text = (if (romanize) word.romanizedText ?: word.text else word.text)
            val last = out.lastOrNull()
            if (word.isPartOfWord && last != null && !text.startsWith(" ")) {
                out[out.lastIndex] = Token(last.text + text.trim(), last.startMs, max(last.endMs, word.endMs))
            } else if (text.isNotBlank()) {
                out += Token(text.trim(), word.startMs, word.endMs)
            }
        }
        return out
    }

    private fun pickLayout(words: Int, last: MotionLayout?, random: Random): MotionLayout {
        val fitting = when {
            words <= 2 -> listOf(MotionLayout.Hero, MotionLayout.Column, MotionLayout.Poster)
            words <= 4 -> listOf(MotionLayout.Hero, MotionLayout.Poster, MotionLayout.Stairs, MotionLayout.Column, MotionLayout.Zigzag)
            words <= 8 -> listOf(MotionLayout.Poster, MotionLayout.Stairs, MotionLayout.Column, MotionLayout.Zigzag, MotionLayout.Strip)
            else -> listOf(MotionLayout.Poster, MotionLayout.Column, MotionLayout.Zigzag, MotionLayout.Strip)
        }.filter { it != last }
        return fitting[random.nextInt(fitting.size)]
    }

    /** Words into rows of about [targetChars] characters, never splitting a word. */
    private fun rows(tokens: List<Token>, targetChars: Int): List<List<Token>> {
        val rows = ArrayList<MutableList<Token>>()
        var chars = 0
        for (token in tokens) {
            val length = token.text.length
            if (rows.isEmpty() || (chars > 0 && chars + 1 + length > targetChars)) {
                rows += ArrayList<Token>()
                chars = 0
            }
            rows.last() += token
            chars += (if (chars > 0) 1 else 0) + length
        }
        return rows
    }

    private fun rowWidth(row: List<Token>, ratio: (String) -> Float): Float =
        row.sumOf { ratio(it.text).toDouble() }.toFloat() + WORD_GAP * (row.size - 1)

    /** Sets one row at ([left], [top]) at [scale]. */
    private fun MutableList<MotionWord>.row(row: List<Token>, left: Float, top: Float, scale: Float, ratio: (String) -> Float) {
        var cursor = left
        for (token in row) {
            val width = ratio(token.text) * scale
            add(MotionWord(token.text, token.startMs, token.endMs, cursor, top, scale, width))
            cursor += width + WORD_GAP * scale
        }
    }

    private fun place(
        layout: MotionLayout,
        tokens: List<Token>,
        targetChars: Int,
        aspect: Float,
        ratio: (String) -> Float,
    ): List<MotionWord> {
        val out = ArrayList<MotionWord>()
        when (layout) {
            MotionLayout.Hero -> for (token in tokens) {
                // Every word the same width, so short words are tall: each fills the view by itself.
                val unit = ratio(token.text).coerceAtLeast(0.2f)
                val scale = ((if (aspect < 1f) 4.2f else 7f) / unit).coerceIn(1f, 3f)
                out += MotionWord(token.text, token.startMs, token.endMs, -unit * scale / 2f, -scale / 2f, scale, unit * scale)
            }
            MotionLayout.Strip -> out.row(tokens, 0f, 0f, 1.2f, ratio)
            else -> {
                val rows = rows(tokens, targetChars)
                val widths = rows.map { rowWidth(it, ratio).coerceAtLeast(0.2f) }
                val block = widths.sorted()[widths.size / 2]
                var top = 0f
                rows.forEachIndexed { index, row ->
                    when (layout) {
                        MotionLayout.Poster -> {
                            val scale = (block / widths[index]).coerceIn(0.55f, 2.6f)
                            out.row(row, 0f, top, scale, ratio)
                            top += scale * 1.02f
                        }
                        MotionLayout.Stairs -> {
                            out.row(row, index * 0.9f, top, 1f, ratio)
                            top += 1.1f
                        }
                        MotionLayout.Zigzag -> {
                            val scale = if (index % 2 == 0) 1.4f else 0.85f
                            val widest = widths.max() * 1.4f
                            val left = if (index % 2 == 0) 0f else widest - widths[index] * scale
                            out.row(row, left, top, scale, ratio)
                            top += scale * 1.08f
                        }
                        else -> {
                            out.row(row, -widths[index] / 2f, top, 1f, ratio)
                            top += 1.15f
                        }
                    }
                }
            }
        }
        return out
    }

    /** Moves the words so the middle of their bounds is the scene's centre. */
    private fun centre(words: List<MotionWord>): List<MotionWord> {
        val dx = (words.minOf { it.x } + words.maxOf { it.x + it.width }) / 2f
        val dy = (words.minOf { it.y } + words.maxOf { it.y + it.scale }) / 2f
        return words.map { it.copy(x = it.x - dx, y = it.y - dy) }
    }
}

object MotionCamera {
    /** The camera sets off for a scene this long before its first word. */
    const val LEAD_MS = 220L
    private const val MARGIN = 1.3f

    /** Where the camera is at [timeMs]: on the scene on stage, or on its way there from the one before. */
    fun poseAt(plan: MotionPlan, timeMs: Long): MotionPose {
        val scenes = plan.scenes
        if (scenes.isEmpty()) return MotionPose(0f, 0f, 10f, 0f)
        val index = plan.sceneIndexAt(timeMs + LEAD_MS).coerceAtLeast(0)
        val scene = scenes[index]
        val here = poseOf(scene, timeMs, plan.aspect)
        if (index == 0) return here
        val before = scenes[index - 1]
        val departed = scene.startMs - LEAD_MS
        val travel = ((scene.startMs - before.startMs) / 2).coerceIn(350L, 800L)
        val progress = ((timeMs - departed).toFloat() / travel).coerceIn(0f, 1f)
        if (progress >= 1f) return here
        val from = poseOf(before, timeMs, plan.aspect)
        val eased = ease(progress)
        // Pull back in the middle of the trip, so a long one reads as a move and not a blur.
        val lift = 1f + 0.25f * sin(PI.toFloat() * eased)
        return MotionPose(
            x = lerp(from.x, here.x, eased),
            y = lerp(from.y, here.y, eased),
            span = lerp(from.span, here.span, eased) * lift,
            angle = lerp(from.angle, here.angle, eased),
        )
    }

    /** The camera's place on [scene] alone at [timeMs]. */
    fun poseOf(scene: MotionScene, timeMs: Long, aspect: Float): MotionPose {
        val least = if (aspect < 1f) 4.5f else 9f
        var span = max(max(scene.width * MARGIN, scene.height * MARGIN * aspect), least)
        val length = (scene.endMs - scene.startMs).coerceAtLeast(1L)
        val progress = ((timeMs - scene.startMs).toFloat() / length).coerceIn(0f, 1f)
        // A slow push in while the line is sung.
        span *= 1.05f - 0.1f * progress
        val focus = focus(scene, timeMs)
        val pull = if (scene.follow) 1f else 0.18f
        if (scene.follow) span = min(span, if (aspect < 1f) 5.5f else 10f)
        val radians = scene.angle * PI.toFloat() / 180f
        val localX = focus.first * pull
        val localY = focus.second * pull
        return MotionPose(
            x = scene.centerX + localX * cos(radians) - localY * sin(radians),
            y = scene.centerY + localX * sin(radians) + localY * cos(radians),
            span = span,
            angle = scene.angle,
        )
    }

    /** The point being sung, from the scene's centre: it glides from each word to the next. */
    private fun focus(scene: MotionScene, timeMs: Long): Pair<Float, Float> {
        val words = scene.words
        val index = scene.activeIndex(timeMs)
        if (index < 0) return words.first().let { it.centerX to it.centerY }
        val word = words[index]
        val next = words.getOrNull(index + 1) ?: return word.centerX to word.centerY
        val glide = (next.startMs - word.startMs).coerceAtLeast(1L)
        val along = ease(((timeMs - word.startMs).toFloat() / glide).coerceIn(0f, 1f))
        return lerp(word.centerX, next.centerX, along) to lerp(word.centerY, next.centerY, along)
    }

    fun ease(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
