@file:Suppress("unused")
package timber.log

/** Timber's calls, to the desktop's log. */
object Timber {
    private val debug = System.getenv("SPICY_DEBUG") != null
    @JvmStatic fun tag(tag: String): Timber = this
    @JvmStatic fun v(msg: String?, vararg args: Any?) { if (debug) println("V: ${fmt(msg, args)}") }
    @JvmStatic fun d(msg: String?, vararg args: Any?) { if (debug) println("D: ${fmt(msg, args)}") }
    @JvmStatic fun i(msg: String?, vararg args: Any?) { if (debug) println("I: ${fmt(msg, args)}") }
    @JvmStatic fun w(msg: String?, vararg args: Any?) { System.err.println("W: ${fmt(msg, args)}") }
    @JvmStatic fun w(t: Throwable?, msg: String?, vararg args: Any?) { System.err.println("W: ${fmt(msg, args)} $t") }
    @JvmStatic fun w(t: Throwable?) { System.err.println("W: $t") }
    @JvmStatic fun e(msg: String?, vararg args: Any?) { System.err.println("E: ${fmt(msg, args)}") }
    @JvmStatic fun e(t: Throwable?, msg: String?, vararg args: Any?) { System.err.println("E: ${fmt(msg, args)}\n${t?.stackTraceToString()}") }
    @JvmStatic fun e(t: Throwable?) { System.err.println("E: ${t?.stackTraceToString()}") }
    @JvmStatic fun d(t: Throwable?, msg: String?, vararg args: Any?) { if (debug) println("D: ${fmt(msg, args)} $t") }
    private fun fmt(msg: String?, args: Array<out Any?>) = if (args.isEmpty()) msg ?: "" else runCatching { (msg ?: "").format(*args) }.getOrDefault(msg ?: "")
}
