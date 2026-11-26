package com.tx24.spicyplayer.playback

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Session access is Windows' media controls here, always on: nothing to grant. */
class SessionAccessService

/** No notification shade on the desktop. */
class LyricsNotificationService {
    companion object { fun sync(context: Context) {} }
}

/** The sleep timer: pauses the player when the time is up (or at the end of the song). */
class SleepTimerService {
    data class SleepTimer(val endsAtElapsed: Long, val endOfSong: Boolean) {
        fun minutesLeft(now: Long = SystemClock.elapsedRealtime()): Int =
            if (endOfSong) 0 else ((endsAtElapsed - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
    }

    companion object {
        val state = mutableStateOf<SleepTimer?>(null)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var job: Job? = null
        /** Pauses the player; set by the desktop app. */
        var pause: () -> Unit = {}
        /** How long the playing song has left, in ms (null when unknown); set by the desktop app. */
        var remainingMs: () -> Long? = { null }

        fun start(context: Context, minutes: Int) {
            job?.cancel()
            val endOfSong = minutes <= 0
            val ends = SystemClock.elapsedRealtime() + minutes * 60_000L
            state.value = SleepTimer(ends, endOfSong)
            job = scope.launch {
                while (true) {
                    val left = if (endOfSong) (remainingMs() ?: Long.MAX_VALUE) - 1_200L else ends - SystemClock.elapsedRealtime()
                    if (left <= 0) break
                    delay(left.coerceIn(200L, 30_000L))
                }
                pause()
                state.value = null
            }
        }

        fun cancel(context: Context) {
            job?.cancel()
            state.value = null
        }
    }
}
