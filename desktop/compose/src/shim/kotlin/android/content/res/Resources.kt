@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content.res

import com.tx24.spicyplayer.desktop.platform.AndroidResources
import java.io.InputStream

class Resources {
    fun getString(id: Int): String = AndroidResources.string(id)
    fun getString(id: Int, vararg args: Any?): String = AndroidResources.string(id).format(*args)
    fun getText(id: Int): CharSequence = getString(id)
    fun getStringArray(id: Int): Array<String> = AndroidResources.stringArray(id)
    fun getQuantityString(id: Int, n: Int, vararg args: Any?): String = AndroidResources.plural(id, n).format(*args)
    fun getQuantityString(id: Int, n: Int): String = AndroidResources.plural(id, n)
    fun getColor(id: Int, theme: Any?): Int = AndroidResources.color(id)
    fun getDimension(id: Int): Float = 0f
    fun getIdentifier(name: String, type: String, pkg: String): Int = AndroidResources.identifier(type, name)
    fun openRawResource(id: Int): InputStream = AndroidResources.open(id)
    fun getDrawable(id: Int, theme: Any?): android.graphics.drawable.Drawable? = null
    fun getDrawable(id: Int): android.graphics.drawable.Drawable? = null
    val displayMetrics: DisplayMetrics get() = DisplayMetrics()
    val configuration: Configuration get() = Configuration()
    class NotFoundException(msg: String? = null) : RuntimeException(msg)
    companion object {
        internal val instance = Resources()
        @JvmStatic fun getSystem(): Resources = instance
    }
}

class DisplayMetrics { var density = 1f; var densityDpi = 160; var widthPixels = 1920; var heightPixels = 1080; var scaledDensity = 1f }

class Configuration {
    var orientation: Int = ORIENTATION_LANDSCAPE
    var uiMode: Int = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL
    var screenWidthDp: Int = 1280
    var screenHeightDp: Int = 800
    var smallestScreenWidthDp: Int = 800
    var fontScale: Float = 1f
    var densityDpi: Int = 160
    var screenLayout: Int = SCREENLAYOUT_SIZE_LARGE
    val locales: android.os.LocaleList get() = android.os.LocaleList.getDefault()
    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_TYPE_MASK = 15
        const val UI_MODE_TYPE_NORMAL = 1
        const val UI_MODE_TYPE_TELEVISION = 4
        const val UI_MODE_TYPE_WATCH = 6
        const val UI_MODE_NIGHT_MASK = 48
        const val UI_MODE_NIGHT_YES = 32
        const val SCREENLAYOUT_SIZE_MASK = 15; const val SCREENLAYOUT_SIZE_SMALL = 1; const val SCREENLAYOUT_SIZE_NORMAL = 2; const val SCREENLAYOUT_SIZE_LARGE = 3; const val SCREENLAYOUT_SIZE_XLARGE = 4
    }
}
