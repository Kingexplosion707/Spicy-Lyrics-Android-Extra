package com.tx24.spicyplayer.lyrics.spicy.animation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Seek-safe envelopes. Times are milliseconds, lengths are the text's own font size (em). */
object RefreshedMotion {
    const val ALPHA = 1f
    const val GAP_EM = 0.62f
    /** The soft edge of the fill, for words and letters alike. */
    const val EDGE_EM = 0.55f
    const val WORD_EDGE_EM = EDGE_EM
    const val LETTER_EDGE_EM = EDGE_EM
    /** The space between words, when a line has no two on one row to measure it from. */
    const val DEFAULT_GAP_EM = 0.19f

    // The letters of a held word swell, spread, lift and glow one after another across the
    // first 2/5 of the word, then ease back, over the word's length but never under a second.
    private const val MIN_MS = 1000.0
    private const val STAGGER = 1.0 / 2.5
    private const val SCALE = 0.1
    private const val SPREAD = 0.03
    private const val LIFT = 0.025
    private const val MAX_AMOUNT = 1.2
    private const val RADIUS = 0.3
    private const val MAX_RADIUS = 0.3
    private const val MAX_BLUR = 0.8
    // A gentle float under the swell: how high (em, twice that on a background line), how far
    // ahead of its letter it starts, and how much longer it runs.
    private const val FLOAT = 0.05
    private const val FLOAT_LEAD_MS = 400.0
    private const val FLOAT_STRETCH = 1.4
    private const val DIM_ALPHA = 0.4

    private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoothstep(x: Double): Double = clamp(x).let { it * it * (3 - 2 * it) }
    private fun ease(x: Double) = 0.5 - 0.5 * cos(PI * clamp(x))

    /** How much a held word's letters move ([amount]), glow ([blur]) and how long it takes ([duration] ms). */
    data class Strength(val amount: Float, val blur: Float, val duration: Double)
    fun waveStrength(duration: Long, lastWord: Boolean): Strength {
        // Faint below two seconds (three for the glow), then growing slowly.
        fun grow(x: Double) = if (x > 1) sqrt(x) else x * x * x
        var ms = maxOf(MIN_MS, duration.toDouble())
        var amount = grow(ms / 2000) * 0.6
        var blur = grow(ms / 3000) * 0.5
        if (lastWord) {
            amount *= 1.6
            blur *= 1.5
            ms *= 1.2
        }
        return Strength(minOf(MAX_AMOUNT, amount).toFloat(), minOf(MAX_BLUR, blur).toFloat(), ms)
    }

