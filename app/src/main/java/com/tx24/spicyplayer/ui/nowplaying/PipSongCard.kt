package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.tx24.spicyplayer.lyrics.spicy.models.MiniCredit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * The floating window's song card, in place of the lyrics: the cover with the title and artist,
 * centred and sized to the window. A wide window puts the text beside the cover, any other under it.
 */
@Composable
internal fun PipSongCard(
    title: String,
    artist: String,
    artwork: Bitmap?,
    artworkUri: String?,
    hasSession: Boolean,
    modifier: Modifier = Modifier,
) {
    val cover = rememberSessionArtwork(artwork, artworkUri, maxDimension = PIP_ARTWORK_MAX_PX, hasSession = hasSession)
    BoxWithConstraints(modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth > maxHeight * 1.2f
        // Wide: the cover takes the window's whole height, the text the rest of its width.
        val coverSize = if (wide) minOf(maxHeight, maxWidth * 0.45f) else minOf(maxWidth * 0.7f, maxHeight * 0.55f)
        val textSp = if (wide) (maxHeight.value / 6.5f).coerceIn(13f, 26f) else (minOf(maxWidth, maxHeight).value / 9f).coerceIn(11f, 22f)
        val titleStyle = SpicyType.Body.copy(fontSize = textSp.sp, fontWeight = FontWeight.Bold, lineHeight = 1.2.em)
        val artistStyle = SpicyType.Body.copy(fontSize = (textSp * 0.82f).sp, color = SpicyColors.TextSecondary, lineHeight = 1.2.em)
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cover(cover?.bitmap, coverSize)
                Column(Modifier.weight(1f)) {
                    CardLine(title, titleStyle, Color.White, Alignment.CenterStart)
                    CardLine(artist, artistStyle, SpicyColors.TextSecondary, Alignment.CenterStart)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(cover?.bitmap, coverSize)
                Spacer(Modifier.height(8.dp))
                CardLine(title, titleStyle, Color.White)
                CardLine(artist, artistStyle, SpicyColors.TextSecondary)
            }
        }
    }
}

/** One line of the card, scrolling like the song header's when it's too long to fit. */
@Composable
private fun CardLine(text: String, style: TextStyle, color: Color, alignment: Alignment = Alignment.Center) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        MarqueeLine(text, style, color, constraints.maxWidth.toFloat())
    }
}

@Composable
private fun Cover(bitmap: Bitmap?, size: Dp) {
    val shape = RoundedCornerShape(size * 0.06f)
    if (bitmap == null) {
        Box(Modifier.size(size).clip(shape).background(SpicyColors.TintBg))
    } else {
        Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    }
}

/**
 * The song along the bottom of a square or tall floating window, docked like a player: the title,
 * the artist under it, then a thin timeline with the time played and the song's length under its
 * ends. Sized off the window's width, over a scrim that keeps it legible on a bright background.
 */
@Composable
internal fun PipDock(
    title: String,
    artist: String,
    positionMs: () -> Long,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    /** A small cover beside the title and artist, as tall as the two of them. */
    cover: Boolean = false,
    artwork: Bitmap? = null,
    artworkUri: String? = null,
    hasSession: Boolean = true,
    /** Where the lyrics come from, as one short line under the times; null shows none. */
    credit: MiniCredit? = null,
) {
    val coverArt = if (cover) rememberSessionArtwork(artwork, artworkUri, maxDimension = PIP_DOCK_ARTWORK_PX, hasSession = hasSession) else null
    // Ticks while playing; paused, the timeline holds still.
    var now by remember { mutableLongStateOf(positionMs()) }
    LaunchedEffect(isPlaying) {
        now = positionMs()
        while (isPlaying) {
            withFrameMillis { now = positionMs() }
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))),
    ) {
        val sp = (maxWidth.value * 0.05f).coerceIn(11f, 18f)
        val gutter = maxWidth * 0.06f
        Column(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 6.dp, bottom = gutter * 0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cover) {
                    // The title's and artist's lines together: 1.15 × sp + 1.2 × 0.72 sp.
                    Cover(coverArt?.bitmap, (sp * 2.05f).dp)
                    Spacer(Modifier.size((sp * 0.55f).dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = SpicyType.Body.copy(fontSize = sp.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist,
                        style = SpicyType.Body.copy(fontSize = (sp * 0.72f).sp, color = SpicyColors.TextSecondary, lineHeight = 1.2.em),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val progress = if (durationMs > 0) (now.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.fillMaxWidth().padding(top = (sp * 0.6f).dp).height((sp * 0.28f).dp.coerceAtLeast(3.dp))) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = radius)
                drawRoundRect(Color.White.copy(alpha = 0.9f), size = size.copy(width = size.width * progress), cornerRadius = radius)
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val timeStyle = SpicyType.Caption.copy(fontSize = (sp * 0.6f).sp, color = SpicyColors.TextSecondary, fontWeight = FontWeight.Medium)
                Text(clock(now), style = timeStyle)
                credit?.let {
                    MiniCreditLine(it, timeStyle, SpicyColors.TextSecondary, Modifier.weight(1f).padding(horizontal = 6.dp))
                }
                if (durationMs > 0) Text(clock(durationMs), style = timeStyle)
            }
        }
    }
}

/** "2:33", or "1:02:05" past an hour. */
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val (h, m, sec) = Triple(total / 3600, total / 60 % 60, total % 60)
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private const val PIP_ARTWORK_MAX_PX = 512
private const val PIP_DOCK_ARTWORK_PX = 128
