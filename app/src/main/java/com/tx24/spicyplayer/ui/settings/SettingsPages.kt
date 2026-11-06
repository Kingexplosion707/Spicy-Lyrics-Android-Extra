package com.tx24.spicyplayer.ui.settings

import android.os.Build
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import com.tx24.spicyplayer.ui.components.drawBrandRamp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import com.tx24.spicyplayer.ui.components.focusRing
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.zIndex
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.LocalSettingsQuery
import androidx.compose.runtime.saveable.rememberSaveable
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import java.util.Collections
import kotlin.math.abs
import com.tx24.spicyplayer.ui.background.LEGACY_BLUR_DP
import com.tx24.spicyplayer.ui.components.ChromeTheme
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.automirrored.rounded.CallMerge

import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Sync
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import com.tx24.spicyplayer.backup.SettingsBackup
import com.tx24.spicyplayer.backup.SettingsBackupStore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import com.tx24.spicyplayer.ui.nowplaying.HeaderSize
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import com.tx24.spicyplayer.ui.nowplaying.LoopKind
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MotionPhotosPaused
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Lyrics
import java.util.Locale
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.PictureInPicture
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import com.tx24.spicyplayer.network.data.LyricsBlends
import com.tx24.spicyplayer.network.data.LyricsOrigin
import com.tx24.spicyplayer.network.data.LyricsOrigins
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.SourceRanking
import com.tx24.spicyplayer.ui.components.SpicyMultiSelect
import com.tx24.spicyplayer.network.data.ProviderAttempt
import com.tx24.spicyplayer.network.data.ProviderAttemptOutcome
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.LOCAL_SOURCE
import com.tx24.spicyplayer.playback.UPLOADED_SOURCE
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.DISABLED_ALPHA
import com.tx24.spicyplayer.ui.components.DescriptionStyle
import com.tx24.spicyplayer.ui.components.RowLabel
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.Searchable
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyResettableValue
import com.tx24.spicyplayer.ui.components.SpicyIconButton
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.components.SpicyToggle
import com.tx24.spicyplayer.ui.components.ToggleRow
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.UpdateViewModel
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.network.data.SpicyLyricsKey
import com.tx24.spicyplayer.analytics.UsageStats

/*
 * What each group's page holds. Every page also shows up, row by row, in the search results, so a
 * page's first rows go without a section title (the page header says it) and later groups get one.
 */

@Composable
internal fun ThisSongContent(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    onOpenLyricsManager: () -> Unit,
    onOpenSpotifySearch: () -> Unit,
) {
    val context = LocalContext.current
    var spotifyInput by remember { mutableStateOf("") }
    LaunchedEffect(state.title, state.artist) { spotifyInput = "" }

    Searchable("This song", "Now playing", state.title, state.artist) {
        Column(
            Modifier.fillMaxWidth().outlinedCard(tinted = true).padding(SpicySpacing.S4),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(state.title, style = SpicyType.Headline)
            Text(state.artist, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary))
            Spacer(Modifier.height(SpicySpacing.S2))
            Text(lyricsSummary(state.lyrics), style = SpicyType.Caption)
            state.lookupStatus?.let { Text(it, style = DescriptionStyle) }
        }
        Spacer(Modifier.height(SpicySpacing.S2))
    }
    SettingRow(
        label = "Wrong lyrics?",
        description = "Paste the song's Spotify link and the lyrics will come from that recording.",
        icon = Icons.Rounded.Link,
        stacked = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyTextField(spotifyInput, { spotifyInput = it }, placeholder = "Spotify link or track ID", modifier = Modifier.weight(1f))
            if (spotifyInput.isBlank()) {
                SpicyButton("Paste", onClick = {
                    val pasted = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    spotifyInput = pasted
                    viewModel.overrideSpotifyId(pasted)
                })
            } else {
                SpicyButton("Use", onClick = { viewModel.overrideSpotifyId(spotifyInput) })
            }
        }
    }
    SettingRow(label = "Find on Spotify", description = "Pick the right recording from Spotify's results for this song.", icon = Icons.Rounded.Search) {
        SpicyButton("Search", onClick = onOpenSpotifySearch)
    }
    state.manualSpotifyId?.let { id ->
        SettingRow(label = "Using your Spotify link", description = id, icon = Icons.Rounded.Link) {
            SpicyButton("Auto-match", onClick = viewModel::clearSpotifyIdOverride)
        }
    }
    SettingRow(label = "Look again", description = "Ask the sources again instead of using the saved lyrics.", icon = Icons.Rounded.Refresh) {
        SpicyButton("Retry", onClick = { viewModel.loadLyrics(force = true) })
    }
    SongDelayRow(state, viewModel)
    SettingRow(
        label = "Lyrics Manager",
        description = "Use your own TTML for this song, once or saved, and manage the songs you've saved.",
        icon = Icons.Rounded.LibraryMusic,
    ) {
        SpicyButton("Open", onClick = onOpenLyricsManager)
    }
    state.status?.let { status ->
        Searchable("Status", status) {
            Text(status, style = DescriptionStyle, modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S2))
        }
    }
}

/**
 * A page's less-used rows, folded behind one row that names them until it's opened, so a page
 * shows first what people actually change. Search shows them unfolded (each row still matches
 * on its own), so a folded row is never harder to find.
 */
@Composable
internal fun MoreOptions(summary: String, content: @Composable () -> Unit) {
    if (LocalSettingsQuery.current.isNotBlank()) return content()
    var open by rememberSaveable { mutableStateOf(false) }
    val animate = LocalUiAnimations.current
    Column(Modifier.fillMaxWidth().padding(vertical = SpicySpacing.S2).outlinedCard()) {
        GroupRow(
            Icons.Rounded.Tune,
            if (open) "Fewer options" else "More options",
            summary,
            trailing = if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        ) { open = !open }
    }
    AnimatedVisibility(
        visible = open,
        enter = if (animate) expandVertically(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) + fadeIn(tween(SpicyMotion.FAST_MS)) else EnterTransition.None,
        exit = if (animate) shrinkVertically(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard)) + fadeOut(tween(SpicyMotion.FAST_MS)) else ExitTransition.None,
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
internal fun LyricsContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, settings: AppSettings) {
    SettingRow(
        label = "Lyrics Style",
        description = "How the lyrics look and move. Apple Music lifts words as they're sung, with a soft wipe, and has lines follow one after another.",
        icon = Icons.Rounded.Style,
    ) {
        SpicySelect(
            value = settings.lyricsStyle.name,
            options = LyricsStyle.entries.map { it.name },
            labels = LyricsStyle.entries.map { it.label },
            onChange = { settings.lyricsStyle = LyricsStyle.valueOf(it) },
        )
    }
    // Simple, Minimal and the word motion are the Spicy Lyrics style's own.
    val spicyStyle = settings.lyricsStyle == LyricsStyle.Spicy
    ToggleRow(
        label = "Simple Lyrics Mode",
        checked = settings.simpleLyricsMode,
        onCheckedChange = { settings.simpleLyricsMode = it },
        description = "Remove extra visual effects from lyrics.",
        icon = Icons.Rounded.AutoFixOff,
        enabled = spicyStyle,
    )
    ToggleRow(
        label = "Refreshed Simple Lyrics",
        checked = settings.refreshedSimpleLyrics,
        onCheckedChange = { settings.refreshedSimpleLyrics = it },
        description = "Softer fill, flowing letters and rippling scroll.",
        icon = Icons.Rounded.AutoFixOff,
        enabled = spicyStyle && settings.simpleLyricsMode,
    )
    ToggleRow(
        label = "Minimal Lyrics Mode",
        checked = settings.minimalLyricsMode,
        onCheckedChange = { settings.minimalLyricsMode = it },
        description = "Hides sung lyrics lines.",
        icon = Icons.Rounded.VisibilityOff,
        enabled = spicyStyle,
    )
    ToggleRow(
        label = "Original word motion",
        checked = settings.originalWordMotion,
        onCheckedChange = { settings.originalWordMotion = it },
        description = "The desktop amount of grow and lift on sung words. Off: ${AppSettings.WORD_MOTION_BOOST}×, which reads better on a phone.",
        icon = Icons.Rounded.Height,
        enabled = spicyStyle,
    )
    SettingsSection("Text") {
        SettingRow(
            label = "Lyrics size",
            description = "With the phone upright, against the screen's default.",
            icon = Icons.Rounded.FormatSize,
            stacked = true,
            searchDescription = "Make the lyrics smaller or bigger than the screen's default. Portrait.",
        ) {
            SpicyBipolarSlider(
                value = settings.lyricsSizePortrait,
                range = LYRICS_SIZE_RANGE,
                step = 5,
                onValueChange = { settings.lyricsSizePortrait = it },
                default = 100,
                unit = "%",
            )
        }
        SettingRow(
            label = "Lyrics size in landscape",
            description = "With the phone on its side, against the screen's default.",
            icon = Icons.Rounded.ScreenRotation,
            stacked = true,
            searchDescription = "Make the lyrics smaller or bigger in landscape.",
        ) {
            SpicyBipolarSlider(
                value = settings.lyricsSizeLandscape,
                range = LYRICS_SIZE_RANGE,
                step = 5,
                onValueChange = { settings.lyricsSizeLandscape = it },
                default = 100,
                unit = "%",
            )
        }
        LyricsFontRows(settings)
    }
    MoreOptions("Merge syllables, duet padding, credits, blur and glow") {
        SettingRow(
            label = "Merge syllables",
            description = "Draw a word split into syllables as one word. Steadily sung words only merges the words whose parts have no gap and keep an even pace. Held words only merges the words that are then long enough for the held-word letter effect.",
            icon = Icons.Rounded.CallMerge,
        ) {
            SpicySelect(
                value = settings.syllableMerge.name,
                options = SyllableMerge.entries.map { it.name },
                labels = SyllableMerge.entries.map { it.label },
                onChange = { settings.syllableMerge = SyllableMerge.valueOf(it) },
            )
        }
        ToggleRow(
            label = "Keep Syllable Pace",
            checked = settings.syllablePace,
            onCheckedChange = { settings.syllablePace = it },
            description = "A merged held word's letters follow each syllable's timing, slow on a long syllable and quick on a short one.",
            icon = Icons.Rounded.Speed,
            enabled = settings.syllableMerge != SyllableMerge.Off,
        )
        ToggleRow(
            label = "Duet Line Padding",
            checked = settings.duetLinePadding,
            onCheckedChange = { settings.duetLinePadding = it },
            description = "Indents lyrics lines on the side they lean away from when a song has duet lines, so the two voices read as separate columns. Disable to give every line the same slight padding.",
            icon = Icons.Rounded.FormatIndentIncrease,
        )
        ToggleRow(
            label = "Credits Follow the Last Singer",
            checked = settings.creditsFollowLastLine,
            onCheckedChange = { settings.creditsFollowLastLine = it },
            description = "When a song ends on a duet line sung from the right, move the credits under it to the right too.",
            icon = Icons.Rounded.FormatAlignRight,
        )
        ToggleRow(
            label = "Keep Overlapping Lines",
            checked = settings.keepOverlappingLines,
            onCheckedChange = { settings.keepOverlappingLines = it },
            description = "In Minimal Lyrics Mode, keep a line that ends while one above it is still sung on screen, dimmed, instead of fading it out early.",
            icon = Icons.Rounded.Layers,
        )
        ToggleRow(
            label = "Background Vocal Pop-Out",
            checked = settings.backgroundPop,
            onCheckedChange = { settings.backgroundPop = it },
            description = "Keep background vocal lines tucked under their line until it is sung, then slide them out.",
            icon = Icons.Rounded.UnfoldMore,
        )
        ToggleRow(
            label = "Refined Credits",
            checked = settings.refinedCredits,
            onCheckedChange = { settings.refinedCredits = it },
            description = "Show the credits as quiet type close under the last line, dim until the song has been sung.",
            icon = Icons.Rounded.Notes,
        )
        SettingsSection("Effects") {
            ToggleRow(
                label = "Blur distant lines",
                checked = settings.distanceBlur && !settings.lowPerformance,
                onCheckedChange = { settings.distanceBlur = it },
                description = "Soften the lines further from the one being sung.",
                icon = Icons.Rounded.BlurOn,
                enabled = !settings.lowPerformance,
            )
            ToggleRow(
                label = "Glow",
                checked = settings.glow && !settings.lowPerformance,
                onCheckedChange = { settings.glow = it },
                description = "Let sung words glow.",
                icon = Icons.Rounded.Flare,
                enabled = !settings.lowPerformance,
            )
        }
    }
}

