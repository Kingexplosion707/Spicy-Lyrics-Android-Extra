package com.tx24.spicyplayer.wallpaper

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.ScrollConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.canvas.StackBlur
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.playback.BackgroundSession
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverVideo
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCoverStream
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.LyricsFont
import com.tx24.spicyplayer.ui.settings.LyricsFontFile
import com.tx24.spicyplayer.ui.settings.LyricsStyle
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the wallpaper shows, written by its engine on the main thread and read by [WallpaperContent]. */
internal class WallpaperUi {
    var track by mutableStateOf<BackgroundSession.Track?>(null)
    var lyrics by mutableStateOf<LyricsState.Ready?>(null)
    /** Said in place of the lyrics: none found, the lookup failed. */
    var notice by mutableStateOf<String?>(null)
    var canvasUrl by mutableStateOf<String?>(null)
    var appleUrl by mutableStateOf<String?>(null)
    var cover by mutableStateOf<Bitmap?>(null)
    var playing by mutableStateOf(false)
    var visible by mutableStateOf(false)
    /** The home screen's page position, 0..1, for the background's drift. */
    var xOffset by mutableStateOf(0.5f)
}

/**
 * The wallpaper, laid out as before: the Canvas video (else the animated cover, else the blurred
 * still cover) behind a veil, the cover at 65% of the screen's width with 24dp corners near the
 * top, the song's name under it, and under that the lyrics, drawn by the app's own renderer
 * ([LyricsView]) with the lyrics style, size, font and effects chosen in the app, so they look
 * and move exactly as on the lyrics screen.
 */
@Composable
internal fun WallpaperContent(
    ui: WallpaperUi,
    prefs: SharedPreferences,
    positionMs: () -> Long,
    onBackgroundFailed: (String) -> Unit,
    onCoverFailed: (String) -> Unit,
) {
    // The settings as the app last wrote them: re-read whenever they change.
    var settingsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settingsVersion++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val settings = remember(settingsVersion) { AppSettings(prefs) }
    val showLyrics = remember(settingsVersion) { prefs.getBoolean("wallpaperShowLyrics", true) }
    LyricsFontSetup(settings)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0) return@BoxWithConstraints
        val track = ui.track
        val titleSize = (w * 0.045f).coerceIn(30f, 64f)
        val coverSize = (minOf(w, h) * COVER_WIDTH_FRACTION).roundToInt()
        val coverTop = if (showLyrics) (h * 0.055f).toInt() else (h * 0.16f).toInt()
        val hasCover = ui.appleUrl != null || ui.cover != null
        val songTop = if (hasCover) coverTop + coverSize else coverTop
        val lyricsTop = (songTop + titleSize * 3.1f).roundToInt()
        val shiftPx = (0.5f - ui.xOffset) * w * PARALLAX_FRACTION

        // The background: the loop sharp-decoded and blurred on the GPU, every frame.
        val backgroundUrl = ui.canvasUrl ?: ui.appleUrl
        StillBackground(ui, w, h, shiftPx, animate = backgroundUrl == null)
        if (backgroundUrl != null) {
            val blurDp = with(density) { BG_BLUR_PX.toDp() }
            // Grown by the drift's reach, so sliding between pages never shows its edge.
            val bgModifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = PARALLAX_OVERSCAN
                scaleY = PARALLAX_OVERSCAN
                translationX = shiftPx
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MotionCoverVideo(
                    url = backgroundUrl,
                    visible = true,
                    onFailed = { onBackgroundFailed(backgroundUrl) },
                    modifier = bgModifier.blur(blurDp, BlurredEdgeTreatment.Rectangle),
                    cropFromTop = 0.5f,
                )
            } else {
                SampledBlurredVideo(backgroundUrl, { onBackgroundFailed(backgroundUrl) }, bgModifier)
            }
        }
        // The veil, so the words stay readable over whatever moves behind them.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = (if (showLyrics) VEIL_LYRICS else VEIL_PLAIN) / 255f))
        }

        // The cover: the animated one over the still one, both the same size and corners.
        if (hasCover) {
            val coverDp = with(density) { coverSize.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((w - coverSize) / 2, coverTop) }
                    .size(coverDp)
                    .clip(RoundedCornerShape(COVER_CORNER_RADIUS_DP.dp)),
            ) {
                ui.cover?.let { still ->
                    Image(still.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                ui.appleUrl?.let { url ->
                    MotionCoverVideo(url = url, visible = true, onFailed = { onCoverFailed(url) }, modifier = Modifier.fillMaxSize(), cropFromTop = 0.5f)
                }
            }
        }

        // The song's name, and what stands in for lyrics when there are none.
        val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT } }
        val ready = ui.lyrics
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                if (track != null) {
                    val titleY = songTop + titleSize * 1.25f
                    drawCentred(canvas, textPaint, track.title, w / 2f, titleY, titleSize, Typeface.DEFAULT_BOLD, 255, w * 0.9f)
                    drawCentred(canvas, textPaint, track.artist, w / 2f, titleY + titleSize * 1.15f, titleSize * 0.85f, Typeface.DEFAULT, 180, w * 0.9f)
                }
                if (!showLyrics) return@drawIntoCanvas
                val message = when {
                    track == null -> WAITING
                    ready == null -> ui.notice
                    ready.lines.isEmpty() -> NO_LYRICS
                    else -> null
                }
                message?.let { drawCentred(canvas, textPaint, it, w / 2f, h * 0.5f, titleSize * 0.8f, Typeface.DEFAULT, 150, w * 0.9f) }
            }
        }

        // The lyrics, by the lyrics screen's own renderer.
        if (showLyrics && track != null && ready != null && ready.lines.isNotEmpty()) {
            val heightDp = with(density) { (h - lyricsTop).coerceAtLeast(1).toDp() }
            WallpaperLyrics(
                lyrics = ready,
                settings = settings,
                positionMs = positionMs,
                isPlaying = ui.playing,
                visible = { ui.visible },
                durationMs = track.durationMs,
                modifier = Modifier.offset { IntOffset(0, lyricsTop) }.fillMaxWidth().height(heightDp),
            )
        }
    }
}