    private val emphasisIn = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.4f, 0.58f, 1f)
    private val emphasisOut = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.58f, 1f)

    /** 0 to 1 and back over [x] from 0 to 1: up over the first half, down over the second. */
    fun emphasis(x: Double): Float {
        if (x <= 0 || x >= 1) return 0f
        return if (x < 0.5) emphasisIn.transform((x / 0.5).toFloat()) else 1f - emphasisOut.transform(((x - 0.5) / 0.5).toFloat())
    }

    /** A letter's pose, in em of its own text size. [glow] is the shadow's opacity. */
    data class LetterPose(val x: Float, val y: Float, val scale: Float, val glow: Float, val radius: Float)

    /**
     * Letter [index] of [count] at time [t]; [wordStart] is the first letter's start and [lit] how
     * far the fill has reached the letter (0..1), which the glow dims with.
     */
    fun letter(t: Double, wordStart: Long, index: Int, count: Int, strength: Strength, background: Boolean, lit: Float): LetterPose {
        val du = strength.duration
        val at = t - wordStart - du * STAGGER / maxOf(1, count) * index
        val swell = emphasis(at / du)
        val floatAt = (at + FLOAT_LEAD_MS) / (du * FLOAT_STRETCH)
        val float = if (floatAt > 0 && floatAt < 1) sin(floatAt * PI) * FLOAT * (if (background) 2 else 1) else 0.0
        val x = -swell * SPREAD * strength.amount * (maxOf(1, count) / 2.0 - index)
        val y = -swell * LIFT * strength.amount - float
        val glow = swell * strength.blur * (DIM_ALPHA + (1 - DIM_ALPHA) * lit)
        return LetterPose(
            x.toFloat(), y.toFloat(), (1 + swell * SCALE * strength.amount).toFloat(),
            glow.toFloat(), minOf(MAX_RADIUS, strength.blur * RADIUS).toFloat(),
        )
    }

    /**
     * A word floats up from the moment it starts, over its own length but never quicker than
     * [MIN_MS], easing out; 0 to 1.
     */
    fun wordFloat(t: Double, start: Long, end: Long): Float {
        val x = clamp((t - start) / maxOf(MIN_MS, (end - start).toDouble()))
        return (1 - (1 - x) * (1 - x)).toFloat()
    }

    /** The word's lift in lyric font sizes for a [float] of 0 to 1 (Simple's resting-to-lifted track). */
    fun wordOffset(float: Float): Float = 0.01f - 0.043f * float

    /** Where the fill's edge is for every word of a line, in px along the line laid end to end. */
    fun sweepEdges(
        x: FloatArray, w: FloatArray, starts: LongArray, ends: LongArray, fade: Float, t: Double,
    ): DoubleArray {
        val n = x.size
        val edges = DoubleArray(n) { Double.NEGATIVE_INFINITY }
        val first = w.indexOfFirst { it > 0f }
        val last = w.indexOfLast { it > 0f }
        if (first < 0) return edges
        val half = fade / 2.0
        var reached = Double.NEGATIVE_INFINITY
        for (i in 0 until n) {
            if (w[i] <= 0f) continue
            val from = if (i == first) x[i] - half else x[i].toDouble()
            val to = x[i] + w[i] + (if (i == last) half else 0.0)
            val at = when {
                t >= ends[i] -> to
                t > starts[i] -> from + (to - from) * (t - starts[i]) / (ends[i] - starts[i])
                else -> Double.NEGATIVE_INFINITY
            }
            edges[i] = at
            if (at > reached) reached = at
        }
        // A word being sung shows its own edge, so two words sung across each other both fill.
        // One still to come is lit only by the words before it; one already sung follows the
        // light wherever it is.
        var before = Double.NEGATIVE_INFINITY
        for (i in 0 until n) {
            if (w[i] <= 0f) continue
            val at = edges[i]
            if (t >= ends[i]) edges[i] = reached else if (t <= starts[i]) edges[i] = before
            if (at > before) before = at
        }
        return edges
    }

    /** The gradient position (-50 fully dim, 100 fully lit) of a box at [x], [width] wide, for an [edge]. */
    fun sweepPosition(edge: Double, x: Float, width: Float, fade: Float): Float {
        if (edge == Double.NEGATIVE_INFINITY) return -50f
        val rest = (width + fade / 2.0 - (edge - x)) / (width + fade)
        return (100.0 - 150.0 * rest).coerceIn(-50.0, 100.0).toFloat()
    }

    /** The dot's own third fills smoothly; its staggered entrance fades in from zero. */
    fun dotAlpha(elapsed: Double, duration: Long, index: Int): Float {
        val span = (duration - 550).coerceAtLeast(90) / 3.0
        val fill = ease((elapsed - index * span) / span)
        val enter = smoothstep((elapsed - index * 120) / 650)
        return ((0.22 + (0.92 - 0.22) * fill) * enter).toFloat()
    }
    fun dotBreath(elapsed: Double, duration: Long): Float {
        val room = duration - 500
        if (room < 2600) return 1f
        val cycles = kotlin.math.floor(room / 4200.0 + 0.5).coerceAtLeast(1.0)
        val phase = (elapsed / (room / cycles)).coerceIn(0.0, cycles)
        return (1 + 0.16 * (0.5 - 0.5 * cos(2 * PI * phase))).toFloat()
    }
}
