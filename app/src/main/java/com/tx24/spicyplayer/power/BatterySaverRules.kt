package com.tx24.spicyplayer.power

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.haptics.BatterySaverReceiver
import com.tx24.spicyplayer.haptics.batterySaverOn

/**
 * What the phone's battery saver turns off in the app while it's on, each its own switch in
 * Settings → Device → Battery saver (all on by default). The features themselves stay switched on:
 * they rest while the battery saver is on and come back by themselves once it's off.
 */
internal enum class SaverRule(val pref: String, val default: Boolean = true) {
    /** The always-on display: no lyrics screen when the screen turns off, and the lyrics screen's own always-on mode lets the screen sleep. */
    AlwaysOn("saverStopsAlwaysOn"),
    /** The lyrics floating over other apps, and its lyric lookups. */
    Overlay("saverStopsOverlay"),
    /** The live lyrics notification (and its player follower). */
    Notification("saverStopsNotification"),
    /** The music haptics outside the app (the lyrics screen's follow Power saving on the wallpaper page). */
    BackgroundHaptics("saverStopsBackgroundHaptics"),
    /** Keep the screen on: the screen times out as usual. */
    KeepScreenOn("saverStopsKeepScreenOn"),
    /** Smoother motion: the lyrics screen draws at 60 Hz. */
    HighRefreshRate("saverStopsHighRefreshRate"),
    /** Listening to stay in sync ([com.tx24.spicyplayer.playback.ListeningSync]). */
    Listening("saverStopsListening"),
    ;

    fun enabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(pref, default)

    /** Whether this is off right now: the battery saver is on and this rule is chosen. */
    fun stopsNow(context: Context, prefs: SharedPreferences = context.getSharedPreferences("ui", Context.MODE_PRIVATE)): Boolean =
        enabled(prefs) && batterySaverOn(context)

    companion object {
        /** Whether [key] is one of the rules' settings. */
        fun isRuleKey(key: String?): Boolean = key != null && entries.any { it.pref == key }
    }
}

/** Whether the phone's battery saver is on, following it as it turns on and off. */
@Composable
internal fun rememberBatterySaverOn(): Boolean {
    val context = LocalContext.current.applicationContext
    var on by remember { mutableStateOf(batterySaverOn(context)) }
    DisposableEffect(context) {
        val receiver = BatterySaverReceiver { on = batterySaverOn(context) }
        receiver.register(context)
        on = batterySaverOn(context)
        onDispose { receiver.unregister(context) }
    }
    return on
}
