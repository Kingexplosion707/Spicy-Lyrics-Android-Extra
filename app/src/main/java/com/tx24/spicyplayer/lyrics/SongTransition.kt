package com.tx24.spicyplayer.lyrics

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.translation.TranslationResult
import kotlinx.coroutines.delay

/** The last song's lyrics during a song change ([rememberSongTransition]). */
internal class OutgoingLyrics(
    val lyrics: LyricsState.Ready,
    val translation: TranslationResult?,
    /** Where they had got to when the song changed, and when that was (uptime). */
    private val fromMs: Long,
    private val atMs: Long,
    /** They carry on in time (the last song ran into the next); otherwise they hold still. */
    val runs: Boolean,
) {
    fun timeMs(): Long = if (runs) fromMs + (SystemClock.uptimeMillis() - atMs) else fromMs
}

internal class SongTransition(val outgoing: OutgoingLyrics?, private val progress: () -> Float) {
    /** The new song's lyrics: hidden while the last song's hold, fading in as they leave. */
    fun incomingAlpha(): Float = if (outgoing != null) progress() else 1f
    fun outgoingAlpha(): Float = 1f - progress()
}

/**
 * Song changes, wherever lyrics show (the lyrics screen, picture-in-picture, the wallpaper, the
 * overlay and the always-on display; Settings → Scroll & Sync → Song changes). When the song
 * changes while its lyrics show, they stay as the outgoing lyrics: held still after a skip,
 * carrying on in time when the song ran into the next. They give way once the new song's lyrics
 * are ready (or it turns out to have none: [LyricsState.Idle] or an error), or with [hold],
 * after a run-in, once the new song reaches its first line; then the two cross over
 * [CROSSFADE_MS]. Off ([enabled] false), nothing is kept.
 */
@Composable
internal fun rememberSongTransition(
    lyrics: LyricsState,
    translation: TranslationResult?,
    songKey: String?,
    currentTimeMs: () -> Long,
    enabled: Boolean,
    hold: Boolean,
): SongTransition {
    var outgoing by remember { mutableStateOf<OutgoingLyrics?>(null) }
    val fade = remember { Animatable(1f) }
    // The lyrics on screen, their translation and the song they're for, and how far into them it last was.
    val last = remember { arrayOfNulls<Any>(3) }
    val lastTime = remember { longArrayOf(0L) }
    val latestLyrics by rememberUpdatedState(lyrics)
    val latestTime by rememberUpdatedState(currentTimeMs)
    val latestTranslation by rememberUpdatedState(translation)

    LaunchedEffect(lyrics, songKey) {
        val shownLyrics = last[0] as? LyricsState.Ready
        val shownTranslation = last[1] as? TranslationResult
        val shownKey = last[2] as? String
        if (lyrics is LyricsState.Ready) {
            last[0] = lyrics
            last[1] = latestTranslation
            last[2] = songKey
        }
        if (!enabled) {
            outgoing = null
            return@LaunchedEffect
        }
        if (shownLyrics != null && songKey != null && shownKey != null && songKey != shownKey && outgoing == null) {
            val at = lastTime[0]
            val lastLineEnd = shownLyrics.lines.maxOfOrNull { it.endMs } ?: 0L
            // At or past its last line, it ran into the next song; otherwise it was skipped.
            val ranOn = at >= lastLineEnd - RAN_ON_WITHIN_MS
            fade.snapTo(0f)
            outgoing = OutgoingLyrics(shownLyrics, shownTranslation, at, SystemClock.uptimeMillis(), runs = ranOn)
        }
    }
    // The translation as it is now, for the lyrics on screen.
    LaunchedEffect(translation) { if (last[0] != null && latestLyrics is LyricsState.Ready) last[1] = translation }
    // How far into the lyrics on screen it is, a few times a second, for when the song changes.
    LaunchedEffect(lyrics) {
        if (lyrics !is LyricsState.Ready) return@LaunchedEffect
        lastTime[0] = 0L
        while (true) {
            val now = latestTime()
            // A drop to the very start is the next song's clock, not a seek back in this one.
            if (!(now < 3_000L && lastTime[0] - now > 3_000L)) lastTime[0] = now
            delay(250L)
        }
    }
    LaunchedEffect(outgoing) {
        val out = outgoing ?: return@LaunchedEffect
        val since = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - since < MAX_HOLD_MS) {
            val now = latestLyrics
            if (now is LyricsState.Error || now == LyricsState.Idle) break
            if (now is LyricsState.Ready) {
                if (!(hold && out.runs)) break
                val firstLine = now.lines.firstOrNull { !it.untimed }?.startMs ?: 0L
                if (latestTime() >= firstLine - FIRST_LINE_LEAD_MS) break
            }
            delay(100L)
        }
        fade.animateTo(1f, tween(CROSSFADE_MS, easing = EASE_OUT))
        outgoing = null
    }
    return SongTransition(outgoing) { fade.value }
}

/** Within this of its last line's end, a song that changes ran into the next one. */
private const val RAN_ON_WITHIN_MS = 4_000L
/** The new lyrics start coming in this long before their first line. */
private const val FIRST_LINE_LEAD_MS = 600L
/** The last song's lyrics never stay longer than this, however long the new song's intro. */
private const val MAX_HOLD_MS = 45_000L
private const val CROSSFADE_MS = 650
private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
