@file:Suppress("unused", "UNUSED_PARAMETER")

package android.media.session

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaDescription
import android.media.MediaMetadata
import android.os.Bundle
import android.os.Handler
import com.tx24.spicyplayer.desktop.platform.WindowsMedia
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackState internal constructor(
    val state: Int,
    val position: Long,
    val lastPositionUpdateTime: Long,
    val playbackSpeed: Float,
    val actions: Long,
    val customActions: List<CustomAction>,
    val activeQueueItemId: Long,
    val bufferedPosition: Long = 0,
    val errorMessage: CharSequence? = null,
    val extras: Bundle? = null,
) {
    class CustomAction(val action: String, val name: CharSequence, val icon: Int, val extras: Bundle? = null) {
        class Builder(private val action: String, private val name: CharSequence, private val icon: Int) { fun setExtras(b: Bundle?) = this; fun build() = CustomAction(action, name, icon) }
    }
    class Builder() {
        private var state = STATE_NONE; private var position = 0L; private var updated = 0L; private var speed = 1f
        private var actions = 0L; private val custom = mutableListOf<CustomAction>(); private var queueId = -1L
        constructor(src: PlaybackState) : this() { state = src.state; position = src.position; updated = src.lastPositionUpdateTime; speed = src.playbackSpeed; actions = src.actions; custom += src.customActions; queueId = src.activeQueueItemId }
        fun setState(s: Int, pos: Long, spd: Float) = apply { state = s; position = pos; speed = spd; updated = android.os.SystemClock.elapsedRealtime() }
        fun setState(s: Int, pos: Long, spd: Float, upd: Long) = apply { state = s; position = pos; speed = spd; updated = upd }
        fun setActions(a: Long) = apply { actions = a }
        fun addCustomAction(a: CustomAction) = apply { custom += a }
        fun setActiveQueueItemId(id: Long) = apply { queueId = id }
        fun setBufferedPosition(p: Long) = this
        fun setErrorMessage(m: CharSequence?) = this
        fun setExtras(b: Bundle?) = this
        fun build() = PlaybackState(state, position, updated, speed, actions, custom.toList(), queueId)
    }
    companion object {
        const val STATE_NONE = 0; const val STATE_STOPPED = 1; const val STATE_PAUSED = 2; const val STATE_PLAYING = 3
        const val STATE_FAST_FORWARDING = 4; const val STATE_REWINDING = 5; const val STATE_BUFFERING = 6; const val STATE_ERROR = 7
        const val STATE_CONNECTING = 8; const val STATE_SKIPPING_TO_PREVIOUS = 9; const val STATE_SKIPPING_TO_NEXT = 10; const val STATE_SKIPPING_TO_QUEUE_ITEM = 11
        const val PLAYBACK_POSITION_UNKNOWN = -1L
        const val ACTION_STOP = 1L; const val ACTION_PAUSE = 2L; const val ACTION_PLAY = 4L; const val ACTION_REWIND = 8L
        const val ACTION_SKIP_TO_PREVIOUS = 16L; const val ACTION_SKIP_TO_NEXT = 32L; const val ACTION_FAST_FORWARD = 64L; const val ACTION_SET_RATING = 128L
        const val ACTION_SEEK_TO = 256L; const val ACTION_PLAY_PAUSE = 512L; const val ACTION_PLAY_FROM_MEDIA_ID = 1024L; const val ACTION_PLAY_FROM_SEARCH = 2048L
        const val ACTION_SKIP_TO_QUEUE_ITEM = 4096L; const val ACTION_PLAY_FROM_URI = 8192L; const val ACTION_PREPARE = 16384L; const val ACTION_SET_PLAYBACK_SPEED = 4194304L
    }
}

class MediaSession(context: Context?, tag: String) {
    class Token internal constructor(internal val id: String) {
        override fun equals(other: Any?) = other is Token && other.id == id
        override fun hashCode() = id.hashCode()
        override fun toString() = "Token($id)"
    }
    class QueueItem(val description: MediaDescription, val queueId: Long) { companion object { const val UNKNOWN_ID = -1L } }
    abstract class Callback {
        open fun onPlay() {}; open fun onPause() {}; open fun onStop() {}; open fun onSkipToNext() {}; open fun onSkipToPrevious() {}
        open fun onSeekTo(pos: Long) {}; open fun onCustomAction(action: String, extras: Bundle?) {}
        open fun onMediaButtonEvent(i: android.content.Intent): Boolean = false
    }
    val sessionToken = Token("own-$tag")
    var isActive: Boolean = false
    fun setCallback(cb: Callback?) {}
    fun setCallback(cb: Callback?, h: Handler?) {}
    fun setMetadata(m: MediaMetadata?) {}
    fun setPlaybackState(s: PlaybackState?) {}
    fun setFlags(f: Int) {}
    fun setSessionActivity(p: PendingIntent?) {}
    fun setMediaButtonReceiver(p: PendingIntent?) {}
    fun setPlaybackToRemote(v: Any?) {}
    fun setPlaybackToLocal(stream: Int) {}
    fun setQueue(q: List<QueueItem>?) {}
    fun release() {}
    val controller: MediaController get() = MediaController(null, sessionToken)
    companion object { const val FLAG_HANDLES_MEDIA_BUTTONS = 1; const val FLAG_HANDLES_TRANSPORT_CONTROLS = 2 }
}

