@file:Suppress("unused")
@file:JvmName("AndroidFontInterop")

package androidx.compose.ui.text.font

import com.tx24.spicyplayer.desktop.platform.AndroidResources

/** A font from the :app resources (R.font), as Android's Font(resId). */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    androidx.compose.ui.text.platform.Font(
        identity = "res-$resId-${weight.weight}-${style.value}",
        data = AndroidResources.open(resId).use { it.readBytes() },
        weight = weight,
        style = style,
    )

fun Font(resId: Int, weight: FontWeight, style: FontStyle, variationSettings: FontVariation.Settings): Font = Font(resId, weight, style)

/** A font file (Android's Font(File)). */
fun Font(file: java.io.File, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal, variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style)): Font =
    androidx.compose.ui.text.platform.Font(file, weight, style)
