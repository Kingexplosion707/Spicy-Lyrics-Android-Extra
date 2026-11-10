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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.lyricsTint
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
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (com.tx24.spicyplayer.wallpaper.changesLook(key)) settingsVersion++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val settings = remember(settingsVersion) { AppSettings(prefs) }
    val size = remember(settingsVersion) { OverlaySize.from(prefs) }
    val backdrop = remember(settingsVersion) { prefs.getBoolean(PREF_BACKDROP, true) }
    val backdropAlpha = remember(settingsVersion) { prefs.getInt(PREF_BACKDROP_OPACITY, DEFAULT_BACKDROP_OPACITY).coerceIn(0, 100) / 100f }
    val lyricsAlpha = remember(settingsVersion) { prefs.getInt(PREF_LYRICS_OPACITY, 100).coerceIn(10, 100) / 100f }
    val style = remember(settingsVersion) { OverlayStyle.from(prefs) }
    val glow = remember(settingsVersion) { prefs.getBoolean(PREF_GLOW, true) }
    val glowStrength = remember(settingsVersion) { prefs.getInt(PREF_GLOW_STRENGTH, 100).coerceIn(50, 300) / 100f }
    val brightness = remember(settingsVersion) { prefs.getInt(PREF_BRIGHTNESS, 100).coerceIn(100, 200) / 100f }
    val colorChoice = remember(settingsVersion) { OverlayColor.from(prefs) }
    val cover = live.track?.cover
    val customColor = settings.customLyricsColor
    val color = remember(colorChoice, cover, customColor) { colorChoice.resolve(cover, com.tx24.spicyplayer.ui.settings.parseHexColor(customColor)) }
    val ink = remember(colorChoice, color) { com.tx24.spicyplayer.lyrics.LyricsInk(color, colorChoice.argbEnd?.let { Color(it) } ?: color) }
    LyricsFontSetup(settings)
    if (style == OverlayStyle.StatusBar) {
        StatusBarLyrics(live, visible, Modifier.fillMaxSize().lyricsTint(Color.White, brightness).graphicsLayer { alpha = lyricsAlpha }, color, glow, glowStrength)
        return
    }

    val shape = RoundedCornerShape(BACKDROP_CORNER_DP.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .then(if (backdrop && backdropAlpha > 0f) Modifier.background(Color.Black.copy(alpha = backdropAlpha), shape) else Modifier),
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
                modifier = Modifier.fillMaxSize().lyricsTint(ink, brightness).graphicsLayer { alpha = lyricsAlpha },
                focusAnchorFraction = FOCUS_FRACTION,
                fontSizeScale = settings.lyricsScale(false) * size.fontScale,
                glow = glow,
                glowStrength = glowStrength,
                // Smooth enough floating over another app, at half a 120 Hz screen's work.
                maxFrameRate = OVERLAY_MAX_FPS,
            )
        } else if (track != null) {
            Text(
                text = "${track.title} — ${track.artist}",
                color = color.copy(alpha = (0.85f * brightness).coerceAtMost(1f) * lyricsAlpha),
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

internal const val PREF_GLOW = "overlayGlow"
private const val OVERLAY_MAX_FPS = 60f
internal const val PREF_GLOW_STRENGTH = "overlayGlowStrength"
internal const val PREF_BRIGHTNESS = "overlayBrightness"
internal const val PREF_BACKDROP = "overlayBackdrop"
internal const val PREF_BACKDROP_OPACITY = "overlayBackdropOpacity"
internal const val PREF_LYRICS_OPACITY = "overlayLyricsOpacity"
internal const val DEFAULT_BACKDROP_OPACITY = 45
private const val BACKDROP_CORNER_DP = 24
/** The line being sung sits a little above the middle, so the next line shows under it. */
private const val FOCUS_FRACTION = 0.38f
