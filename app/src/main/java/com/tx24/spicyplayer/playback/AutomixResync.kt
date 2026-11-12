package com.tx24.spicyplayer.playback

import android.content.Context

/**
 * After a song change Spotify can report a position that isn't where its audio is, and keep to it
 * for the whole song: measured on a Pixel, a second or two either way, and with Automix (which
 * starts the next song under the last one, on the beat) a whole number of beats. Android has no
 * other way to learn where the audio is, so a moment into each new song the player is sent to
 * where it says it is, as tapping a line does: it answers with a report that matches its audio.
 * The music skips by however far off the player was.
 *
 * Settings → Scroll & Sync → Song changes (on by default). The lyrics screen and the background
 * follower both schedule it; [claim] lets only the first one seek for each song. While it's on,
 * [ListeningSync] rests: the player is right after the seek.
 */
internal object AutomixResync {
    /** The player whose new songs are resynced. */
    const val PLAYER = "com.spotify.music"
    /** How long after a song change to resync: past Automix's switch, early in the song. */
    const val DELAY_MS = 2_000L
    /** A resync that can't go yet (the player still loading) is tried again this much later, a few times. */
    const val RETRY_MS = 1_500L
    const val MAX_RETRIES = 4
    /** The setting, in the "ui" preferences. */
    const val PREF = "resyncAfterSongChange"

    /**
     * How far past the lyrics' position to send the player: about as long as it takes to answer
     * ([measuredLatencyMs], its last answer), so that a player that was right carries on from
     * where it would have been and only its short gap is heard.
     */
    fun seekLeadMs(measuredLatencyMs: Long?): Long = (measuredLatencyMs ?: 200L).coerceIn(100L, 400L)

    private var claimed: String? = null

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, true)

    /** The song's key across the app: the player, title and artist. */
    fun songKey(player: String, title: String?, artist: String?): String = "$player|$title|$artist"

    /** Whether the caller is the one to resync [songKey]: true once per song, for the first to ask. */
    @Synchronized
    fun claim(songKey: String): Boolean {
        if (claimed == songKey) return false
        claimed = songKey
        return true
    }
}
