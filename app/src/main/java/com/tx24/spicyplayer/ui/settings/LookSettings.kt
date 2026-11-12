package com.tx24.spicyplayer.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.lyrics.LyricsExport
import android.content.Intent
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.lyrics.SongLinks
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.overlay.OverlayColor
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.background.BackgroundPhoto
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A look: several lyric settings set together, as a place to start from. */
internal enum class LyricsLook(val label: String) {
    Choose("Choose…"),
    Spicy("Spicy Lyrics"),
    AppleMusic("Apple Music"),
    Karaoke("Karaoke"),
    Soft("Soft"),
    Neon("Neon"),
    Quiet("Quiet");

    fun apply(settings: AppSettings) {
        if (this == Choose) return
        // Every look starts from the same plain page.
        settings.lyricsStyle = if (this == AppleMusic) LyricsStyle.AppleMusic else LyricsStyle.Spicy
        settings.simpleLyricsMode = this == Quiet
        settings.minimalLyricsMode = false
        settings.lyricsCentered = this == Karaoke
        settings.lyricsWeight = when (this) {
            Karaoke -> LyricsWeight.ExtraBold
            Soft -> LyricsWeight.Medium
            Quiet -> LyricsWeight.SemiBold
            else -> LyricsWeight.Bold
        }
        settings.lineSpacing = when (this) {
            Karaoke -> 130
            Soft -> 120
            else -> 100
        }
        settings.otherLinesBrightness = when (this) {
            Karaoke -> 70
            Neon -> 60
            Quiet -> 80
            else -> 100
        }
        settings.glow = this != Quiet
        settings.glowStrength = when (this) {
            Karaoke -> 200
            Neon -> 300
            Soft -> 75
            else -> 100
        }
        settings.lyricsColor = when (this) {
            Neon -> OverlayColor.Candy
            Soft -> OverlayColor.SongFade
            else -> OverlayColor.White
        }
    }
}

/** Lyrics: a look to start from, above the settings it sets. */
@Composable
internal fun LyricsLookRow(settings: AppSettings) {
    SettingRow(
        label = "Look",
        description = "Sets the style, weight, spacing, alignment, glow and colour together. Change any of them afterwards.",
        icon = Icons.Rounded.AutoAwesome,
    ) {
        SpicySelect(
            value = LyricsLook.Choose.name,
            options = LyricsLook.entries.map { it.name },
            labels = LyricsLook.entries.map { it.label },
            onChange = { LyricsLook.valueOf(it).apply(settings) },
        )
    }
}

/** Lyrics → Text: how heavy the lines are, how far apart, and where they sit. */
@Composable
internal fun LyricsTypeRows(settings: AppSettings) {
    SettingRow(
        label = "Lyrics weight",
        description = "How heavy the lines are drawn. A font without the weight draws its nearest.",
        icon = Icons.Rounded.FormatSize,
    ) {
        SpicySelect(
            value = settings.lyricsWeight.name,
            options = LyricsWeight.entries.map { it.name },
            labels = LyricsWeight.entries.map { it.label },
            onChange = { settings.lyricsWeight = LyricsWeight.valueOf(it) },
        )
    }
    SettingRow(
        label = "Line spacing",
        description = "The gap between one line and the next.",
        icon = Icons.Rounded.Height,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = settings.lineSpacing,
            range = 50..250,
            step = 10,
            onValueChange = { settings.lineSpacing = it },
            default = 100,
            unit = "%",
        )
    }
    ToggleRow(
        label = "Centre the lyrics",
        checked = settings.lyricsCentered,
        onCheckedChange = { settings.lyricsCentered = it },
        description = "Lines sit in the middle instead of at the edge. A duet keeps its two sides.",
        icon = Icons.Rounded.VerticalAlignCenter,
    )
}

