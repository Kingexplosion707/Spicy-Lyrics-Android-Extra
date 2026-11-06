package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.ui.components.SettingsPeek
import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSkeleton
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.components.screenSlide
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerContent
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import dev.chrisbanes.haze.HazeState

/** The screens of quick settings; a floating button can open it on its own. */
enum class QuickScreen { Main, Queue, LyricsManager, Source }

/**
 * The quick settings pop-up, from its floating button: one card leading to the player's queue
 * (when it shares one), the song's lyrics source and the Lyrics Manager, then the language and delays, tuned while the lyrics keep playing behind it rather
 * than from inside Settings. The queue and the Lyrics Manager open as screens of this pop-up,
 * sliding in over it, with a way back.
 */
@Composable
fun QuickSettingsModal(
    visible: Boolean,
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    backdrop: HazeState?,
    /** The screen it opens on: the queue's and the source's own floating buttons open theirs. */
    startOn: QuickScreen = QuickScreen.Main,
    onDismiss: () -> Unit,
) {
    var screen by remember { mutableStateOf(QuickScreen.Main) }
    var uploading by remember { mutableStateOf(false) }
    LaunchedEffect(visible, state.lyrics) { if (visible) viewModel.detectLyricsLanguage() }
    // Opens on its first screen each time.
    LaunchedEffect(visible) {
        if (visible) {
            screen = startOn
            if (startOn == QuickScreen.Queue) UsageStats.count(UsageCounter.QUEUE)
            uploading = false
        }
    }
    fun open(target: QuickScreen) {
        if (target == QuickScreen.Queue) UsageStats.count(UsageCounter.QUEUE)
        if (target == QuickScreen.LyricsManager) {
            UsageStats.count(UsageCounter.LYRICS_MANAGER)
            viewModel.refreshLocalLyrics()
        }
        screen = target
    }
    // Dragging a slider here clears the pop-up away to show the lyrics, as in Settings.
    val peek = remember { SettingsPeek(allowRows = { settings.peekWhileAdjusting }) }
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        peek = peek,
        title = when (screen) {
            QuickScreen.Main -> "Quick settings"
            QuickScreen.Queue -> "Queue"
            QuickScreen.LyricsManager -> if (uploading) "Upload TTML" else "Local Lyrics DB"
            QuickScreen.Source -> "Lyrics source"
        },
        skeleton = { SettingsSkeleton(rows = 4) },
        // The header's back arrow (and Back) step out of a screen before Back closes the pop-up.
        onBack = when {
            screen == QuickScreen.Main -> null
            uploading -> ({ uploading = false })
            else -> ({ screen = QuickScreen.Main })
        },
    ) {
        val animate = LocalUiAnimations.current
        AnimatedContent(
            targetState = screen,
            transitionSpec = { screenSlide(forward = targetState != QuickScreen.Main, animate = animate) },
            label = "quickSettingsScreen",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
                when (shown) {
                    QuickScreen.Main -> MainScreen(state, viewModel, settings.quickDelays, ::open)
                    // Its own height: the list scrolls inside the pop-up's body.
                    QuickScreen.Queue -> QueueList(state, viewModel, onJumped = onDismiss, modifier = Modifier.height(QUEUE_HEIGHT))
                    QuickScreen.LyricsManager -> LyricsManagerContent(state, viewModel, settings, uploading, { uploading = it }, onDismiss)
                    QuickScreen.Source -> SourceList(state, viewModel, onChosen = { screen = QuickScreen.Main }, modifier = Modifier.height(QUEUE_HEIGHT))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, delays: Set<DelayKind>, open: (QuickScreen) -> Unit) {
    // The screens this opens share one compact card, so the settings tuned right here keep the room.
    Column(Modifier.fillMaxWidth().outlinedCard()) {
        if (state.queue.isNotEmpty()) {
            GroupRow(SpicyGlyphs.Queue, "Queue", "${state.queue.size} ${if (state.queue.size == 1) "song" else "songs"}") {
                open(QuickScreen.Queue)
            }
            GroupDivider()
        }
        if (state.localLyricsKey != null && state.lyrics !is LyricsState.Idle) {
            val chosen = state.sourceOverride?.let { id -> state.sourceDescriptors.firstOrNull { it.id == id }?.displayName ?: id }
            val showing = (state.lyrics as? LyricsState.Ready)?.provider
            GroupRow(
                SpicyGlyphs.Source,
                "Lyrics source",
                when {
                    chosen != null -> "$chosen, chosen for this song"
                    showing != null -> "Automatic, showing $showing"
                    else -> "Automatic"
                },
            ) { open(QuickScreen.Source) }
            GroupDivider()
        }
        GroupRow(SpicyGlyphs.LyricsManager, "Lyrics Manager", "Save or upload lyrics, link a folder") { open(QuickScreen.LyricsManager) }
    }
    LyricsLanguageRow(state, viewModel)
    // Only the delays picked in Scroll & Sync, so the pop-up stays short.
    if (DelayKind.Song in delays) SongDelayRow(state, viewModel)
    if (DelayKind.Player in delays) PlayerDelayRow(state, viewModel)
    if (DelayKind.Output in delays) SettingRow(
        label = "Output delay",
        description = "For every song on ${state.outputLabel}. Move it right if the lyrics run ahead of the song.",
        icon = Icons.Rounded.Timer,
        stacked = true,
    ) {
        DelayControl(state.lyricDelayMs, viewModel::setLyricDelay)
    }
}

/** The queue's height in the pop-up: room for about eight songs. */
private val QUEUE_HEIGHT = 440.dp
