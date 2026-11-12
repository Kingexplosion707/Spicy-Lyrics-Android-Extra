package com.tx24.spicyplayer.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.lyrics.SavedLines
import com.tx24.spicyplayer.quotes.SavedLinesStore
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Saved lines: the lines kept from Share lyrics → Save, newest first, each with the song it's
 * from. Copy one, share it, or remove it. They never leave the phone unless shared.
 */
@Composable
internal fun SavedLinesContent() {
    val context = LocalContext.current
    // Rises with every change, so the page reads the list again.
    var version by remember { mutableIntStateOf(0) }
    val quotes by produceState<List<SavedLines.Quote>?>(null, version) {
        value = withContext(Dispatchers.IO) { SavedLinesStore.get(context).all }
    }
    val list = quotes ?: return
    if (list.isEmpty()) {
        SettingRow(
            label = "No lines saved yet",
            description = "On the lyrics screen, open Share lyrics (under More), pick a few lines and tap Save.",
            icon = Icons.Rounded.FormatQuote,
        )
        return
    }
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    list.forEach { quote ->
        SettingRow(
            label = quote.lines.joinToString("\n"),
            description = "${quote.title} — ${quote.artist} · ${dates.format(Date(quote.savedAtMs))}",
            stacked = true,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                SpicyButton("Copy", onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Lyrics", quote.asText()))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                })
                SpicyButton("Share", onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, quote.asText())
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share lyrics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
                SpicyButton("Remove", onClick = {
                    SavedLinesStore.remove(context, quote.id)
                    version++
                })
            }
        }
    }
    SettingsSection("All of them") {
        SettingRow(
            label = "Remove every saved line",
            description = "${list.size} saved. This can't be undone.",
            icon = Icons.Rounded.DeleteSweep,
        ) {
            SpicyButton("Remove all", onClick = {
                SavedLinesStore.clear(context)
                version++
            })
        }
    }
}
