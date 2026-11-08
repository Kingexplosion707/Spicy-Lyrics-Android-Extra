package com.tx24.spicyplayer.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyModalActions
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicyModalGap
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Share lyrics: the song's lines to pick from (the one being sung picked when it opens), a
 * preview of the card they make ([LyricsCard]), and buttons to copy the lines as text or share
 * the card as a picture through the system's share sheet.
 */
@Composable
internal fun ShareLyricsModal(
    visible: Boolean,
    lyrics: LyricsState.Ready?,
    title: String,
    artist: String,
    cover: Bitmap?,
    /** The lyric clock, to pick the line being sung. */
    positionMs: () -> Long,
    backdrop: HazeState?,
    onDismiss: () -> Unit,
) {
    // Kept through the closing animation.
    var shown by remember { mutableStateOf(lyrics) }
    if (visible && lyrics != null) shown = lyrics
    SpicyModal(visible = visible && shown != null, onDismissRequest = onDismiss, backdrop = backdrop, title = "Share lyrics") {
        val current = shown ?: return@SpicyModal
        ShareLyricsBody(current, visible, title, artist, cover, positionMs, onDismiss)
    }
}

@Composable
private fun ShareLyricsBody(
    lyrics: LyricsState.Ready,
    open: Boolean,
    title: String,
    artist: String,
    cover: Bitmap?,
    positionMs: () -> Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember(lyrics) { shareableLines(lyrics) }
    val picked = remember(lyrics) { mutableStateListOf<Int>() }
    val list = rememberLazyListState()
    // Opening picks the line being sung (the first, for lyrics without timing) and shows it.
    LaunchedEffect(lyrics, open) {
        if (!open || lines.isEmpty()) return@LaunchedEffect
        val now = positionMs()
        val at = if (lyrics.lyricsType == LyricsType.Static) 0
            else lines.indexOfLast { it.startMs <= now }.coerceAtLeast(0)
        picked.clear()
        picked += at
        list.scrollToItem((at - 1).coerceAtLeast(0))
    }
    val chosen = picked.sorted().map { lines[it].text }
    // A small preview, redrawn a moment after the picks settle.
    val preview by produceState<ImageBitmap?>(null, chosen, cover, title, artist) {
        if (chosen.isEmpty()) { value = null; return@produceState }
        delay(PREVIEW_SETTLE_MS)
        value = withContext(Dispatchers.Default) {
            runCatching { LyricsCard.render(chosen, title, artist, cover, PREVIEW_SCALE).asImageBitmap() }.getOrNull()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(SpicyRadii.Md)
            Box(
                Modifier
                    .width(PREVIEW_WIDTH)
                    .aspectRatio(LyricsCard.WIDTH / LyricsCard.HEIGHT.toFloat())
                    .clip(shape)
                    .background(SpicyColors.TintBg)
                    .border(1.dp, SpicyColors.Hairline, shape),
            ) {
                preview?.let { Image(it, contentDescription = "Card preview", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
        }
        Text(
            if (picked.size >= LyricsCard.MAX_LINES) "Up to ${LyricsCard.MAX_LINES} lines fit on a card." else "Tap the lines to share.",
            style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary),
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT),
            state = list,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val selected = index in picked
                LineRow(line.text, selected) {
                    when {
                        selected -> picked.remove(index)
                        picked.size < LyricsCard.MAX_LINES -> picked += index
                    }
                }
            }
        }
    }
    SpicyModalGap()
    SpicyModalActions {
        SpicyModalButton("Copy", {
            if (chosen.isEmpty()) return@SpicyModalButton
            copyLines(context, chosen, title, artist)
            onDismiss()
        })
        SpicyModalButton("Share", {
            if (chosen.isEmpty()) return@SpicyModalButton
            scope.launch {
                val uri = withContext(Dispatchers.IO) {
                    runCatching { saveCard(context, LyricsCard.render(chosen, title, artist, cover)) }.getOrNull()
                }
                if (uri == null) {
                    Toast.makeText(context, "Couldn't make the card", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "$title — $artist")
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(send, "Share lyrics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onDismiss()
            }
        }, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun LineRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    Text(
        text,
        style = SpicyType.Body.copy(
            color = if (selected) SpicyColors.TextPrimary else SpicyColors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SpicyColors.TintBg else Color.Transparent)
            .border(1.dp, if (selected) SpicyColors.HairlineStrong else Color.Transparent, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = SpicySpacing.S3, vertical = 8.dp),
    )
}

/** A line to pick: its text, and when it's sung. */
private class ShareLine(val text: String, val startMs: Long)

/** The lyrics' lead lines with words in them, as text. */
private fun shareableLines(lyrics: LyricsState.Ready): List<ShareLine> = lyrics.lines
    .filter { it.role == LineRole.LEAD && it.words.any { word -> word.text.isNotBlank() } }
    .map { ShareLine(lineText(it), it.startMs) }
    .filter { it.text.isNotBlank() }

internal fun lineText(line: TimedLine): String =
    line.words.joinToString("") { word -> if (word.attached) word.text else " " + word.text }
        .trim()
        .replace(Regex("\\s+"), " ")

private fun copyLines(context: Context, lines: List<String>, title: String, artist: String) {
    val text = lines.joinToString("\n") + "\n\n— $title · $artist"
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics", text))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Lyrics copied", Toast.LENGTH_SHORT).show()
    }
}

/** Writes [card] where the share sheet can read it (the last few only), and returns its address. */
private fun saveCard(context: Context, card: Bitmap): android.net.Uri {
    val folder = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
    folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP_CARDS - 1)?.forEach { it.delete() }
    val file = File(folder, "lyrics-${System.currentTimeMillis()}.png")
    file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
    card.recycle()
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private const val SHARE_FOLDER = "shared"
private const val KEEP_CARDS = 3
private const val PREVIEW_SCALE = 0.32f
private const val PREVIEW_SETTLE_MS = 120L
private val PREVIEW_WIDTH = 168.dp
private val LIST_MAX_HEIGHT = 280.dp
