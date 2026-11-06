package com.tx24.spicyplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.background.CoverColors

/**
 * The home screen widget, laid out like the floating window: the line being sung with the lines
 * around it, the song under them, a progress bar and the lyrics' credit, over the cover. What
 * shows and how is set per widget ([WidgetSettings]). [NowPlayingHolder] keeps it current.
 */
class LyricsWidget : AppWidgetProvider() {
    internal data class Content(
        val title: String,
        val artist: String,
        val lines: WidgetLines,
        val playing: Boolean,
        /** The player's position, for the progress bar and the time (lyric delays aside). */
        val positionMs: Long,
        val durationMs: Long,
        /** Where the lyrics are from, as the controls and the floating window say it ([LyricsFooter.miniCredit]). */
        val credit: String?,
        /** The cover, small and rounded, for the song row. */
        val cover: Bitmap?,
        /** A still frame of the app's background for this cover ([KawarpSnapshot]). */
        val backdrop: Bitmap?,
        val colors: CoverColors?,
        /** The song has a duet, so its lines lean to their singer's side. */
        val duet: Boolean = false,
    )

    override fun onEnabled(context: Context) = NowPlayingHolder.start(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = NowPlayingHolder.start(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = WidgetSettingsStore(context).forget(appWidgetIds)

    /** Resized: laid out again for its new height. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        NowPlayingHolder.start(context)

    override fun onDisabled(context: Context) = NowPlayingHolder.stop()

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_PLAY_PAUSE -> NowPlayingHolder::playPause
            ACTION_PREVIOUS -> NowPlayingHolder::skipPrevious
            ACTION_NEXT -> NowPlayingHolder::skipNext
            else -> return super.onReceive(context, intent)
        }
        NowPlayingHolder.start(context)
        action()
    }

    internal companion object {
        private const val ACTION_PLAY_PAUSE = "com.tx24.spicyplayer.widget.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.tx24.spicyplayer.widget.PREVIOUS"
        private const val ACTION_NEXT = "com.tx24.spicyplayer.widget.NEXT"
        /** The background without a cover: the app's near-black. */
        const val DEFAULT_ACCENT = 0xFF121212.toInt()
        private const val PROGRESS_MAX = 1_000

        /** Without a reported size (the settings preview), the lyrics area is drawn for a widget this wide. */
        private const val DEFAULT_WIDTH_DP = 320

        private var painter: WidgetLyricsPainter? = null
        /** What each widget's lyrics picture was last drawn from, so an update that moves only the progress bar doesn't resend it. */
        private val painted = HashMap<Int, Any>()

        fun placed(context: Context): Boolean = ids(context).isNotEmpty()

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LyricsWidget::class.java))

