package com.tx24.spicyplayer.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tx24.spicyplayer.spotify.SpotifyAccount
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.theme.SpicySpacing
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
                "Spotify is sent to where the lyrics are, as tapping a line does, and the song may jump a little.",
            icon = Icons.Rounded.Sync,
        )
        SpotifyAccountRows()
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

/**
 * Your own Spotify account: with it, Spotify's servers say where a new song is and the lyrics move
 * there with no jump ([SpotifyAccount]).
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
            signedIn -> "Signed in${name?.let { " as $it" } ?: ""}. Testing: where Spotify's servers say each new song is goes into Debug info, beside the phone's."
            else -> "Testing: sign in to compare where Spotify's servers say each new song is with the phone (Debug info). Make an app at developer.spotify.com " +
                "with the redirect URI ${SpotifyAccount.REDIRECT_URI}, then paste its Client ID below."
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
