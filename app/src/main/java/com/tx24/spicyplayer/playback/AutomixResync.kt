package com.tx24.spicyplayer.playback

import android.content.Context

/**
 * Spotify's Automix starts the next song while the last one is still playing, then reports it
 * from 0 once it has switched over, a few seconds (a different amount each song) behind what's
 * heard, and nothing later corrects it. Android has no other way to learn where the audio is, so
 * a moment into each new song the player is sent to where the lyrics are, as tapping a line does:
 * it answers with a report that matches its audio. The music jumps back by Automix's head start.
 *
 * Settings → Sources → Resync after a song change (on by default). The lyrics screen and the
 * background follower both schedule it; [claim] lets only the first one seek for each song.
 */
internal object AutomixResync {
    /** The player whose new songs are resynced. */
    const val PLAYER = "com.spotify.music"
    /** How long after a song change to resync: past Automix's switch, early in the song. */
    const val DELAY_MS = 2_000L
    /** The setting, in the "ui" preferences. */
    const val PREF = "resyncAfterSongChange"

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
