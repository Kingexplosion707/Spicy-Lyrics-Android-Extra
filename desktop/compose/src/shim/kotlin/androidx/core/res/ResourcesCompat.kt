@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.core.content.res

object ResourcesCompat {
    @JvmStatic fun getFont(c: android.content.Context, id: Int): android.graphics.Typeface? =
        runCatching {
            val bytes = com.tx24.spicyplayer.desktop.platform.AndroidResources.open(id).readBytes()
            android.graphics.Typeface.ofSkia(org.jetbrains.skia.FontMgr.default.makeFromData(org.jetbrains.skia.Data.makeFromBytes(bytes)))
        }.getOrNull()
    @JvmStatic fun getDrawable(r: android.content.res.Resources, id: Int, theme: Any?): android.graphics.drawable.Drawable? = null
    @JvmStatic fun getColor(r: android.content.res.Resources, id: Int, theme: Any?): Int = r.getColor(id, theme)
}
