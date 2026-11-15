package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.animation.BloomMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Per-line scroll positions. The list snaps to its goal and the rows follow in sequence: each on a
 * critically damped spring a moment after the row above it, or, timed, all for the same time on
 * one curve, the rows below the line setting off a little later each.
 */
internal class RippleScroll {
    private data class Row(var p: Float, var v: Float = 0f, var goal: Float = p,
        var next: Float = p, var wait: Float = 0f,
        /** A timed row runs from [from] to its goal over [dur] s, starting at [at] on the clock; 0 for none. */
        var from: Float = p, var at: Float = 0f, var dur: Float = 0f)
    private var rows = emptyArray<Row>()
    private var scroll = 0f
    private var target: Int? = null
    /** Seconds since the first update, for the timed rows. */
    private var clock = 0f

    fun clear(y: Float) {
        rows = emptyArray()
        scroll = y
        target = null
    }

    /** [timedMs] above 0 moves the rows on the timed curve for that long instead of the spring. */
    fun update(from: Float, to: Float, targetIndex: Int?, tops: FloatArray, heights: FloatArray,
        anchor: Float, viewport: Float, dt: Float, snap: Boolean, userScrolling: Boolean, timedMs: Int = 0): Boolean {
        val elapsed = dt.coerceIn(0f, 0.1f)
        clock += elapsed
        if (snap || abs(to - from) > viewport * 0.7f) {
            clear(to)
            rows = Array(tops.size) { Row(to) }
            target = targetIndex
            return false
        }
        if (rows.size != tops.size) rows = Array(tops.size) { Row(from) }
        if (userScrolling) rows.forEach { it.p += to - scroll; it.dur = 0f }
        if (abs(to - scroll) >= 0.01f && timedMs > 0 && !userScrolling) {
            // Every row sets off from where it's drawn now; a correction of the same move only
            // moves the goal of the rows already under way.
            val changed = targetIndex != target
            rows.forEachIndexed { i, row ->
                row.v = 0f
                row.wait = 0f
                row.goal = to
                row.next = to
                if (!changed && row.dur > 0f) return@forEachIndexed
                row.from = row.p
                row.at = clock + BloomMotion.rowDelayMs(if (targetIndex == null) 0 else i - targetIndex) / 1000f
                row.dur = timedMs / 1000f
            }
            target = targetIndex
        } else if (abs(to - scroll) >= 0.01f) {
            var delay = 0f
            var step = 0.042f
            val changed = targetIndex != target
            rows.forEachIndexed { i, row ->
                row.next = to
                if (userScrolling || anchor + tops[i] + heights[i] + from <= 0f) {
                    row.goal = to
                    row.wait = 0f
                } else if (changed) {
                    row.wait = delay
                    if (delay == 0f) row.goal = to
                    delay += step
                    if (i >= (targetIndex ?: 0)) step *= 0.9f
                } else if (row.wait == 0f) row.goal = to
            }
            target = targetIndex
        }
        scroll = to
        var active = false
        val w = (2 * PI * 2.1).toFloat()
        rows.forEach { row ->
            if (userScrolling) { row.goal = to; row.wait = 0f }
            if (row.dur > 0f) {
                val u = ((clock - row.at) / row.dur).coerceIn(0f, 1f)
                row.p = row.from + (row.goal - row.from) * BloomMotion.SCROLL_EASE.transform(u)
                if (u >= 1f && row.goal == to) { row.p = to; row.dur = 0f } else active = true
                return@forEach
            }
            if (row.wait > 0f) {
                row.wait = (row.wait - elapsed).coerceAtLeast(0f)
                if (row.wait == 0f) row.goal = row.next
            }
            val q = exp(-w * elapsed)
            val o = row.p - row.goal
            val k = row.v + w * o
            row.p = (o + k * elapsed) * q + row.goal
            row.v = (row.v - w * k * elapsed) * q
            if (row.wait == 0f && row.goal == to && abs(row.p - to) < 0.2f && abs(row.v) < 4f) {
                row.p = to; row.v = 0f
            } else active = true
        }
        return active
    }

    fun offset(index: Int): Float = rows.getOrNull(index.coerceAtMost(rows.lastIndex))?.let { it.p - scroll } ?: 0f
}
