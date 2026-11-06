package com.tx24.spicyplayer.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.network.data.TrafficCounter
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.playback.SyncTrace
import com.tx24.spicyplayer.playback.TranslationTrace
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SpicyButton
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What Settings → Advanced shows, plus the app, phone and player versions, as plain text for a bug
 * report. Keys, tokens and URL queries are blanked out.
 */
internal fun debugReport(context: Context, state: PlayerUiState): String {
    val playerVersion = state.sourcePackage?.let { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
    }
    val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    val report = buildString {
        appendLine("Spicy Lyrics Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Song: ${state.title} · ${state.artist}" + state.album.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
        sessionLines(state).forEach { (label, value) ->
            appendLine("$label: $value" + if (label == "Player" && playerVersion != null) " $playerVersion" else "")
        }
        appendLine("Delays: output ${state.lyricDelayMs.signed()} ms, ${state.playerLabel ?: "player"} ${state.playerDelayMs.signed()} ms, this song ${state.songDelayMs.signed()} ms")
        appendLine("Lyrics: ${songSummary(state)}")
        lyricsLines(state)?.forEach { (label, value) -> appendLine("$label: $value") }
        textLines(context, state).forEach { (label, value) -> appendLine("$label: $value") }
        appendLine()
        appendLine("Last lookup:")
        if (state.providerAttempts.isEmpty()) appendLine("  none yet")
        state.providerAttempts.forEach { attempt ->
            val detail = attemptDetail(attempt) { time.format(it) }
            appendLine("  ${attemptSourceName(attempt, state)}: ${attempt.outcome.label()}" + if (detail.isNotEmpty()) " ($detail)" else "")
        }
    }
    return redactSecrets(report).trimEnd()
}

/**
 * The step-by-step logs kept apart from [debugReport], which they'd bury: what translation did,
 * how the lyric clock was synced, and what was downloaded. Null when there are none yet.
 */
internal fun extraLogs(context: Context): String? {
    val now = SystemClock.elapsedRealtime()
    val report = buildString {
        fun section(title: String, lines: List<String>) {
            if (lines.isEmpty()) return
            if (isNotEmpty()) appendLine()
            appendLine(title)
            lines.forEach { appendLine("  $it") }
        }
        section("Translation (newest last):", TranslationTrace.lines(now))
        section("Sync (newest last):", SyncTrace.lines(now))
        section("Downloaded since start:", TrafficCounter.lines())
        section("Widgets:", com.tx24.spicyplayer.widget.LyricsWidget.sizeLines(context).takeIf { it.size > 1 }.orEmpty())
        section("Last crash:", com.tx24.spicyplayer.playback.CrashLog.lines(context))
    }
    return redactSecrets(report).trimEnd().ifEmpty { null }
}

/** Where the romanization and translation on screen come from, and how they're set. */
internal fun textLines(context: Context, state: PlayerUiState): List<Pair<String, String>> {
    val settings = AppSettings(context.getSharedPreferences("ui", Context.MODE_PRIVATE))
    val lyrics = state.lyrics as? LyricsState.Ready
    val romanization = when {
        !settings.romanize -> "off"
        else -> settings.romanizationMode.label.lowercase() + ", from " + when {
            lyrics?.sourceRomanized == true -> "the lyrics themselves"
            state.humanRomanizedLines > 0 -> "human romanization on ${state.humanRomanizedLines} lines, on-device for the rest"
            state.humanRomanizations -> "on-device (no human romanization matched)"
            else -> "on-device"
        }
    }
    val preferences = state.translationPreferences
    val translation = if (!state.translationEnabled) "off" else buildString {
        append("${preferences.provider.label} to ${preferences.targetLanguage}, ${settings.translationMode.label.lowercase()}")
        if (preferences.humanTranslations) append(", Genius first")
        state.translation?.let { append("; lines: ${it.lineSources()}") }
        if (state.translating) append("; translating now")
    }
    val language = state.lyricsLanguage ?: "not detected yet"
    return listOf(
        "Romanization" to romanization,
        "Translation" to translation,
        "Lyrics language" to language + (state.lyricsLanguageOverride?.let { " (set to $it for this song)" } ?: ""),
    )
}

/** Blanks Spicy Lyrics keys and URL queries, where provider tokens travel. */
internal fun redactSecrets(text: String): String = text
    .replace(Regex("""sl_(pk|sk)_[A-Za-z0-9_-]+"""), "sl_$1_…")
    .replace(Regex("""(https?://[^\s?#]+)\?\S*"""), "$1?…")

/** Copies [extraLogs], for when a bug needs the step-by-step story. */
@Composable
internal fun CopyExtraLogsRow() {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    SettingRow(
        label = "Copy extra logs",
        description = "What translation, syncing and downloads did, step by step, and how the app last crashed. Add them when asked.",
        icon = Icons.Rounded.ContentCopy,
    ) {
        SpicyButton(if (copied) "Copied" else "Copy", onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Spicy Lyrics Mobile logs", extraLogs(context) ?: "No logs yet."))
            copied = clipboard != null
        })
    }
}

/** Copies [debugReport], for the Discord thread or a bug report written by hand. */
@Composable
internal fun CopyDebugInfoRow(state: PlayerUiState) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    SettingRow(
        label = "Copy debug info",
        description = "Your app, phone and player versions, this song and the details below, to paste into a bug report.",
        icon = Icons.Rounded.ContentCopy,
    ) {
        SpicyButton(if (copied) "Copied" else "Copy", onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Spicy Lyrics Mobile debug info", debugReport(context, state)))
            copied = clipboard != null
        })
    }
}
