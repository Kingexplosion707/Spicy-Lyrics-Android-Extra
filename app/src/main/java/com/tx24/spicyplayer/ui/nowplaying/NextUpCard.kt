package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.playback.QueueEntry
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.settings.QueueCover
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * The song queued next, as plain type beside its cover and without a plate, like the queue's
 * first row peeking in under the lyrics. Display only. When the next song changes while it
 * shows, the two cross-fade. [shown] is the card's own opacity, 0..1; [onHeight] reports its
 * measured height so the lyrics can fade out above it.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NextUpCard(entry: QueueEntry?, shown: () -> Float, onHeight: (Int) -> Unit, modifier: Modifier = Modifier) {
    val animate = LocalUiAnimations.current
    Box(modifier.graphicsLayer { alpha = shown() }.onSizeChanged { onHeight(it.height) }) {
        AnimatedContent(
            targetState = entry,
            transitionSpec = {
                val ms = if (animate) SpicyMotion.FAST_MS else 0
                fadeIn(tween(ms, delayMillis = 0)) togetherWith fadeOut(tween(ms))
            },
            contentKey = { it?.id },
            label = "nextUp",
        ) { shownEntry ->
            if (shownEntry != null) NextUpBody(shownEntry, animate)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NextUpBody(entry: QueueEntry, animate: Boolean) {
    val iterations = if (animate) Int.MAX_VALUE else 0
    // The card starts where the lyrics' lines (and the pinned credits) start: their column's inset.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = maxWidth * CompactHeaderMetrics.LYRICS_SIDE_INSET).widthIn(max = MAX_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            QueueCover(entry, size = ART)
            Column(Modifier.weight(1f)) {
                Text("Up Next", maxLines = 1, style = SpicyType.Footnote.copy(
                    color = SpicyColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = LABEL_SP.sp, lineHeight = 1.25.em))
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = iterations),
                    style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = TITLE_SP.sp, lineHeight = 1.25.em),
                )
                if (entry.subtitle.isNotBlank()) {
                    Text(
                        entry.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = iterations),
                        style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, fontSize = ARTIST_SP.sp, lineHeight = 1.25.em),
                    )
                }
            }
        }
    }
}

// The desktop card's proportions: a cover 2.9x the title's size, the label at .7em and the artists
// at .84em, 0.75em apart, in a column no wider than 19em.
private const val TITLE_SP = 13f
private const val LABEL_SP = 9.5f
private const val ARTIST_SP = 11f
private val ART = 38.dp
private val GAP = 10.dp
private val MAX_WIDTH = 250.dp