        /** Sends [content] to every placed widget, each as it's set: only what moves unless [full]. */
        fun show(context: Context, content: Content, full: Boolean) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val store = WidgetSettingsStore(context)
            for (id in ids) {
                val (widthDp, heightDp) = sizeDp(context, manager.getAppWidgetOptions(id))
                val views = views(context, content, store.read(id), full, heightDp, widthDp, id)
                if (full) manager.updateAppWidget(id, views) else manager.partiallyUpdateAppWidget(id, views)
            }
        }

        /** What placed widgets show between them: whether any wants romanization, and any the progress bar. */
        fun wants(context: Context): Pair<Boolean, Boolean> {
            val store = WidgetSettingsStore(context)
            val all = ids(context).map(store::read)
            return all.any { it.romanization } to all.any { it.progress }
        }

        /**
         * A widget's width and height in dp (null: not reported) as the screen is turned now, from
         * its [options]: launchers report it in portrait as its narrowest width and largest height,
         * and in landscape as its widest width and smallest height. Drawn for the wrong one, the
         * lyrics picture is shrunk to fit. Sizes in pixels are taken back to dp ([WidgetFit.reportedDp]).
         */
        fun sizeDp(context: Context, options: Bundle): Pair<Int?, Int?> {
            fun read(key: String) = options.getInt(key).takeIf { it > 0 }
            val minWidth = read(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = read(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = read(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = read(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val (screenWidth, screenHeight) = screenDp(context)
            val (reportedWidth, reportedHeight) = if (screenWidth > screenHeight) (maxWidth ?: minWidth) to (minHeight ?: maxHeight)
            else (minWidth ?: maxWidth) to (maxHeight ?: minHeight)
            val (width, height) = if (homePackage(context) == ONE_UI_HOME) {
                WidgetFit.launcherDp(reportedWidth, reportedHeight, context.resources.displayMetrics.densityDpi, stableDpi())
            } else reportedWidth to reportedHeight
            return WidgetFit.reportedDp(width, height, screenWidth, screenHeight, context.resources.displayMetrics.density)
        }

        private const val ONE_UI_HOME = "com.sec.android.app.launcher"
        private var home: String? = null

        /** The home screen app's package, looked up once a process: widgets redraw every second. */
        private fun homePackage(context: Context): String =
            home ?: (runCatching {
                context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                )?.activityInfo?.packageName
            }.getOrNull() ?: "unknown").also { home = it }

        private fun stableDpi() =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) android.util.DisplayMetrics.DENSITY_DEVICE_STABLE else 0

        /**
         * The phone's screen in dp as it's turned now. Not the app's configuration: in the
         * floating window that's the window's, small and wide.
         */
        fun screenDp(context: Context): Pair<Int, Int> {
            val config = context.resources.configuration.let { it.screenWidthDp to it.screenHeightDp }
            val size = realSize(context) ?: return config
            val density = context.resources.displayMetrics.density
            return WidgetFit.screenDp((size.x / density).toInt() to (size.y / density).toInt(), config)
        }

        private fun realSize(context: Context): Point? {
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
            @Suppress("DEPRECATION")
            return Point().also(display::getRealSize)
        }

        /** Each placed widget's size as its launcher reports it and as it's drawn, for the extra logs. */
        fun sizeLines(context: Context): List<String> {
            val manager = AppWidgetManager.getInstance(context)
            val (screenWidth, screenHeight) = screenDp(context)
            val real = realSize(context)?.let { "${it.x}x${it.y} px" } ?: "unknown"
            val config = context.resources.configuration
            val header = "Screen ${screenWidth}x$screenHeight dp (display $real, app ${config.screenWidthDp}x${config.screenHeightDp} dp), " +
                "density ${context.resources.displayMetrics.densityDpi} dpi (device ${stableDpi()}), home ${homePackage(context)}"
            return listOf(header) + ids(context).map { id ->
                val options = manager.getAppWidgetOptions(id)
                fun read(key: String) = options.getInt(key)
                val (width, height) = sizeDp(context, options)
                "#$id reported min ${read(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}x${read(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}, " +
                    "max ${read(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)}x${read(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)}; drawn for ${width}x$height"
            }
        }

        /**
         * [content] as [chosen] lay it out, less what doesn't fit [heightDp] ([WidgetFit]), its lyrics
         * drawn for a widget [widthDp] wide. With [full] off, the pictures, colours and buttons'
         * actions are left as they were, and so are the lyrics if widget [id] already shows them.
         */
        fun views(
            context: Context,
            content: Content,
            chosen: WidgetSettings,
            full: Boolean,
            heightDp: Int?,
            widthDp: Int? = null,
            id: Int? = null,
        ): RemoteViews {
            val fit = WidgetFit.of(chosen, heightDp)
            val settings = fit.settings
            // By the chosen place, not the fitted one: a partial update must name the layout it updates.
            val layout = if (chosen.songInfo == WidgetSettings.SongInfo.TOP) R.layout.widget_lyrics_top else R.layout.widget_lyrics
            val views = RemoteViews(context.packageName, layout)
            showLines(context, views, content, settings, heightDp, widthDp, full, id)
            val timed = settings.progress && content.durationMs > 0L
            if (timed) {
                val permille = (content.positionMs * PROGRESS_MAX / content.durationMs).toInt().coerceIn(0, PROGRESS_MAX)
                views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, permille, false)
                views.setChronometer(R.id.widget_elapsed, SystemClock.elapsedRealtime() - content.positionMs, null, content.playing)
            }
            val credit = content.credit?.takeIf { settings.credit && it.isNotBlank() }
            views.setViewVisibility(R.id.widget_progress, visibleIf(timed))
            views.setViewVisibility(R.id.widget_elapsed, visibleIf(timed))
            views.setViewVisibility(R.id.widget_duration, visibleIf(timed))
            views.setTextViewText(R.id.widget_duration, clockTime(content.durationMs))
            views.setViewVisibility(R.id.widget_credit, if (credit != null) View.VISIBLE else View.INVISIBLE)
            views.setTextViewText(R.id.widget_credit, credit.orEmpty())
            views.setViewVisibility(R.id.widget_times, visibleIf(timed || credit != null))

            val hasSong = settings.songInfo != WidgetSettings.SongInfo.HIDDEN && content.title.isNotEmpty()
            views.setViewVisibility(R.id.widget_song_row, visibleIf(hasSong || settings.controls != WidgetSettings.Controls.NONE))
            views.setViewVisibility(R.id.widget_cover, visibleIf(hasSong))
            // Kept in place while hidden, so the buttons stay on the right.
            views.setViewVisibility(R.id.widget_song, if (hasSong) View.VISIBLE else View.INVISIBLE)
            views.setTextViewText(R.id.widget_title, content.title)
            views.setTextViewText(R.id.widget_artist, content.artist)

            views.setViewVisibility(R.id.widget_play_pause, visibleIf(settings.controls != WidgetSettings.Controls.NONE))
            views.setViewVisibility(R.id.widget_previous, visibleIf(settings.controls == WidgetSettings.Controls.ALL))
            views.setViewVisibility(R.id.widget_next, visibleIf(settings.controls == WidgetSettings.Controls.ALL))
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (content.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            views.setContentDescription(R.id.widget_play_pause, if (content.playing) "Pause" else "Play")
            if (!full) return views

            showBackground(views, content, settings)
            if (content.cover != null) views.setImageViewBitmap(R.id.widget_cover, content.cover)
            else views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)

            val open = Intent(context, LyricsActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause, broadcast(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_previous, broadcast(context, ACTION_PREVIOUS, 2))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
            return views
        }

        private fun showLines(
            context: Context,
            views: RemoteViews,
            content: Content,
            settings: WidgetSettings,
            heightDp: Int?,
            widthDp: Int?,
            full: Boolean,
            id: Int?,
        ) {
            val density = context.resources.displayMetrics.density
            val areaWidthPx = (((widthDp ?: DEFAULT_WIDTH_DP) - WidgetFit.PADDING_DP) * density).toInt()
            val areaHeightPx = (WidgetFit.linesAreaDp(settings, heightDp) * density).toInt()
            val style = WidgetLyricsPainter.Style(
                duet = content.duet,
                wideDuet = AppSettings(context.getSharedPreferences("ui", Context.MODE_PRIVATE)).duetLinePadding,
            )
            val key = listOf(content.lines, settings, areaWidthPx, areaHeightPx, style)
            if (!full && id != null && painted[id] == key) return
            val painter = painter ?: WidgetLyricsPainter(context.applicationContext).also { painter = it }
            views.setImageViewBitmap(R.id.widget_lyrics_image, painter.paint(content.lines, settings, style, areaWidthPx, areaHeightPx, density))
            views.setContentDescription(R.id.widget_lyrics_image,
                (listOf(content.lines.current) + content.lines.concurrent).joinToString("\n") { it.text }
                    .takeUnless { content.lines.current.interlude })
            if (id != null) painted[id] = key
        }

        private fun showBackground(views: RemoteViews, content: Content, settings: WidgetSettings) {
            val clear = settings.background == WidgetSettings.Background.CLEAR
            views.setInt(R.id.widget_root, "setBackgroundResource", if (clear) android.R.color.transparent else R.drawable.widget_background)
            val dim = settings.dim / 100f
            val shade = content.colors?.higherContrast ?: DEFAULT_ACCENT
            val color = when (settings.background) {
                WidgetSettings.Background.BLURRED_COVER -> shade
                WidgetSettings.Background.COVER_COLOR -> ColorUtils.blendARGB(content.colors?.highContrast ?: DEFAULT_ACCENT, Color.BLACK, dim)
                WidgetSettings.Background.BLACK -> Color.BLACK
                WidgetSettings.Background.CLEAR -> Color.TRANSPARENT
            }
            views.setInt(R.id.widget_backdrop, "setBackgroundColor", color)
            val backdrop = content.backdrop?.takeIf { settings.background == WidgetSettings.Background.BLURRED_COVER }
            if (backdrop != null) views.setImageViewBitmap(R.id.widget_backdrop, backdrop)
            else views.setImageViewResource(R.id.widget_backdrop, android.R.color.transparent)
            // Dimmed under a black veil (the filter draws over the picture, keeping its shape).
            views.setInt(R.id.widget_backdrop, "setColorFilter", if (backdrop != null) ColorUtils.setAlphaComponent(Color.BLACK, (dim * 255).toInt().coerceIn(0, 255)) else Color.TRANSPARENT)
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, LyricsWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun visibleIf(shown: Boolean) = if (shown) View.VISIBLE else View.GONE

        private fun clockTime(ms: Long): String {
            val seconds = (ms / 1_000L).coerceAtLeast(0L)
            return "%d:%02d".format(seconds / 60, seconds % 60)
        }
    }
}
