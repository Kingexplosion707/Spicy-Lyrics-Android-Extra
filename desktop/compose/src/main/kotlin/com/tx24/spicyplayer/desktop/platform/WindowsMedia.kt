package com.tx24.spicyplayer.desktop.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * The players Windows' media controls know about, as Android media sessions: the shared playback
 * code then follows them exactly as it follows other apps on a phone. A PowerShell helper
 * (windows/media-sessions.ps1) talks to Windows and sends JSON lines.
 */
object WindowsMedia {
    class Session(
        val id: String,
        val packageName: String,
        val metadata: MediaMetadata,
        val state: PlaybackState,
        internal val trackKey: String,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val order = CopyOnWriteArrayList<String>()
    private val covers = ConcurrentHashMap<String, Bitmap?>()
    private val callbacks = CopyOnWriteArrayList<Pair<String, MediaController.Callback>>()
    private val listeners = CopyOnWriteArrayList<MediaSessionManager.OnActiveSessionsChangedListener>()
    @Volatile var error: String? = null; private set
    private var process: Process? = null

    fun session(id: String): Session? = sessions[id]
    fun controllers(): List<MediaController> {
        ensureStarted()
        return order.mapNotNull { id -> sessions[id]?.let { MediaController(null, MediaSession.Token(id)) } }
    }
    fun addCallback(id: String, cb: MediaController.Callback) { callbacks += id to cb }
    fun removeCallback(cb: MediaController.Callback) { callbacks.removeIf { it.second === cb } }
    fun addSessionsListener(l: MediaSessionManager.OnActiveSessionsChangedListener) { ensureStarted(); listeners += l }
    fun removeSessionsListener(l: MediaSessionManager.OnActiveSessionsChangedListener) { listeners -= l }

    fun command(id: String, cmd: String) {
        val p = process ?: return
        runCatching { p.outputStream.write("$id\t$cmd\n".toByteArray()); p.outputStream.flush() }
    }

    /** How a Windows player id reads to the shared code (which knows Android package names). */
    fun packageFor(id: String): String {
        val l = id.lowercase()
        return when {
            "spotify" in l -> "com.spotify.music"
            "applemusic" in l || "apple.music" in l || "itunes" in l -> "com.apple.android.music"
            "tidal" in l -> "com.aspiro.tidal"
            "deezer" in l -> "deezer.android.app"
            "amazon" in l && "music" in l -> "com.amazon.mp3"
            "youtube" in l -> "com.google.android.apps.youtube.music"
            else -> id.substringAfterLast('\\').substringBefore('!').removeSuffix(".exe").ifEmpty { id }
        }
    }

    @Synchronized
    private fun ensureStarted() {
        if (process != null) return
        System.getenv("SPICY_FAKE_MEDIA")?.let { FakeMedia.start(it, ::apply); process = ProcessBuilder("cat").start(); return }
        if (!System.getProperty("os.name").startsWith("Windows")) { error = "Following players needs Windows."; return }
        val script = File(DesktopPlatform.dataDir("helper"), "media-sessions.ps1")
        script.writeBytes(javaClass.getResourceAsStream("/windows/media-sessions.ps1")!!.readBytes())
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.path)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process = p
        Thread({
            p.inputStream.bufferedReader().forEachLine { line -> runCatching { apply(JsonParser.parseString(line).asJsonObject) } }
            // The helper ended: start it again in a moment.
            synchronized(this) { process = null }
            Thread.sleep(3000)
            ensureStarted()
        }, "windows-media").apply { isDaemon = true }.start()
        Runtime.getRuntime().addShutdownHook(Thread { p.destroy() })
    }

    internal fun apply(o: JsonObject) {
        o["error"]?.let { error = it.asString; return }
        error = null
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val seen = mutableListOf<String>()
        val changed = mutableListOf<Session>()
        o["sessions"].asJsonArray.forEach { el ->
            val s = el.asJsonObject
            val id = s["id"].asString
            seen += id
            val key = "${s["artist"].asString}|${s["title"].asString}|${s["album"].asString}"
            s["cover"]?.asString?.takeIf { it.isNotEmpty() }?.let { b64 ->
                covers[id] = runCatching { Base64.getDecoder().decode(b64).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
            } ?: run { if (sessions[id]?.trackKey != key && s["cover"] != null) covers[id] = null }
            val cover = covers[id]
            val md = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s["title"].asString)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s["artist"].asString)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, s["album"].asString)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, s["albumArtist"]?.asString ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s["durationMs"].asLong)
                .apply { if (cover != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover) }
                .build()
            val playing = s["isPlaying"].asBoolean
            val stateCode = when (s["status"]?.asString) {
                "Playing" -> PlaybackState.STATE_PLAYING
                "Paused" -> PlaybackState.STATE_PAUSED
                "Stopped", "Closed" -> PlaybackState.STATE_STOPPED
                "Changing" -> PlaybackState.STATE_BUFFERING
                else -> if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            }
            // Android stamps a position on the elapsed-realtime clock: the same anchor, moved over.
            val anchorElapsed = nowElapsed - (nowWall - s["anchorMs"].asLong)
            var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
            if (s["canNext"]?.asBoolean != false) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
            if (s["canPrevious"]?.asBoolean != false) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            if (s["canSeek"]?.asBoolean != false) actions = actions or PlaybackState.ACTION_SEEK_TO
            val state = PlaybackState.Builder()
                .setState(stateCode, s["positionMs"].asLong, if (playing) 1f else 0f, anchorElapsed)
                .setActions(actions)
                .build()
            val session = Session(id, packageFor(id), md, state, key)
            val old = sessions.put(id, session)
            if (old == null || old.metadata != md || old.state.state != state.state || old.state.position != state.position || old.state.lastPositionUpdateTime != state.lastPositionUpdateTime) changed += session
            shuffle[id] = s["shuffle"]?.asBoolean ?: false
            repeat[id] = s["repeat"]?.asInt ?: 0
        }
        val current = o["current"]?.asString
        val gone = sessions.keys - seen.toSet()
        gone.forEach { sessions.remove(it); covers.remove(it) }
        // The session Windows calls current first, as Android lists the most recent first.
        val newOrder = seen.sortedByDescending { it == current }
        val listChanged = newOrder != order.toList()
        order.clear(); order.addAll(newOrder)
        SwingUtilities.invokeLater {
            gone.forEach { id -> callbacks.filter { it.first == id }.forEach { it.second.onSessionDestroyed() } }
            changed.forEach { s ->
                callbacks.filter { it.first == s.id }.forEach { (_, cb) -> cb.onMetadataChanged(s.metadata); cb.onPlaybackStateChanged(s.state) }
            }
            if (listChanged) { val list = controllers(); listeners.forEach { it.onActiveSessionsChanged(list) } }
        }
    }

    val shuffle = ConcurrentHashMap<String, Boolean>()
    val repeat = ConcurrentHashMap<String, Int>()
}

/** SystemVolume: Windows' master volume isn't reachable from the JVM without native code. */
object SystemVolume {
    @Volatile private var value = 1f
    fun get(): Float = value
    fun set(v: Float) { value = v.coerceIn(0f, 1f) }
}
