@file:Suppress("unused")

package android.net

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A URI: web links, and file:// paths for files picked on the desktop. */
class Uri private constructor(private val raw: String) : Comparable<Uri> {
    private val parsed: URI? by lazy { runCatching { URI(raw) }.getOrNull() }
    val scheme: String? get() = parsed?.scheme ?: raw.substringBefore(':', "").ifEmpty { null }
    val host: String? get() = parsed?.host
    val port: Int get() = parsed?.port ?: -1
    val path: String? get() = if (scheme == "file") File(parsed ?: URI(raw)).path else parsed?.path
    val encodedPath: String? get() = parsed?.rawPath
    val query: String? get() = parsed?.query
    val encodedQuery: String? get() = parsed?.rawQuery
    val fragment: String? get() = parsed?.fragment
    val authority: String? get() = parsed?.authority
    val lastPathSegment: String? get() = pathSegments.lastOrNull()
    val pathSegments: List<String> get() = (parsed?.path ?: "").split('/').filter { it.isNotEmpty() }
    val schemeSpecificPart: String? get() = parsed?.schemeSpecificPart
    val isAbsolute: Boolean get() = scheme != null
    val isRelative: Boolean get() = scheme == null
    val queryParameterNames: Set<String> get() = params().map { it.first }.toSet()
    private fun params(): List<Pair<String, String>> = (parsed?.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.map {
        val k = it.substringBefore('='); val v = it.substringAfter('=', "")
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
    fun getQueryParameter(name: String): String? = params().firstOrNull { it.first == name }?.second
    fun getQueryParameters(name: String): List<String> = params().filter { it.first == name }.map { it.second }
    fun getBooleanQueryParameter(name: String, def: Boolean): Boolean = getQueryParameter(name)?.let { it != "false" && it != "0" } ?: def
    fun buildUpon(): Builder = Builder(raw)
    override fun toString() = raw
    override fun equals(other: Any?) = other is Uri && other.raw == raw
    override fun hashCode() = raw.hashCode()
    override fun compareTo(other: Uri) = raw.compareTo(other.raw)

    class Builder(private var base: String = "") {
        private var scheme: String? = null
        private var authority: String? = null
        private var path = StringBuilder()
        private val query = mutableListOf<Pair<String, String>>()
        private var fragment: String? = null
        init { if (base.isNotEmpty()) URI(base).let { scheme = it.scheme; authority = it.rawAuthority; path.append(it.rawPath ?: ""); it.rawQuery?.split('&')?.filter { q -> q.isNotEmpty() }?.forEach { q -> query += q.substringBefore('=') to q.substringAfter('=', "") }; fragment = it.rawFragment } }
        fun scheme(s: String) = apply { scheme = s }
        fun authority(a: String) = apply { authority = a }
        fun encodedAuthority(a: String) = authority(a)
        fun path(p: String) = apply { path = StringBuilder(p.split('/').joinToString("/") { enc(it) }) }
        fun encodedPath(p: String) = apply { path = StringBuilder(p) }
        fun appendPath(p: String) = apply { if (!path.endsWith("/")) path.append('/'); path.append(enc(p)) }
        fun appendEncodedPath(p: String) = apply { if (!path.endsWith("/")) path.append('/'); path.append(p) }
        fun appendQueryParameter(k: String, v: String?) = apply { query += enc(k) to enc(v ?: "") }
        fun encodedQuery(q: String?) = apply { query.clear(); q?.split('&')?.filter { it.isNotEmpty() }?.forEach { query += it.substringBefore('=') to it.substringAfter('=', "") } }
        fun clearQuery() = apply { query.clear() }
        fun fragment(f: String?) = apply { fragment = f?.let(::enc) }
        fun build(): Uri {
            val sb = StringBuilder()
            scheme?.let { sb.append(it).append(':') }
            authority?.let { sb.append("//").append(it) }
            sb.append(path)
            if (query.isNotEmpty()) sb.append('?').append(query.joinToString("&") { "${it.first}=${it.second}" })
            fragment?.let { sb.append('#').append(it) }
            return Uri(sb.toString())
        }
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }

    companion object {
        @JvmField val EMPTY = Uri("")
        @JvmStatic fun parse(s: String?): Uri = Uri(s ?: "")
        @JvmStatic fun fromFile(f: File): Uri = Uri(f.toURI().toString())
        @JvmStatic fun fromParts(scheme: String, ssp: String, fragment: String?): Uri = Uri("$scheme:$ssp" + (fragment?.let { "#$it" } ?: ""))
        @JvmStatic fun encode(s: String?): String = URLEncoder.encode(s ?: "", "UTF-8").replace("+", "%20")
        @JvmStatic fun encode(s: String?, allow: String?): String {
            var out = encode(s); allow?.forEach { c -> out = out.replace(URLEncoder.encode(c.toString(), "UTF-8"), c.toString()) }; return out
        }
        @JvmStatic fun decode(s: String?): String = URLDecoder.decode(s ?: "", "UTF-8")
        @JvmStatic fun withAppendedPath(base: Uri, seg: String): Uri = base.buildUpon().appendEncodedPath(seg).build()
    }
}

/** androidx.core.net.toUri. */
fun String.toUri(): Uri = Uri.parse(this)
fun Uri.toFile(): File = File(path ?: "")

class ConnectivityManager {
    val activeNetwork: Network? get() = Network()
    val allNetworks: Array<Network> get() = arrayOf(Network())
    fun getNetworkCapabilities(n: Network?): NetworkCapabilities = NetworkCapabilities()
    val isActiveNetworkMetered: Boolean get() = false
    fun registerDefaultNetworkCallback(cb: NetworkCallback) {}
    fun unregisterNetworkCallback(cb: NetworkCallback) {}
    open class NetworkCallback { open fun onAvailable(n: Network) {}; open fun onLost(n: Network) {}; open fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {} }
}
class Network
class NetworkCapabilities {
    fun hasCapability(c: Int) = true
    fun hasTransport(t: Int) = t == TRANSPORT_ETHERNET || t == TRANSPORT_WIFI
    companion object {
        const val NET_CAPABILITY_INTERNET = 12; const val NET_CAPABILITY_VALIDATED = 16; const val NET_CAPABILITY_NOT_METERED = 11
        const val TRANSPORT_CELLULAR = 0; const val TRANSPORT_WIFI = 1; const val TRANSPORT_ETHERNET = 3; const val TRANSPORT_VPN = 4
    }
}
