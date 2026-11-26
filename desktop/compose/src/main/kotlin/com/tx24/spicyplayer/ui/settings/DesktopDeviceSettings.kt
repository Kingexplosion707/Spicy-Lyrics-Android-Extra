package com.tx24.spicyplayer.ui.settings

// The phone-only settings sections of :app's WallpaperSettings.kt and HapticsSettings.kt: the live
// wallpaper, the notification, the floating overlay and vibration aren't on a desktop.

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer

@Composable
internal fun WallpaperSection(settings: AppSettings, showPickerButton: Boolean = true, titled: Boolean = true) {}

@Composable
internal fun LyricsNotificationSection(settings: AppSettings) {}

@Composable
internal fun LyricsOverlaySection(settings: AppSettings) {}

internal fun askOverlayPermission(context: Context) {}

@Composable
internal fun ResyncOnResume() {}

internal fun openWallpaperPicker(context: Context) {}

@Composable
internal fun HapticsSection(settings: AppSettings) {}

/**
 * Settings → Device on a computer: what applies to a window. The lock screen, always-on display,
 * floating lyrics, live wallpaper, battery saver, screen timeout and system bars are phone things.
 */
@Composable
internal fun DeviceContent(settings: AppSettings) {
    com.tx24.spicyplayer.ui.components.ToggleRow(
        label = "Smoother motion",
        checked = settings.highRefreshRate,
        onCheckedChange = { settings.highRefreshRate = it },
        description = "Draw at your monitor's full refresh rate instead of 60 Hz. Off uses less power.",
        icon = androidx.compose.material.icons.Icons.Rounded.Animation,
    )
    com.tx24.spicyplayer.ui.components.ToggleRow(
        label = "Ambient mode",
        checked = settings.ambientMode,
        onCheckedChange = { settings.ambientMode = it },
        description = "Left alone while music plays, dim to the lyrics and the song on black. A click or key brings it back.",
        icon = androidx.compose.material.icons.Icons.Rounded.DarkMode,
    )
    com.tx24.spicyplayer.ui.components.SettingRow(
        label = "Dim After",
        description = "How long ambient mode waits after you last used the window.",
        icon = androidx.compose.material.icons.Icons.Rounded.Timer,
        enabled = settings.ambientMode,
    ) {
        com.tx24.spicyplayer.ui.components.SpicySelect(
            value = settings.ambientDelay.name,
            options = AmbientDelay.entries.map { it.name },
            labels = AmbientDelay.entries.map { it.label },
            onChange = { settings.ambientDelay = AmbientDelay.valueOf(it) },
        )
    }
    com.tx24.spicyplayer.flipper.FlipperRow()
    com.tx24.spicyplayer.ui.components.SettingsSection("Performance") {
        com.tx24.spicyplayer.ui.components.ToggleRow(
            label = "Low performance mode",
            checked = settings.lowPerformance,
            onCheckedChange = { settings.lowPerformance = it },
            description = "Stills the background and cover and turns off blur and glow, for smoother lyrics on slower computers.",
            icon = androidx.compose.material.icons.Icons.Rounded.Speed,
        )
        com.tx24.spicyplayer.ui.components.ToggleRow(
            label = "Interface animations",
            checked = settings.uiAnimations && !settings.lowPerformance,
            onCheckedChange = { settings.uiAnimations = it },
            description = "Animate the playback buttons, the queue and pop-up screens. Off in low performance mode.",
            icon = androidx.compose.material.icons.Icons.Rounded.Animation,
            enabled = !settings.lowPerformance,
        )
    }
}
