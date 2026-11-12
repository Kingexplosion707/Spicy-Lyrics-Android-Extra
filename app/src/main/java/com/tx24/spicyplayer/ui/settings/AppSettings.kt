package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.ui.background.LEGACY_BLUR_DP
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.haptics.HapticBeatDetection
import com.tx24.spicyplayer.haptics.HapticBeatEngine
import com.tx24.spicyplayer.haptics.HapticBeatMode
import com.tx24.spicyplayer.haptics.HapticBeatTiming
import com.tx24.spicyplayer.haptics.MusicHapticsStyle
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.ui.components.ChromeTheme
import com.tx24.spicyplayer.ui.nowplaying.HeaderSize
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import com.tx24.spicyplayer.ui.nowplaying.LoopKind
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * How the lyrics screen looks and behaves, saved in the `ui` preferences. Each setting reads as
 * Compose state, so changing one redraws what uses it; writing one also saves it.
 *
 * Lookup, source and sync settings live in the view model instead (see `docs/settings.md`).
 */
class AppSettings(private val prefs: SharedPreferences) {
    /** Nothing was saved yet when this was made: a new install, or one whose data was cleared. */
    val startedEmpty = prefs.all.isEmpty()

    /** Every setting below, so [reload] can re-read them; first, so it exists when they register. */
    private val all = mutableListOf<Setting<*>>()
    // `coverStage` and `bigCoverCanvas` are only read now, by [stageArt] and [bigCoverArt], but an
    // older backup still restores them.
    private val types = mutableMapOf<String, KClass<*>>("coverStage" to Boolean::class, "bigCoverCanvas" to Boolean::class)

    /** The type each setting is stored as, by key, so a restore can turn away the wrong one. */
    val storedTypes: Map<String, KClass<*>> get() = types

    /** Re-reads every setting from storage, after a restore wrote it behind their backs. */
    fun reload() = all.forEach { it.reload() }

    /** Romanized lyrics where the song has them; the floating button, not a settings row. */
    var romanize by boolean("romanize", false)
    var romanizationMode by enum("romanizationMode", com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode.Replace)
    var translationMode by enum("translationMode", com.tx24.spicyplayer.translation.TranslationMode.UnderLine)

