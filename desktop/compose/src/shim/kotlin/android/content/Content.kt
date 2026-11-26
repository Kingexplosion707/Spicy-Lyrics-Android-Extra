@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content

import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform
import com.tx24.spicyplayer.desktop.platform.FilePreferences
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** The app's one context on the desktop: its folders, preferences, resources and services. */
open class Context {
    open val applicationContext: Context get() = DesktopPlatform.context
    open val packageName: String get() = "com.behnoud.spicylyrics"
    open val filesDir: File get() = DesktopPlatform.dataDir("files")
    open val cacheDir: File get() = DesktopPlatform.dataDir("cache")
    open val noBackupFilesDir: File get() = filesDir
    open val resources: Resources get() = Resources.instance
    open val packageManager: PackageManager get() = PackageManager.instance
    open val contentResolver: ContentResolver get() = ContentResolver.instance
    open val mainLooper: android.os.Looper get() = android.os.Looper.getMainLooper()
    open val theme: Any? get() = null

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = FilePreferences.named(name)
    open fun getString(id: Int): String = resources.getString(id)
    open fun getString(id: Int, vararg args: Any?): String = resources.getString(id, *args)
    open fun getText(id: Int): CharSequence = getString(id)
    open fun getDir(name: String, mode: Int): File = DesktopPlatform.dataDir(name)
    open fun getExternalFilesDir(type: String?): File? = filesDir
    open fun getDatabasePath(name: String): File = File(DesktopPlatform.dataDir("databases"), name)
    open fun deleteFile(name: String): Boolean = File(filesDir, name).delete()
    open fun openFileInput(name: String): InputStream = File(filesDir, name).inputStream()
    open fun openFileOutput(name: String, mode: Int): OutputStream = File(filesDir, name).outputStream()
    open fun fileList(): Array<String> = filesDir.list() ?: emptyArray()

    /** Every service the shared code asks for has a desktop stand-in (Android returns a platform type). */
    open fun <T> getSystemService(c: Class<T>): T = DesktopPlatform.service(c) ?: throw IllegalStateException("No ${c.simpleName} on the desktop")
    open fun getSystemService(name: String): Any? = DesktopPlatform.service(name)

    open fun startActivity(intent: Intent) = DesktopPlatform.startActivity(intent)
    open fun startActivity(intent: Intent, options: Bundle?) = DesktopPlatform.startActivity(intent)
    open fun startService(intent: Intent): ComponentName? = null
    open fun startForegroundService(intent: Intent): ComponentName? = null
    open fun stopService(intent: Intent): Boolean = false
    open fun sendBroadcast(intent: Intent) = DesktopPlatform.broadcast(intent)
    open fun sendBroadcast(intent: Intent, permission: String?) = DesktopPlatform.broadcast(intent)
    open fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter): Intent? { DesktopPlatform.register(r, f); return null }
    open fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter, flags: Int): Intent? = registerReceiver(r, f)
    open fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter, perm: String?, h: android.os.Handler?): Intent? = registerReceiver(r, f)
    open fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter, perm: String?, h: android.os.Handler?, flags: Int): Intent? = registerReceiver(r, f)
    open fun unregisterReceiver(r: BroadcastReceiver) = DesktopPlatform.unregister(r)
    open fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
    open fun grantUriPermission(pkg: String, uri: Uri, flags: Int) {}
    open fun createConfigurationContext(c: android.content.res.Configuration): Context = this

    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_APPEND = 32768
        const val RECEIVER_EXPORTED = 2
        const val RECEIVER_NOT_EXPORTED = 4
        const val BIND_AUTO_CREATE = 1
        const val NOTIFICATION_SERVICE = "notification"
        const val AUDIO_SERVICE = "audio"
        const val POWER_SERVICE = "power"
        const val CLIPBOARD_SERVICE = "clipboard"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val MEDIA_SESSION_SERVICE = "media_session"
        const val WINDOW_SERVICE = "window"
        const val KEYGUARD_SERVICE = "keyguard"
        const val VIBRATOR_SERVICE = "vibrator"
        const val ACTIVITY_SERVICE = "activity"
        const val DISPLAY_SERVICE = "display"
        const val INPUT_METHOD_SERVICE = "input_method"
        const val TEXT_CLASSIFICATION_SERVICE = "textclassification"
    }
}

