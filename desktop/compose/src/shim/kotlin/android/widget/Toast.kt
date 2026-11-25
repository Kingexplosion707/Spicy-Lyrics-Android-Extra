@file:Suppress("unused", "UNUSED_PARAMETER")

package android.widget

import android.content.Context
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform

/** A short message, shown as the app's own toast. */
class Toast private constructor(private val text: CharSequence) {
    fun show() = DesktopPlatform.toast(text.toString())
    fun cancel() {}
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        @JvmStatic fun makeText(c: Context?, text: CharSequence?, d: Int) = Toast(text ?: "")
        @JvmStatic fun makeText(c: Context?, id: Int, d: Int) = Toast(c?.getString(id) ?: "")
    }
}