/** The lyrics as the lyrics screen draws them, with its settings ([RenderConfig] built the same way). */
@Composable
private fun WallpaperLyrics(
    lyrics: LyricsState.Ready,
    settings: AppSettings,
    positionMs: () -> Long,
    isPlaying: Boolean,
    visible: () -> Boolean,
    durationMs: Long,
    modifier: Modifier,
) {
    val appleMusicStyle = settings.lyricsStyle == LyricsStyle.AppleMusic
    val config = RenderConfig(
        simpleLyricsMode = settings.simpleLyricsMode && !appleMusicStyle,
        minimalLyricsMode = settings.minimalLyricsMode && !appleMusicStyle,
        appleMusic = appleMusicStyle,
        refreshedSimple = settings.refreshedSimpleLyrics && settings.simpleLyricsMode && !appleMusicStyle,
        wordMotionBoost = settings.wordMotionBoost,
        wideDuetPadding = settings.duetLinePadding,
        creditsFollowLastLine = settings.creditsFollowLastLine,
        refinedCredits = settings.refinedCredits,
        backgroundPop = settings.backgroundPop && !appleMusicStyle,
        keepOverlappingLines = settings.keepOverlappingLines,
        syllableMerge = settings.syllableMerge,
        syllablePace = settings.syllablePace,
        distanceBlurEnabled = settings.distanceBlur && !settings.lowPerformance,
        glowEnabled = settings.glow && !settings.lowPerformance,
        scroll = ScrollConfig(
            leadMs = if (settings.scrollLeadEnabled) settings.scrollLeadMs.coerceAtLeast(0).toLong() else 0L,
            smooth = settings.smoothScrolling,
            seekFadeCompensation = settings.seekFadeCompensation,
            staticSongMs = if (settings.staticAutoScroll) durationMs else 0L,
        ),
    )
    val originalLines = remember(lyrics.lines) {
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
    val rendererLines = remember(originalLines, config.isMinimal, config.isSimple) {
        buildDisplayTimeline(originalLines, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
            LyricsType.Syllable -> config.isMinimal
            LyricsType.Line -> config.isSimple
            else -> false
        })
    }
    LyricsView(
        lines = rendererLines,
        documentId = remember(lyrics.lines) { lyrics.lines.hashCode().toString() },
        currentTimeMs = positionMs,
        onSeekWord = {},
        isPlaying = isPlaying,
        visible = visible,
        focusAnchorFraction = LYRICS_FOCUS_FRACTION,
        lyricsType = lyrics.lyricsType,
        config = config,
        fontSizeScale = settings.lyricsScale(false),
        romanizationMode = settings.romanizationMode,
        footer = remember(lyrics) {
            LyricsFooter(
                songwriters = lyrics.songwriters,
                provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
            )
        },
        modifier = modifier,
    )
}

/** The lyrics font chosen in the app, as the lyrics screen sets it. */
@Composable
private fun LyricsFontSetup(settings: AppSettings) {
    val context = LocalContext.current
    val lyricsFont = settings.lyricsFont
    val customFontFile = settings.customFontFile
    val fontFamily = remember(lyricsFont, customFontFile) {
        when (lyricsFont) {
            LyricsFont.Default -> null
            LyricsFont.System -> FontFamily.Default
            LyricsFont.Custom -> LyricsFontFile.family(context, customFontFile)
        }
    }
    SideEffect {
        LyricsLayoutCalculator.setFont(fontFamily, if (fontFamily == null) "" else "$lyricsFont:$customFontFile")
    }
}

