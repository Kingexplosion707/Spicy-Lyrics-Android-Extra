package com.tx24.spicyplayer

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.LifecycleEventObserver
import android.content.pm.ActivityInfo
import com.tx24.spicyplayer.ui.settings.SettingsPage
import com.tx24.spicyplayer.ui.settings.StaticAutoScroll
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.lyrics.lyricsTint
import com.tx24.spicyplayer.ui.share.ShareLyricsModal
import com.tx24.spicyplayer.ui.sleep.SleepTimerModal
import com.tx24.spicyplayer.playback.SleepTimerService
import com.tx24.spicyplayer.lyrics.rememberLyricsTint
import com.tx24.spicyplayer.ui.spotifysearch.LyricsSearchModal
import com.tx24.spicyplayer.ui.spotifysearch.SpotifySearchModal
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Public
import com.tx24.spicyplayer.network.data.SourceDisclosure
import com.tx24.spicyplayer.network.data.SourceDisclosures
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tx24.spicyplayer.network.data.counted
import com.tx24.spicyplayer.haptics.BackgroundHapticsService
import com.tx24.spicyplayer.haptics.HapticBeatMode
import com.tx24.spicyplayer.haptics.HapticBeatOptions
import com.tx24.spicyplayer.haptics.HapticPlayer
import com.tx24.spicyplayer.haptics.playMusicHaptics
import com.tx24.spicyplayer.haptics.rememberHapticsStoppedBySaving
import com.tx24.spicyplayer.haptics.LyricHapticTimes
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.nowplaying.NextUpCard
import com.tx24.spicyplayer.playback.NextUp
import com.tx24.spicyplayer.playback.QueueEntry
import com.tx24.spicyplayer.ui.components.SpicyToastHost
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerModal
import com.tx24.spicyplayer.ui.components.SpicyModalActions
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicyModalGap
import com.tx24.spicyplayer.ui.components.SpicyModalHeading
import com.tx24.spicyplayer.ui.components.SpicyModalMessage
import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.tx24.spicyplayer.ui.components.LyricsSkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.ScrollConfig
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutMetrics
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsViewState
import com.tx24.spicyplayer.lyrics.spicy.canvas.ActiveLineDirection
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.ui.profile.ProfileModal
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedLyricsFooter
import com.tx24.spicyplayer.ui.components.CAPSULE_PAD
import com.tx24.spicyplayer.ui.components.CapsuleButton
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.GlassCapsule
import com.tx24.spicyplayer.ui.components.LocalGroupedControls
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerLimit
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.translation.romanizationOrigins
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import com.tx24.spicyplayer.translation.translateButtonHelps
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.network.data.providers.CustomLyricsSource
import com.tx24.spicyplayer.ui.background.SpicySessionBackground
import com.tx24.spicyplayer.ui.components.LocalBackdrop
import com.tx24.spicyplayer.ui.components.SpicyModalNotes
import com.tx24.spicyplayer.ui.components.SpicyVersionRow
import com.tx24.spicyplayer.ui.controls.KeyInput
import com.tx24.spicyplayer.ui.controls.ActionKind
import com.tx24.spicyplayer.ui.controls.CoverControls
import com.tx24.spicyplayer.ui.controls.PanelTimeline
import com.tx24.spicyplayer.ui.controls.StageControls
import com.tx24.spicyplayer.ui.settings.QuickScreen
import com.tx24.spicyplayer.ui.settings.QuickSettingsModal
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.controls.LyricsControls
import com.tx24.spicyplayer.ui.controls.controlsClearancePx
import com.tx24.spicyplayer.ui.controls.kind
import com.tx24.spicyplayer.ui.nowplaying.LandscapeMetrics
import com.tx24.spicyplayer.ui.settings.PanelSide
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import com.tx24.spicyplayer.ui.controls.PlaybackControlsState
import com.tx24.spicyplayer.ui.nowplaying.CompactHeaderMetrics
import com.tx24.spicyplayer.ui.nowplaying.CompactNowPlayingHeader
import com.tx24.spicyplayer.ui.nowplaying.LyricsNotice
import com.tx24.spicyplayer.ui.nowplaying.NoticeActions
import com.tx24.spicyplayer.ui.nowplaying.PipSongCard
import com.tx24.spicyplayer.ui.nowplaying.PipDock
import com.tx24.spicyplayer.ui.settings.PipWithoutLyrics
import com.tx24.spicyplayer.ui.settings.debugReport
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverQuery
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingInfo
import com.tx24.spicyplayer.ui.nowplaying.CoverStage
import com.tx24.spicyplayer.ui.nowplaying.CoverStageDock
import com.tx24.spicyplayer.ui.nowplaying.SlidingAlignment
import com.tx24.spicyplayer.ui.nowplaying.StageMetadata
import com.tx24.spicyplayer.ui.nowplaying.STAGE_FADE_MS
import com.tx24.spicyplayer.ui.nowplaying.forgetMotionCover
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCoverUrl
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCover
import com.tx24.spicyplayer.ui.nowplaying.ReleaseYear
import com.tx24.spicyplayer.ui.nowplaying.besideArtists
import com.tx24.spicyplayer.ui.settings.BackgroundType
import com.tx24.spicyplayer.ui.settings.ReleaseYearPosition
import com.tx24.spicyplayer.playback.TrackExtrasWanted
import com.tx24.spicyplayer.ui.nowplaying.CanvasLookup
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice
import com.tx24.spicyplayer.ui.nowplaying.StagePicture
import com.tx24.spicyplayer.ui.nowplaying.PortraitCanvas
import com.tx24.spicyplayer.ui.nowplaying.PortraitCoverStage
import com.tx24.spicyplayer.ui.nowplaying.MotionCovers
import com.tx24.spicyplayer.ui.controls.controlsContentStartPx
import com.tx24.spicyplayer.ui.nowplaying.PortraitCanvasDock
import com.tx24.spicyplayer.ui.background.CanvasBackdrop
import com.tx24.spicyplayer.ui.nowplaying.TrackDirection
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.RendererStyle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import com.tx24.spicyplayer.ui.settings.SettingsScreen
import com.tx24.spicyplayer.ui.intro.IntroScreen
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.update.UpdateStatus
import com.tx24.spicyplayer.update.UpdateUiState
import com.tx24.spicyplayer.update.UpdateViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.analytics.lyricsOutcome
import com.tx24.spicyplayer.ui.controls.ControlsRevealGuard
import com.tx24.spicyplayer.ui.controls.LocalControlsRevealGuard


/** The lookup settings for the daily settings snapshot: which sources are on and first, never a key. */
internal fun lookupSnapshot(state: PlayerUiState): Map<String, Any> = buildMap {
    // A custom source's ID and name are the user's own: only that it's custom is said.
    val ranking = state.sourceRanking
    val on = ranking.origins.map { it.id }.filter { LyricsSourceSettings.isOn(ranking, it) }
    on.firstOrNull()?.let { put("first_source", if (CustomLyricsSource.isCustom(it)) "custom" else it) }
    put("sources_off", ranking.origins.size - on.size)
    // The built-in sources that are on, by id, in a fixed order; custom ones are only counted.
    put("sources_on", on.filterNot(CustomLyricsSource::isCustom).sorted().joinToString(","))
    put("blends_on", ranking.liveBlends.size)
    put("human_romanizations", state.humanRomanizations)
    // Which translation is set up; never what was translated.
    state.translationPreferences.let { translation ->
        put("translate_automatically", translation.automatic)
        put("translation_provider", translation.provider.name)
        put("translation_target", translation.targetLanguage)
        put("human_translations", translation.humanTranslations)
        put("translation_excluded", translation.excludedLanguages.size)
    }
    put("own_key", state.ownKeyHint != null)
    put("custom_sources", state.customSources.size)
}

