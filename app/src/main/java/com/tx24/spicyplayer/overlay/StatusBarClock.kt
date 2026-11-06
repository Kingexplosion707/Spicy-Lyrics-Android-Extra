package com.tx24.spicyplayer.overlay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Hides the status bar's clock while the status bar lyrics show, so they take its place, and
 * puts it back after. It does that through the system's list of hidden status bar icons
 * (`icon_blacklist`, which System UI Tuner writes), which needs WRITE_SECURE_SETTINGS: a
 * permission only a shell can grant, so it's granted once through Shizuku ([grantWithShizuku]).
 *
 * The list as it was before is kept, so putting the clock back leaves any icons the user hid
 * themselves hidden.
 */
internal object StatusBarClock {
    private const val ICON_BLACKLIST = "icon_blacklist"
    private const val CLOCK = "clock"
    /** The `ui` setting: hide the clock while the status bar lyrics show. */
    const val PREF_HIDE = "overlayHideClock"
    /** Set while this app has the clock hidden, so a restart can put it back. */
    private const val PREF_HIDDEN_BY_US = "overlayClockHiddenByUs"

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Hides the clock (once; does nothing without the permission). */
    fun hide(context: Context) {
        if (!canWrite(context)) return
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val icons = current(context)
        if (CLOCK in icons) {
            // Already hidden: by us, or by the user (then it stays theirs to show).
            return
        }
        val written = runCatching {
            Settings.Secure.putString(context.contentResolver, ICON_BLACKLIST, (icons + CLOCK).joinToString(","))
        }.getOrDefault(false)
        if (written) prefs.edit().putBoolean(PREF_HIDDEN_BY_US, true).apply()
    }

    /** Shows the clock again, if this app hid it. */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_HIDDEN_BY_US, false) || !canWrite(context)) return
        val icons = current(context) - CLOCK
        runCatching {
            Settings.Secure.putString(context.contentResolver, ICON_BLACKLIST, icons.joinToString(",").ifEmpty { null })
        }
        prefs.edit().putBoolean(PREF_HIDDEN_BY_US, false).apply()
    }

    private fun current(context: Context): List<String> =
        Settings.Secure.getString(context.contentResolver, ICON_BLACKLIST).orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Why granting through Shizuku didn't work, or [Ok]. */
    enum class Grant(val message: String) {
        Ok("Ready: the clock hides while the lyrics show"),
        NotRunning("Shizuku isn't running. Start it in the Shizuku app, then try again"),
        Denied("Shizuku permission was denied"),
        Failed("Shizuku couldn't grant the permission"),
    }

    /**
     * Asks Shizuku for its permission if needed, then has it run
     * `pm grant <this app> android.permission.WRITE_SECURE_SETTINGS`. The grant lasts until the
     * app is uninstalled.
     */
    suspend fun grantWithShizuku(context: Context): Grant {
        if (canWrite(context)) return Grant.Ok
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return Grant.NotRunning
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) return Grant.Failed
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED && !requestPermission()) return Grant.Denied
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                // Shizuku's process API is private in its current library; it's reached directly.
                val newProcess = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
                ).apply { isAccessible = true }
                val command = arrayOf("pm", "grant", context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS)
                val process = newProcess.invoke(null, command, null, null) as Process
                process.waitFor() == 0
            }.getOrDefault(false)
        }
        return if (ok && canWrite(context)) Grant.Ok else Grant.Failed
    }

    private suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val code = 4206
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != code) return
                Shizuku.removeRequestPermissionResultListener(this)
                if (continuation.isActive) continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        runCatching { Shizuku.requestPermission(code) }.onFailure {
            Shizuku.removeRequestPermissionResultListener(listener)
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
