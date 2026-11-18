package com.tx24.spicyplayer.ui.spotifysearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.playback.SearchedSong
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Lyrics search: a song's lyrics found by its title and artist and followed with no player
 * ([SearchedSong]), on the app's own timer. Opens filled in with the song on screen, so a tap on
 * Search looks it up again.
 */

@Composable
fun LyricsSearchModal(
    visible: Boolean,
    /** The song on screen, filled in as it opens. */
    title: String,
    artist: String,
    backdrop: HazeState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = "Search lyrics",
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var song by remember { mutableStateOf("") }
        var singer by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf<Job?>(null) }
        var notice by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(visible) {
            if (!visible) {
                searching?.cancel()
                searching = null
                return@LaunchedEffect
            }
            song = title
            singer = artist
            notice = null
        }
        fun submit() {
            if (song.isBlank() || singer.isBlank() || searching != null) return
            notice = null
            searching = scope.launch {
                val outcome = SearchedSong.search(context, song, singer)
                searching = null
                when (outcome) {
                    SearchedSong.Outcome.Playing, SearchedSong.Outcome.Untimed -> onDismissRequest()
                    SearchedSong.Outcome.NotFound -> notice = "No lyrics found for this song. Check the title and artist, or try another spelling."
                    SearchedSong.Outcome.Failed -> notice = "Couldn't reach the lyrics sources. Check your connection and search again."
                }
            }
        }

        Text(
            "Find lyrics by song title and artist, with nothing playing. Synced lyrics play on the app's own timer: " +
                "use play, pause and the timeline to move through them. A song a player starts takes over.",
            style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyTextField(
                song,
                { song = it },
                placeholder = "Song title",
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Rounded.MusicNote, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(18.dp)) },
            )
            SpicyTextField(
                singer,
                { singer = it },
                placeholder = "Artist",
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Rounded.Person, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(18.dp)) },
                onSearch = ::submit,
            )
        }
        if (searching != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = SpicyColors.TextSecondary, strokeWidth = 2.5.dp)
            }
        } else {
            notice?.let { Text(it, style = SpicyType.Body.copy(color = SpicyColors.TextSecondary)) }
            SpicyButton(
                "Find lyrics",
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = song.isNotBlank() && singer.isNotBlank(),
            )
        }
    }
}
