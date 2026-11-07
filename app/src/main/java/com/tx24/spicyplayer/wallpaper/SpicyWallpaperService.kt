package com.tx24.spicyplayer.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.WordTiming
import com.tx24.spicyplayer.lyrics.spicy.canvas.StackBlur
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.playback.BackgroundSession
import com.tx24.spicyplayer.playback.BackgroundTranslation
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverStream
import com.tx24.spicyplayer.ui.nowplaying.MotionCovers
import com.tx24.spicyplayer.ui.nowplaying.leadArtist
import com.tx24.spicyplayer.widget.BackgroundLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sin

/**
 * The Spicy now-playing screen as a live wallpaper: the word-synced lyrics over the song's
 * Canvas video or animated cover (sampled from the same shared player the app shows them with),
 * falling back to the blurred cover art, with the cover and the song's name on top. It follows
 * the active media session like the app does, and keeps the lyrics synced while the home screen
 * is visible. Picked from the system wallpaper picker; Settings → Device → Live wallpaper
 * opens that picker, and "Show lyrics on wallpaper" there (or the wallpaper's own settings)
 * hides the lyrics, leaving only the moving picture. That toggle is read on every draw, so it
 * takes effect without re-applying the wallpaper.
 *
 * The picture moves only while the home screen is visible and the music plays; paused, it holds
 * its frame. Drawing rests with the screen off or the home screen hidden.
 *
 * It is drawn with Compose ([WallpaperHost], [WallpaperContent]): the lyrics by the lyrics
 * screen's own renderer, with the style, size, font and effects chosen in the app, and the loops
 * in the app's own video views at their full frame rate, blurred on the GPU. The frame drawn by
 * hand here is the fallback for a phone that can't host Compose on a wallpaper.
 */

/** A Canvas lookup's answer, with the IDs it was asked under. */
private class CanvasResult(val url: String?, val ids: List<String>)

