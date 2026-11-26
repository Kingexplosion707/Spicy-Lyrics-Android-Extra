package com.tx24.spicyplayer.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import dev.chrisbanes.haze.HazeState

/** A credited writer's profile: on the desktop it opens in the browser. */
@Composable
fun ProfileModal(line: FooterLine?, visible: Boolean, backdrop: HazeState?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(visible, line?.profileUrl) {
        if (!visible) return@LaunchedEffect
        line?.profileUrl?.let { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it))) }
        onDismiss()
    }
}
