package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.ui.theme.SpicyMotion

import com.tx24.spicyplayer.ui.components.LocalUiAnimations

import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.snap

import androidx.compose.animation.animateColorAsState

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.playback.QueueEntry
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.TrackListSkeleton
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState

/**
 * The player's queue, a screen of quick settings: the playing song marked and scrolled to, a tap
 * on another jumping there (or saying the player won't). Only players that share their queue
 * have one; none let other apps reorder or remove songs. [modifier] gives it its height: it
 * scrolls itself.
 */
/** What the queue list shows: a section label or a song. */
private sealed interface QueueItem {
    val key: String

    class Label(val text: String, override val key: String) : QueueItem
    class Song(val entry: QueueEntry, override val key: String) : QueueItem
}

/**
 * The queue as list rows: the played songs under "Earlier" (when there are any), the playing one,
 * then "Up next". The player gives no other sections. A player may list one queue id twice (a
 * song queued twice), and the list's keys must differ.
 */
private fun queueItems(queue: List<QueueEntry>): List<QueueItem> {
    val seen = HashMap<Long, Int>()
    val current = queue.indexOfFirst { it.current }
    val out = ArrayList<QueueItem>(queue.size + 2)
    queue.forEachIndexed { i, entry ->
        if (current > 0 && i == 0) out += QueueItem.Label("Earlier", "label-earlier")
        if (current >= 0 && i == current + 1) out += QueueItem.Label("Up next", "label-next")
        val n = (seen[entry.id] ?: 0) + 1
        seen[entry.id] = n
        out += QueueItem.Song(entry, if (n == 1) entry.id.toString() else "${entry.id}#$n")
    }
    return out
}

/**
 * The player's queue, a screen of quick settings: the playing song marked and scrolled to, a tap
 * on another jumping there (or saying the player won't). Rows keep their place by queue id, so
 * they slide to new ones when the list changes, and the first rows rise in one after another when
 * it opens. Only players that share their queue have one; none let other apps reorder or remove
 * songs. [modifier] gives it its height: it scrolls itself.
 */
@Composable
fun QueueList(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, onJumped: () -> Unit, modifier: Modifier = Modifier) {
    val items = remember(state.queue) { queueItems(state.queue) }
    val current = items.indexOfFirst { it is QueueItem.Song && it.entry.current }.coerceAtLeast(0)
    // Opens with the playing song near the top, one before it in view.
    val firstShown = (current - 1).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = firstShown)
    val animate = LocalUiAnimations.current
    // Only the rows there when it opens rise in turn; ones scrolled to later just appear.
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(OPEN_CASCADE_MS)
        opening = false
    }
    // The next song starting while the queue is open: follow it if it's out of view.
    LaunchedEffect(current) {
        val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isNotEmpty() && current !in visible.drop(1).dropLast(1)) {
            val target = (current - 1).coerceAtLeast(0)
            if (animate) list.animateScrollToItem(target) else list.scrollToItem(target)
        }
    }
    if (state.queue.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("The player isn't sharing a queue right now.", style = SpicyType.Body.copy(color = SpicyColors.TextSecondary))
        }
        return
    }
    LazyColumn(modifier.fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val place = if (animate) Modifier.animateItem() else Modifier
            val rank = index - firstShown
            val cascade = if (opening && animate && rank in 0 until OPEN_CASCADE_ROWS) rank else -1
            when (item) {
                is QueueItem.Label -> QueueLabel(item.text, place.cascaded(cascade))
                is QueueItem.Song -> QueueRow(item.entry, place.cascaded(cascade)) {
                    viewModel.skipToQueueItem(item.entry.id)
                    onJumped()
                }
            }
        }
    }
}

private const val OPEN_CASCADE_ROWS = 8
private const val OPEN_CASCADE_STEP_MS = 40L
private const val OPEN_CASCADE_MS = 900L

/** Fades and lifts in after [rank] steps; a negative [rank] is already there. */
@Composable
private fun Modifier.cascaded(rank: Int): Modifier {
    val shown = remember { Animatable(if (rank >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (shown.value < 1f) {
            delay(rank * OPEN_CASCADE_STEP_MS)
            shown.animateTo(1f, tween(260, easing = SpicyMotion.Standard))
        }
    }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    return graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * lift
    }
}

@Composable
private fun QueueLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(start = SpicySpacing.S3, top = 10.dp, bottom = 2.dp),
        style = SpicyType.Footnote.copy(color = SpicyColors.TextTertiary, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun QueueRow(entry: QueueEntry, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SpicyRadii.Md)
    // The playing song's highlight fades across to the next one.
    val spec = if (LocalUiAnimations.current) tween<Color>(SpicyMotion.FAST_MS * 2) else snap()
    val fill by animateColorAsState(if (entry.current) SpicyColors.TintBg else Color.Transparent, spec, label = "queueFill")
    val edge by animateColorAsState(if (entry.current) SpicyColors.HairlineStrong else Color.Transparent, spec, label = "queueEdge")
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .clickable(enabled = !entry.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = SpicySpacing.S3, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        QueueCover(entry)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.current) Box(Modifier.padding(end = 6.dp).size(6.dp).background(SpicyColors.StatusSuccess, CircleShape))
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = SpicyType.Body.copy(fontWeight = if (entry.current) FontWeight.SemiBold else FontWeight.Medium),
                )
            }
            if (entry.subtitle.isNotBlank()) {
                Text(entry.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            }
        }
    }
}

/** The entry's cover: the bitmap the player sent, else its URI, else a note. */
@Composable
internal fun QueueCover(entry: QueueEntry, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.size(size).clip(shape).background(SpicyColors.TintBgPressed), contentAlignment = Alignment.Center) {
        val bitmap = remember(entry.icon) { entry.icon?.asImageBitmap() }
        when {
            bitmap != null -> Image(bitmap, null, Modifier.size(size), contentScale = ContentScale.Crop)
            entry.iconUri != null -> AsyncImage(entry.iconUri, null, Modifier.size(size), contentScale = ContentScale.Crop)
            else -> Icon(Icons.Rounded.MusicNote, null, tint = SpicyColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
