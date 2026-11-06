package com.tx24.spicyplayer.ui.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.ToggleRow
import com.tx24.spicyplayer.wallpaper.SpicyWallpaperService

/** Device → Live wallpaper: the now-playing screen as the home screen wallpaper. */
@Composable
internal fun WallpaperSection(settings: AppSettings, showPickerButton: Boolean = true) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    // What the wallpaper's last lookup found, so a track with no Canvas reads as no Canvas,
    // not as a playback bug. Read when this screen opens; the wallpaper writes it per song.
    val canvasStatus = prefs.getString(SpicyWallpaperService.PREF_CANVAS_STATUS, null)
    val appleStatus = prefs.getString(SpicyWallpaperService.PREF_APPLE_STATUS, null)
    SettingsSection("Live wallpaper") {
        if (showPickerButton) SettingRow(
            label = "Live wallpaper",
            description = "The lyrics, video and cover art as your home screen wallpaper.",
            icon = Icons.Rounded.Wallpaper,
        ) {
            SpicyButton("Choose wallpaper", onClick = { openWallpaperPicker(context) })
        }
        ToggleRow(
            label = "Show lyrics on wallpaper",
            checked = settings.wallpaperShowLyrics,
            onCheckedChange = { settings.wallpaperShowLyrics = it },
            description = "Word-synced lyrics over the moving picture. Off: only the video and cover art show.",
            icon = Icons.Rounded.MusicNote,
        )
        SettingRow(
            label = "Canvas",
            description = canvasStatus ?: "No track has played yet with the wallpaper on.",
            icon = Icons.Rounded.Movie,
        )
        SettingRow(
            label = "Animated cover",
            description = appleStatus ?: "No track has played yet with the wallpaper on.",
            icon = Icons.Rounded.Movie,
        )
    }
}

/** Opens the system picker straight onto this app's live wallpaper. */
internal fun openWallpaperPicker(context: Context) {
    val component = ComponentName(context, SpicyWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    if (!runCatching { context.startActivity(direct) }.isSuccess) {
        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)) }
    }
}
