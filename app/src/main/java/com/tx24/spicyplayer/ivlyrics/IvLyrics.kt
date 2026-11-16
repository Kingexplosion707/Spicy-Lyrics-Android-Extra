package com.tx24.spicyplayer.ivlyrics

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tx24.spicyplayer.lyrics.spicy.models.GeneratedWordTiming
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import kotlinx.coroutines.delay
import kr.ivlis.ivlyricsandroid.SpicyBridge

/*
 * ivLyrics' features in Spicy's screens, through [SpicyBridge]: motion lyrics, vinyl mode and the
 * YouTube music video background.
 */

/** Spicy's lines as ivLyrics takes them: each word's text carries the space after it. */
internal fun Line.toIvLyrics(romanize: Boolean = false): SpicyBridge.Line {
    val syllables = words.mapIndexed { index, word ->
        val text = if (romanize) word.romanizedText ?: word.text else word.text
        val next = words.getOrNull(index + 1)
        SpicyBridge.Syllable(if (next != null && !next.isPartOfWord) "$text " else text, word.startMs, word.endMs)
    }
    val timed = !lineTimed && !untimed
    return SpicyBridge.Line(
        syllables.joinToString("") { it.text }.trim(),
        startMs,
        endMs,
        if (timed) syllables else emptyList(),
        role == LineRole.BACKGROUND,
        agent,
        translation,
    )
}

internal fun List<Line>.toIvLyrics(romanize: Boolean = false): List<SpicyBridge.Line> =
    filter { it.role != LineRole.INTERLUDE && it.words.isNotEmpty() }.map { it.toIvLyrics(romanize) }

/** The renderer's lines for [lyrics], as the lyrics views build them. */
internal fun rendererLines(lyrics: com.tx24.spicyplayer.lyrics.LyricsState.Ready): List<Line> = lyrics.lines.map { line ->
    Line(
        words = line.words.map { word ->
            com.tx24.spicyplayer.lyrics.spicy.models.Word(word.text, word.startMs, word.endMs, isPartOfWord = word.attached, romanizedText = word.romanized)
        },
        startMs = line.startMs,
        endMs = line.endMs,
        agent = line.agent,
        role = line.role,
        groupId = line.groupId,
        oppositeAligned = line.oppositeAligned,
        lineTimed = line.lineTimed,
        untimed = line.untimed,
    )
}

/**
 * ivLyrics' furigana for [lines], laid on each word as its reading; the lines as they are until
 * it answers, or where they have no kanji.
 */
@Composable
internal fun rememberFurigana(enabled: Boolean, lines: List<Line>, documentId: String): List<Line> {
    val context = LocalContext.current
    var readings by remember(documentId) { mutableStateOf<Array<String>?>(null) }
    LaunchedEffect(enabled, documentId) {
        readings = null
        if (!enabled || !SpicyBridge.supported()) return@LaunchedEffect
        if (lines.none { line -> line.words.any { word -> word.text.any { it in '\u4E00'..'\u9FFF' } } }) return@LaunchedEffect
        SpicyBridge.furigana(context, "", "", lines.lastOrNull()?.endMs ?: 0L, lines.map { it.toIvLyrics() }) { readings = it }
    }
    val found = readings ?: return lines
    return remember(lines, found) {
        lines.mapIndexed { index, line ->
            val ruby = found.getOrNull(index).orEmpty()
            val perWord = com.tx24.spicyplayer.network.data.providers.FuriganaReadings.forWords(ruby, line.words.map { it.text })
            if (perWord.all { it == null }) line
            // Kana stays kana on a line that has readings, so it doesn't mix with romaji.
            else line.copy(words = line.words.mapIndexed { i, word -> word.copy(romanizedText = perWord[i] ?: word.text) })
        }
    }
}

/** Whether [lines] play as motion lyrics; otherwise the usual lyrics are shown. */
internal fun motionLyricsFit(enabled: Boolean, lines: List<Line>, type: LyricsType): Boolean {
    // Line-timed songs play too, on made-up word timing ([GeneratedWordTiming]).
    if (!enabled || (type != LyricsType.Syllable && type != LyricsType.Line) || !SpicyBridge.supported()) return false
    // Asked on every recomposition of the lyrics: answered once per set of lines.
    lastFit?.let { (seen, fits) -> if (seen === lines) return fits }
    return SpicyBridge.motionCanPlay(GeneratedWordTiming.fillIn(lines).toIvLyrics()).also { lastFit = lines to it }
}

@Volatile private var lastFit: Pair<List<Line>, Boolean>? = null

