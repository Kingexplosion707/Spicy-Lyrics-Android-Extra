@file:Suppress("unused", "UNUSED_PARAMETER")

package android.app

import android.content.Context
import android.content.Intent
import android.os.Bundle

open class Application : Context()

/** Pending intents run their intent in-process when sent. */
class PendingIntent private constructor(val intent: Intent, private val kind: Int) {
    fun send() = when (kind) { 1 -> android.content.Context().sendBroadcast(intent); else -> android.content.Context().startActivity(intent) }
    fun send(code: Int) = send()
    fun cancel() {}
    val intentSender: Any? get() = null
    val creatorPackage: String get() = "com.behnoud.spicylyrics"
    class CanceledException : Exception()
    companion object {
        const val FLAG_IMMUTABLE = 0x04000000
        const val FLAG_MUTABLE = 0x02000000
        const val FLAG_UPDATE_CURRENT = 0x08000000
        const val FLAG_CANCEL_CURRENT = 0x10000000
        const val FLAG_ONE_SHOT = 0x40000000
        const val FLAG_NO_CREATE = 0x20000000
        @JvmStatic fun getActivity(c: Context?, code: Int, i: Intent, flags: Int) = PendingIntent(i, 0)
        @JvmStatic fun getActivity(c: Context?, code: Int, i: Intent, flags: Int, o: Bundle?) = PendingIntent(i, 0)
        @JvmStatic fun getBroadcast(c: Context?, code: Int, i: Intent, flags: Int) = PendingIntent(i, 1)
        @JvmStatic fun getService(c: Context?, code: Int, i: Intent, flags: Int) = PendingIntent(i, 2)
        @JvmStatic fun getForegroundService(c: Context?, code: Int, i: Intent, flags: Int) = PendingIntent(i, 2)
    }
}

open class Activity : Context() {
    open fun finish() {}
    open val window: android.view.Window = android.view.Window()
    open val windowManager: android.view.WindowManager get() = window.windowManager
    open var requestedOrientation: Int = -1
    open fun setShowWhenLocked(show: Boolean) {}
    open fun setTurnScreenOn(on: Boolean) {}
    open val intent: Intent get() = Intent()
    open val isFinishing: Boolean get() = false
    open fun runOnUiThread(r: Runnable) = javax.swing.SwingUtilities.invokeLater(r)
    companion object { const val RESULT_OK = -1; const val RESULT_CANCELED = 0 }
}

class NotificationManager {
    fun notify(id: Int, n: Any?) {}
    fun notify(tag: String?, id: Int, n: Any?) {}
    fun cancel(id: Int) {}
    fun cancel(tag: String?, id: Int) {}
    fun cancelAll() {}
    fun createNotificationChannel(c: Any?) {}
    fun deleteNotificationChannel(id: String) {}
    fun areNotificationsEnabled() = false
    val activeNotifications: Array<Any> get() = emptyArray()
    fun isNotificationPolicyAccessGranted() = false
    companion object { const val IMPORTANCE_LOW = 2; const val IMPORTANCE_DEFAULT = 3; const val IMPORTANCE_HIGH = 4; const val IMPORTANCE_MIN = 1 }
}

class KeyguardManager {
    val isKeyguardLocked: Boolean get() = false
    val isDeviceLocked: Boolean get() = false
    fun requestDismissKeyguard(a: Activity?, cb: KeyguardDismissCallback?) { cb?.onDismissSucceeded() }
    abstract class KeyguardDismissCallback { open fun onDismissSucceeded() {}; open fun onDismissCancelled() {}; open fun onDismissError() {} }
}

class ActivityManager {
    fun getHistoricalProcessExitReasons(pkg: String?, pid: Int, max: Int): List<ApplicationExitInfo> = emptyList()
    val isLowRamDevice: Boolean get() = false
    val memoryClass: Int get() = 512
    val runningAppProcesses: List<Any>? get() = null
}
class ApplicationExitInfo {
    val reason = 0; val timestamp = 0L; val description: String? = null; val pss = 0L; val rss = 0L; val importance = 0; val status = 0
    companion object {
        const val REASON_UNKNOWN = 0; const val REASON_EXIT_SELF = 1; const val REASON_SIGNALED = 2; const val REASON_LOW_MEMORY = 3; const val REASON_CRASH = 4
        const val REASON_CRASH_NATIVE = 5; const val REASON_ANR = 6; const val REASON_INITIALIZATION_FAILURE = 7; const val REASON_PERMISSION_CHANGE = 8
        const val REASON_EXCESSIVE_RESOURCE_USAGE = 9; const val REASON_USER_REQUESTED = 10; const val REASON_USER_STOPPED = 11; const val REASON_DEPENDENCY_DIED = 12; const val REASON_OTHER = 13
    }
}
class WallpaperManager { companion object { @JvmStatic fun getInstance(c: Context?) = WallpaperManager() }; val wallpaperInfo: Any? get() = null }
class LocaleManager { var applicationLocales: android.os.LocaleList = android.os.LocaleList.getDefault() }
