package com.tx24.spicyplayer.aod

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Sets where the fingerprint sensor is by touch: full screen, as the always-on display is, and
 * the first finger to rest on the screen marks the spot ([AodFingerprint.saveTouched]). Outside
 * the lock screen the sensor isn't listening, so the touch reaches the app.
 */
class FingerprintSpotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Rounded.Fingerprint, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        "Rest your finger on the fingerprint sensor, where the lock screen shows its icon.",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center,
                    )
                    Text(
                        "Press back to cancel.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val view = window.decorView
            if (view.width > 0 && view.height > 0) {
                AodFingerprint.saveTouched(this, ev.x / view.width, ev.y / view.height)
                window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                Toast.makeText(this, "Fingerprint spot set", Toast.LENGTH_SHORT).show()
                finish()
            }
            return true
        }
        return true
    }
}