/** A player Windows' media controls know about (Spotify, a browser tab, Apple Music...). */
class MediaController(context: Context?, val sessionToken: MediaSession.Token) {
    private val session get() = WindowsMedia.session(sessionToken.id)
    val packageName: String get() = session?.packageName ?: sessionToken.id
    val tag: String get() = sessionToken.id
    val metadata: MediaMetadata? get() = session?.metadata
    val playbackState: PlaybackState? get() = session?.state
    val playbackInfo: PlaybackInfo get() = PlaybackInfo()
    val queue: List<MediaSession.QueueItem>? get() = null
    val queueTitle: CharSequence? get() = null
    val extras: Bundle? get() = null
    val flags: Long get() = 0
    val ratingType: Int get() = 0
    val sessionActivity: PendingIntent? get() = null
    val transportControls: TransportControls get() = TransportControls(sessionToken.id)
    fun registerCallback(cb: Callback) = registerCallback(cb, null)
    fun registerCallback(cb: Callback, handler: Handler?) = WindowsMedia.addCallback(sessionToken.id, cb)
    fun unregisterCallback(cb: Callback) = WindowsMedia.removeCallback(cb)
    fun dispatchMediaButtonEvent(e: Any?): Boolean = false
    fun sendCommand(command: String, args: Bundle?, cb: Any?) {}
    fun setVolumeTo(v: Int, flags: Int) {}
    fun adjustVolume(d: Int, flags: Int) {}
    override fun equals(other: Any?) = other is MediaController && other.sessionToken == sessionToken
    override fun hashCode() = sessionToken.hashCode()

    class PlaybackInfo {
        val playbackType: Int get() = PLAYBACK_TYPE_LOCAL
        val volumeControl: Int get() = 0
        val maxVolume: Int get() = 100
        val currentVolume: Int get() = 100
        val audioAttributes: android.media.AudioAttributes? get() = null
        val volumeControlId: String? get() = null
        companion object { const val PLAYBACK_TYPE_LOCAL = 1; const val PLAYBACK_TYPE_REMOTE = 2 }
    }

    abstract class Callback {
        open fun onPlaybackStateChanged(state: PlaybackState?) {}
        open fun onMetadataChanged(metadata: MediaMetadata?) {}
        open fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {}
        open fun onQueueTitleChanged(title: CharSequence?) {}
        open fun onExtrasChanged(extras: Bundle?) {}
        open fun onSessionDestroyed() {}
        open fun onSessionEvent(event: String, extras: Bundle?) {}
        open fun onAudioInfoChanged(info: PlaybackInfo?) {}
    }

    class TransportControls internal constructor(private val id: String) {
        fun play() = WindowsMedia.command(id, "play")
        fun pause() = WindowsMedia.command(id, "pause")
        fun stop() = WindowsMedia.command(id, "stop")
        fun skipToNext() = WindowsMedia.command(id, "next")
        fun skipToPrevious() = WindowsMedia.command(id, "previous")
        fun seekTo(ms: Long) = WindowsMedia.command(id, "seek ${ms.coerceAtLeast(0)}")
        fun fastForward() = WindowsMedia.command(id, "seekby 10000")
        fun rewind() = WindowsMedia.command(id, "seekby -10000")
        fun setPlaybackSpeed(s: Float) {}
        fun sendCustomAction(action: String, args: Bundle?) = WindowsMedia.command(id, "custom $action")
        fun sendCustomAction(action: PlaybackState.CustomAction, args: Bundle?) = sendCustomAction(action.action, args)
        fun skipToQueueItem(qid: Long) {}
        fun playFromSearch(q: String?, extras: Bundle?) {}
        fun playFromMediaId(id: String?, extras: Bundle?) {}
        fun playFromUri(uri: android.net.Uri?, extras: Bundle?) {}
        fun setRating(r: Any?) {}
        fun prepare() {}
        fun setRepeatMode(m: Int) = WindowsMedia.command(id, "repeat $m")
        fun setShuffleMode(m: Int) = WindowsMedia.command(id, "shuffle $m")
    }
}

class MediaSessionManager {
    fun getActiveSessions(listener: ComponentName?): List<MediaController> = WindowsMedia.controllers()
    fun addOnActiveSessionsChangedListener(l: OnActiveSessionsChangedListener, c: ComponentName?) = WindowsMedia.addSessionsListener(l)
    fun addOnActiveSessionsChangedListener(l: OnActiveSessionsChangedListener, c: ComponentName?, h: Handler?) = WindowsMedia.addSessionsListener(l)
    fun removeOnActiveSessionsChangedListener(l: OnActiveSessionsChangedListener) = WindowsMedia.removeSessionsListener(l)
    fun interface OnActiveSessionsChangedListener { fun onActiveSessionsChanged(controllers: List<MediaController>?) }
}