/**
 * The lyrics font: ours, the phone's, or a font file picked from the phone. Choosing Custom with
 * nothing picked yet opens the picker.
 */
@Composable
private fun LyricsFontRows(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = LyricsFontFile.import(context, uri)
            failed = imported == null
            if (imported != null) {
                settings.customFontFile = imported.fileName
                settings.customFontName = imported.displayName
                settings.lyricsFont = LyricsFont.Custom
            }
        }
    }
    val pick = { picker.launch(FONT_MIME_TYPES) }
    val hasCustom = settings.customFontFile.isNotBlank()
    SettingRow(label = "Lyrics Font", description = "Draw the lyrics in their own font, your phone's, or any font file on your phone.", icon = Icons.Rounded.FontDownload) {
        SpicySelect(
            value = settings.lyricsFont.name,
            options = LyricsFont.entries.map { it.name },
            labels = LyricsFont.entries.map { if (it == LyricsFont.Custom && hasCustom) settings.customFontName else it.label },
            onChange = { choice ->
                val font = LyricsFont.valueOf(choice)
                if (font == LyricsFont.Custom && !hasCustom) pick() else settings.lyricsFont = font
            },
        )
    }
    SettingRow(
        label = "Font File",
        description = when {
            failed -> "That file isn't a font Android can read. Pick a .ttf or .otf file."
            hasCustom -> "Using ${settings.customFontName}."
            else -> "Pick a .ttf or .otf font file from your phone."
        },
        icon = Icons.Rounded.FolderOpen,
    ) {
        SpicyButton(if (hasCustom) "Change" else "Choose", onClick = pick)
    }
}

/** Font files come labelled all sorts of ways, so the picker offers these (and checks the file). */
private val FONT_MIME_TYPES = arrayOf(
    "font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf",
    "application/vnd.ms-opentype", "application/octet-stream",
)

@Composable
internal fun ScrollSyncContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, settings: AppSettings) {
    SettingRow(
        label = "Lyric delay",
        description = "Saved for ${state.outputLabel}. Move it right if the lyrics run ahead of the song.",
        icon = Icons.Rounded.Timer,
        stacked = true,
    ) {
        DelayControl(state.lyricDelayMs, viewModel::setLyricDelay)
    }
    PlayerDelayRow(state, viewModel)
    SongDelayRow(state, viewModel)
    DelayCalibrationRow(state, viewModel)
    SettingRow(
        label = "Delays in Quick Settings",
        description = "Which delays the quick settings pop-up shows.",
        icon = Icons.Rounded.Tune,
    ) {
        SpicyMultiSelect(
            selected = settings.quickDelays.mapTo(mutableSetOf()) { it.name },
            options = DelayKind.entries.map { it.name },
            labels = DelayKind.entries.map { it.label },
            none = "None",
            onChange = { picked -> settings.quickDelays = DelayKind.entries.filter { it.name in picked }.toSet() },
        )
    }
    SettingsSection("Scrolling") {
        ToggleRow(
            label = "Early Scroll",
            checked = settings.scrollLeadEnabled,
            onCheckedChange = { settings.scrollLeadEnabled = it },
            description = "Start scrolling to the next line slightly before it becomes active, so the move feels less abrupt.",
            icon = Icons.Rounded.KeyboardDoubleArrowUp,
        )
        SettingRow(
            label = "Early Scroll Time",
            description = "How early the next line is scrolled to, before it becomes active.",
            icon = Icons.Rounded.Timer,
            enabled = settings.scrollLeadEnabled,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.scrollLeadMs,
                range = 0..800,
                step = 10,
                onValueChange = { settings.scrollLeadMs = it },
                default = 250,
                unit = "ms",
                enabled = settings.scrollLeadEnabled,
            )
        }
        ToggleRow(
            label = "Smooth Scrolling",
            checked = settings.smoothScrolling,
            onCheckedChange = { settings.smoothScrolling = it },
            description = "Makes the lyrics scroll smoothly.",
            icon = Icons.Rounded.Waves,
        )
    }
    MoreOptions("Seek fade-in compensation, scroll-to-active button, auto-scroll") {
        ToggleRow(
            label = "Seek Fade-in Compensation",
            checked = settings.seekFadeCompensation,
            onCheckedChange = { settings.seekFadeCompensation = it },
            description = "Tapping a line jumps 300ms before it, so the player's fade-in doesn't cut off the start. Best for rap or fast-paced songs.",
            icon = Icons.Rounded.FastRewind,
        )
        ToggleRow(
            label = "Show Scroll to Active Button",
            checked = settings.showScrollToActive,
            onCheckedChange = { settings.showScrollToActive = it },
            description = "Show an arrow when the active lyric is outside the viewport.",
            icon = Icons.Rounded.VerticalAlignCenter,
        )
        ToggleRow(
            label = "Auto-scroll Unsynced Lyrics",
            checked = settings.staticAutoScroll,
            onCheckedChange = { settings.staticAutoScroll = it },
            description = "Lyrics without timing scroll through at the song's pace, from top to bottom.",
            icon = Icons.Rounded.SwapVert,
        )
    }
}

/** Dark presets that keep the white lyrics readable, black first. */
private val PresetBackgroundColors = listOf("#000000", "#121212", "#0B1426", "#1E0E24", "#0D1F17", "#2A0B12", "#2B2118")

/** Swatches for the presets, and a hex field for any other colour; [onPick] gets `#RRGGBB`. */
@Composable
private fun CustomColorPicker(value: String, onPick: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val current = parseHexColor(value)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (preset in PresetBackgroundColors) {
                val argb = parseHexColor(preset)!!
                val selected = argb == current
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else SpicyColors.Hairline, CircleShape)
                        .clickable(role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onPick(preset)
                        },
                )
            }
        }
        // Only a whole colour is kept, so a half-typed one doesn't flash the background.
        SpicyTextField(
            value = value,
            onValueChange = { text -> parseHexColor(text)?.let { onPick("#" + text.trim().removePrefix("#").uppercase()) } },
            placeholder = "#000000",
            leading = { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(current ?: 0xFF000000.toInt())).border(1.dp, SpicyColors.Hairline, CircleShape)) },
        )
    }
}

