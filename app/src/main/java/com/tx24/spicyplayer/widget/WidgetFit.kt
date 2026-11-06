package com.tx24.spicyplayer.widget

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What of a widget's [WidgetSettings] fits its height, so a short widget drops its extras instead
 * of clipping the line being sung. Only the fixed chrome is fitted here; the painter measures
 * the actual lyrics and fades surrounding lines at the edges. [currentLines] is how many
 * lines the current one may wrap to, 1 to 3.
 *
 * The heights are estimates in dp from the text sizes, a little generous so nothing clips.
 * Plain Kotlin, no Android.
 */
internal data class WidgetFit(val settings: WidgetSettings, val currentLines: Int) {
    companion object {
        /** The widget's padding, both sides together. */
        const val PADDING_DP = 32
        private const val SONG_ROW_DP = 54f
        private const val TIMELINE_DP = 34f
        /** A line of text's height per sp of its size. */
        private const val LINE_HEIGHT = 1.32f
        private const val MAX_CURRENT_LINES = 3

        /**
         * [settings] fitted to [heightDp] (none known: as they are). The result must not depend
         * on the current lyric: otherwise a translation or backing vocal hides the timeline.
         */
        fun of(settings: WidgetSettings, heightDp: Int?): WidgetFit {
            if (heightDp == null || heightDp <= 0) return WidgetFit(settings, MAX_CURRENT_LINES)
            val steps = listOf(
                settings,
                settings.copy(progress = false, credit = false),
                settings.copy(progress = false, credit = false, songInfo = WidgetSettings.SongInfo.HIDDEN, controls = WidgetSettings.Controls.NONE),
            ).distinct()
            for (step in steps) {
                val room = heightDp - fixedDp(step)
                val lines = floor(room / currentLineDp(step)).toInt()
                if (lines >= 1 || step === steps.last()) return WidgetFit(step, lines.coerceIn(1, MAX_CURRENT_LINES))
            }
            return WidgetFit(steps.last(), 1)
        }

        /**
         * A launcher's reported [widthDp] by [heightDp] in dp. Some report pixels instead: a widget
         * far bigger than the screen ([screenWidthDp] by [screenHeightDp]) is read as pixels and
         * scaled by [density], or the lyrics would be drawn for that size and shrunk to fit. One a
         * little past the screen is dp from a launcher rounding up: it stops at the screen, as
         * read as pixels it would be drawn a few words wide.
         */
        fun reportedDp(widthDp: Int?, heightDp: Int?, screenWidthDp: Int, screenHeightDp: Int, density: Float): Pair<Int?, Int?> {
            // A screen too small to be one can't tell pixels from dp, so the size is left as reported.
            if (minOf(screenWidthDp, screenHeightDp) < MIN_SCREEN_DP) return widthDp to heightDp
            val tooBig = (widthDp ?: 0) > screenWidthDp * PIXELS_PAST_SCREEN || (heightDp ?: 0) > screenHeightDp * PIXELS_PAST_SCREEN
            if (tooBig && density > 1f) return widthDp?.let { (it / density).toInt() } to heightDp?.let { (it / density).toInt() }
            return widthDp?.coerceAtMost(screenWidthDp) to heightDp?.coerceAtMost(screenHeightDp)
        }

        /**
         * A size from a launcher that works in dp at the display's default [stableDpi] while the app
         * runs at a lower [densityDpi] (the user made the display size smaller): taken back to the
         * app's dp. One UI Home does this; it reports a widget a third too big there. At a larger
         * display size the same sum would make the widget wider than the screen, so it's left alone.
         */
        fun launcherDp(widthDp: Int?, heightDp: Int?, densityDpi: Int, stableDpi: Int): Pair<Int?, Int?> {
            if (densityDpi <= 0 || stableDpi <= 0 || densityDpi >= stableDpi) return widthDp to heightDp
            val ratio = densityDpi.toFloat() / stableDpi
            return widthDp?.let { (it * ratio).roundToInt() } to heightDp?.let { (it * ratio).roundToInt() }
        }

        /**
         * The screen in dp: the display's [realDp], or the app's [configDp] when the display's
         * answer is too small to be a screen. Taken as the screen, such an answer would make every
         * widget read as pixels and its lyrics be drawn a few words wide.
         */
        fun screenDp(realDp: Pair<Int, Int>, configDp: Pair<Int, Int>): Pair<Int, Int> =
            if (minOf(realDp.first, realDp.second) >= MIN_SCREEN_DP) realDp else configDp

        /** How many screens big a reported size must be before it's taken for pixels. */
        private const val PIXELS_PAST_SCREEN = 1.25f
        /** The narrowest a screen can be, in dp; Android needs 320 of any phone. */
        private const val MIN_SCREEN_DP = 240

        /** Without a known height, the lyrics area is drawn this tall. */
        private const val DEFAULT_LINES_DP = 180f

        /** How tall the lyrics area is in a widget [heightDp] tall laid out by [settings]. */
        fun linesAreaDp(settings: WidgetSettings, heightDp: Int?): Float {
            if (heightDp == null || heightDp <= 0) return DEFAULT_LINES_DP
            val songRow = if (settings.songInfo != WidgetSettings.SongInfo.HIDDEN || settings.controls != WidgetSettings.Controls.NONE) SONG_ROW_DP else 0f
            val timeline = if (settings.progress || settings.credit) TIMELINE_DP else 0f
            return (heightDp - PADDING_DP - songRow - timeline).coerceAtLeast(currentLineDp(settings))
        }

        private fun currentLineDp(settings: WidgetSettings) = settings.textSize.currentSp * LINE_HEIGHT

        /** Everything but the current line's own text. */
        private fun fixedDp(settings: WidgetSettings): Float {
            val songRow = if (settings.songInfo != WidgetSettings.SongInfo.HIDDEN || settings.controls != WidgetSettings.Controls.NONE) SONG_ROW_DP else 0f
            val timeline = if (settings.progress || settings.credit) TIMELINE_DP else 0f
            return PADDING_DP + songRow + timeline
        }
    }
}
