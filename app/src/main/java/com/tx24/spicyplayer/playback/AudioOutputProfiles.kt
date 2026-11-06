package com.tx24.spicyplayer.playback

import android.content.Context
import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

internal data class AudioOutputRoute(val key: String, val label: String)

/** A media-route estimate, not a measurement of when sound reaches the listener. */
internal class AudioOutputProfiles(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mediaRouter = context.getSystemService(MediaRouter::class.java)
    private val preferences = context.getSharedPreferences("lyric_output_delays", Context.MODE_PRIVATE)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun currentRoute(): AudioOutputRoute {
        val routedDevices = if (Build.VERSION.SDK_INT >= 33) {
            runCatching { audioManager.getAudioDevicesForAttributes(mediaAttributes) }.getOrDefault(emptyList())
                .filterNot { it.type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX }
        } else emptyList()
        if (routedDevices.isNotEmpty()) {
            val devices = routedDevices.sortedWith(compareBy<AudioDeviceInfo> { it.type }.thenBy { it.productName.toString() })
            if (devices.size == 1) return devices.single().asRoute()
            val labels = devices.map { it.asRoute().label }.distinct()
            val keys = devices.map { it.asRoute().key }.distinct()
            return AudioOutputRoute("multi:${keys.joinToString("+")}", labels.joinToString(" + "))
        }

        // Older Android: framework MediaRouter tracks the selected live-audio route.
        val route = runCatching { mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO) }.getOrNull()
        val rawName = route?.name?.toString()?.trim().orEmpty()
        val name = if (rawName.isBlank() || rawName.equals("Phone", ignoreCase = true)) "Phone speaker" else rawName
        return AudioOutputRoute("route:${name.lowercase(Locale.ROOT)}", name)
    }

    fun delayMs(route: AudioOutputRoute): Int = preferences.getInt("delay:${route.key}", 0)

    fun saveDelayMs(route: AudioOutputRoute, delayMs: Int) {
        preferences.edit().putInt("delay:${route.key}", clampDelay(delayMs)).apply()
    }

    @SuppressLint("InlinedApi")
    private fun AudioDeviceInfo.asRoute(): AudioOutputRoute {
        val kind = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call audio"
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            else -> "Audio output"
        }
        val product = productName?.toString()?.trim().orEmpty()
        val label = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            kind
        } else product.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) } ?: kind
        val bluetooth = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            type == AudioDeviceInfo.TYPE_HEARING_AID
        val address = if (bluetooth && Build.VERSION.SDK_INT >= 28) {
            runCatching { this.address.trim() }.getOrNull().orEmpty()
        } else ""
        val identity = if (address.isNotBlank()) {
            MessageDigest.getInstance("SHA-256").digest(address.toByteArray())
                .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } else label.lowercase(Locale.ROOT)
        return AudioOutputRoute("device:$type:$identity", label)
    }
}