@Composable
internal fun BackgroundContent(settings: AppSettings, viewModel: ExternalPlaybackViewModel) {
    val type = settings.backgroundType
    SettingRow(
        label = "Background Type",
        description = "Choose the dynamic, legacy, static image, cover color, or a color of your own.",
        searchDescription = "Choose the dynamic, legacy, static image, or color background.",
        icon = Icons.Rounded.Wallpaper,
    ) {
        SpicySelect(
            value = type.name,
            options = BackgroundType.entries.map { it.name },
            labels = BackgroundType.entries.map { it.label },
            onChange = { settings.backgroundType = BackgroundType.valueOf(it) },
        )
    }
    if (type == BackgroundType.Custom) {
        SettingRow(
            label = "Background Color",
            description = "Pick one, or type a hex color like #1A1A2E. Black suits AMOLED screens.",
            icon = Icons.Rounded.Palette,
            stacked = true,
        ) {
            CustomColorPicker(settings.backgroundColor) { settings.backgroundColor = it }
        }
    }
    // Each background keeps its own blur: they're measured differently and start from different looks.
    if (type != BackgroundType.Color && type != BackgroundType.Custom) {
        SettingRow(
            label = "Background Blur",
            description = if (type.image) "Soften the static background image." else "How soft the moving cover is.",
            icon = Icons.Rounded.BlurLinear,
            stacked = true,
        ) {
            when (type) {
                BackgroundType.Default -> SpicyBipolarSlider(
                    value = settings.dynamicBackgroundBlur,
                    range = 0..100,
                    step = 1,
                    onValueChange = { settings.dynamicBackgroundBlur = it },
                    default = DEFAULT_DYNAMIC_BLUR,
                    unit = "%",
                )
                BackgroundType.Legacy -> SpicyBipolarSlider(
                    value = settings.legacyBackgroundBlur,
                    range = 0..MAX_BACKGROUND_BLUR,
                    step = 1,
                    onValueChange = { settings.legacyBackgroundBlur = it },
                    default = LEGACY_BLUR_DP,
                    unit = "dp",
                )
                else -> SpicyBipolarSlider(
                    value = settings.backgroundBlur,
                    range = 0..MAX_BACKGROUND_BLUR,
                    step = 1,
                    onValueChange = { settings.backgroundBlur = it },
                    unit = "dp",
                )
            }
        }
    }
    if (type == BackgroundType.CoverArt) {
        ToggleRow(
            label = "Animated Background",
            checked = settings.animatedBackground && !settings.lowPerformance,
            onCheckedChange = { settings.animatedBackground = it },
            description = "Play the album's animated cover from Apple Music behind the lyrics, where it has one.",
            icon = Icons.Rounded.Animation,
            enabled = !settings.lowPerformance,
        )
    }
    // Next to the animated cover's rows: the other video a background can show.
    ToggleRow(
        label = "Show Canvas Where Available",
        checked = settings.canvasBackground && !settings.lowPerformance,
        onCheckedChange = { on ->
            // Canvas asks a third-party service, so it says what it sends first.
            if (on) viewModel.chooseCanvas { settings.canvasBackground = true } else settings.canvasBackground = false
        },
        description = "Where the song has a Spotify Canvas, the background is that Canvas, blurred. Other songs keep your background.",
        icon = Icons.Rounded.VideoLibrary,
        enabled = !settings.lowPerformance,
    )
    if (settings.canvasBackground && !settings.lowPerformance) {
        SettingRow(
            label = "Canvas Blur",
            description = "How soft the Canvas is. Below 100% the video itself plays behind the lyrics.",
            icon = Icons.Rounded.BlurOn,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.canvasBlur,
                range = 0..100,
                step = 1,
                onValueChange = { settings.canvasBlur = it },
                default = 100,
                unit = "%",
            )
        }
    }
    MoreOptions("Refined background, still background, moving with the music") {
        ToggleRow(
            label = "Refined Background",
            checked = settings.refinedBackground,
            onCheckedChange = { settings.refinedBackground = it },
            description = "Crossfade background changes, keep the image's edges, and deepen Cover Colors.",
            icon = Icons.Rounded.AutoAwesome,
        )
        if (type.moving) {
            ToggleRow(
                label = "Still Background",
                checked = settings.staticBackground || settings.lowPerformance,
                onCheckedChange = { settings.staticBackground = it },
                description = "Hold the background still instead of animating it.",
                icon = Icons.Rounded.MotionPhotosPaused,
                enabled = !settings.lowPerformance,
            )
        }
        if (type == BackgroundType.Default) {
            ToggleRow(
                label = "Move with the Music",
                checked = settings.beatReactiveBackground && !settings.staticBackground && !settings.lowPerformance,
                onCheckedChange = { settings.beatReactiveBackground = it },
                description = "Speed the background up and down with the song's tempo, loudness and beats, where Spotify has them.",
                icon = Icons.Rounded.GraphicEq,
                enabled = !settings.staticBackground && !settings.lowPerformance,
            )
            val held = settings.staticBackground || settings.lowPerformance
            ToggleRow(
                label = "Follow the Animated Cover",
                checked = settings.backgroundFollowsMotion && settings.animatedCover && !held,
                onCheckedChange = { settings.backgroundFollowsMotion = it },
                description = when {
                    held -> "Off while the background is held still."
                    !settings.animatedCover -> "Needs Animated Cover (Now Playing) on, whose video it follows."
                    else -> "When the song has an animated cover, the background moves with it."
                },
                icon = Icons.Rounded.SlowMotionVideo,
                enabled = settings.animatedCover && !held,
                searchDescription = "When the song has an animated cover, the background moves with it.",
            )
        }
    }
}

@Composable
internal fun NowPlayingContent(settings: AppSettings, viewModel: ExternalPlaybackViewModel) {
    SettingRow(
        label = "Song Header Size",
        description = "How tall the song header is. Small is a slim row, like Apple Music's.",
        icon = Icons.Rounded.Height,
        enabled = !settings.hideHeader,
    ) {
        SpicySelect(
            value = settings.headerSize.name,
            options = HeaderSize.entries.map { it.name },
            labels = HeaderSize.entries.map { it.label },
            onChange = { settings.headerSize = HeaderSize.valueOf(it) },
        )
    }
    SettingRow(
        label = "Release Year Position",
        description = "Show the release year beside the artists.",
        icon = Icons.Rounded.CalendarMonth,
    ) {
        SpicySelect(
            value = settings.releaseYearPosition.name,
            options = ReleaseYearPosition.entries.map { it.name },
            labels = ReleaseYearPosition.entries.map { it.label },
            onChange = { settings.releaseYearPosition = ReleaseYearPosition.valueOf(it) },
        )
    }
    ToggleRow(
        label = "Animated Cover",
        checked = settings.animatedCover && !settings.lowPerformance,
        onCheckedChange = { settings.animatedCover = it },
        description = "Play the album's animated cover from Apple Music, where it has one.",
        icon = Icons.Rounded.Animation,
        enabled = !settings.lowPerformance,
    )
    SettingRow(
        label = "Cover stage (landscape)",
        description = if (!settings.animatedCover && StageArt.AnimatedCover in settings.stageArt) {
            "In landscape, the first of these the song has fills its side of the screen. The animated cover needs Animated Cover on."
        } else {
            "In landscape, the first of these the song has fills its side of the screen, with the song docked along its bottom. None picked is off."
        },
        icon = Icons.Rounded.Panorama,
        enabled = !settings.lowPerformance,
        searchDescription = "In landscape, the song's Spotify Canvas, animated cover or cover art fills its side of the screen, with the song docked along its bottom.",
    ) {
        SpicyMultiSelect(
            selected = settings.stageArt.mapTo(mutableSetOf()) { it.name },
            options = StageArt.entries.map { it.name },
            labels = StageArt.entries.map { it.label },
            none = "Off",
            enabled = !settings.lowPerformance,
            onChange = { picked ->
                val arts = StageArt.entries.filter { it.name in picked }.toSet()
                // Canvas asks a third-party service, so it says what it sends first.
                if (StageArt.Canvas in arts && StageArt.Canvas !in settings.stageArt) {
                    viewModel.chooseCanvas { settings.stageArt = arts }
                } else {
                    settings.stageArt = arts
                }
            },
        )
    }
    SettingRow(
        label = "Big cover (portrait)",
        description = if (!settings.animatedCover && StageArt.AnimatedCover in settings.bigCoverArt) {
            "In portrait, the first of these the song has takes the big cover's place. The animated cover needs Animated Cover on."
        } else {
            "In portrait, the first of these the song has takes the big cover's place: a Canvas fills the screen, a cover spreads edge to edge. None picked is the big cover as it is."
        },
        icon = Icons.Rounded.Fullscreen,
        enabled = !settings.lowPerformance,
        searchDescription = "In portrait, the song's Spotify Canvas, animated cover or cover art takes the big cover's place, with the song docked under it.",
    ) {
        SpicyMultiSelect(
            selected = settings.bigCoverArt.mapTo(mutableSetOf()) { it.name },
            options = StageArt.entries.map { it.name },
            labels = StageArt.entries.map { it.label },
            none = "Off",
            enabled = !settings.lowPerformance,
            onChange = { picked ->
                val arts = StageArt.entries.filter { it.name in picked }.toSet()
                // Canvas asks a third-party service, so it says what it sends first.
                if (StageArt.Canvas in arts && StageArt.Canvas !in settings.bigCoverArt) {
                    viewModel.chooseCanvas { settings.bigCoverArt = arts }
                } else {
                    settings.bigCoverArt = arts
                }
            },
        )
    }
    if (StageArt.Canvas in settings.bigCoverArt && StageArt.AnimatedCover in settings.bigCoverArt) {
        ToggleRow(
            label = "Animated cover before Canvas",
            description = "The big cover shows a song's animated cover, and its Canvas only when it has none.",
            icon = Icons.Rounded.Fullscreen,
            checked = settings.bigCoverAnimatedFirst,
            enabled = !settings.lowPerformance,
            onCheckedChange = { settings.bigCoverAnimatedFirst = it },
        )
    }
    MoreOptions("Hiding the header, cover without lyrics, blurred Canvas, pausing, credits") {
        ToggleRow(
            label = "Hide the song header",
            checked = settings.hideHeader,
            onCheckedChange = { settings.hideHeader = it },
            description = "Show only the lyrics, centred on the page (in landscape, without the cover panel). The expand button still opens the big cover.",
            icon = Icons.Rounded.HideImage,
        )
        ToggleRow(
            label = "Show the cover without lyrics",
            checked = settings.expandWithoutLyrics,
            onCheckedChange = { settings.expandWithoutLyrics = it },
            description = "Grow the song header into the big cover when a song has no lyrics.",
            icon = Icons.Rounded.Album,
        )
        val showsCanvas = StageArt.Canvas in settings.stageArt || StageArt.Canvas in settings.bigCoverArt
        ToggleRow(
            label = "Blurred Canvas Behind",
            checked = settings.canvasBackdrop && showsCanvas && !settings.lowPerformance,
            onCheckedChange = { settings.canvasBackdrop = it },
            description = if (showsCanvas) {
                "While the stage or the big cover shows a Canvas, the background becomes it, blurred."
            } else {
                "Needs Spotify Canvas on the cover stage or the big cover."
            },
            icon = Icons.Rounded.BlurOn,
            enabled = showsCanvas && !settings.lowPerformance,
            searchDescription = "While the stage or the big cover shows a Canvas, the background becomes it, blurred.",
        )
        SettingRow(
            label = "Pause with the Music",
            description = "These hold their frame while the music is paused.",
            icon = Icons.Rounded.PauseCircle,
            searchDescription = "Animated covers and Canvas videos hold their frame while the music is paused.",
        ) {
            SpicyMultiSelect(
                selected = settings.pauseWithMusic.mapTo(mutableSetOf()) { it.name },
                options = LoopKind.entries.map { it.name },
                labels = LoopKind.entries.map { it.label },
                none = "Neither",
                onChange = { picked -> settings.pauseWithMusic = LoopKind.entries.filter { it.name in picked }.toSet() },
            )
        }
        SettingsSection("Credits") {
            SettingRow(
                label = "Pinned Lyrics Footer",
                description = "Keep source and community credits visible. Full also pins writers.",
                icon = Icons.Rounded.PushPin,
            ) {
                SpicySelect(
                    value = settings.pinnedFooter.name,
                    options = PinnedFooterMode.entries.map { it.name },
                    labels = PinnedFooterMode.entries.map { it.label },
                    onChange = { settings.pinnedFooter = PinnedFooterMode.valueOf(it) },
                )
            }
            ToggleRow(
                label = "Translation & Romanization Credits",
                checked = settings.textCredits,
                onCheckedChange = { settings.textCredits = it },
                description = "Say in the credits who translated and romanized the lyrics on screen.",
                icon = Icons.Rounded.Translate,
            )
            ToggleRow(
                label = "Open Profiles in Browser",
                checked = settings.profilesInBrowser,
                onCheckedChange = { settings.profilesInBrowser = it },
                description = "Open contributor profiles in your browser instead of inside the app.",
                icon = Icons.Rounded.OpenInBrowser,
            )
        }
    }
}

