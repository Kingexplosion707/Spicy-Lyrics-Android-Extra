@file:Suppress("unused", "UNUSED_PARAMETER")

package android.provider

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

object Settings {
    const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_MANAGE_OVERLAY_PERMISSION = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
    const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
    const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"
    const val ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS"
    const val ACTION_BLUETOOTH_SETTINGS = "android.settings.BLUETOOTH_SETTINGS"
    const val ACTION_USAGE_ACCESS_SETTINGS = "android.settings.USAGE_ACCESS_SETTINGS"
    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    @JvmStatic fun canDrawOverlays(c: Context?): Boolean = true
    object Secure {
        const val ANDROID_ID = "android_id"
        @JvmStatic fun getString(r: ContentResolver?, k: String): String? = when (k) {
            ANDROID_ID -> com.tx24.spicyplayer.desktop.platform.DesktopPlatform.installId
            "enabled_notification_listeners" -> "com.behnoud.spicylyrics/com.tx24.spicyplayer.playback.SessionAccessService"
            else -> null
        }
        @JvmStatic fun getInt(r: ContentResolver?, k: String, d: Int): Int = d
        @JvmStatic fun getFloat(r: ContentResolver?, k: String, d: Float): Float = d
    }
    object Global {
        const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
        const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        @JvmStatic fun getFloat(r: ContentResolver?, k: String, d: Float): Float = d
        @JvmStatic fun getInt(r: ContentResolver?, k: String, d: Int): Int = d
        @JvmStatic fun getString(r: ContentResolver?, k: String): String? = null
    }
    object System {
        const val SCREEN_BRIGHTNESS = "screen_brightness"
        const val SCREEN_OFF_TIMEOUT = "screen_off_timeout"
        @JvmStatic fun getInt(r: ContentResolver?, k: String, d: Int): Int = d
        @JvmStatic fun getInt(r: ContentResolver?, k: String): Int = if (k == SCREEN_BRIGHTNESS) 255 else throw SettingNotFoundException(k)
        @JvmStatic fun getFloat(r: ContentResolver?, k: String, d: Float): Float = d
        @JvmStatic fun canWrite(c: Context?) = false
    }
}

class SettingNotFoundException(msg: String) : Exception(msg)

object OpenableColumns { const val DISPLAY_NAME = "_display_name"; const val SIZE = "_size" }

object DocumentsContract {
    const val EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI"
    @JvmStatic fun getTreeDocumentId(u: Uri): String = u.path ?: ""
    @JvmStatic fun getDocumentId(u: Uri): String = u.path ?: ""
    @JvmStatic fun buildChildDocumentsUriUsingTree(u: Uri, id: String): Uri = u
    @JvmStatic fun buildDocumentUriUsingTree(u: Uri, id: String): Uri = Uri.fromFile(java.io.File(id))
    @JvmStatic fun isTreeUri(u: Uri) = u.scheme == "file"
    object Document {
        const val COLUMN_DOCUMENT_ID = "document_id"; const val COLUMN_DISPLAY_NAME = "_display_name"; const val COLUMN_MIME_TYPE = "mime_type"
        const val COLUMN_LAST_MODIFIED = "last_modified"; const val COLUMN_SIZE = "_size"; const val MIME_TYPE_DIR = "vnd.android.document/directory"
    }
}

object MediaStore { object Images { object Media { val EXTERNAL_CONTENT_URI: Uri = Uri.parse("file:///") } }; const val ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE" }