    var lyricsStyle by enum("lyricsStyle", LyricsStyle.Spicy)
    var simpleLyricsMode by boolean("simpleLyricsMode", false)
    var refreshedSimpleLyrics by boolean("refreshedSimpleLyrics", false)
    var minimalLyricsMode by boolean("minimalLyricsMode", false)
    var originalWordMotion by boolean("originalWordMotion", false)
    /**
     * The lyric text size against the screen's default, in percent, held apart for portrait and
     * landscape. Both start from the size picked before they were split.
     */
    var lyricsSizePortrait by int("lyricsSizePortrait", legacyLyricsSize(), LYRICS_SIZE_RANGE)
    var lyricsSizeLandscape by int("lyricsSizeLandscape", legacyLyricsSize(), LYRICS_SIZE_RANGE)
    /** The lyrics' size as a scale, for the way the screen is turned. */
    fun lyricsScale(landscape: Boolean): Float = (if (landscape) lyricsSizeLandscape else lyricsSizePortrait) / 100f
    /** Sets both sizes to [preset]'s, as the intro's one choice does. */
    fun setLyricsSize(preset: LyricsSize) {
        lyricsSizePortrait = preset.percent
        lyricsSizeLandscape = preset.percent
    }
    private fun legacyLyricsSize(): Int =
        runCatching { LyricsSize.valueOf(prefs.getString("lyricsSize", null)!!).percent }.getOrDefault(100)
    /** Carries over the old "Use System Font" switch. */
    var lyricsFont by enum("lyricsFont", if (read { prefs.getBoolean("systemFont", false) } == true) LyricsFont.System else LyricsFont.Default)
    /** The picked font's file in the app's storage (`LyricsFontFile`), and the name it came with. */
    var customFontFile by string("customFontFile", "")
    var customFontName by string("customFontName", "")
    /** The font gallery's font in use ([FontGallery]), for [LyricsFont.Gallery]. */
    var galleryFont by string("galleryFont", "")
    /** How heavy the lead lines are drawn. */
    var lyricsWeight by enum("lyricsWeight", LyricsWeight.Bold)
    /** The gap between lines, in percent of the usual one. */
    var lineSpacing by int("lineSpacing", 100, 50..250)
    /** Lines sit in the middle of the screen instead of at its edge (a duet keeps its sides). */
    var lyricsCentered by boolean("lyricsCentered", false)
    /** How bright the lines not being sung are, in percent of their usual brightness. */
    var otherLinesBrightness by int("otherLinesBrightness", 100, 40..190)
    /** [com.tx24.spicyplayer.overlay.OverlayColor.Custom]'s colour, as `#RRGGBB`. */
    var customLyricsColor by string("customLyricsColor", "#FFFFFF")
    /** Holding a line copies it. */
    var holdToCopy by boolean("holdToCopy", true)
    /** The media volume in quick settings. */
    var quickVolume by boolean("quickVolume", true)
    /** How much the background is darkened, in percent. */
    var backgroundDim by int("backgroundDim", 0, 0..80)
    /** [BackgroundType.Photo]'s picture, a file in the app's storage ([BackgroundPhoto]). */
    var backgroundPhoto by string("backgroundPhoto", "")
    /** Counts what plays, on this phone only ([com.tx24.spicyplayer.stats.ListeningTracker]). */
    var listeningStats by boolean("listeningStats", false)
    var showScrollToActive by boolean("showScrollToActive", true)
    var pinnedFooter by enum("pinnedFooter", PinnedFooterMode.Off)
    /** A credit's profile opens in the browser (after asking) rather than in a pop-up. */
    var profilesInBrowser by boolean("profilesInBrowser", false)
    var duetLinePadding by boolean("duetLinePadding", true)
    /** A song ending on a duet line sung from the right has its credits there too. */
    var creditsFollowLastLine by boolean("creditsFollowLastLine", false)
    var refinedCredits by boolean("refinedCredits", true)
    /** "Translation from" and "Romanization from" in the credits. */
    var textCredits by boolean("textCredits", true)
    var backgroundPop by boolean("backgroundPop", false)
    var keepOverlappingLines by boolean("keepOverlappingLines", true)
    var syllableMerge by enum("syllableMerge", SyllableMerge.Off)
    /** A merged held word's letters keep each syllable's own pace. */
    var syllablePace by boolean("syllablePace", false)

    var scrollLeadEnabled by boolean("scrollLeadEnabled", false)
    var scrollLeadMs by int("scrollLeadMs", 250, 0..800)
    var smoothScrolling by boolean("smoothScrolling", false)
    /** Unsynced lyrics scroll through at the song's pace, top to bottom over its length. */
    var staticAutoScroll by boolean("staticAutoScroll", true)
    var seekFadeCompensation by boolean("seekFadeCompensation", true)

    var distanceBlur by boolean("distanceBlur", true)
    var glow by boolean("glow", true)
    /** How strong the glow is, in percent: 100 is the desktop amount. The lyrics screen and wallpaper. */
    var glowStrength by int("glowStrength", 100, 50..300)
    /** The Share lyrics button among the floating buttons (or under More). */
    var shareButton by boolean("shareButton", true)
    /** The sleep timer's button among the floating buttons (or under More). */
    var sleepTimerButton by boolean("sleepTimerButton", true)
    /** The lyrics' colour on the lyrics screen, the live wallpaper and the always-on display. */
    internal var lyricsColor by enum("lyricsColor", com.tx24.spicyplayer.overlay.OverlayColor.White)
    /** Drops the costliest effects: the moving background, animated cover, glass blur, and lyric blur and glow. */
    var lowPerformance by boolean("lowPerformance", false)
    /** The playback buttons, the queue and pop-ups animate; off in [lowPerformance] too. */
    var uiAnimations by boolean("uiAnimations", true)
    /** Dragging a settings slider clears the rest of the settings away, so the change shows on the lyrics. */
    var peekWhileAdjusting by boolean("peekWhileAdjusting", true)

