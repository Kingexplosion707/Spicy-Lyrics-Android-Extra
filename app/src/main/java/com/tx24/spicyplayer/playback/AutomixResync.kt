package com.tx24.spicyplayer.playback

import android.content.Context

/**
 * Spotify's Automix starts the next song while the last one is still playing, then reports it
 * from 0 once it has switched over, a few seconds (a different amount each song) behind what's
 * heard, and nothing later corrects it. Android has no other way to learn where the audio is, so
 * a moment into each new song the player is sent to where the lyrics are, as tapping a line does:
 * it answers with a report that matches its audio. The music jumps back by Automix's head start.
 *
 * A song that is being listened to ([ListeningSync]) is left alone: there the lyrics move to
 * the music instead.
 *
 * Settings → Scroll & Sync → Song changes (on by default). The lyrics screen and the background
 * follower both schedule it; [claim] lets only the first one seek for each song.
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

/**
 * Settings → Scroll & Sync → Crossfade: how far into a song that follows another on its own the
 * player's audio is when it reports the song. With a Spotify account signed in, the device Spotify
 * plays on decides whether it applies ([com.tx24.spicyplayer.spotify.SpotifyDevice]); a remote
 * session (a speaker through Connect) never crossfades.
 */
internal object CrossfadeLead {
    fun ms(context: Context): Long =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt(CROSSFADE_PREF, 0) * 1_000L

    /**
     * Whether the lead applies, as far as is known without waiting: not on a remote session, and
     * on a phone when the account last said where Spotify plays.
     */
    fun appliesNow(context: Context, relayed: Boolean): Boolean {
        if (relayed) return false
        if (!com.tx24.spicyplayer.spotify.SpotifyAccount.signedIn(context)) return true
        return com.tx24.spicyplayer.spotify.SpotifyDevice.lastOnPhone ?: true
    }
}
