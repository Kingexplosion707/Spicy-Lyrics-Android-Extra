package com.tx24.spicyplayer.haptics

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Asks Android once more to let the live haptics capture the phone's sound (its "start recording
 * or casting" prompt), from the live haptics notification after Android stopped the capture, and
 * hands the answer to [LiveHapticsService]. Shows nothing of its own.
 */
class CaptureConsentActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            LiveHapticsService.startWithCapture(this, result.resultCode, data)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null || savedInstanceState != null) {
            finish()
            return
        }
        runCatching { ask.launch(manager.createScreenCaptureIntent()) }.onFailure { finish() }
    }
}