    /** Carries over the old Default/Legacy switch. */
    var backgroundType by enum(
        "backgroundType",
        if (read { prefs.getBoolean("legacyBackground", false) } == true) BackgroundType.Legacy else BackgroundType.Default,
    )
    /** How much the image backgrounds ([BackgroundType.image]) are blurred, in dp (0..67). */
    /** The still image backgrounds' blur, in dp. */
    var backgroundBlur by int("backgroundBlur", 0, 0..MAX_BACKGROUND_BLUR)
    /** The moving cover's (Default) blur strength, in percent. */
    var dynamicBackgroundBlur by int("dynamicBackgroundBlur", DEFAULT_DYNAMIC_BLUR, 0..100)
    /** The Legacy background's blur, in dp. */
    var legacyBackgroundBlur by int("legacyBackgroundBlur", LEGACY_BLUR_DP, 0..MAX_BACKGROUND_BLUR)

    /** [BackgroundType.Custom]'s colour, as `#RRGGBB`. */
    var backgroundColor by string("backgroundColor", "#000000")
    /** [backgroundColor] as an opaque ARGB colour, black if it doesn't read. */
    val backgroundColorArgb: Int get() = parseHexColor(backgroundColor) ?: 0xFF000000.toInt()

    /** The blur [SpicySessionBackground] takes for [type]: percent for Default, dp for the rest. */
    fun backgroundBlurFor(type: BackgroundType): Int = when (type) {
        BackgroundType.Default -> dynamicBackgroundBlur
        BackgroundType.Legacy -> legacyBackgroundBlur
        else -> backgroundBlur
    }
    /** The moving background speeds up and slows down with the song's tempo, loudness and beats. */
    var beatReactiveBackground by boolean("beatReactiveBackground", true)
    /** Holds the moving backgrounds (Default, Legacy) still. */
    var staticBackground by boolean("staticBackground", false)
    var releaseYearPosition by enum("releaseYearPosition", ReleaseYearPosition.Off)
    var expandWithoutLyrics by boolean("expandWithoutLyrics", false)
    /** Landscape: the timeline sits under the cover, above the song, rather than with the controls on it. */
    var timelineOutsideCover by boolean("timelineOutsideCover", true)
    /** The record's looping Apple Music cover in place of the still one, where it has one. */
    var animatedCover by boolean("animatedCover", true)
    /**
     * Landscape, beside the lyrics: what may fill its side of the screen, with the song docked
     * along its bottom. A song gets the first one it has; nothing picked is no stage. Unset, it
     * follows the `coverStage` switch it replaced, if that was ever flipped, else [StageArt.DEFAULT].
     */
    var stageArt by setting("stageArts", String::class, {
        read { prefs.getString("stageArts", null) }?.let(StageArt::parse)
            ?: when (read { prefs.getBoolean("coverStage", true) }.takeIf { prefs.contains("coverStage") }) {
                true -> StageArt.COVER_STAGE_ON
                false -> emptySet()
                null -> StageArt.DEFAULT
            }
    }) { prefs.edit().putString("stageArts", StageArt.store(it)).apply() }
    /** Where the song has a Spotify Canvas, the background is that Canvas, blurred, over [backgroundType]. */
    var canvasBackground by boolean("canvasBackground", false)
    /** How blurred the Canvas background is, in percent: 100 is the soft glow, 0 the video itself. */
    var canvasBlur by int("canvasBlur", 100, 0..100)
    /**
     * Portrait, while the big cover is open: what takes its place. A Canvas fills the screen; the
     * animated or still cover spreads edge to edge and melts into the background. A song gets the
     * first one it has; nothing picked is the big cover as it is. Unset, it follows the
     * `bigCoverCanvas` switch it replaced, if that was ever flipped off, else [StageArt.DEFAULT].
     */
    var bigCoverArt by setting("bigCoverArts", String::class, {
        read { prefs.getString("bigCoverArts", null) }?.let(StageArt::parse)
            ?: if (prefs.contains("bigCoverCanvas") && read { prefs.getBoolean("bigCoverCanvas", true) } == false) emptySet()
            else StageArt.DEFAULT
    }) { prefs.edit().putString("bigCoverArts", StageArt.store(it)).apply() }
    /** The big cover shows a song's animated cover before its Canvas, which fills in for songs without one. */
    var bigCoverAnimatedFirst by boolean("bigCoverAnimatedFirst", false)
    /** While the stage or the big cover shows a Canvas, the background is that Canvas, blurred. */
    var canvasBackdrop by boolean("canvasBackdrop", true)
    /** The loops that hold their frame while the music is paused; both kinds by default. */
    var pauseWithMusic by setting("pauseWithMusic", String::class, {
        read { prefs.getString("pauseWithMusic", null) }?.let(LoopKind::parse) ?: LoopKind.entries.toSet()
    }) { prefs.edit().putString("pauseWithMusic", LoopKind.store(it)).apply() }
    /** Cover Art background: the record's looping Apple Music cover in place of the still one. */
    var animatedBackground by boolean("animatedBackground", false)
    /**
     * Background changes crossfade in place (the type too), image backgrounds keep their edges, and
     * Cover Colors uses the cover's two deepest shades.
     */
    var refinedBackground by boolean("refinedBackground", true)
    /** Default background: follows the record's animated cover while the header plays one ([animatedCover]). */
    var backgroundFollowsMotion by boolean("backgroundFollowsMotion", true)