@Composable
internal fun ControlsContent(settings: AppSettings) {
    ToggleRow(
        label = "Timeline Outside Cover",
        checked = settings.timelineOutsideCover,
        onCheckedChange = { settings.timelineOutsideCover = it },
        description = "In landscape, show the timeline under the cover, above the song, where it always stays.",
        icon = Icons.Rounded.LinearScale,
    )
    SettingsSection("Floating Buttons") {
        ToggleRow(
            label = "Player buttons",
            checked = settings.playerButtons,
            onCheckedChange = { settings.playerButtons = it },
            description = "Show the extra buttons the player offers, like Like or Save. Shuffle and repeat always stay.",
            icon = Icons.Rounded.Widgets,
        )
        ToggleRow(
            label = "Floating lyrics button",
            checked = settings.pipButton,
            onCheckedChange = { settings.pipButton = it },
            description = "Show the button that shrinks the lyrics into a small floating window, over other apps.",
            icon = SpicyGlyphs.PictureInPicture,
            enabled = PictureInPicture.supported(LocalContext.current),
        )
        ToggleRow(
            label = "Lyrics Manager button",
            checked = settings.lyricsManagerButton,
            onCheckedChange = { settings.lyricsManagerButton = it },
            description = "Show its own button for the Lyrics Manager. It's always in quick settings and This song.",
            icon = SpicyGlyphs.LyricsManager,
        )
        ToggleRow(
            label = "Queue button",
            checked = settings.queueButton,
            onCheckedChange = { settings.queueButton = it },
            description = "Show its own button for the player's queue, while it shares one. It's always in quick settings.",
            icon = SpicyGlyphs.Queue,
        )
    }
    MoreOptions("Hiding the controls, and the resync, expand, quick settings, rotate and source buttons") {
        ToggleRow(
            label = "Hide controls",
            checked = settings.autoHideControls,
            onCheckedChange = { settings.autoHideControls = it },
            description = "Fade the controls out a while after the last touch. A touch brings them back.",
            icon = Icons.Rounded.TouchApp,
        )
        SettingRow(
            label = "Hide After",
            description = "How long the controls stay after the last touch.",
            icon = Icons.Rounded.Timer,
            enabled = settings.autoHideControls,
        ) {
            SpicySelect(
                value = settings.controlsHideDelay.name,
                options = ControlsHideDelay.entries.map { it.name },
                labels = ControlsHideDelay.entries.map { it.label },
                onChange = { settings.controlsHideDelay = ControlsHideDelay.valueOf(it) },
            )
        }
        ToggleRow(
            label = "Hide while paused",
            checked = settings.hideControlsWhilePaused,
            onCheckedChange = { settings.hideControlsWhilePaused = it },
            description = "Fade the controls while the song is paused too, not only while it plays.",
            icon = Icons.Rounded.PauseCircle,
            enabled = settings.autoHideControls,
        )
        SettingsSection("More Buttons") {
            ToggleRow(
                label = "Resync button",
                checked = settings.resyncButton,
                onCheckedChange = { settings.resyncButton = it },
                description = "Show the button that snaps the lyrics back in time with the player.",
                icon = SpicyGlyphs.Resync,
            )
            ToggleRow(
                label = "Expand button",
                checked = settings.expandButton,
                onCheckedChange = { settings.expandButton = it },
                description = "Show the button that opens the big cover. It stays while the cover is open, to get back.",
                icon = SpicyGlyphs.Expand,
            )
            ToggleRow(
                label = "Quick settings button",
                checked = settings.quickSettingsButton,
                onCheckedChange = { settings.quickSettingsButton = it },
                description = "Show the button that opens the delays, the Lyrics Manager and the queue without leaving the lyrics.",
                icon = SpicyGlyphs.QuickSettings,
            )
            ToggleRow(
                label = "Rotate button",
                checked = settings.rotateButton,
                onCheckedChange = { settings.rotateButton = it },
                description = "Show the button that turns the lyrics to landscape and back, even with rotation locked.",
                icon = SpicyGlyphs.Rotate,
            )
            ToggleRow(
                label = "Lyrics source button",
                checked = settings.sourceButton,
                onCheckedChange = { settings.sourceButton = it },
                description = "Show its own button for choosing this song's lyrics source. It's always in quick settings.",
                icon = SpicyGlyphs.Source,
            )
        }
    }
}

@Composable
internal fun ThemeContent(settings: AppSettings) {
    SettingRow(
        label = "Settings Theme",
        description = "Dress this screen in Spicy Lyrics' purple, or keep the plain glass.",
        icon = Icons.Rounded.Tune,
    ) {
        SpicySelect(
            value = settings.settingsTheme.name,
            options = ChromeTheme.entries.map { it.name },
            labels = ChromeTheme.entries.map { it.label },
            onChange = { settings.settingsTheme = ChromeTheme.valueOf(it) },
        )
    }
    SettingRow(
        label = "Pop-up Theme",
        description = "Dress the pop-ups, like quick settings, the queue and update notices, the same way.",
        icon = Icons.Rounded.Widgets,
    ) {
        SpicySelect(
            value = settings.popupTheme.name,
            options = ChromeTheme.entries.map { it.name },
            labels = ChromeTheme.entries.map { it.label },
            onChange = { settings.popupTheme = ChromeTheme.valueOf(it) },
        )
    }
    SettingRow(
        label = "App Icon",
        description = "Choose the icon on your home screen. It changes once you leave the app.",
        icon = Icons.Rounded.Apps,
    ) {
        SpicySelect(
            value = settings.appIcon.name,
            options = AppIcon.entries.map { it.name },
            labels = AppIcon.entries.map { it.label },
            onChange = { name ->
                settings.appIcon = AppIcon.valueOf(name)
            },
        )
    }
    ToggleRow(
        label = "Peek While Adjusting",
        checked = settings.peekWhileAdjusting,
        onCheckedChange = { settings.peekWhileAdjusting = it },
        description = "Clear this screen away while you drag a slider, so you see the change on the lyrics.",
        icon = Icons.Rounded.Visibility,
    )
}