@OptIn(UnstableApi::class)
class SpicyWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SpicyEngine()

    inner class SpicyEngine : Engine() {
        private val app = applicationContext
        private val main = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val session = BackgroundSession(app)
        private val lookup = BackgroundLyrics(app)
        private val translations = BackgroundTranslation(app)
        private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
        private var visible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var currentKey: String? = null
        private var lyrics: LyricsState.Ready? = null
        private var lyricsNotice: String? = null
        private var cover: Bitmap? = null
        private var blurredBg: Bitmap? = null
        /**
         * The layers: the background is the Canvas video, lightly blurred; the cover is the
         * record's animated cover, playing sharp. Each has its own stream and sampler, so the
         * two can play at once; the background falls back to the animated cover, then to the
         * blurred still cover, and the cover falls back to the still cover.
         */
        /** The background video's latest frame, blurred lightly (see [BG_BLUR_RADIUS]). */
        @Volatile private var bgFrame: Bitmap? = null
        /** The animated cover's latest frame, drawn sharp as the cover. */
        @Volatile private var coverFrame: Bitmap? = null
        private var bgStream: MotionCoverStream? = null
        private var coverStream: MotionCoverStream? = null
        private var bgStreamOnScreen = false
        private var coverStreamOnScreen = false
        private var bgPlayerListener: Player.Listener? = null
        private var coverPlayerListener: Player.Listener? = null
        private var bgStopSampler: (() -> Unit)? = null
        private var coverStopSampler: (() -> Unit)? = null
        /** Rises on a layer's release, so a frame its dead stream already posted cannot land after it. */
        private var bgGeneration = 0
        private var coverGeneration = 0
        private var xOffset = 0.5f
        private var gradient: LinearGradient? = null
        private var gradientHeight = 0
        /**
         * What the Compose wallpaper shows ([WallpaperContent]), and the host drawing it into the
         * surface. While the host is up it draws everything, smoothly, with the app's own lyrics
         * renderer and video views; the hand-drawn frame below is only the fallback for a phone
         * that can't host it.
         */
        private val ui = WallpaperUi()
        private var host: WallpaperHost? = null

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        private val veilPaint = Paint()
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        /**
         * When the next word starts, in song ms (set by each draw of the lyrics), so the next draw
         * can land on it instead of up to [DRAW_INTERVAL_MS] after it.
         */
        private var nextWordAtMs = Long.MAX_VALUE
        /**
         * The redraw step while a word is being wiped (set by each draw): a quarter of the word,
         * so a short word fills in a few steps instead of jumping, never under [MIN_WIPE_STEP_MS].
         */
        private var wipeStepMs = DRAW_INTERVAL_MS

        private val drawRunnable: Runnable = Runnable {
            nextWordAtMs = Long.MAX_VALUE
            wipeStepMs = DRAW_INTERVAL_MS
            drawFrame()
            if (visible && session.isPlaying) main.postDelayed(drawRunnable, nextDrawDelayMs())
        }

        /** The usual redraw interval, cut short so a word starting sooner lights on time. */
        private fun nextDrawDelayMs(): Long {
            val untilWord = nextWordAtMs - session.positionMs()
            return minOf(untilWord, wipeStepMs).coerceIn(MIN_DRAW_INTERVAL_MS, DRAW_INTERVAL_MS)
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            session.onTrack = { onTrack(it) }
            session.onPlayingChanged = { playing -> onPlayingChanged(playing) }
            session.start()
            // Power saving can follow the phone's battery saver.
            ui.batterySaver = app.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
            androidx.core.content.ContextCompat.registerReceiver(
                app, batterySaverReceiver,
                android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        private val batterySaverReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                ui.batterySaver = app.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
            }
        }

        override fun onDestroy() {
            if (visible && !isPreview) WallpaperPresence.engineVisible(false)
            main.removeCallbacks(drawRunnable)
            releaseHost()
            releaseVisuals()
            runCatching { app.unregisterReceiver(batterySaverReceiver) }
            session.stop()
            scope.cancel()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible != this.visible && !isPreview) WallpaperPresence.engineVisible(visible)
            this.visible = visible
            ui.visible = visible
            host?.let {
                it.setActive(visible && session.isPlaying)
                return
            }
            if (visible) {
                setStreamsOnScreen(session.isPlaying)
                requestDraw()
            } else {
                main.removeCallbacks(drawRunnable)
                setStreamsOnScreen(false)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            setStreamsOnScreen(visible && session.isPlaying)
            requestDraw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            gradient = null
            host?.resize(width, height) ?: createHost(holder, width, height)
            requestDraw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            main.removeCallbacks(drawRunnable)
            releaseHost()
            setStreamsOnScreen(false)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            // The home screen sliding between pages: the picture drifts with it.
            this.xOffset = xOffset
            ui.xOffset = xOffset
            requestDraw()
        }

        /** A new song (or its cover): new lyrics and new loops, the old ones dropped. */
        private fun onTrack(track: BackgroundSession.Track?) {
            val newKey = track?.key
            ui.track = track
            ui.cover = track?.cover
            if (newKey != currentKey) {
                currentKey = newKey
                lyrics = null
                lyricsNotice = null
                ui.lyrics = null
                ui.translation = null
                ui.notice = null
                ui.canvasUrl = null
                ui.appleUrl = null
                releaseVisuals()
                blurredBg = null
                cover = track?.cover
                if (track != null) {
                    lookupLyrics(track)
                    lookupVisuals(track)
                }
                requestDraw()
            } else if (track?.cover != null && track.cover !== cover) {
                cover = track.cover
                blurredBg = null
                requestDraw()
            }
        }

        private fun onPlayingChanged(playing: Boolean) {
            ui.playing = playing
            host?.setActive(visible && playing)
            setStreamsOnScreen(visible && playing)
            // Paused, one frozen frame is drawn; playing, the draw loop restarts.
            requestDraw()
        }

        private fun lookupLyrics(track: BackgroundSession.Track) {
            scope.launch {
                // Romanized on the device when the app romanizes, as the lyrics screen does.
                val romanize = prefs.getBoolean("romanize", false)
                val result = withContext(Dispatchers.IO) {
                    runCatching { lookup.lookUp(track.request, romanize = romanize, track.spotifyId) }.getOrNull()
                }
                if (currentKey != track.key) return@launch
                when (result) {
                    is BackgroundLyrics.Result.Found -> {
                        // Lines timed only as a whole (LRC) get their words laid out one after
                        // another, so each word lights as it is sung, not all at once over the
                        // whole span to the next line. The words and their text are unchanged.
                        val found = result.lyrics
                        lyrics = found.copy(lines = WordTiming.spread(found.lines, found.lyricsType))
                        lyricsNotice = null
                    }
                    BackgroundLyrics.Result.None -> {
                        lyrics = null
                        lyricsNotice = NO_LYRICS
                    }
                    BackgroundLyrics.Result.Failed, null -> {
                        lyrics = null
                        lyricsNotice = LOOKUP_FAILED
                    }
                }
                // The Compose wallpaper draws them as the lyrics screen does, timings as they came.
                ui.lyrics = (result as? BackgroundLyrics.Result.Found)?.lyrics
                ui.translation = null
                ui.notice = lyricsNotice
                requestDraw()
                // Translated as the lyrics screen would (Translate automatically), from its cache
                // when it has translated the song already.
                if (result is BackgroundLyrics.Result.Found) {
                    val translated = withContext(Dispatchers.IO) {
                        translations.translate(result.lyrics, track.title, track.artist, result.sourceId) { target ->
                            runCatching { lookup.humanTranslation(track.title, track.artist, target) }.getOrNull()
                        }
                    }
                    if (currentKey == track.key && ui.lyrics === result.lyrics) ui.translation = translated
                }
            }
        }

        /**
         * The song's moving pictures: the Canvas for the background and the record's animated
         * cover for the cover. The Canvas is asked for under every Spotify ID the track
         * resolves to, not just the first: the first is the track as the session knows it, and
         * another release of the same song may be the one the Canvas service knows.
         *
         * The Canvas needs no Spicy key: the Canvas service is asked anonymously, as the app's
         * own now-playing screen asks it. What the service answers is written to the settings,
         * so a track with no Canvas reads as no Canvas, not as a playback bug.
         */
        private fun lookupVisuals(track: BackgroundSession.Track) {
            scope.launch {
                val canvas = withContext(Dispatchers.IO) {
                    runCatching {
                        val meta = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                        val ids = SharedSpotify.extras(app.cacheDir).trackIds(meta, track.request.spotifyTrackId)
                        var found: String? = null
                        for (id in ids) {
                            found = SharedSpotify.canvas(app.cacheDir).find(id)
                            if (found != null) break
                        }
                        CanvasResult(found, ids)
                    }.getOrNull()
                }
                if (currentKey != track.key) return@launch
                val result = canvas ?: CanvasResult(null, emptyList())
                prefs.edit().putString(PREF_CANVAS_STATUS, canvasStatus(result)).apply()
                val apple = withContext(Dispatchers.IO) {
                    runCatching { MotionCovers.finder(app).find(leadArtist(track.artist), track.album, track.title) }.getOrNull()
                }
                if (currentKey != track.key) return@launch
                prefs.edit().putString(PREF_APPLE_STATUS, if (apple != null) STATUS_APPLE_PLAYING else STATUS_APPLE_NONE).apply()
                attachVisuals(result.url, apple)
            }
        }

        /**
         * What the Canvas lookup means: found, the service has none for this track (a miss it
         * remembered in its memo file), the lookup failed, or there was no track ID to ask
         * under. A miss is a service answer, not a playback bug, and no key is needed for it.
         */
        private fun canvasStatus(result: CanvasResult): String {
            if (result.url != null) return STATUS_CANVAS_PLAYING
            if (result.ids.isEmpty()) return STATUS_CANVAS_NO_ID
            if (canvasMissRemembered(result.ids)) return STATUS_CANVAS_NONE
            return STATUS_CANVAS_FAILED
        }

        /** Whether the Canvas service's miss for any of [ids] is in its memo file. */
        private fun canvasMissRemembered(ids: List<String>): Boolean {
            val memo = runCatching {
                java.io.File(app.cacheDir, "canvas/known-v2.json").readText()
            }.getOrNull() ?: return false
            return runCatching {
                val known = com.google.gson.JsonParser.parseString(memo).asJsonObject
                ids.any { id ->
                    known.get(id)?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("url")?.takeIf { it.isJsonPrimitive }?.asString.isNullOrEmpty()
                }
            }.getOrDefault(false)
        }

        /**
         * Plays the two loops: the Canvas as the background, the animated cover as the cover.
         * With no Canvas the animated cover is the background too; with neither, the blurred
         * still cover is. A loop that fails to play drops to the next layer down.
         */
        private fun attachVisuals(canvasUrl: String?, appleUrl: String?) {
            ui.canvasUrl = canvasUrl
            ui.appleUrl = appleUrl
            // The Compose wallpaper plays them in its own views, at the video's frame rate.
            if (host != null) return
            releaseVisuals()
            val bgUrl = canvasUrl ?: appleUrl
            if (bgUrl != null) attachStream(bgUrl, isBackground = true)
            if (appleUrl != null) attachStream(appleUrl, isBackground = false)
            setStreamsOnScreen(visible && session.isPlaying)
            requestDraw()
        }

        /** One loop through the shared stream, sampled into its layer's frame. */
        private fun attachStream(url: String, isBackground: Boolean) {
            val stream = MotionCovers.acquire(app, url)
            val generation = if (isBackground) bgGeneration else coverGeneration
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // The loop won't play: fall back to the next layer down.
                    main.post {
                        val current = if (isBackground) bgGeneration else coverGeneration
                        if (current == generation) releaseStream(isBackground)
                    }
                }
            }.also { stream.addListener(it) }
            val stopSampler = stream.addSampler(
                if (isBackground) BG_LONG_SIDE else COVER_LONG_SIDE,
                VIDEO_INTERVAL_MS,
            ) { sample ->
                val frame = runCatching {
                    Bitmap.createBitmap(sample.pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888)
                }.getOrNull() ?: return@addSampler
                main.post {
                    // A frame a dead stream already posted must not land after its release.
                    val current = if (isBackground) bgGeneration else coverGeneration
                    if (current != generation) return@post
                    if (isBackground) {
                        bgFrame = StackBlur.blur(frame, BG_BLUR_RADIUS)
                    } else {
                        coverFrame = frame
                    }
                    requestDraw()
                }
            }
            if (isBackground) {
                bgStream = stream
                bgPlayerListener = listener
                bgStopSampler = stopSampler
            } else {
                coverStream = stream
                coverPlayerListener = listener
                coverStopSampler = stopSampler
            }
        }

        private fun setStreamsOnScreen(on: Boolean) {
            bgStream?.let { stream ->
                if (on && !bgStreamOnScreen) {
                    stream.onScreen(true)
                    bgStreamOnScreen = true
                } else if (!on && bgStreamOnScreen) {
                    stream.onScreen(false)
                    bgStreamOnScreen = false
                }
            }
            coverStream?.let { stream ->
                if (on && !coverStreamOnScreen) {
                    stream.onScreen(true)
                    coverStreamOnScreen = true
                } else if (!on && coverStreamOnScreen) {
                    stream.onScreen(false)
                    coverStreamOnScreen = false
                }
            }
        }

        /** Drops one loop: its sampler, its listener, its frames. */
        private fun releaseStream(isBackground: Boolean) {
            if (isBackground) {
                bgGeneration++
                bgStopSampler?.invoke()
                bgStopSampler = null
                if (bgStreamOnScreen) {
                    bgStream?.onScreen(false)
                    bgStreamOnScreen = false
                }
                bgPlayerListener?.let { bgStream?.removeListener(it) }
                bgPlayerListener = null
                bgStream?.let { MotionCovers.release(it) }
                bgStream = null
                bgFrame = null
            } else {
                coverGeneration++
                coverStopSampler?.invoke()
                coverStopSampler = null
                if (coverStreamOnScreen) {
                    coverStream?.onScreen(false)
                    coverStreamOnScreen = false
                }
                coverPlayerListener?.let { coverStream?.removeListener(it) }
                coverPlayerListener = null
                coverStream?.let { MotionCovers.release(it) }
                coverStream = null
                coverFrame = null
            }
            requestDraw()
        }

        /** Drops both loops, for a new song or the engine going away. */
        private fun releaseVisuals() {
            releaseStream(isBackground = true)
            releaseStream(isBackground = false)
        }

        /**
         * Puts the Compose wallpaper on [holder]'s surface. Should the phone refuse it, the
         * hand-drawn frame carries on, with its sampled loops.
         */
        private fun createHost(holder: SurfaceHolder, width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            val created = runCatching {
                WallpaperHost(app, holder.surface, width, height, app.resources.displayMetrics.densityDpi) {
                    WallpaperContent(
                        ui = ui,
                        prefs = prefs,
                        positionMs = session::positionMs,
                        onBackgroundFailed = ::backgroundFailed,
                        onCoverFailed = ::coverFailed,
                    )
                }
            }.onFailure { android.util.Log.w(TAG, "No Compose host for the wallpaper; drawing by hand", it) }.getOrNull()
            if (created == null) {
                if (bgStream == null && coverStream == null && (ui.canvasUrl != null || ui.appleUrl != null)) {
                    attachVisuals(ui.canvasUrl, ui.appleUrl)
                }
                return
            }
            main.removeCallbacks(drawRunnable)
            // Its own views play the loops now: the sampled copies go.
            releaseVisuals()
            host = created
            ui.playing = session.isPlaying
            ui.visible = visible
            created.setActive(visible && session.isPlaying)
        }

        private fun releaseHost() {
            host?.release()
            host = null
        }

        /** The background loop won't play: the next layer down shows. */
        private fun backgroundFailed(url: String) {
            if (ui.canvasUrl == url) ui.canvasUrl = null else if (ui.appleUrl == url) ui.appleUrl = null
        }

        /** The animated cover won't play: the still cover shows. */
        private fun coverFailed(url: String) {
            if (ui.appleUrl == url) ui.appleUrl = null
        }

        private fun requestDraw() {
            if (!visible || host != null) return
            main.removeCallbacks(drawRunnable)
            main.post(drawRunnable)
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                drawOn(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawOn(canvas: Canvas) {
            val w = surfaceWidth.takeIf { it > 0 } ?: canvas.width
            val h = surfaceHeight.takeIf { it > 0 } ?: canvas.height
            if (w <= 0 || h <= 0) return
            val showLyrics = prefs.getBoolean("wallpaperShowLyrics", true)
            val track = session.track
            drawBackground(canvas, w, h, showLyrics)
            val titleSize = (w * 0.045f).coerceIn(30f, 64f)
            // The cover is the wallpaper's centrepiece: about two thirds of the screen width
            // (it used to be about a third), centred at the top. Both the animated cover and
            // the still album cover draw here, so both get the size and the rounded corners.
            val coverSize = (minOf(w, h) * COVER_WIDTH_FRACTION).toInt()
            val coverTop = if (showLyrics) (h * 0.055f).toInt() else (h * 0.16f).toInt()
            val coverArt = coverFrame ?: cover
            var songTop = coverTop
            if (coverArt != null) {
                val left = (w - coverSize) / 2
                val src = squareSrc(coverArt)
                val dst = RectF(left.toFloat(), coverTop.toFloat(), (left + coverSize).toFloat(), (coverTop + coverSize).toFloat())
                // Rounded corners: a BitmapShader mapped from the square source onto the
                // destination rect, drawn as a round rect, so the corners are smooth.
                val shader = BitmapShader(coverArt, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val scale = coverSize.toFloat() / src.width()
                val matrix = Matrix()
                matrix.setScale(scale, scale)
                matrix.postTranslate(dst.left - src.left * scale, dst.top - src.top * scale)
                shader.setLocalMatrix(matrix)
                coverPaint.shader = shader
                val radius = COVER_CORNER_RADIUS_DP * app.resources.displayMetrics.density
                canvas.drawRoundRect(dst, radius, radius, coverPaint)
                coverPaint.shader = null
                songTop = coverTop + coverSize
            }
            if (track != null) {
                val titleY = songTop + titleSize * 1.25f
                drawCentred(canvas, track.title, w / 2f, titleY, titleSize, Typeface.DEFAULT_BOLD, 255, w * 0.9f)
                drawCentred(canvas, track.artist, w / 2f, titleY + titleSize * 1.15f, titleSize * 0.85f, Typeface.DEFAULT, 180, w * 0.9f)
            }
            if (!showLyrics) return
            val lyricsTop = songTop + titleSize * 3.1f
            val ready = lyrics
            when {
                track == null -> drawCentred(canvas, WAITING, w / 2f, h * 0.5f, titleSize * 0.8f, Typeface.DEFAULT, 150, w * 0.9f)
                ready == null -> lyricsNotice?.let {
                    drawCentred(canvas, it, w / 2f, h * 0.5f, titleSize * 0.8f, Typeface.DEFAULT, 150, w * 0.9f)
                }
                ready.lines.isEmpty() -> drawCentred(canvas, NO_LYRICS, w / 2f, h * 0.5f, titleSize * 0.8f, Typeface.DEFAULT, 150, w * 0.9f)
                else -> drawLyrics(canvas, ready, w, h, lyricsTop.toInt())
            }
        }

        /** The background loop's frame, else the blurred cover drifting slowly, else a dark gradient. */
        private fun drawBackground(canvas: Canvas, w: Int, h: Int, showLyrics: Boolean) {
            val frame = bgFrame
            val bg = frame ?: blurredCover()
            if (bg != null) {
                val now = SystemClock.uptimeMillis()
                // The still cover breathes slowly; the video moves on its own.
                val breathe = if (frame != null) 1f else 1.05f + 0.03f * sin(now / 12_000.0).toFloat()
                val scale = max(w.toFloat() / bg.width, h.toFloat() / bg.height) * breathe
                val drawnW = bg.width * scale
                val drawnH = bg.height * scale
                val shift = (0.5f - xOffset) * w * PARALLAX_FRACTION
                val left = (w - drawnW) / 2f + shift
                val top = (h - drawnH) / 2f
                canvas.drawBitmap(bg, null, RectF(left, top, left + drawnW, top + drawnH), bgPaint)
            } else {
                if (gradient == null || gradientHeight != h) {
                    gradient = LinearGradient(
                        0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF141419.toInt(), 0xFF1E1E26.toInt()),
                        null, Shader.TileMode.CLAMP,
                    )
                    gradientHeight = h
                }
                veilPaint.shader = gradient
                veilPaint.alpha = 255
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veilPaint)
                veilPaint.shader = null
            }
            // A veil, so the words stay readable over whatever moves behind them.
            veilPaint.color = Color.BLACK
            veilPaint.alpha = if (showLyrics) VEIL_LYRICS else VEIL_PLAIN
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veilPaint)
        }

        /**
         * The cover blurred once per song; drawn big, the blur is the background. The blur is
         * light on purpose: the copy is [COVER_BLUR_WIDTH] wide and the radius is
         * [COVER_BLUR_RADIUS], about 6 px once it is stretched over the screen, so the cover
         * stays recognisable instead of a muddy smear.
         */
        private fun blurredCover(): Bitmap? {
            blurredBg?.let { return it }
            val art = cover ?: return null
            val small = Bitmap.createScaledBitmap(art, COVER_BLUR_WIDTH, max(1, COVER_BLUR_WIDTH * art.height / art.width), true)
            return StackBlur.blur(small, COVER_BLUR_RADIUS).also { blurredBg = it }
        }

        /** The square in the middle of [bitmap], for the cover's frame. */
        private fun squareSrc(bitmap: Bitmap): Rect {
            val side = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - side) / 2
            val top = (bitmap.height - side) / 2
            return Rect(left, top, left + side, top + side)
        }

        /** One centred line, shrunk until it fits [maxWidth]. */
        private fun drawCentred(canvas: Canvas, text: String, cx: Float, y: Float, size: Float, face: Typeface, alpha: Int, maxWidth: Float) {
            textPaint.typeface = face
            textPaint.color = Color.WHITE
            textPaint.alpha = alpha
            var s = size
            textPaint.textSize = s
            while (s > 20f && textPaint.measureText(text) > maxWidth) {
                s *= 0.9f
                textPaint.textSize = s
            }
            canvas.drawText(text, cx - textPaint.measureText(text) / 2f, y, textPaint)
        }

        /**
         * The synced lines around the one being sung, the active line centred: sung words bright,
         * the word being sung wiped across, the rest dim. Unsynced lyrics sit on their place in
         * the song instead.
         */
        private fun drawLyrics(canvas: Canvas, ready: LyricsState.Ready, w: Int, h: Int, top: Int) {
            val lines = ready.lines
            val pos = session.positionMs()
            val synced = ready.lyricsType != LyricsType.Static
            val activeSize = (w * 0.062f).coerceIn(40f, 96f)
            val otherSize = activeSize * 0.78f
            val maxWidth = w * 0.88f
            val centreY = (top + (h - top) * 0.42f).toFloat()
            val activeIdx = if (synced) {
                // The lead line being sung, not a backing vocal that started after it: those sit
                // under their lead line and are lit by their own times below.
                lines.indexOfLast { it.startMs <= pos && it.role != LineRole.BACKGROUND }
                    .takeIf { it >= 0 } ?: lines.indexOfLast { it.startMs <= pos }.coerceAtLeast(0)
            } else {
                val duration = session.track?.durationMs ?: 0L
                val at = if (duration > 0) (pos.toFloat() / duration * lines.size).toInt() else 0
                at.coerceIn(0, lines.size - 1)
            }
            val first = max(0, activeIdx - LINES_AROUND)
            val last = minOf(lines.size - 1, activeIdx + LINES_AROUND)
            // Measure first, so the active line lands centred.
            val measured = (first..last).map { i ->
                val line = lines[i]
                val size = if (i == activeIdx) activeSize else otherSize
                val rows = layoutRows(line, size, maxWidth)
                val height = rows.size * size * ROW_HEIGHT + LINE_GAP * size
                Triple(i, rows, height to size)
            }
            val activeHeight = measured.firstOrNull { it.first == activeIdx }?.third?.first ?: 0f
            var cursor = centreY - activeHeight / 2f
            // Lines above the active one, upward from it.
            for ((i, rows, heightAndSize) in measured.asReversed()) {
                if (i >= activeIdx) continue
                cursor -= heightAndSize.first + LINE_GAP * activeSize
                drawLineBlock(canvas, lines[i], rows, w, cursor, heightAndSize.second, past = true, active = false, pos, synced)
            }
            cursor = centreY - activeHeight / 2f
            for ((i, rows, heightAndSize) in measured) {
                if (i == activeIdx) {
                    drawLineBlock(canvas, lines[i], rows, w, cursor, heightAndSize.second, past = false, active = true, pos, synced)
                    cursor += heightAndSize.first + LINE_GAP * activeSize
                } else if (i > activeIdx) {
                    drawLineBlock(canvas, lines[i], rows, w, cursor, heightAndSize.second, past = false, active = false, pos, synced)
                    cursor += heightAndSize.first + LINE_GAP * activeSize
                }
            }
        }

        /** A line's words wrapped to [maxWidth], as rows of words. */
        private fun layoutRows(line: TimedLine, size: Float, maxWidth: Float): List<List<TimedWord>> {
            if (line.role == LineRole.INTERLUDE) return listOf(emptyList())
            textPaint.textSize = size
            val space = textPaint.measureText(" ")
            val rows = ArrayList<List<TimedWord>>()
            var row = ArrayList<TimedWord>()
            var width = 0f
            for (word in line.words) {
                val wordWidth = textPaint.measureText(word.text)
                val add = if (row.isEmpty()) wordWidth else width + space + wordWidth
                if (row.isNotEmpty() && add > maxWidth) {
                    rows += row
                    row = ArrayList()
                    width = 0f
                }
                if (row.isNotEmpty()) width += space
                row += word
                width += wordWidth
            }
            if (row.isNotEmpty()) rows += row
            return rows.ifEmpty { listOf(emptyList()) }
        }

        /** Draws one line's rows at [top], sung words bright and the current one wiped across. */
        private fun drawLineBlock(
            canvas: Canvas, line: TimedLine, rows: List<List<TimedWord>>, w: Int,
            top: Float, size: Float, past: Boolean, active: Boolean, pos: Long, synced: Boolean,
        ) {
            textPaint.textSize = size
            val fm = textPaint.fontMetrics
            val rowHeight = size * ROW_HEIGHT
            if (line.role == LineRole.INTERLUDE) {
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.color = Color.WHITE
                textPaint.alpha = 120
                val dots = "• • •"
                canvas.drawText(dots, (w - textPaint.measureText(dots)) / 2f, top - fm.ascent, textPaint)
                return
            }
            val background = line.role == LineRole.BACKGROUND
            val drawSize = if (background) size * 0.72f else size
            textPaint.textSize = drawSize
            val metrics = textPaint.fontMetrics
            val space = textPaint.measureText(" ")
            var y = top
            for (row in rows) {
                val width = row.sumOf { textPaint.measureText(it.text).toDouble() }.toFloat() + space * (row.size - 1).coerceAtLeast(0)
                var x = (w - width) / 2f
                val baseline = y - metrics.ascent
                for (word in row) {
                    val wordWidth = textPaint.measureText(word.text)
                    val alpha = when {
                        background -> 90
                        active -> 110
                        past -> 150
                        else -> 110
                    }
                    // The dim word underneath, then the bright part over it.
                    textPaint.typeface = Typeface.DEFAULT
                    textPaint.color = Color.WHITE
                    textPaint.alpha = alpha
                    canvas.drawText(word.text, x, baseline, textPaint)
                    // Every synced line is lit by its own times, so a line still being sung beside
                    // the current one (a backing vocal, an overlap) fills as it is sung; done
                    // lines come out full and lines not reached yet empty, as before.
                    val fraction = when {
                        !synced -> if (past) 1f else 0f
                        line.lineTimed || line.untimed -> if (pos >= line.startMs) 1f else 0f
                        else -> ((pos - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                    }
                    if (synced && word.startMs > pos && word.startMs < nextWordAtMs) nextWordAtMs = word.startMs
                    if (fraction > 0f && fraction < 1f) {
                        wipeStepMs = minOf(wipeStepMs, ((word.endMs - word.startMs) / 4).coerceAtLeast(MIN_WIPE_STEP_MS))
                    }
                    if (fraction > 0f) {
                        textPaint.typeface = Typeface.DEFAULT_BOLD
                        textPaint.alpha = 255
                        if (fraction >= 1f) {
                            canvas.drawText(word.text, x, baseline, textPaint)
                        } else {
                            canvas.save()
                            canvas.clipRect(x, y, x + wordWidth * fraction, y + rowHeight)
                            canvas.drawText(word.text, x, baseline, textPaint)
                            canvas.restore()
                        }
                    }
                    x += wordWidth + space
                }
                y += rowHeight
            }
        }
    }

    companion object {
        private const val TAG = "SpicyWallpaper"
        private const val WAITING = "Play something to see its lyrics here"
        private const val NO_LYRICS = "No lyrics for this song"
        private const val LOOKUP_FAILED = "Couldn't load lyrics"
        /** How often the wallpaper redraws while the music plays. */
        private const val DRAW_INTERVAL_MS = 100L
        /** The soonest the next redraw comes, when it is pulled in to land on a word's start: a frame. */
        private const val MIN_DRAW_INTERVAL_MS = 16L
        /** The shortest step while a word is wiped: about two frames. */
        private const val MIN_WIPE_STEP_MS = 33L
        /** Lines on either side of the one being sung. */
        private const val LINES_AROUND = 2
        /** A wrapped row's height, in lyric text sizes. */
        private const val ROW_HEIGHT = 1.3f
        /** The gap between lines, in lyric text sizes. */
        private const val LINE_GAP = 0.35f
        /** How far the background drifts as the home screen slides between pages. */
        private const val PARALLAX_FRACTION = 0.08f
        /** The veil over the moving picture: heavier under the words. */
        private const val VEIL_LYRICS = 100
        private const val VEIL_PLAIN = 70
        /**
         * The background video's blur: 6 px on the screen, no more. The frames are sampled
         * [BG_LONG_SIDE] px on the long side, so a radius of [BG_BLUR_RADIUS] there is about
         * 6 px once the frame is stretched over a 1080 px wide screen.
         */
        private const val BG_LONG_SIDE = 360
        private const val BG_BLUR_RADIUS = 2
        /** The animated cover's frames: this big on the long side, drawn sharp as the cover. */
        private const val COVER_LONG_SIDE = 320
        /** The sampled video frames: this often. */
        private const val VIDEO_INTERVAL_MS = 100L
        /**
         * The still cover's blur, on a [COVER_BLUR_WIDTH] px copy: the same light 6 px once it
         * is stretched over the screen.
         */
        private const val COVER_BLUR_WIDTH = 540
        private const val COVER_BLUR_RADIUS = 3

        /** The cover's width as a fraction of the screen's short side (its width in portrait). */
        private const val COVER_WIDTH_FRACTION = 0.65f
        /** The cover's corner radius, in dp — the same for the animated and the still cover. */
        private const val COVER_CORNER_RADIUS_DP = 24f

        /** The wallpaper's Canvas and animated-cover status, for Settings → Device. */
        const val PREF_CANVAS_STATUS = "wallpaperCanvasStatus"
        const val PREF_APPLE_STATUS = "wallpaperAppleStatus"
        const val STATUS_CANVAS_PLAYING = "Canvas video playing"
        const val STATUS_CANVAS_NONE = "No Canvas for this track — the Canvas service has none"
        const val STATUS_CANVAS_FAILED = "Canvas lookup failed — network or service error"
        const val STATUS_CANVAS_NO_ID = "No Spotify track ID for this track"
        const val STATUS_APPLE_PLAYING = "Animated cover playing"
        const val STATUS_APPLE_NONE = "No animated cover for this record"
    }
}
