package com.tx24.spicyplayer.ui.settings

// The desktop version of :app's AlwaysOnSettings.kt: the always-on display is a phone's; the
// Spotify account rows it also holds are the same.

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
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow

@Composable
internal fun AlwaysOnContent(settings: AppSettings) {}

/**
 * Your own Spotify account ([SpotifyAccount]), for the Add to playlist button: Spotify's own
 * sign-in, through a Spotify app of your own (no secret).
 */
@Composable
internal fun SpotifyAccountRows() {
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
                "If you signed in before, sign in again: this asks to control playback and add to playlists too."
        },
        icon = Icons.Rounded.AccountCircle,
    ) {
        if (signedIn) {
            SpicyButton("Sign out", onClick = {
                SpotifyAccount.logOut(context)
                com.tx24.spicyplayer.spotify.SpotifyConnectSession.sync(context)
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

