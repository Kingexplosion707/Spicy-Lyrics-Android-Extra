@file:Suppress("unused")

package android.text

object TextUtils {
    @JvmStatic fun isEmpty(s: CharSequence?): Boolean = s.isNullOrEmpty()
    @JvmStatic fun equals(a: CharSequence?, b: CharSequence?): Boolean = a?.toString() == b?.toString()
    @JvmStatic fun join(d: CharSequence, parts: Iterable<*>): String = parts.joinToString(d)
    @JvmStatic fun join(d: CharSequence, parts: Array<*>): String = parts.joinToString(d)
    @JvmStatic fun htmlEncode(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    @JvmStatic fun isDigitsOnly(s: CharSequence) = s.all { it.isDigit() }
    enum class TruncateAt { START, MIDDLE, END, MARQUEE }
    /** [text] cut with an ellipsis to fit [avail] pixels in [paint]. */
    @JvmStatic fun ellipsize(text: CharSequence, paint: android.text.TextPaint, avail: Float, where: TruncateAt): CharSequence {
        val s = text.toString()
        if (paint.measureText(s) <= avail) return s
        var n = s.length
        while (n > 0 && paint.measureText(s.take(n) + "…") > avail) n--
        return s.take(n).trimEnd() + "…"
    }
}
object Html { const val FROM_HTML_MODE_LEGACY = 0; @JvmStatic fun fromHtml(s: String, f: Int): CharSequence = s.replace(Regex("<[^>]+>"), "") }