/** Lyrics → Effects: the custom colour's picker, and how bright the other lines are. */
@Composable
internal fun LyricsInkRows(settings: AppSettings) {
    if (settings.lyricsColor == OverlayColor.Custom) {
        SettingRow(
            label = "Custom lyrics colour",
            description = "Pick one, or type a hex colour like #FFD166.",
            icon = Icons.Rounded.Palette,
            stacked = true,
        ) {
            CustomColorPicker(settings.customLyricsColor, LyricColorPresets) { settings.customLyricsColor = it }
        }
    }
    SettingRow(
        label = "Other lines",
        description = "How bright the lines not being sung are.",
        icon = Icons.Rounded.Visibility,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = settings.otherLinesBrightness,
            range = 40..190,
            step = 10,
            onValueChange = { settings.otherLinesBrightness = it },
            default = 100,
            unit = "%",
        )
    }
}

private val LyricColorPresets = listOf("#FFFFFF", "#FFD166", "#FF8FAB", "#9BF6FF", "#CAFFBF", "#BDB2FF", "#FFADAD")

/** Lyrics → Fonts: the gallery's fonts, each downloaded when first picked. */
@Composable
internal fun FontGalleryContent(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    // Rises with each download, so the rows look at the files again.
    var downloads by remember { mutableIntStateOf(0) }
    SettingRow(
        label = "Font gallery",
        description = "Free fonts from Google Fonts, downloaded from its GitHub repository when you pick one.",
        icon = Icons.Rounded.FontDownload,
    ) {
        if (settings.lyricsFont == LyricsFont.Gallery) SpicyButton("Use the default", onClick = { settings.lyricsFont = LyricsFont.Default })
    }
    for (entry in FontGallery.entries) {
        val downloaded = remember(entry.id, downloads) { FontGallery.isDownloaded(context, entry.id) }
        val inUse = settings.lyricsFont == LyricsFont.Gallery && settings.galleryFont == entry.id
        SettingRow(
            label = entry.name,
            description = when {
                failed == entry.id -> "Couldn't download it. Check the connection and try again."
                inUse -> "${entry.kind}. In use."
                downloaded -> "${entry.kind}. Downloaded."
                else -> "${entry.kind}."
            },
        ) {
            SpicyButton(
                when {
                    busy == entry.id -> "Downloading…"
                    inUse -> "In use"
                    downloaded -> "Use"
                    else -> "Download"
                },
                enabled = busy == null && !inUse,
                onClick = {
                    busy = entry.id
                    failed = null
                    scope.launch {
                        val ok = FontGallery.download(context, entry)
                        busy = null
                        if (ok) {
                            downloads++
                            settings.galleryFont = entry.id
                            settings.lyricsFont = LyricsFont.Gallery
                        } else failed = entry.id
                    }
                },
            )
        }
    }
}

/** Background: the Your Photo picture's picker, and the dim over any background. */
@Composable
internal fun BackgroundPhotoRow(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { BackgroundPhoto.save(context, uri) }
            failed = saved == null
            if (saved != null) settings.backgroundPhoto = saved
        }
    }
    val picked = settings.backgroundPhoto.isNotBlank()
    SettingRow(
        label = "Your photo",
        description = when {
            failed -> "That file isn't a picture Android can read."
            picked -> "Shown behind the lyrics, softened by Background Blur."
            else -> "Pick a picture to show behind the lyrics."
        },
        icon = Icons.Rounded.Panorama,
    ) {
        SpicyButton(if (picked) "Change" else "Choose", onClick = { picker.launch("image/*") })
    }
}

@Composable
internal fun BackgroundDimRow(settings: AppSettings) {
    SettingRow(
        label = "Background dim",
        description = "Darken the background so the lyrics stand out.",
        icon = Icons.Rounded.BrightnessLow,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = settings.backgroundDim,
            range = 0..80,
            step = 5,
            onValueChange = { settings.backgroundDim = it },
            default = 0,
            unit = "%",
        )
    }
}

