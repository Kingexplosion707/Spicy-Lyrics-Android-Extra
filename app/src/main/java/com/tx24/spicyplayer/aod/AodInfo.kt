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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.draw.clip
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
    /** Opens a notification from the list (unlocking first), or null to keep the list closed. */
    val openNotification: ((AodNotifications.Detail) -> Unit)? = null,
    /** The clock's style and size, in percent. */
    val clockStyle: com.tx24.spicyplayer.ui.settings.AodClockStyle = com.tx24.spicyplayer.ui.settings.AodClockStyle.Light,
    val clockSize: Int = 100,
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

/**
 * The waiting notifications' app icons, in a row, white like the status bar's ([AodNotifications]).
 * Tapped (with [onOpen]), a list of them opens under the row; tapping one opens it, and the list
 * closes again by itself after a while.
 */
@Composable
internal fun AodNotificationIcons(
    alpha: Float,
    modifier: Modifier = Modifier,
    onOpen: ((AodNotifications.Detail) -> Unit)? = null,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val entries = AodNotifications.entries.value
    val details = AodNotifications.details.value
    var open by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(open, details) {
        if (details.isEmpty()) open = false
        if (open) {
            kotlinx.coroutines.delay(LIST_OPEN_MS)
            open = false
        }
    }
    if (entries.isEmpty()) return
    Column(modifier.fillMaxWidth(), horizontalAlignment = align) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = onOpen != null && details.isNotEmpty()) { open = !open }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry -> AppIcon(entry.packageName, entry.icon, alpha, 18.dp) }
            if (onOpen != null && details.isNotEmpty()) {
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Hide notifications" else "Show notifications",
                    tint = Color.White.copy(alpha = alpha * 0.8f), modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(visible = open && onOpen != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val now = System.currentTimeMillis()
                details.take(LIST_MAX).forEach { detail -> NotificationCard(detail, alpha, now) { onOpen?.invoke(detail) } }
                if (details.size > LIST_MAX) {
                    Text(
                        "+${details.size - LIST_MAX} more", color = Color.White.copy(alpha = alpha * 0.6f), fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String, icon: android.graphics.drawable.Icon, alpha: Float, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName, icon) {
        runCatching { icon.loadDrawable(context)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() }.getOrNull()
    } ?: return
    Image(bitmap, null, colorFilter = ColorFilter.tint(Color.White.copy(alpha = alpha)), modifier = Modifier.size(size))
}

/** One notification: its app and when, its title, and a line or two of what it says. */
@Composable
private fun NotificationCard(detail: AodNotifications.Detail, alpha: Float, now: Long, onClick: () -> Unit) {
    val ago = remember(detail.postTime, now / 60_000L) {
        android.text.format.DateUtils.getRelativeTimeSpanString(
            detail.postTime, now, android.text.format.DateUtils.MINUTE_IN_MILLIS, android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f * alpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp)) { AppIcon(detail.packageName, detail.icon, alpha, 20.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${detail.appName} · $ago", color = Color.White.copy(alpha = alpha * 0.6f), fontSize = 12.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (detail.title == null && detail.text == null) {
                Text("Contents hidden", color = Color.White.copy(alpha = alpha * 0.7f), fontSize = 14.sp)
            }
            detail.title?.let {
                Text(
                    it, color = Color.White.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            detail.text?.let {
                Text(
                    it, color = Color.White.copy(alpha = alpha * 0.75f), fontSize = 13.sp,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
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
/** The list shows this many, and closes by itself this long after opening. */
private const val LIST_MAX = 5
private const val LIST_OPEN_MS = 20_000L
