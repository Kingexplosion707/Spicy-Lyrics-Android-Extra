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
import com.tx24.spicyplayer.overlay.LyricsOverlayService
import com.tx24.spicyplayer.overlay.OverlaySize
import com.tx24.spicyplayer.aod.AlwaysOnService
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Rectangle
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
import com.tx24.spicyplayer.ui.components.SpicySelect
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

/**
 * Device → Lyrics overlay: the lyrics floating over other apps, drawn as on the lyrics screen.
 * Turning it on asks for "Display over other apps" first when it's missing.
 */
@Composable
internal fun LyricsOverlaySection(settings: AppSettings) {
    val context = LocalContext.current
    ResyncOnResume()
    SettingsSection("Lyrics overlay") {
        ToggleRow(
            label = "Lyrics overlay",
            checked = settings.lyricsOverlay,
            onCheckedChange = { on ->
                settings.lyricsOverlay = on
                if (on && !android.provider.Settings.canDrawOverlays(context)) askOverlayPermission(context)
                LyricsOverlayService.sync(context)
            },
            description = "The lyrics float over your other apps, word by word, in your lyrics style. Drag to move.",
            icon = Icons.Rounded.PictureInPictureAlt,
        )
        SettingRow(
            label = "Overlay size",
            description = "How tall the overlay is and how big its lyrics are.",
            icon = Icons.Rounded.FormatSize,
            enabled = settings.lyricsOverlay,
        ) {
            SpicySelect(
                value = settings.overlaySize.name,
                options = OverlaySize.entries.map { it.name },
                labels = OverlaySize.entries.map { it.label },
                onChange = { settings.overlaySize = OverlaySize.valueOf(it) },
            )
        }
        SettingRow(
            label = "Overlay width",
            description = "How much of the screen's width it takes.",
            icon = Icons.Rounded.SwapHoriz,
            enabled = settings.lyricsOverlay,
        ) {
            val widths = listOf(60, 75, 85, 94, 100)
            SpicySelect(
                value = (widths.minByOrNull { kotlin.math.abs(it - settings.overlayWidth) } ?: 94).toString(),
                options = widths.map { it.toString() },
                labels = widths.map { "$it%" },
                onChange = { settings.overlayWidth = it.toInt() },
            )
        }
        ToggleRow(
            label = "Hide when paused",
            checked = settings.overlayHideWhenPaused,
            onCheckedChange = { settings.overlayHideWhenPaused = it },
            description = "Goes away a moment after the music pauses, and comes back when it plays.",
            icon = Icons.Rounded.PauseCircle,
            enabled = settings.lyricsOverlay,
        )
        ToggleRow(
            label = "Lock in place",
            checked = settings.overlayLocked,
            onCheckedChange = { settings.overlayLocked = it },
            description = "It can't be dragged by accident.",
            icon = Icons.Rounded.Lock,
            enabled = settings.lyricsOverlay,
        )
        ToggleRow(
            label = "Touches pass through",
            checked = settings.overlayClickThrough,
            onCheckedChange = { settings.overlayClickThrough = it },
            description = "Taps reach the app under it. It can't be moved then, and shows slightly see-through, as Android requires.",
            icon = Icons.Rounded.TouchApp,
            enabled = settings.lyricsOverlay,
        )
        ToggleRow(
            label = "Backdrop",
            checked = settings.overlayBackdrop,
            onCheckedChange = { settings.overlayBackdrop = it },
            description = "A dark rounded panel behind the lyrics, so they read over anything.",
            icon = Icons.Rounded.Rectangle,
            enabled = settings.lyricsOverlay,
        )
    }
}

/** Opens Android's "Display over other apps" page for this app. */
internal fun askOverlayPermission(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + context.packageName),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Back from a permission page: the overlay and the always-on display start (or stop) to match. */
@Composable
internal fun ResyncOnResume() {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                LyricsOverlayService.sync(context)
                AlwaysOnService.sync(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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