/** Counts, for the usage report, what each song's lookup ended with and which pop-ups get opened. */
@Composable
private fun CountUsage(state: PlayerUiState, vararg opened: Pair<Boolean, String>) {
    val outcome = when (val lyrics = state.lyrics) {
        // A custom source counts as "Custom", never by the name the user gave it.
        is LyricsState.Ready -> lyricsOutcome(lyrics.lyricsType) to
            if (state.customSources.any { it.name == lyrics.provider }) "Custom" else lyrics.provider
        is LyricsState.Error -> lyricsOutcome(null) to null
        else -> null
    }
    val song = state.title to state.artist
    var counted by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(song, outcome) {
        if (outcome != null && counted != song) {
            counted = song
            UsageStats.countSong(outcome.first, outcome.second, state.sourcePackage)
        }
    }
    opened.forEach { (shown, counter) ->
        LaunchedEffect(shown) { if (shown) UsageStats.count(counter) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsApp(
    openNotificationAccess: () -> Unit,
    viewModel: ExternalPlaybackViewModel,
    updater: UpdateViewModel,
    settings: AppSettings,
    inPip: Boolean,
    /** Shrinks the lyrics into the floating window; null where the phone has none. */
    onEnterPip: (() -> Unit)?,
    onPipAllowed: (Boolean) -> Unit,
    keyInput: KeyInput = remember { KeyInput() },
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // The battery saver can turn on low performance mode too (Settings → Device → Battery saver).
    val batterySaver = com.tx24.spicyplayer.power.rememberBatterySaverOn()
    val lowPerformance = settings.lowPerformance || (batterySaver && settings.saverLowPerformance)
    val hideHeader = settings.hideHeader
    val romanizationAvailable = (state.lyrics as? LyricsState.Ready)?.let { ready ->
        ready.lines.any { line -> line.words.any { it.romanized != null } }
    } == true
    val romanize = settings.romanize && romanizationAvailable
    // The lyrics font: ours, the phone's, or the file picked in settings (ours again if it's gone).
    // With its weight, line spacing and alignment, as the wallpaper and the overlay set them.
    com.tx24.spicyplayer.wallpaper.LyricsFontSetup(settings)
    // Shared with the lyrics: the scroll-to-active button and the pinned credits.
    val lyricsViewState = remember { LyricsViewState() }
    // New installs and cleared apps only: someone updating already knows their way around.
    val freshStart = remember {
        settings.startedEmpty || runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }
            .getOrNull()?.let { it.firstInstallTime == it.lastUpdateTime } == true
    }
    val showIntro = (freshStart || settings.introReplay) && !settings.introDone
    val answerUsageStats: (Boolean) -> Unit = { keep ->
        settings.usageStats = keep
        settings.usageStatsAsked = true
        UsageStats.onSettingChanged(context, keep)
        UsageStats.appOpened(context) { lookupSnapshot(viewModel.state.value) }
    }
    val pinnedFooter = settings.pinnedFooter
    // Awake while the music plays, so the lyrics can be followed without touching the phone.
    // The always-on display needs the same: its dim lyrics stay up while the music plays.
    val view = LocalView.current
    // As the always-on display it stays up whatever plays, paused or not. The battery saver can
    // let the screen sleep anyway (Settings → Device → Battery saver).
    val charging = com.tx24.spicyplayer.power.rememberCharging()
    val keepAwake = (settings.keepScreenOn && state.isPlaying && (charging || !settings.keepScreenOnOnlyCharging) &&
        !(batterySaver && settings.saverStopsKeepScreenOn)) ||
        (settings.ambientAsAod && !(batterySaver && settings.saverStopsAlwaysOn))
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
    // 60 Hz unless asked otherwise: word sweeps and the background look the same, and a 90/120 Hz
    // screen would otherwise draw (and composite) half again or twice as many frames.
    val activity = LocalActivity.current
    val highRefreshRate = settings.highRefreshRate && !(batterySaver && settings.saverStopsHighRefreshRate)
    DisposableEffect(activity, highRefreshRate) {
        val window = activity?.window
        window?.let { setPreferredRefreshRate(it, if (highRefreshRate) null else 60f) }
        onDispose { window?.let { setPreferredRefreshRate(it, null) } }
    }
    // The big cover: the button (or a tap on the cover) asks for it; the setting brings it up on
    // its own while a song has no lyrics, until it's put away for that song. Errors stay in view.
    var userExpanded by remember { mutableStateOf(false) }
    var dismissedForSong by remember(state.title, state.artist) { mutableStateOf(false) }
    val autoExpanded = settings.expandWithoutLyrics && (state.lyrics as? LyricsState.Error)?.kind == NoticeKind.NoLyrics &&
        !dismissedForSong
    val headerExpanded = userExpanded || autoExpanded
    val toggleExpanded = {
        if (headerExpanded) {
            userExpanded = false
            if (autoExpanded) dismissedForSong = true
        } else {
            userExpanded = true
        }
    }
    // The floating button's own way in, counted apart from a tap on the cover.
    val expandButtonTapped = {
        UsageStats.count(UsageCounter.EXPAND_BUTTON)
        toggleExpanded()
    }
    // The header expands over 0.4 s with CSS's `ease`.
    val expansion by animateFloatAsState(
        if (headerExpanded) 1f else 0f,
        tween(400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
        label = "headerExpansion",
    )
    // Landscape whenever the window is wider than tall: the song sits in a panel beside the lyrics.
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    // In landscape the controls live on the cover, unless there's no panel and no big cover:
    // then they're the bottom bar, as in portrait.
    val controlsOnCover = landscape && (!hideHeader || headerExpanded)
    val bottomBar = !landscape || hideHeader
    // The rotate button turns the screen to landscape even with rotation locked, and back to the
    // phone's own rotation. Leaving the app hands rotation back too.
    // Settings are open but peeked through: the player's own settings button steps aside.
    var settingsPeeking by remember { mutableStateOf(false) }
    var rotated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(rotated) {
        activity?.requestedOrientation =
            if (rotated) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val rotationLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(rotationLifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) rotated = false }
        rotationLifecycle.addObserver(observer)
        onDispose { rotationLifecycle.removeObserver(observer) }
    }
    // The panel slides to the other side over 0.4 s (`transition: left .4s`).
    val panelSide = animateFloatAsState(
        if (settings.panelSide == PanelSide.Right) 1f else 0f,
        tween(400, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
        label = "panelSide",
    )
    // The cover stage: beside the lyrics, the cover fills its side of the screen; with the big
    // cover up it stands in the middle in its place. Only over the moving background, which
    // already takes its colours from the cover.
    val stageAllowed = settings.stageArt.isNotEmpty() && landscape && (!hideHeader || headerExpanded) && !inPip &&
        settings.backgroundType == BackgroundType.Default && !lowPerformance
    // Portrait, with the big cover up: what takes its place ([AppSettings.bigCoverArt]).
    val portraitAllowed = !landscape && !inPip && settings.bigCoverArt.isNotEmpty() && !lowPerformance
    // The song's Spotify Canvas is looked up only while something may show it: the stage, or the
    // big cover in portrait, where it's picked; the Canvas background.
    val stageCanvas = stageAllowed && StageArtChoice.wantsCanvas(settings.stageArt, lowPerformance)
    val portraitCanvas = portraitAllowed && StageArtChoice.wantsCanvas(settings.bigCoverArt, lowPerformance)
    val canvasBackground = settings.canvasBackground && !lowPerformance
    // Motion lyrics take their colours from the cover.
    LaunchedEffect(state.artwork) { com.tx24.spicyplayer.ivlyrics.MotionAccent.update(state.artwork) }
    // Auto's theme follows the song's genre, looked up only while motion lyrics are on.
    LaunchedEffect(state.title, state.artist, settings.motionLyrics) {
        com.tx24.spicyplayer.ivlyrics.MotionGenre.update(state.title, state.artist, settings.motionLyrics)
    }
    val canvasWanted = stageCanvas || portraitCanvas || canvasBackground
    LaunchedEffect(canvasWanted) { viewModel.setCanvasWanted(canvasWanted) }
    // The song's canvas, held while the next song's is looked up, so it gives way to the next one
    // instead of vanishing with the song change.
    var heldCanvasUrl by remember { mutableStateOf<String?>(null) }
    if (!state.canvasPending || !canvasWanted) heldCanvasUrl = state.canvasUrl
    // The loops picked to pause with the music hold their frame while it's paused.
    LaunchedEffect(state.isPlaying, settings.pauseWithMusic) {
        MotionCovers.pausedKinds = settings.pauseWithMusic
        MotionCovers.musicPaused = !state.isPlaying
    }
    // The panel's own loop: the same stream, so no second decode.
    val stageMotion = rememberMotionCover(
        StageArtChoice.wantsAnimated(settings.stageArt, settings.animatedCover, lowPerformance),
        MotionCoverQuery(state.artist, state.album, state.title),
    )
    // The first picked picture the song has; with none, no stage for this song.
    val stagePicture = StageArtChoice.pick(
        arts = settings.stageArt,
        animatedCover = settings.animatedCover,
        lowPerformance = lowPerformance,
        canvas = state.canvasUrl?.let { CanvasLookup.Found(it) }
            ?: if (state.canvasPending) CanvasLookup.Pending else CanvasLookup.None,
        motionUrl = stageMotion.url.takeIf { stageMotion.settled },
    )
    // The big cover's own pick, in portrait. The canvas is the one held through a song change.
    val portraitMotion = rememberMotionCover(
        portraitAllowed && StageArtChoice.wantsAnimated(settings.bigCoverArt, settings.animatedCover, lowPerformance),
        MotionCoverQuery(state.artist, state.album, state.title),
        tall = true,
    )
    val portraitPicture = if (!portraitAllowed) null else StageArtChoice.pick(
        arts = settings.bigCoverArt,
        animatedCover = settings.animatedCover,
        lowPerformance = lowPerformance,
        canvas = heldCanvasUrl?.let { CanvasLookup.Found(it) }
            ?: if (state.canvasPending) CanvasLookup.Pending else CanvasLookup.None,
        motionUrl = portraitMotion.url.takeIf { portraitMotion.settled },
        animatedFirst = settings.bigCoverAnimatedFirst,
        motionPending = !portraitMotion.settled,
    )
    // Held while a new song's animated cover is looked up, so it doesn't drop out between songs.
    var shownPortrait by remember { mutableStateOf<StagePicture?>(null) }
    if (portraitMotion.settled || portraitPicture is StagePicture.Canvas || !portraitAllowed) shownPortrait = portraitPicture
    // While a new song's videos are still looked up, the stage holds what it shows (the last
    // song's loop keeps playing), then gives way to the answer, as Spotify's own player does.
    val stageSettled = !(stageCanvas && state.canvasPending) && stageMotion.settled
    var stageHasPicture by remember { mutableStateOf(false) }
    var shownStagePicture by remember { mutableStateOf<StagePicture>(StagePicture.Still) }
    if (stageSettled || !stageAllowed) {
        stageHasPicture = stagePicture != null
        // Fading out, the stage keeps what it last showed.
        if (stagePicture != null) shownStagePicture = stagePicture
    }
    val stageOn = stageAllowed && stageHasPicture
    // How far it has moved to the middle: with the big cover's expansion, or there throughout
    // without the panel, where it only shows while the big cover is up.
    val stageCentred = { if (hideHeader) 1f else expansion }
    val stageShown = animateFloatAsState(
        if (stageOn) 1f else 0f,
        tween(STAGE_FADE_MS, easing = StageEaseOut),
        label = "coverStage",
    )
    val stageVisible by remember { derivedStateOf { stageShown.value > 0f } }
    // Fully up, the panel's cover underneath stops playing its loop.
    val stageCovers by remember { derivedStateOf { stageShown.value == 1f } }
    // Swapping sides, the stage slides out toward the new side, flips while it's hidden, and slides
    // back in from the old one.
    var stageRight by remember { mutableStateOf(settings.panelSide == PanelSide.Right) }
    val stageSwapAlpha = remember { Animatable(1f) }
    val stageSwapX = remember { Animatable(0f) }
    val uiAnimations = LocalUiAnimations.current
    val swapDriftPx = configuration.screenWidthDp * LocalDensity.current.density * STAGE_SWAP_DRIFT
    LaunchedEffect(settings.panelSide, stageOn, uiAnimations) {
        val right = settings.panelSide == PanelSide.Right
        if (!stageOn || !uiAnimations) {
            stageRight = right
            stageSwapAlpha.snapTo(1f)
            stageSwapX.snapTo(0f)
            return@LaunchedEffect
        }
        val settle = tween<Float>(STAGE_SWAP_IN_MS, easing = StageEaseOut)
        // Pressed again before it flipped: it settles back where it was.
        if (right != stageRight) {
            val drift = swapDriftPx * (if (right) 1f else -1f)
            val out = tween<Float>(STAGE_SWAP_OUT_MS, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            coroutineScope {
                launch { stageSwapAlpha.animateTo(0f, out) }
                stageSwapX.animateTo(drift, out)
            }
            stageRight = right
            stageSwapX.snapTo(-drift)
        }
        coroutineScope {
            launch { stageSwapAlpha.animateTo(1f, settle) }
            stageSwapX.animateTo(0f, settle)
        }
    }
    // Where the cover is while the controls sit on it: only a touch there brings them up.
    val coverTouchArea = remember { CoverTouchArea() }
    // Elsewhere, where a touch leaves the controls alone: the cover's gestures, credit links.
    val revealGuard = remember { ControlsRevealGuard() }
    // Built fresh, not copied: the mode-dependent defaults are worked out in the constructor.
    val bloom = settings.rendererStyle == RendererStyle.Bloom
    val renderConfig = RenderConfig(
        bloom = bloom,
        minimalLyricsMode = settings.minimalLyricsMode,
        wordMotionBoost = settings.wordMotionBoost,
        wideDuetPadding = settings.duetLinePadding,
        creditsFollowLastLine = settings.creditsFollowLastLine,
        refinedCredits = settings.refinedCredits,
        backgroundPop = settings.backgroundPop,
        keepOverlappingLines = settings.keepOverlappingLines,
        romanizationHighlight = settings.romanizationHighlight,
        animatedUnderRows = settings.animatedUnderRows,
        syllableMerge = settings.syllableMerge,
        syllablePace = settings.syllablePace,
        distanceBlurEnabled = settings.distanceBlur && !lowPerformance,
        glowEnabled = settings.glow && !lowPerformance,
        glowStrength = settings.glowStrength / 100f,
        otherLinesOpacity = settings.otherLinesBrightness / 100f,
        scroll = ScrollConfig(
            leadMs = if (settings.scrollLeadEnabled) settings.scrollLeadMs.coerceAtLeast(0).toLong() else 0L,
            smooth = settings.smoothScrolling,
            seekFadeCompensation = settings.seekFadeCompensation,
            staticSongMs = if (settings.staticAutoScroll != StaticAutoScroll.Off) state.durationMs else 0L,
            staticPace = viewModel::staticPace,
            bloomEarly = inPip,
        ),
    )
    var showSettings by remember { mutableStateOf(false) }
    // The settings pages left open, so coming back from the floating window lands on the same one.
    var settingsPages by remember { mutableStateOf(emptyList<SettingsPage>()) }
    var showLyricsManager by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var quickStart by remember { mutableStateOf(QuickScreen.Main) }
    var showSpotifySearch by remember { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showShareLyrics by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    // Over the lock screen the lyrics can be read and played without unlocking. Anything opened
    // over them asks to unlock first and opens once unlocked; whatever was open when the phone
    // locked is closed before the lock screen shows it. The always-on display shows over the
    // lock screen too, so its dim lyrics are the display the power button brings back.
    val showOverLock = settings.showOverLockScreen || settings.ambientAsAod
    LaunchedEffect(activity, showOverLock) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) activity?.setShowWhenLocked(showOverLock)
    }
    val whenUnlocked: (() -> Unit) -> Unit = remember(activity) {
        { open ->
            val keyguard = activity?.getSystemService(KeyguardManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyguard?.isKeyguardLocked == true) {
                keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = open()
                })
            } else open()
        }
    }
    val lockLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lockLifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START &&
                activity?.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            ) {
                showSettings = false
                showLyricsManager = false
                showQuickSettings = false
                showSpotifySearch = false
                showLyricsSearch = false
                showShareLyrics = false
                showSleepTimer = false
            }
        }
        lockLifecycle.addObserver(observer)
        onDispose { lockLifecycle.removeObserver(observer) }
    }
    val noticeActions = remember(viewModel, context, state.liveCaptions) {
        NoticeActions(
            onRetry = { viewModel.loadLyrics(force = true) },
            report = { debugReport(context, viewModel.state.value) },
            onSearchSpotify = { whenUnlocked { showSpotifySearch = true } },
            onLiveCaptions = { viewModel.setLiveCaptions(true) }.takeIf { !state.liveCaptions },
            onSearchLyrics = { whenUnlocked { showLyricsSearch = true } },
        )
    }
    // Leaving the app only shrinks the lyrics when nothing else is half done in front of them.
    // Settings and the other panels don't count: the settings wait on their page for its return,
    // and the others close as it shrinks, like on the lock screen.
    val pipAllowed = settings.autoPip && state.accessGranted && !showIntro &&
        state.limitNotice == null && lyricsViewState.profileRequest == null
    LaunchedEffect(pipAllowed) { onPipAllowed(pipAllowed) }
    LaunchedEffect(inPip) {
        if (inPip) {
            showLyricsManager = false
            showQuickSettings = false
            showSpotifySearch = false
            showLyricsSearch = false
            showShareLyrics = false
            showSleepTimer = false
        }
    }
    CountUsage(
        state,
        showSettings to UsageCounter.SETTINGS,
        showLyricsManager to UsageCounter.LYRICS_MANAGER,
        showQuickSettings to UsageCounter.QUICK_SETTINGS,
        showSpotifySearch to UsageCounter.SPOTIFY_SEARCH,
    )
    // Android before 12 can't blur, so nothing blurs the page there: the glass and pop-ups fall
    // back to their solid fills instead of showing the page through.
    val backdrop = remember { HazeState().takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S } }

    // Any touch shows the controls, and they fade after a few seconds without one.
    var controlsVisible by remember { mutableStateOf(true) }
    // Touch times go to the idle timer only: nothing in composition reads them, so a drag
    // (a touch event every frame) doesn't recompose the whole screen.
    val touches = remember { MutableStateFlow(0L) }
    // They stay while paused, and while the portrait big cover is up (it has nothing else to
    // show). On the landscape cover they always go, like the desktop page's once the mouse
    // rests: they'd keep the cover darkened.
    val hold = (!state.isPlaying && !controlsOnCover && !settings.hideControlsWhilePaused) ||
        // Over a Canvas there's something to see, so there they go as usual.
        (headerExpanded && !landscape && shownPortrait !is StagePicture.Canvas) || !settings.autoHideControls
    val idleMs = settings.controlsHideDelay.millis
    // A key (remote, keyboard, gamepad) counts as a touch; a screen over the lyrics counts as shown.
    SideEffect { keyInput.controlsShown = { controlsVisible || showSettings || showQuickSettings || showSpotifySearch || showLyricsSearch } }
    // While something is open over the lyrics screen, a remote's arrows stay in it: the screen
    // underneath takes no focus, and gives up what it had as the overlay opens.
    val overSettings = showSpotifySearch || showLyricsManager || showLyricsSearch
    val overlayOpen = showSettings || showQuickSettings || overSettings || showShareLyrics || showSleepTimer ||
        lyricsViewState.profileRequest != null || state.limitNotice != null
    // Whichever is on top: one opened from settings takes the focus from them too.
    val topOverlay = listOf(showSpotifySearch || showLyricsSearch, showLyricsManager, showQuickSettings, showSettings, overlayOpen).indexOf(true)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(topOverlay) { if (topOverlay >= 0) focusManager.clearFocus(force = true) }
    LaunchedEffect(keyInput) {
        keyInput.presses.collect { at ->
            if (at == 0L) return@collect
            controlsVisible = true
            touches.value = at
        }
    }
    LaunchedEffect(hold, idleMs) {
        if (hold) {
            controlsVisible = true
            return@LaunchedEffect
        }
        touches.collectLatest {
            delay(idleMs)
            controlsVisible = false
        }
    }
    // Ambient mode: left untouched while the music plays, the page goes dark and dim with only the
    // lyrics on it. Any touch or key brings it back (and does nothing else). Over the big cover in
    // portrait too: the cover fades with the header, leaving only the docked song, and comes back as it was.
    var ambient by remember { mutableStateOf(false) }
    // The always-on display dims on its own, with or without ambient mode and whether or not
    // the music plays; ambient mode alone dims only while it plays.
    val ambientAllowed = (settings.ambientAsAod || (settings.ambientMode && state.isPlaying)) &&
        !inPip && !overlayOpen && !(headerExpanded && landscape)
    LaunchedEffect(ambientAllowed, settings.ambientDelay) {
        if (!ambientAllowed) {
            ambient = false
            return@LaunchedEffect
        }
        touches.collectLatest {
            ambient = false
            delay(settings.ambientDelay.millis)
            ambient = true
        }
    }
    val ambientFade = animateFloatAsState(if (ambient) 1f else 0f, tween(if (ambient) AMBIENT_IN_MS else AMBIENT_OUT_MS), label = "ambient")
    val ambientDrawn by remember { derivedStateOf { ambientFade.value > 0f } }
    // Fully dark, nothing under the black shows: the loops beneath hold their frames.
    val ambientOpaque by remember { derivedStateOf { ambientFade.value >= 1f } }
    // Never brighter than the phone already is. As the always-on display, lower still: the
    // lowest brightness, so the dim lyrics stay up without using much battery.
    val aodOn = settings.ambientAsAod
    DisposableEffect(activity, ambient, aodOn) {
        val window = activity?.window
        if (ambient && window != null) {
            val system = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            }.getOrDefault(1f)
            val brightness = if (aodOn) AOD_BRIGHTNESS else minOf(system, AMBIENT_BRIGHTNESS)
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        onDispose {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    val backgroundType = settings.backgroundType
    // Only what's on screen is looked up: the moving Kawarp background's beats, the artist's
    // header for the header backgrounds, the year where it shows.
    val beatReactive = backgroundType == BackgroundType.Default && settings.beatReactiveBackground &&
        !settings.staticBackground && !lowPerformance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val hapticPlayer = remember { HapticPlayer(context) }
    // Power saving can stop them too (Settings → Device → Live wallpaper → Power saving).
    val savingStopsHaptics = rememberHapticsStoppedBySaving(settings)
    val musicHapticsOn = settings.musicHaptics && hapticPlayer.canPlayMusic && !savingStopsHaptics
    val extrasWanted = TrackExtrasWanted(
        releaseYear = settings.releaseYearPosition != ReleaseYearPosition.Off,
        artistHeader = backgroundType.usesArtistHeader,
        beats = beatReactive || musicHapticsOn,
        staticPace = settings.staticAutoScroll == StaticAutoScroll.Basic &&
            (state.lyrics as? LyricsState.Ready)?.lyricsType == LyricsType.Static,
    )
    LaunchedEffect(extrasWanted) { viewModel.setTrackExtrasWanted(extrasWanted) }
    // Vibrates with the beats while the song plays and the app is in front with the screen on.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // They rest while the delay is tapped out: tapping to them would only find their own timing.
    // HapticBeat's choices shape the score; changing one restarts the loop with the new feel.
    val beatOptions = HapticBeatOptions(
        mode = settings.hapticBeatMode,
        timing = settings.hapticBeatTiming,
        detection = settings.hapticBeatDetection,
        sensitivity = settings.hapticBeatSensitivity,
        vocalsOnly = settings.hapticBeatVocalsOnly,
    )
    LaunchedEffect(
        musicHapticsOn, state.isPlaying, state.calibratingDelay,
        settings.hapticBeatMode, settings.hapticBeatEngine, settings.hapticBeatTiming,
        settings.hapticBeatDetection, settings.hapticBeatSensitivity, settings.hapticBeatVocalsOnly,
    ) {
        if (!musicHapticsOn || !state.isPlaying || state.calibratingDelay) return@LaunchedEffect
        hapticPlayer.renderEngine = settings.hapticBeatEngine
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            playMusicHaptics(
                hapticPlayer,
                score = {
                    // Sung words and lead lines only, whole-timed lines laid out word by word;
                    // built once per lyrics object, not before every pulse.
                    val times = LyricHapticTimes.of(state.lyrics as? LyricsState.Ready)
                    viewModel.musicHaptics(settings.musicHapticsStyle, beatOptions, times.wordTimes, times.lineTimes, times.wordsEstimated)
                },
                positionMs = { viewModel.currentLyricPositionMs() + settings.hapticBeatRemoteOffset },
                strength = { settings.musicHapticsStrength / 100f },
                continuous = beatOptions.mode == HapticBeatMode.Continuous,
            )
        }
    }
    // Off the lyrics screen, the background haptics service takes over the same vibrations; it
    // rests while this screen is up, and stops when either haptics switch is off.
    LaunchedEffect(settings.musicHaptics, settings.backgroundMusicHaptics) {
        BackgroundHapticsService.sync(context)
        com.tx24.spicyplayer.playback.LyricsNotificationService.sync(context)
        com.tx24.spicyplayer.overlay.LyricsOverlayService.sync(context)
        com.tx24.spicyplayer.aod.AlwaysOnService.sync(context)
        com.tx24.spicyplayer.flipper.FlipperService.sync(context)
    }
    LaunchedEffect(Unit) { updater.checkOnLaunch(settings.includePrereleases) }
    val update by updater.state.collectAsState()

    // The bars are hidden, so this is only the camera cutout (and the keyboard in settings).
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        // The floating window has room for the lyrics and nothing else.
        if (inPip) {
            PipLyrics(state, viewModel, settings, renderConfig, lyricsViewState, romanize, lowPerformance, heldCanvasUrl)
            return@Scaffold
        }
        CompositionLocalProvider(LocalControlsRevealGuard provides revealGuard) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { revealGuard.screen = it }
                .pointerInput(Unit) {
                    // Sees every touch on its way down without taking it from what's underneath.
                    awaitPointerEventScope {
                        // What the touch under way holds back, decided where its first finger lands.
                        var hold = ControlsRevealGuard.Hold.None
                        var downAt = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val onCover = coverTouchArea.contains
                            if (event.type == PointerEventType.Press && event.changes.count { it.pressed } == 1) {
                                downAt = event.changes.first { it.pressed }.position
                                // The cover that carries the controls brings them up; nothing else holds.
                                hold = if (onCover != null) ControlsRevealGuard.Hold.None else revealGuard.holdAt(downAt)
                            }
                            if (hold == ControlsRevealGuard.Hold.Taps &&
                                event.changes.any { (it.position - downAt).getDistance() > viewConfiguration.touchSlop }
                            ) {
                                hold = ControlsRevealGuard.Hold.None
                            }
                            val held = hold != ControlsRevealGuard.Hold.None
                            if (event.changes.none { it.pressed }) hold = ControlsRevealGuard.Hold.None
                            if (held) continue
                            if (onCover != null && event.changes.none { onCover(it.position) }) continue
                            controlsVisible = true
                            touches.value = SystemClock.uptimeMillis()
                        }
                    }
                },
        ) {
            // The background fills the screen; everything on it keeps clear of the cutout.
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .focusProperties { onEnter = { if (overlayOpen) cancelFocusChange() } }
                    .focusGroup(),
            ) {
                // The header rises into the cutout's band (the camera is clear of the cover), so the
                // page starts at the top of the screen and only the bottom inset comes off.
                val topInsetPx = with(LocalDensity.current) { padding.calculateTopPadding().toPx() }
                val bottomInsetPx = with(LocalDensity.current) { padding.calculateBottomPadding().toPx() }
                val pageWidth = constraints.maxWidth.toFloat()
                val pageHeight = constraints.maxHeight.toFloat() - bottomInsetPx
                val density = LocalDensity.current.density
                val headerMetrics = remember(pageWidth, pageHeight, density, topInsetPx, settings.headerSize) {
                    CompactHeaderMetrics(
                        pageWidthPx = pageWidth,
                        pageHeightPx = pageHeight,
                        density = density,
                        lyricFontSizeSp = LyricsLayoutMetrics(pageWidth, density, LyricsType.Syllable, 1f).baseFontSizeSp,
                        topInsetPx = topInsetPx,
                        size = settings.headerSize,
                    )
                }
                val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
                val belowTop = androidx.compose.foundation.layout.PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding(),
                )
                val startInsetPx = with(LocalDensity.current) { belowTop.calculateStartPadding(layoutDirection).toPx() }
                val endInsetPx = with(LocalDensity.current) { belowTop.calculateEndPadding(layoutDirection).toPx() }
                // Landscape: the page between the cutout's insets, which is where the header and
                // lyrics are placed (`belowTop`).
                val timelineOutside = settings.timelineOutsideCover
                val landscapeMetrics = remember(pageWidth, pageHeight, density, topInsetPx, startInsetPx, endInsetPx, timelineOutside) {
                    LandscapeMetrics(
                        pageWidthPx = pageWidth - startInsetPx - endInsetPx,
                        pageHeightPx = pageHeight - topInsetPx,
                        density = density,
                        topPx = topInsetPx,
                        timelineOutside = timelineOutside,
                    )
                }
                // The lyrics' box beside the panel (or across the page without one). Mid-swap they
                // fade out and come back on the other side, rather than crossing the panel.
                val lyricsBoxLeft = landscapeMetrics.lyricsBox(withPanel = !hideHeader, panelOnRight = false)
                val lyricsBoxRight = landscapeMetrics.lyricsBox(withPanel = !hideHeader, panelOnRight = true)
                val lyricsBoxWidth = with(LocalDensity.current) { lyricsBoxLeft.widthPx.toDp() }
                fun Modifier.inLyricsBox(): Modifier = if (!landscape) this else this
                    .offset { IntOffset((if (panelSide.value < 0.5f) lyricsBoxLeft else lyricsBoxRight).leftPx.roundToInt(), 0) }
                    .width(lyricsBoxWidth)
                val swapFade = { if (landscape && !hideHeader) abs(1f - 2f * panelSide.value) else 1f }
                // Landscape lyrics keep the desktop page's size against the cover rather than
                // following their box's width.
                val lyricsFontScale = settings.lyricsScale(landscape) * if (landscape) {
                    landscapeMetrics.lyricFontSizeSp /
                        LyricsLayoutMetrics(lyricsBoxLeft.widthPx, density, LyricsType.Syllable, 1f).baseFontSizeSp
                } else 1f
                coverTouchArea.contains = if (!controlsOnCover) null else if (stageOn && headerExpanded) { position ->
                    // The centred stage's solid part, around the big cover's place.
                    val left = startInsetPx + landscapeMetrics.centred.artLeftPx
                    position.x in left..(left + landscapeMetrics.coverSizePx)
                } else if (stageOn) { position ->
                    // The stage's solid part, from the screen's edge.
                    val solid = (if (stageRight) endInsetPx else startInsetPx) + landscapeMetrics.stageSolidPx
                    if (stageRight) position.x >= pageWidth - solid else position.x <= solid
                } else { position ->
                    val size = landscapeMetrics.coverSizePx
                    val t = if (hideHeader) 1f else expansion
                    val left = startInsetPx + landscapeMetrics.coverLeftPx(panelSide.value) +
                        (landscapeMetrics.centred.artLeftPx - landscapeMetrics.coverLeftPx(panelSide.value)) * t
                    val top = landscapeMetrics.coverTopPx
                    position.x in left..(left + size) && position.y in top..(top + size)
                }
                var controlsHeightPx by remember { mutableIntStateOf(0) }
                // Pinned credits sit just above the screen's edge, under the controls, and fade out
                // while the bottom controls show (they'd move under a finger otherwise). The lyrics
                // fade out above them.
                var pinnedHeightPx by remember { mutableIntStateOf(0) }
                // Up Next: the song queued after this one, for the last stretch of this one. It
                // sits where the pinned credits do, and the lyrics fade out above it.
                var nextUpHeightPx by remember { mutableIntStateOf(0) }
                val queuedNext = remember(state.queue) { NextUp.nextEntry(state.queue) }
                var heldNext by remember { mutableStateOf<QueueEntry?>(null) }
                LaunchedEffect(queuedNext) { if (queuedNext != null) heldNext = queuedNext }
                var nextUpDue by remember { mutableStateOf(false) }
                LaunchedEffect(settings.nextUpCard, queuedNext != null, state.durationMs, state.isPlaying, state.title) {
                    if (!settings.nextUpCard || queuedNext == null) {
                        nextUpDue = false
                        return@LaunchedEffect
                    }
                    while (true) {
                        nextUpDue = NextUp.due(viewModel.currentPositionMs(), state.durationMs)
                        delay(NEXT_UP_TICK_MS)
                    }
                }
                val nextUpActive by animateFloatAsState(
                    if (nextUpDue && queuedNext != null && !overlayOpen && !ambient && !inPip && expansion == 0f) 1f else 0f,
                    tween(280),
                    label = "nextUp",
                )
                val nextUpClearPx = with(LocalDensity.current) { 24.dp.toPx() }
                val pinnedGapPx = with(LocalDensity.current) { PINNED_FOOTER_GAP.toPx() }
                val pinnedClearPx = with(LocalDensity.current) { 8.dp.toPx() }
                // While the controls show, the scroll-to-active button keeps clear of them.
                val controlsBottomPx by animateFloatAsState(
                    if (controlsVisible && bottomBar) controlsHeightPx.toFloat() else 0f,
                    tween(SpicyMotion.CONTROLS_FADE_MS),
                    label = "controlsBottom",
                )
                // A notice centres in the space they leave above them and the blurring part of
                // their shade, so its buttons stay sharp. It sits in what the shade blurs.
                val controlsClearPx = remember(controlsHeightPx, landscape, density) {
                    controlsClearancePx(controlsHeightPx, wide = landscape, density = density)
                }
                val noticeBottomPx by animateFloatAsState(
                    if (controlsVisible && bottomBar) controlsClearPx else 0f,
                    tween(SpicyMotion.CONTROLS_FADE_MS),
                    label = "noticeBottom",
                )
                // Out of the way under settings too: their glass would keep blurring behind it.
                val controlsTarget = controlsVisible && (!showSettings || settingsPeeking)
                // Quick to appear under the finger; a slow, soft fade when they time out.
                val controlsShown by animateFloatAsState(
                    if (controlsTarget) 1f else 0f,
                    if (controlsTarget) {
                        tween(CONTROLS_SHOW_MS, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    } else {
                        tween(CONTROLS_HIDE_MS, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                    },
                    label = "controlsShown",
                )
                // Once faded out, the controls stop blurring: the shade's progressive blur and each
                // glass button would otherwise re-blur the whole page every frame, unseen.
                val controlsGone by remember { derivedStateOf { controlsShown == 0f } }
                val maContext = LocalContext.current
                val playbackControls = PlaybackControlsState(
                    credit = lyricsViewState.shownFooter?.miniCredit()?.takeIf { pinnedFooter != PinnedFooterMode.Off },
                    onShareLyrics = { whenUnlocked { showShareLyrics = true } }.takeIf { settings.shareButton && state.lyrics is LyricsState.Ready },
                    onSleepTimer = { showSleepTimer = true }.takeIf { settings.sleepTimerButton && state.sourcePackage != null },
                    sleepTimerOn = SleepTimerService.state.value != null,
                    onMusicAssistant = { com.tx24.spicyplayer.musicassistant.SpicyMusicAssistant.open(maContext) }
                        .takeIf { settings.musicAssistantButton && com.tx24.spicyplayer.musicassistant.SpicyMusicAssistant.supported() },
                    hideSettings = settingsPeeking,
                    // Hidden on a screen that's landscape on its own: there's nothing to turn.
                    onRotate = { rotated = !rotated }.takeIf { settings.rotateButton && (rotated || !landscape) },
                    rotated = rotated,
                    isPlaying = state.isPlaying,
                    canSeek = state.canSeek,
                    durationMs = state.durationMs,
                    positionMs = viewModel::currentPositionMs,
                    onPlayPause = viewModel::playPause,
                    onPrevious = viewModel::skipPrevious,
                    onNext = viewModel::skipNext,
                    onSeek = viewModel::seekTo,
                    // Shuffle and repeat stay in the playback row; only the player's other actions are floating buttons.
                    customActions = if (settings.playerButtons) state.customActions else state.customActions.filter { it.kind != ActionKind.Other },
                    onCustomAction = { action ->
                        UsageStats.count(UsageCounter.PLAYER_ACTION)
                        viewModel.sendCustomAction(action)
                    },
                    onResync = viewModel::resync,
                    // Only where a translation would help: not on songs already in the target language
                    // or one left untranslated, going by what the phone guesses before anything is sent.
                    onTranslate = viewModel::toggleTranslation.takeIf {
                        state.lyrics is LyricsState.Ready && settings.translateButton && translateButtonHelps(
                            state.translationEnabled,
                            state.lyricsLanguageOverride ?: state.lyricsLanguage ?: state.guessedLanguage,
                            state.translationPreferences,
                        )
                    },
                    translated = state.translationEnabled && !state.translationFailed,
                    translating = state.translating,
                )
                val onEnterPipButton = onEnterPip.takeIf { settings.pipButton }
                val onOpenLyricsManager = if (settings.lyricsManagerButton) ({ whenUnlocked { showLyricsManager = true } }) else null
                val onOpenQuickSettings = if (settings.quickSettingsButton) ({ whenUnlocked { quickStart = QuickScreen.Main; showQuickSettings = true } }) else null
                // The queue's own button opens quick settings on its queue screen.
                val onOpenQueue = if (settings.queueButton && state.queue.isNotEmpty()) ({ whenUnlocked { quickStart = QuickScreen.Queue; showQuickSettings = true } }) else null
                // So does the source's, on its list of sources, while there's a song to choose for.
                val onOpenSource = if (settings.sourceButton && state.localLyricsKey != null) ({ whenUnlocked { quickStart = QuickScreen.Source; showQuickSettings = true } }) else null
                val romanizeButton = romanizationAvailable && settings.romanizeButton
                // Expanded, it's the only way back to the lyrics, so it stays.
                val expandButton = settings.expandButton || headerExpanded
                // The Cover Art background's animated cover; a stream that fails is forgotten, like the header's.
                val backgroundMotionQuery = MotionCoverQuery(state.artist, state.album, state.title)
                val backgroundMotionUrl = rememberMotionCoverUrl(
                    backgroundType == BackgroundType.CoverArt && settings.animatedBackground && !lowPerformance,
                    backgroundMotionQuery,
                )
                var failedBackgroundMotionUrl by remember { mutableStateOf<String?>(null) }
                // The animated cover the moving background follows: the header's own stream, so
                // only while that setting is on. Under the big cover it's that stream instead, as
                // they share one player.
                val followMotionUrl = rememberMotionCoverUrl(
                    backgroundType == BackgroundType.Default && settings.backgroundFollowsMotion && settings.animatedCover &&
                        !lowPerformance && !settings.staticBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    backgroundMotionQuery,
                )?.let { (shownPortrait as? StagePicture.Animated)?.url ?: it }
                // While the stage shows a canvas, or the Canvas background has one, the page's
                // backdrop is that canvas, blurred; the moving background fades out under it and
                // holds still once it's hidden.
                val stageAmbientUrl = (shownStagePicture as? StagePicture.Canvas)?.url?.takeIf { stageOn && settings.canvasBackdrop }
                    ?: (shownPortrait as? StagePicture.Canvas)?.url?.takeIf { headerExpanded && settings.canvasBackdrop }
                // While the stage or the big cover shows the record's own cover, it melts into the
                // background's colours, which are the cover's: the Canvas background steps aside.
                val coverOnStage = (stageOn && shownStagePicture !is StagePicture.Canvas) ||
                    (headerExpanded && shownPortrait != null && shownPortrait !is StagePicture.Canvas)
                val backgroundAmbientUrl = heldCanvasUrl?.takeIf { canvasBackground && !coverOnStage }
                val ambientShown by animateFloatAsState(
                    if (stageAmbientUrl != null || backgroundAmbientUrl != null) 1f else 0f,
                    tween(STAGE_FADE_MS, easing = StageEaseOut),
                    label = "canvasAmbient",
                )
                // Fading out, it keeps what it showed.
                var ambientUrl by remember { mutableStateOf<String?>(null) }
                (stageAmbientUrl ?: backgroundAmbientUrl)?.let { ambientUrl = it }
                // Portrait, with the big cover up: the song's canvas fills the screen in its place,
                // or its cover spreads edge to edge.
                val immersiveShown by animateFloatAsState(
                    if (shownPortrait != null) 1f else 0f,
                    tween(STAGE_FADE_MS, easing = StageEaseOut),
                    label = "portraitCanvas",
                )
                // Fading out, it keeps what it showed.
                var immersivePicture by remember { mutableStateOf<StagePicture?>(null) }
                shownPortrait?.let { immersivePicture = it }
                val immersive = { immersiveShown * expansion }
                // Fully up, the portrait picture hides the header's cover.
                val immersiveOpaque by remember { derivedStateOf { immersiveShown * expansion >= 1f } }
                var failedFollowMotionUrl by remember { mutableStateOf<String?>(null) }
                val motionScope = rememberCoroutineScope()
                val stageInfo = NowPlayingInfo(state.title, state.artist, state.album, state.artwork, state.artworkUri, state.trackDirection, hasSession = state.sourcePackage != null)
                val releaseYear = ReleaseYear(state.releaseYear, state.releaseYearPending, settings.releaseYearPosition)
                // Everything the glass controls blur.
                Box(Modifier.fillMaxSize().then(backdrop?.let { Modifier.hazeSource(it) } ?: Modifier)) {
                    SpicySessionBackground(
                        artwork = state.artwork,
                        artworkUri = state.artworkUri,
                        isPlaying = state.isPlaying,
                        hasSession = state.sourcePackage != null,
                        modifier = Modifier.fillMaxSize(),
                        type = backgroundType,
                        animate = !lowPerformance && !settings.staticBackground && ambientShown < 1f && !ambient,
                        blurDp = settings.backgroundBlurFor(backgroundType),
                        artistHeaderUrl = state.artistHeaderUrl,
                        artistHeaderPending = state.artistHeaderPending,
                        motion = if (beatReactive) viewModel.backgroundMotion else null,
                        refined = settings.refinedBackground,
                        followMotionUrl = followMotionUrl?.takeIf { it != failedFollowMotionUrl },
                        onFollowMotionFailed = {
                            failedFollowMotionUrl = followMotionUrl
                            motionScope.launch { forgetMotionCover(context, backgroundMotionQuery) }
                        },
                        motionCoverUrl = backgroundMotionUrl?.takeIf { it != failedBackgroundMotionUrl },
                        onMotionCoverFailed = {
                            failedBackgroundMotionUrl = backgroundMotionUrl
                            motionScope.launch { forgetMotionCover(context, backgroundMotionQuery) }
                        },
                        motionCoverHeld = ambientOpaque,
                        customColor = Color(settings.backgroundColorArgb),
                        photo = settings.backgroundPhoto,
                    )
                    ambientUrl?.takeIf { ambientShown > 0f }?.let { url ->
                        CanvasBackdrop(
                            url = url,
                            // Behind the stage or the big cover it keeps the Canvas background's
                            // blur: switching kinds there would show what's beneath while the other
                            // draws its first frame. Without that background it's the soft glow.
                            blur = if (settings.canvasBackground) settings.canvasBlur else 100,
                            onFailed = { viewModel.canvasFailed(url) },
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ambientShown },
                            held = ambientOpaque,
                        )
                    }
                    // ivLyrics' music video, over the picked background, where the song has one.
                    if (settings.musicVideoBackground && !lowPerformance && kr.ivlis.ivlyricsandroid.SpicyBridge.supported()) {
                        var videoFound by remember { mutableStateOf(false) }
                        val videoShown by animateFloatAsState(if (videoFound) 1f else 0f, tween(STAGE_FADE_MS, easing = StageEaseOut), label = "musicVideo")
                        val videoLines = remember(state.lyrics) {
                            (state.lyrics as? LyricsState.Ready)?.let { com.tx24.spicyplayer.ivlyrics.rendererLines(it) }.orEmpty()
                        }
                        com.tx24.spicyplayer.ivlyrics.MusicVideoBackground(
                            title = state.title,
                            artist = state.artist,
                            album = state.album,
                            spotifyId = state.manualSpotifyId ?: state.detectedSpotifyId,
                            durationMs = state.durationMs,
                            lines = videoLines,
                            isPlaying = state.isPlaying,
                            currentTimeMs = viewModel::currentLyricPositionMs,
                            onFound = { videoFound = it },
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = videoShown },
                        )
                    }
                    // Background dim: a veil over whatever the background shows.
                    if (settings.backgroundDim > 0) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.backgroundDim / 100f)))
                    }
                    if (landscape && stageVisible) {
                        CoverStage(
                            info = stageInfo,
                            metrics = landscapeMetrics,
                            onRight = stageRight,
                            startInsetPx = startInsetPx,
                            endInsetPx = endInsetPx,
                            picture = shownStagePicture,
                            onCanvasFailed = viewModel::canvasFailed,
                            shown = { stageShown.value },
                            controlsShown = { controlsShown },
                            swapAlpha = { stageSwapAlpha.value },
                            swapX = { stageSwapX.value },
                            centred = stageCentred,
                        )
                    }
                    if (!landscape) immersivePicture?.takeIf { immersive() > 0f }?.let { picture ->
                        if (picture is StagePicture.Canvas) {
                            PortraitCanvas(
                                url = picture.url,
                                onFailed = viewModel::canvasFailed,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = immersive() },
                                held = ambientOpaque,
                            )
                        } else {
                            PortraitCoverStage(
                                info = stageInfo,
                                picture = picture,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = immersive() },
                                held = ambientOpaque,
                            )
                        }
                    }
                    if (ambientDrawn) Box(Modifier.fillMaxSize().graphicsLayer { alpha = ambientFade.value }.background(Color.Black))
                    // Without the header (or in ambient mode, where it has faded), the lyrics start
                    // just under the camera instead; in landscape they run the page's full height.
                    val lyricsFullPage = hideHeader || ambient
                    val lyricsTopPx = when {
                        landscape -> topInsetPx
                        lyricsFullPage -> headerMetrics.barTopPx
                        else -> headerMetrics.lyricsTopPx
                    }
                    // The box moves at once; drawn where it was, it glides to its new top.
                    val shownLyricsTopPx = animateFloatAsState(
                        lyricsTopPx,
                        tween(if (ambient) AMBIENT_IN_MS else AMBIENT_OUT_MS),
                        label = "lyricsTop",
                    )
                    Column(
                        Modifier
                            .padding(belowTop)
                            .inLyricsBox()
                            .fillMaxSize()
                            // Expanding, the lyrics fade out under the cover. In ambient mode a notice in
                            // their place (no lyrics, an error) fades too, leaving the docked song alone.
                            .graphicsLayer {
                                val notice = if (state.lyrics is LyricsState.Ready) 1f else 1f - ambientFade.value
                                alpha = (1f - expansion) * notice * swapFade()
                                translationY = shownLyricsTopPx.value - lyricsTopPx
                            },
                    ) {
                        Spacer(Modifier.height(with(LocalDensity.current) { lyricsTopPx.toDp() }))
                        // Landscape lyrics scroll like the full page: centred, 30px high against lyrics
                        // at their size here, on the whole page (the low bar comes and goes over it).
                        val centredLiftPx = if (landscape) {
                            val lyricSp = landscapeMetrics.lyricFontSizeSp * settings.lyricsScale(landscape = true)
                            30f * lyricSp / 56f * density
                        } else {
                            30f * headerMetrics.lyricsScale * headerMetrics.density + controlsHeightPx / 2f
                        }
                        LyricsPanel(
                            lyrics = state.lyrics,
                            motionLyrics = settings.motionLyrics && settings.motionOnApp,
                            motionShade = settings.motionShade,
                            motionMerge = settings.motionMerge,
                            motionTheme = settings.motionTheme,
                            furigana = settings.furigana,
                            wordByWord = settings.wordByWord,
                            translation = state.translation.takeIf { state.translationEnabled },
                            translationMode = settings.translationMode,
                            textCredits = settings.textCredits,
                            romanizationCredit = rememberRomanizationCredit(state, romanize, settings.textCredits),
                            romanizationMode = settings.romanizationMode,
                            currentTimeMs = viewModel::currentLyricPositionMs,
                            onSeek = viewModel::seekTo,
                            romanize = romanize,
                            isPlaying = state.isPlaying,
                            // Compact scrolling keeps the active line near the top; without the
                            // header, full-page scrolling centres it, 30px high (`GetScrollType`), in the
                            // space above the controls (whether or not they are showing, so it holds still).
                            activeLineTopPx = headerMetrics.activeLineTopPx.takeUnless { lyricsFullPage || landscape },
                            centredLiftPx = centredLiftPx,
                            noticeBottomPx = { noticeBottomPx },
                            viewState = lyricsViewState,
                            // Pinned credits fade out under the controls, so while those show the
                            // credits go back to the end of the lyrics.
                            pinnedFooter = if (controlsVisible && bottomBar) PinnedFooterMode.Off else pinnedFooter,
                            maskBottomPx = {
                                val credits = if (pinnedHeightPx > 0) pinnedGapPx + pinnedHeightPx + pinnedClearPx else 0f
                                val card = if (nextUpHeightPx > 0) pinnedGapPx + credits + nextUpHeightPx + nextUpClearPx else 0f
                                maxOf(credits, card * nextUpActive)
                            },
                            config = renderConfig,
                            fontSizeScale = lyricsFontScale,
                            noticeActions = noticeActions,
                            modifier = Modifier.weight(1f),
                            // Under the fully expanded big cover they're drawn at alpha 0.
                            visible = { expansion < 1f },
                            tint = rememberLyricsTint(settings, state.artwork),
                            songKey = state.title + "\u0000" + state.artist,
                            songTransition = settings.lyricsSongTransition,
                            holdUntilFirstLine = settings.lyricsHoldUntilFirstLine,
                        )
                    }
                    // Ambient mode: the lyrics sink into the black under the song docked at the bottom,
                    // and rise out of it at the top.
                    if (ambientDrawn && !landscape) Box(
                        Modifier.fillMaxSize().graphicsLayer { alpha = ambientFade.value }.drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.04f to Color.Black,
                                    0.2f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    0.82f to Color.Black,
                                    1f to Color.Black,
                                ),
                            )
                        },
                    )
                // Hidden lyrics take no touches (no seeking through the cover view).
                if (headerExpanded) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                }
                // The stage's dock sits over the lyrics' layer: centred, it's where they are.
                if (landscape && stageVisible) {
                    // Along the stage's outer side beside the lyrics, gliding to the middle as it centres.
                    val stageAlign = { (if (stageRight) 1f else -1f) * (1f - stageCentred()) }
                    CoverStageDock(
                        metrics = landscapeMetrics,
                        onRight = stageRight,
                        startInsetPx = startInsetPx,
                        endInsetPx = endInsetPx,
                        shown = { stageShown.value },
                        swapAlpha = { stageSwapAlpha.value },
                        swapX = { stageSwapX.value },
                        centred = stageCentred,
                    ) {
                        // No glass blur here: the stage is part of what the glass blurs.
                        CompositionLocalProvider(LocalBackdrop provides null) {
                            StageControls(
                                controls = playbackControls,
                                romanizeAvailable = romanizeButton,
                                showResync = settings.resyncButton,
                                showExpand = expandButton,
                                romanized = romanize,
                                onToggleRomanize = {
                                    settings.romanize = !settings.romanize
                                    UsageStats.count(UsageCounter.ROMANIZE)
                                },
                                onOpenSettings = { whenUnlocked { showSettings = true } },
                                onOpenLyricsManager = onOpenLyricsManager,
                                onOpenQueue = onOpenQueue,
                                onOpenSource = onOpenSource,
                                onOpenQuickSettings = onOpenQuickSettings,
                                onEnterPip = onEnterPipButton,
                                expanded = headerExpanded,
                                onToggleExpanded = expandButtonTapped,
                                // Only beside the lyrics: centred, it stands where the big cover does.
                                onSwapSide = if (headerExpanded) null else ({
                                    settings.panelSide = if (settings.panelSide == PanelSide.Left) PanelSide.Right else PanelSide.Left
                                }),
                                shown = { controlsShown },
                                interactive = controlsVisible && stageOn,
                                onRight = stageRight,
                                align = stageAlign,
                                metadata = {
                                    val gutter = landscapeMetrics.coverSizePx * 0.04f
                                    val alignment = remember(stageRight) { SlidingAlignment(stageAlign) }
                                    StageMetadata(stageInfo, landscapeMetrics.panel, alignment, stageAlign, landscapeMetrics.coverSizePx - gutter * 2f,
                                        releaseYear = releaseYear)
                                },
                            )
                        }
                    }
                }
                // Hidden, the header only shows while the big cover is open or fading.
                val headerShowing by remember { derivedStateOf { expansion > 0f } }
                val expandedHeader = remember(headerMetrics, controlsHeightPx, landscape, landscapeMetrics) {
                    if (landscape) landscapeMetrics.centred else headerMetrics.expanded(pageHeight - controlsHeightPx)
                }
                CompactNowPlayingHeader(
                    info = NowPlayingInfo(state.title, state.artist, state.album, state.artwork, state.artworkUri, state.trackDirection, hasSession = state.sourcePackage != null),
                    releaseYear = releaseYear,
                    metrics = headerMetrics,
                    modifier = Modifier.padding(belowTop).graphicsLayer { alpha = 1f - ambientFade.value },
                    expansion = { expansion },
                    expanded = expandedHeader,
                    isPlaying = state.isPlaying,
                    onPlayPause = viewModel::playPause,
                    onSkip = { direction -> if (direction == TrackDirection.Forward) viewModel.skipNext() else viewModel.skipPrevious() },
                    animatedCover = settings.animatedCover && !lowPerformance && (!hideHeader || headerShowing) && !stageCovers,
                    hidden = hideHeader,
                    // Under the stage its gestures would sit over the dock.
                    interactive = (!hideHeader || headerExpanded) && !stageOn,
                    panel = landscapeMetrics.panel.takeIf { landscape },
                    panelLeft = { landscapeMetrics.coverLeftPx(panelSide.value) },
                    stage = { stageShown.value },
                    immersive = { if (landscape) 0f else immersive() },
                    animatedCoverHeld = ambientOpaque || (!landscape && immersiveOpaque),
                    visible = { ambientFade.value < 1f },
                    coverOverlay = if (!controlsOnCover || controlsGone || stageOn) null else {
                        {
                            // No glass blur here: the cover is part of what the glass blurs.
                            CompositionLocalProvider(LocalBackdrop provides null) {
                                CoverControls(
                                    controls = playbackControls,
                                    romanizeAvailable = romanizeButton,
                                    showResync = settings.resyncButton,
                                    showExpand = expandButton,
                                    romanized = romanize,
                                    onToggleRomanize = {
                                        settings.romanize = !settings.romanize
                                        UsageStats.count(UsageCounter.ROMANIZE)
                                    },
                                    onOpenSettings = { whenUnlocked { showSettings = true } },
                                    onOpenLyricsManager = onOpenLyricsManager,
                                    onOpenQueue = onOpenQueue,
                                    onOpenSource = onOpenSource,
                                    onOpenQuickSettings = onOpenQuickSettings,
                                    onEnterPip = onEnterPipButton,
                                    expanded = headerExpanded,
                                    onToggleExpanded = expandButtonTapped,
                                    // Only beside the lyrics: the big cover sits in the middle.
                                    onSwapSide = if (hideHeader || headerExpanded) null else ({
                                        settings.panelSide = if (settings.panelSide == PanelSide.Left) PanelSide.Right else PanelSide.Left
                                    }),
                                    coverOnRight = settings.panelSide == PanelSide.Right,
                                    shown = { controlsShown },
                                    timeline = !timelineOutside,
                                )
                            }
                        }
                    },
                    belowCover = if (!timelineOutside || !controlsOnCover) null else {
                        {
                            PanelTimeline(
                                controls = playbackControls,
                                coverSizePx = landscapeMetrics.coverSizePx,
                                pageHeightPx = landscapeMetrics.pageHeightPx,
                                timeSizeSp = landscapeMetrics.baseFontSizeSp,
                            )
                        }
                    },
                )
                // The song docked over the portrait Canvas or cover, where the big cover's text was.
                // The controls' shade is off with the big cover up, so nothing blurs it here. Only
                // while it shows: faded out, its timeline and names would run on unseen.
                val canvasDockShown by remember { derivedStateOf { immersiveShown * expansion * (1f - ambientFade.value) > 0f } }
                if (!landscape && immersivePicture != null && canvasDockShown) {
                    // In line with the controls' rows, so the song sits over the timeline.
                    val gutter = controlsContentStartPx(pageWidth, density)
                    PortraitCanvasDock(
                        info = stageInfo,
                        sizes = expandedHeader,
                        controls = playbackControls,
                        controlsShown = { if (bottomBar) controlsShown else 0f },
                        controlsHeightPx = controlsHeightPx,
                        widthPx = pageWidth - gutter * 2f,
                        gutterPx = gutter,
                        bottomPx = gutter,
                        // Ambient mode docks the song itself, below; this one goes with the cover.
                        alpha = { immersive() * (1f - ambientFade.value) },
                        // Clear of a shown navigation bar, as the controls are.
                        modifier = Modifier.padding(bottom = belowTop.calculateBottomPadding()),
                        releaseYear = releaseYear,
                    )
                }
                // Ambient mode: the song and its timeline docked at the bottom, as over the Canvas.
                if (!landscape && ambientDrawn) {
                    val gutter = controlsContentStartPx(pageWidth, density)
                    PortraitCanvasDock(
                        info = stageInfo,
                        sizes = expandedHeader,
                        controls = playbackControls,
                        controlsShown = { 0f },
                        controlsHeightPx = controlsHeightPx,
                        widthPx = pageWidth - gutter * 2f,
                        gutterPx = gutter,
                        bottomPx = gutter,
                        alpha = { ambientFade.value },
                        modifier = Modifier.padding(bottom = belowTop.calculateBottomPadding()),
                        releaseYear = releaseYear,
                    )
                }
                // The header sits in what the glass and the shade blur, so the shade blurs it
                // rather than painting the blurred page over it.
                }
                // In landscape, the bottom bar gives way to the controls on the big cover.
                val barFade = { if (landscape) 1f - expansion.coerceIn(0f, 1f) else 1f }
                val barGone by remember { derivedStateOf { controlsShown * barFade() == 0f } }
                if (bottomBar) CompositionLocalProvider(LocalBackdrop provides backdrop?.takeUnless { barGone || lowPerformance }) {
                    LyricsControls(
                        controls = playbackControls,
                        romanizeAvailable = romanizeButton,
                        showResync = settings.resyncButton,
                        showExpand = expandButton,
                        romanized = romanize,
                        onToggleRomanize = {
                            settings.romanize = !settings.romanize
                            UsageStats.count(UsageCounter.ROMANIZE)
                        },
                        onOpenSettings = { whenUnlocked { showSettings = true } },
                        onOpenLyricsManager = onOpenLyricsManager,
                        onOpenQueue = onOpenQueue,
                        onOpenSource = onOpenSource,
                        onOpenQuickSettings = onOpenQuickSettings,
                        onEnterPip = onEnterPipButton,
                        expanded = headerExpanded,
                        onToggleExpanded = expandButtonTapped,
                        interactive = controlsVisible && !controlsOnCover,
                        shown = { controlsShown * barFade() * (1f - ambientFade.value) },
                        // No lyrics behind them when expanded, so no shade over the cover.
                        shade = { 1f - expansion.coerceIn(0f, 1f) },
                        onControlsHeight = { controlsHeightPx = it },
                        wide = landscape,
                        insets = belowTop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                NextUpCard(
                    entry = queuedNext ?: heldNext,
                    shown = { nextUpActive * (1f - if (bottomBar) controlsShown else 0f) },
                    onHeight = { nextUpHeightPx = it },
                    modifier = Modifier
                        .align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
                        .padding(belowTop)
                        .inLyricsBox()
                        .graphicsLayer {
                            translationY = -(pinnedGapPx + if (pinnedHeightPx > 0) pinnedHeightPx + pinnedClearPx else 0f)
                        },
                )
                // Over the controls' shade rather than under it, so neither blurs nor covers them.
                PinnedLyricsFooter(
                    state = lyricsViewState,
                    mode = pinnedFooter,
                    fontSizeScale = lyricsFontScale,
                    // Faded out, they let the controls under them take the touch.
                    tappable = !(bottomBar && controlsTarget),
                    refined = settings.refinedCredits,
                    onHeight = { pinnedHeightPx = it },
                    modifier = Modifier
                        .align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
                        .padding(belowTop)
                        .inLyricsBox()
                        .graphicsLayer {
                            translationY = -pinnedGapPx
                            val underControls = if (bottomBar) controlsShown * barFade() else 0f
                            // In portrait ambient mode the docked song has the bottom.
                            val underDock = if (landscape) 0f else ambientFade.value
                            alpha = (1f - expansion) * swapFade() * (1f - underControls) * (1f - underDock)
                        },
                )
                CompositionLocalProvider(LocalBackdrop provides backdrop?.takeUnless { lowPerformance }) {
                    ScrollToActiveButton(
                        direction = lyricsViewState.activeLineDirection.takeIf {
                            settings.showScrollToActive && !headerExpanded && (!showSettings || settingsPeeking)
                        },
                        onClick = lyricsViewState::scrollToActive,
                        topPx = when {
                            landscape -> topInsetPx
                            hideHeader -> headerMetrics.barTopPx
                            else -> headerMetrics.lyricsTopPx
                        },
                        bottomPx = { pinnedGapPx + maxOf(controlsBottomPx, if (pinnedHeightPx > 0) pinnedHeightPx + pinnedClearPx else 0f) },
                        modifier = Modifier.padding(belowTop).inLyricsBox(),
                    )
                }
                // The touch that wakes ambient mode only wakes it: no seek, no button. It wakes it
                // here too, since the page's own watcher lets some touches by.
                if (ambient) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                    touches.value = SystemClock.uptimeMillis()
                                }
                            }
                        },
                    )
                }
            }
        }
        }

        // Coming back from the system's settings refreshes the grant (onResume), which closes it.
        SpicyModal(
            visible = !state.accessGranted && !showIntro,
            onDismissRequest = null,
            backdrop = backdrop,
        ) {
            NotificationAccessMessage(openNotificationAccess)
        }

        // Once, after access is sorted, for new installs and updates alike; nothing is sent before it's answered.
        SpicyModal(
            visible = state.accessGranted && UsageStats.available && !settings.usageStatsAsked && !showIntro,
            onDismissRequest = null,
            backdrop = backdrop,
        ) {
            UsageStatsMessage(answerUsageStats)
        }

        // Once, after the update that switched sources off; after the stats question. Kept through
        // the closing animation.
        val switchedOff = SourceDisclosures.switchedOffNotice(state.switchedOffSources)
        var lastSwitchedOff by remember { mutableStateOf<Pair<String, String>?>(null) }
        switchedOff?.let { lastSwitchedOff = it }
        SpicyModal(
            visible = state.accessGranted && switchedOff != null && !showIntro &&
                (!UsageStats.available || settings.usageStatsAsked),
            onDismissRequest = viewModel::dismissSwitchedOff,
            backdrop = backdrop,
        ) {
            lastSwitchedOff?.let { (title, text) ->
                SwitchedOffMessage(
                    title, text,
                    onDismiss = viewModel::dismissSwitchedOff,
                    onOpenSettings = { viewModel.dismissSwitchedOff(); whenUnlocked { showSettings = true } },
                )
            }
        }

        SleepTimerModal(visible = showSleepTimer, backdrop = backdrop, onDismiss = { showSleepTimer = false })

        ShareLyricsModal(
            visible = showShareLyrics,
            lyrics = state.lyrics as? LyricsState.Ready,
            title = state.title,
            artist = state.artist,
            cover = state.artwork,
            positionMs = viewModel::currentLyricPositionMs,
            backdrop = backdrop,
            onDismiss = { showShareLyrics = false },
        )

        QuickSettingsModal(
            visible = showQuickSettings,
            state = state,
            viewModel = viewModel,
            settings = settings,
            backdrop = backdrop,
            startOn = quickStart,
            onDismiss = { showQuickSettings = false },
            onSearchLyrics = { showQuickSettings = false; whenUnlocked { showLyricsSearch = true } },
        )

        // Kept through the closing animation, after the view model has cleared it.
        var lastLimit by remember { mutableStateOf<PlayerLimit?>(null) }
        state.limitNotice?.let { lastLimit = it }
        SpicyModal(
            visible = state.limitNotice != null,
            onDismissRequest = viewModel::dismissLimitNotice,
            backdrop = backdrop,
        ) {
            lastLimit?.let { SpotifyLimitMessage(it, viewModel::dismissLimitNotice) }
        }

        // Kept through the closing animation, after it's been answered.
        var lastProfile by remember { mutableStateOf<FooterLine?>(null) }
        lyricsViewState.profileRequest?.let { lastProfile = it }
        if (!settings.profilesInBrowser) {
            ProfileModal(lastProfile, lyricsViewState.profileRequest != null, backdrop, lyricsViewState::dismissProfile)
        } else SpicyModal(
            visible = lyricsViewState.profileRequest != null,
            onDismissRequest = lyricsViewState::dismissProfile,
            backdrop = backdrop,
        ) {
            lastProfile?.let { OpenProfileMessage(it, lyricsViewState::dismissProfile) }
        }

        if (showSettings) Box(
            Modifier
                .focusProperties { onEnter = { if (overSettings) cancelFocusChange() } }
                .focusGroup(),
        ) {
            SettingsScreen(
                state = state,
                viewModel = viewModel,
                updater = updater,
                settings = settings,
                backdrop = backdrop,
                contentPadding = padding,
                onClosed = { showSettings = false; settingsPages = emptyList(); viewModel.customSourceForm = null },
                onOpenLyricsManager = { whenUnlocked { showLyricsManager = true } },
                onOpenSpotifySearch = { whenUnlocked { showSpotifySearch = true } },
                onPeekChange = { settingsPeeking = it },
                restorePages = settingsPages,
                onPagesChange = { settingsPages = it },
            )
        }

        // Over settings, since it opens from there too.
        LyricsManagerModal(
            visible = showLyricsManager,
            state = state,
            viewModel = viewModel,
            settings = settings,
            backdrop = backdrop,
            onDismissRequest = { showLyricsManager = false },
        )

        // Over settings, since it opens from there too.
        SpotifySearchModal(
            visible = showSpotifySearch,
            state = state,
            viewModel = viewModel,
            backdrop = backdrop,
            onDismissRequest = { showSpotifySearch = false },
        )

        // Lyrics search: a song followed with no player, from the waiting notice or quick settings.
        LyricsSearchModal(
            visible = showLyricsSearch,
            title = if (state.lyrics is LyricsState.Idle) "" else state.title,
            artist = if (state.lyrics is LyricsState.Idle) "" else state.artist,
            backdrop = backdrop,
            onDismissRequest = { showLyricsSearch = false },
        )

        // Over settings, where sources are switched on. Kept through the closing animation.
        var lastDisclosure by remember { mutableStateOf<SourceDisclosure?>(null) }
        state.sourceDisclosure?.let { lastDisclosure = it }
        SpicyModal(
            visible = state.sourceDisclosure != null,
            onDismissRequest = viewModel::dismissSourceDisclosure,
            backdrop = backdrop,
        ) {
            lastDisclosure?.let { SourceDisclosureMessage(it, viewModel::acceptSourceDisclosure, viewModel::dismissSourceDisclosure) }
        }

        // Over settings, so a check from there answers in place.
        UpdatePopup(update, updater, backdrop)

        // Asked for from Settings: it goes, so the intro ends on the lyrics.
        LaunchedEffect(showIntro) { if (showIntro) showSettings = false }
        if (showIntro) {
            IntroScreen(
                settings = settings,
                accessGranted = state.accessGranted,
                askUsageStats = UsageStats.available && !settings.usageStatsAsked,
                openNotificationAccess = openNotificationAccess,
                onUsageStats = { keep -> answerUsageStats(keep) },
                onDone = {
                    settings.introDone = true
                    settings.introReplay = false
                },
            )
        }

        // ivLyrics' vinyl player, over everything but the toasts; its close turns it off.
        if (settings.vinylMode && kr.ivlis.ivlyricsandroid.SpicyBridge.supported() && !showIntro) {
            val vinylLines = remember(state.lyrics) {
                (state.lyrics as? LyricsState.Ready)?.let { com.tx24.spicyplayer.ivlyrics.rendererLines(it) }.orEmpty()
            }
            com.tx24.spicyplayer.ivlyrics.VinylMode(
                title = state.title,
                artist = state.artist,
                album = state.album,
                spotifyId = state.manualSpotifyId ?: state.detectedSpotifyId,
                artwork = state.artwork,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                lines = vinylLines,
                currentTimeMs = viewModel::currentLyricPositionMs,
                onClose = { settings.vinylMode = false },
                onPlayPause = viewModel::playPause,
                onSeek = viewModel::seekTo,
                onPause = { if (state.isPlaying) viewModel.playPause() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        SpicyToastHost(viewModel.messages, Modifier.padding(padding).padding(top = SpicySpacing.S4))
    }
}

/**
 * The lyrics in the floating window: the background and the lyrics, centred, with the text sized
 * to the window (the 29.6sp minimum at 300x190dp, shrinking with it) so a few rows still fit.
 * With the lyrics turned off for the window, or none for the song and the song card picked for
 * that, the song's card stands in. A square or tall window names the song along its bottom.
 *
 * With the Canvas background, the song's [canvasUrl] is blurred behind the lyrics, as on the full screen.
 */
@Composable
private fun PipLyrics(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    config: RenderConfig,
    viewState: LyricsViewState,
    romanize: Boolean,
    lowPerformance: Boolean,
    canvasUrl: String?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A short window fits few rows, so the height limits the text as much as the width does.
        val windowScale = minOf(maxWidth.value / 300f, maxHeight.value / 190f).coerceIn(0.4f, 1f)
        // Unsynced lyrics are drawn smaller to fit a page of text, which in a window this small
        // leaves them unreadable: they get the synced lyrics' size here.
        val staticBoost = if ((state.lyrics as? LyricsState.Ready)?.lyricsType == LyricsType.Static)
            LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Syllable, maxWidth.value) / LyricsLayoutMetrics.baseFontSizeSp(LyricsType.Static, maxWidth.value)
            else 1f
        // The floating window has its own lyrics size, apart from the full screen's.
        val fontScale = windowScale * (settings.pipLyricsSize / 100f) * staticBoost
        // The Canvas background, as on the full screen.
        val canvasShown by animateFloatAsState(
            if (settings.canvasBackground && !lowPerformance && canvasUrl != null) 1f else 0f,
            tween(STAGE_FADE_MS, easing = StageEaseOut),
            label = "pipCanvas",
        )
        SpicySessionBackground(
            artwork = state.artwork,
            artworkUri = state.artworkUri,
            isPlaying = state.isPlaying,
            hasSession = state.sourcePackage != null,
            modifier = Modifier.fillMaxSize(),
            type = settings.backgroundType,
            // Held still once the Canvas backdrop covers it, as on the full screen.
            animate = !lowPerformance && !settings.staticBackground && canvasShown < 1f,
            blurDp = settings.backgroundBlurFor(settings.backgroundType),
            artistHeaderUrl = state.artistHeaderUrl,
            artistHeaderPending = state.artistHeaderPending,
            refined = settings.refinedBackground,
            customColor = Color(settings.backgroundColorArgb),
            photo = settings.backgroundPhoto,
        )
        var shownCanvasUrl by remember { mutableStateOf<String?>(null) }
        canvasUrl?.let { shownCanvasUrl = it }
        shownCanvasUrl?.takeIf { canvasShown > 0f }?.let { url ->
            CanvasBackdrop(url, settings.canvasBlur, onFailed = { viewModel.canvasFailed(url) }, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = canvasShown })
        }
        val songCard = !settings.pipShowLyrics ||
            (state.lyrics is LyricsState.Error && settings.pipWithoutLyrics == PipWithoutLyrics.SongCard)
        val pipYear = ReleaseYear(state.releaseYear, state.releaseYearPending, settings.releaseYearPosition)
        if (songCard) {
            PipSongCard(state.title, pipYear.besideArtists(state.artist), state.artwork, state.artworkUri, hasSession = state.sourcePackage != null)
            return@BoxWithConstraints
        }
        // Square or taller, the song docks along the bottom, under the lyrics.
        val dock = settings.pipDock && settings.pipAspect <= 1f
        Column(Modifier.fillMaxSize()) {
        LyricsPanel(
            lyrics = state.lyrics,
            motionLyrics = settings.motionLyrics && settings.motionOnApp,
            motionShade = settings.motionShade,
            motionMerge = settings.motionMerge,
            motionTheme = settings.motionTheme,
            furigana = settings.furigana,
            wordByWord = settings.wordByWord,
            translation = state.translation.takeIf { state.translationEnabled },
            translationMode = settings.translationMode,
            textCredits = settings.textCredits,
            romanizationCredit = rememberRomanizationCredit(state, romanize, settings.textCredits),
            romanizationMode = settings.romanizationMode,
            currentTimeMs = viewModel::currentLyricPositionMs,
            onSeek = {},
            romanize = romanize,
            isPlaying = state.isPlaying,
            // At the top: just under the window's edge fade, so the lines above scroll out of it.
            activeLineTopPx = if (settings.pipLineAtTop) with(LocalDensity.current) { PIP_LINE_TOP.toPx() } else null,
            centredLiftPx = 0f,
            noticeBottomPx = { 0f },
            viewState = viewState,
            pinnedFooter = PinnedFooterMode.Off,
            maskBottomPx = { 0f },
            config = config,
            fontSizeScale = fontScale,
            noticeActions = null,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // The page's edge fade is 64dp deep: all of a window this small.
            maskScale = 0.25f,
            tint = rememberLyricsTint(settings, state.artwork),
            songKey = state.title + "\u0000" + state.artist,
            songTransition = settings.lyricsSongTransition,
            holdUntilFirstLine = settings.lyricsHoldUntilFirstLine,
        )
        if (dock) {
            PipDock(
                state.title, pipYear.besideArtists(state.artist), viewModel::currentPositionMs, state.durationMs, state.isPlaying,
                cover = settings.pipDockCover, artwork = state.artwork, artworkUri = state.artworkUri,
                hasSession = state.sourcePackage != null,
                credit = viewState.shownFooter?.miniCredit()?.takeIf { settings.pinnedFooter != PinnedFooterMode.Off },
            )
        }
        }
    }
}

