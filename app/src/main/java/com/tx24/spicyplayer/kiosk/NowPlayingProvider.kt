package com.tx24.spicyplayer.kiosk

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.playback.LiveLyrics
import com.tx24.spicyplayer.ui.nowplaying.MotionCovers
import com.tx24.spicyplayer.ui.nowplaying.leadArtist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * What plays now, for other apps (Kiosk Satellite Plus) to show: the song, where it is, its
 * lyrics with their timing, and its animated cover and Canvas. Reading it needs the
 * `READ_NOW_PLAYING` permission. One row, one `json` column.
 *
 * It follows the player only while someone reads it: the first query starts it (that answer may
 * still be empty) and it stops a minute after the last one.
 */
class NowPlayingProvider : ContentProvider() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var live: LiveLyrics? = null
    private var mediaFor: String? = null
    private var mediaJob: Job? = null
    @Volatile private var motionCoverUrl: String? = null
    @Volatile private var canvasUrl: String? = null
    private val idleStop = Runnable { stopFollowing() }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        main.post {
            if (live == null) startFollowing()
            main.removeCallbacks(idleStop)
            main.postDelayed(idleStop, IDLE_STOP_MS)
        }
        val cursor = MatrixCursor(arrayOf("json"))
        cursor.addRow(arrayOf(snapshot().toString()))
        return cursor
    }

    private fun startFollowing() {
        val context = context ?: return
        live = LiveLyrics(context).also {
            it.onChange = { lookUpMedia() }
            it.start()
        }
    }

    private fun stopFollowing() {
        live?.stop()
        live = null
        mediaJob?.cancel()
        mediaFor = null
        motionCoverUrl = null
        canvasUrl = null
    }

    /** The song's animated cover and Canvas, once per song. */
    private fun lookUpMedia() {
        val context = context ?: return
        val track = live?.track ?: return
        if (track.key == mediaFor) return
        mediaFor = track.key
        motionCoverUrl = null
        canvasUrl = null
        mediaJob?.cancel()
        mediaJob = scope.launch(Dispatchers.IO) {
            motionCoverUrl = runCatching { MotionCovers.finder(context).find(leadArtist(track.artist), track.album, track.title) }.getOrNull()
            canvasUrl = runCatching {
                val meta = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                SharedSpotify.extras(context.cacheDir).trackIds(meta, track.request.spotifyTrackId)
                    .firstNotNullOfOrNull { SharedSpotify.canvas(context.cacheDir).find(it) }
            }.getOrNull()
        }
    }

    private fun snapshot(): JSONObject {
        val out = JSONObject()
        val follower = live ?: return out.put("following", false)
        val track = follower.track ?: return out.put("following", true)
        out.put("following", true)
        out.put("title", track.title)
        out.put("artist", track.artist)
        out.put("album", track.album)
        out.put("durationMs", track.durationMs)
        out.put("positionMs", follower.positionMs())
        out.put("playing", follower.playing)
        out.put("settled", follower.settled)
        motionCoverUrl?.let { out.put("motionCoverUrl", it) }
        canvasUrl?.let { out.put("canvasUrl", it) }
        val lyrics = follower.lyrics
        if (lyrics != null) {
            out.put("lyricsType", lyrics.lyricsType.name)
            val lines = JSONArray()
            for (line in lyrics.lines) {
                if (line.role == LineRole.INTERLUDE || line.words.isEmpty()) continue
                val words = JSONArray()
                line.words.forEach { word ->
                    words.put(JSONObject().put("text", word.text).put("startMs", word.startMs).put("endMs", word.endMs).put("attached", word.attached))
                }
                lines.put(
                    JSONObject()
                        .put("startMs", line.startMs)
                        .put("endMs", line.endMs)
                        .put("text", line.words.joinToString("") { (if (it.attached) "" else " ") + it.text }.trim())
                        .put("background", line.role == LineRole.BACKGROUND)
                        .put("opposite", line.oppositeAligned)
                        .put("words", words),
                )
            }
            out.put("lines", lines)
        }
        return out
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.spicylyrics.nowplaying"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val IDLE_STOP_MS = 60_000L
    }
}