@Composable
internal fun DeviceContent(settings: AppSettings) {
    ToggleRow(
        label = "Smoother motion",
        checked = settings.highRefreshRate,
        onCheckedChange = { settings.highRefreshRate = it },
        description = "Draw at your screen's full refresh rate instead of 60 Hz. Uses slightly more battery.",
        icon = Icons.Rounded.Animation,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) ToggleRow(
        label = "Show over lock screen",
        checked = settings.showOverLockScreen,
        onCheckedChange = { settings.showOverLockScreen = it },
        description = "The power button brings the lyrics back without unlocking. Settings still ask to unlock.",
        icon = Icons.Rounded.ScreenLockPortrait,
    )
    ToggleRow(
        label = "Ambient mode",
        checked = settings.ambientMode,
        onCheckedChange = { settings.ambientMode = it },
        description = "Left untouched while music plays, dim to the lyrics and the song on black.",
        searchDescription = "Untouched for 30 seconds while music plays, dim to the lyrics alone on black.",
        icon = Icons.Rounded.DarkMode,
    )
    SettingRow(
        label = "Dim After",
        description = "How long ambient mode waits after the last touch.",
        icon = Icons.Rounded.Timer,
        enabled = settings.ambientMode,
    ) {
        SpicySelect(
            value = settings.ambientDelay.name,
            options = AmbientDelay.entries.map { it.name },
            labels = AmbientDelay.entries.map { it.label },
            onChange = { settings.ambientDelay = AmbientDelay.valueOf(it) },
        )
    }
    ToggleRow(
        label = "Always-on display",
        checked = settings.ambientAsAod,
        onCheckedChange = { settings.ambientAsAod = it },
        description = "Once dimmed, keep the lyrics on as the always-on display at the lowest brightness, instead of the screen turning off. Uses a little battery while music plays.",
        searchDescription = "Keep the dimmed lyrics on as the always-on display at the lowest brightness.",
        icon = Icons.Rounded.Nightlight,
        enabled = settings.ambientMode,
    )
    SubPageCard {
        val supported = PictureInPicture.supported(LocalContext.current)
        SubPageRow(
            SettingsPage.FloatingLyrics,
            SpicyGlyphs.PictureInPicture,
            when {
                !supported -> "Not available on this phone"
                settings.autoPip -> "Opens when you leave the app · ${settings.pipShape.name}"
                else -> "Off"
            },
        )
    }
    WallpaperSection(settings)
    LyricsNotificationSection(settings)
    HapticsSection(settings)
    MoreOptions("Keeping the screen on, system bars, low performance mode, interface animations") {
        ToggleRow(
            label = "Keep the screen on",
            checked = settings.keepScreenOn,
            onCheckedChange = { settings.keepScreenOn = it },
            description = "Stop the screen from turning off while music plays.",
            icon = Icons.Rounded.StayCurrentPortrait,
        )
        SettingRow(
            label = "System bars",
            description = "Whether the status and navigation bars stay on screen over the lyrics.",
            icon = Icons.Rounded.Fullscreen,
        ) {
            SpicySelect(
                value = settings.systemBars.name,
                options = SystemBarsMode.entries.map { it.name },
                labels = SystemBarsMode.entries.map { it.label },
                onChange = { settings.systemBars = SystemBarsMode.valueOf(it) },
            )
        }
        SettingsSection("Performance") {
            ToggleRow(
                label = "Low performance mode",
                checked = settings.lowPerformance,
                onCheckedChange = { settings.lowPerformance = it },
                description = "Stills the background and cover and turns off blur and glow, for smoother lyrics on slower phones.",
                icon = Icons.Rounded.Speed,
            )
            ToggleRow(
                label = "Interface animations",
                checked = settings.uiAnimations && !settings.lowPerformance,
                onCheckedChange = { settings.uiAnimations = it },
                description = "Animate the playback buttons, the queue and pop-up screens. Off in low performance mode.",
                icon = Icons.Rounded.Animation,
                enabled = !settings.lowPerformance,
            )
        }
    }
}

@Composable
internal fun FloatingLyricsContent(settings: AppSettings) {
    val supported = PictureInPicture.supported(LocalContext.current)
    ToggleRow(
        label = "Floating lyrics",
        checked = settings.autoPip,
        onCheckedChange = { settings.autoPip = it },
        description = "Leave the app while a song plays and the lyrics shrink into a small floating window.",
        icon = SpicyGlyphs.PictureInPicture,
        enabled = supported,
    )
    SettingRow(
        label = "Shape",
        description = "The floating window's width to height.",
        icon = Icons.Rounded.AspectRatio,
        enabled = supported,
    ) {
        SpicySelect(
            value = settings.pipShape.name,
            options = PipShape.entries.map { it.name },
            labels = PipShape.entries.map { it.label },
            onChange = { settings.pipShape = PipShape.valueOf(it) },
            enabled = supported,
        )
    }
    if (settings.pipShape == PipShape.Custom) {
        SettingRow(
            label = "Custom shape",
            description = "Width to height, from tall (0.42 : 1) to wide (2.39 : 1).",
            icon = Icons.Rounded.Crop,
            enabled = supported,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.pipCustomRatio,
                range = PIP_RATIO_RANGE,
                step = 1,
                onValueChange = { settings.pipCustomRatio = it },
                default = 100,
                enabled = supported,
                valueText = { String.format(Locale.ROOT, "%.2f : 1", it / 100f) },
            )
        }
    }
    ToggleRow(
        label = "Show lyrics",
        checked = settings.pipShowLyrics,
        onCheckedChange = { settings.pipShowLyrics = it },
        description = "Off shows the song instead: cover, title and artist.",
        icon = Icons.Rounded.Lyrics,
        enabled = supported,
    )
    SettingRow(
        label = "Lyrics size",
        description = "The floating window's own text size, apart from the full screen's.",
        icon = Icons.Rounded.FormatSize,
        enabled = supported && settings.pipShowLyrics,
        stacked = true,
        searchDescription = "Make the floating lyrics smaller or bigger. Picture-in-picture text size.",
    ) {
        SpicyBipolarSlider(
            value = settings.pipLyricsSize,
            range = LYRICS_SIZE_RANGE,
            step = 5,
            onValueChange = { settings.pipLyricsSize = it },
            default = 100,
            unit = "%",
            enabled = supported && settings.pipShowLyrics,
        )
    }
    ToggleRow(
        label = "Current line at the top",
        checked = settings.pipLineAtTop,
        onCheckedChange = { settings.pipLineAtTop = it },
        description = "Keep the line being sung at the top of the window, so more of what's next fits.",
        icon = Icons.Rounded.VerticalAlignTop,
        enabled = supported && settings.pipShowLyrics,
    )
    ToggleRow(
        label = "Song bar",
        checked = settings.pipDock,
        onCheckedChange = { settings.pipDock = it },
        description = "Square and tall windows show the title, artist and timeline under the lyrics.",
        icon = Icons.Rounded.MusicNote,
        enabled = supported && settings.pipShowLyrics,
    )
    ToggleRow(
        label = "Cover in the song bar",
        checked = settings.pipDockCover,
        onCheckedChange = { settings.pipDockCover = it },
        description = "A small cover beside the title and artist.",
        icon = Icons.Rounded.Album,
        enabled = supported && settings.pipShowLyrics && settings.pipDock,
    )
    SettingRow(
        label = "Without lyrics, show",
        description = "What the window shows for a song that has no lyrics.",
        icon = Icons.Rounded.Album,
        enabled = supported && settings.pipShowLyrics,
    ) {
        SpicySelect(
            value = settings.pipWithoutLyrics.name,
            options = PipWithoutLyrics.entries.map { it.name },
            labels = PipWithoutLyrics.entries.map { it.label },
            onChange = { settings.pipWithoutLyrics = PipWithoutLyrics.valueOf(it) },
            enabled = supported && settings.pipShowLyrics,
        )
    }
}

