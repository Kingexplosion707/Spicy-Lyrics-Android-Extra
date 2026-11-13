package com.tx24.spicyplayer.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Intent
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.MyLocation
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.VisibilityOff
import com.tx24.spicyplayer.spotify.SpotifyAccount
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.aod.AodFingerprint
import com.tx24.spicyplayer.aod.AodWeather
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
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
    val hasLight = remember { context.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) != null }
    if (hasLight) {
        ToggleRow(
            label = "Auto brightness",
            checked = settings.aodAutoBrightness,
            onCheckedChange = { settings.aodAutoBrightness = it },
            description = "Follows the light sensor: dimmer than the screen's lowest in the dark, brighter in daylight.",
            icon = Icons.Rounded.BrightnessAuto,
            enabled = on,
        )
    }
    if (hasLight && settings.aodAutoBrightness) {
        SettingRow(
            label = "Brighter or dimmer",
            description = "Shifts what the light sensor picks.",
            icon = Icons.Rounded.BrightnessMedium,
            enabled = on,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.aodBrightnessBias,
                range = -40..40,
                step = 5,
                onValueChange = { settings.aodBrightnessBias = it },
                default = 0,
                enabled = on,
            )
        }
    } else {
        SettingRow(
            label = "Brightness",
            description = "Below about a third it goes dimmer than the screen's lowest. Brighter reads better in daylight and uses more battery.",
            searchDescription = "Always-on dim",
            icon = Icons.Rounded.BrightnessLow,
            enabled = on,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.aodLevel,
                range = 0..100,
                step = 5,
                onValueChange = { settings.aodLevel = it },
                default = 15,
                unit = "%",
                enabled = on,
            )
        }
    }
    SettingRow(
        label = "Clock",
        description = if (settings.aodClockStyle == AodClockStyle.Glass) "Frosted glass lit from above, with a bright rim." else "How the clock's figures look.",
        icon = Icons.Rounded.Schedule,
        enabled = on,
    ) {
        SpicySelect(
            value = settings.aodClockStyle.name,
            options = AodClockStyle.entries.map { it.name },
            labels = AodClockStyle.entries.map { it.label },
            onChange = { settings.aodClockStyle = AodClockStyle.valueOf(it) },
            enabled = on,
        )
    }
    SettingRow(
        label = "Clock size",
        icon = Icons.Rounded.FormatSize,
        enabled = on,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = settings.aodClockSize,
            range = 60..160,
            step = 10,
            onValueChange = { settings.aodClockSize = it },
            default = 100,
            unit = "%",
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
            description = "The icons of the apps with notifications waiting. Tap them for a list of every one, which scrolls; tap one to unlock and open it. Hidden ones show only their app, as on your lock screen.",
            searchDescription = "The icons of the apps with notifications waiting, as Android's own always-on display shows them. Only icons: nothing they say.",
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
            description = "Play/pause, previous and next under the lyrics, with the player's like button and shuffle beside them when it has them. A tap anywhere else still goes to the lock screen.",
            icon = Icons.Rounded.SmartDisplay,
            enabled = on,
        )
        ToggleRow(
            label = "Add to playlist button",
            checked = settings.aodPlaylistButton,
            onCheckedChange = { settings.aodPlaylistButton = it },
            description = "With the controls, a button that adds the song to the Spotify playlist it's playing from, if it isn't there. Needs your Spotify account below.",
            icon = Icons.Rounded.PlaylistAdd,
            enabled = on && settings.aodControls,
        )
        if (settings.aodControls && settings.aodPlaylistButton) SpotifyAccountRows()
        ToggleRow(
            label = "Notification animation",
            checked = settings.aodArrivalAnimation,
            onCheckedChange = { settings.aodArrivalAnimation = it },
            description = "When a notification comes in, light runs round the screen's edge and its app drops in from the top. Tap it to open.",
            icon = Icons.Rounded.NotificationsActive,
            enabled = on && settings.aodNotifications,
        )
        ToggleRow(
            label = "Hide what notifications say until unlocked",
            checked = settings.aodHideNotificationText,
            onCheckedChange = { settings.aodHideNotificationText = it },
            description = "The list shows only each app until your face or fingerprint unlocks the phone, then what they say.",
            icon = Icons.Rounded.VisibilityOff,
            enabled = on && settings.aodNotifications,
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
        if (remember { AodFingerprint.supported(context) }) {
            ToggleRow(
                label = "Hold the fingerprint sensor to unlock",
                checked = settings.aodFingerprintUnlock,
                onCheckedChange = { settings.aodFingerprintUnlock = it },
                description = "Resting a finger on the sensor brings up Android's unlock, which takes the same finger (lift and press again if it doesn't).",
                icon = Icons.Rounded.Fingerprint,
                enabled = on,
            )
            if (settings.aodFingerprintUnlock) FingerprintSpotRow(on)
        }
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

/**
 * Your own Spotify account ([SpotifyAccount]), for the Add to playlist button: Spotify's own
 * sign-in, through a Spotify app of your own (no secret).
 */
@Composable
private fun SpotifyAccountRows() {
    val context = LocalContext.current
    // Signing in finishes in another activity: read again on coming back.
    var seen by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        seen++
        onPauseOrDispose { }
    }
    val signedIn = remember(seen) { SpotifyAccount.signedIn(context) }
    val name = remember(seen) { SpotifyAccount.accountName(context) }
    var clientId by remember(seen) { mutableStateOf(SpotifyAccount.clientId(context)) }
    SettingRow(
        label = "Spotify account",
        description = when {
            signedIn -> "Signed in${name?.let { " as $it" } ?: ""}."
            else -> "Make an app at developer.spotify.com with the redirect URI ${SpotifyAccount.REDIRECT_URI}, then paste its Client ID below. " +
                "If you signed in before for the crossfade, sign in again: this asks to add to playlists too."
        },
        icon = Icons.Rounded.AccountCircle,
    ) {
        if (signedIn) {
            SpicyButton("Sign out", onClick = {
                SpotifyAccount.logOut(context)
                seen++
            })
        } else {
            SpicyButton("Open", external = true, onClick = { openUrl(context, SpotifyAccount.DEVELOPER_URL) })
        }
    }
    if (!signedIn) {
        SettingRow(
            label = "Client ID",
            description = "From your app's page on the Spotify developer site. No secret is needed.",
            icon = Icons.Rounded.Key,
            stacked = true,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                SpicyTextField(clientId, { clientId = it.trim() }, placeholder = "Client ID", modifier = Modifier.weight(1f))
                SpicyButton("Sign in", enabled = clientId.length >= 16, onClick = {
                    SpotifyAccount.setClientId(context, clientId)
                    val uri = SpotifyAccount.loginUri(context)
                    if (uri == null) {
                        Toast.makeText(context, "Paste your Client ID first", Toast.LENGTH_SHORT).show()
                    } else {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { Toast.makeText(context, "No browser to sign in with", Toast.LENGTH_SHORT).show() }
                    }
                })
            }
        }
    }
}

/**
 * Where the fingerprint sensor is ([AodFingerprint]): read from the phone through Shizuku, or set
 * by touching it; until then a guess.
 */
@Composable
private fun FingerprintSpotRow(on: Boolean) {
    val context = LocalContext.current
    var seen by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        seen++
        onPauseOrDispose { }
    }
    val found = remember(seen) { AodFingerprint.found(context) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SettingRow(
        label = "Fingerprint spot",
        description = message ?: if (found) "Set. Find or set it again if holding the sensor doesn't work." else
            "Guessed. Find it with Shizuku running, or set it by touching the sensor.",
        icon = Icons.Rounded.MyLocation,
        enabled = on,
        stacked = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyButton("Find with Shizuku", enabled = on, onClick = {
                scope.launch {
                    message = AodFingerprint.findWithShizuku(context).message
                    seen++
                }
            })
            SpicyButton("Set by touch", enabled = on, onClick = {
                message = null
                context.startActivity(Intent(context, com.tx24.spicyplayer.aod.FingerprintSpotActivity::class.java))
            })
        }
    }
}
