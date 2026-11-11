package com.tx24.spicyplayer.playback

import android.content.Context

/**
 * Spotify's Automix starts the next song while the last one is still playing, then reports it
 * from 0 once it has switched over, a few seconds (a different amount each song) behind what's
 * heard, and nothing later corrects it. Android has no other way to learn where the audio is, so
 * a moment into each new song the player is sent to where the lyrics are, as tapping a line does:
 * it answers with a report that matches its audio. The music jumps back by Automix's head start.
 *
 * With a Spotify account signed in ([com.tx24.spicyplayer.spotify.SpotifyAccount]) Spotify's own
 * servers are asked where the song is instead, and the lyrics move there with no jump; the seek is
 * left for when they can't say.
 *
 * Settings → Scroll & Sync → Song changes (on by default). The lyrics screen and the background
 * follower both schedule it; [claim] lets only the first one seek for each song.
 */
internal object AutomixResync {
    /** The player whose new songs are resynced. */
    const val PLAYER = "com.spotify.music"
    /** How long after a song change to resync: past Automix's switch, early in the song. */
    const val DELAY_MS = 2_000L
    /** The setting, in the "ui" preferences. */
    const val PREF = "resyncAfterSongChange"

    private var claimed: String? = null

    /** Smaller differences between Spotify's account and the lyrics are its own timing, not Automix. */
    const val MIN_OFFSET_MS = 250L
    /** Automix starts a song at most this far ahead; Spotify running behind the phone is its delay. */
    private val OFFSET_RANGE = -2_000L..20_000L

    /** How far to move the lyrics to [spotifyMs], from [lyricsMs]: 0 for its own jitter or an answer out of range. */
    fun offsetFrom(spotifyMs: Long, lyricsMs: Long): Long {
        val offset = spotifyMs - lyricsMs
        return if (offset in OFFSET_RANGE && kotlin.math.abs(offset) >= MIN_OFFSET_MS) offset else 0L
    }

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
