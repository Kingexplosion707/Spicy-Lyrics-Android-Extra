package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.animation.LineAnimState

/** A line as it is drawn: its animation state and where it sits. */
internal data class DrawnLine(val index: Int, val anim: LineAnimState, val scrollOffset: Float, val dynamicY: Float)

/** Everything a set of still lines is drawn from. Layouts compare by identity: a re-measure is new. */
internal class StillSet(
    private val layouts: List<LineLayout>,
    private val config: RenderConfig,
    private val width: Float,
    private val height: Float,
    val lines: List<DrawnLine>,
) {
    override fun equals(other: Any?) = other is StillSet && layouts === other.layouts && width == other.width &&
        height == other.height && config == other.config && lines == other.lines
    override fun hashCode() = lines.hashCode()
}

/**
 * Most lines don't change between line changes, yet every line was drawn again each frame. Lines
 * whose state didn't change since the last frame go into one offscreen layer, which is reused while
 * they stay the same; only the lines still moving are drawn each frame. A set of still lines is
 * recorded only once it has held for two frames, so a scroll or a line change, where everything
 * moves, draws directly instead of recording every frame.
 */
internal class StaticLines {
    enum class Plan { Direct, Record, Replay }

    private var previousStates: List<LineAnimState>? = null
    private var previousSet: StillSet? = null
    private var recorded: StillSet? = null

    /** Whether a line has to be drawn this frame: the active line, or one that changed since the last. */
    fun isLive(index: Int, anim: LineAnimState): Boolean =
        anim.isActive || previousStates?.getOrNull(index) != anim

    /** How to draw this frame's still lines; [recordedAs] must follow a [Plan.Record]. */
    fun plan(set: StillSet): Plan {
        val plan = when {
            set.lines.isEmpty() -> Plan.Direct
            set == recorded -> Plan.Replay
            set == previousSet -> Plan.Record
            else -> Plan.Direct
        }
        previousSet = set
        return plan
    }

    fun recordedAs(set: StillSet) {
        recorded = set
    }

    fun endFrame(states: List<LineAnimState>) {
        previousStates = states
    }
}
