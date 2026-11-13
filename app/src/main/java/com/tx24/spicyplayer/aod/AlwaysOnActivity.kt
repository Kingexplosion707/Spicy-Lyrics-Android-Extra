package com.tx24.spicyplayer.aod

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tx24.spicyplayer.playback.LiveLyrics
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.ui.nowplaying.MotionCoverVideo
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.wallpaper.LyricsFontSetup
import com.tx24.spicyplayer.wallpaper.WallpaperLyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.tx24.spicyplayer.ui.settings.AodStyle
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random

/**
 * The always-on display ([AlwaysOnService] brings it up when the screen turns off): the time and
 * date, the song, and its lyrics drawn by the lyrics screen's own renderer, all dim on black at
 * the lowest brightness, over the lock screen. Everything shifts a little each minute so nothing
 * burns in. A touch, or the back gesture, leaves it for the lock screen.
 */
class AlwaysOnActivity : ComponentActivity() {
    private lateinit var live: LiveLyrics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        // By the light sensor, or the level asked for (Settings → Device → Always-on display).
        autoBrightness = prefs.getBoolean(AodBrightness.PREF_AUTO, true) && light.available
        bias = prefs.getInt(AodBrightness.PREF_BIAS, 0).coerceIn(-40, 40)
        levelState.floatValue = if (autoBrightness) {
            AodBrightness.levelForLux(DARK_ROOM_LUX, bias)
        } else {
            prefs.getInt(AodBrightness.PREF_LEVEL, AodBrightness.DEFAULT_LEVEL).coerceIn(0, 100).toFloat()
        }
        fingerprintUnlock = prefs.getBoolean(AodFingerprint.PREF, true) && AodFingerprint.supported(this)
        window.attributes = window.attributes.apply {
            screenBrightness = AodBrightness.windowBrightness(levelState.floatValue)
            // The lowest refresh rate the screen offers at its size: dim lyrics drifting by don't
            // need 120 Hz, and an always-on screen runs for hours.
            lowestRefreshRateMode()?.let { preferredDisplayModeId = it }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        live = LiveLyrics(this)
        live.start()
        // The Canvas stays off while the phone's battery saver is on.
        val batterySaver = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val canvas = prefs.getBoolean(PREF_CANVAS, false) && !batterySaver
        controls = prefs.getBoolean(PREF_CONTROLS, false)
        doubleTap = prefs.getBoolean(PREF_DOUBLE_TAP, false)
        val settings = AppSettings(prefs)
        val extras = AodExtras(
            weather = AodWeather.enabled(this),
            alarm = settings.aodAlarm,
            battery = settings.aodBattery,
            notifications = settings.aodNotifications,
            ringer = settings.aodRinger,
            openNotification = ::openNotification,
            clockStyle = settings.aodClockStyle,
            clockSize = settings.aodClockSize,
        )
        if (extras.weather) AodWeather.load(this)
        setContent {
            // Eased, so the light changing doesn't jump the screen.
            val level by androidx.compose.animation.core.animateFloatAsState(
                levelState.floatValue, androidx.compose.animation.core.tween(BRIGHTNESS_EASE_MS), label = "aodLevel",
            )
            LaunchedEffect(level) { setWindowBrightness(AodBrightness.windowBrightness(level)) }
            Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { alpha = AodBrightness.contentAlpha(level) }) {
                AlwaysOnContent(live, settings, canvas, controls, extras, onDismiss = ::tapToLeave)
            }
        }
    }

    /** The brightness level now (0 to 100, [AodBrightness]), from the light sensor or the setting. */
    private val levelState = androidx.compose.runtime.mutableFloatStateOf(AodBrightness.DEFAULT_LEVEL.toFloat())
    private var autoBrightness = false
    private var bias = 0
    private val light by lazy { AodBrightness.LightFollower(this) { lux -> levelState.floatValue = AodBrightness.levelForLux(lux, bias) } }

    private fun setWindowBrightness(value: Float) {
        val attrs = window.attributes
        if (kotlin.math.abs(attrs.screenBrightness - value) < 0.0005f) return
        window.attributes = attrs.apply { screenBrightness = value }
    }

    /** Opens a notification from the list: unlocking first, as the lock screen's own do. */
    private fun openNotification(detail: AodNotifications.Detail) {
        val go = {
            runCatching { detail.open?.send(this, 0, null, null, null, null, pendingIntentOptions()) }
            finish()
        }
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { go() }
            })
        } else {
            go()
        }
    }

    /** Lets the notification's app open from here (Android 14 on asks the sender to say so). */
    private fun pendingIntentOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = android.app.ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 36) {
            options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return options.toBundle()
    }

    /** The display mode with this one's size and the lowest refresh rate (not under 30 Hz), if there's a slower one. */
    @Suppress("DEPRECATION")
    private fun lowestRefreshRateMode(): Int? = runCatching {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        val current = display?.mode ?: return null
        display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight && it.refreshRate >= MIN_REFRESH_HZ }
            .minByOrNull { it.refreshRate }
            ?.takeIf { it.refreshRate < current.refreshRate - 1f }
            ?.modeId
    }.getOrNull()

    override fun onResume() {
        super.onResume()
        shownState.value = true
        if (autoBrightness) light.start()
        // The notification icons and the weather as they are now.
        AodNotifications.refresher?.invoke()
        if (AodWeather.enabled(this)) lifecycleScope.launch { AodWeather.refreshIfStale(this@AlwaysOnActivity) }
    }

    override fun onPause() {
        super.onPause()
        shownState.value = false
        light.stop()
        cancelHold()
    }

    override fun onDestroy() {
        live.stop()
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    /** Play/pause and the rest under the lyrics. */
    private var controls = false
    /** Two taps in a row to leave ([PREF_DOUBLE_TAP]). */
    private var doubleTap = false
    private var lastTapAt = 0L

    /** A tap away from the controls: leaves it, or with [doubleTap] only the second of two quick ones. */
    private fun tapToLeave() {
        val now = android.os.SystemClock.uptimeMillis()
        if (!doubleTap || now - lastTapAt <= DOUBLE_TAP_MS) finish() else lastTapAt = now
    }

    /** Holding a Pixel's fingerprint sensor leaves for the lock screen, where it unlocks ([AodFingerprint]). */
    private var fingerprintUnlock = false
    private var holding = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val held = Runnable {
        holding = false
        heldDown?.recycle()
        heldDown = null
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        leaveNow()
    }

    private fun cancelHold() {
        holding = false
        handler.removeCallbacks(held)
    }

    /** Straight to the lock screen, with no animation, so its fingerprint sensor is ready at once. */
    private fun leaveNow() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /** The touch that started on the sensor, handed on as usual if it turns out not to be a hold. */
    private var heldDown: MotionEvent? = null

    private fun replayDown() {
        heldDown?.let { super.dispatchTouchEvent(it); it.recycle() }
        heldDown = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!fingerprintUnlock) return super.dispatchTouchEvent(ev)
        val view = window.decorView
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                heldDown?.recycle()
                heldDown = null
                if (AodFingerprint.area(currentDisplay(), view.width, view.height).contains(ev.x, ev.y)) {
                    holding = true
                    heldDown = MotionEvent.obtain(ev)
                    handler.postDelayed(held, HOLD_MS)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (holding && !AodFingerprint.area(currentDisplay(), view.width, view.height).contains(ev.x, ev.y)) {
                // Slid off the sensor: an ordinary touch after all.
                cancelHold()
                replayDown()
            } else if (holding) {
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (holding) {
                // Let go before it took: an ordinary tap (a button there still works).
                cancelHold()
                replayDown()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(): android.view.Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay

    companion object {
        /** Compose state, so the lyrics rest while it's paused and pick up when it's back. Main thread. */
        private val shownState = mutableStateOf(false)
        /** Whether it's on screen now: the screen turning off then means the power button on it. */
        val showing: Boolean get() = shownState.value
        private var current: WeakReference<AlwaysOnActivity>? = null
        private const val MIN_REFRESH_HZ = 30f
        /** Until the light sensor first answers, it starts as dim as for a dark room. */
        private const val DARK_ROOM_LUX = 5f
        private const val BRIGHTNESS_EASE_MS = 900
        /** How long a finger rests on the fingerprint sensor before it leaves. */
        private const val HOLD_MS = 250L
        /** The song's Spotify Canvas behind it, blurred and darkened. */
        const val PREF_CANVAS = "aodCanvas"
        /** Play/pause, previous and next under the lyrics. */
        const val PREF_CONTROLS = "aodControls"
        /** Two taps in a row to leave. */
        const val PREF_DOUBLE_TAP = "aodDoubleTap"
        /** It only comes up while charging. */
        const val PREF_ONLY_CHARGING = "aodOnlyCharging"
        private const val DOUBLE_TAP_MS = 400L

        fun dismissAll() {
            current?.get()?.finish()
            shownState.value = false
        }
    }
}

@Composable
private fun AlwaysOnContent(live: LiveLyrics, settings: AppSettings, canvas: Boolean, controls: Boolean, extras: AodExtras, onDismiss: () -> Unit) {
    LyricsFontSetup(settings)
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var canvasUrl by remember { mutableStateOf<String?>(null) }
    val track0 = live.track
    LaunchedEffect(canvas, track0?.key) {
        canvasUrl = null
        val track = track0 ?: return@LaunchedEffect
        if (!canvas) return@LaunchedEffect
        canvasUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val meta = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                val ids = SharedSpotify.extras(context.cacheDir).trackIds(meta, track.request.spotifyTrackId)
                ids.firstNotNullOfOrNull { SharedSpotify.canvas(context.cacheDir).find(it) }
            }.getOrNull()
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shift by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        // The clock shows minutes, so it wakes once a minute, on the minute (and moves everything
        // a little against burn-in), not every second.
        while (true) {
            now = System.currentTimeMillis()
            shift = IntOffset(Random.nextInt(-SHIFT_PX, SHIFT_PX + 1), Random.nextInt(-SHIFT_PX, SHIFT_PX + 1))
            delay(60_000L - now % 60_000L + 20L)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        // The Canvas, blurred and darkened under everything, drifting with the same shift.
        canvasUrl?.let { url ->
            val blur = with(density) { CANVAS_BLUR_PX.toDp() }
            val bg = Modifier.fillMaxSize().offset { shift }.graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }
            MotionCoverVideo(
                url = url,
                visible = true,
                onFailed = { canvasUrl = null },
                modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bg.blur(blur, BlurredEdgeTreatment.Rectangle) else bg,
                cropFromTop = 0.5f,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = CANVAS_VEIL)))
        }
        val track = live.track
        if (settings.aodStyle == AodStyle.Player && track != null) {
            // The cover, blurred and darkened, when there's no Canvas.
            if (canvasUrl == null) BlurredCover(track.cover, shift)
            PlayerLayout(live, settings, controls, extras, now, shift)
        } else {
            ClassicLayout(live, settings, controls, extras, now, shift)
        }
    }
}