    var keepScreenOn by boolean("keepScreenOn", true)
    /** The lyrics screen shows over the lock screen; anything opened over the lyrics asks to unlock first. */
    var showOverLockScreen by boolean("showOverLockScreen", false)
    /** Left untouched while the music plays, the lyrics screen dims to the lyrics alone on black. */
    var ambientMode by boolean("ambientMode", false)
    /** How long the lyrics screen waits after the last touch before [ambientMode] dims it. */
    var ambientDelay by enum("ambientDelay", AmbientDelay.Default)
    /** The lyrics screen as the always-on display: it stays on over the lock screen and dims to the lyrics at the lowest brightness, with or without [ambientMode], playing or paused. */
    var ambientAsAod by boolean("ambientAsAod", false)
    /** The always-on display's screen brightness, in percent (1 is the lowest the screen allows). */
    var aodBrightness by int("aodBrightness", 1, 1..100)
    /** The song's Canvas behind the always-on display, blurred and darkened. */
    var aodCanvas by boolean("aodCanvas", false)
    /** Play/pause, previous and next on the always-on display. */
    var aodControls by boolean("aodControls", false)
    /** Which of the phone's bars stay on screen. */
    var systemBars by enum("systemBars", SystemBarsMode.HideBoth)
    /** Leaving the app with a song playing shrinks the lyrics into a small floating window. */
    var autoPip by boolean("autoPip", true)
    /** The floating window's shape; [pipCustomRatio] for [PipShape.Custom]. */
    var pipShape by enum("pipShape", PipShape.Wide)
    /** A custom window's width over its height, times 100 (Android allows 1:2.39 to 2.39:1). */
    var pipCustomRatio by int("pipCustomRatio", 100, PIP_RATIO_RANGE)
    /** The floating window shows the lyrics; off, it shows the song's cover, title and artist. */
    var pipShowLyrics by boolean("pipShowLyrics", true)
    /** The song bar (title, artist, timeline) along the bottom of a square or tall window. */
    var pipDock by boolean("pipDock", true)
    /** The floating window's line being sung sits at its top instead of its middle, with no lines above it. */
    var pipLineAtTop by boolean("pipLineAtTop", false)
    /** Which delays quick settings shows; the song's and the output's by default, as before the player's. */
    var quickDelays by setting("quickDelays", String::class, {
        read { prefs.getString("quickDelays", null) }?.let(DelayKind::parse) ?: setOf(DelayKind.Song, DelayKind.Output)
    }) { prefs.edit().putString("quickDelays", DelayKind.store(it)).apply() }
    /** A small cover beside the title in a square or tall window's song bar. */
    var pipDockCover by boolean("pipDockCover", true)
    /** What the floating window shows for a song without lyrics. */
    var pipWithoutLyrics by enum("pipWithoutLyrics", PipWithoutLyrics.Message)
    /**
     * The floating window's lyric text size, in percent, on top of its own fit to the window. Held
     * apart from the full screen's, so big lyrics there don't leave one word per row in the window.
     */
    var pipLyricsSize by int("pipLyricsSize", 100, LYRICS_SIZE_RANGE)

