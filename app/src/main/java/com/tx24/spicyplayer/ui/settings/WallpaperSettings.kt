package com.tx24.spicyplayer.ui.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.wallpaper.WallpaperIdlePicture
import com.tx24.spicyplayer.playback.LyricsNotificationService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.rounded.Notifications
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        IdlePictureRow()
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

/**
 * Device → Lyrics notification: the line being sung in an ongoing notification, which apps with
 * a dynamic island (Essentials) and Android 16's Live Updates show at the top of the screen.
 */
@Composable
internal fun LyricsNotificationSection(settings: AppSettings) {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LyricsNotificationService.sync(context)
    }
    SettingsSection("Lyrics notification") {
        ToggleRow(
            label = "Lyrics in a notification",
            checked = settings.lyricsNotification,
            onCheckedChange = { on ->
                settings.lyricsNotification = on
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    LyricsNotificationService.sync(context)
                }
            },
            description = "The line being sung, live, for dynamic islands like Essentials and Live Updates.",
            icon = Icons.Rounded.Notifications,
        )
    }
}

/** The picture the wallpaper shows while nothing plays: pick one, or remove it. */
@Composable
private fun IdlePictureRow() {
    val context = LocalContext.current
    var picked by remember { mutableStateOf(WallpaperIdlePicture.isSet(context)) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { WallpaperIdlePicture.save(context, uri) }
            picked = picked || saved
        }
    }
    SettingRow(
        label = "When nothing plays",
        description = if (picked) "Your picture shows while no song plays or it's paused."
            else "Pick a picture to show while no song plays or it's paused.",
        icon = Icons.Rounded.Image,
    ) {
        if (picked) {
            SpicyButton("Remove", onClick = {
                WallpaperIdlePicture.remove(context)
                picked = false
            })
        } else {
            SpicyButton("Choose", onClick = { picker.launch("image/*") })
        }
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
