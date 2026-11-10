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
        // As dim as asked (Settings → Device → Always-on dim), the lowest by default.
        val brightness = prefs.getInt(PREF_BRIGHTNESS, DEFAULT_BRIGHTNESS).coerceIn(1, 100) / 100f
        window.attributes = window.attributes.apply {
            screenBrightness = brightness
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
        setContent { AlwaysOnContent(live, AppSettings(prefs), canvas, controls, onDismiss = ::finish) }
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
    }

    override fun onPause() {
        super.onPause()
        shownState.value = false
    }

    override fun onDestroy() {
        live.stop()
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    /** With the controls shown, touches reach them (and the rest of the screen dismisses it). */
    private var controls = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (controls) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP) finish()
        return true
    }

    companion object {
        /** Compose state, so the lyrics rest while it's paused and pick up when it's back. Main thread. */
        private val shownState = mutableStateOf(false)
        /** Whether it's on screen now: the screen turning off then means the power button on it. */
        val showing: Boolean get() = shownState.value
        private var current: WeakReference<AlwaysOnActivity>? = null
        private const val MIN_REFRESH_HZ = 30f
        /** The screen's brightness on it, in percent: 1 is the lowest the screen allows. */
        const val PREF_BRIGHTNESS = "aodBrightness"
        const val DEFAULT_BRIGHTNESS = 1
        /** The song's Spotify Canvas behind it, blurred and darkened. */
        const val PREF_CANVAS = "aodCanvas"
        /** Play/pause, previous and next under the lyrics. */
        const val PREF_CONTROLS = "aodControls"

        fun dismissAll() {
            current?.get()?.finish()
            shownState.value = false
        }
    }
}

@Composable
private fun AlwaysOnContent(live: LiveLyrics, settings: AppSettings, canvas: Boolean, controls: Boolean, onDismiss: () -> Unit) {
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
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.FULL) }
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
        Column(
            Modifier.fillMaxSize().offset { shift }.padding(top = 72.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(timeFormat.format(Date(now)), color = Color.White.copy(alpha = 0.9f), fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(dateFormat.format(Date(now)), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            Spacer(Modifier.height(28.dp))
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
            val lyrics = live.lyrics
            if (track != null && lyrics != null && lyrics.lines.isNotEmpty()) {
                WallpaperLyrics(
                    lyrics = lyrics,
                    translation = live.translation,
                    settings = settings,
                    positionMs = live::positionMs,
                    isPlaying = live.playing,
                    visible = { AlwaysOnActivity.showing },
                    durationMs = track.durationMs,
                    modifier = Modifier.fillMaxWidth().weight(1f).graphicsLayer { alpha = LYRICS_ALPHA },
                    focusAnchorFraction = 0.35f,
                    tint = com.tx24.spicyplayer.lyrics.rememberLyricsTint(settings, track.cover),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (controls && track != null) {
                Row(
                    Modifier.padding(top = 12.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(Icons.Rounded.SkipPrevious, "Previous", 30.dp) { live.skipPrevious() }
                    ControlButton(if (live.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live.playing) "Pause" else "Play", 40.dp) { live.playPause() }
                    ControlButton(Icons.Rounded.SkipNext, "Next", 30.dp) { live.skipNext() }
                }
            }
        }
    }
}

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

/** The Canvas behind the always-on display: blurred, and under a heavy veil to keep it dim. */
private const val CANVAS_BLUR_PX = 18f
private const val CANVAS_VEIL = 0.55f
/** How far everything wanders each minute, in px, against burn-in. */
private const val SHIFT_PX = 18
/** The lyrics a touch dimmer than on the lyrics screen, as an always-on display should be. */
private const val LYRICS_ALPHA = 0.85f
