@file:Suppress("unused", "UNUSED_PARAMETER")

package android.media

import android.graphics.Bitmap

class MediaMetadata private constructor(private val values: Map<String, Any?>) {
    fun getString(key: String): String? = values[key]?.toString()
    fun getText(key: String): CharSequence? = values[key] as? CharSequence
    fun getLong(key: String): Long = (values[key] as? Number)?.toLong() ?: 0L
    fun getBitmap(key: String): Bitmap? = values[key] as? Bitmap
    fun getRating(key: String): Any? = null
    fun containsKey(key: String): Boolean = values.containsKey(key)
    fun keySet(): Set<String> = values.keys
    fun size(): Int = values.size
    val description: MediaDescription get() = MediaDescription(
        getString(METADATA_KEY_MEDIA_ID), getText(METADATA_KEY_DISPLAY_TITLE) ?: getText(METADATA_KEY_TITLE),
        getText(METADATA_KEY_DISPLAY_SUBTITLE) ?: getText(METADATA_KEY_ARTIST), getBitmap(METADATA_KEY_ART) ?: getBitmap(METADATA_KEY_ALBUM_ART),
    )
    override fun equals(other: Any?) = other is MediaMetadata && other.values == values
    override fun hashCode() = values.hashCode()

    class Builder() {
        private val values = LinkedHashMap<String, Any?>()
        constructor(src: MediaMetadata) : this() { values.putAll(src.values) }
        fun putString(k: String, v: String?) = apply { values[k] = v }
        fun putText(k: String, v: CharSequence?) = apply { values[k] = v }
        fun putLong(k: String, v: Long) = apply { values[k] = v }
        fun putBitmap(k: String, v: Bitmap?) = apply { values[k] = v }
        fun putRating(k: String, v: Any?) = apply { values[k] = v }
        fun build() = MediaMetadata(LinkedHashMap(values))
    }

    companion object {
        const val METADATA_KEY_TITLE = "android.media.metadata.TITLE"
        const val METADATA_KEY_ARTIST = "android.media.metadata.ARTIST"
        const val METADATA_KEY_DURATION = "android.media.metadata.DURATION"
        const val METADATA_KEY_ALBUM = "android.media.metadata.ALBUM"
        const val METADATA_KEY_AUTHOR = "android.media.metadata.AUTHOR"
        const val METADATA_KEY_WRITER = "android.media.metadata.WRITER"
        const val METADATA_KEY_COMPOSER = "android.media.metadata.COMPOSER"
        const val METADATA_KEY_COMPILATION = "android.media.metadata.COMPILATION"
        const val METADATA_KEY_DATE = "android.media.metadata.DATE"
        const val METADATA_KEY_YEAR = "android.media.metadata.YEAR"
        const val METADATA_KEY_GENRE = "android.media.metadata.GENRE"
        const val METADATA_KEY_TRACK_NUMBER = "android.media.metadata.TRACK_NUMBER"
        const val METADATA_KEY_NUM_TRACKS = "android.media.metadata.NUM_TRACKS"
        const val METADATA_KEY_DISC_NUMBER = "android.media.metadata.DISC_NUMBER"
        const val METADATA_KEY_ALBUM_ARTIST = "android.media.metadata.ALBUM_ARTIST"
        const val METADATA_KEY_ART = "android.media.metadata.ART"
        const val METADATA_KEY_ART_URI = "android.media.metadata.ART_URI"
        const val METADATA_KEY_ALBUM_ART = "android.media.metadata.ALBUM_ART"
        const val METADATA_KEY_ALBUM_ART_URI = "android.media.metadata.ALBUM_ART_URI"
        const val METADATA_KEY_USER_RATING = "android.media.metadata.USER_RATING"
        const val METADATA_KEY_RATING = "android.media.metadata.RATING"
        const val METADATA_KEY_DISPLAY_TITLE = "android.media.metadata.DISPLAY_TITLE"
        const val METADATA_KEY_DISPLAY_SUBTITLE = "android.media.metadata.DISPLAY_SUBTITLE"
        const val METADATA_KEY_DISPLAY_DESCRIPTION = "android.media.metadata.DISPLAY_DESCRIPTION"
        const val METADATA_KEY_DISPLAY_ICON = "android.media.metadata.DISPLAY_ICON"
        const val METADATA_KEY_DISPLAY_ICON_URI = "android.media.metadata.DISPLAY_ICON_URI"
        const val METADATA_KEY_MEDIA_ID = "android.media.metadata.MEDIA_ID"
        const val METADATA_KEY_MEDIA_URI = "android.media.metadata.MEDIA_URI"
        const val METADATA_KEY_BT_FOLDER_TYPE = "android.media.metadata.BT_FOLDER_TYPE"
    }
}