open class ContextWrapper(private val base: Context?) : Context() {
    val baseContext: Context get() = base ?: DesktopPlatform.context
}

interface SharedPreferences {
    val all: Map<String, *>
    fun getString(key: String, def: String?): String?
    fun getStringSet(key: String, def: Set<String>?): Set<String>?
    fun getInt(key: String, def: Int): Int
    fun getLong(key: String, def: Long): Long
    fun getFloat(key: String, def: Float): Float
    fun getBoolean(key: String, def: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener)

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, value: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?)
    }
}

/** androidx.core.content.edit, for the shared code. */
inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
    val e = edit(); e.action(); if (commit) e.commit() else e.apply()
}

open class Intent() {
    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var flags: Int = 0
    var component: ComponentName? = null
    var `package`: String? = null
    val categories = mutableSetOf<String>()
    private val extrasBundle = Bundle()
    val extras: Bundle? get() = extrasBundle
    var clipData: ClipData? = null

    constructor(action: String?) : this() { this.action = action }
    constructor(action: String?, uri: Uri?) : this() { this.action = action; this.data = uri }
    constructor(context: Context?, cls: Class<*>) : this() { component = ComponentName("com.behnoud.spicylyrics", cls.name) }
    constructor(other: Intent) : this() { action = other.action; data = other.data; type = other.type; flags = other.flags; component = other.component }

    fun setAction(a: String?): Intent { action = a; return this }
    fun setData(u: Uri?): Intent { data = u; return this }
    fun setType(t: String?): Intent { type = t; return this }
    fun setDataAndType(u: Uri?, t: String?): Intent { data = u; type = t; return this }
    fun setPackage(p: String?): Intent { `package` = p; return this }
    fun setClassName(pkg: String, cls: String): Intent { component = ComponentName(pkg, cls); return this }
    fun setComponent(c: ComponentName?): Intent { component = c; return this }
    fun addFlags(f: Int): Intent { flags = flags or f; return this }
    fun setFlags(f: Int): Intent { flags = f; return this }
    fun addCategory(c: String): Intent { categories += c; return this }
    fun putExtra(k: String, v: String?): Intent { extrasBundle.putString(k, v); return this }
    fun putExtra(k: String, v: CharSequence?): Intent { extrasBundle.putCharSequence(k, v); return this }
    fun putExtra(k: String, v: Int): Intent { extrasBundle.putInt(k, v); return this }
    fun putExtra(k: String, v: Long): Intent { extrasBundle.putLong(k, v); return this }
    fun putExtra(k: String, v: Float): Intent { extrasBundle.putFloat(k, v); return this }
    fun putExtra(k: String, v: Boolean): Intent { extrasBundle.putBoolean(k, v); return this }
    fun putExtra(k: String, v: Any?): Intent { extrasBundle.putParcelable(k, v); return this }
    fun putExtras(b: Bundle): Intent { b.keySet().forEach { extrasBundle.putParcelable(it, b.get(it)) }; return this }
    fun getStringExtra(k: String): String? = extrasBundle.getString(k)
    fun getCharSequenceExtra(k: String): CharSequence? = extrasBundle.getCharSequence(k)
    fun getIntExtra(k: String, d: Int): Int = extrasBundle.getInt(k, d)
    fun getLongExtra(k: String, d: Long): Long = extrasBundle.getLong(k, d)
    fun getFloatExtra(k: String, d: Float): Float = extrasBundle.getFloat(k, d)
    fun getBooleanExtra(k: String, d: Boolean): Boolean = extrasBundle.getBoolean(k, d)
    fun <T> getParcelableExtra(k: String): T? = extrasBundle.getParcelable(k)
    fun <T> getParcelableExtra(k: String, c: Class<T>): T? = extrasBundle.getParcelable(k)
    fun hasExtra(k: String) = extrasBundle.containsKey(k)
    fun resolveActivity(pm: PackageManager): ComponentName? = component

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
        const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"
        const val ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT"
        const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"
        const val ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"
        const val ACTION_CONFIGURATION_CHANGED = "android.intent.action.CONFIGURATION_CHANGED"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES"
        const val EXTRA_KEY_EVENT = "android.intent.extra.KEY_EVENT"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
        const val CATEGORY_HOME = "android.intent.category.HOME"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_NO_HISTORY = 0x40000000
        const val FLAG_ACTIVITY_NO_ANIMATION = 0x00010000
        const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
        const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 64
        @JvmStatic fun createChooser(target: Intent, title: CharSequence?): Intent = target
    }
}

