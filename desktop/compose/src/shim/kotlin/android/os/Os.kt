// A small stand-in for the Android APIs the shared :app code calls, so that code runs on the
// desktop unchanged. Only what that code uses is here.
@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

package android.os

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

object Build {
    @JvmField val MANUFACTURER: String = System.getProperty("os.name") ?: "Desktop"
    @JvmField val MODEL: String = "Desktop"
    @JvmField val BRAND: String = "desktop"
    @JvmField val DEVICE: String = "desktop"
    @JvmField val PRODUCT: String = "desktop"
    @JvmField val FINGERPRINT: String = "desktop"
    @JvmField val SUPPORTED_ABIS: Array<String> = arrayOf(System.getProperty("os.arch") ?: "x86_64")
    object VERSION {
        /** The renderer's newest paths (runtime shaders, blur) all exist on the desktop. */
        const val SDK_INT: Int = 36
        @JvmField val RELEASE: String = System.getProperty("os.version") ?: ""
        const val PREVIEW_SDK_INT: Int = 0
    }
    object VERSION_CODES {
        const val M = 23; const val N = 24; const val N_MR1 = 25; const val O = 26; const val O_MR1 = 27
        const val P = 28; const val Q = 29; const val R = 30; const val S = 31; const val S_V2 = 32
        const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34; const val VANILLA_ICE_CREAM = 35; const val BAKLAVA = 36
    }
}

object SystemClock {
    private val start = System.nanoTime()
    @JvmStatic fun elapsedRealtime(): Long = (System.nanoTime() - start) / 1_000_000 + 60_000
    @JvmStatic fun elapsedRealtimeNanos(): Long = System.nanoTime() - start + 60_000_000_000
    @JvmStatic fun uptimeMillis(): Long = elapsedRealtime()
    @JvmStatic fun currentThreadTimeMillis(): Long = elapsedRealtime()
    @JvmStatic fun sleep(ms: Long) = Thread.sleep(ms)
}

/** The main thread is Swing's event thread; other loopers are single background threads. */
class Looper private constructor(internal val main: Boolean, internal val name: String) {
    internal val executor by lazy { Executors.newSingleThreadScheduledExecutor { r -> Thread(r, name).apply { isDaemon = true } } }
    val thread: Thread get() = Thread.currentThread()
    fun quit() { if (!main) executor.shutdownNow() }
    fun quitSafely() { if (!main) executor.shutdown() }
    companion object {
        private val MAIN = Looper(true, "main")
        private val mine = ThreadLocal<Looper?>()
        @JvmStatic fun getMainLooper(): Looper = MAIN
        @JvmStatic fun myLooper(): Looper? = if (SwingUtilities.isEventDispatchThread()) MAIN else mine.get()
        @JvmStatic fun prepare() {}
        @JvmStatic fun loop() {}
        internal fun background(name: String): Looper = Looper(false, name)
        internal fun bind(l: Looper) { l.executor.execute { mine.set(l) } }
    }
}

open class Handler(private val looper: Looper = Looper.getMainLooper()) {
    constructor(looper: Looper, callback: Any?) : this(looper)
    private val pending = ConcurrentHashMap<Runnable, MutableList<Any>>()
    fun getLooper(): Looper = looper