    /** The floating window's width over its height. */
    val pipAspect: Float
        get() = if (pipShape == PipShape.Custom) pipCustomRatio / 100f else pipShape.width.toFloat() / pipShape.height

    /** The floating button that turns the screen to landscape, rotation lock or not. */
    var rotateButton by boolean("rotateButton", true)
    /**
     * A floating button that shrinks the lyrics into that window by hand. Off by default where
     * Android 12+ enters the window on its own; before that the button is the easy way in.
     */
    var pipButton by boolean("pipButton", Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    /** No song header: the lyrics fill the page and scroll with the active line near the centre. */
    var hideHeader by boolean("hideHeader", false)
    var headerSize by enum("headerSize", HeaderSize.Large)
    /** Which side the now-playing panel sits on in landscape; the floating swap button, not a settings row. */
    var panelSide by enum("panelSide", PanelSide.Left)
    /**
     * The screen's full refresh rate (90/120 Hz) instead of 60 Hz. On by default in Plus: on a
     * 120 Hz screen the lyrics at 60 Hz look choppy beside everything else on the phone. Costs
     * some battery; the battery saver can cap it ([com.tx24.spicyplayer.power.SaverRule.HighRefreshRate]).
     */
    var highRefreshRate by boolean("highRefreshRate", true)
    var autoHideControls by boolean("autoHideControls", true)
    var controlsHideDelay by enum("controlsHideDelay", ControlsHideDelay.Default)
    /** The controls fade while paused too, not only while playing. */
    var hideControlsWhilePaused by boolean("hideControlsWhilePaused", false)
    /** Ticks and clicks on buttons, toggles, sliders and gestures (they also follow the system's touch-feedback setting). */
    var touchHaptics by boolean("touchHaptics", true)
    /** Vibrates along with the song's beats while the lyrics are on screen. */
    var musicHaptics by boolean("musicHaptics", false)
    /** How strong the music haptics are, in percent of their own strength (25..400; over 100 layers heavier vibrations on). */
    var musicHapticsStrength by int("musicHapticsStrength", 100, 25..200)
    var musicHapticsStyle by enum("musicHapticsStyle", MusicHapticsStyle.Drums)
    /** The music haptics keep vibrating once the app is not in front (a foreground service). */
    var backgroundMusicHaptics by boolean("backgroundMusicHaptics", true)
    /** HapticBeat's style: which pulses fire (Bass, Beat, Drums, Full mix, Apple-style, ...). */
    var hapticBeatMode by enum("hapticBeatMode", HapticBeatMode.Full)
    /** HapticBeat's engine: how a pulse is rendered on the motor. */
    var hapticBeatEngine by enum("hapticBeatEngine", HapticBeatEngine.Auto)
    /** HapticBeat's timing: which streams the haptics come from (analysis, lyrics, or fused). */
    var hapticBeatTiming by enum("hapticBeatTiming", HapticBeatTiming.Auto)
    /** HapticBeat's detection: how the score's pulses are gated and fused. */
    var hapticBeatDetection by enum("hapticBeatDetection", HapticBeatDetection.Auto)
    /** HapticBeat's sensitivity, 0..100: higher lets quieter pulses through. */
    var hapticBeatSensitivity by int("hapticBeatSensitivity", 50, 0..100)
    /** HapticBeat's sync offset, in ms: fires the haptics this much earlier, against report and motor delay. */
    var hapticBeatRemoteOffset by int("hapticBeatRemoteOffset", 150, -600..600)
    /** HapticBeat's vocals-only: in Apple-style, only lyric and word pulses fire. */
    var hapticBeatVocalsOnly by boolean("hapticBeatVocalsOnly", false)
    /** The live wallpaper draws the word-synced lyrics over the moving picture. */
    var wallpaperShowLyrics by boolean("wallpaperShowLyrics", true)
    /** The wallpaper's cover: its size, or hidden. */
    var wallpaperCover by enum("wallpaperCover", WallpaperCover.Large)
    /** The wallpaper's lyric text size, in percent of the lyrics screen's. */
    var wallpaperLyricsSize by int("wallpaperLyricsSize", 100, 60..160)
    /** How dark the veil over the wallpaper's picture is, in percent of the usual one. */
    var wallpaperDim by int("wallpaperDim", 100, 0..200)
    /** When the wallpaper saves power, and what it cuts then. */
    internal var wallpaperPowerSaving by enum("wallpaperPowerSaving", com.tx24.spicyplayer.wallpaper.PowerSaving.BatterySaver)
    var wallpaperSaveNoCanvas by boolean("wallpaperSaveNoCanvas", true)
    var wallpaperSaveNoAnimatedCover by boolean("wallpaperSaveNoAnimatedCover", true)
    var wallpaperSaveSimpler by boolean("wallpaperSaveSimpler", true)
    var wallpaperSaveLineByLine by boolean("wallpaperSaveLineByLine", false)
    var wallpaperSaveStopAll by boolean("wallpaperSaveStopAll", false)
    var wallpaperSaveNoHaptics by boolean("wallpaperSaveNoHaptics", false)
    /** What the phone's battery saver turns off while it's on ([com.tx24.spicyplayer.power.SaverRule]). */
    var saverStopsAlwaysOn by boolean("saverStopsAlwaysOn", true)
    var saverStopsOverlay by boolean("saverStopsOverlay", true)
    var saverStopsNotification by boolean("saverStopsNotification", true)
    var saverStopsBackgroundHaptics by boolean("saverStopsBackgroundHaptics", true)
    var saverStopsKeepScreenOn by boolean("saverStopsKeepScreenOn", true)
    var saverStopsHighRefreshRate by boolean("saverStopsHighRefreshRate", true)
    var saverLowPerformance by boolean("saverLowPerformance", false)
    var saverStopsListening by boolean("saverStopsListening", true)
    /** Hears the phone's playback to place the lyrics ([com.tx24.spicyplayer.playback.ListeningSync]); needs record-audio. */
    var listeningSync by boolean("listeningSync", false)
    /** Spotify's Automix: resync a moment into each new song ([com.tx24.spicyplayer.playback.AutomixResync]). */
    var resyncAfterSongChange by boolean("resyncAfterSongChange", true)
    /** The player's crossfade, in seconds: a song that follows another on its own starts this far in. */
    var crossfadeSeconds by int("crossfadeSeconds", 0, 0..12)
    /** The line being sung in an ongoing notification, for islands and Live Updates. */
    var lyricsNotification by boolean("lyricsNotification", false)
    /** The lyrics floating over other apps while music plays. */
    var lyricsOverlay by boolean("overlayLyrics", false)
    var overlayHideWhenPaused by boolean("overlayHideWhenPaused", true)
    var overlayLocked by boolean("overlayLocked", false)
    var overlayClickThrough by boolean("overlayClickThrough", false)
    var overlayBackdrop by boolean("overlayBackdrop", true)
    internal var overlaySize by enum("overlaySize", com.tx24.spicyplayer.overlay.OverlaySize.Medium)
    var overlayWidth by int("overlayWidth", 94, 45..100)
    internal var overlayStyle by enum("overlayStyle", com.tx24.spicyplayer.overlay.OverlayStyle.Full)
    internal var overlayColor by enum("overlayColor", com.tx24.spicyplayer.overlay.OverlayColor.White)
    var overlayGlow by boolean("overlayGlow", true)
    var overlayGlowStrength by int("overlayGlowStrength", 100, 50..300)
    /** How bright the overlay's lyrics are drawn, in percent: 100 as the renderer draws them. */
    var overlayBrightness by int("overlayBrightness", 100, 100..200)
    var overlayLyricsOpacity by int("overlayLyricsOpacity", 100, 10..100)
    var overlayBackdropOpacity by int("overlayBackdropOpacity", 45, 0..100)
    var overlayHideInLandscape by boolean("overlayHideInLandscape", false)
    /** The overlay steps aside while the Spicy live wallpaper shows (the home screen). */
    var overlayHideOnWallpaper by boolean("overlayHideOnWallpaper", true)
    /** Hide the status bar clock while the status bar lyrics show (needs Shizuku once). */
    var overlayHideClock by boolean("overlayHideClock", false)
    /** A floating button that opens the quick settings (the song's and the output's delay). */
    var quickSettingsButton by boolean("quickSettingsButton", true)
    /**
     * A floating button that opens the Lyrics Manager. Off by default: it's always in quick
     * settings and Settings → This song, and few open it often.
     */
    var lyricsManagerButton by boolean("lyricsManagerButton", false)
    /** A floating button that opens the queue (it's always in quick settings); shown while the player shares one. */
    var queueButton by boolean("queueButton", false)
    /** A floating button that opens the song's lyrics source choice (it's always in quick settings). */
    var sourceButton by boolean("sourceButton", false)
    /** The player's own extra actions (like, shuffle modes, …) as floating buttons. */
    var playerButtons by boolean("playerButtons", false)
    var romanizeButton by boolean("romanizeButton", true)
    var translateButton by boolean("translateButton", true)
    var resyncButton by boolean("resyncButton", true)
    var expandButton by boolean("expandButton", true)
    /** How the Lyrics Manager's last upload was applied; the upload screen reopens on it. */
    var ttmlUploadSaves by boolean("ttmlUploadSaves", true)

    /** How Settings and the pop-ups are dressed, and which launcher icon shows (`AppIcon.apply`). */
    var settingsTheme by enum("settingsTheme", ChromeTheme.Purple)
    var popupTheme by enum("popupTheme", ChromeTheme.Purple)
    var appIcon by enum("appIcon", AppIcon.Purple)

    /** Anonymous usage stats (`UsageStats`); nothing is sent until the notice has been answered. */
    var usageStats by boolean(UsageStats.KEY_ENABLED, true)
    var usageStatsAsked by boolean(UsageStats.KEY_ASKED, false)
    /** The first-run introduction has been gone through (it only shows on a new install). */
    var introDone by boolean("introDone", false)
    /** Asked for again from Settings, on an install that wouldn't show it by itself. Not saved. */
    var introReplay by mutableStateOf(false)

    /** Pre-releases offered as updates. On by default while the app itself is one (0.x). */
    var includePrereleases by boolean("includePrereleases", BuildConfig.VERSION_NAME.startsWith("0."))

    /** The word motion used when [originalWordMotion] is off (`RenderConfig.wordMotionBoost`). */
    val wordMotionBoost get() = if (originalWordMotion) 1f else WORD_MOTION_BOOST

    private fun boolean(key: String, default: Boolean) =
        setting(key, Boolean::class, { read { prefs.getBoolean(key, default) } ?: default }) { prefs.edit().putBoolean(key, it).apply() }

    private fun string(key: String, default: String) =
        setting(key, String::class, { read { prefs.getString(key, default) } ?: default }) { prefs.edit().putString(key, it).apply() }

    private fun int(key: String, default: Int, range: IntRange) =
        setting(key, Int::class, { (read { prefs.getInt(key, default) } ?: default).coerceIn(range) }) {
            prefs.edit().putInt(key, it).apply()
        }

    private inline fun <reified E : Enum<E>> enum(key: String, default: E) =
        setting(key, String::class, { runCatching { enumValueOf<E>(prefs.getString(key, null)!!) }.getOrDefault(default) }) {
            prefs.edit().putString(key, it.name).apply()
        }

    /** A value stored as another type (an old or hand-edited backup) reads as unset, not a crash. */
    private inline fun <T> read(get: () -> T): T? = try { get() } catch (_: ClassCastException) { null }

    private fun <T> setting(key: String, type: KClass<*>, load: () -> T, save: (T) -> Unit) =
        Setting(load, save).also { all += it; types[key] = type }

    private class Setting<T>(private val load: () -> T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
        private var value by mutableStateOf(load())
        fun reload() { value = load() }
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
            save(value)
        }
    }