/** The classic look: a big clock and date, what's around them, the song and its lyrics on black. */
@Composable
private fun ClassicLayout(live: LiveLyrics, settings: AppSettings, controls: Boolean, extras: AodExtras, now: Long, shift: IntOffset) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.FULL) }
    Column(
        Modifier.fillMaxSize().offset { shift }.padding(top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AodClock(now, extras.clockStyle, clockSize(64.sp, extras.clockSize), alpha = 0.9f)
        Text(dateFormat.format(Date(now)), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        AodStatusLine(extras, alpha = 0.6f, modifier = Modifier.padding(top = 10.dp))
        if (extras.notifications) AodNotificationIcons(alpha = 0.6f, modifier = Modifier.padding(top = 10.dp), onOpen = extras.openNotification)
        Spacer(Modifier.height(24.dp))
        val track = live.track
        if (track != null) {
            Text(
                track.title, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                track.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        AodLyrics(live, settings, Modifier.fillMaxWidth().weight(1f), LYRICS_ALPHA)
        if (controls && track != null) Controls(live)
    }
}

/**
 * The player look: as the lyrics screen's player, dimmed. A small clock with what's around it at
 * the top, the cover beside the song, the lyrics in the app's own look, and the progress bar.
 */
@Composable
private fun PlayerLayout(live: LiveLyrics, settings: AppSettings, controls: Boolean, extras: AodExtras, now: Long, shift: IntOffset) {
    val track = live.track ?: return
    val dateFormat = remember { java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()) }
    Column(
        Modifier.fillMaxSize().offset { shift }.padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            AodClock(now, extras.clockStyle, clockSize(44.sp, extras.clockSize), alpha = 0.9f, align = Alignment.Start)
            Spacer(Modifier.weight(1f))
            if (extras.weather) {
                Column(horizontalAlignment = Alignment.End) { AodWeatherRange(alpha = 0.55f) }
            }
        }
        Text(dateFormat.format(Date(now)), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        AodStatusLine(extras, alpha = 0.6f, modifier = Modifier.padding(top = 8.dp), size = 13.sp)
        if (extras.notifications) {
            AodNotificationIcons(alpha = 0.6f, modifier = Modifier.padding(top = 8.dp), onOpen = extras.openNotification, align = Alignment.Start)
        }
        Spacer(Modifier.height(22.dp))
        // As the lyrics screen's header: the cover with the song beside it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cover = remember(track.cover) { track.cover?.asImageBitmap() }
            if (cover != null) {
                AodCover(cover, track, settings)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title, color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AodLyrics(live, settings, Modifier.fillMaxWidth().weight(1f), 1f)
        if (settings.aodProgress && track.durationMs > 0L) ProgressBar(live, track.durationMs)
        if (controls) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Controls(live) }
    }
}

/**
 * The cover beside the song, as on the lyrics screen: the record's animated cover over it when
 * Animated cover is on and the record has one (not with the battery saver on, or low
 * performance mode), and it pauses while the display is off screen.
 */
@Composable
private fun AodCover(cover: androidx.compose.ui.graphics.ImageBitmap, track: com.tx24.spicyplayer.playback.BackgroundSession.Track, settings: AppSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val batterySaver = remember { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }
    val animated = settings.animatedCover && !settings.lowPerformance && !batterySaver
    val url = com.tx24.spicyplayer.ui.nowplaying.rememberMotionCoverUrl(
        animated,
        com.tx24.spicyplayer.ui.nowplaying.MotionCoverQuery(track.artist, track.album, track.title),
    )
    var failed by remember(track.key) { mutableStateOf<String?>(null) }
    Box(Modifier.size(COVER_DP.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).graphicsLayer { alpha = 0.9f }) {
        androidx.compose.foundation.Image(
            cover, null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (url != null && url != failed) {
            MotionCoverVideo(
                url = url,
                visible = AlwaysOnActivity.showing,
                onFailed = { failed = url },
                modifier = Modifier.fillMaxSize(),
                held = !AlwaysOnActivity.showing,
            )
        }
    }
}

/** The lyrics, by the lyrics screen's renderer in the app's look; empty space with none. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AodLyrics(live: LiveLyrics, settings: AppSettings, modifier: Modifier, alpha: Float) {
    val track = live.track
    val lyrics = live.lyrics
    // The last song's lyrics fade out over the new song's on a song change.
    val transition = com.tx24.spicyplayer.lyrics.rememberSongTransition(
        live.lyricsState, live.translation, track?.key, live::positionMs,
        settings.lyricsSongTransition, settings.lyricsHoldUntilFirstLine,
    )
    val outgoing = transition.outgoing
    if (track != null && (outgoing != null || (lyrics != null && lyrics.lines.isNotEmpty()))) {
        val tint = com.tx24.spicyplayer.lyrics.rememberLyricsTint(settings, track.cover)
        Box(modifier.graphicsLayer { this.alpha = alpha }) {
            if (outgoing != null) {
                WallpaperLyrics(
                    lyrics = outgoing.lyrics,
                    translation = outgoing.translation,
                    settings = settings,
                    positionMs = outgoing::timeMs,
                    isPlaying = live.playing && outgoing.runs,
                    visible = { AlwaysOnActivity.showing },
                    durationMs = Long.MAX_VALUE / 4,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = transition.outgoingAlpha() },
                    focusAnchorFraction = 0.35f,
                    tint = tint,
                )
            }
            if (lyrics != null && lyrics.lines.isNotEmpty()) {
                WallpaperLyrics(
                    lyrics = lyrics,
                    translation = live.translation,
                    settings = settings,
                    positionMs = live::positionMs,
                    isPlaying = live.playing,
                    visible = { AlwaysOnActivity.showing },
                    durationMs = track.durationMs,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = transition.incomingAlpha() },
                    focusAnchorFraction = 0.35f,
                    tint = tint,
                )
            }
        }
    } else {
        Spacer(Modifier.weight(1f))
    }
}

/**
 * How far into the song it is, as the lyrics screen's timeline. Moves once a second while
 * playing, and not at all while paused or off screen.
 */
@Composable
private fun ProgressBar(live: LiveLyrics, durationMs: Long) {
    var position by remember { mutableLongStateOf(live.positionMs()) }
    val playing = live.playing
    LaunchedEffect(playing) {
        position = live.positionMs()
        while (playing) {
            delay(1_000L - position % 1_000L + 10L)
            if (AlwaysOnActivity.showing) position = live.positionMs()
        }
    }
    val fraction = (position.toFloat() / durationMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(clock(position), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(clock(durationMs), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** The cover, blurred and darkened, behind the player look. */
@Composable
private fun BlurredCover(cover: android.graphics.Bitmap?, shift: IntOffset) {
    val image = remember(cover) {
        // Small first: blurred anyway, and a small bitmap is cheap to draw for hours.
        cover?.let { android.graphics.Bitmap.createScaledBitmap(it, 64, 64, true).asImageBitmap() }
    } ?: return
    val bg = Modifier.fillMaxSize().offset { shift }.graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
    androidx.compose.foundation.Image(
        image, null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bg.blur(40.dp, BlurredEdgeTreatment.Rectangle) else bg,
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_VEIL)))
}

/**
 * The player's buttons, without crowding: previous, play/pause and next in the middle, and at
 * most two of the player's own beside them, its like button on the left and its shuffle (or
 * repeat) on the right, when it offers them.
 */
@Composable
private fun Controls(live: LiveLyrics) {
    val actions = live.actions
    val like = remember(actions) { actions.firstOrNull { it.matches(LIKE_WORDS) } }
    val mode = remember(actions) { actions.firstOrNull { it.matches(SHUFFLE_WORDS) } ?: actions.firstOrNull { it.matches(REPEAT_WORDS) } }
    Row(
        Modifier.padding(top = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAction(like, live)
        ControlButton(Icons.Rounded.SkipPrevious, "Previous", 30.dp) { live.skipPrevious() }
        ControlButton(if (live.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live.playing) "Pause" else "Play", 40.dp) { live.playPause() }
        ControlButton(Icons.Rounded.SkipNext, "Next", 30.dp) { live.skipNext() }
        PlayerAction(mode, live)
    }
}

/** One of the player's own buttons in its own icon, tinted to match; an empty space of its size when there's none. */
@Composable
private fun PlayerAction(action: android.media.session.PlaybackState.CustomAction?, live: LiveLyrics) {
    val size = 24.dp
    if (action == null) {
        Spacer(Modifier.size(size + 28.dp))
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = live.playerPackage
    val icon = remember(player, action.icon) {
        runCatching {
            val resources = context.packageManager.getResourcesForApplication(player!!)
            androidx.core.content.res.ResourcesCompat.getDrawable(resources, action.icon, null)
                ?.toBitmap(72, 72)?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        Modifier.size(size + 28.dp).clip(CircleShape).clickable { live.sendCustomAction(action.action) },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                icon, action.name.toString(),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = CONTROL_ALPHA)),
                modifier = Modifier.size(size),
            )
        } else {
            Text(action.name.toString().take(1), color = Color.White.copy(alpha = CONTROL_ALPHA), fontSize = 16.sp)
        }
    }
}

private fun android.media.session.PlaybackState.CustomAction.matches(words: List<String>): Boolean {
    val text = (action + " " + name).lowercase()
    return words.any { it in text }
}

private val LIKE_WORDS = listOf("like", "heart", "favorite", "favourite", "save", "thumb")
private val SHUFFLE_WORDS = listOf("shuffle")
private val REPEAT_WORDS = listOf("repeat")

/** One control: a dim white glyph in a round touch target. */
@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = CONTROL_ALPHA), modifier = Modifier.size(size))
    }
}

/** The controls as dim as the lyrics around them. */
private const val CONTROL_ALPHA = 0.8f

private const val COVER_DP = 72
/** The cover behind the player look, under a veil heavy enough to keep it an always-on display. */
private const val COVER_VEIL = 0.62f
/** The Canvas behind the always-on display: blurred, and under a heavy veil to keep it dim. */
private const val CANVAS_BLUR_PX = 18f
private const val CANVAS_VEIL = 0.55f
/** How far everything wanders each minute, in px, against burn-in. */
private const val SHIFT_PX = 18
/** The lyrics a touch dimmer than on the lyrics screen, as an always-on display should be. */
private const val LYRICS_ALPHA = 0.85f
