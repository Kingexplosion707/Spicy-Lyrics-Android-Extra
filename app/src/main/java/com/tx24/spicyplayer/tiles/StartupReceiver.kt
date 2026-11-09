package com.tx24.spicyplayer.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tx24.spicyplayer.aod.AlwaysOnService
import com.tx24.spicyplayer.overlay.LyricsOverlayService
import com.tx24.spicyplayer.playback.LyricsNotificationService

/**
 * Brings back what's switched on (the overlay, the always-on display, the lyrics notification)
 * after the phone restarts or the app updates, rather than waiting for the app to be opened. Each
 * starts only if its setting is on. The background haptics wait for the app: Android doesn't let
 * a media service start at boot.
 */
class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val app = context.applicationContext
        runCatching { AlwaysOnService.sync(app) }
        runCatching { LyricsOverlayService.sync(app) }
        runCatching { LyricsNotificationService.sync(app) }
    }
}
