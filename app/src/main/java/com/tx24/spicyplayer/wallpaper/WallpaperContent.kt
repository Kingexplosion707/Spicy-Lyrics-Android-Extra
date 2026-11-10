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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.tx24.spicyplayer.lyrics.lyricsTint
import com.tx24.spicyplayer.lyrics.rememberLyricsTint
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
import com.tx24.spicyplayer.ui.settings.WallpaperCover
import androidx.compose.ui.draw.clipToBounds
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.translation.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the wallpaper shows, written by its engine on the main thread and read by [WallpaperContent]. */
internal class WallpaperUi {
    var track by mutableStateOf<BackgroundSession.Track?>(null)
    var lyrics by mutableStateOf<LyricsState.Ready?>(null)
    /** The lyrics' translation, as the lyrics screen would show it; null while there's none. */
    var translation by mutableStateOf<TranslationResult?>(null)
    /** Said in place of the lyrics: none found, the lookup failed. */
    var notice by mutableStateOf<String?>(null)
    var canvasUrl by mutableStateOf<String?>(null)
    var appleUrl by mutableStateOf<String?>(null)
    var cover by mutableStateOf<Bitmap?>(null)
    var playing by mutableStateOf(false)
    var visible by mutableStateOf(false)
    /** The home screen's page position, 0..1, for the background's drift. */
    var xOffset by mutableStateOf(0.5f)
    /** The phone's battery saver is on (for power saving set to follow it). */
    var batterySaver by mutableStateOf(false)
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
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (com.tx24.spicyplayer.wallpaper.changesLook(key)) settingsVersion++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val settings = remember(settingsVersion) { AppSettings(prefs) }
    val showLyrics = remember(settingsVersion) { prefs.getBoolean("wallpaperShowLyrics", true) }
    val saving = remember(settingsVersion) { PowerSaving.from(prefs) }.let { it.cuts(prefs, ui.batterySaver) }
    LyricsFontSetup(settings)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0) return@BoxWithConstraints
        val track = ui.track
        // Nothing playing (no song, or paused a moment): the picture picked for that, if any.
        val idlePicture = rememberIdlePicture(prefs, settingsVersion, w, h)
        var paused by remember { mutableStateOf(!ui.playing) }
        LaunchedEffect(ui.playing) {
            if (ui.playing) paused = false else { delay(IDLE_AFTER_MS); paused = true }
        }
        if (idlePicture != null && (track == null || paused || saving.stopAll)) {
            Image(idlePicture.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        // Saving power with nothing picked to show: a still frame (the blurred cover, the cover
        // and the song's name), nothing moving.
        val still = saving.stopAll
        val titleSize = (minOf(w, h) * 0.045f).coerceIn(30f, 64f)
        val coverChoice = settings.wallpaperCover
        // The covers trading places on a song change (see CoverHandoff). Started in this same
        // composition, so the first frame of a new song already shows the old cover in place.
        val handoff = rememberCoverHandoff(track?.key, ui.cover)
        val handing by remember(handoff) { androidx.compose.runtime.derivedStateOf { handoff.running } }
        // A new song whose cover hasn't come yet keeps the layout it had: nothing jumps up into
        // the cover's room and back.
        val hasCover = coverChoice != WallpaperCover.Hidden && (ui.appleUrl != null || ui.cover != null || handing)
        // A song with no lyrics: the cover grows and the cover and song's name sit centred.
        val noLyrics = showLyrics && track != null && hasCover &&
            ((ui.lyrics == null && ui.notice != null) || ui.lyrics?.lines?.isEmpty() == true)
        // On its side, the cover and the song's name take the left of the screen and the lyrics
        // the right, as on the lyrics screen; with no lyrics to show, the cover has the middle.
        val landscape = w > h
        val beside = landscape && showLyrics && !noLyrics && track != null
        val areaW = if (beside) (w * LANDSCAPE_PANEL).roundToInt() else w
        val bigCover = minOf(w, h) * (if (landscape) LANDSCAPE_BIG_COVER else BIG_COVER_WIDTH_FRACTION)
        val coverTarget = when {
            !hasCover -> 0f
            noLyrics -> bigCover
            landscape -> minOf(h * LANDSCAPE_COVER, areaW * 0.8f) * coverChoice.fraction / COVER_WIDTH_FRACTION
            else -> minOf(w, h) * coverChoice.fraction
        }
        val topTarget = when {
            noLyrics || landscape -> (h - (coverTarget + titleSize * 2.6f)) / 2f
            showLyrics -> h * 0.055f
            else -> h * 0.16f
        }
        val coverSize = animateFloatAsState(coverTarget, tween(COVER_MOVE_MS), label = "coverSize").value.roundToInt()
        val coverTop = animateFloatAsState(topTarget, tween(COVER_MOVE_MS), label = "coverTop").value.roundToInt()
        val songTop = if (hasCover) coverTop + coverSize else coverTop
        val lyricsTop = if (beside) (h * LANDSCAPE_LYRICS_INSET).roundToInt() else (songTop + titleSize * 3.1f).roundToInt()
        val lyricsLeft = if (beside) areaW else 0
        val centreX = areaW / 2f
        // Read where it's drawn, not here: sliding between pages then moves the layers without
        // recomposing (and redrawing) everything above them.
        val shiftPx = { (0.5f - ui.xOffset) * w * PARALLAX_FRACTION }

        // The background: the Canvas (unless power saving stops it), else the animated cover
        // (likewise), else the blurred still cover.
        // The new song's videos wait until the covers have settled, so starting a player doesn't
        // land on the animation's frames.
        val canvasUrl = ui.canvasUrl.takeUnless { saving.noCanvas || still || handing }
        val appleUrl = ui.appleUrl.takeUnless { saving.noAnimatedCover || still || handing }
        val backgroundUrl = canvasUrl ?: appleUrl
        // The still background blends into the next song's, as the covers trade places. It blends
        // from one blurred picture to the next, once the next is ready, never through a blank one.
        val blurredCover = rememberBlurredCover(track?.key, ui.cover)
        androidx.compose.animation.Crossfade(targetState = blurredCover, animationSpec = tween(BACKGROUND_BLEND_MS), label = "background") { blurred ->
            StillBackground(ui, blurred, w, h, shiftPx, animate = backgroundUrl == null && !still && !saving.active)
        }
        // A video leaving (the song changed) fades out over the still background instead of
        // vanishing, and the next song's fades in.
        val lastBackground = remember { arrayOfNulls<String>(1) }
        if (backgroundUrl != null) lastBackground[0] = backgroundUrl
        val bgAlpha = remember { androidx.compose.animation.core.Animatable(if (backgroundUrl != null) 1f else 0f) }
        LaunchedEffect(backgroundUrl) {
            if (backgroundUrl != null) bgAlpha.animateTo(1f, tween(VIDEO_FADE_IN_MS))
            else bgAlpha.animateTo(0f, tween(VIDEO_FADE_OUT_MS))
        }
        val bgShowing by remember { androidx.compose.runtime.derivedStateOf { bgAlpha.value > 0.01f } }
        val shownBackground = backgroundUrl ?: lastBackground[0]?.takeIf { bgShowing && !still }
        if (shownBackground != null) {
            val backgroundUrl = shownBackground
            // Grown by the drift's reach, so sliding between pages never shows its edge.
            val bgModifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = bgAlpha.value
                scaleX = PARALLAX_OVERSCAN
                scaleY = PARALLAX_OVERSCAN
                translationX = shiftPx()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Blurred at a quarter of the screen's size and stretched back up: the same soft
                // 6 px look for a sixteenth of the GPU work, every frame.
                val smallW = with(density) { (w / BG_DOWNSCALE).toDp() }
                val smallH = with(density) { (h / BG_DOWNSCALE).toDp() }
                val blurDp = with(density) { (BG_BLUR_PX / BG_DOWNSCALE).coerceAtLeast(1f).toDp() }
                Box(bgModifier) {
                    MotionCoverVideo(
                        url = backgroundUrl,
                        visible = true,
                        onFailed = { onBackgroundFailed(backgroundUrl) },
                        modifier = Modifier
                            .size(smallW, smallH)
                            .graphicsLayer {
                                scaleX = BG_DOWNSCALE.toFloat()
                                scaleY = BG_DOWNSCALE.toFloat()
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                            }
                            .blur(blurDp, BlurredEdgeTreatment.Rectangle),
                        cropFromTop = 0.5f,
                    )
                }
            } else {
                SampledBlurredVideo(backgroundUrl, { onBackgroundFailed(backgroundUrl) }, bgModifier)
            }
        }
        // The veil, so the words stay readable over whatever moves behind them.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(androidx.compose.ui.graphics.Color.Black.copy(
                alpha = ((if (showLyrics) VEIL_LYRICS else VEIL_PLAIN) / 255f * settings.wallpaperDim / 100f).coerceIn(0f, 0.92f),
            ))
        }

        // The cover: the animated one over the still one, both the same size and corners. On a
        // song change the covers trade places as Spotify's automix does (see CoverHandoff).
        val coverDp = with(density) { coverSize.toDp() }
        val handoffOld = handoff.old
        if (handing && handoffOld != null) {
            // Beside the lyrics, the covers trade places inside their own side of the screen.
            Box(Modifier.size(with(density) { areaW.toDp() }, with(density) { h.toDp() }).then(if (beside) Modifier.clipToBounds() else Modifier)) {
                CoverHandoffCovers(handoff, handoffOld, ui.cover, areaW, coverSize, coverTop, coverDp)
            }
        } else if (hasCover) {
            Box(
                Modifier
                    .offset { IntOffset((areaW - coverSize) / 2, coverTop) }
                    .size(coverDp)
                    .clip(RoundedCornerShape(COVER_CORNER_RADIUS_DP.dp)),
            ) {
                ui.cover?.let { still ->
                    Image(still.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                appleUrl?.let { url ->
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
                    drawCentred(canvas, textPaint, track.title, centreX, titleY, titleSize, Typeface.DEFAULT_BOLD, 255, areaW * 0.9f)
                    drawCentred(canvas, textPaint, track.artist, centreX, titleY + titleSize * 1.15f, titleSize * 0.85f, Typeface.DEFAULT, 180, areaW * 0.9f)
                }
                if (!showLyrics) return@drawIntoCanvas
                val message = when {
                    track == null -> WAITING
                    noLyrics -> null
                    ready == null -> ui.notice
                    ready.lines.isEmpty() -> NO_LYRICS
                    else -> null
                }
                // Beside the cover on its side, where the lyrics would be; under it upright.
                val messageLeft = if (beside) areaW else 0
                message?.let {
                    drawCentred(canvas, textPaint, it, messageLeft + (w - messageLeft) / 2f, h * 0.5f, titleSize * 0.8f, Typeface.DEFAULT, 150, (w - messageLeft) * 0.9f)
                }
            }
        }

        // The lyrics, by the lyrics screen's own renderer.
        if (showLyrics && !still && track != null && ready != null && ready.lines.isNotEmpty()) {
            val heightDp = with(density) { (if (beside) h - 2 * lyricsTop else h - lyricsTop).coerceAtLeast(1).toDp() }
            val widthDp = with(density) { (w - lyricsLeft).coerceAtLeast(1).toDp() }
            WallpaperLyrics(
                // Line by line when power saving asks: each line lights as a whole.
                lyrics = if (saving.lineByLine && ready.lyricsType == LyricsType.Syllable) ready.copy(lyricsType = LyricsType.Line) else ready,
                reduced = saving.simplerAnimation,
                translation = ui.translation,
                settings = settings,
                positionMs = positionMs,
                isPlaying = ui.playing,
                visible = { ui.visible },
                durationMs = track.durationMs,
                modifier = Modifier.offset { IntOffset(lyricsLeft, lyricsTop) }.size(widthDp, heightDp),
                focusAnchorFraction = if (beside) 0.5f else LYRICS_FOCUS_FRACTION,
                fontSizeScale = settings.lyricsScale(landscape) * settings.wallpaperLyricsSize / 100f,
                tint = rememberLyricsTint(settings, ui.cover),
            )
        }
    }
}

