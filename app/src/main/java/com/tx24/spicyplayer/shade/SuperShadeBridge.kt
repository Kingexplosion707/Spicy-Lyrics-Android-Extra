package com.tx24.spicyplayer.shade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.overlay.LyricsOverlayService
import com.tx24.spicyplayer.overlay.OverlayColor
import com.tx24.spicyplayer.overlay.OverlaySize
import com.tx24.spicyplayer.playback.LiveLyrics
import com.tx24.spicyplayer.ui.nowplaying.MotionCovers
import com.tx24.spicyplayer.ui.nowplaying.leadArtist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Spicy Lyrics as a live activity in SuperShade Plus's notification shade. SuperShade asks
 * ([ACTION_REQUEST]) while its shade is open; this follows the player for as long as it keeps
 * asking (it stops by itself [IDLE_STOP_MS] after the last ask), and sends SuperShade what to
 * draw ([ACTION_LIVE]): the song, its lyrics with their word timing, where the song is (with the
 * elapsed-realtime clock it was read on, so SuperShade runs the clock itself between sends), the
 * Canvas and animated cover streams, and the lyrics overlay's look and whether it's on, so its
 * pin button can float the lyrics out as the overlay and back ([ACTION_OVERLAY]).
 *
 * Settings → Device → SuperShade (on by default): off, it answers nothing.
 */
internal object SuperShadeBridge {
    const val ACTION_REQUEST = "com.behnoud.spicylyrics.action.SHADE_REQUEST"
    const val ACTION_STOP = "com.behnoud.spicylyrics.action.SHADE_STOP"
    const val ACTION_OVERLAY = "com.behnoud.spicylyrics.action.SHADE_OVERLAY"
    const val ACTION_LIVE = "com.behnoud.spicylyrics.action.LIVE_LYRICS"
    const val PREF = "superShadeLiveActivity"
    /** The protocol's version, so SuperShade can tell what it's reading. */
    private const val VERSION = 1
    private val SHADE_PACKAGES = listOf("com.supershade", "com.supershade.debug")
    private const val HEARTBEAT_MS = 4_000L
    private const val IDLE_STOP_MS = 120_000L