@Composable
internal fun SourcesContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    var clientKey by remember { mutableStateOf("") }
    val ranking = state.sourceRanking
    val names = state.sourceDescriptors.associate { it.id to it.displayName }
    Searchable("Sources", "Priority", "Order", "Providers", *ranking.origins.map { it.displayName }.toTypedArray()) {
        Text(
            "Each source is where lyrics come from; higher sources win when the timing is as good. Tap one to pick the services " +
                "that fetch it and the sync types it may give. Services that haven't said any app may use them say what they send " +
                "before they're switched on.",
            style = DescriptionStyle,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = SpicySpacing.S3),
        )
    }
    SourceList(ranking, names, viewModel)
    MoreOptions("Custom sources, background vocals in brackets, your own Spicy Lyrics key") {
        CustomSourcesSection(state, viewModel)
        SettingsSection("Reading lyrics") {
            ToggleRow(
                label = "Background vocals in brackets",
                description = "A \"(…)\" at the start or end of a synced line is shown as its background vocals.",
                checked = state.splitBackgroundVocals,
                onCheckedChange = viewModel::setSplitBackgroundVocals,
                icon = Icons.Rounded.RecordVoiceOver,
            )
        }
        SettingsSection("Spicy Lyrics key") {
            val context = LocalContext.current
            val ownKey = state.ownKeyHint
            SettingRow(
                label = "Get your own key",
                description = if (ownKey == null) {
                    "The built-in key works for everyone. If you want your own, sign in, tap Add, and paste the client key below."
                } else {
                    "You're using your own key, $ownKey. Its page on the developer site can pause or replace it."
                },
                icon = Icons.Rounded.Key,
            ) {
                SpicyButton(if (ownKey == null) "Get key" else "Open", onClick = { openUrl(context, SpicyLyricsKey.CATALOG_URL) })
            }
            SettingRow(
                label = "Your Spicy Lyrics key",
                description = if (ownKey == null) "Paste the client key (sl_pk_…). Empty uses the built-in key." else "Paste a new key to replace yours.",
                icon = Icons.Rounded.Key,
                stacked = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    SpicyTextField(clientKey, { clientKey = it }, placeholder = "sl_pk_…", password = true, modifier = Modifier.weight(1f))
                    SpicyButton("Use", onClick = {
                        viewModel.useApiKey(clientKey)
                        if (SpicyLyricsKey.check(clientKey) is SpicyLyricsKey.Check.Ok) clientKey = ""
                    })
                }
            }
            if (ownKey != null) {
                SettingRow(
                    label = "Use the built-in key",
                    description = "Forget your key on this phone. It keeps working on the developer site until you delete it there.",
                    icon = Icons.Rounded.KeyOff,
                ) {
                    SpicyButton("Remove", onClick = { viewModel.useApiKey("") })
                }
            }
        }
    }
}

/**
 * The sources as cards, in the user's order, reordered by dragging a card's handle: the card
 * follows the finger, the others make way, the page scrolls at its edges, and the order is saved
 * when it's dropped. Tapping a card opens it.
 */
@Composable
private fun SourceList(ranking: SourceRanking, names: Map<String, String>, viewModel: ExternalPlaybackViewModel) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val byId = ranking.origins.associateBy { it.id }
    // While a card is dragged: the order shown, which card, and how far it is from its slot.
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<String, Float>() }
    val tops = remember { mutableMapOf<String, Float>() }
    val density = LocalDensity.current
    val gap = with(density) { SpicySpacing.S2.toPx() }
    val edge = with(density) { DRAG_EDGE.toPx() }
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val scroll = LocalPageScroll.current
    val haptics = LocalHapticFeedback.current
    val animate = LocalUiAnimations.current
    val saved = ranking.origins.map { it.id }
    val shown = dragOrder ?: saved
    // A card's drag handler outlives the composition it started in: it reads the order through
    // these, or a second drag starts from the order before the first one.
    val currentShown by rememberUpdatedState(shown)
    val currentSaved by rememberUpdatedState(saved)

    // Past half of a neighbour, the dragged card takes its place; its offset keeps it under the finger.
    fun settle() {
        val id = dragged ?: return
        val order = (dragOrder ?: return).toMutableList()
        var i = order.indexOf(id)
        var moved = false
        while (true) {
            val toward = if (dragOffset > 0f) i + 1 else i - 1
            if (toward !in order.indices) break
            val room = (heights[order[toward]] ?: break) + gap
            if (abs(dragOffset) <= room / 2f) break
            Collections.swap(order, i, toward)
            dragOffset += if (toward > i) -room else room
            i = toward
            moved = true
        }
        if (moved) {
            dragOrder = order
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Near the top or bottom of the screen, the page scrolls under the dragged card.
    LaunchedEffect(dragged) {
        val id = dragged ?: return@LaunchedEffect
        if (scroll == null || windowHeight <= 0f) return@LaunchedEffect
        while (dragged == id) {
            val top = (tops[id] ?: 0f) + dragOffset
            val bottom = top + (heights[id] ?: 0f)
            val speed = when {
                top < edge -> -(edge - top) / edge
                bottom > windowHeight - edge -> (bottom - windowHeight + edge) / edge
                else -> 0f
            }.coerceIn(-1f, 1f) * DRAG_SCROLL_PX
            if (speed != 0f) {
                dragOffset += scroll.scrollBy(speed)
                settle()
            }
            withFrameNanos { }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        shown.forEachIndexed { index, id ->
            val origin = byId[id] ?: return@forEachIndexed
            val summary = origin.summary(ranking)
            val isDragged = dragged == id
            key(id) {
                Searchable("Sources", origin.displayName, summary, *origin.providerIds.map { names[it] ?: it }.toTypedArray()) {
                    SourceCard(
                        rank = index + 1,
                        origin = origin,
                        summary = summary,
                        ranking = ranking,
                        providerName = { names[it] ?: it },
                        expanded = id in expanded,
                        dragged = isDragged,
                        onExpand = { expanded = if (id in expanded) expanded - id else expanded + id },
                        onEnabledChange = { viewModel.setSourceEnabled(id, it) },
                        onProviderChange = { provider, on -> viewModel.setProviderEnabled(id, provider, on) },
                        onSyncTypesChange = { viewModel.setSourceSyncTypes(id, it) },
                        handle = Modifier.pointerInput(id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragOrder = currentShown
                                    dragged = id
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val order = dragOrder
                                    dragged = null
                                    dragOffset = 0f
                                    dragOrder = null
                                    if (order != null && order != currentSaved) viewModel.setSourceOrder(order)
                                },
                                onDragCancel = {
                                    dragged = null
                                    dragOffset = 0f
                                    dragOrder = null
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                settle()
                            }
                        },
                        modifier = Modifier
                            // Where the card's slot is, read before the drag moves it.
                            .onGloballyPositioned {
                                heights[id] = it.size.height.toFloat()
                                tops[id] = it.positionInWindow().y
                            }
                            .glidesToPlace(enabled = animate && dragged != null && !isDragged)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = dragOffset
                                    scaleX = DRAGGED_SCALE
                                    scaleY = DRAGGED_SCALE
                                    // Lifted off the list: a shadow under it.
                                    shadowElevation = DRAGGED_ELEVATION.toPx()
                                    shape = RoundedCornerShape(SpicyRadii.Lg)
                                }
                            },
                    )
                }
            }
        }
    }
}

/** A card the reorder moved glides from where it was instead of jumping. */
private fun Modifier.glidesToPlace(enabled: Boolean): Modifier = composed {
    val shift = remember { Animatable(0f) }
    var lastY by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val enabledNow by rememberUpdatedState(enabled)
    onPlaced { coordinates ->
        val y = coordinates.positionInParent().y
        val previous = lastY
        lastY = y
        if (enabledNow && previous != null && previous != y) {
            scope.launch {
                shift.snapTo(shift.value + previous - y)
                shift.animateTo(0f, tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard))
            }
        }
    }.graphicsLayer { translationY = shift.value }
}

/**
 * A source's card: the rank in a ring, the name and what it gives, a chevron, the switch and the
 * drag handle. Tapping the card opens it to the providers that fetch the source and the sync
 * types it may give.
 */
@Composable
private fun SourceCard(
    rank: Int,
    origin: LyricsOrigin,
    summary: String,
    ranking: SourceRanking,
    providerName: (String) -> String,
    expanded: Boolean,
    dragged: Boolean,
    onExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProviderChange: (String, Boolean) -> Unit,
    onSyncTypesChange: (Set<RemoteLyricsQuality>) -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    val enabled = LyricsSourceSettings.isOn(ranking, origin.id)
    val animate = LocalUiAnimations.current
    val chevron by animateFloatAsState(
        if (expanded) 180f else 0f,
        if (animate) tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard) else snap(),
        label = "sourceChevron",
    )
    // Dragged, the card is solid, or the cards it passes would show through its tint: it carries
    // the page's own backdrop (the blurred lyrics and the theme's wash), so it looks the same.
    val backdrop = LocalSettingsBackdrop.current
    val brand = SpicyColors.brand
    val lifted = if (!dragged) Modifier else Modifier
        .clip(RoundedCornerShape(SpicyRadii.Lg))
        .then(
            if (backdrop != null) {
                Modifier.hazeEffect(backdrop, SettingsBackdrop) { inputScale = HazeInputScale.Fixed(BACKDROP_INPUT_SCALE) }
            } else {
                Modifier.background(Color.Black.copy(alpha = 0.97f))
            },
        )
        .drawBehind {
            if (brand != null) {
                drawRect(brand.fieldDeep.copy(alpha = 0.14f))
                drawBrandRamp(brand, fromAlpha = 0.26f, toAlpha = 0.4f)
            }
        }
    Column(modifier.fillMaxWidth().then(lifted).outlinedCard(tinted = true)) {
        Row(
            Modifier.fillMaxWidth().padding(end = SpicySpacing.S1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
        ) {
          // What opens the source, beside its switch rather than around it: a remote's arrows only
          // move to what lies outside the focused control.
          Row(
            Modifier
                .weight(1f)
                .focusRing(RoundedCornerShape(SpicyRadii.Md))
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onExpand)
                .padding(start = SpicySpacing.S3, top = SpicySpacing.S3, bottom = SpicySpacing.S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
          ) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(RankFill, CircleShape)
                    .border(1.dp, SpicyColors.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$rank", style = SpicyType.Caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
            RowLabel(origin.displayName, summary, Modifier.weight(1f).alpha(if (enabled) 1f else DISABLED_ALPHA))
            Icon(
                Icons.Rounded.ExpandMore,
                if (expanded) "Close ${origin.displayName}" else "Open ${origin.displayName}",
                tint = SpicyColors.TextSecondary,
                modifier = Modifier.size(20.dp).rotate(chevron),
            )
          }
            SpicyToggle(enabled, onCheckedChange = onEnabledChange)
            Box(handle.size(32.dp, 40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragIndicator, "Drag ${origin.displayName} to reorder", tint = SpicyColors.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (animate) expandVertically(tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal)) + fadeIn(tween(SpicyMotion.FAST_MS)) else EnterTransition.None,
            exit = if (animate) shrinkVertically(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Standard)) + fadeOut(tween(SpicyMotion.FAST_MS)) else ExitTransition.None,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = SpicySpacing.S3, end = SpicySpacing.S3, bottom = SpicySpacing.S3),
                verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
            ) {
                origin.providerIds.forEach { provider ->
                    val on = ranking.pairOn(origin.id, provider)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        RowLabel(
                            providerName(provider),
                            providerNote(origin, provider),
                            Modifier.weight(1f).alpha(if (on && ranking.sourceOn(origin.id)) 1f else DISABLED_ALPHA),
                            icon = if (LyricsBlends.byId(provider) != null) Icons.AutoMirrored.Rounded.CallMerge else Icons.Rounded.Cloud,
                        )
                        SpicyToggle(on, onCheckedChange = { onProviderChange(provider, it) })
                    }
                }
                if (origin.qualities.size > 1) {
                    val off = ranking.policy.syncTypesOff[origin.id].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                        RowLabel("Sync types", "Answers in a type left out don't count.", Modifier.weight(1f), icon = Icons.Rounded.Sync)
                        SpicyMultiSelect(
                            selected = origin.qualities.filter { it !in off }.map { it.name }.toSet(),
                            options = origin.qualities.map { it.name },
                            labels = origin.qualities.map { it.typeLabel() },
                            onChange = { picked -> onSyncTypesChange(picked.map(RemoteLyricsQuality::valueOf).toSet()) },
                        )
                    }
                }
            }
        }
    }
}

