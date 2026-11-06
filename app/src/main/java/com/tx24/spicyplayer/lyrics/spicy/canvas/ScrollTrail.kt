package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.animation.AppleMusicMotion
import kotlin.math.abs

/**
 * The scroll's recent positions, so a line can follow the same move a little later: the Apple
 * Music style sets each line past the active one off [AppleMusicMotion.STAGGER_S] after the one
 * before it, up to [AppleMusicMotion.STAGGER_STEPS] lines, like a CSS transition delay.
 *
 * The lines' own shifts (a background vocal folding or an interlude opening) are kept with each
 * position: a fold above the active line moves every line below it as the scroll makes up for it,
 * so a late line has to take both from the same moment or it jumps ahead and back.
 */
internal class ScrollTrail {
    private val times = LongArray(CAPACITY)
    private val positions = FloatArray(CAPACITY)
    /** Each frame's line shifts; the published arrays never change, so these are shared, not copied. */
    private val shifts = arrayOfNulls<FloatArray>(CAPACITY)
    /** When the line shifts last changed, so the late lines are known to still be catching up. */
    private var shiftsChangedNanos: Long? = null
    private var count = 0
    private var head = 0

    /**
     * Which way the view is going: -1 as the lyrics go up (forward), 1 as they come back down. Set
     * by the line scrolled to when it changes; a scroll to a later line can still back up for a
     * moment (making up for a background vocal folding above it), and flipping the delays to the
     * lines above then would snap the late lines below to the view and back.
     */
    var direction = -1
        private set

    /** The line the moves are measured from: the one scrolled to. */
    var reference = 0
        set(value) {
            if (value != field) {
                direction = if (value > field) -1 else 1
                referenceChanged = true
            }
            field = value
        }
    private var referenceChanged = false
    /** When the line scrolled to last changed; until the scroll there has settled, it sets the direction. */
    private var referenceChangedNanos: Long? = null

    /** Each line's place in the stagger: background vocals move with their lead, not a step after it. */
    var places = IntArray(0)

    fun record(timeNanos: Long, y: Float, lineShifts: FloatArray) {
        if (referenceChanged) {
            referenceChanged = false
            referenceChangedNanos = timeNanos
        }
        if (count > 0) {
            val previous = (head - 1 + CAPACITY) % CAPACITY
            val last = positions[previous]
            // A move of the view's own (a drag) says which way once the scroll to a line has settled.
            val settled = referenceChangedNanos?.let { timeNanos - it > SETTLE_NANOS } ?: true
            if (settled && abs(y - last) > 0.01f) direction = if (y < last) -1 else 1
            if (shifts[previous] !== lineShifts) shiftsChangedNanos = timeNanos
        }
        times[head] = timeNanos
        positions[head] = y
        shifts[head] = lineShifts
        head = (head + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    fun clear() {
        count = 0
        head = 0
        shifts.fill(null)
        shiftsChangedNanos = null
        referenceChangedNanos = null
    }

    /** Where the view was at [timeNanos], between the frames recorded around it. */
    fun at(timeNanos: Long): Float = sample(timeNanos, 0f) { positions[it] }

    /** Line [index]'s shift at [timeNanos], or [now] when no frame then knows that line. */
    fun shiftAt(timeNanos: Long, index: Int, now: Float): Float = sample(timeNanos, now) { slot ->
        shifts[slot]?.takeIf { index < it.size }?.get(index) ?: return now
    }

    /** [value] of the frames recorded around [timeNanos], blended between them. */
    private inline fun sample(timeNanos: Long, empty: Float, value: (Int) -> Float): Float {
        if (count == 0) return empty
        var newer = (head - 1 + CAPACITY) % CAPACITY
        if (timeNanos >= times[newer]) return value(newer)
        for (i in 1 until count) {
            val older = (newer - 1 + CAPACITY) % CAPACITY
            if (timeNanos >= times[older]) {
                val span = (times[newer] - times[older]).coerceAtLeast(1L)
                val f = (timeNanos - times[older]).toFloat() / span
                val from = value(older)
                return from + (value(newer) - from) * f
            }
            newer = older
        }
        return value(newer)
    }

    /** How long after the view [lineIndex] moves. */
    fun delayNanos(lineIndex: Int): Long {
        val line = places.getOrElse(lineIndex) { lineIndex }
        val target = places.getOrElse(reference) { reference }
        val steps = if (direction < 0) line - target else target - line
        return steps.coerceIn(0, AppleMusicMotion.STAGGER_STEPS) * (AppleMusicMotion.STAGGER_S * 1e9f).toLong()
    }

    /** True while a line that follows late is still behind the view. */
    fun catchingUp(nowNanos: Long): Boolean {
        if (count == 0) return false
        if (shiftsChangedNanos?.let { nowNanos - it < MAX_DELAY_NANOS } == true) return true
        val y = at(nowNanos)
        return abs(at(nowNanos - MAX_DELAY_NANOS) - y) > 0.25f
    }

    private companion object {
        /** Enough frames for the longest delay at 120 fps. */
        const val CAPACITY = 64
        val MAX_DELAY_NANOS = (AppleMusicMotion.STAGGER_STEPS * AppleMusicMotion.STAGGER_S * 1e9f).toLong()
        /** Longer than a scroll to the next line takes, with the last late line behind it. */
        const val SETTLE_NANOS = 1_500_000_000L
    }
}