/** Asks for the display mode nearest [hz] at the current resolution, or the system's choice for null. */
private fun setPreferredRefreshRate(window: Window, hz: Float?) {
    @Suppress("DEPRECATION")
    val display = window.windowManager.defaultDisplay
    val current = display.mode
    val modeId = hz?.let {
        display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { mode -> abs(mode.refreshRate - hz) }?.modeId
    } ?: 0
    if (window.attributes.preferredDisplayModeId != modeId) {
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
    }
}

/** Tells the touch watcher whether a touch landed on the landscape cover; null: anywhere counts. */
private class CoverTouchArea {
    var contains: ((Offset) -> Boolean)? = null
}

/** The floating window's line being sung, with Current line at the top: past the 16dp edge fade. */
private val PIP_LINE_TOP = 14.dp

/** Pinned credits' distance above the controls or the screen's edge (20px + 1.25rem). */
private val PINNED_FOOTER_GAP = 20.dp
private const val NEXT_UP_TICK_MS = 500L
/** The cover stage's side swap: out, then in, drifting this share of the screen's width. */
private const val STAGE_SWAP_OUT_MS = 160
private const val STAGE_SWAP_IN_MS = 420
private const val STAGE_SWAP_DRIFT = 0.025f
private val StageEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val CONTROLS_SHOW_MS = 350
private const val AMBIENT_IN_MS = 1_500
private const val AMBIENT_OUT_MS = 300
/** Ambient mode's screen brightness, as a window override (0-1). */
private const val AMBIENT_BRIGHTNESS = 0.1f
/** The always-on display's brightness: the lowest the window takes, barely lit. */
private const val AOD_BRIGHTNESS = 0.01f
private const val CONTROLS_HIDE_MS = 700
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The arrow that takes the lyrics back to the line being sung once it has been scrolled out of
 * sight: at the top when the line is below, at the bottom (above the controls and pinned
 * credits) when it's above, pointing its way. It fades in over 0.2 s and grows from 0.92 with a
 * little overshoot.
 */
