package io.music_assistant.client.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter

/** Android keeps Kermit's stock Logcat writer. */
actual fun platformLogWriters(): List<LogWriter> = listOf(platformLogWriter())
