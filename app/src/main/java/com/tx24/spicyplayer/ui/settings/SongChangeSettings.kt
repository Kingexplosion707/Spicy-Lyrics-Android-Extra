package com.tx24.spicyplayer.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.playback.ListeningSync
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.ToggleRow

/**
 * Scroll & Sync → Song changes: keeping the lyrics on time after a song change, when Spotify can
 * report a position that isn't where its audio is. Two ways, one at a time: the resync (a seek,
 * which always works and is heard) or listening (no seek, and it can't always tell).
 */
@Composable
internal fun SongChangeSection(settings: AppSettings) {
    SettingsSection("Song changes") {
        ToggleRow(
            label = "Resync after a song change",
            checked = settings.resyncAfterSongChange,
            onCheckedChange = { on ->
                settings.resyncAfterSongChange = on
                if (on) settings.listeningSync = false
            },
            description = "After a song change Spotify can say it's a second or two from where its audio is, and keep " +
                "to it for the whole song. Two seconds into each song, Spotify is sent to where it says it is, as " +
                "tapping a line does, and the lyrics are right from there. You hear a short skip.",
            icon = Icons.Rounded.Sync,
        )
        ListeningSyncRow(settings)
    }
}

/**
 * Listen to stay in sync ([ListeningSync]), in place of the resync: asks for Android's
 * record-audio permission when switched on, and stays off without it.
 */
@Composable
private fun ListeningSyncRow(settings: AppSettings) {
    val context = LocalContext.current
    fun switchOn() {
        settings.listeningSync = true
        settings.resyncAfterSongChange = false
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) switchOn()
        else Toast.makeText(context, "Listening needs the permission; it stays off", Toast.LENGTH_LONG).show()
    }
    ToggleRow(
        label = "Listen to stay in sync",
        // Switched on in a backup from another phone, the permission taken away since, or the resync on: off.
        checked = settings.listeningSync && !settings.resyncAfterSongChange && ListeningSync.hasPermission(context),
        onCheckedChange = { on ->
            when {
                !on -> settings.listeningSync = false
                ListeningSync.hasPermission(context) -> switchOn()
                else -> ask.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        description = "Instead of the resync, with no skip: a little into each song it hears what the phone is playing " +
            "and moves the lyrics to where the music is. Experimental: in a song with a steady, repeating beat it " +
            "often can't tell, and the lyrics then stay where Spotify says. It hears the phone's own playback, not " +
            "the microphone; nothing is recorded or sent. Android asks for its record-audio permission.",
        icon = Icons.Rounded.Hearing,
    )
}