/** This song: the lyrics as text on the clipboard, or as a file. */
@Composable
internal fun LyricsExportRows(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    val ready = state.lyrics as? LyricsState.Ready ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val text = LyricsExport.lrc(ready.lines, ready.lyricsType, state.title, state.artist, state.album, state.durationMs)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
                }.getOrDefault(false)
            }
            viewModel.showMessage(if (written) "Lyrics saved." else "Couldn't save the lyrics.")
        }
    }
    SettingRow(
        label = "Copy lyrics",
        description = "The whole song's lyrics as plain text.",
        icon = Icons.Rounded.Notes,
    ) {
        SpicyButton("Copy", onClick = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("Lyrics", LyricsExport.plainText(ready.lines)))
            viewModel.showMessage("Lyrics copied.")
        })
    }
    SettingRow(
        label = "Save lyrics as a file",
        description = "An .lrc file with the timing, word by word where the lyrics have it.",
        icon = Icons.Rounded.Save,
    ) {
        SpicyButton("Save", onClick = { save.launch(LyricsExport.fileName(state.title, state.artist)) })
    }
}

/** This song: sharing it, and looking it up elsewhere ([SongLinks]). */
@Composable
internal fun SongLinkRows(state: PlayerUiState) {
    if (state.sourcePackage == null || state.title.isBlank()) return
    val context = LocalContext.current
    val trackId = state.manualSpotifyId ?: state.detectedSpotifyId
    SettingRow(
        label = "Share song",
        description = if (SongLinks.spotify(trackId) != null) "Its name and its Spotify link." else "Its name and artist.",
        icon = Icons.Rounded.IosShare,
    ) {
        SpicyButton("Share", onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, SongLinks.shareText(state.title, state.artist, trackId))
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
    }
    SettingRow(
        label = "Look it up",
        description = "Search for the song on Genius or YouTube.",
        icon = Icons.Rounded.TravelExplore,
        stacked = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyButton("Genius", external = true, onClick = { openUrl(context, SongLinks.genius(state.title, state.artist)) })
            SpicyButton("YouTube", external = true, onClick = { openUrl(context, SongLinks.youtube(state.title, state.artist)) })
        }
    }
}

/** Controls: holding a line to copy it, and the volume in quick settings. */
@Composable
internal fun LyricsGestureRows(settings: AppSettings) {
    ToggleRow(
        label = "Hold a line to copy",
        checked = settings.holdToCopy,
        onCheckedChange = { settings.holdToCopy = it },
        description = "Holding a line of the lyrics copies it.",
        icon = Icons.Rounded.Notes,
    )
    ToggleRow(
        label = "Volume in quick settings",
        checked = settings.quickVolume,
        onCheckedChange = { settings.quickVolume = it },
        description = "A slider for the media volume in the quick settings pop-up.",
        icon = Icons.Rounded.Tune,
    )
}

/** Quick settings: the phone's media volume. */
@Composable
internal fun QuickVolumeRow() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) } ?: return
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    SettingRow(
        label = "Volume",
        description = "The phone's media volume.",
        icon = Icons.Rounded.Tune,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = volume,
            range = 0..max,
            step = 1,
            onValueChange = {
                volume = it
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
            },
            default = volume,
            valueText = { "${it * 100 / max}%" },
        )
    }
}

/** Live wallpaper: its cover, lyric size and dim. */
@Composable
internal fun WallpaperLookRows(settings: AppSettings) {
    SettingRow(
        label = "Cover on the wallpaper",
        description = "How big the album cover is, or none at all.",
        icon = Icons.Rounded.Style,
    ) {
        SpicySelect(
            value = settings.wallpaperCover.name,
            options = WallpaperCover.entries.map { it.name },
            labels = WallpaperCover.entries.map { it.label },
            onChange = { settings.wallpaperCover = WallpaperCover.valueOf(it) },
        )
    }
    SettingRow(
        label = "Wallpaper lyrics size",
        description = "Against the lyrics screen's size.",
        icon = Icons.Rounded.FormatSize,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = settings.wallpaperLyricsSize,
            range = 60..160,
            step = 5,
            onValueChange = { settings.wallpaperLyricsSize = it },
            default = 100,
            unit = "%",
        )
    }
    SettingRow(
        label = "Wallpaper dim",
        description = "How dark the veil over the picture is. Lower shows more of the video.",
        icon = Icons.Rounded.BrightnessLow,
        stacked = true,
    ) {
        SpicyBipolarSlider(
            value = settings.wallpaperDim,
            range = 0..200,
            step = 10,
            onValueChange = { settings.wallpaperDim = it },
            default = 100,
            unit = "%",
        )
    }
}
