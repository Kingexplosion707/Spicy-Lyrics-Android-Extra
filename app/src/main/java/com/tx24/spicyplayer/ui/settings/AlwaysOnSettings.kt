package com.tx24.spicyplayer.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.aod.AodWeather
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow

/**
 * Settings → Device → Always-on display: its look (the classic clock or the lyrics screen's
 * player), what it shows around the music as Android's own does, and how it behaves.
 */
@Composable
internal fun AlwaysOnContent(settings: AppSettings) {
    val context = LocalContext.current
    val on = settings.ambientAsAod
    SettingRow(
        label = "Look",
        description = when (settings.aodStyle) {
            AodStyle.Classic -> "A big clock over the song and its lyrics, on black."
            AodStyle.Player -> "The lyrics screen's player: the cover blurred behind, the song beside its cover, the lyrics, the progress bar."
        },
        icon = Icons.Rounded.Dashboard,
        enabled = on,
    ) {
        SpicySelect(
            value = settings.aodStyle.name,
            options = AodStyle.entries.map { it.name },
            labels = AodStyle.entries.map { it.label },
            onChange = { settings.aodStyle = AodStyle.valueOf(it) },
            enabled = on,
        )
    }
    SettingRow(
        label = "Always-on dim",
        description = "How bright the always-on display is. Brighter reads better in daylight and uses more battery.",
        icon = Icons.Rounded.BrightnessLow,
        enabled = on,
    ) {
        val levels = listOf(1 to "Dimmest", 5 to "Dim", 12 to "Medium", 25 to "Bright", 45 to "Brightest")
        SpicySelect(
            value = (levels.minByOrNull { kotlin.math.abs(it.first - settings.aodBrightness) } ?: levels.first()).first.toString(),
            options = levels.map { it.first.toString() },
            labels = levels.map { it.second },
            onChange = { settings.aodBrightness = it.toInt() },
            enabled = on,
        )
    }
    ToggleRow(
        label = "Canvas behind it",
        checked = settings.aodCanvas,
        onCheckedChange = { settings.aodCanvas = it },
        description = "The song's Spotify Canvas moves behind it, blurred and darkened. Uses more battery; off with the battery saver.",
        icon = Icons.Rounded.Movie,
        enabled = on,
    )

    SettingsSection("Shows") {
        WeatherRows(settings, on)
        ToggleRow(
            label = "Next alarm",
            checked = settings.aodAlarm,
            onCheckedChange = { settings.aodAlarm = it },
            description = "The next alarm set in your clock app, when it's within a day.",
            icon = Icons.Rounded.Alarm,
            enabled = on,
        )
        ToggleRow(
            label = "Notifications",
            checked = settings.aodNotifications,
            onCheckedChange = { settings.aodNotifications = it },
            description = "The icons of the apps with notifications waiting, as Android's own always-on display shows them. Only icons: nothing they say.",
            icon = Icons.Rounded.Notifications,
            enabled = on,
        )
        ToggleRow(
            label = "Battery",
            checked = settings.aodBattery,
            onCheckedChange = { settings.aodBattery = it },
            description = "The battery's charge, and whether it's charging.",
            icon = Icons.Rounded.BatteryStd,
            enabled = on,
        )
        ToggleRow(
            label = "Silent and Do Not Disturb",
            checked = settings.aodRinger,
            onCheckedChange = { settings.aodRinger = it },
            description = "An icon while the phone is on vibrate, silent or Do Not Disturb.",
            icon = Icons.Rounded.DoNotDisturbOn,
            enabled = on,
        )
        ToggleRow(
            label = "Progress bar",
            checked = settings.aodProgress,
            onCheckedChange = { settings.aodProgress = it },
            description = "How far into the song it is, with the times, in the player look.",
            icon = Icons.Rounded.LinearScale,
            enabled = on && settings.aodStyle == AodStyle.Player,
        )
        ToggleRow(
            label = "Controls",
            checked = settings.aodControls,
            onCheckedChange = { settings.aodControls = it },
            description = "Play/pause, previous and next under the lyrics. A tap anywhere else still goes to the lock screen.",
            icon = Icons.Rounded.SmartDisplay,
            enabled = on,
        )
    }

    SettingsSection("Behaviour") {
        ToggleRow(
            label = "Double-tap to leave",
            checked = settings.aodDoubleTap,
            onCheckedChange = { settings.aodDoubleTap = it },
            description = "Two taps in a row go to the lock screen, so a brush against it in a pocket doesn't.",
            icon = Icons.Rounded.TouchApp,
            enabled = on,
        )
        ToggleRow(
            label = "Only while charging",
            checked = settings.aodOnlyCharging,
            onCheckedChange = { settings.aodOnlyCharging = it },
            description = "It comes up only when the phone is plugged in or on a charger; on battery the screen just turns off.",
            icon = Icons.Rounded.BatteryChargingFull,
            enabled = on,
        )
    }
}

/** The weather switch (asking for rough location) and its unit. */
@Composable
private fun WeatherRows(settings: AppSettings, on: Boolean) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        settings.aodWeather = granted
        if (!granted) Toast.makeText(context, "The weather needs your rough location; it stays off", Toast.LENGTH_LONG).show()
    }
    ToggleRow(
        label = "Weather",
        checked = settings.aodWeather && AodWeather.hasPermission(context),
        onCheckedChange = { want ->
            when {
                !want -> settings.aodWeather = false
                AodWeather.hasPermission(context) -> settings.aodWeather = true
                else -> ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        description = "The temperature and sky where you are, from Open-Meteo, at most every half hour. Uses your phone's last rough " +
            "location (no GPS is switched on for it) and sends it rounded to about a kilometre. Android asks for location.",
        icon = Icons.Rounded.WbSunny,
        enabled = on,
    )
    if (settings.aodWeather) {
        SettingRow(
            label = "Temperature in",
            icon = Icons.Rounded.Thermostat,
            enabled = on,
        ) {
            SpicySelect(
                value = settings.aodWeatherUnit.name,
                options = TemperatureUnit.entries.map { it.name },
                labels = TemperatureUnit.entries.map { it.label },
                onChange = { settings.aodWeatherUnit = TemperatureUnit.valueOf(it) },
                enabled = on,
            )
        }
    }
}
