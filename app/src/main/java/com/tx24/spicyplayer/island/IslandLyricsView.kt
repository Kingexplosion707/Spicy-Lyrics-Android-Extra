package com.tx24.spicyplayer.island

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sameerasw.island.sdk.HostInfo
import com.sameerasw.island.sdk.compose.islandSnapshot
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.playback.LiveLyrics
import kotlinx.coroutines.delay

/**
 * The expanded island: the song (its cover and title open the player), the lyrics in Spicy Lyrics'
 * style (the line being sung filling word by word with a soft glow, its background vocals smaller
 * under it, a second singer's lines on the right, the translation under each line), the progress
 * (tap to move the song) and the controls. Tapping a line plays from it.
 *
 * It sits in the island's surface: its own black background clipped to the island's corners, room
 * for the camera at the top, its real height reported to the island ([onResize]) and pictures of
 * it sent for the island's animations ([onSnapshot]). It redraws per frame only while the song
 * plays, and four times a second in power saving.
 */
@Composable
internal fun IslandLyricsView(
    live: LiveLyrics,
    info: HostInfo?,
    onResize: (Int) -> Unit,
    onSnapshot: (Bitmap) -> Unit,
    onKeepAlive: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val density = LocalDensity.current
    val corner = with(density) { (info?.cornerPx ?: 0).toDp() }
    val camera = with(density) { (info?.cameraClearancePx ?: 0).toDp() }
    val padding = with(density) { (info?.expandedPaddingPx ?: 0).toDp() }.coerceAtLeast(14.dp)
    val powerSave = info?.powerSave == true
    val track = live.track
    val playing = live.playing
    // The clock, read while drawing; only a change of line recomposes.
    val clock = remember { mutableLongStateOf(live.positionMs()) }
    LaunchedEffect(playing, powerSave, track?.key) {
        clock.longValue = live.positionMs()
        while (playing) {
            if (powerSave) {
                delay(250L)
                clock.longValue = live.positionMs()
            } else {
                withFrameMillis { clock.longValue = live.positionMs() }
            }
        }
    }
    val seek: (Long) -> Unit = { ms ->
        live.seekTo(ms)
        clock.longValue = ms
        onKeepAlive()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .onSizeChanged { onResize(it.height) }
            .islandSnapshot { onSnapshot(it) }
            .clip(RoundedCornerShape(corner))
            .background(Color.Black),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = padding).padding(bottom = 14.dp)) {
            Spacer(Modifier.height(camera))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cover = remember(track?.cover) { track?.cover?.asImageBitmap() }
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClickLabel = "Open the player", onClick = onOpenPlayer),
                ) {
                    if (cover != null) Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable(onClickLabel = "Open the player", onClick = onOpenPlayer)) {
                    Text(track?.title.orEmpty(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist.orEmpty(), color = Color.White.copy(alpha = 0.65f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Lyrics(live, clock, powerSave, seek, Modifier.fillMaxWidth().heightIn(min = 112.dp))
            Spacer(Modifier.height(10.dp))
            Progress(clock, track?.durationMs ?: 0L, seek)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Control(Icons.Rounded.SkipPrevious, "Previous", 24.dp) { live.skipPrevious(); onKeepAlive() }
                Spacer(Modifier.width(20.dp))
                Control(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", 30.dp, filled = true) { live.playPause(); onKeepAlive() }
                Spacer(Modifier.width(20.dp))
                Control(Icons.Rounded.SkipNext, "Next", 24.dp) { live.skipNext(); onKeepAlive() }
            }
        }
    }
}

@Composable
private fun Control(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size + 18.dp).clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(size))
    }
}

