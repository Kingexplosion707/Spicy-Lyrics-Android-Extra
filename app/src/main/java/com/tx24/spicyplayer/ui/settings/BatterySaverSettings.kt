package com.tx24.spicyplayer.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import com.tx24.spicyplayer.power.rememberBatterySaverOn
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.ToggleRow

/** The Battery saver card's one-line summary: how many things it turns off, and whether it's on now. */
internal fun batterySaverSummary(settings: AppSettings, saverOn: Boolean): String {
    val count = listOf(
        settings.saverStopsAlwaysOn, settings.saverStopsOverlay, settings.saverStopsNotification,
        settings.saverStopsBackgroundHaptics, settings.saverStopsKeepScreenOn, settings.saverStopsHighRefreshRate,
        settings.saverLowPerformance, settings.saverStopsListening,
    ).count { it }
    val what = if (count == 0) "Turns nothing off" else "Turns off $count ${if (count == 1) "thing" else "things"}"
    return if (saverOn) "On now · $what" else "$what while it's on"
}

/**
 * Settings → Device → Battery saver: what the phone's battery saver turns off in the app. Each
 * feature stays switched on, rests while the battery saver is on, and comes back after.
 */
@Composable
internal fun BatterySaverContent(settings: AppSettings) {
    val saverOn = rememberBatterySaverOn()
    SettingRow(
        label = "Battery saver",
        description = if (saverOn) {
            "On now: what's switched on below is resting until it's off."
        } else {
            "Off now. While the phone's battery saver is on, what's switched on below rests."
        },
        icon = Icons.Rounded.BatterySaver,
    )
    ToggleRow(
        label = "Turn off the always-on display",
        checked = settings.saverStopsAlwaysOn,
        onCheckedChange = { settings.saverStopsAlwaysOn = it },
        description = "No lyrics when the screen turns off, and the lyrics screen lets the screen sleep.",
        icon = Icons.Rounded.Nightlight,
    )
    ToggleRow(
        label = "Turn off the lyrics overlay",
        checked = settings.saverStopsOverlay,
        onCheckedChange = { settings.saverStopsOverlay = it },
        description = "The floating lyrics and the status bar lyrics hide and stop looking up lyrics.",
        icon = Icons.Rounded.Layers,
    )
    ToggleRow(
        label = "Pause the lyrics notification",
        checked = settings.saverStopsNotification,
        onCheckedChange = { settings.saverStopsNotification = it },
        description = "The live line stops updating and stops following the player.",
        icon = Icons.Rounded.Notifications,
    )
    ToggleRow(
        label = "Stop background haptics",
        checked = settings.saverStopsBackgroundHaptics,
        onCheckedChange = { settings.saverStopsBackgroundHaptics = it },
        description = "No vibrations outside the app, and no song analysis for them.",
        icon = Icons.Rounded.Vibration,
    )
    ToggleRow(
        label = "Stop listening to stay in sync",
        checked = settings.saverStopsListening,
        onCheckedChange = { settings.saverStopsListening = it },
        description = "The lyrics follow the player's own position only.",
        icon = Icons.Rounded.Hearing,
    )
    SettingsSection("Lyrics screen") {
        ToggleRow(
            label = "Let the screen turn off",
            checked = settings.saverStopsKeepScreenOn,
            onCheckedChange = { settings.saverStopsKeepScreenOn = it },
            description = "Keep the screen on is ignored, so the screen times out as usual.",
            icon = Icons.Rounded.StayCurrentPortrait,
        )
        ToggleRow(
            label = "Draw at 60 Hz",
            checked = settings.saverStopsHighRefreshRate,
            onCheckedChange = { settings.saverStopsHighRefreshRate = it },
            description = "Smoother motion is ignored, halving the frames on a 120 Hz screen.",
            icon = Icons.Rounded.Animation,
        )
        ToggleRow(
            label = "Low performance mode",
            checked = settings.saverLowPerformance,
            onCheckedChange = { settings.saverLowPerformance = it },
            description = "Stills the background and cover and turns off blur and glow while it's on.",
            icon = Icons.Rounded.Speed,
        )
    }
}