/** How close to the screen's top or bottom a dragged card scrolls the page. */
private val DRAG_EDGE = 72.dp
/** The page's fastest scroll under a dragged card, per frame. */
private const val DRAG_SCROLL_PX = 18f
private const val DRAGGED_SCALE = 1.02f
private val DRAGGED_ELEVATION = 12.dp

/** The rank ring's fill. */
private val RankFill = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.09f)

/** What a source gives (the sync types it has on) and, when several can fetch it, how many do. */
private fun LyricsOrigin.summary(ranking: SourceRanking): String {
    val off = ranking.policy.syncTypesOff[id].orEmpty()
    val types = qualities.filter { it !in off }
    val synced = types.filter { it != RemoteLyricsQuality.PLAIN }.map { if (it == RemoteLyricsQuality.WORD_SYNCED) "word" else "line" }
    val gives = listOfNotNull(
        synced.takeIf { it.isNotEmpty() }?.joinToString(" and ", postfix = " synced"),
        "unsynced".takeIf { RemoteLyricsQuality.PLAIN in types },
    ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "No sync type on" }
    if (providerIds.size <= 1) return gives
    return "$gives · ${providerIds.count { ranking.pairOn(id, it) }} of ${providerIds.size} on"
}

private fun RemoteLyricsQuality.typeLabel() = when (this) {
    RemoteLyricsQuality.WORD_SYNCED -> "Word"
    RemoteLyricsQuality.LINE_SYNCED -> "Line"
    else -> "Unsynced"
}

/** A short line on how [provider] gets [origin]'s lyrics, where that isn't plain from its name. */
private fun providerNote(origin: LyricsOrigin, provider: String): String? = when {
    LyricsBlends.byId(provider) != null -> "Lines from the sources above, word timing from its donors."
    provider == "rmm_revival" -> "Relays Spicy Lyrics' API by Apple Music ID."
    provider == "spicy_unison" -> "Spicy Lyrics' copy of Unison's lyrics, by the song's ISRC or YouTube video."
    provider == "spicy_lyrics" && origin.id != LyricsOrigins.SPICY_COMMUNITY -> "Passed on by Spicy Lyrics' API."
    provider == "lrc_red" && origin.id == LyricsOrigins.APPLE_MUSIC -> "Serves Apple Music's lyrics by ISRC."
    provider == "lrcmux" -> "Passes on what it finds at ${origin.displayName}."
    else -> null
}

@Composable
internal fun AdvancedContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, updater: UpdateViewModel, settings: AppSettings) {
    SettingRow(
        label = "Clear All Caches for Current Song",
        description = "Remove all cached lyrics data for the currently playing track.",
        icon = Icons.Rounded.DeleteSweep,
    ) {
        SpicyButton("Clear", onClick = viewModel::clearCurrentSongCaches)
    }
    SettingRow(
        label = "Clear Stored Lyrics Cache",
        description = "Delete every cached lyric and translation, then reload the current song.",
        icon = Icons.Rounded.DeleteForever,
    ) {
        SpicyButton("Clear Cache", onClick = viewModel::clearStoredLyricsCache)
    }
    SettingRow(
        label = "Clear Current Song from Internal State",
        description = "Remove the current song's lyrics from the in-memory state only.",
        icon = Icons.Rounded.Memory,
    ) {
        SpicyButton("Clear State", onClick = viewModel::clearCurrentSongFromMemory)
    }
    SettingsSection("Backup") {
        SettingsBackupRows(viewModel, settings)
    }
    SettingsSection("Help") {
        SettingRow(
            label = "Show the Introduction",
            description = "Walk through the first-run screens again, gestures included.",
            icon = Icons.Rounded.School,
        ) {
            SpicyButton("Show", onClick = {
                settings.introDone = false
                settings.introReplay = true
            })
        }
    }
    if (updater.enabled) {
        SettingsSection("Updates") {
            ToggleRow(
                label = "Include pre-releases",
                checked = settings.includePrereleases,
                onCheckedChange = { settings.includePrereleases = it },
                description = "Also offer test builds, which get new things first and may be rough.",
                icon = Icons.Rounded.Science,
            )
        }
    }
    SettingsSection("Privacy") {
        val context = LocalContext.current
        ToggleRow(
            label = "Share anonymous usage stats",
            checked = settings.usageStats,
            onCheckedChange = {
                settings.usageStats = it
                UsageStats.onSettingChanged(context, it)
            },
            description = "How many people use the app, on which versions and devices, and which features they use. Never what you listen to.",
            icon = Icons.Rounded.Insights,
        )
    }
    SettingsSection("Debug info") {
        CopyDebugInfoRow(state)
        CopyExtraLogsRow()
    }
    MoreOptions("Session, these lyrics, translation and last lookup details") {
        SettingsSection("Session") { InfoLines(sessionLines(state)) }
        lyricsLines(state)?.let { details -> SettingsSection("These lyrics") { InfoLines(details) } }
        SettingsSection("Romanization and translation") {
            InfoLines(textLines(LocalContext.current, state))
            // What translation did last, newest last. The whole story goes in the extra logs.
            val log = com.tx24.spicyplayer.playback.TranslationTrace.lines(android.os.SystemClock.elapsedRealtime())
            if (log.isEmpty()) {
                Text("Nothing translated yet", style = DescriptionStyle, modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1))
            }
            log.takeLast(SHOWN_TRANSLATION_LOG).forEach { line ->
                Searchable("Translation log", line, "Diagnostics") {
                    Text(line, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary),
                        modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1))
                }
            }
        }
        SettingsSection("Last lookup") {
            Searchable("Last lookup", "Attempts", "Diagnostics") {
                if (state.providerAttempts.isEmpty()) {
                    Text("No lookup yet", style = DescriptionStyle, modifier = Modifier.padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1))
                }
                state.providerAttempts.forEach { attempt -> AttemptLine(attempt, state) }
            }
        }
    }
}

/** How many of the translation log's lines Advanced shows; Copy extra logs has them all. */
private const val SHOWN_TRANSLATION_LOG = 5

internal fun sessionLines(state: PlayerUiState): List<Pair<String, String>> = listOfNotNull(
    "Player" to (state.sourcePackage ?: "None"),
    "Output" to state.outputLabel,
    state.matchInfo?.let { "Match" to it },
    state.detectedSpotifyId?.let { "Spotify ID" to it },
    if (state.canSeek) null else "Seeking" to "This player doesn't allow it",
    state.lastCommandLatencyMs?.let { "Command answered" to "in $it ms" },
    state.clockDriftMs?.let { "Clock drift" to "${it.signed()} ms" },
)

/** Where the shown lyrics came from, or null while none are shown. */
internal fun lyricsLines(state: PlayerUiState): List<Pair<String, String>>? {
    val lyrics = state.lyrics as? LyricsState.Ready ?: return null
    return listOfNotNull(
        "Source" to lyrics.provider,
        lyrics.source?.let { "Origin" to it },
        lyrics.maker?.let { "Made by" to it.username },
        lyrics.uploader?.let { "Uploaded by" to it.username },
        lyrics.songwriters.takeIf { it.isNotEmpty() }?.let { "Writers" to it.joinToString() },
    )
}

internal fun attemptSourceName(attempt: ProviderAttempt, state: PlayerUiState): String =
    (state.sourceDescriptors + LOCAL_SOURCE + UPLOADED_SOURCE)
        .firstOrNull { it.id == attempt.sourceId }?.displayName ?: attempt.sourceId

