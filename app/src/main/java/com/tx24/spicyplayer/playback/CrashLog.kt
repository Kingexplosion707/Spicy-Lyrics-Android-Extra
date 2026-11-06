package com.tx24.spicyplayer.playback

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.tx24.spicyplayer.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How the app last ended, for the extra logs: the stack of the last crash (kept on the phone,
 * never sent), and on Android 11 and later why the system ended it (a crash, running out of
 * memory, not responding).
 */
object CrashLog {
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val file = file(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stack = error.stackTraceToString().lines().take(MAX_STACK_LINES).joinToString("\n")
                file.writeText("${stamp(System.currentTimeMillis())}, ${BuildConfig.VERSION_NAME}, thread ${thread.name}\n$stack")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lines(context: Context): List<String> {
        val crash = runCatching { file(context).takeIf(File::exists)?.readText() }.getOrNull()
            ?.lines()?.filter(String::isNotBlank).orEmpty()
        return crash.ifEmpty { listOf("no crash recorded") } + exits(context)
    }

    private fun exits(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val exits = runCatching { manager.getHistoricalProcessExitReasons(null, 0, MAX_EXITS) }.getOrNull().orEmpty()
        if (exits.isEmpty()) return emptyList()
        return listOf("", "Ended by the system (newest first):") + exits.map { exit ->
            "  ${stamp(exit.timestamp)} ${reason(exit.reason)}" +
                exit.description?.takeIf(String::isNotBlank)?.let { ", $it" }.orEmpty() +
                ", ${exit.pss / 1024} MB in use"
        }
    }

    private fun reason(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "out of memory (system)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "too much resource use"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by signal"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "force-stopped"
        ApplicationExitInfo.REASON_USER_STOPPED -> "stopped by the user"
        ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a dependency died"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        else -> "other ($code)"
    }

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")
    private fun stamp(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(ms))

    private const val MAX_STACK_LINES = 40
    private const val MAX_EXITS = 5
}
