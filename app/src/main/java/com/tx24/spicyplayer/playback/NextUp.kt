package com.tx24.spicyplayer.playback

/** Up Next: the last stretch of a song shows the one queued after it. */
object NextUp {
    /** The card shows once this little of the song is left. */
    const val WINDOW_MS = 30_000L

    /** The entry after the playing one, or null when nothing is marked playing or nothing follows. */
    fun nextEntry(queue: List<QueueEntry>): QueueEntry? {
        val at = queue.indexOfFirst { it.current }
        return if (at < 0) null else queue.getOrNull(at + 1)
    }

    /** Whether [positionMs] of a [durationMs] song falls in the last [windowMs]. An unknown length never does. */
    fun due(positionMs: Long, durationMs: Long, windowMs: Long = WINDOW_MS): Boolean =
        durationMs > 0L && positionMs >= 0L && durationMs - positionMs in 0..windowMs
}
