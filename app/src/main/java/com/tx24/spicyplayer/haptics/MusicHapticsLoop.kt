package com.tx24.spicyplayer.haptics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Vibrate this much ahead of the beat: the motor takes about as long to spin up. */
private const val LEAD_MS = 15L
/** A pulse this late (after a stall) still plays; later ones are dropped. */
private const val LATE_MS = 40L
/** The longest single wait, so a seek or a song change is noticed quickly. */
private const val STEP_MS = 200L
/** Continuous rumble: a low burst this often, as in HapticBeat. */
private const val CONTINUOUS_MS = 150L

/**
 * Plays the song's [score] against the lyric clock ([positionMs]) until cancelled: waits for each
 * pulse and plays it at [strength] times its own. Seeks and song changes need no signal; the clock
 * is read again before every pulse.
 *
 * [continuous] is HapticBeat's Continuous rumble: no score, a steady low burst while the music
 * plays, re-issued every [CONTINUOUS_MS].
 */
suspend fun playMusicHaptics(
    player: HapticPlayer,
    score: () -> List<MusicHaptic>?,
    positionMs: () -> Long,
    strength: () -> Float,
    continuous: Boolean = false,
) = withContext(Dispatchers.Default) {
    if (continuous) {
        while (true) {
            player.playRumble(strength().coerceAtMost(1f))
            delay(CONTINUOUS_MS)
        }
    }
    var played: List<MusicHaptic>? = null
    var lastAt = -1L
    while (true) {
        val events = score()
        if (events.isNullOrEmpty()) { delay(STEP_MS); continue }
        if (events !== played) { played = events; lastAt = -1L }
        val now = positionMs() + LEAD_MS
        // Sought back: the pulses there play again.
        if (now < lastAt - STEP_MS) lastAt = -1L
        val next = firstFrom(events, maxOf(now - LATE_MS, lastAt + 1))
        val event = events.getOrNull(next)
        if (event == null) { delay(STEP_MS); continue }
        val wait = event.atMs - now
        if (wait > STEP_MS / 4) { delay(minOf(wait - 10, STEP_MS)); continue }
        if (wait > 0) delay(wait)
        player.play(event.pulse, (event.strength * strength()).coerceAtMost(1f), event.roomMs)
        lastAt = event.atMs
    }
}

/** The index of the first pulse at or after [ms], or the list's size. */
private fun firstFrom(events: List<MusicHaptic>, ms: Long): Int {
    var lo = 0
    var hi = events.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (events[mid].atMs < ms) lo = mid + 1 else hi = mid
    }
    return lo
}
