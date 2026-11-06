package com.tx24.spicyplayer.playback

/**
 * Spaces out lyric lookups that reach the network in quick succession. A lookup that reaches the
 * network within [windowMs] of the previous one waits [backoffMs] first; the caller cancels that
 * wait when a newer track replaces it, so fast skipping sends fewer requests to the sources.
 */
class RapidSwitchGate(
    private val windowMs: Long,
    private val backoffMs: Long,
) {
    private var lastNetworkAt: Long? = null

    /**
     * Records a lookup reaching the network at [nowMs] (a monotonic clock) and returns how long it
     * should wait first. [force] (a retry the user asked for) never waits.
     */
    @Synchronized
    fun waitBeforeNetwork(nowMs: Long, force: Boolean = false): Long {
        val previous = lastNetworkAt
        lastNetworkAt = nowMs
        if (force || previous == null) return 0L
        return if (nowMs - previous < windowMs) backoffMs else 0L
    }
}