internal fun ProviderAttemptOutcome.label(): String = when (this) {
    ProviderAttemptOutcome.HIT -> "Found"
    ProviderAttemptOutcome.MISS -> "Not found"
    ProviderAttemptOutcome.NEEDS_MATCH -> "Needs a Spotify link"
    ProviderAttemptOutcome.DISABLED -> "Switched off"
    ProviderAttemptOutcome.COOLING_DOWN -> "Resting after errors"
    ProviderAttemptOutcome.QUEUED, ProviderAttemptOutcome.PENDING -> "Asking…"
    ProviderAttemptOutcome.SKIPPED -> "Skipped"
    ProviderAttemptOutcome.FILTERED -> "Not counted"
    ProviderAttemptOutcome.UNAVAILABLE, ProviderAttemptOutcome.MALFORMED_HIT -> "Failed"
}

/** The attempt's quality, failure and retry time, joined; [time] formats the retry time. */
internal fun attemptDetail(attempt: ProviderAttempt, time: (java.util.Date) -> String): String = listOfNotNull(
    attempt.quality.takeIf { it != RemoteLyricsQuality.NONE }?.label(),
    attempt.failureCategory?.name?.lowercase()?.replace('_', ' '),
    attempt.message,
    attempt.retryAt?.let { "tries again at " + time(java.util.Date(it.toEpochMilli())) },
).joinToString(" · ")

/** Label and value pairs, searchable by their labels. */
@Composable
private fun InfoLines(lines: List<Pair<String, String>>) {
    lines.forEach { (label, value) ->
        Searchable(label, value, "Diagnostics") {
            Row(Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1)) {
                Text(label, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary), modifier = Modifier.width(INFO_LABEL_WIDTH))
                Text(value, style = SpicyType.Caption, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttemptLine(attempt: ProviderAttempt, state: PlayerUiState) {
    val name = attemptSourceName(attempt, state)
    val outcome = attempt.outcome.label()
    val color = when (attempt.outcome) {
        ProviderAttemptOutcome.HIT -> SpicyColors.StatusSuccess
        ProviderAttemptOutcome.NEEDS_MATCH, ProviderAttemptOutcome.COOLING_DOWN -> SpicyColors.StatusWarning
        ProviderAttemptOutcome.QUEUED, ProviderAttemptOutcome.PENDING -> SpicyColors.StatusInfo
        ProviderAttemptOutcome.UNAVAILABLE, ProviderAttemptOutcome.MALFORMED_HIT -> SpicyColors.StatusDanger
        ProviderAttemptOutcome.MISS, ProviderAttemptOutcome.DISABLED, ProviderAttemptOutcome.SKIPPED,
        ProviderAttemptOutcome.FILTERED -> SpicyColors.TextTertiary
    }
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val detail = attemptDetail(attempt) { date ->
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        java.text.SimpleDateFormat(pattern, locale).format(date)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S3, vertical = SpicySpacing.S1)) {
        Text(name, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary), modifier = Modifier.width(INFO_LABEL_WIDTH))
        Column(Modifier.weight(1f)) {
            Text(outcome, style = SpicyType.Caption.copy(color = color, fontWeight = FontWeight.Medium))
            if (detail.isNotEmpty()) Text(detail, style = DescriptionStyle)
        }
    }
}

private fun RemoteLyricsQuality.label() = when (this) {
    RemoteLyricsQuality.WORD_SYNCED -> "word synced"
    RemoteLyricsQuality.LINE_SYNCED -> "line synced"
    RemoteLyricsQuality.PLAIN -> "unsynced"
    RemoteLyricsQuality.NONE -> "nothing"
}

/** The source, and for a source passing on another's lyrics, where they came from: "Spicy Lyrics (Apple Music)". */
private fun lyricsFrom(lyrics: LyricsState.Ready): String {
    val origin = lyrics.source?.takeIf { it.isNotBlank() && it != lyrics.provider } ?: return lyrics.provider
    return "${lyrics.provider} (${origin})"
}

/** One line on where the current lyrics stand. */
internal fun lyricsSummary(lyrics: LyricsState): String = when (lyrics) {
    is LyricsState.Ready -> lyricsFrom(lyrics) + ", " + when (lyrics.lyricsType) {
        LyricsType.Syllable -> "word synced"
        LyricsType.Line -> "line synced"
        LyricsType.Static -> "unsynced"
    }
    LyricsState.Loading -> "Looking for lyrics…"
    is LyricsState.Error -> lyrics.message
    LyricsState.Idle -> "No lyrics yet"
}

/**
 * [lyricsSummary], naming the source the lookup is still waiting on while the lyrics shown are a
 * stand-in that may yet be replaced.
 */
internal fun songSummary(state: PlayerUiState): String {
    val summary = lyricsSummary(state.lyrics) +
        if (state.songDelayMs != 0) " · ${state.songDelayMs.signed()} ms delay" else ""
    if (state.lyrics !is LyricsState.Ready) return summary
    val waiting = state.providerAttempts.firstOrNull { it.outcome == ProviderAttemptOutcome.PENDING } ?: return summary
    val name = state.sourceDescriptors.firstOrNull { it.id == waiting.sourceId }?.displayName ?: waiting.sourceId
    return "$summary · checking $name…"
}

/**
 * A delay's slider, with a stepper under it: −100, −10, the delay (tap to reset), +10, +100.
 * Used for both the output's delay and the song's.
 */
@Composable
internal fun DelayControl(valueMs: Int, onValueChange: (Int) -> Unit, enabled: Boolean = true) {
    fun nudge(deltaMs: Int) {
        if (enabled) onValueChange((valueMs + deltaMs).coerceIn(-DELAY_RANGE_MS, DELAY_RANGE_MS))
    }
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        SpicyBipolarSlider(
            value = valueMs,
            range = -DELAY_RANGE_MS..DELAY_RANGE_MS,
            step = DELAY_STEP_MS,
            onValueChange = onValueChange,
            enabled = enabled,
            showValue = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2), verticalAlignment = Alignment.CenterVertically) {
            val step = Modifier.weight(1f)
            SpicyButton("−$DELAY_BIG_STEP_MS", { nudge(-DELAY_BIG_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
            SpicyButton("−$DELAY_STEP_MS", { nudge(-DELAY_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
            SpicyResettableValue(
                "${valueMs.signed()} ms",
                atDefault = valueMs == 0,
                onReset = { if (enabled) onValueChange(0) },
                modifier = Modifier.weight(1.6f),
                enabled = enabled,
            )
            SpicyButton("+$DELAY_STEP_MS", { nudge(DELAY_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
            SpicyButton("+$DELAY_BIG_STEP_MS", { nudge(DELAY_BIG_STEP_MS) }, step, enabled, horizontalPadding = 0.dp)
        }
    }
}

/** This song's own delay, added to the output's. */
@Composable
internal fun SongDelayRow(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    SettingRow(
        label = "Song delay",
        description = if (state.localLyricsKey == null) "Play a song to give it its own delay."
            else "Only for this song, added to the delay for ${state.outputLabel}. For lyrics timed a little off.",
        icon = Icons.Rounded.MusicNote,
        stacked = true,
    ) {
        DelayControl(state.songDelayMs, viewModel::setSongDelay, enabled = state.localLyricsKey != null)
    }
}

/** The player app's own delay, added to the output's. */
@Composable
internal fun PlayerDelayRow(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    val player = state.playerLabel
    SettingRow(
        label = "Player delay",
        description = if (player == null) "Play something to give its app its own delay."
            else "For every song from $player, added to the delay for ${state.outputLabel}.",
        icon = Icons.Rounded.Apps,
        stacked = true,
    ) {
        DelayControl(state.playerDelayMs, viewModel::setPlayerDelay, enabled = state.sourcePackage != null)
    }
}

/** The delay slider's reach and step; the buttons nudge by one step. */
private const val DELAY_RANGE_MS = com.tx24.spicyplayer.playback.MAX_DELAY_MS
private const val DELAY_STEP_MS = 10
private const val DELAY_BIG_STEP_MS = 100

private val INFO_LABEL_WIDTH = 116.dp

/** Saving the settings to a file and bringing them back from one, through the system's file picker. */
@Composable
private fun SettingsBackupRows(viewModel: ExternalPlaybackViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsBackupStore(context.applicationContext) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(store.export().toByteArray()) } != null
                }.getOrDefault(false)
            }
            if (written) UsageStats.count(UsageCounter.BACKUP_EXPORT)
            viewModel.showMessage(if (written) "Settings saved." else "Couldn't save the settings there.")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: throw SettingsBackup.InvalidBackup("Couldn't open that file.")
                    store.restore(text)
                }.exceptionOrNull()
            }
            if (error == null) {
                UsageStats.count(UsageCounter.BACKUP_RESTORE)
                settings.reload()
                viewModel.reloadSavedSettings()
                viewModel.showMessage("Settings restored.")
            } else {
                viewModel.showMessage((error as? SettingsBackup.InvalidBackup)?.message ?: "Couldn't read that backup.")
            }
        }
    }
    SettingRow(
        label = "Back up settings",
        description = "Save your settings, source order, delays and Spotify links to a file. Your custom font and Spicy Lyrics key stay out.",
        icon = Icons.Rounded.Save,
    ) {
        SpicyButton("Save", onClick = { export.launch("spicy-lyrics-mobile-settings-${java.time.LocalDate.now()}.json") })
    }
    SettingRow(
        label = "Restore settings",
        description = "Replace your settings with the ones in a backup file.",
        icon = Icons.Rounded.SettingsBackupRestore,
    ) {
        // Some file managers label .json as plain text or as nothing in particular.
        SpicyButton("Restore", onClick = { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
    }
}
