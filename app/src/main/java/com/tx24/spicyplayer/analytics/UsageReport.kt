package com.tx24.spicyplayer.analytics

import com.tx24.spicyplayer.ui.nowplaying.LoopKind
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import com.tx24.spicyplayer.ui.settings.AppSettings

/** How often the settings snapshot and the usage counts go out. */
const val DAILY_INTERVAL_MS = 24 * 60 * 60 * 1000L

fun dailyDue(lastSentMs: Long, nowMs: Long): Boolean =
    lastSentMs <= 0L || nowMs - lastSentMs >= DAILY_INTERVAL_MS || nowMs < lastSentMs

/** Counter names, kept in one place so the README's list stays true. */
object UsageCounter {
    const val SONGS = "songs"
    const val SETTINGS = "settings_opened"
    const val QUICK_SETTINGS = "quick_settings_opened"
    const val LYRICS_MANAGER = "lyrics_manager_opened"
    const val SPOTIFY_SEARCH = "spotify_search_opened"
    const val ROMANIZE = "romanize_toggled"
    const val TRANSLATE = "translate_toggled"
    const val RESYNC = "resync"
    const val CALIBRATION = "delay_calibration"
    const val BACKUP_EXPORT = "backup_exported"
    const val BACKUP_RESTORE = "backup_restored"
    const val QUEUE = "queue_opened"
    /** The floating expand button only; a tap on the cover isn't counted. */
    const val EXPAND_BUTTON = "expand_button_tapped"
    const val PLAYER_ACTION = "player_action_tapped"
    /** lrc.red's model asked to word-sync a song from the source picker. */
    const val MODEL_SYNC = "model_sync_requested"

    fun lyrics(outcome: String) = "lyrics_$outcome"
    fun page(name: String) = "page_${slug(name)}"
    fun player(packageName: String) = "$PLAYER$packageName"
    fun source(provider: String) = "$SOURCE$provider"

    const val PLAYER = "player:"
    const val SOURCE = "source:"
}

/** The lyrics a song ended up with, as counted: word-synced, line-synced, plain, or none. */
fun lyricsOutcome(type: com.tx24.spicyplayer.lyrics.spicy.models.LyricsType?): String = when (type) {
    com.tx24.spicyplayer.lyrics.spicy.models.LyricsType.Syllable -> "word"
    com.tx24.spicyplayer.lyrics.spicy.models.LyricsType.Line -> "line"
    com.tx24.spicyplayer.lyrics.spicy.models.LyricsType.Static -> "plain"
    null -> "none"
}

/**
 * The day's counts as event data: plain counters as they are, the player and source tallies as
 * the most used one plus a count per source (the top few), so a dashboard can show hit rates.
 */
fun dailyUsageData(counts: Map<String, Int>): Map<String, Any> {
    val data = linkedMapOf<String, Any>()
    counts.filter { (key, value) -> value > 0 && ':' !in key }.toSortedMap().forEach { (key, value) -> data[key] = value }
    val players = tally(counts, UsageCounter.PLAYER)
    players.maxByOrNull { it.value }?.let { data["top_player"] = it.key }
    if (players.isNotEmpty()) data["players"] = players.size
    val sources = tally(counts, UsageCounter.SOURCE)
    sources.maxByOrNull { it.value }?.let { data["top_source"] = it.key }
    sources.entries.sortedByDescending { it.value }.take(MAX_SOURCES).forEach { (name, value) ->
        data["source_${slug(name)}"] = value
    }
    return data
}