class ActivityNotFoundException(msg: String? = null) : RuntimeException(msg)

data class ComponentName(val packageName: String, val className: String) {
    constructor(context: Context, cls: Class<*>) : this(context.packageName, cls.name)
    fun flattenToString() = "$packageName/$className"
    fun flattenToShortString() = flattenToString()
    companion object { @JvmStatic fun unflattenFromString(s: String): ComponentName? = s.split('/').takeIf { it.size == 2 }?.let { ComponentName(it[0], it[1]) } }
}

abstract class BroadcastReceiver {
    abstract fun onReceive(context: Context, intent: Intent)
    fun goAsync(): PendingResult = PendingResult()
    class PendingResult { fun finish() {} }
}

class IntentFilter() {
    val actions = mutableListOf<String>()
    constructor(action: String) : this() { actions += action }
    fun addAction(a: String) { actions += a }
    fun hasAction(a: String) = a in actions
    fun addCategory(c: String) {}
    fun addDataScheme(s: String) {}
    var priority: Int = 0
}

class ClipData private constructor(val label: CharSequence?, private val text: CharSequence?, private val uri: Uri?) {
    class Item(val text: CharSequence?, val uri: Uri? = null) { fun coerceToText(c: Context?): CharSequence = text ?: uri?.toString() ?: "" }
    val itemCount: Int get() = 1
    fun getItemAt(i: Int) = Item(text, uri)
    val description: Any? get() = null
    companion object {
        @JvmStatic fun newPlainText(label: CharSequence?, text: CharSequence?) = ClipData(label, text, null)
        @JvmStatic fun newRawUri(label: CharSequence?, uri: Uri?) = ClipData(label, null, uri)
        @JvmStatic fun newUri(r: ContentResolver?, label: CharSequence?, uri: Uri?) = ClipData(label, null, uri)
    }
}

class ClipboardManager {
    fun setPrimaryClip(clip: ClipData) {
        val text = clip.getItemAt(0).coerceToText(null).toString()
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
    }
    val primaryClip: ClipData?
        get() = runCatching {
            val s = java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            s?.let { ClipData.newPlainText("text", it) }
        }.getOrNull()
    fun hasPrimaryClip() = primaryClip != null
    fun clearPrimaryClip() = setPrimaryClip(ClipData.newPlainText("", ""))
}

/** Files only: a desktop Uri is a file:// path (file pickers hand those back). */
class ContentResolver {
    fun openInputStream(uri: Uri): InputStream? = runCatching { uri.toFile().inputStream() }.getOrNull()
    fun openOutputStream(uri: Uri): OutputStream? = runCatching { uri.toFile().also { it.parentFile?.mkdirs() }.outputStream() }.getOrNull()
    fun openOutputStream(uri: Uri, mode: String): OutputStream? = openOutputStream(uri)
    fun getType(uri: Uri): String? = runCatching { java.nio.file.Files.probeContentType(uri.toFile().toPath()) }.getOrNull()
    fun takePersistableUriPermission(uri: Uri, flags: Int) {}
    fun releasePersistableUriPermission(uri: Uri, flags: Int) {}
    val persistedUriPermissions: List<Any> get() = emptyList()
    fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): android.database.Cursor? =
        android.database.Cursor.forFile(uri.toFile())
    fun delete(uri: Uri, where: String?, args: Array<String>?): Int = if (uri.toFile().delete()) 1 else 0
    companion object {
        internal val instance = ContentResolver()
        const val SCHEME_CONTENT = "content"
        const val SCHEME_FILE = "file"
    }
}

private fun Uri.toFile(): File = File(path ?: toString().removePrefix("file://"))
