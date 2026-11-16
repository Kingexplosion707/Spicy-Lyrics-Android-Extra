package com.tx24.spicyplayer.ivlyrics

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import com.tx24.spicyplayer.lyrics.motion.MotionTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    // Only real word timing plays as motion; line-timed songs keep Spicy's lyrics (word by word on
    // made-up timing where that's on).
    if (!enabled || type != LyricsType.Syllable || !SpicyBridge.supported()) return false
    // Asked on every recomposition of the lyrics: answered once per set of lines.
    lastFit?.let { (seen, fits) -> if (seen === lines) return fits }
    return SpicyBridge.motionCanPlay(lines.toIvLyrics()).also { lastFit = lines to it }
}

@Volatile private var lastFit: Pair<List<Line>, Boolean>? = null

/** A line longer than this reads better in Spicy's lyrics than as moving type. */
private const val MOTION_MAX_CHARS = 45
/** Spicy's lyrics take over this long before such a line, so it's there to be read from its start. */
private const val SWITCH_LEAD_MS = 300L

/** Whether [line] plays as moving type: it has its own word timing and isn't long. */
internal fun motionFriendly(line: Line): Boolean =
    !GeneratedWordTiming.needsTiming(line) && line.words.sumOf { it.text.length } <= MOTION_MAX_CHARS

/**
 * How much of the motion lyrics shows now (1) against Spicy's lyrics (0): motion while the line
 * being sung is [motionFriendly], Spicy's lyrics for a long line or one with no word timing of
 * its own, crossfading between them.
 */
@Composable
internal fun rememberMotionShown(lines: List<Line>, currentTimeMs: () -> Long, longLines: Boolean = true): androidx.compose.runtime.State<Float> {
    val spans = remember(lines, longLines) {
        lines.filter { it.role == LineRole.LEAD && it.words.isNotEmpty() }
            .map { Triple(it.startMs, it.endMs, if (longLines) motionFriendly(it) else !GeneratedWordTiming.needsTiming(it)) }
    }
    var motion by remember(spans) { mutableStateOf(spans.firstOrNull()?.third ?: true) }
    LaunchedEffect(spans) {
        while (true) {
            val at = currentTimeMs() + SWITCH_LEAD_MS
            spans.lastOrNull { it.first <= at }?.let { motion = it.third }
            delay(120L)
        }
    }
    return androidx.compose.animation.core.animateFloatAsState(
        if (motion) 1f else 0f, androidx.compose.animation.core.tween(380), label = "motionShown",
    )
}

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
    /** The page's dark scrim, 0 (none) to 1 (ivLyrics' own). */
    shade: Float = 0.35f,
    /** Spicy's word fill and glow over the page's own animation. */
    blend: Boolean = false,
    /** A [MotionTheme] id, or [MotionTheme.AUTO] for one from the song's genre. */
    theme: String = MotionTheme.AUTO,
) {
    val context = LocalContext.current
    val holder = remember(context) { PassThrough(context, SpicyBridge.createMotion(context)) }
    DisposableEffect(holder) { onDispose { SpicyBridge.release(holder.inner) } }
    val accent by MotionAccent.hex.collectAsState()
    LaunchedEffect(holder, documentId, romanize, accent) {
        SpicyBridge.motionSong(holder.inner, spotifyId, documentId, title, artist, accent, lines.toIvLyrics(romanize))
    }
    // The theme: the one picked, or Auto's from the artist's genre (looked up once per artist).
    val song by MotionAccent.song.collectAsState()
    var genreTheme by remember { mutableStateOf<MotionTheme?>(null) }
    LaunchedEffect(theme, song) {
        genreTheme = null
        if (theme == MotionTheme.AUTO && song.second.isNotBlank()) {
            genreTheme = MotionTheme.forGenres(MotionGenres.of(context, song.first, song.second))
        }
    }
    val chosen = if (theme == MotionTheme.AUTO) genreTheme ?: MotionTheme.Original else MotionTheme.of(theme) ?: MotionTheme.Original
    val look = remember(shade, blend, chosen) { MotionStyles.styleScript(MotionStyles.css(shade, blend, chosen)) }
    val words = remember(lines) { MotionStyles.wordsScript(lines) }
    val playing by rememberUpdatedState(isPlaying)
    val lookNow by rememberUpdatedState(look)
    val wordsNow by rememberUpdatedState(words)
    val blendNow by rememberUpdatedState(blend)
    var lookSent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(holder) {
        // The page runs its own clock between reports; four a second keeps seeks prompt.
        var tick = 0
        while (true) {
            val position = currentTimeMs()
            if (visible()) SpicyBridge.motionPlayback(holder.inner, position, playing)
            if (blendNow) SpicyBridge.motionEval(holder.inner, MotionStyles.positionScript(position, playing))
            // Spicy's look, laid over the page at once when it changes and again now and then
            // (the page may have reloaded).
            if (tick++ % 8 == 0 || lookSent != lookNow) {
                lookSent = lookNow
                SpicyBridge.motionEval(holder.inner, lookNow)
                if (blendNow) {
                    SpicyBridge.motionEval(holder.inner, MotionStyles.hookScript)
                    SpicyBridge.motionEval(holder.inner, wordsNow)
                }
            }
            delay(250L)
        }
    }
    Box(modifier) {
        AndroidView(factory = { holder }, modifier = Modifier.fillMaxSize())
        MotionBreaths(lines, currentTimeMs, Modifier.fillMaxSize())
    }
}

