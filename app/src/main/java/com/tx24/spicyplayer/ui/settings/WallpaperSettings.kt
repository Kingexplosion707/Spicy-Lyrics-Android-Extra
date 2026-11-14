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
import com.tx24.spicyplayer.wallpaper.PowerSaving
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material.icons.rounded.Vibration
import com.tx24.spicyplayer.playback.LyricsNotificationService
import com.tx24.spicyplayer.overlay.LyricsOverlayService
import com.tx24.spicyplayer.overlay.OverlaySize
import com.tx24.spicyplayer.overlay.OverlayStyle
import com.tx24.spicyplayer.overlay.OverlayColor
import com.tx24.spicyplayer.overlay.StatusBarClock
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Schedule
import com.tx24.spicyplayer.aod.AlwaysOnService
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
internal fun WallpaperSection(settings: AppSettings, showPickerButton: Boolean = true, titled: Boolean = true) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    // What the wallpaper's last lookup found, so a track with no Canvas reads as no Canvas,
    // not as a playback bug. Read when this screen opens; the wallpaper writes it per song.
    val canvasStatus = prefs.getString(SpicyWallpaperService.PREF_CANVAS_STATUS, null)
    val appleStatus = prefs.getString(SpicyWallpaperService.PREF_APPLE_STATUS, null)
    SettingsSection("Live wallpaper".takeIf { titled }) {
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
        WallpaperLookRows(settings)
        SettingRow(
            label = "Unlock animation",
            description = when (settings.wallpaperUnlockAnimation) {
                UnlockAnimation.Off -> "Off. Or the wallpaper waits in a locked look (dimmed) and springs to life as you unlock."
                UnlockAnimation.Zoom -> "Zoomed in and dimmed while locked; it settles back as you unlock."
                UnlockAnimation.Rise -> "Lowered and dimmed while locked; it rises into place as you unlock."
                UnlockAnimation.Focus -> "Blurred and dimmed while locked; it comes into focus as you unlock."
            } + " It plays once the phone unlocks: Android doesn't tell wallpapers how far along the swipe is.",
            icon = Icons.Rounded.LockOpen,
        ) {
            SpicySelect(
                value = settings.wallpaperUnlockAnimation.name,
                options = UnlockAnimation.entries.map { it.name },
                labels = UnlockAnimation.entries.map { it.label },
                onChange = { settings.wallpaperUnlockAnimation = UnlockAnimation.valueOf(it) },
            )
        }
        IdlePictureRow()
        PowerSavingRows(settings)
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
        ToggleRow(
            label = "Live activity in SuperShade Plus",
            checked = settings.superShadeLiveActivity,
            onCheckedChange = {
                settings.superShadeLiveActivity = it
                if (!it) com.tx24.spicyplayer.shade.SuperShadeBridge.stop()
            },
            description = "With SuperShade Plus installed, its shade shows the lyrics as a live activity: word-synced in this app's style, with the animated cover and the Canvas behind them. Its pin floats them out as the lyrics overlay, with the overlay's settings.",
            icon = Icons.Rounded.PictureInPictureAlt,
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
            label = "Overlay style",
            description = if (settings.overlayStyle == OverlayStyle.StatusBar) "One line in the status bar, filling word by word."
                else "The lyrics as on the lyrics screen, in a floating panel you can drag anywhere, up to the top of the screen.",
            icon = Icons.Rounded.ViewDay,
            enabled = settings.lyricsOverlay,
        ) {
            SpicySelect(
                value = settings.overlayStyle.name,
                options = OverlayStyle.entries.map { it.name },
                labels = OverlayStyle.entries.map { it.label },
                onChange = { settings.overlayStyle = OverlayStyle.valueOf(it) },
            )
        }
        if (settings.overlayStyle == OverlayStyle.StatusBar) HideClockRow(settings)
        SettingRow(
            label = "Lyrics colour",
            description = "The lyrics' colour in the overlay. From the cover takes it from the song's art.",
            icon = Icons.Rounded.Palette,
            enabled = settings.lyricsOverlay,
        ) {
            SpicySelect(
                value = settings.overlayColor.name,
                options = OverlayColor.entries.map { it.name },
                labels = OverlayColor.entries.map { it.label },
                onChange = { settings.overlayColor = OverlayColor.valueOf(it) },
            )
        }
        ToggleRow(
            label = "Glow",
            checked = settings.overlayGlow,
            onCheckedChange = { settings.overlayGlow = it },
            description = "A soft glow around the words being sung.",
            icon = Icons.Rounded.AutoAwesome,
            enabled = settings.lyricsOverlay,
        )
        SettingRow(
            label = "Glow strength",
            description = "How bright and wide the overlay's glow is.",
            icon = Icons.Rounded.AutoAwesome,
            enabled = settings.lyricsOverlay && settings.overlayGlow,
        ) {
            val steps = listOf(50, 75, 100, 150, 200, 250, 300)
            SpicySelect(
                value = (steps.minByOrNull { kotlin.math.abs(it - settings.overlayGlowStrength) } ?: 100).toString(),
                options = steps.map { it.toString() },
                labels = steps.map { if (it == 100) "Normal" else "$it%" },
                onChange = { settings.overlayGlowStrength = it.toInt() },
            )
        }
        SettingRow(
            label = "Text brightness",
            description = "Brighter lyrics, the words not yet sung included, for a busy screen behind them.",
            icon = Icons.Rounded.Opacity,
            enabled = settings.lyricsOverlay,
        ) {
            val steps = listOf(100, 125, 150, 175, 200)
            SpicySelect(
                value = (steps.minByOrNull { kotlin.math.abs(it - settings.overlayBrightness) } ?: 100).toString(),
                options = steps.map { it.toString() },
                labels = steps.map { if (it == 100) "Normal" else "$it%" },
                onChange = { settings.overlayBrightness = it.toInt() },
            )
        }
        SettingRow(
            label = "Lyrics opacity",
            description = "How solid the overlay's lyrics are.",
            icon = Icons.Rounded.Opacity,
            enabled = settings.lyricsOverlay,
        ) {
            val steps = listOf(100, 85, 70, 55, 40, 25)
            SpicySelect(
                value = (steps.minByOrNull { kotlin.math.abs(it - settings.overlayLyricsOpacity) } ?: 100).toString(),
                options = steps.map { it.toString() },
                labels = steps.map { "$it%" },
                onChange = { settings.overlayLyricsOpacity = it.toInt() },
            )
        }
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
            enabled = settings.lyricsOverlay && settings.overlayStyle == OverlayStyle.Full,
        )
        SettingRow(
            label = "Backdrop opacity",
            description = "How dark the panel behind the lyrics is.",
            icon = Icons.Rounded.Gradient,
            enabled = settings.lyricsOverlay && settings.overlayBackdrop && settings.overlayStyle == OverlayStyle.Full,
        ) {
            val steps = listOf(15, 30, 45, 60, 75, 90)
            SpicySelect(
                value = (steps.minByOrNull { kotlin.math.abs(it - settings.overlayBackdropOpacity) } ?: 45).toString(),
                options = steps.map { it.toString() },
                labels = steps.map { "$it%" },
                onChange = { settings.overlayBackdropOpacity = it.toInt() },
            )
        }
        ToggleRow(
            label = "Hide on the home screen",
            checked = settings.overlayHideOnWallpaper,
            onCheckedChange = { settings.overlayHideOnWallpaper = it },
            description = "Steps aside while the Spicy live wallpaper is showing, since it has the lyrics already.",
            icon = Icons.Rounded.Wallpaper,
            enabled = settings.lyricsOverlay,
        )
        ToggleRow(
            label = "Hide in landscape",
            checked = settings.overlayHideInLandscape,
            onCheckedChange = { settings.overlayHideInLandscape = it },
            description = "Out of the way of videos and games turned sideways.",
            icon = Icons.Rounded.ScreenRotation,
            enabled = settings.lyricsOverlay,
        )
    }
}

