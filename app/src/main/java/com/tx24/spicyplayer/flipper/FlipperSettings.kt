package com.tx24.spicyplayer.flipper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.ui.components.ToggleRow

/** Settings → Device → Flipper Zero: the lyrics on a Flipper running the Spicy Lyrics app ([FlipperService]). */
@Composable
internal fun FlipperRow() {
    val context = LocalContext.current
    if (!FlipperService.supported(context)) return
    val prefs = remember { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var on by remember { mutableStateOf(FlipperService.enabled(context) && FlipperService.permitted(context)) }
    fun turn(value: Boolean) {
        on = value
        prefs.edit().putBoolean(FlipperService.PREF_ENABLED, value).apply()
        FlipperService.sync(context)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        turn(granted.values.all { it })
    }
    ToggleRow(
        label = "Flipper Zero",
        checked = on,
        onCheckedChange = { value ->
            if (value && !FlipperService.permitted(context)) ask.launch(FlipperService.permissions) else turn(value)
        },
        description = FlipperService.HOW,
        icon = Icons.Rounded.Memory,
        searchDescription = "Flipper Zero lyrics Bluetooth USB buttons",
    )
}