/** A break between sung lines this long or longer shows Spicy's three dots. */
private const val BREATH_MIN_MS = 3_000L

/**
 * Spicy's three dots in the breaks between vocals: each dot lights in turn across the break, the
 * three breathing, and they swell and fade as the singing comes back.
 */
@Composable
private fun MotionBreaths(lines: List<Line>, currentTimeMs: () -> Long, modifier: Modifier) {
    val breaks = remember(lines) {
        val leads = lines.filter { it.role == LineRole.LEAD && it.words.isNotEmpty() }.sortedBy { it.startMs }
        buildList {
            leads.firstOrNull()?.let { if (it.startMs >= BREATH_MIN_MS) add(0L to it.startMs) }
            var reached = leads.firstOrNull()?.endMs ?: 0L
            for (line in leads.drop(1)) {
                if (line.startMs - reached >= BREATH_MIN_MS) add(reached to line.startMs)
                reached = maxOf(reached, line.endMs)
            }
        }
    }
    if (breaks.isEmpty()) return
    var frame by remember { mutableStateOf(0L) }
    LaunchedEffect(breaks) { while (true) withFrameNanos { frame = it } }
    androidx.compose.foundation.Canvas(modifier) {
        if (frame < 0L) return@Canvas
        val now = currentTimeMs()
        val (from, to) = breaks.lastOrNull { it.first <= now && now < it.second } ?: return@Canvas
        val span = (to - from).toFloat()
        val progress = ((now - from) / span).coerceIn(0f, 1f)
        // In over the first 400 ms, out (swelling) over the last 450 ms.
        val appear = ((now - from) / 400f).coerceIn(0f, 1f)
        val leave = ((to - now) / 450f).coerceIn(0f, 1f)
        val breathe = 1f + 0.06f * kotlin.math.sin(now / 520f)
        val swell = 1f + (1f - leave) * 0.35f
        val radius = size.minDimension * 0.016f * breathe * swell
        val gap = radius * 3.4f
        val centre = center
        for (i in 0..2) {
            val lit = (progress * 3f - i).coerceIn(0f, 1f)
            val alpha = (0.28f + 0.72f * lit) * appear * leave
            val at = androidx.compose.ui.geometry.Offset(centre.x + (i - 1) * gap, centre.y)
            if (lit > 0f) drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = alpha * 0.22f * lit), radius * 2.2f, at)
            drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = alpha), radius * (0.86f + 0.14f * lit), at)
        }
    }
}

/** [lines] with each one's translation from [translation] (by line), for motion lyrics' subtitle. */
internal fun withTranslations(lines: List<Line>, translation: com.tx24.spicyplayer.translation.TranslationResult?): List<Line> =
    if (translation == null) lines else lines.mapIndexed { index, line ->
        translation.texts.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { line.copy(translation = it) } ?: line
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