    companion object {
        /** Sung words grow and lift this much more than the desktop amount, which reads better on a phone. */
        const val WORD_MOTION_BOOST = 1.25f
    }
}

/**
 * What fills the screen behind the lyrics: the moving cover ([Default], [Legacy]), a still image
 * ([Auto] and [ArtistHeader]: the artist's Spotify header, else the cover; [CoverArt]), or the
 * cover's colours ([Color]).
 */
enum class BackgroundType(val label: String) {
    Default("Default"),
    Legacy("Legacy"),
    Auto("Auto"),
    ArtistHeader("Artist Header"),
    CoverArt("Cover Art"),
    Color("Color"),
    /** One colour of the user's choosing, [AppSettings.backgroundColor]. */
    Custom("Custom Color"),
    /** A picture of the user's own, [AppSettings.backgroundPhoto]. */
    Photo("Your Photo");

    /** A still picture, which the blur setting softens. */
    val image get() = this == Auto || this == ArtistHeader || this == CoverArt || this == Photo
    val usesArtistHeader get() = this == Auto || this == ArtistHeader
    /** Moves on its own (and can be held still). */
    val moving get() = this == Default || this == Legacy
}

/** `RRGGBB`, with or without a leading `#`, as an opaque ARGB colour; null for anything else. */
fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return (0xFF000000 or hex.toLong(16)).toInt()
}

