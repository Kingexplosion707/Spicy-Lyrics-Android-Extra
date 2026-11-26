package com.tx24.spicyplayer.overlay

import android.content.Context

/** The phone's status bar clock: nothing to hide or restore on a desktop. */
internal object StatusBarClock {
    fun restore(context: Context) {}
    suspend fun requestShizukuPermission(): Boolean = false
}
