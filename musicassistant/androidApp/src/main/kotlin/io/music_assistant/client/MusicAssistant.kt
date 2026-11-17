package io.music_assistant.client

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.appModule
import io.music_assistant.client.di.initKoin
import io.music_assistant.client.services.MediaNotificationManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import java.io.File

/**
 * Music Assistant inside Spicy Lyrics Plus: started the first time any of its parts is used (its
 * screen, its media services), not with the app, so Spicy pays nothing for it until then.
 */
object MusicAssistant {
    @Volatile private var started = false

    /** The host's own Koin modules (Spicy's Spotify play route), set before anything starts. */
    @Volatile var hostModules: () -> List<Module> = { emptyList() }

    fun ensure(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            val app = context.applicationContext
            val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            initKoin(androidModule(), appModule(), *hostModules().toTypedArray(), verboseLogging = debuggable) {
                androidContext(app)
            }
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(MediaNotificationManager.CHANNEL_ID, "Music Assistant playback", NotificationManager.IMPORTANCE_LOW),
            )
            File(app.cacheDir, "ma_client_logs.txt").delete()
            started = true
        }
    }
}
