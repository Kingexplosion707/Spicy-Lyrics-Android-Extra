package com.tx24.spicyplayer.aod

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.tx24.spicyplayer.weather.OpenMeteo
import java.text.DateFormat
import java.util.Date

/** What the always-on display shows around the music, as Android's own does; each can be switched off. */
internal class AodExtras(
    val weather: Boolean,
    val alarm: Boolean,
    val battery: Boolean,
    val notifications: Boolean,
    val ringer: Boolean,
)

internal class BatteryState(val percent: Int, val charging: Boolean)

/** The battery, following it while shown (Android sends its sticky state at once). */
@Composable
internal fun rememberBattery(): BatteryState? {
    val context = LocalContext.current.applicationContext
    var state by remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0) {
                    state = BatteryState(
                        level * 100 / scale,
                        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    )
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

/** The next alarm set in any clock app, as Android knows it; null with none. */
@Composable
internal fun rememberNextAlarm(): Long? {
    val context = LocalContext.current.applicationContext
    fun read() = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    var next by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { next = read() }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        next = read()
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return next
}

/** Silent, vibrate, or Do Not Disturb, when the phone is in one of them. */
internal enum class Quiet { None, Vibrate, Silent, DoNotDisturb }

@Composable
internal fun rememberQuiet(): Quiet {
    val context = LocalContext.current.applicationContext
    fun read(): Quiet {
        val dnd = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        if (dnd != null && dnd != NotificationManager.INTERRUPTION_FILTER_ALL && dnd != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            return Quiet.DoNotDisturb
        }
        return when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> Quiet.Vibrate
            AudioManager.RINGER_MODE_SILENT -> Quiet.Silent
            else -> Quiet.None
        }
    }
    var quiet by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { quiet = read() }
        }
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        quiet = read()
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return quiet
}

/**
 * One dim line of what's around the music: the next alarm, the weather, silent or Do Not
 * Disturb, and the battery. Shows nothing when there's nothing to show.
 */
@Composable
internal fun AodStatusLine(extras: AodExtras, alpha: Float, modifier: Modifier = Modifier, size: TextUnit = 14.sp) {
    val context = LocalContext.current
    val alarm = if (extras.alarm) rememberNextAlarm()?.takeIf { it - System.currentTimeMillis() in 0..ALARM_WITHIN_MS } else null
    val battery = if (extras.battery) rememberBattery() else null
    val quiet = if (extras.ringer) rememberQuiet() else Quiet.None
    val weather = if (extras.weather) AodWeather.current.value else null
    val fahrenheit = remember { AodWeather.fahrenheit(context) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val color = Color.White.copy(alpha = alpha)
    val iconSize = (size.value + 2).dp
    if (alarm == null && battery == null && quiet == Quiet.None && weather == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (alarm != null) Item(Icons.Rounded.Alarm, timeFormat.format(Date(alarm)), color, iconSize, size)
        if (weather != null) Item(skyIcon(weather.sky, weather.isDay), OpenMeteo.degrees(weather.temperatureC, fahrenheit), color, iconSize, size)
        when (quiet) {
            Quiet.Vibrate -> Icon(Icons.Rounded.Vibration, "Vibrate", tint = color, modifier = Modifier.size(iconSize))
            Quiet.Silent -> Icon(Icons.Rounded.NotificationsOff, "Silent", tint = color, modifier = Modifier.size(iconSize))
            Quiet.DoNotDisturb -> Icon(Icons.Rounded.DoNotDisturbOn, "Do Not Disturb", tint = color, modifier = Modifier.size(iconSize))
            Quiet.None -> Unit
        }
        if (battery != null) Item(batteryIcon(battery), "${battery.percent}%", color, iconSize, size)
    }
}

@Composable
private fun Item(icon: ImageVector, text: String, color: Color, iconSize: androidx.compose.ui.unit.Dp, size: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = size, fontWeight = FontWeight.Medium)
    }
}

/** Today's high and low under the weather, for the player look's larger weather. */
@Composable
internal fun AodWeatherRange(alpha: Float, size: TextUnit = 12.sp) {
    val context = LocalContext.current
    val weather = AodWeather.current.value ?: return
    val fahrenheit = remember { AodWeather.fahrenheit(context) }
    Text(
        "${OpenMeteo.degrees(weather.highC, fahrenheit)} / ${OpenMeteo.degrees(weather.lowC, fahrenheit)}",
        color = Color.White.copy(alpha = alpha), fontSize = size,
    )
}

/** The waiting notifications' app icons, in a row, white like the status bar's ([AodNotifications]). */
@Composable
internal fun AodNotificationIcons(alpha: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries = AodNotifications.entries.value
    if (entries.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        entries.forEach { entry ->
            val bitmap = remember(entry.packageName, entry.icon) {
                runCatching { entry.icon.loadDrawable(context)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() }.getOrNull()
            } ?: return@forEach
            Image(
                bitmap, null,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = alpha)),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun skyIcon(sky: OpenMeteo.Sky, day: Boolean): ImageVector = when (sky) {
    OpenMeteo.Sky.Clear -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime
    OpenMeteo.Sky.PartlyCloudy -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.Cloud
    OpenMeteo.Sky.Cloudy -> Icons.Rounded.Cloud
    OpenMeteo.Sky.Fog -> Icons.Rounded.BlurOn
    OpenMeteo.Sky.Drizzle -> Icons.Rounded.Umbrella
    OpenMeteo.Sky.Rain -> Icons.Rounded.Grain
    OpenMeteo.Sky.Snow -> Icons.Rounded.AcUnit
    OpenMeteo.Sky.Storm -> Icons.Rounded.Thunderstorm
}

private fun batteryIcon(battery: BatteryState): ImageVector = when {
    battery.charging -> Icons.Rounded.BatteryChargingFull
    battery.percent <= 15 -> Icons.Rounded.BatteryAlert
    battery.percent <= 40 -> Icons.Rounded.Battery2Bar
    battery.percent <= 65 -> Icons.Rounded.Battery4Bar
    battery.percent <= 90 -> Icons.Rounded.Battery6Bar
    else -> Icons.Rounded.BatteryFull
}

/** An alarm further off than this (tomorrow evening, next week) isn't worth a place. */
private const val ALARM_WITHIN_MS = 24 * 60 * 60_000L
private const val ICON_PX = 64
