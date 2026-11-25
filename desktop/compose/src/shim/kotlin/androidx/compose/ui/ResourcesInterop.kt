@file:Suppress("unused")
@file:JvmName("AndroidResourcesInterop")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import com.tx24.spicyplayer.desktop.platform.AndroidResources
import org.xml.sax.InputSource

@Composable
fun stringResource(id: Int): String = AndroidResources.string(id)

@Composable
fun stringResource(id: Int, vararg args: Any): String = AndroidResources.string(id).format(*args)

@Composable
fun pluralStringResource(id: Int, count: Int, vararg args: Any): String = AndroidResources.plural(id, count).format(*args)

@Composable
fun ImageVector.Companion.vectorResource(id: Int): ImageVector {
    val density = LocalDensity.current
    return remember(id) { AndroidResources.open(id).use { loadXmlImageVector(InputSource(it), density) } }
}

@Composable
fun painterResource(id: Int): Painter {
    val path = AndroidResources.path(id) ?: ""
    return if (path.endsWith(".xml")) rememberVectorPainter(ImageVector.vectorResource(id))
    else {
        val bitmap = remember(id) { AndroidResources.open(id).use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() } }
        remember(bitmap) { BitmapPainter(bitmap) }
    }
}

@Composable
fun colorResource(id: Int): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(AndroidResources.color(id))
