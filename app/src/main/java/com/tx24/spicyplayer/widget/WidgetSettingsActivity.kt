package com.tx24.spicyplayer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * One widget's settings, opened when it's placed and, on Android 12 and later, from its
 * long-press menu. Every change is saved and sent to the widget at once, so backing out keeps
 * it; a live preview shows it with the song playing (or a sample) at the widget's own height.
 */
class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Saved as it changes, so the widget is placed however this screen is left.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val store = WidgetSettingsStore(this)
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id)
        val heightDp = LyricsWidget.sizeDp(this, options).second ?: PREVIEW_HEIGHT_DP
        enableEdgeToEdge()
        setContent {
            var settings by remember { mutableStateOf(store.read(id)) }
            WidgetSettingsScreen(
                settings = settings,
                previewHeightDp = heightDp,
                onChange = { changed ->
                    settings = changed
                    store.save(id, changed)
                    NowPlayingHolder.start(this)
                },
                onDone = ::finish,
            )
        }
    }

    private companion object {
        const val PREVIEW_HEIGHT_DP = 200
    }
}

@Composable
private fun WidgetSettingsScreen(
    settings: WidgetSettings,
    previewHeightDp: Int,
    onChange: (WidgetSettings) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpicySpacing.S4, vertical = SpicySpacing.S4),
    ) {
        Text("Lyrics widget", style = SpicyType.Title)
        Box(
            Modifier.fillMaxWidth().padding(vertical = SpicySpacing.S4),
            contentAlignment = Alignment.Center,
        ) {
            WidgetPreview(settings, previewHeightDp)
        }

        SettingsSection("Layout", divider = false) {
            SettingRow("Song info", description = "Cover, title and artist.") {
                EnumSelect(settings.songInfo, WidgetSettings.SongInfo.entries, { it.label }) { onChange(settings.copy(songInfo = it)) }
            }
            SettingRow("Lines around the current one", description = "Shown when space allows; resize the widget to see more.") {
                SpicySelect(
                    value = settings.around.toString(),
                    options = (0..WidgetLines.MAX_AROUND).map(Int::toString),
                    labels = listOf("None", "One each side", "Two each side"),
                    onChange = { onChange(settings.copy(around = it.toInt())) },
                )
            }
            SettingRow("Buttons") {
                EnumSelect(settings.controls, WidgetSettings.Controls.entries, { it.label }) { onChange(settings.copy(controls = it)) }
            }
            ToggleRow("Progress bar", settings.progress, { onChange(settings.copy(progress = it)) }, description = "With the time played and the song's length.")
            ToggleRow("Lyrics credit", settings.credit, { onChange(settings.copy(credit = it)) }, description = "Which source the lyrics are from.")
        }

        SettingsSection("Lyrics") {
            ToggleRow("Romanization", settings.romanization, { onChange(settings.copy(romanization = it)) }, description = "Under lines in Japanese, Korean, Chinese and other scripts.")
            ToggleRow("Translation", settings.translation, { onChange(settings.copy(translation = it)) }, description = "When the lyrics come with their own.")
            SettingRow("Text size") {
                EnumSelect(settings.textSize, WidgetSettings.TextSize.entries, { it.label }) { onChange(settings.copy(textSize = it)) }
            }
            ToggleRow("Centre the lines", settings.centered, { onChange(settings.copy(centered = it)) })
        }

        SettingsSection("Background") {
            SettingRow("Background") {
                EnumSelect(settings.background, WidgetSettings.Background.entries, { it.label }) { onChange(settings.copy(background = it)) }
            }
            val dims = settings.background == WidgetSettings.Background.BLURRED_COVER || settings.background == WidgetSettings.Background.COVER_COLOR
            SettingRow("Dim", description = "How dark the cover gets behind the lyrics.", enabled = dims, stacked = true) {
                SpicyBipolarSlider(
                    value = settings.dim,
                    range = 0..90,
                    step = 5,
                    onValueChange = { onChange(settings.copy(dim = it)) },
                    default = WidgetSettings().dim,
                    unit = "%",
                    enabled = dims,
                )
            }
        }

        SettingsSection("Background updates") {
            SettingRow("Battery settings", description = "If updates stop, allow unrestricted battery use for this app. This may use more battery.", stacked = true) {
                SpicyButton("Open battery settings", {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                })
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = SpicySpacing.S5), horizontalArrangement = Arrangement.End) {
            SpicyButton("Done", onDone)
        }
    }
}

@Composable
private fun <E : Enum<E>> EnumSelect(value: E, entries: List<E>, label: (E) -> String, onChange: (E) -> Unit) {
    SpicySelect(
        value = value.name,
        options = entries.map { it.name },
        labels = entries.map(label),
        onChange = { name -> entries.firstOrNull { it.name == name }?.let(onChange) },
    )
}

/** The widget itself, drawn from the same views the launcher gets, [heightDp] tall. */
@Composable
private fun WidgetPreview(settings: WidgetSettings, heightDp: Int) {
    val content = NowPlayingHolder.last ?: SAMPLE
    AndroidView(
        factory = { FrameLayout(it) },
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(heightDp.dp),
        update = { frame ->
            val views = LyricsWidget.views(frame.context, content, settings, full = true, heightDp = heightDp)
            frame.removeAllViews()
            frame.addView(views.apply(frame.context, frame))
        },
    )
}

private val SAMPLE = LyricsWidget.Content(
    title = "Song title",
    artist = "Artist",
    lines = WidgetLines(
        before = listOf(
            WidgetLines.Lead(0, 0, "空を見上げて", "sora wo miagete"),
            WidgetLines.Lead(0, 0, "星が見えるよ", "hoshi ga mieru yo"),
        ),
        current = WidgetLines.Lead(0, 0, "ここにいるよ", "koko ni iru yo", "I'm right here"),
        after = listOf(
            WidgetLines.Lead(0, 0, "また明日ね", "mata ashita ne"),
            WidgetLines.Lead(0, 0, "おやすみ", "oyasumi"),
        ),
        changesAtMs = null,
    ),
    playing = true,
    positionMs = 83_000L,
    durationMs = 181_000L,
    credit = "Provided by Spicy Lyrics",
    cover = null,
    backdrop = null,
    colors = null,
)
