package com.tx24.spicyplayer.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.stats.ListeningLog
import com.tx24.spicyplayer.stats.ListenBrainz
import com.tx24.spicyplayer.stats.ListeningTracker
import com.tx24.spicyplayer.stats.Scrobbler
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The periods the listening stats can show, as days back from today. */
private enum class StatsPeriod(val label: String, val days: Long) {
    Week("Last 7 days", 7),
    Month("Last 30 days", 30),
    Year("Last 365 days", 365),
    All("All time", 100_000),
}

/**
 * Listening stats: your top songs and artists, counted on this phone from whatever plays, in any
 * player. Off until switched on here; nothing is sent anywhere.
 */
@Composable
internal fun ListeningStatsContent(settings: AppSettings) {
    val context = LocalContext.current
    var period by remember { mutableStateOf(StatsPeriod.Month) }
    // Rises when the history is cleared, so the page reads it again.
    var version by remember { mutableIntStateOf(0) }
    ToggleRow(
        label = "Count what I listen to",
        checked = settings.listeningStats,
        onCheckedChange = {
            settings.listeningStats = it
            ListeningTracker.sync(context)
        },
        description = "Keeps your top songs and artists on this phone, from any player. The stats never leave the phone.",
        icon = Icons.Rounded.Insights,
    )
    ScrobbleRows(settings)
    SettingRow(
        label = "Period",
        description = "Which days the stats below cover.",
        icon = Icons.Rounded.CalendarMonth,
    ) {
        SpicySelect(
            value = period.name,
            options = StatsPeriod.entries.map { it.name },
            labels = StatsPeriod.entries.map { it.label },
            onChange = { period = StatsPeriod.valueOf(it) },
        )
    }
    val summary by produceState<ListeningLog.Summary?>(null, period, version) {
        value = withContext(Dispatchers.IO) {
            val today = ListeningTracker.today()
            ListeningTracker.log(context).summary(today - period.days + 1, today)
        }
    }
    val stats = summary
    if (stats == null || stats.songs == 0) {
        SettingRow(
            label = "Nothing counted yet",
            description = if (settings.listeningStats) "Play some music and come back."
                else "Switch it on above, and your listening is counted from then on.",
        )
        return
    }
    SettingRow(
        label = "${ListeningLog.duration(stats.ms).replaceFirstChar(Char::uppercase)} listened",
        description = "${stats.plays} plays of ${stats.songs} songs by ${stats.artists} artists.",
    )
    SettingsSection("Top artists") {
        stats.topArtists.forEachIndexed { index, row ->
            SettingRow(label = "${index + 1}. ${row.name}", description = "${ListeningLog.duration(row.ms)} · ${plays(row.plays)}")
        }
    }
    SettingsSection("Top songs") {
        stats.topSongs.forEachIndexed { index, row ->
            SettingRow(label = "${index + 1}. ${row.name}", description = "${row.detail} · ${ListeningLog.duration(row.ms)} · ${plays(row.plays)}")
        }
    }
    SettingsSection("History") {
        SettingRow(
            label = "Clear listening history",
            description = "Forgets everything counted so far.",
            icon = Icons.Rounded.DeleteSweep,
        ) {
            SpicyButton("Clear", onClick = {
                ListeningTracker.clear(context)
                version++
            })
        }
    }
}

private fun plays(count: Int) = if (count == 1) "1 play" else "$count plays"

/**
 * Scrobbling to ListenBrainz ([Scrobbler]): the switch, and the user's own token with a check that
 * ListenBrainz knows it.
 */
@Composable
private fun ScrobbleRows(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(Scrobbler.token(context)) }
    var checking by remember { mutableStateOf(false) }
    ToggleRow(
        label = "Scrobble to ListenBrainz",
        checked = settings.scrobbleListenBrainz,
        onCheckedChange = {
            settings.scrobbleListenBrainz = it
            ListeningTracker.sync(context)
        },
        description = "Sends each song you've heard half of (or four minutes of) to your ListenBrainz account: its title, " +
            "artist, album, length and when you heard it. From any player, with or without the stats above.",
        icon = Icons.Rounded.Publish,
    )
    if (!settings.scrobbleListenBrainz) return
    SettingRow(
        label = "User token",
        description = "From your ListenBrainz settings page. Kept on this phone, out of settings exports.",
        icon = Icons.Rounded.Key,
        stacked = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyTextField(
                token,
                {
                    token = it.trim()
                    Scrobbler.setToken(context, token)
                    ListeningTracker.sync(context)
                },
                placeholder = "Token",
                modifier = Modifier.weight(1f),
            )
            if (token.isBlank()) {
                SpicyButton("Get one", external = true, onClick = { openUrl(context, ListenBrainz.TOKEN_URL) })
            } else {
                SpicyButton(if (checking) "Checking" else "Check", enabled = !checking, onClick = {
                    checking = true
                    scope.launch {
                        val user = Scrobbler.validate(token)
                        checking = false
                        Toast.makeText(
                            context,
                            if (user != null) "ListenBrainz knows this token: $user" else "ListenBrainz didn't accept this token (or can't be reached)",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                })
            }
        }
    }
}
