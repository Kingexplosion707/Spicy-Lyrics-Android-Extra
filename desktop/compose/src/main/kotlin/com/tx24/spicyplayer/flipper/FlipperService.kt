package com.tx24.spicyplayer.flipper

import android.content.Context
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel

/**
 * Lyrics on a Flipper Zero plugged into the computer by USB, sent to the Spicy Lyrics app on it
 * ([FlipperUsbLink]) from what the app's window plays, and the Flipper's buttons play, pause and skip.
 *
 * Settings → Device → Flipper Zero. The wallpaper leaves the Flipper to the app.
 */
object FlipperService {
    /** The `ui` setting behind Settings → Device → Flipper Zero. */
    const val PREF_ENABLED = "flipperZero"

    /** How this computer reaches the Flipper, for the setting. */
    const val HOW = "Show the lyrics over USB on a Flipper Zero running Spicy Lyrics (Apps > Media), with playback on its buttons."

    val permissions: Array<String> = emptyArray()

    /** What the window plays, given at start. */
    var playback: ExternalPlaybackViewModel? = null

    private var relay: FlipperRelay? = null

    fun supported(context: Context): Boolean = !DesktopPlatform.wallpaper
    fun permitted(context: Context): Boolean = true

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

    /** Starts the link while it's on, stops it otherwise. */
    fun sync(context: Context) {
        if (DesktopPlatform.wallpaper) return
        val player = playback ?: return
        if (enabled(context)) {
            if (relay != null) return
            relay = FlipperRelay(
                link = FlipperUsbLink(),
                snapshot = {
                    val state = player.state.value
                    FlipperFeed.Snapshot(
                        title = state.title.takeIf { state.sourcePackage != null },
                        artist = state.artist,
                        durationMs = state.durationMs,
                        playing = state.isPlaying,
                        positionMs = player.currentLyricPositionMs(),
                        lyrics = state.lyrics as? LyricsState.Ready,
                        settled = state.lyrics !is LyricsState.Loading,
                    )
                },
                controls = { command ->
                    when (command) {
                        FlipperProtocol.Command.PlayPause -> player.playPause()
                        FlipperProtocol.Command.Next -> player.skipNext()
                        FlipperProtocol.Command.Previous -> player.skipPrevious()
                    }
                },
            ).also { it.start() }
        } else {
            relay?.stop()
            relay = null
        }
    }
}
