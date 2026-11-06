package com.tx24.spicyplayer.aod

import android.os.Build
import android.os.Bundle
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
        window.attributes = window.attributes.apply { screenBrightness = BRIGHTNESS }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        live = LiveLyrics(this)
        live.start()
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        setContent { AlwaysOnContent(live, AppSettings(prefs)) }
    }

    override fun onResume() {
        super.onResume()
        showing = true
    }

    override fun onPause() {
        super.onPause()
        showing = false
    }

    override fun onDestroy() {
        live.stop()
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) finish()
        return true
    }

    companion object {
        /** Whether it's on screen now: the screen turning off then means the power button on it. */
        @Volatile var showing = false
            private set
        private var current: WeakReference<AlwaysOnActivity>? = null
        /** The lowest brightness the screen allows. */
        private const val BRIGHTNESS = 0.01f

        fun dismissAll() {
            current?.get()?.finish()
            showing = false
        }
    }
}

@Composable
private fun AlwaysOnContent(live: LiveLyrics, settings: AppSettings) {
    LyricsFontSetup(settings)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shift by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        var lastMinute = -1L
        while (true) {
            now = System.currentTimeMillis()
            val minute = now / 60_000L
            if (minute != lastMinute) {
                lastMinute = minute
                shift = IntOffset(Random.nextInt(-SHIFT_PX, SHIFT_PX + 1), Random.nextInt(-SHIFT_PX, SHIFT_PX + 1))
            }
            delay(1_000L - now % 1_000L)
        }
    }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.FULL) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                )
            }
        }
    }
}

/** How far everything wanders each minute, in px, against burn-in. */
private const val SHIFT_PX = 18
/** The lyrics a touch dimmer than on the lyrics screen, as an always-on display should be. */
private const val LYRICS_ALPHA = 0.85f
