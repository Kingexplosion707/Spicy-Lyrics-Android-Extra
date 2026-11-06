package com.tx24.spicyplayer.lyrics.spicy.canvas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/** Per-line scroll positions. The list snaps to its goal and rows spring there in sequence. */
internal class RippleScroll {
    private data class Row(var p: Float, var v: Float = 0f, var goal: Float = p,
        var next: Float = p, var wait: Float = 0f)
    private var rows = emptyArray<Row>()
    private var scroll = 0f
    private var target: Int? = null

    fun clear(y: Float) {
        rows = emptyArray()
        scroll = y
        target = null
    }

    fun update(from: Float, to: Float, targetIndex: Int?, tops: FloatArray, heights: FloatArray,
        anchor: Float, viewport: Float, dt: Float, snap: Boolean, userScrolling: Boolean): Boolean {
        if (snap || abs(to - from) > viewport * 0.7f) {
            clear(to)
            rows = Array(tops.size) { Row(to) }
            target = targetIndex
            return false
        }
        if (rows.size != tops.size) rows = Array(tops.size) { Row(from) }
        if (userScrolling) rows.forEach { it.p += to - scroll }
        if (abs(to - scroll) >= 0.01f) {
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
        val elapsed = dt.coerceIn(0f, 0.1f)
        rows.forEach { row ->
            if (userScrolling) { row.goal = to; row.wait = 0f }
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