    private val main = Handler(Looper.getMainLooper())
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var live: LiveLyrics? = null
    private var app: Context? = null
    private var sentLyricsFor: String? = null
    private var visualsFor: String? = null
    private var visualsJob: Job? = null
    private var canvasUrl: String? = null
    private var motionUrl: String? = null

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, true)

    /** SuperShade asked: follow the player (if not already), and send everything now. */
    fun request(context: Context) {
        if (!enabled(context)) return
        val appContext = context.applicationContext
        app = appContext
        val current = live ?: LiveLyrics(appContext).also { created ->
            created.onChange = { publish(full = false) }
            created.start()
            live = created
        }
        sentLyricsFor = null
        lookUpVisuals(current)
        publish(full = true)
        main.removeCallbacks(heartbeat)
        main.postDelayed(heartbeat, HEARTBEAT_MS)
        main.removeCallbacks(idleStop)
        main.postDelayed(idleStop, IDLE_STOP_MS)
    }

    fun stop() {
        main.removeCallbacks(heartbeat)
        main.removeCallbacks(idleStop)
        visualsJob?.cancel()
        live?.stop()
        live = null
        sentLyricsFor = null
        visualsFor = null
        canvasUrl = null
        motionUrl = null
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    /** Floats the lyrics out as the overlay, or brings them back, keeping all its settings. */
    fun setOverlay(context: Context, on: Boolean) {
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(LyricsOverlayService.PREF_ENABLED, on).apply()
        LyricsOverlayService.sync(context.applicationContext)
        publish(full = false)
    }

    private val heartbeat: Runnable = object : Runnable {
        override fun run() {
            if (live == null) return
            publish(full = false)
            main.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val idleStop = Runnable { stop() }

    /** The Canvas and the animated cover for the song now, looked up once per song. */
    private fun lookUpVisuals(current: LiveLyrics) {
        val context = app ?: return
        val track = current.track ?: return
        if (visualsFor == track.key) return
        visualsFor = track.key
        canvasUrl = null
        motionUrl = null
        visualsJob?.cancel()
        visualsJob = scope.launch {
            val canvas = withContext(Dispatchers.IO) {
                runCatching {
                    val meta = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                    val ids = SharedSpotify.extras(context.cacheDir).trackIds(meta, track.request.spotifyTrackId)
                    ids.firstNotNullOfOrNull { SharedSpotify.canvas(context.cacheDir).find(it) }
                }.getOrNull()
            }
            val motion = withContext(Dispatchers.IO) {
                runCatching { MotionCovers.finder(context).find(leadArtist(track.artist), track.album, track.title) }.getOrNull()
            }
            if (visualsFor != track.key) return@launch
            canvasUrl = canvas
            motionUrl = motion
            publish(full = false)
        }
    }

    /** Sends the state to SuperShade; the lyrics themselves only when they're new to it, or [full]. */
    private fun publish(full: Boolean) {
        val context = app ?: return
        val current = live ?: return
        val track = current.track
        if (track != null && visualsFor != track.key) lookUpVisuals(current)
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val intent = Intent(ACTION_LIVE).apply {
            putExtra("version", VERSION)
            putExtra("has_track", track != null)
            if (track != null) {
                putExtra("key", track.key)
                putExtra("title", track.title)
                putExtra("artist", track.artist)
                putExtra("album", track.album)
                putExtra("duration", track.durationMs)
            }
            putExtra("player", current.playerPackage)
            putExtra("playing", current.playing)
            putExtra("position", current.positionMs())
            putExtra("at", SystemClock.elapsedRealtime())
            putExtra(
                "state",
                when (current.lyricsState) {
                    is LyricsState.Ready -> "ready"
                    LyricsState.Loading -> "loading"
                    else -> "none"
                },
            )
            val lyrics = current.lyrics
            if (track != null && lyrics != null && (full || sentLyricsFor != track.key)) {
                putExtra("lyrics", lyricsJson(lyrics))
                sentLyricsFor = track.key
            }
            putExtra("canvas_url", canvasUrl)
            putExtra("motion_url", motionUrl)
            // The overlay's look, so the shade's lyrics match it, and whether it's floating now.
            putExtra("overlay_on", LyricsOverlayService.enabled(context))
            val choice = OverlayColor.from(prefs)
            val custom = com.tx24.spicyplayer.ui.settings.parseHexColor(prefs.getString("customLyricsColor", "") ?: "")
            val ink = choice.resolve(track?.cover, custom)
            putExtra("ink", ink.toArgb())
            putExtra("ink_end", (choice.argbEnd?.toInt() ?: ink.toArgb()))
            putExtra("glow", prefs.getBoolean("overlayGlow", true))
            putExtra("glow_strength", prefs.getInt("overlayGlowStrength", 100).coerceIn(50, 300) / 100f)
            putExtra("lyrics_opacity", prefs.getInt("overlayLyricsOpacity", 100).coerceIn(10, 100) / 100f)
            putExtra("backdrop_opacity", prefs.getInt("overlayBackdropOpacity", 45).coerceIn(0, 100) / 100f)
            putExtra("font_scale", OverlaySize.from(prefs).fontScale)
        }
        for (pkg in SHADE_PACKAGES) {
            runCatching { context.sendBroadcast(Intent(intent).setPackage(pkg)) }
        }
    }

    /**
     * The lyrics as compact JSON: the type and each line's times, role and words, each word as
     * `[text, start, end, attached]`.
     */
    private fun lyricsJson(lyrics: LyricsState.Ready): String {
        val lines = JSONArray()
        for (line in lyrics.lines) {
            if (line.untimed && lyrics.lyricsType != com.tx24.spicyplayer.lyrics.spicy.models.LyricsType.Static) continue
            val words = JSONArray()
            for (word in line.words) {
                words.put(JSONArray().put(word.text).put(word.startMs).put(word.endMs).put(if (word.attached) 1 else 0))
            }
            lines.put(
                JSONObject()
                    .put("s", line.startMs)
                    .put("e", line.endMs)
                    .put("bg", line.role != LineRole.LEAD)
                    .put("op", line.oppositeAligned)
                    .put("lt", line.lineTimed)
                    .put("w", words)
                    .apply { line.translation?.let { put("tr", it) } },
            )
        }
        return JSONObject().put("type", lyrics.lyricsType.name).put("lines", lines).toString()
    }
}

/** SuperShade's asks: follow and send, stop, or float the lyrics out as the overlay and back. */
class SuperShadeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SuperShadeBridge.ACTION_REQUEST -> SuperShadeBridge.request(context)
            SuperShadeBridge.ACTION_STOP -> SuperShadeBridge.stop()
            SuperShadeBridge.ACTION_OVERLAY -> {
                if (SuperShadeBridge.enabled(context)) SuperShadeBridge.setOverlay(context, intent.getBooleanExtra("on", false))
            }
        }
    }
}
