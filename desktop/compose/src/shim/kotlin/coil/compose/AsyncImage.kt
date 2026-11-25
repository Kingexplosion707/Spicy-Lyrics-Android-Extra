@file:Suppress("unused", "UNUSED_PARAMETER")

package coil.compose

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    var image by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model) {
        val result = coil.Coil.imageLoader(DesktopPlatform.context).execute(coil.request.ImageRequest.Builder(DesktopPlatform.context).data(model).build())
        image = ((result as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.asImageBitmap()
    }
    val img = image
    if (img != null) Image(img, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)
    else if (placeholder != null) Image(placeholder, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)
    else Box(modifier)
}