/**
 * The lyrics as the lyrics screen draws them, with its settings ([RenderConfig] built the same
 * way): for what shows the lyrics outside it, the wallpaper and the overlay.
 */
@Composable
internal fun WallpaperLyrics(
    lyrics: LyricsState.Ready,
    translation: TranslationResult?,
    settings: AppSettings,
    positionMs: () -> Long,
    isPlaying: Boolean,
    visible: () -> Boolean,
    durationMs: Long,
    modifier: Modifier,
    focusAnchorFraction: Float = LYRICS_FOCUS_FRACTION,
    fontSizeScale: Float = settings.lyricsScale(false),
    /** Glow on or off here whatever the app's Glow setting; null follows it. */
    glow: Boolean? = null,
    /** Simpler motion to save power: no glow, distance blur, word lift or background pop. */
    reduced: Boolean = false,
    /** The glow's strength here (1 is normal); null follows the app's Glow strength. */
    glowStrength: Float? = null,
    /** The lyrics' colour (Lyrics colour); white draws them as they are. */
    tint: com.tx24.spicyplayer.lyrics.LyricsInk = com.tx24.spicyplayer.lyrics.LyricsInk.White,
    /** The most frames a second the lyrics draw; 0 for every frame. */
    maxFrameRate: Float = 0f,
) {
    val appleMusicStyle = settings.lyricsStyle == LyricsStyle.AppleMusic
    val config = RenderConfig(
        simpleLyricsMode = settings.simpleLyricsMode && !appleMusicStyle,
        minimalLyricsMode = settings.minimalLyricsMode && !appleMusicStyle,
        appleMusic = appleMusicStyle,
        refreshedSimple = settings.refreshedSimpleLyrics && settings.simpleLyricsMode && !appleMusicStyle,
        wordMotionBoost = if (reduced) 0f else settings.wordMotionBoost,
        wideDuetPadding = settings.duetLinePadding,
        creditsFollowLastLine = settings.creditsFollowLastLine,
        refinedCredits = settings.refinedCredits,
        backgroundPop = settings.backgroundPop && !appleMusicStyle && !reduced,
        keepOverlappingLines = settings.keepOverlappingLines,
        syllableMerge = settings.syllableMerge,
        syllablePace = settings.syllablePace,
        distanceBlurEnabled = settings.distanceBlur && !settings.lowPerformance && !reduced,
        glowEnabled = (glow ?: settings.glow) && !settings.lowPerformance && !reduced,
        glowStrength = glowStrength ?: (settings.glowStrength / 100f),
        otherLinesOpacity = settings.otherLinesBrightness / 100f,
        scroll = ScrollConfig(
            leadMs = if (settings.scrollLeadEnabled) settings.scrollLeadMs.coerceAtLeast(0).toLong() else 0L,
            smooth = settings.smoothScrolling,
            seekFadeCompensation = settings.seekFadeCompensation,
            staticSongMs = if (settings.staticAutoScroll) durationMs else 0L,
        ),
    )
    // Prepared off the main thread: new lyrics arriving mid-transition don't stall its frames.
    val prepared by androidx.compose.runtime.produceState<Pair<List<Line>, List<Line>>?>(null, lyrics.lines, config.isMinimal, config.isSimple, lyrics.lyricsType) {
        value = withContext(Dispatchers.Default) {
            val original = toRendererLines(lyrics)
            original to buildDisplayTimeline(original, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
                LyricsType.Syllable -> config.isMinimal
                LyricsType.Line -> config.isSimple
                else -> false
            })
        }
    }
    val (originalLines, rendererLines) = prepared ?: return
    val translationMode = settings.translationMode
    val presentation = remember(rendererLines, originalLines, translation, translationMode) {
        translation?.let { TranslationPresentation.forTimeline(originalLines, rendererLines, it, translationMode) }
    }
    // Romanized as on the lyrics screen: with Romanize on, where the lyrics have it.
    val romanize = settings.romanize && remember(lyrics) { lyrics.lines.any { line -> line.words.any { it.romanized != null } } }
    val textCredits = settings.textCredits
    LyricsView(
        lines = rendererLines,
        documentId = remember(lyrics.lines) { lyrics.lines.hashCode().toString() },
        currentTimeMs = positionMs,
        onSeekWord = {},
        romanize = romanize,
        translation = presentation,
        isPlaying = isPlaying,
        visible = visible,
        maxFrameRate = maxFrameRate,
        focusAnchorFraction = focusAnchorFraction,
        lyricsType = lyrics.lyricsType,
        config = config,
        fontSizeScale = fontSizeScale,
        romanizationMode = settings.romanizationMode,
        footer = remember(lyrics, translation, textCredits) {
            LyricsFooter(
                songwriters = lyrics.songwriters,
                translationCredit = translation?.credit()?.takeIf { textCredits },
                provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
            )
        },
        modifier = modifier.lyricsTint(tint),
    )
}