@Composable
private fun ScrollToActiveButton(
    direction: ActiveLineDirection?,
    onClick: () -> Unit,
    topPx: Float,
    bottomPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    // Keeps its place and arrow while it fades out.
    var shownDirection by remember { mutableStateOf(ActiveLineDirection.Below) }
    if (direction != null) shownDirection = direction
    val visible = direction != null
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "scrollToActiveAlpha")
    val scale by animateFloatAsState(
        if (visible) 1f else 0.92f,
        tween(160, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "scrollToActiveScale",
    )
    val above = shownDirection == ActiveLineDirection.Above
    val rotation by animateFloatAsState(if (above) 180f else 0f, tween(200), label = "scrollToActiveArrow")
    if (alpha == 0f && !visible) return
    val grouped = LocalGroupedControls.current
    val label = if (above) "Scroll up to active lyric" else "Scroll down to active lyric"
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // clamp(2rem, 8cqh, 6rem) from the lyrics' top or bottom edge.
        val edge = (maxHeight * 0.08f).coerceIn(32.dp, 96.dp)
        val top = with(density) { topPx.toDp() } + edge
        val arrow = @Composable {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                null,
                Modifier.size(28.dp).graphicsLayer { rotationZ = rotation },
                tint = SpicyColors.TextPrimary,
            )
        }
        if (grouped) {
            // A one-button capsule in the bottom corner, above the controls, whichever way the line
            // is: the arrow turns to point there.
            GlassCapsule(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp - 48.dp * CAPSULE_PAD)
                    .graphicsLayer {
                        translationY = -(bottomPx() + edge.toPx())
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                CapsuleButton(
                    onClick = { if (visible) onClick() },
                    contentDescription = label,
                    size = 48.dp,
                    modifier = Modifier.padding(48.dp * CAPSULE_PAD),
                ) { arrow() }
            }
            return@BoxWithConstraints
        }
        GlassButton(
            onClick = { if (visible) onClick() },
            contentDescription = label,
            size = 48.dp,
            modifier = Modifier
                .align(if (above) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(end = 24.dp, top = if (above) 0.dp else top)
                .graphicsLayer {
                    if (above) translationY = -(bottomPx() + edge.toPx())
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        ) { arrow() }
    }
}

