package com.tx24.spicyplayer.musicassistant

import android.content.Context
import android.content.Intent
import android.os.Build
import com.tx24.spicyplayer.spotify.SpotifyPlayRoute
import io.music_assistant.client.MusicAssistant
import io.music_assistant.client.data.play.PlayInterceptor
import org.koin.dsl.module

/**
 * Music Assistant inside Spicy Lyrics Plus (its code is in musicassistant/): its library, search,
 * players and queue, opened from the lyrics screen's controls or Settings. It starts only when
 * first opened. Its Spotify songs play through Spicy's Spotify sign-in ([SpotifyPlayRoute]).
 */
object SpicyMusicAssistant {
    /** Music Assistant needs Android 9. */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Called once from the Application: tells Music Assistant about Spicy's Spotify play route. Starts nothing. */
    fun install(context: Context) {
        val app = context.applicationContext
        MusicAssistant.hostModules = { listOf(module { single<PlayInterceptor> { SpotifyPlayRoute(app) } }) }
    }

    fun open(context: Context) {
        if (!supported()) return
        context.startActivity(
            Intent(context, io.music_assistant.client.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
