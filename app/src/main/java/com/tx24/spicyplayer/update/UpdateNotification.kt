package com.tx24.spicyplayer.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.R

/**
 * The notification that finishes an update downloaded while the app was out of view: Android
 * won't open the installer's prompt then, but tapping the notification does.
 */
internal object UpdateNotification {
    private const val CHANNEL = "updates"
    private const val ID = 1
    private const val EXTRA_VERSION_CODE = "com.tx24.spicyplayer.update.VERSION_CODE"

    /** Posts it, opening [confirm] on a tap. Without permission to notify, the app's own "Install" is the only way on. */
    @SuppressLint("MissingPermission") // allowed() checks POST_NOTIFICATIONS first
    fun show(context: Context, confirm: Intent) {
        if (!allowed(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.update_channel))
                .build(),
        )
        val tap = PendingIntent.getActivity(
            context, ID, Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(context.getString(R.string.update_ready_title))
            .setContentText(context.getString(R.string.update_ready_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addExtras(Bundle().apply { putInt(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE) })
            .build()
        runCatching { manager.notify(ID, notification) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    /** Takes it down once the app has been replaced: the prompt it opens was for the old install. */
    fun cancelStale(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val posted = runCatching { manager.activeNotifications }.getOrNull()?.firstOrNull { it.id == ID } ?: return
        if (posted.notification.extras.getInt(EXTRA_VERSION_CODE, -1) != BuildConfig.VERSION_CODE) manager.cancel(ID)
    }

    private fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}
