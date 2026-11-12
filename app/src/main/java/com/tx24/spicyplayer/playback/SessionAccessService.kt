package com.tx24.spicyplayer.playback

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName
import android.os.Build
import com.tx24.spicyplayer.widget.NowPlayingHolder

/**
 * Exists for its grant: `MediaSessionManager` lets a notification listener read other apps'
 * sessions. While access is on, the system keeps it bound and the app's process alive, so the
 * home screen widget starts following the music here.
 */
class SessionAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        NowPlayingHolder.start(this)
        // Access is (back) on: the listening stats, when they're on, follow the players from here.
        runCatching { com.tx24.spicyplayer.stats.ListeningTracker.sync(this, reconnect = true) }
        com.tx24.spicyplayer.aod.AodNotifications.refresher = { refreshAodIcons() }
        refreshAodIcons()
    }

    // The always-on display's notification icons; nothing is gathered while it doesn't show them.
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshAodIcons()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshAodIcons()

    private fun refreshAodIcons() {
        if (!com.tx24.spicyplayer.aod.AodNotifications.wanted(this)) {
            com.tx24.spicyplayer.aod.AodNotifications.entries.value = emptyList()
            return
        }
        com.tx24.spicyplayer.aod.AodNotifications.update(this, runCatching { activeNotifications }.getOrNull())
    }

    override fun onListenerDisconnected() {
        com.tx24.spicyplayer.aod.AodNotifications.refresher = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, SessionAccessService::class.java))
        }
    }
}
