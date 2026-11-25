// androidx.core helpers the shared code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.content

import android.content.Context
import android.content.SharedPreferences

inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
    val e = edit(); e.action(); if (commit) e.commit() else e.apply()
}

inline fun <reified T : Any> Context.getSystemService(): T? = getSystemService(T::class.java)

object ContextCompat {
    const val RECEIVER_EXPORTED = 2
    const val RECEIVER_NOT_EXPORTED = 4
    @JvmStatic fun <T> getSystemService(c: Context, cls: Class<T>): T? = c.getSystemService(cls)
    @JvmStatic fun registerReceiver(c: Context, r: android.content.BroadcastReceiver?, f: android.content.IntentFilter, flags: Int): android.content.Intent? = c.registerReceiver(r, f)
    @JvmStatic fun startForegroundService(c: Context, i: android.content.Intent) {}
    @JvmStatic fun checkSelfPermission(c: Context, p: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED
    @JvmStatic fun getColor(c: Context, id: Int): Int = c.resources.getColor(id, null)
    @JvmStatic fun getMainExecutor(c: Context): java.util.concurrent.Executor = java.util.concurrent.Executor { javax.swing.SwingUtilities.invokeLater(it) }
}

object FileProvider {
    @JvmStatic fun getUriForFile(c: Context, authority: String, f: java.io.File): android.net.Uri = android.net.Uri.fromFile(f)
}
