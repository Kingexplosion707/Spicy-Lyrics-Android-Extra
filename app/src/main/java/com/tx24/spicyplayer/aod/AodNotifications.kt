package com.tx24.spicyplayer.aod

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

/**
 * The icons of the apps with notifications waiting, for the always-on display (as Android's own
 * shows them): one per app, newest first, from the notification listener the app already has.
 * Only icons: no titles or text reach the screen over the lock screen.
 */
internal object AodNotifications {
    class Entry(val packageName: String, val icon: Icon)

    /** Compose state, main thread. */
    val entries = mutableStateOf<List<Entry>>(emptyList())

    /** Rebuilds [entries] from the listener while it's connected (the display asks as it comes up). */
    @Volatile var refresher: (() -> Unit)? = null

    /** Whether the always-on display shows them: otherwise nothing is gathered. */
    fun wanted(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        return prefs.getBoolean("ambientAsAod", false) && prefs.getBoolean(PREF, true)
    }

    /** Rebuilds the list from what's showing now. */
    fun update(context: Context, active: Array<StatusBarNotification>?) {
        if (active == null || !wanted(context)) {
            entries.value = emptyList()
            return
        }
        val own = context.packageName
        val seen = HashSet<String>()
        val list = active
            .filter { sbn -> shows(sbn, own) }
            .sortedByDescending { it.postTime }
            .mapNotNull { sbn ->
                if (!seen.add(sbn.packageName)) return@mapNotNull null
                val icon = sbn.notification.smallIcon ?: return@mapNotNull null
                Entry(sbn.packageName, icon)
            }
            .take(MAX_ICONS)
        entries.value = list
    }

    private fun shows(sbn: StatusBarNotification, own: String): Boolean {
        val n = sbn.notification
        if (sbn.packageName == own || sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.visibility == Notification.VISIBILITY_SECRET) return false
        // The music app's own controls are already the always-on display.
        if (n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return false
        return true
    }

    const val PREF = "aodNotifications"
    private const val MAX_ICONS = 8
}
