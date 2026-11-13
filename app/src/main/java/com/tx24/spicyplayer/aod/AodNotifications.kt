package com.tx24.spicyplayer.aod

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

/**
 * The notifications waiting, for the always-on display (as Android's own shows them), from the
 * notification listener the app already has: one icon per app, newest first, and tapped, a list
 * of them. What a notification says shows only as the phone's lock screen would show it: a
 * private one (or every one, with the lock screen set to hide what they say) shows its app alone.
 */
internal object AodNotifications {
    class Entry(val packageName: String, val icon: Icon)

    /**
     * One notification in the list. [title] and [text] are what it says; [lockedTitle] and
     * [lockedText] what the lock screen may show of it while locked (its public version, or
     * nothing), and [private] whether the lock screen would show only that.
     */
    class Detail(
        val key: String,
        val packageName: String,
        val appName: String,
        val icon: Icon,
        val title: String?,
        val text: String?,
        val lockedTitle: String?,
        val lockedText: String?,
        val private: Boolean,
        val postTime: Long,
        val open: PendingIntent?,
    )

    /** Compose state, main thread. */
    val entries = mutableStateOf<List<Entry>>(emptyList())
    val details = mutableStateOf<List<Detail>>(emptyList())
    /** A face or fingerprint was given on the always-on display: what they say shows until it closes. */
    val revealed = mutableStateOf(false)

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
            details.value = emptyList()
            return
        }
        val own = context.packageName
        val seen = HashSet<String>()
        val shown = active
            .filter { sbn -> shows(sbn, own) }
            .sortedByDescending { it.postTime }
        val hideAll = !lockScreenShowsContent(context)
        details.value = shown.take(MAX_DETAILS).mapNotNull { sbn ->
            val n = sbn.notification
            val icon = n.smallIcon ?: return@mapNotNull null
            val private = hideAll || n.visibility == Notification.VISIBILITY_PRIVATE
            // Locked, a private one shows its public version, if it gave one, else just its app.
            val locked = n.publicVersion?.extras
            Detail(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName(context, sbn),
                icon = icon,
                title = titleOf(n.extras),
                text = textOf(n.extras),
                lockedTitle = titleOf(locked),
                lockedText = textOf(locked),
                private = private,
                postTime = sbn.postTime,
                open = n.contentIntent,
            )
        }
        val list = shown
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

    private fun titleOf(extras: android.os.Bundle?): String? =
        extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }

    private fun textOf(extras: android.os.Bundle?): String? =
        (extras?.getCharSequence(Notification.EXTRA_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString()?.takeIf { it.isNotBlank() }

    private val names = HashMap<String, String>()

    /** The posting app's name: from the app info Android puts in every notification, so no package lookup is needed. */
    private fun appName(context: Context, sbn: StatusBarNotification): String = names.getOrPut(sbn.packageName) {
        val pm = context.packageManager
        runCatching {
            @Suppress("DEPRECATION")
            val info = sbn.notification.extras?.getParcelable<android.content.pm.ApplicationInfo>("android.appInfo")
                ?: pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)
    }

    /** Whether the phone's lock screen shows what notifications say (Settings → Notifications); yes if it can't be read. */
    private fun lockScreenShowsContent(context: Context): Boolean = runCatching {
        android.provider.Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications", 1) != 0
    }.getOrDefault(true)

    const val PREF = "aodNotifications"
    private const val MAX_ICONS = 8
    private const val MAX_DETAILS = 12
}