/** The live wallpaper's cover: its width as a share of the screen's short side, or none. */
enum class WallpaperCover(val label: String, val fraction: Float) {
    Large("Large", 0.65f),
    Medium("Medium", 0.5f),
    Small("Small", 0.36f),
    Hidden("Hidden", 0f),
}

/** The strongest [AppSettings.backgroundBlur]. */
const val MAX_BACKGROUND_BLUR = 67

/** The moving cover's blur by default: the reference's. */
const val DEFAULT_DYNAMIC_BLUR = 60

/** How long the controls stay after the last touch before they fade. */
/** The floating window's shapes, as width:height; [Custom] takes `AppSettings.pipCustomRatio`. */
enum class PipShape(val label: String, val width: Int, val height: Int) {
    Wide("Wide (16:9)", 16, 9),
    Classic("Classic (4:3)", 4, 3),
    Square("Square (1:1)", 1, 1),
    Tall("Tall (3:4)", 3, 4),
    Custom("Custom", 1, 1),
}

/** What the floating window shows for a song without lyrics: the notice, or the song's card. */
enum class PipWithoutLyrics(val label: String) {
    Message("Message"),
    SongCard("Song card"),
}

/** `AppSettings.pipCustomRatio`'s range: Android's 1:2.39 to 2.39:1, times 100. */
val PIP_RATIO_RANGE = 42..239