@Composable
private fun Progress(clock: androidx.compose.runtime.MutableLongState, durationMs: Long, onSeek: (Long) -> Unit) {
    // Read in drawing only, so the bar moving doesn't recompose anything.
    Box(
        Modifier.fillMaxWidth().height(16.dp).then(
            if (durationMs <= 0) Modifier
            else Modifier.pointerInput(durationMs) {
                detectTapGestures { p -> onSeek((p.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)) }
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val fraction = if (durationMs > 0) (clock.longValue.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r)
            drawRoundRect(Color.White, size = size.copy(width = size.width * fraction), cornerRadius = r)
        }
    }
}

@Composable
private fun Lyrics(
    live: LiveLyrics,
    clock: androidx.compose.runtime.MutableLongState,
    powerSave: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val lyrics = live.lyrics
    if (lyrics == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                if (live.track == null) "Nothing playing" else if (live.settled) "No lyrics for this song" else "Finding the lyrics…",
                color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
            )
        }
        return
    }
    val groups = remember(lyrics) { IslandLyrics.groups(lyrics) }
    val static = lyrics.lyricsType == LyricsType.Static
    val wordSynced = lyrics.lyricsType == LyricsType.Syllable
    val translation = live.translation
    val index by remember(groups) { derivedStateOf { if (static) 0 else IslandLyrics.indexAt(groups, clock.longValue) } }
    val tapSource = remember { MutableInteractionSource() }
    fun tapTo(ms: Long): Modifier =
        if (static) Modifier else Modifier.clickable(interactionSource = tapSource, indication = null, onClickLabel = "Play from this line") { onSeek(ms) }
    fun translated(i: Int, line: TimedLine): String? = translation?.texts?.getOrNull(i) ?: line.translation
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(240)))
                .togetherWith(slideOutVertically(tween(240)) { -it / 3 } + fadeOut(tween(180)))
        },
        modifier = modifier,
        label = "islandLine",
    ) { i ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            groups.getOrNull(i - 1)?.takeIf { !static }?.let { prev ->
                Plain(prev.lead.text(), prev.lead.oppositeAligned, Color.White.copy(alpha = 0.32f), 13.5f, 1, tapTo(prev.startMs))
                Spacer(Modifier.height(4.dp))
            }
            val group = groups.getOrNull(i)
            if (group != null) Column(Modifier.fillMaxWidth().then(tapTo(group.startMs))) {
                val lead = group.lead
                if (static) {
                    Plain(lead.text(), lead.oppositeAligned, Color.White.copy(alpha = 0.9f), 19f, 3)
                } else {
                    Sung(lead, wordSynced && !lead.lineTimed, clock, 21f, lead.oppositeAligned, glow = !powerSave)
                }
                translated(group.index, lead)?.let {
                    Spacer(Modifier.height(2.dp))
                    Plain(it, lead.oppositeAligned, Color.White.copy(alpha = 0.58f), 12.5f, 2)
                }
                for ((n, backing) in group.backing) {
                    Spacer(Modifier.height(3.dp))
                    if (static) {
                        Plain(backing.text(), lead.oppositeAligned, Color.White.copy(alpha = 0.6f), 14f, 2)
                    } else {
                        Sung(backing, wordSynced && !backing.lineTimed, clock, 14.5f, lead.oppositeAligned, glow = !powerSave, dim = 0.26f, weight = FontWeight.Bold)
                    }
                    translated(n, backing)?.let { Plain(it, lead.oppositeAligned, Color.White.copy(alpha = 0.5f), 11.5f, 1) }
                }
            }
            groups.getOrNull(i + 1)?.let { next ->
                Spacer(Modifier.height(6.dp))
                Plain(next.lead.text(), next.lead.oppositeAligned, Color.White.copy(alpha = 0.42f), 14.5f, 2, tapTo(next.startMs))
            }
        }
    }
}

@Composable
private fun Plain(text: String, opposite: Boolean, color: Color, sizeSp: Float, maxLines: Int, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.SemiBold,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        textAlign = if (opposite) TextAlign.End else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().then(modifier),
    )
}

/** One line filling word by word ([wordSynced]) or as a whole, drawn by hand for the fill and glow. */
@Composable
private fun Sung(
    line: TimedLine,
    wordSynced: Boolean,
    clock: androidx.compose.runtime.MutableLongState,
    sizeSp: Float,
    opposite: Boolean,
    glow: Boolean,
    dim: Float = 0.38f,
    weight: FontWeight = FontWeight.ExtraBold,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val text = remember(line) { line.text() }
    val spans = remember(line) {
        val out = ArrayList<IntArray>()
        var at = 0
        for ((n, w) in line.words.withIndex()) {
            val word = w.text.trim()
            if (n > 0 && !w.attached) at += 1
            out += intArrayOf(at, at + word.length)
            at += word.length
        }
        out
    }
    val style = TextStyle(
        fontSize = sizeSp.sp, fontWeight = weight, lineHeight = (sizeSp * 1.18f).sp,
        textAlign = if (opposite) TextAlign.End else TextAlign.Start,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val layout = remember(text, widthPx, sizeSp, opposite, weight) {
            measurer.measure(text, style, maxLines = 3, overflow = TextOverflow.Ellipsis, constraints = Constraints(minWidth = widthPx, maxWidth = widthPx))
        }
        val heightDp = with(density) { layout.size.height.toDp() }
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            val now = clock.longValue
            // Shadow.None: a layout keeps the last shadow drawn with it.
            drawText(layout, color = Color.White.copy(alpha = dim), shadow = Shadow.None)
            val glowShadow = if (glow) Shadow(Color.White.copy(alpha = 0.5f), Offset.Zero, sizeSp * density.density * 0.4f) else Shadow.None
            val pieces: List<Pair<IntArray, Float>> = if (wordSynced) {
                line.words.indices.map { k ->
                    val w = line.words[k]
                    spans[k] to ((now - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            } else {
                listOf(intArrayOf(0, text.length) to ((now - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f))
            }
            for ((span, progress) in pieces) {
                if (progress <= 0f || span[1] <= span[0] || span[1] > text.length) continue
                val firstLine = layout.getLineForOffset(span[0])
                val lastLine = layout.getLineForOffset(span[1] - 1)
                if (firstLine == lastLine) {
                    val first = layout.getBoundingBox(span[0])
                    val last = layout.getBoundingBox(span[1] - 1)
                    val right = first.left + (last.right - first.left) * progress
                    clipRect(first.left, first.top - 8f, right, first.bottom + 8f) {
                        drawText(layout, color = Color.White, shadow = glowShadow)
                    }
                } else {
                    val total = (firstLine..lastLine).sumOf { (layout.getLineRight(it) - layout.getLineLeft(it)).toDouble() }.toFloat()
                    var remaining = total * progress
                    for (l in firstLine..lastLine) {
                        val left = layout.getLineLeft(l)
                        val width = layout.getLineRight(l) - left
                        val take = remaining.coerceIn(0f, width)
                        if (take > 0f) {
                            clipRect(left, layout.getLineTop(l), left + take, layout.getLineBottom(l)) {
                                drawText(layout, color = Color.White, shadow = glowShadow)
                            }
                        }
                        remaining -= width
                        if (remaining <= 0f) break
                    }
                }
            }
        }
    }
}
