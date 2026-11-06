package com.tx24.spicyplayer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.tx24.spicyplayer.playback.CrashLog
import com.tx24.spicyplayer.update.UpdateNotification

/**
 * Process start, whatever started it: the lyrics screen, the widget, the notification listener or
 * the updater's receiver. Crashes are recorded from here so those without the screen count too.
 */
class SpicyLyricsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        UpdateNotification.cancelStale(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        @Volatile private var startedActivities = 0

        /** One of the app's screens is on view, so Android lets it open another (the installer's prompt). */
        val visible: Boolean get() = startedActivities > 0
    }
}