/**
 * Status bar style: hide the system clock while the lyrics show, so they sit in its place. The
 * permission it needs is granted once through Shizuku.
 */
@Composable
private fun HideClockRow(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canWrite by remember { mutableStateOf(StatusBarClock.canWrite(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    if (canWrite) {
        ToggleRow(
            label = "Replace the clock",
            checked = settings.overlayHideClock,
            onCheckedChange = { settings.overlayHideClock = it },
            description = "The status bar clock hides while the lyrics show, so they take its place. It comes back when they stop.",
            icon = Icons.Rounded.Schedule,
            enabled = settings.lyricsOverlay,
        )
    } else {
        SettingRow(
            label = "Replace the clock",
            description = message ?: "With Shizuku running, hide the status bar clock while the lyrics show so they take its place.",
            icon = Icons.Rounded.Schedule,
            enabled = settings.lyricsOverlay,
        ) {
            SpicyButton("Set up", onClick = {
                scope.launch {
                    val result = StatusBarClock.grantWithShizuku(context)
                    message = result.message
                    canWrite = StatusBarClock.canWrite(context)
                    if (canWrite) settings.overlayHideClock = true
                }
            })
        }
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

/**
 * Power saving for the wallpaper and the music haptics: when (never, with the phone's battery
 * saver, always), and what goes then. Each cut is its own switch.
 */
@Composable
private fun PowerSavingRows(settings: AppSettings) {
    SettingRow(
        label = "Power saving",
        description = when (settings.wallpaperPowerSaving) {
            PowerSaving.Off -> "The wallpaper and haptics always run in full."
            PowerSaving.BatterySaver -> "Cuts what you choose below while the phone's battery saver is on."
            PowerSaving.Always -> "Always cuts what you choose below."
        },
        icon = Icons.Rounded.BatterySaver,
    ) {
        SpicySelect(
            value = settings.wallpaperPowerSaving.name,
            options = PowerSaving.entries.map { it.name },
            labels = PowerSaving.entries.map { it.label },
            onChange = { settings.wallpaperPowerSaving = PowerSaving.valueOf(it) },
        )
    }
    val on = settings.wallpaperPowerSaving != PowerSaving.Off
    ToggleRow(
        label = "Stop the Canvas video",
        checked = settings.wallpaperSaveNoCanvas,
        onCheckedChange = { settings.wallpaperSaveNoCanvas = it },
        description = "The blurred still cover shows behind instead.",
        icon = Icons.Rounded.Movie,
        enabled = on && !settings.wallpaperSaveStopAll,
    )
    ToggleRow(
        label = "Stop the animated cover",
        checked = settings.wallpaperSaveNoAnimatedCover,
        onCheckedChange = { settings.wallpaperSaveNoAnimatedCover = it },
        description = "The still album cover shows instead.",
        icon = Icons.Rounded.Album,
        enabled = on && !settings.wallpaperSaveStopAll,
    )
    ToggleRow(
        label = "Simpler lyrics animation",
        checked = settings.wallpaperSaveSimpler,
        onCheckedChange = { settings.wallpaperSaveSimpler = it },
        description = "No glow, distance blur or word lift on the lyrics.",
        icon = Icons.Rounded.AutoAwesomeMotion,
        enabled = on && !settings.wallpaperSaveStopAll,
    )
    ToggleRow(
        label = "Line by line",
        checked = settings.wallpaperSaveLineByLine,
        onCheckedChange = { settings.wallpaperSaveLineByLine = it },
        description = "Each line lights up whole instead of word by word.",
        icon = Icons.Rounded.ViewHeadline,
        enabled = on && !settings.wallpaperSaveStopAll,
    )
    ToggleRow(
        label = "Stop everything",
        checked = settings.wallpaperSaveStopAll,
        onCheckedChange = { settings.wallpaperSaveStopAll = it },
        description = "Shows your picture for when nothing plays (or a still frame without one), with nothing moving.",
        icon = Icons.Rounded.Wallpaper,
        enabled = on,
    )
    ToggleRow(
        label = "Stop the haptics",
        checked = settings.wallpaperSaveNoHaptics,
        onCheckedChange = { settings.wallpaperSaveNoHaptics = it },
        description = "No vibrations to the music, in the app or in the background.",
        icon = Icons.Rounded.Vibration,
        enabled = on,
    )
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