/** Holds an ivLyrics view and lets touches through to what's around it. */
private class PassThrough(context: Context, val inner: View) : FrameLayout(context) {
    init { addView(inner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * ivLyrics' motion lyrics: its moving-type page, fed the song and the position. It fills
 * whatever it is given, upright or wide, so the lyrics screen, the wallpaper, the overlay and the
 * always-on display all show it.
 */
@Composable
internal fun MotionLyricsView(
    lines: List<Line>,
    documentId: String,
    currentTimeMs: () -> Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    romanize: Boolean = false,
    visible: () -> Boolean = { true },
    @Suppress("UNUSED_PARAMETER") maxFrameRate: Float = 0f,
    title: String = "",
    artist: String = "",
    spotifyId: String? = null,
) {
    val context = LocalContext.current
    val holder = remember(context) { PassThrough(context, SpicyBridge.createMotion(context)) }
    DisposableEffect(holder) { onDispose { SpicyBridge.release(holder.inner) } }
    LaunchedEffect(holder, documentId, romanize) {
        SpicyBridge.motionSong(holder.inner, spotifyId, documentId, title, artist, GeneratedWordTiming.fillIn(lines).toIvLyrics(romanize))
    }
    val playing by rememberUpdatedState(isPlaying)
    LaunchedEffect(holder) {
        // The page runs its own clock between reports; four a second keeps seeks prompt.
        while (true) {
            if (visible()) SpicyBridge.motionPlayback(holder.inner, currentTimeMs(), playing)
            delay(250L)
        }
    }
    AndroidView(factory = { holder }, modifier = modifier)
}

/** ivLyrics' vinyl mode: the record, its tonearm and the line being sung. */
@Composable
internal fun VinylMode(
    title: String,
    artist: String,
    album: String,
    spotifyId: String?,
    artwork: Bitmap?,
    durationMs: Long,
    isPlaying: Boolean,
    lines: List<Line>,
    currentTimeMs: () -> Long,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val closeNow by rememberUpdatedState(onClose)
    val playPauseNow by rememberUpdatedState(onPlayPause)
    val seekNow by rememberUpdatedState(onSeek)
    val pauseNow by rememberUpdatedState(onPause)
    val view = remember(context) {
        SpicyBridge.createVinyl(context, object : SpicyBridge.VinylControls {
            override fun close() = closeNow()
            override fun playPause() = playPauseNow()
            override fun seek(positionMs: Long) = seekNow(positionMs)
            override fun pause() = pauseNow()
        })
    }
    DisposableEffect(view) { onDispose { SpicyBridge.release(view) } }
    var lastSong by remember { mutableStateOf<String?>(null) }
    val song = "$title\u001f$artist"
    LaunchedEffect(view, song, artwork) {
        SpicyBridge.vinylTrack(view, title, artist, album, spotifyId, durationMs, currentTimeMs(), isPlaying, artwork, lastSong != null && lastSong != song)
        lastSong = song
    }
    val playing by rememberUpdatedState(isPlaying)
    val leadLines = remember(lines) { lines.filter { it.role == LineRole.LEAD && it.words.isNotEmpty() } }
    val bridged = remember(leadLines) { GeneratedWordTiming.fillIn(leadLines).map { it.toIvLyrics() } }
    LaunchedEffect(view, bridged) {
        var shownLine = -2
        var lastPush = 0L
        while (true) {
            withFrameNanos { now ->
                val position = currentTimeMs()
                val index = leadLines.indexOfLast { it.startMs <= position }.takeIf { it >= 0 && position <= leadLines[it].endMs + 800 } ?: -1
                // The line and the record are told now and then; the fill runs every frame.
                if (index != shownLine || now - lastPush > 250_000_000L) {
                    shownLine = index
                    lastPush = now
                    SpicyBridge.vinylLine(view, bridged.getOrNull(index), position, playing)
                    SpicyBridge.vinylPlayback(view, position, durationMs, playing)
                }
                SpicyBridge.vinylFrame(view, position, now)
            }
            if (!playing) delay(200L)
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/**
 * ivLyrics' YouTube background: the song's music video, playing in time with the song, over
 * whatever background is picked. [onFound] says whether the song has one, so the screen can show
 * its own background where it doesn't.
 */
@Composable
internal fun MusicVideoBackground(
    title: String,
    artist: String,
    album: String,
    spotifyId: String?,
    durationMs: Long,
    lines: List<Line>,
    isPlaying: Boolean,
    currentTimeMs: () -> Long,
    onFound: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val holder = remember(context) { PassThrough(context, SpicyBridge.createVideo(context)) }
    DisposableEffect(holder) { onDispose { SpicyBridge.release(holder.inner) } }
    val found by rememberUpdatedState(onFound)
    val bridged = remember(lines) { lines.toIvLyrics() }
    LaunchedEffect(holder, title, artist, bridged.isNotEmpty()) {
        found(false)
        if (title.isNotBlank()) {
            SpicyBridge.videoSong(holder.inner, title, artist, album, spotifyId, durationMs, bridged) { found(it) }
        }
    }
    val playing by rememberUpdatedState(isPlaying)
    LaunchedEffect(holder) {
        while (true) {
            SpicyBridge.videoPlayback(holder.inner, currentTimeMs(), playing)
            delay(500L)
        }
    }
    AndroidView(factory = { holder }, modifier = modifier)
}
