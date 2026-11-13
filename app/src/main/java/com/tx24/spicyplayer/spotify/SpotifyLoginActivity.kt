package com.tx24.spicyplayer.spotify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Where Spotify's sign-in page comes back to ([SpotifyAccount.REDIRECT_URI]): finishes signing in and closes. */
class SpotifyLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirect = intent?.data
        if (redirect == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val error = SpotifyAccount.finishLogin(this@SpotifyLoginActivity, redirect)
            val name = SpotifyAccount.accountName(this@SpotifyLoginActivity)
            Toast.makeText(
                this@SpotifyLoginActivity,
                error ?: if (name != null) "Signed in to Spotify as $name" else "Signed in to Spotify",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }
}
