package com.tx24.spicyplayer.overlay

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.playback.LiveLyrics
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.wallpaper.LyricsFontSetup
import com.tx24.spicyplayer.wallpaper.WallpaperLyrics

/**
 * The overlay's inside: the lyrics, word by word, drawn by the lyrics screen's own renderer with
 * its style, font, effects, romanization and translation, smaller to fit, on an optional dark
 * rounded backdrop. Before lyrics arrive it shows the song's name.
 */
@Composable
internal fun OverlayContent(live: LiveLyrics, prefs: SharedPreferences, visible: () -> Boolean) {
    var settingsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settingsVersion++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val settings = remember(settingsVersion) { AppSettings(prefs) }
    val size = remember(settingsVersion) { OverlaySize.from(prefs) }
    val backdrop = remember(settingsVersion) { prefs.getBoolean(PREF_BACKDROP, true) }
    LyricsFontSetup(settings)

    val shape = RoundedCornerShape(BACKDROP_CORNER_DP.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .then(if (backdrop) Modifier.background(Color.Black.copy(alpha = BACKDROP_ALPHA), shape) else Modifier),
    ) {
        val track = live.track
        val lyrics = live.lyrics
        if (track != null && lyrics != null && lyrics.lines.isNotEmpty()) {
            WallpaperLyrics(
                lyrics = lyrics,
                translation = live.translation,
                settings = settings,
                positionMs = live::positionMs,
                isPlaying = live.playing,
                visible = visible,
                durationMs = track.durationMs,
                modifier = Modifier.fillMaxSize(),
                focusAnchorFraction = FOCUS_FRACTION,
                fontSizeScale = settings.lyricsScale(false) * size.fontScale,
            )
        } else if (track != null) {
            Text(
                text = "${track.title} — ${track.artist}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            )
        }
    }
}

internal const val PREF_BACKDROP = "overlayBackdrop"
private const val BACKDROP_CORNER_DP = 24
private const val BACKDROP_ALPHA = 0.45f
/** The line being sung sits a little above the middle, so the next line shows under it. */
private const val FOCUS_FRACTION = 0.38f