/** Asks for the notification-listener grant, which is how Android hands out media sessions. */
@Composable
private fun NotificationAccessMessage(openNotificationAccess: () -> Unit) {
    SpicyModalMessage(
        title = "Allow notification access",
        description = "Spicy Lyrics Plus reads the song playing in your music app, and controls it, through its media " +
            "notification. Android files that under notification access. You can turn it off again in system settings.",
        icon = {
            Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary)
        },
    )
    SpicyModalGap()
    SpicyModalButton("Open notification access", openNotificationAccess, style = SpicyButtonStyle.Primary, fill = true)
}

/** The one-time notice for the anonymous usage stats, which are on unless turned off here. */
@Composable
private fun UsageStatsMessage(onAnswer: (keepOn: Boolean) -> Unit) {
    SpicyModalMessage(
        title = "Anonymous usage stats",
        description = "Spicy Lyrics Plus sends a small anonymous report a few times a day: the app and Android version, " +
            "your phone model and country, and which features you use. Never what you listen to. It shows what's " +
            "worth working on. You can change this in Settings → Advanced.",
        icon = { Icon(Icons.Rounded.Insights, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalActions {
        SpicyModalButton("Turn off", { onAnswer(false) })
        SpicyModalButton("Keep on", { onAnswer(true) }, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun SwitchedOffMessage(title: String, text: String, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    SpicyModalMessage(
        title = title,
        description = text,
        icon = { Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalActions {
        SpicyModalButton("Open settings", onOpenSettings)
        SpicyModalButton("Got it", onDismiss, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun SourceDisclosureMessage(disclosure: SourceDisclosure, onAccept: () -> Unit, onCancel: () -> Unit) {
    SpicyModalMessage(
        title = "Turn on ${disclosure.name}?",
        description = disclosure.description,
        icon = { Icon(Icons.Rounded.Public, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalActions {
        SpicyModalButton("Cancel", onCancel)
        SpicyModalButton("Turn on", onAccept, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun UpdatePopup(update: UpdateUiState, updater: UpdateViewModel, backdrop: HazeState?) {
    // Kept through the closing animation.
    var shown by remember { mutableStateOf(update.status) }
    if (update.prompt) shown = update.status
    val release = when (val status = shown) {
        is UpdateStatus.Available -> status.release
        is UpdateStatus.Downloading -> status.release
        is UpdateStatus.Installing -> status.release
        is UpdateStatus.ReadyToInstall -> status.release
        is UpdateStatus.Failed -> status.release
        else -> null
    }
    val context = LocalContext.current
    // Asked for once an update starts, so a download that finishes out of view can say so; the
    // update goes ahead whatever the answer.
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { updater.install() }
    val startUpdate = {
        val ask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (ask) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else updater.install()
    }
    SpicyModal(
        visible = update.prompt && release != null,
        onDismissRequest = updater::later,
        backdrop = backdrop,
    ) {
        if (release == null) return@SpicyModal
        SpicyModalHeading("Update available", release.name.takeIf { it != release.tag && it != release.version })
        SpicyVersionRow(BuildConfig.VERSION_NAME, release.version)
        val status = when (val current = shown) {
            is UpdateStatus.Installing -> "Confirm the install in Android's prompt."
            is UpdateStatus.ReadyToInstall -> "Downloaded. Tap Install to confirm it in Android's prompt."
            is UpdateStatus.Failed -> current.message
            else -> null
        }
        (shown as? UpdateStatus.Downloading)?.let { DownloadProgress(it.progress) }
            ?: SpicyModalNotes(release.notes, status ?: "No notes for this version.".takeIf { release.notes.isEmpty })
        SpicyModalGap()
        val busy = shown is UpdateStatus.Downloading || shown is UpdateStatus.Installing
        val ready = shown is UpdateStatus.ReadyToInstall
        SpicyModalActions {
            if (!busy && !ready) SpicyModalButton("Skip this version", updater::skip, style = SpicyButtonStyle.Quiet)
            SpicyModalButton(if (busy) "Hide" else "Later", updater::later)
            when {
                ready -> SpicyModalButton("Install", { updater.confirmInstall(context) }, style = SpicyButtonStyle.Primary)
                !busy -> SpicyModalButton(if (shown is UpdateStatus.Failed) "Try again" else "Update", startUpdate, style = SpicyButtonStyle.Primary)
            }
        }
    }
}

/** The download as a capsule filling with white, like the timeline, with the percentage under it. */
@Composable
private fun DownloadProgress(progress: Float) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(200), label = "downloadProgress")
    Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(SpicyColors.TintBg)
                .border(1.dp, SpicyColors.Hairline, CircleShape),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(CircleShape).background(Color.White))
        }
        Text(
            "Downloading… ${(progress * 100).roundToInt()}%",
            style = SpicyType.Footnote.copy(color = SpicyColors.TextSecondary, fontFeatureSettings = "tnum"),
        )
    }
}

/** Asks before leaving the app for a credit's profile, since the links sit where thumbs land. */
@Composable
private fun OpenProfileMessage(line: FooterLine, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val url = line.profileUrl ?: return
    SpicyModalMessage(
        title = line.name?.let { "Open @$it's profile?" } ?: "Open this profile?",
        description = url.removePrefix("https://").substringBefore('/'),
        icon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalGap()
    SpicyModalActions {
        SpicyModalButton("Cancel", onDismiss)
        SpicyModalButton("Open", {
            onDismiss()
            runCatching { uriHandler.openUri(url) }
        }, style = SpicyButtonStyle.Primary)
    }
}

@Composable
private fun SpotifyLimitMessage(limit: PlayerLimit, onDismiss: () -> Unit) {
    val (title, description) = when (limit) {
        PlayerLimit.Seek -> "Seeking needs Spotify Premium" to
            "Spotify Free doesn't let other apps move around in a song, so tapping a line or dragging " +
            "the timeline can't jump there. Playback carries on as normal."
        PlayerLimit.Previous -> "Going back needs Spotify Premium" to
            "Spotify Free doesn't allow going back to the previous song from here."
        PlayerLimit.Skips -> "Spotify isn't skipping" to
            "Spotify Free stops allowing skips for a while after a few. Skipping works again later."
    }
    SpicyModalMessage(
        title = title,
        description = description,
        icon = { Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
    )
    SpicyModalGap()
    SpicyModalButton("Got it", onDismiss, style = SpicyButtonStyle.Primary, fill = true)
}

@Composable
private fun LyricsPanel(
    lyrics: LyricsState,
    currentTimeMs: () -> Long,
    onSeek: (Long) -> Unit,
    romanize: Boolean,
    isPlaying: Boolean,
    activeLineTopPx: Float?,
    centredLiftPx: Float,
    noticeBottomPx: () -> Float,
    viewState: LyricsViewState,
    pinnedFooter: PinnedFooterMode,
    maskBottomPx: () -> Float,
    config: RenderConfig,
    fontSizeScale: Float,
    /** The notice's buttons; null draws the floating window's compact notice. */
    noticeActions: NoticeActions?,
    modifier: Modifier = Modifier,
    maskScale: Float = 1f,
    translation: TranslationResult? = null,
    translationMode: TranslationMode = TranslationMode.UnderLine,
    romanizationMode: RomanizationMode = RomanizationMode.Replace,
    /** False while the lyrics are fully hidden: their frame loop rests. */
    visible: () -> Boolean = { true },
    /** Whether the credits say who translated the lyrics; [romanizationCredit] is already null when they don't. */
    textCredits: Boolean = true,
    romanizationCredit: String? = null,
    /** The lyrics' colour (Lyrics colour); white draws them as they are. */
    tint: com.tx24.spicyplayer.lyrics.LyricsInk = com.tx24.spicyplayer.lyrics.LyricsInk.White,
    /** The song the lyrics are for; a change of it is a song change ([songTransition]). */
    songKey: String? = null,
    /** Song changes: the last song's lyrics fade into the next one's instead of vanishing. */
    songTransition: Boolean = false,
    /**
     * With [songTransition], when the last song ran into the next one (it was at or past its last
     * line, as with a crossfade or Automix), its lyrics stay until the new song's first line.
     */
    holdUntilFirstLine: Boolean = false,
    /** Word-synced songs play as motion lyrics. */
    motionLyrics: Boolean = false,
    /** Motion lyrics' shade, 0-100. */
    motionShade: Int = 35,
    /** Spicy's lyrics show under the motion lyrics. */
    motionMerge: Boolean = false,
    /** Motion lyrics' look. */
    motionTheme: com.tx24.spicyplayer.ivlyrics.MotionTheme = com.tx24.spicyplayer.ivlyrics.MotionTheme.Classic,
    /** Japanese lines read from ivLyrics' furigana. */
    furigana: Boolean = false,
    /** Line-timed songs light word by word. */
    wordByWord: Boolean = false,
) {
    val transition = com.tx24.spicyplayer.lyrics.rememberSongTransition(
        lyrics, translation, songKey, currentTimeMs, songTransition, holdUntilFirstLine,
    )
    val outgoing = transition.outgoing
    // The loading skeleton: up as soon as the lookup starts, over whatever the panel
    // shows, fading in over 0.2s and out over 0.25s (ease-out).
    Box(modifier) {
        if (outgoing != null) {
            // The last song's lyrics, on their own time, fading out as the new ones come in.
            val outgoingState = remember(outgoing) { LyricsViewState() }
            LyricsPanelContent(
                outgoing.lyrics, { outgoing.timeMs() }, {}, romanize, isPlaying && outgoing.runs, activeLineTopPx, centredLiftPx,
                noticeBottomPx, outgoingState, PinnedFooterMode.Off, { 0f }, config, fontSizeScale, null,
                Modifier.fillMaxSize().graphicsLayer { alpha = transition.outgoingAlpha() },
                maskScale = maskScale,
                tint = tint,
                motionMerge = motionMerge,
                motionTheme = motionTheme,
                translation = outgoing.translation,
                translationMode = translationMode,
                romanizationMode = romanizationMode,
                visible = visible,
                motionLyrics = motionLyrics,
                motionShade = motionShade,
                furigana = furigana,
                wordByWord = wordByWord,
            )
        }
        LyricsPanelContent(
            lyrics, currentTimeMs, onSeek, romanize, isPlaying, activeLineTopPx, centredLiftPx, noticeBottomPx,
            viewState, pinnedFooter, maskBottomPx, config, fontSizeScale, noticeActions,
            Modifier.fillMaxSize().let { base ->
                if (outgoing != null) base.graphicsLayer { alpha = transition.incomingAlpha() } else base
            },
            maskScale = maskScale,
            tint = tint,
            motionMerge = motionMerge,
            motionTheme = motionTheme,
            translation = translation,
            translationMode = translationMode,
            romanizationMode = romanizationMode,
            textCredits = textCredits,
            romanizationCredit = romanizationCredit,
            visible = visible,
            motionLyrics = motionLyrics,
            motionShade = motionShade,
            furigana = furigana,
            wordByWord = wordByWord,
        )
        AnimatedVisibility(
            visible = lyrics == LyricsState.Loading && outgoing == null,
            enter = fadeIn(tween(200, easing = CssEaseOut)),
            exit = fadeOut(tween(250, easing = CssEaseOut)),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density
                val metrics = LyricsLayoutMetrics(constraints.maxWidth.toFloat(), density, LyricsType.Syllable, fontSizeScale)
                val column = metrics.contentSlot(hasDuet = false, isRtl = false, oppositeAligned = false)
                LyricsSkeleton(
                    lineSizeSp = metrics.baseFontSizeSp,
                    lineStartPx = column.startPx,
                    lineMaxWidthPx = column.widthPx,
                    topPx = activeLineTopPx ?: 0f,
                )
            }
        }
    }
}

@Composable
private fun LyricsPanelContent(
    lyrics: LyricsState,
    currentTimeMs: () -> Long,
    onSeek: (Long) -> Unit,
    romanize: Boolean,
    isPlaying: Boolean,
    activeLineTopPx: Float?,
    centredLiftPx: Float,
    noticeBottomPx: () -> Float,
    viewState: LyricsViewState,
    pinnedFooter: PinnedFooterMode,
    maskBottomPx: () -> Float,
    config: RenderConfig,
    fontSizeScale: Float,
    /** The notice's buttons; null draws the floating window's compact notice. */
    noticeActions: NoticeActions?,
    modifier: Modifier = Modifier,
    maskScale: Float = 1f,
    translation: TranslationResult? = null,
    translationMode: TranslationMode = TranslationMode.UnderLine,
    romanizationMode: RomanizationMode = RomanizationMode.Replace,
    textCredits: Boolean = true,
    romanizationCredit: String? = null,
    visible: () -> Boolean = { true },
    /** Word-synced songs play as motion lyrics. */
    motionLyrics: Boolean = false,
    /** Motion lyrics' shade, 0-100. */
    motionShade: Int = 35,
    /** Spicy's lyrics show under the motion lyrics, not only its interlude dots. */
    motionMerge: Boolean = false,
    /** Motion lyrics' look. */
    motionTheme: com.tx24.spicyplayer.ivlyrics.MotionTheme = com.tx24.spicyplayer.ivlyrics.MotionTheme.Classic,
    /** The lyrics' colour; the motion page keeps its own (see [com.tx24.spicyplayer.ivlyrics.MotionWithSpicy]). */
    tint: com.tx24.spicyplayer.lyrics.LyricsInk = com.tx24.spicyplayer.lyrics.LyricsInk.White,
    /** Japanese lines read from ivLyrics' furigana. */
    furigana: Boolean = false,
    /** Line-timed songs light word by word. */
    wordByWord: Boolean = false,
) {
    when (lyrics) {
        LyricsState.Idle -> LyricsNotice(
            "Waiting for a song",
            modifier.lyricsTint(tint),
            body = if (noticeActions?.onSearchLyrics != null) "Play something, or search for a song's lyrics to follow them with nothing playing." else null,
            actions = noticeActions,
            bottomPx = noticeBottomPx,
            compact = noticeActions == null,
        )
        LyricsState.Loading -> Unit
        is LyricsState.Error -> LyricsNotice(lyrics, noticeActions, noticeBottomPx, modifier.lyricsTint(tint))
        is LyricsState.Ready -> {
            val baseLines = remember(lyrics.lines) {
                lyrics.lines.map { line ->
                    Line(
                        words = line.words.map { word ->
                            Word(word.text, word.startMs, word.endMs, isPartOfWord = word.attached, romanizedText = word.romanized)
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
            }
            // ivLyrics' furigana, where it's on and the song has kanji: shown as the lines' reading.
            val furiganaLines = com.tx24.spicyplayer.ivlyrics.rememberFurigana(
                furigana, baseLines, remember(lyrics.lines) { lyrics.lines.hashCode().toString() },
            )
            @Suppress("NAME_SHADOWING")
            val romanize = romanize || furiganaLines !== baseLines
            // Line-timed songs, word by word on made-up timing, where that's on.
            val wordTimed = wordByWord && lyrics.lyricsType == LyricsType.Line &&
                remember(furiganaLines) { com.tx24.spicyplayer.lyrics.spicy.models.GeneratedWordTiming.applies(furiganaLines) }
            val originalLines = remember(furiganaLines, wordTimed) {
                if (wordTimed) com.tx24.spicyplayer.lyrics.spicy.models.GeneratedWordTiming.fillIn(furiganaLines) else furiganaLines
            }
            val lyricsType = if (wordTimed) LyricsType.Syllable else lyrics.lyricsType
            val rendererLines = remember(originalLines, config.isMinimal, config.isBloom) {
                buildDisplayTimeline(originalLines, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyricsType) {
                    LyricsType.Syllable -> config.isMinimal || config.isBloom
                    LyricsType.Line -> config.isBloom
                    else -> false
                })
            }
            val presentation = remember(rendererLines, originalLines, translation, translationMode) {
                translation?.let { result ->
                    TranslationPresentation.forTimeline(originalLines, rendererLines, result, translationMode)
                }
            }
            val copyContext = LocalContext.current
            // Motion lyrics for word-synced songs, the whole song through, with Spicy's interlude
            // dots in the gaps (or, merged, Spicy's lyrics under it).
            val motionFits = com.tx24.spicyplayer.ivlyrics.motionLyricsFit(motionLyrics, furiganaLines, lyrics.lyricsType)
            val documentId = remember(lyrics.lines) { lyrics.lines.hashCode().toString() }
            val spicyLyrics: @Composable (List<Line>, com.tx24.spicyplayer.ivlyrics.SpicyWithMotion?, Modifier) -> Unit = { lines, part, partModifier ->
                val dots = part == com.tx24.spicyplayer.ivlyrics.SpicyWithMotion.Dots
                val full = part == null
                LyricsView(
                    lines = lines,
                    documentId = documentId,
                    currentTimeMs = currentTimeMs,
                    onSeekWord = if (dots) { _ -> } else onSeek,
                    onLineLongPress = if (dots) null else { line -> copyHeldLine(copyContext, line) },
                    romanize = romanize,
                    romanizationMode = romanizationMode,
                    translation = if (dots) null else presentation,
                    isPlaying = isPlaying,
                    visible = visible,
                    activeLineTopPx = if (full) activeLineTopPx else null,
                    focusAnchorFraction = 0.5f,
                    focusLiftPx = if (dots) 0f else centredLiftPx,
                    // The list's `padding-top: 25cqh`.
                    restTopFraction = if (full) 0.25f else null,
                    lyricsType = lyricsType,
                    config = config,
                    fontSizeScale = fontSizeScale,
                    viewState = if (dots) null else viewState,
                    pinnedFooter = if (dots) PinnedFooterMode.Off else pinnedFooter,
                    maskBottomPx = if (part != null) { { 0f } } else maskBottomPx,
                    maskScale = maskScale,
                    footer = if (dots) LyricsFooter() else remember(lyrics, translation, textCredits, romanizationCredit) {
                        LyricsFooter(
                            songwriters = lyrics.songwriters,
                            translationCredit = translation?.credit()?.takeIf { textCredits },
                            romanizationCredit = romanizationCredit,
                            // "Lyrics: Spicy Lyrics • Apple Music" when a provider syndicates another catalogue.
                            // The source alone: Apple Music's lyrics read the same from every service that fetches them.
                            provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                            maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                            uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                        )
                    },
                    modifier = partModifier.lyricsTint(tint),
                )
            }
            if (motionFits) {
                com.tx24.spicyplayer.ivlyrics.MotionWithSpicy(
                    motionLines = remember(furiganaLines, translation) { com.tx24.spicyplayer.ivlyrics.withTranslations(furiganaLines, translation) },
                    timeline = rendererLines,
                    documentId = documentId,
                    currentTimeMs = currentTimeMs,
                    isPlaying = isPlaying,
                    romanize = romanize,
                    visible = visible,
                    shade = motionShade / 100f,
                    merge = motionMerge,
                    theme = motionTheme,
                    // Merged, the motion page and Spicy's lines both stay above the controls,
                    // which would otherwise cover the lines at the bottom.
                    modifier = if (motionMerge) modifier.padding(bottom = with(LocalDensity.current) { maskBottomPx().toDp() }) else modifier,
                ) { lines, part, partModifier -> spicyLyrics(lines, part, partModifier) }
            } else {
                spicyLyrics(rendererLines, null, modifier)
            }
        }
    }
}

/** "Romanization from" in the credits: while romanization shows and the credit is on, where it comes from. */
@Composable
private fun rememberRomanizationCredit(state: PlayerUiState, romanize: Boolean, shown: Boolean): String? =
    remember(state.lyrics, state.humanRomanizedLines, romanize, shown) {
        val lyrics = state.lyrics as? LyricsState.Ready
        val romanized = lyrics?.lines?.count { line -> line.words.any { it.romanized != null } } ?: 0
        if (!romanize || !shown || lyrics == null || romanized == 0) null
        else romanizationOrigins(lyrics.sourceRomanized, state.humanRomanizedLines, romanized).joinToString(" + ") { it.label }
    }

/** How long the custom PiP shape has to stay put before it's sent. */
internal const val PIP_RATIO_SETTLE_MS = 600L
/** How long to wait before asking again for a PiP shape the system refused. */
internal const val PIP_RATIO_RETRY_MS = 5_000L

/** Holding a line copies it (Settings → Controls → Hold a line to copy), unless that's off. */
private fun copyHeldLine(context: android.content.Context, line: String) {
    if (line.isBlank() || !context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getBoolean("holdToCopy", true)) return
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lyrics", line))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast.makeText(context, "Line copied", android.widget.Toast.LENGTH_SHORT).show()
    }
}
