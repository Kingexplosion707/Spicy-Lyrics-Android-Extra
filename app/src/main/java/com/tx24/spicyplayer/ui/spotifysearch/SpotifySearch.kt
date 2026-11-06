package com.tx24.spicyplayer.ui.spotifysearch

import com.tx24.spicyplayer.ui.components.BlockSkeleton
import com.tx24.spicyplayer.ui.components.TrackListSkeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackCandidate
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState

/*
 * Manual Spotify search: opens on the results the automatic match chose from, closest first; a
 * typed query searches Spotify afresh. Picking one uses that recording for this song from now on
 * (remembered per title, artist and length), the same as pasting its link.
 */

@Composable
fun SpotifySearchModal(
    visible: Boolean,
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    backdrop: HazeState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        title = "Find on Spotify",
        skeleton = {
            BlockSkeleton()
            TrackListSkeleton(rows = 4)
        },
    ) {
        var query by remember { mutableStateOf("") }
        // null: the automatic match's own search. Bumped per search, so the same query re-runs.
        var searched by remember { mutableStateOf<String?>(null) }
        var searchCount by remember { mutableIntStateOf(0) }
        var results by remember { mutableStateOf<List<SpotifyTrackCandidate>?>(null) }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(visible, state.title, state.artist) {
            if (!visible) return@LaunchedEffect
            query = listOf(state.title, state.artist).filter(String::isNotBlank).joinToString(" ")
            searched = null
            searchCount++
        }
        LaunchedEffect(searchCount) {
            if (searchCount == 0) return@LaunchedEffect
            results = null
            failed = false
            val found = viewModel.searchSpotify(searched)
            failed = found == null
            results = found.orEmpty()
        }
        fun submit() {
            if (query.isBlank()) return
            searched = query.trim()
            searchCount++
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
        ) {
            SpicyTextField(
                query,
                { query = it },
                placeholder = "Song and artist",
                modifier = Modifier.weight(1f),
                leading = { Icon(Icons.Rounded.Search, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(18.dp)) },
                onSearch = ::submit,
            )
            // As tall as the field.
            SpicyButton("Search", onClick = ::submit, modifier = Modifier.height(40.dp), enabled = query.isNotBlank())
        }
        val shown = results
        when {
            shown == null -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = SpicyColors.TextSecondary, strokeWidth = 2.5.dp)
            }
            shown.isEmpty() -> Text(
                if (failed) "Couldn't reach Spotify. Check your connection and search again." else "No songs found. Try another search.",
                style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
            // The rows' padding sits outside the column, so covers and text line up with the field.
            else -> Column(Modifier.bleed(SpicySpacing.S3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEach { candidate ->
                    ResultRow(candidate, current = candidate.id == state.detectedSpotifyId) {
                        viewModel.overrideSpotifyId(candidate.id)
                        onDismissRequest()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(candidate: SpotifyTrackCandidate, current: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (current) Modifier.background(SpicyColors.TintBg).border(1.dp, SpicyColors.HairlineStrong, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(SpicyRadii.Sm)).background(SpicyColors.TintBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(20.dp))
            if (candidate.coverUrl != null) {
                AsyncImage(candidate.coverUrl, null, Modifier.size(52.dp), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                candidate.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = SpicyType.Body.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium),
            )
            Text(
                listOf(candidate.artists.joinToString(), candidate.album).filter(String::isNotBlank).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary),
            )
            Text(
                candidate.id,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = SpicyType.Caption.copy(color = SpicyColors.TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            )
        }
        Text(formatDuration(candidate.durationMs), style = SpicyType.Caption.copy(color = SpicyColors.TextTertiary))
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Widens this by [x] on each side, past the parent's padding. */
private fun Modifier.bleed(x: Dp) = layout { measurable, constraints ->
    val extra = x.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