class MediaDescription(val mediaId: String?, val title: CharSequence?, val subtitle: CharSequence?, val iconBitmap: Bitmap?) {
    val description: CharSequence? get() = null
    val iconUri: android.net.Uri? get() = null
    val mediaUri: android.net.Uri? get() = null
    val extras: android.os.Bundle? get() = null
}

class AudioManager {
    fun getStreamVolume(stream: Int): Int = (com.tx24.spicyplayer.desktop.platform.SystemVolume.get() * 100).toInt()
    fun getStreamMaxVolume(stream: Int): Int = 100
    fun getStreamMinVolume(stream: Int): Int = 0
    fun setStreamVolume(stream: Int, index: Int, flags: Int) = com.tx24.spicyplayer.desktop.platform.SystemVolume.set(index / 100f)
    fun adjustStreamVolume(stream: Int, direction: Int, flags: Int) = setStreamVolume(stream, getStreamVolume(stream) + direction * 5, flags)
    val isMusicActive: Boolean get() = true
    fun getDevices(flags: Int): Array<AudioDeviceInfo> = arrayOf(AudioDeviceInfo())
    fun getAudioDevicesForAttributes(a: AudioAttributes): List<AudioDeviceInfo> = listOf(AudioDeviceInfo())
    fun registerAudioDeviceCallback(cb: AudioDeviceCallback?, h: android.os.Handler?) {}
    fun unregisterAudioDeviceCallback(cb: AudioDeviceCallback?) {}
    fun requestAudioFocus(r: Any?): Int = AUDIOFOCUS_REQUEST_GRANTED
    fun abandonAudioFocusRequest(r: Any?): Int = AUDIOFOCUS_REQUEST_GRANTED
    var mode: Int = MODE_NORMAL
    val ringerMode: Int get() = RINGER_MODE_NORMAL
    fun getProperty(key: String): String? = null
    companion object {
        const val STREAM_MUSIC = 3
        const val FLAG_SHOW_UI = 1
        const val ADJUST_RAISE = 1; const val ADJUST_LOWER = -1; const val ADJUST_SAME = 0
        const val GET_DEVICES_OUTPUTS = 2; const val GET_DEVICES_INPUTS = 1; const val GET_DEVICES_ALL = 3
        const val AUDIOFOCUS_REQUEST_GRANTED = 1
        const val MODE_NORMAL = 0; const val RINGER_MODE_NORMAL = 2
        const val ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY"
        const val PROPERTY_OUTPUT_SAMPLE_RATE = "android.media.property.OUTPUT_SAMPLE_RATE"
    }
}
abstract class AudioDeviceCallback { open fun onAudioDevicesAdded(d: Array<AudioDeviceInfo>) {}; open fun onAudioDevicesRemoved(d: Array<AudioDeviceInfo>) {} }
class AudioDeviceInfo {
    val type: Int get() = TYPE_BUILTIN_SPEAKER
    val id: Int get() = 1
    val productName: CharSequence get() = "Speakers"
    val isSink: Boolean get() = true
    val address: String get() = ""
    companion object {
        const val TYPE_UNKNOWN = 0; const val TYPE_BUILTIN_EARPIECE = 1; const val TYPE_BUILTIN_SPEAKER = 2; const val TYPE_WIRED_HEADSET = 3
        const val TYPE_WIRED_HEADPHONES = 4; const val TYPE_BLUETOOTH_SCO = 7; const val TYPE_BLUETOOTH_A2DP = 8; const val TYPE_HDMI = 9
        const val TYPE_USB_DEVICE = 11; const val TYPE_USB_ACCESSORY = 12; const val TYPE_REMOTE_SUBMIX = 25; const val TYPE_USB_HEADSET = 22
        const val TYPE_HEARING_AID = 23; const val TYPE_BLE_HEADSET = 26; const val TYPE_BLE_SPEAKER = 27; const val TYPE_BLE_BROADCAST = 30
    }
}
class AudioAttributes private constructor() {
    class Builder { fun setUsage(u: Int) = this; fun setContentType(c: Int) = this; fun build() = AudioAttributes() }
    companion object { const val USAGE_MEDIA = 1; const val USAGE_ASSISTANCE_SONIFICATION = 13; const val CONTENT_TYPE_MUSIC = 2; const val USAGE_ALARM = 4; const val USAGE_NOTIFICATION = 5 }
}
class MediaRouter {
    fun getSelectedRoute(type: Int): RouteInfo = RouteInfo()
    class RouteInfo { val name: CharSequence get() = "Speakers"; fun getName(c: android.content.Context?): CharSequence = name; val deviceType: Int get() = 0; val playbackType: Int get() = 0 }
    fun addCallback(type: Int, cb: Callback?) {}
    fun removeCallback(cb: Callback?) {}
    open class SimpleCallback : Callback()
    abstract class Callback
    companion object { const val ROUTE_TYPE_LIVE_AUDIO = 1 }
}
