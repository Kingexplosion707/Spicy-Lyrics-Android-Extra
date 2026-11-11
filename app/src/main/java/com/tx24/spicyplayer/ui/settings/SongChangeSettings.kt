package com.tx24.spicyplayer.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.ToggleRow

/**
 * Scroll & Sync → Song changes: keeping the lyrics on time when a player blends one song into the
 * next, which it reports late (Spotify's Automix and Crossfade).
 */
@Composable
internal fun SongChangeSection(settings: AppSettings) {
    SettingsSection("Song changes") {
        ToggleRow(
            label = "Resync after a song change",
            checked = settings.resyncAfterSongChange,
            onCheckedChange = { settings.resyncAfterSongChange = it },
            description = "For Spotify's Automix, which reports each new song a few seconds late: two seconds in, " +
                "Spotify is sent to where the lyrics are, as tapping a line does. The song jumps back a little.",
            icon = Icons.Rounded.Sync,
        )
        SettingRow(
            label = "Crossfade",
            description = "Set it to your player's Crossfade. A song that follows another on its own starts this far in, " +
                "with no jump. Off for Automix.",
            icon = Icons.Rounded.BlurLinear,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.crossfadeSeconds,
                range = 0..12,
                step = 1,
                onValueChange = { settings.crossfadeSeconds = it },
                default = 0,
                unit = "s",
            )
        }
    }
}