    fun post(r: Runnable): Boolean = postDelayed(r, 0)
    fun postAtFrontOfQueue(r: Runnable): Boolean = post(r)
    fun postDelayed(r: Runnable, delayMs: Long): Boolean {
        val wrapped = Runnable { pending[r]?.let { if (it.isNotEmpty()) it.removeAt(0) }; r.run() }
        if (looper.main) {
            val timer = javax.swing.Timer(delayMs.coerceAtLeast(0).toInt()) { wrapped.run() }.apply { isRepeats = false }
            pending.getOrPut(r) { mutableListOf() }.add(timer)
            timer.start()
        } else {
            val f = looper.executor.schedule(wrapped, delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            pending.getOrPut(r) { mutableListOf() }.add(f)
        }
        return true
    }
    fun postDelayed(r: Runnable, token: Any?, delayMs: Long): Boolean = postDelayed(r, delayMs)
    fun removeCallbacks(r: Runnable) {
        pending.remove(r)?.forEach { when (it) { is javax.swing.Timer -> it.stop(); is ScheduledFuture<*> -> it.cancel(false) } }
    }
    fun removeCallbacksAndMessages(token: Any?) { pending.keys.toList().forEach(::removeCallbacks) }
    fun hasCallbacks(r: Runnable): Boolean = pending[r]?.isNotEmpty() == true
}

open class HandlerThread(name: String, priority: Int = 0) : Thread(name) {
    private val l = Looper.background(name)
    val looper: Looper get() = l
    override fun start() { Looper.bind(l) }
    fun quit(): Boolean { l.quit(); return true }
    fun quitSafely(): Boolean { l.quitSafely(); return true }
}

object Process {
    const val THREAD_PRIORITY_BACKGROUND = 10
    const val THREAD_PRIORITY_DISPLAY = -4
    const val THREAD_PRIORITY_URGENT_DISPLAY = -8
    const val THREAD_PRIORITY_AUDIO = -16
    @JvmStatic fun setThreadPriority(p: Int) {}
    @JvmStatic fun myPid(): Int = ProcessHandle.current().pid().toInt()
}

open class Bundle() {
    private val map = LinkedHashMap<String, Any?>()
    constructor(other: Bundle) : this() { map.putAll(other.map) }
    fun putString(k: String, v: String?) { map[k] = v }
    fun putInt(k: String, v: Int) { map[k] = v }
    fun putLong(k: String, v: Long) { map[k] = v }
    fun putFloat(k: String, v: Float) { map[k] = v }
    fun putBoolean(k: String, v: Boolean) { map[k] = v }
    fun putCharSequence(k: String, v: CharSequence?) { map[k] = v }
    fun putParcelable(k: String, v: Any?) { map[k] = v }
    fun putBinder(k: String, v: Any?) { map[k] = v }
    fun putBundle(k: String, v: Bundle?) { map[k] = v }
    fun getString(k: String): String? = map[k] as? String
    fun getString(k: String, d: String): String = map[k] as? String ?: d
    fun getInt(k: String, d: Int = 0): Int = map[k] as? Int ?: d
    fun getLong(k: String, d: Long = 0): Long = map[k] as? Long ?: d
    fun getFloat(k: String, d: Float = 0f): Float = map[k] as? Float ?: d
    fun getBoolean(k: String, d: Boolean = false): Boolean = map[k] as? Boolean ?: d
    fun getCharSequence(k: String): CharSequence? = map[k] as? CharSequence
    @Suppress("UNCHECKED_CAST") fun <T> getParcelable(k: String): T? = map[k] as? T
    @Suppress("UNCHECKED_CAST") fun <T> getParcelable(k: String, c: Class<T>): T? = map[k] as? T
    fun getBundle(k: String): Bundle? = map[k] as? Bundle
    fun containsKey(k: String) = map.containsKey(k)
    fun keySet(): Set<String> = map.keys
    fun get(k: String): Any? = map[k]
    fun remove(k: String) { map.remove(k) }
    fun isEmpty() = map.isEmpty()
    fun size() = map.size
    companion object { @JvmField val EMPTY = Bundle() }
}

interface Parcelable
interface IBinder
open class Binder : IBinder

class PowerManager {
    val isPowerSaveMode: Boolean get() = false
    val isInteractive: Boolean get() = true
    val isIgnoringBatteryOptimizations: (String) -> Boolean = { true }
    fun isIgnoringBatteryOptimizations(pkg: String) = true
    fun newWakeLock(flags: Int, tag: String) = WakeLock()
    class WakeLock { fun acquire() {}; fun acquire(ms: Long) {}; fun release() {}; val isHeld = false; fun setReferenceCounted(b: Boolean) {} }
    companion object {
        const val ACTION_POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED"
        const val PARTIAL_WAKE_LOCK = 1
        const val SCREEN_BRIGHT_WAKE_LOCK = 10
    }
}

class BatteryManager {
    val isCharging: Boolean get() = true
    fun getIntProperty(id: Int): Int = 100
    companion object { const val BATTERY_PROPERTY_CAPACITY = 4; const val EXTRA_LEVEL = "level"; const val EXTRA_SCALE = "scale"; const val EXTRA_PLUGGED = "plugged" }
}
