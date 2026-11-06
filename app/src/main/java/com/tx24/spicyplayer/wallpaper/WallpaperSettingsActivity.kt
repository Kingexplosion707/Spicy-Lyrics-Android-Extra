package com.tx24.spicyplayer.wallpaper

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.WallpaperSection

/**
 * The wallpaper's own settings, opened from the system wallpaper picker: just the lyrics
 * toggle, saved at once. The full row (with the picker button) lives in Settings → Device.
 */
class WallpaperSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppSettings(getSharedPreferences("ui", Context.MODE_PRIVATE))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .systemBarsPadding()
                        .padding(16.dp),
                ) {
                    WallpaperSection(settings, showPickerButton = false)
                }
            }
        }
    }
}
