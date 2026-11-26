package com.tx24.spicyplayer.ivlyrics

// The desktop version of :app's IvLyrics.kt (same API). Motion lyrics run ivLyrics' page in the
// desktop's web engine (MotionPage); vinyl mode and the music video background are Android views.

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/**
 * [lines] with the translation shown for each laid on it, so motion lyrics show it under the
 * moving type as the lyrics do. [lines] are the song's own lines, one per entry of [result].
 */
internal fun withTranslations(lines: List<Line>, result: com.tx24.spicyplayer.translation.TranslationResult?): List<Line> {
    if (result == null || result.texts.size != lines.size) return lines
    return lines.mapIndexed { index, line ->
        val text = result.texts[index]?.takeIf { it.isNotBlank() } ?: return@mapIndexed line
        line.copy(translation = com.tx24.spicyplayer.translation.plainTranslation(line.words.joinToString("") { it.text }, text))
    }
}

/** How much of the area the motion page keeps, at the top, when Spicy's lyrics show with it. */
internal const val MOTION_MERGE_SHARE = 0.6f
/** How far Spicy's lyrics reach up under the motion page's faded lower edge. */
private const val MOTION_MERGE_OVERLAP = 0.05f
/** The motion page's lower edge that fades out over Spicy's lyrics, as a share of its height. */
private const val MOTION_MERGE_FADE = 0.2f

/** What part of Spicy's lyrics shows with motion lyrics. */
internal enum class SpicyWithMotion {
    /** Every line, word by word with its glow, under the motion page (Merge with Spicy's lyrics). */
    Lyrics,
    /** Only the three interlude dots, in the gaps between vocals. */
    Dots,
}

/**
 * Motion lyrics for the whole song, with Spicy's lyrics alongside: its interlude dots in the
 * gaps between vocals, or with [merge] every line under the moving type, each in its own part
 * of the area so neither covers the other. [timeline] is Spicy's display timeline (interludes
 * included); [spicy] draws Spicy's lyrics for the lines it's given in the place it's given.
 *
 * The motion page is never put in a layer of its own (alpha, tint, blur): a web view inside one
 * draws black on some phones. Fades at its edges are done inside the page.
 */
@Composable
internal fun MotionWithSpicy(
    motionLines: List<Line>,
    timeline: List<Line>,
    documentId: String,
    currentTimeMs: () -> Long,
    isPlaying: Boolean,
    romanize: Boolean,
    visible: () -> Boolean,
    shade: Float,
    merge: Boolean,
    modifier: Modifier = Modifier,
    maxFrameRate: Float = 0f,
    /** Motion lyrics' look; Auto picks one by the song's genre. */
    theme: MotionTheme = MotionTheme.Classic,
    spicy: @Composable (lines: List<Line>, part: SpicyWithMotion, modifier: Modifier) -> Unit,
) {
    androidx.compose.foundation.layout.Box(modifier) {
        MotionLyricsView(
            // Merged, the translations show under Spicy's lines instead.
            lines = if (merge) remember(motionLines) { motionLines.map { if (it.translation == null) it else it.copy(translation = null) } } else motionLines,
            documentId = documentId,
            currentTimeMs = currentTimeMs,
            isPlaying = isPlaying,
            romanize = romanize,
            visible = visible,
            shade = shade,
            maxFrameRate = maxFrameRate,
            fadeBottom = if (merge) MOTION_MERGE_FADE else 0f,
            theme = theme,
            modifier = if (merge) Modifier.fillMaxWidth().fillMaxHeight(MOTION_MERGE_SHARE) else Modifier.fillMaxSize(),
        )
        if (merge) {
            spicy(
                timeline,
                SpicyWithMotion.Lyrics,
                Modifier.fillMaxWidth().fillMaxHeight(1f - MOTION_MERGE_SHARE + MOTION_MERGE_OVERLAP)
                    .align(androidx.compose.ui.Alignment.BottomCenter),
            )
        } else {
            val dots = remember(timeline) { timeline.filter { it.role == LineRole.INTERLUDE } }
            if (dots.isNotEmpty()) spicy(dots, SpicyWithMotion.Dots, Modifier.fillMaxSize())
        }
    }
}

/**
 * ivLyrics' motion lyrics: its moving-type page, fed the song and the position, in the desktop's
 * web engine ([com.tx24.spicyplayer.desktop.motion.MotionPage]).
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
    shade: Float = 0.35f,
    fadeBottom: Float = 0f,
    theme: MotionTheme = MotionTheme.Classic,
) {
    val context = LocalContext.current
    val page = remember { com.tx24.spicyplayer.desktop.motion.MotionPage() }
    DisposableEffect(page) { onDispose { page.release() } }
    val accent by MotionAccent.hex.collectAsState()
    val translations = remember(lines) { lines.map { it.translation }.hashCode() }
    val genre by MotionGenre.genre.collectAsState()
    val shownTheme = theme.resolve(genre, documentId)
    LaunchedEffect(page, documentId, romanize, accent, translations, shownTheme) {
        val (uri, json, themeJson) = SpicyBridge.motionSong(
            spotifyId, documentId, title, artist, accent, lines.toIvLyrics(romanize),
            if (shownTheme.spec == null) "" else shownTheme.name, shownTheme.json(),
        )
        page.setSong(uri, json, themeJson)
    }
    val prefs = remember(context) { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var comfort by remember(prefs) { mutableStateOf(MotionComfort.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == null || key in MotionComfort.KEYS) comfort = MotionComfort.read(changed)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(page, comfort) { page.setComfort(comfort.intensity.id, comfort.rotation.id, comfort.safe) }
    val redirectsBefore = remember(page) { MotionRedirect.requests.value }
    val redirects by MotionRedirect.requests.collectAsState()
    LaunchedEffect(page, redirects) { if (redirects != redirectsBefore) page.redirect() }
    val playing by rememberUpdatedState(isPlaying)
    val shadeNow by rememberUpdatedState(shade)
    val fadeNow by rememberUpdatedState(fadeBottom)
    LaunchedEffect(page, fadeBottom) {
        var tick = 0
        var active = true
        while (true) {
            val shown = visible()
            if (shown != active) { active = shown; page.setActive(shown) }
            if (shown) page.setPlayback(currentTimeMs(), playing)
            if (tick < 16 || tick % 8 == 0) page.setLook(shadeNow, fadeNow)
            tick++
            delay(250L)
        }
    }
    com.tx24.spicyplayer.desktop.motion.MotionPageView(page, modifier)
}

/** Vinyl mode is one of ivLyrics' Android views: on the desktop it closes straight away. */
@Composable
internal fun VinylMode(
    title: String,
    artist: String,
    album: String,
    spotifyId: String?,
    artwork: android.graphics.Bitmap?,
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
    LaunchedEffect(Unit) { onClose() }
}

/** The YouTube background is one of ivLyrics' Android views: on the desktop no song has one. */
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
    LaunchedEffect(Unit) { onFound(false) }
}
