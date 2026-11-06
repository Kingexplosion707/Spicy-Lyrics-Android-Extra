package com.tx24.spicyplayer.ui.profile

import com.tx24.spicyplayer.ui.components.SettingsSkeleton

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicyModalMessage
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import dev.chrisbanes.haze.HazeState

/**
 * A credit's profile page in a pop-up, so reading it doesn't leave the app. [line] is kept by the
 * caller through the closing animation, after [visible] goes false.
 */
@Composable
fun ProfileModal(line: FooterLine?, visible: Boolean, backdrop: HazeState?, onDismiss: () -> Unit) {
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = line?.name?.let { "@$it" } ?: "Profile",
        fillBody = true,
        // The page is a web view: building it mid-animation stutters the opening.
        skeleton = { SettingsSkeleton(rows = 5, modifier = Modifier.padding(SpicySpacing.S4)) },
    ) {
        val url = line?.profileUrl ?: return@SpicyModal
        key(url) { ProfilePage(url, onDismiss) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ProfilePage(url: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    // The site's own pages (its verification step included) stay here; anything else is the browser's.
    val home = remember(url) { Uri.parse(url).host.orEmpty().removePrefix("www.") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val openOutside: (String) -> Unit = { link -> runCatching { uriHandler.openUri(link) } }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val host = request.url.host.orEmpty()
                                if (request.url.scheme == "https" && (host == home || host.endsWith(".$home"))) return false
                                openOutside(request.url.toString())
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                failed = false
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) failed = true
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame && response.statusCode >= 500) failed = true
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                },
            )
            val loadingAlpha by animateFloatAsState(if (loading && !failed) 1f else 0f, tween(SpicyMotion.BASE_MS), label = "profileLoading")
            if (loadingAlpha > 0f) {
                Text(
                    "Loading profile…",
                    modifier = Modifier.align(Alignment.Center).alpha(loadingAlpha),
                    style = SpicyType.Body.copy(color = SpicyColors.TextSecondary),
                )
            }
            if (failed) {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(PAGE_COVER) }.padding(SpicySpacing.S6), contentAlignment = Alignment.Center) {
                    SpicyModalMessage(
                        title = "Couldn't load the profile",
                        description = "Check your connection, or open it in your browser instead.",
                        icon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(24.dp), tint = SpicyColors.TextPrimary) },
                    )
                }
            }
        }
        val hairline = SpicyColors.Hairline
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val px = 1.dp.toPx()
                    drawRect(hairline, Offset.Zero, Size(size.width, px))
                }
                .padding(horizontal = SpicySpacing.S6, vertical = SpicySpacing.S4),
        ) {
            SpicyModalButton(
                "Open in browser",
                {
                    onDismiss()
                    openOutside(url)
                },
                style = SpicyButtonStyle.Secondary,
                fill = true,
            )
        }
    }
}

private val PAGE_COVER = androidx.compose.ui.graphics.Color(22, 22, 22)