/** The blurred still cover, breathing slowly while the music plays, or a dark gradient with no cover. */
@Composable
private fun StillBackground(ui: WallpaperUi, w: Int, h: Int, shiftPx: Float, animate: Boolean) {
    val cover = ui.cover
    val blurred = remember(cover) {
        cover?.let { art ->
            val small = Bitmap.createScaledBitmap(art, COVER_BLUR_WIDTH, max(1, COVER_BLUR_WIDTH * art.height / art.width), true)
            StackBlur.blur(small, COVER_BLUR_RADIUS)
        }
    }
    var nowMs by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    val running = animate && blurred != null && ui.playing && ui.visible
    LaunchedEffect(running) {
        while (running) {
            androidx.compose.runtime.withFrameMillis { nowMs = android.os.SystemClock.uptimeMillis() }
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val gradient = remember(h) {
        LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF141419.toInt(), 0xFF1E1E26.toInt()), null, Shader.TileMode.CLAMP)
    }
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            if (blurred != null) {
                val breathe = 1.05f + 0.03f * sin(nowMs / 12_000.0).toFloat()
                val scale = max(w.toFloat() / blurred.width, h.toFloat() / blurred.height) * breathe * PARALLAX_OVERSCAN
                val drawnW = blurred.width * scale
                val drawnH = blurred.height * scale
                val left = (w - drawnW) / 2f + shiftPx
                val top = (h - drawnH) / 2f
                canvas.drawBitmap(blurred, null, RectF(left, top, left + drawnW, top + drawnH), paint)
            } else {
                paint.shader = gradient
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
            }
        }
    }
}

/**
 * Below Android 12, which has no GPU blur for views: the loop sampled small, ~30 times a second,
 * blurred on the stream's own thread and stretched over the screen.
 */
@Composable
private fun SampledBlurredVideo(url: String, onFailed: () -> Unit, modifier: Modifier) {
    val stream = rememberMotionCoverStream(url, onFailed)
    var frame by remember(url) { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(stream) {
        val main = Handler(Looper.getMainLooper())
        var live = true
        val stop = stream.addSampler(SAMPLE_LONG_SIDE, SAMPLE_INTERVAL_MS) { sample ->
            val bitmap = runCatching {
                StackBlur.blur(Bitmap.createBitmap(sample.pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888), SAMPLE_BLUR_RADIUS)
            }.getOrNull() ?: return@addSampler
            main.post { if (live) frame = bitmap }
        }
        onDispose {
            live = false
            stop()
        }
    }
    frame?.let {
        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

/** One centred line, shrunk until it fits [maxWidth]. */
private fun drawCentred(canvas: android.graphics.Canvas, paint: Paint, text: String, cx: Float, y: Float, size: Float, face: Typeface, alpha: Int, maxWidth: Float) {
    paint.typeface = face
    paint.color = android.graphics.Color.WHITE
    paint.alpha = alpha
    var s = size
    paint.textSize = s
    while (s > 20f && paint.measureText(text) > maxWidth) {
        s *= 0.9f
        paint.textSize = s
    }
    canvas.drawText(text, cx - paint.measureText(text) / 2f, y, paint)
}

private const val WAITING = "Play something to see its lyrics here"
private const val NO_LYRICS = "No lyrics for this song"
/** The cover's width as a fraction of the screen's short side (its width in portrait). */
private const val COVER_WIDTH_FRACTION = 0.65f
/** The cover's corner radius, in dp — the same for the animated and the still cover. */
private const val COVER_CORNER_RADIUS_DP = 24
/** How far the background drifts as the home screen slides between pages. */
private const val PARALLAX_FRACTION = 0.08f
/** The background drawn this much bigger than the screen: the drift's full reach, both sides. */
private const val PARALLAX_OVERSCAN = 1f + PARALLAX_FRACTION
/** The veil over the moving picture: heavier under the words. */
private const val VEIL_LYRICS = 100
private const val VEIL_PLAIN = 70
/** The background video's blur: 6 px on the screen. */
private const val BG_BLUR_PX = 6f
/** Where the line being sung sits in the lyrics' area, from its top, as before. */
private const val LYRICS_FOCUS_FRACTION = 0.42f
/** The still cover's blur, on a [COVER_BLUR_WIDTH] px copy: about 6 px once stretched over the screen. */
private const val COVER_BLUR_WIDTH = 540
private const val COVER_BLUR_RADIUS = 3
/** The pre-Android 12 background: sampled this big and this often, blurred to about 6 px on screen. */
private const val SAMPLE_LONG_SIDE = 360
private const val SAMPLE_INTERVAL_MS = 33L
private const val SAMPLE_BLUR_RADIUS = 2
