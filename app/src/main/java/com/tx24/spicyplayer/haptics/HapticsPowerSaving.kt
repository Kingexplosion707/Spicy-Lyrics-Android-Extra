package com.tx24.spicyplayer.haptics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.wallpaper.PowerSaving

/**
 * Whether power saving stops the music haptics now: Stop the haptics is chosen under Power saving
 * (Settings → Device → Live wallpaper), and power saving is on (always, or with the phone's
 * battery saver). [prefs] is the `ui` store.
 */
internal fun hapticsStoppedBySaving(context: Context, prefs: SharedPreferences): Boolean =
    prefs.getBoolean(PowerSaving.PREF_NO_HAPTICS, false) && PowerSaving.from(prefs).active(batterySaverOn(context))

/** [hapticsStoppedBySaving] for the lyrics screen, following the settings and the battery saver as they change. */
@Composable
internal fun rememberHapticsStoppedBySaving(settings: AppSettings): Boolean {
    val context = LocalContext.current.applicationContext
    var batterySaver by remember { mutableStateOf(batterySaverOn(context)) }
    DisposableEffect(context) {
        val receiver = BatterySaverReceiver { batterySaver = batterySaverOn(context) }
        receiver.register(context)
        batterySaver = batterySaverOn(context)
        onDispose { receiver.unregister(context) }
    }
    return settings.wallpaperSaveNoHaptics && settings.wallpaperPowerSaving.active(batterySaver)
}

internal fun batterySaverOn(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

/** Calls [onChange] whenever the phone's battery saver turns on or off. */
internal class BatterySaverReceiver(private val onChange: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = onChange()

    fun register(context: Context) {
        ContextCompat.registerReceiver(
            context, this, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
    }
}
