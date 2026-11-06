package com.tx24.spicyplayer.playback

/**
 * Whether to stay on the media session already followed. A player that only remote-controls
 * another device (Spotify Connect to a speaker) drops out of playing now and then, while another
 * session for the same speaker (the phone's Cast controls) plays on. Switching on each drop reset
 * the lyric clock every time, so the followed session is kept while it plays or is between states,
 * and for [graceMs] after it stops.
 *
 * Plain Kotlin, no Android: the times are `elapsedRealtime`, passed in.
 */
internal class SessionStickiness(private val graceMs: Long) {
    private var stoppedAt: Long? = null

    /** Whether to keep the followed session, which is [active] (playing or between states) at [nowMs]. */
    fun keep(active: Boolean, nowMs: Long): Boolean {
        if (active) {
            stoppedAt = null
            return true
        }
        val since = stoppedAt ?: nowMs.also { stoppedAt = it }
        return nowMs - since < graceMs
    }

    /** How long until the grace runs out, to look again then; null when the session isn't stopped. */
    fun remainingMs(nowMs: Long): Long? = stoppedAt?.let { (graceMs - (nowMs - it)).coerceAtLeast(0L) }

    /** Another session (or none) is followed now. */
    fun reset() {
        stoppedAt = null
    }
}