/** Which settings are in use, by name and value; never a typed-in value (font names, keys). */
fun configSnapshot(settings: AppSettings): Map<String, Any> = with(settings) {
    mapOf(
        "lyrics_style" to lyricsStyle.name,
        "simple_mode" to simpleLyricsMode,
        "refreshed_simple_lyrics" to refreshedSimpleLyrics,
        "minimal_mode" to minimalLyricsMode,
        "original_word_motion" to originalWordMotion,
        "lyrics_size" to lyricsSizePortrait,
        "lyrics_size_landscape" to lyricsSizeLandscape,
        // Default, System or Custom; a custom font's own name stays on the phone.
        "font" to lyricsFont.name,
        "romanize" to romanize,
        "romanization_mode" to romanizationMode.name,
        "duet_line_padding" to duetLinePadding,
        "ttml_upload_saves" to ttmlUploadSaves,
        "translation_mode" to translationMode.name,
        "scroll_to_active" to showScrollToActive,
        "pinned_footer" to pinnedFooter.name,
        "profiles_in_browser" to profilesInBrowser,
        "syllable_merge" to syllableMerge.name,
        "syllable_pace" to syllablePace,
        "scroll_lead" to scrollLeadEnabled,
        "scroll_lead_ms" to scrollLeadMs,
        "seek_fade_compensation" to seekFadeCompensation,
        "smooth_scrolling" to smoothScrolling,
        "static_auto_scroll" to staticAutoScroll,
        "distance_blur" to distanceBlur,
        "glow" to glow,
        "low_performance" to lowPerformance,
        "ui_animations" to uiAnimations,
        "peek_while_adjusting" to peekWhileAdjusting,
        "credits_follow_last_line" to creditsFollowLastLine,
        "rotate_button" to rotateButton,
        "background" to backgroundType.name,
        "background_blur" to backgroundBlur,
        "dynamic_background_blur" to dynamicBackgroundBlur,
        "legacy_background_blur" to legacyBackgroundBlur,
        "beat_reactive_background" to beatReactiveBackground,
        "static_background" to staticBackground,
        "release_year" to releaseYearPosition.name,
        "animated_cover" to animatedCover,
        "cover_stage" to stageArt.isNotEmpty(),
        "stage_art" to StageArt.store(stageArt),
        "canvas_background" to canvasBackground,
        "big_cover_canvas" to (StageArt.Canvas in bigCoverArt),
        "big_cover_art" to StageArt.store(bigCoverArt),
        "big_cover_animated_first" to bigCoverAnimatedFirst,
        "canvas_backdrop" to canvasBackdrop,
        "pause_with_music" to LoopKind.store(pauseWithMusic),
        "timeline_outside_cover" to timelineOutsideCover,
        "expand_without_lyrics" to expandWithoutLyrics,
        "animated_background" to animatedBackground,
        "refined_background" to refinedBackground,
        "background_follows_motion" to backgroundFollowsMotion,
        "keep_screen_on" to keepScreenOn,
        "show_over_lock_screen" to showOverLockScreen,
        "ambient_mode" to ambientMode,
        "ambient_delay" to ambientDelay.name,
        "auto_pip" to autoPip,
        "pip_shape" to pipShape.name,
        "pip_custom_ratio" to pipCustomRatio,
        "pip_show_lyrics" to pipShowLyrics,
        "pip_dock" to pipDock,
        "pip_dock_cover" to pipDockCover,
        "pip_without_lyrics" to pipWithoutLyrics.name,
        "pip_lyrics_size" to pipLyricsSize,
        "pip_button" to pipButton,
        "hide_header" to hideHeader,
        "header_size" to headerSize.name,
        "panel_side" to panelSide.name,
        "high_refresh_rate" to highRefreshRate,
        "auto_hide_controls" to autoHideControls,
        "controls_hide_delay" to controlsHideDelay.name,
        "hide_controls_while_paused" to hideControlsWhilePaused,
        "system_bars" to systemBars.name,
        "touch_haptics" to touchHaptics,
        "music_haptics" to musicHaptics,
        "music_haptics_style" to musicHapticsStyle.name,
        "music_haptics_strength" to musicHapticsStrength,
        "quick_settings_button" to quickSettingsButton,
        "lyrics_manager_button" to lyricsManagerButton,
        "queue_button" to queueButton,
        "source_button" to sourceButton,
        "player_buttons" to playerButtons,
        "romanize_button" to romanizeButton,
        "translate_button" to translateButton,
        "resync_button" to resyncButton,
        "expand_button" to expandButton,
        "prereleases" to includePrereleases,
        "settings_theme" to settingsTheme.name,
        "popup_theme" to popupTheme.name,
        "app_icon" to appIcon.name,
    )
}

private const val MAX_SOURCES = 6

private fun tally(counts: Map<String, Int>, prefix: String): Map<String, Int> =
    counts.filter { (key, value) -> key.startsWith(prefix) && value > 0 }.mapKeys { it.key.removePrefix(prefix) }

private fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
