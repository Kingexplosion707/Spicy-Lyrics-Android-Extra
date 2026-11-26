// The activity APIs the shared screens use: back handling and "pick a file" go to the desktop.
@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.activity.compose

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.tx24.spicyplayer.desktop.platform.BackDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry { current.value() } }
    entry.enabled = enabled
    DisposableEffect(entry) { BackDispatcher.push(entry); onDispose { BackDispatcher.remove(entry) } }
}

@Composable
fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<androidx.activity.BackEventCompat>) -> Unit) {
    val current = rememberUpdatedState(onBack)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(enabled) { scope.launch { current.value(kotlinx.coroutines.flow.emptyFlow()) } }
}


@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ActivityResultLauncher<I> {
    val current = rememberUpdatedState(onResult)
    return remember(contract) { ActivityResultLauncher { input -> contract.run(input) { current.value(it) } } }
}

val LocalActivity = androidx.compose.runtime.staticCompositionLocalOf<android.app.Activity?> { null }
