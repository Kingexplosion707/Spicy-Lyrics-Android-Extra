package com.tx24.spicyplayer.desktop.platform

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Where the desktop app keeps its things, and the Android services the shared code asks for. */
object DesktopPlatform {
    val application: Application by lazy { Application() }
    val context: Context get() = application

    private val root: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File)
            ?: File(System.getProperty("user.home"), if (System.getProperty("os.name").startsWith("Mac")) "Library/Application Support" else ".config")
        File(base, "SpicyLyricsPlus").apply { mkdirs() }
    }
    fun dataDir(name: String): File = File(root, name).apply { mkdirs() }

    /** The desktop app's own version (its installer's), e.g. "1.0.12". */
    val version: String by lazy { System.getProperty("spicy.version") ?: DesktopPlatform::class.java.`package`?.implementationVersion ?: "1.0.0" }

    /** Ends the app (after the preferences are written). */
    @Volatile var onQuit: () -> Unit = { kotlin.system.exitProcess(0) }
    fun quit() { runCatching { FilePreferences.flush() }; onQuit() }

    val installId: String by lazy {
        val f = File(root, "install-id")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString().also { f.writeText(it) }
    }

    private val services = mutableMapOf<Class<*>, Any>()
    private val named = mapOf(
        Context.CLIPBOARD_SERVICE to android.content.ClipboardManager::class.java,
        Context.POWER_SERVICE to android.os.PowerManager::class.java,
        Context.NOTIFICATION_SERVICE to android.app.NotificationManager::class.java,
        Context.AUDIO_SERVICE to android.media.AudioManager::class.java,
        Context.MEDIA_SESSION_SERVICE to android.media.session.MediaSessionManager::class.java,
        Context.CONNECTIVITY_SERVICE to android.net.ConnectivityManager::class.java,
        Context.KEYGUARD_SERVICE to android.app.KeyguardManager::class.java,
        Context.ACTIVITY_SERVICE to android.app.ActivityManager::class.java,
    )
    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> service(c: Class<T>): T? = services.getOrPut(c) {
        runCatching { c.getDeclaredConstructor().also { it.isAccessible = true }.newInstance() as Any }.getOrNull() ?: return null
    } as T
    fun service(name: String): Any? = named[name]?.let { service(it) }

    /** Web links open in the browser; anything else Android-only is ignored. */
    fun startActivity(intent: Intent) {
        val uri = intent.data?.toString() ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("http") }
        if (uri != null && (uri.startsWith("http") || uri.startsWith("mailto:") || uri.startsWith("file:"))) {
            runCatching { Desktop.getDesktop().browse(URI(uri)) }
        } else if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { android.content.ClipboardManager().setPrimaryClip(android.content.ClipData.newPlainText("", it)); toast("Copied") }
        }
    }

    private val receivers = CopyOnWriteArrayList<Pair<BroadcastReceiver, IntentFilter>>()
    fun register(r: BroadcastReceiver?, f: IntentFilter) { if (r != null) receivers += r to f }
    fun unregister(r: BroadcastReceiver) { receivers.removeIf { it.first === r } }
    fun broadcast(intent: Intent) { receivers.filter { it.second.hasAction(intent.action ?: "") }.forEach { it.first.onReceive(context, intent) } }

    private val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages = toasts.asSharedFlow()
    fun toast(text: String) { toasts.tryEmit(text) }
}
