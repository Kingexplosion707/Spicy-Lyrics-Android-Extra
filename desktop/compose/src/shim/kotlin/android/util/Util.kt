@file:Suppress("unused", "UNUSED_PARAMETER")

package android.util

import com.tx24.spicyplayer.platform.CoreLog

object Log {
    const val VERBOSE = 2; const val DEBUG = 3; const val INFO = 4; const val WARN = 5; const val ERROR = 6
    private val debug = System.getenv("SPICY_DEBUG") != null
    private fun out(level: String, tag: String?, msg: String?, t: Throwable? = null): Int {
        if (debug || level == "E" || level == "W") System.err.println("$level/$tag: $msg" + (t?.let { "\n" + it.stackTraceToString() } ?: ""))
        return 0
    }
    @JvmStatic fun v(tag: String?, msg: String): Int = out("V", tag, msg)
    @JvmStatic fun v(tag: String?, msg: String, t: Throwable?): Int = out("V", tag, msg, t)
    @JvmStatic fun d(tag: String?, msg: String): Int = out("D", tag, msg)
    @JvmStatic fun d(tag: String?, msg: String, t: Throwable?): Int = out("D", tag, msg, t)
    @JvmStatic fun i(tag: String?, msg: String): Int = out("I", tag, msg)
    @JvmStatic fun i(tag: String?, msg: String, t: Throwable?): Int = out("I", tag, msg, t)
    @JvmStatic fun w(tag: String?, msg: String): Int = out("W", tag, msg)
    @JvmStatic fun w(tag: String?, msg: String?, t: Throwable?): Int = out("W", tag, msg, t)
    @JvmStatic fun w(tag: String?, t: Throwable?): Int = out("W", tag, "", t)
    @JvmStatic fun e(tag: String?, msg: String): Int = out("E", tag, msg)
    @JvmStatic fun e(tag: String?, msg: String?, t: Throwable?): Int = out("E", tag, msg, t)
    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = debug
    @JvmStatic fun getStackTraceString(t: Throwable?): String = t?.stackTraceToString() ?: ""
}

object Base64 {
    const val DEFAULT = 0; const val NO_PADDING = 1; const val NO_WRAP = 2; const val CRLF = 4; const val URL_SAFE = 8
    private fun enc(flags: Int): java.util.Base64.Encoder {
        var e = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) e = e.withoutPadding()
        return e
    }
    @JvmStatic fun encodeToString(b: ByteArray, flags: Int): String = enc(flags).encodeToString(b)
    @JvmStatic fun encode(b: ByteArray, flags: Int): ByteArray = enc(flags).encode(b)
    @JvmStatic fun decode(s: String, flags: Int): ByteArray =
        (if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder() else java.util.Base64.getMimeDecoder()).decode(s.trim())
    @JvmStatic fun decode(b: ByteArray, flags: Int): ByteArray = decode(String(b), flags)
}

/** Half-precision floats, as Android's android.util.Half. */
object Half {
    @JvmStatic fun toHalf(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        var value = (bits and 0x7fffffff) + 0x1000
        if (value >= 0x47800000) {
            if (bits and 0x7fffffff >= 0x47800000) return (sign or 0x7c00 or ((bits and 0x007fffff) ushr 13)).toShort()
            return (sign or 0x7bff).toShort()
        }
        if (value >= 0x38800000) return (sign or ((value - 0x38000000) ushr 13)).toShort()
        if (value < 0x33000000) return sign.toShort()
        value = (bits and 0x7fffffff) ushr 23
        return (sign or (((bits and 0x7fffff) or 0x800000) + (0x800000 ushr (value - 102)) ushr (126 - value))).toShort()
    }
    @JvmStatic fun toFloat(h: Short): Float {
        val bits = h.toInt() and 0xffff
        val sign = (bits and 0x8000) shl 16
        val exp = (bits ushr 10) and 0x1f
        val mant = bits and 0x3ff
        if (exp == 0) return (if (sign != 0) -1 else 1) * mant / 16777216f
        if (exp == 31) return java.lang.Float.intBitsToFloat(sign or 0x7f800000 or (mant shl 13))
        return java.lang.Float.intBitsToFloat(sign or ((exp + 112) shl 23) or (mant shl 13))
    }
    @JvmStatic fun halfToShortBits(h: Short): Short = h
}

class Size(val width: Int, val height: Int)
class SizeF(val width: Float, val height: Float)
class Rational(val numerator: Int, val denominator: Int) { fun toFloat() = numerator.toFloat() / denominator; fun toDouble() = numerator.toDouble() / denominator }
class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<K, V>?) = size > maxSize }
    @Synchronized fun get(k: K): V? = map[k]
    @Synchronized fun put(k: K, v: V): V? = map.put(k, v)
    @Synchronized fun remove(k: K): V? = map.remove(k)
    @Synchronized fun evictAll() = map.clear()
    @Synchronized fun size() = map.size
    @Synchronized fun snapshot(): Map<K, V> = LinkedHashMap(map)
}
class TypedValue { companion object { const val COMPLEX_UNIT_DIP = 1; const val COMPLEX_UNIT_SP = 2; @JvmStatic fun applyDimension(u: Int, v: Float, m: android.content.res.DisplayMetrics) = v * m.density } }
class AtomicFile(val baseFile: java.io.File) {
    fun readFully(): ByteArray = baseFile.readBytes()
    fun openRead(): java.io.FileInputStream = baseFile.inputStream()
    fun startWrite(): java.io.FileOutputStream { baseFile.parentFile?.mkdirs(); return java.io.FileOutputStream(java.io.File(baseFile.path + ".new")) }
    fun finishWrite(s: java.io.FileOutputStream) { s.close(); java.io.File(baseFile.path + ".new").let { java.nio.file.Files.move(it.toPath(), baseFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) } }
    fun failWrite(s: java.io.FileOutputStream) { s.close(); java.io.File(baseFile.path + ".new").delete() }
    fun delete() { baseFile.delete() }
}