enum class ControlsHideDelay(val label: String, val millis: Long) {
    Short("2 seconds", 2_000L),
    Default("3 seconds", 3_000L),
    Long("5 seconds", 5_000L),
    Longer("10 seconds", 10_000L),
}

enum class AmbientDelay(val label: String, val millis: Long) {
    Shortest("15 seconds", 15_000L),
    Default("30 seconds", 30_000L),
    Minute("1 minute", 60_000L),
    TwoMinutes("2 minutes", 120_000L),
    FiveMinutes("5 minutes", 300_000L),
}

/** Where the song's release year shows beside the artists, if at all. */
enum class PanelSide { Left, Right }

enum class ReleaseYearPosition(val label: String) { Off("Off"), Left("Left"), Right("Right") }

/** How the lyrics look and move: Spicy Lyrics' own, or like Apple Music's. */
enum class LyricsStyle(val label: String) {
    Spicy("Spicy Lyrics"),
    AppleMusic("Apple Music"),
}

/** Lyric text sizes to pick from in one step, against the default, which follows the screen width. */
enum class LyricsSize(val percent: Int, val label: String) {
    Small(85, "Small"),
    Default(100, "Default"),
    Large(115, "Large"),
    Larger(130, "Larger"),
    Huge(160, "Huge"),
    ;

    companion object {
        /** The one nearest [percent]. */
        fun nearest(percent: Int): LyricsSize = entries.minBy { kotlin.math.abs(it.percent - percent) }
    }
}

/** How far the lyrics' size goes either way, in percent of the default. */
val LYRICS_SIZE_RANGE = 60..250

/** The lyric delays, in the order quick settings lists them. */
enum class DelayKind(val label: String) {
    Song("Song"),
    Player("Player"),
    Output("Output");

    companion object {
        /** Stored as names joined by commas; an unknown name is dropped. */
        fun parse(stored: String): Set<DelayKind> =
            stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()

        fun store(kinds: Set<DelayKind>): String = entries.filter { it in kinds }.joinToString(",") { it.name }
    }
}