/**
 * The covers trading places on a song change, as Spotify's automix shows it: the old cover
 * shrinks and moves left as the new one slides in small from the right; both rest side by side
 * a moment; then the old one slides away left and the new one moves to the middle and grows.
 * [progress] runs 0→1 (in), holds, then 1→2 (out); at 2 it's done.
 */
@androidx.compose.runtime.Stable
internal class CoverHandoff {
    var old by mutableStateOf<Bitmap?>(null)
    val progress = androidx.compose.animation.core.Animatable(2f)
    /** Begun this frame, before its animation has been set to the start. */
    var fresh by mutableStateOf(false)
    val running: Boolean get() = old != null
    /** Where the handoff is; one just begun is at the start. */
    fun at(): Float = if (fresh) 0f else progress.value
}

/** Starts a [CoverHandoff] whenever the song changes from one with a cover. */
@Composable
private fun rememberCoverHandoff(key: String?, cover: Bitmap?): CoverHandoff {
    val handoff = remember { CoverHandoff() }
    // The song shown and its cover, read as the composition sees them, so the outgoing cover is
    // caught before the new song's replaces it.
    val seen = remember { SeenSong(key, cover) }
    var start by remember { mutableStateOf<Pair<Bitmap, Long>?>(null) }
    val incoming = androidx.compose.runtime.rememberUpdatedState(cover)
    if (key != seen.key) {
        val outgoing = seen.cover
        if (key != null && seen.key != null && outgoing != null) {
            start = outgoing to System.nanoTime()
            // Shown from this frame on, so the old cover never blinks out before it moves.
            handoff.old = outgoing
            handoff.fresh = true
        }
        seen.key = key
        // The player can report the new song before its art, still holding the old picture.
        seen.cover = cover.takeUnless { it === outgoing }
    } else if (cover != null) {
        seen.cover = cover
    }
    LaunchedEffect(start) {
        val (outgoing, _) = start ?: return@LaunchedEffect
        handoff.old = outgoing
        handoff.progress.snapTo(0f)
        handoff.fresh = false
        // The new song's cover usually comes a moment after its name: wait a little for it, so it
        // slides in rather than an empty space.
        kotlinx.coroutines.withTimeoutOrNull(HANDOFF_WAIT_MS) {
            androidx.compose.runtime.snapshotFlow { incoming.value }.first { it != null && it !== outgoing }
        }
        handoff.progress.animateTo(1f, tween(HANDOFF_IN_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(HANDOFF_HOLD_MS)
        handoff.progress.animateTo(2f, tween(HANDOFF_OUT_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        handoff.old = null
    }
    return handoff
}

private class SeenSong(var key: String?, var cover: Bitmap?)

/** The two covers mid-handoff, laid out where the one cover sits and moved from there. */
@Composable
private fun CoverHandoffCovers(handoff: CoverHandoff, old: Bitmap, new: Bitmap?, w: Int, coverSize: Int, coverTop: Int, coverDp: androidx.compose.ui.unit.Dp) {
    val small = coverSize * HANDOFF_SMALL
    val gap = coverSize * HANDOFF_GAP
    val center = w / 2f
    val leftSlot = center - (small + gap) / 2f
    val rightSlot = center + (small + gap) / 2f
    // Where each cover is at the handoff's progress: read only while drawing, so each frame
    // moves two layers and recomposes nothing.
    fun oldAt(t: Float): Pair<Float, Float> {
        val inP = t.coerceIn(0f, 1f)
        val outP = (t - 1f).coerceIn(0f, 1f)
        return if (t <= 1f) lerpF(center, leftSlot, inP) to lerpF(1f, HANDOFF_SMALL, inP) else lerpF(leftSlot, -small, outP) to HANDOFF_SMALL
    }
    fun newAt(t: Float): Pair<Float, Float> {
        val inP = t.coerceIn(0f, 1f)
        val outP = (t - 1f).coerceIn(0f, 1f)
        return if (t <= 1f) lerpF(w + small, rightSlot, inP) to HANDOFF_SMALL else lerpF(rightSlot, center, outP) to lerpF(HANDOFF_SMALL, 1f, outP)
    }
    val oldImage = remember(old) { old.asImageBitmap() }
    val newImage = remember(new, old) { new?.takeUnless { it === old }?.asImageBitmap() }
    @Composable
    fun cover(image: androidx.compose.ui.graphics.ImageBitmap, place: (Float) -> Pair<Float, Float>) {
        Box(
            Modifier
                .offset { IntOffset((w - coverSize) / 2, coverTop) }
                .size(coverDp)
                .graphicsLayer {
                    val (x, scale) = place(handoff.at())
                    translationX = x - center
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(COVER_CORNER_RADIUS_DP.dp)
                    clip = true
                },
        ) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
    cover(oldImage, ::oldAt)
    newImage?.let { cover(it, ::newAt) }
}

private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * Whether a written setting can change how the wallpaper or overlay look: not the bookkeeping
 * written as songs play (lookup statuses, the overlay's dragged place, the clock-swap marker),
 * which would otherwise rebuild them each time.
 */
internal fun changesLook(key: String?): Boolean = key == null || !(
    key.endsWith("Status") || key == "overlayX" || key == "overlayY" || key == "overlayClockHiddenByUs"
    )

/** [lyrics]' lines as the renderer's models. */
private fun toRendererLines(lyrics: LyricsState.Ready): List<Line> = lyrics.lines.map { line ->
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

/** The picture for when nothing plays, loaded at about the screen's size, or null with none. */
@Composable
private fun rememberIdlePicture(prefs: SharedPreferences, settingsVersion: Int, w: Int, h: Int): Bitmap? {
    val context = LocalContext.current
    val stamp = remember(settingsVersion) { prefs.getLong(WallpaperIdlePicture.PREF_STAMP, 0L) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(stamp, w, h) {
        picture = if (stamp == 0L) null else withContext(Dispatchers.IO) { WallpaperIdlePicture.load(context, w, h) }
    }
    return picture
}

/** The lyrics font chosen in the app, as the lyrics screen sets it. */
@Composable
internal fun LyricsFontSetup(settings: AppSettings) {
    val context = LocalContext.current
    val lyricsFont = settings.lyricsFont
    val customFontFile = settings.customFontFile
    val galleryFont = settings.galleryFont
    // Ours again when the picked file has gone.
    val fontFamily = remember(lyricsFont, customFontFile, galleryFont) {
        when (lyricsFont) {
            LyricsFont.Default -> null
            LyricsFont.System -> FontFamily.Default
            LyricsFont.Custom -> LyricsFontFile.family(context, customFontFile)
            LyricsFont.Gallery -> com.tx24.spicyplayer.ui.settings.FontGallery.family(context, galleryFont)
        }
    }
    val weight = settings.lyricsWeight
    val gap = settings.lineSpacing
    val centred = settings.lyricsCentered
    SideEffect {
        LyricsLayoutCalculator.setTypography(
            fontFamily, weight.weight, gap / 100f, centred,
            (if (fontFamily == null) "" else "$lyricsFont:$customFontFile:$galleryFont") + "|$weight|$gap|$centred",
        )
    }
}

/**
 * The song's cover blurred for the background, made off the main thread so a song change doesn't
 * stall the frames animating it. While a new song's cover is still on its way (or being blurred)
 * the last one stays; with no song, none.
 */
@Composable
private fun rememberBlurredCover(key: String?, cover: Bitmap?): Bitmap? {
    val blurred = androidx.compose.runtime.produceState<Bitmap?>(null, key == null, cover) {
        if (key == null) { value = null; return@produceState }
        val art = cover ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val small = Bitmap.createScaledBitmap(art, COVER_BLUR_WIDTH, max(1, COVER_BLUR_WIDTH * art.height / art.width), true)
                StackBlur.blur(small, COVER_BLUR_RADIUS)
            }.getOrNull()
        } ?: value
    }
    return blurred.value
}

/** The blurred still cover, breathing slowly while the music plays, or a dark gradient with no cover. */
@Composable
private fun StillBackground(ui: WallpaperUi, blurred: Bitmap?, w: Int, h: Int, shiftPx: () -> Float, animate: Boolean) {
    var nowMs by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    val running = animate && blurred != null && ui.playing && ui.visible
    // The breathing is a 12-second drift: 20 steps a second are plenty, not every display frame.
    LaunchedEffect(running) {
        while (running) {
            nowMs = android.os.SystemClock.uptimeMillis()
            delay(BREATHE_STEP_MS)
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
                val left = (w - drawnW) / 2f + shiftPx()
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

/** Paused this long, the wallpaper shows the picture for when nothing plays. */
private const val IDLE_AFTER_MS = 3_000L
private const val WAITING = "Play something to see its lyrics here"
private const val NO_LYRICS = "No lyrics for this song"
/** The cover's width as a fraction of the screen's short side (its width in portrait). */
private const val COVER_WIDTH_FRACTION = 0.65f
/** With no lyrics for the song, the cover at this share of the screen's width, centred. */
private const val BIG_COVER_WIDTH_FRACTION = 0.85f
/** On its side: the cover's share of the screen's width, with the lyrics in the rest. */
private const val LANDSCAPE_PANEL = 0.42f
/** On its side: the cover's height as a share of the screen's, and with no lyrics. */
private const val LANDSCAPE_COVER = 0.56f
private const val LANDSCAPE_BIG_COVER = 0.62f
/** On its side: the lyrics keep this share of the screen's height clear above and below. */
private const val LANDSCAPE_LYRICS_INSET = 0.06f
/** The automix handoff: in, resting side by side, out; the covers' size side by side, and their gap. */
private const val HANDOFF_IN_MS = 650
/** How long a handoff waits for the new song's cover before moving without it. */
private const val HANDOFF_WAIT_MS = 1_500L
private const val VIDEO_FADE_OUT_MS = 500
private const val VIDEO_FADE_IN_MS = 700
private const val HANDOFF_HOLD_MS = 2_500L
private const val HANDOFF_OUT_MS = 750
private const val HANDOFF_SMALL = 0.45f
private const val HANDOFF_GAP = 0.06f
/** The still background's blend into the next song's. */
private const val BACKGROUND_BLEND_MS = 1_500
/** How long the cover takes to grow or settle back. */
private const val COVER_MOVE_MS = 600
/** The cover's corner radius, in dp — the same for the animated and the still cover. */
private const val COVER_CORNER_RADIUS_DP = 24
/** How far the background drifts as the home screen slides between pages. */
private const val PARALLAX_FRACTION = 0.08f
/** The background drawn this much bigger than the screen: the drift's full reach, both sides. */
private const val PARALLAX_OVERSCAN = 1f + PARALLAX_FRACTION
/** The veil over the moving picture: heavier under the words. */
private const val VEIL_LYRICS = 100
private const val VEIL_PLAIN = 70
/** How often the still background's slow breathing steps. */
private const val BREATHE_STEP_MS = 50L
/** The background video's blur: 6 px on the screen. */
private const val BG_BLUR_PX = 6f
/** The background video is blurred at this fraction of the screen's size, then stretched back. */
private const val BG_DOWNSCALE = 4
/** Where the line being sung sits in the lyrics' area, from its top, as before. */
private const val LYRICS_FOCUS_FRACTION = 0.42f
/** The still cover's blur, on a [COVER_BLUR_WIDTH] px copy: about 6 px once stretched over the screen. */
private const val COVER_BLUR_WIDTH = 540
private const val COVER_BLUR_RADIUS = 3
/** The pre-Android 12 background: sampled this big and this often, blurred to about 6 px on screen. */
private const val SAMPLE_LONG_SIDE = 360
private const val SAMPLE_INTERVAL_MS = 33L
private const val SAMPLE_BLUR_RADIUS = 2
