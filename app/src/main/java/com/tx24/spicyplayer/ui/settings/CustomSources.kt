package com.tx24.spicyplayer.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.network.data.providers.CustomLyricsSource
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel.SourceTest
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.DescriptionStyle
import com.tx24.spicyplayer.ui.components.Searchable
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.components.SpicyButtonStyle
import com.tx24.spicyplayer.ui.components.SpicyIconButton
import com.tx24.spicyplayer.ui.components.SpicyModalButton
import com.tx24.spicyplayer.ui.components.SpicyTextField
import com.tx24.spicyplayer.ui.components.outlinedCard
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The user's own sources: each one web address with the song filled in. They take their place
 * in the order above like any source; here they're added, edited, tested and shared.
 */
@Composable
internal fun CustomSourcesSection(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    val context = LocalContext.current
    // The source being edited: a saved one, or a new one not in the list yet. Its form is kept on
    // the view model, so what's typed survives the settings leaving the screen (the floating window).
    var form by remember { mutableStateOf(viewModel.customSourceForm) }
    fun edit(source: CustomLyricsSource?) {
        form = source?.let(::CustomSourceForm)
        viewModel.customSourceForm = form
    }
    val editing = form?.source
    SettingsSection("Custom sources") {
        Searchable("Custom sources", "Custom", "API", "URL") {
            Text(
                "Your own lyrics sources: a web address with the song filled in, answering with TTML, LRC or plain text, " +
                    "as is or in JSON. They join the order above.",
                style = DescriptionStyle,
                modifier = Modifier.padding(horizontal = 2.dp).padding(bottom = SpicySpacing.S1),
            )
        }
        state.customSources.forEach { custom ->
            if (editing?.id == custom.id) {
                CustomSourceEditor(form!!, saved = true, viewModel) { edit(null) }
            } else {
                SettingRow(label = custom.name, description = custom.url.toHttpUrlOrNull()?.host ?: custom.url, icon = Icons.Rounded.Link) {
                    SpicyButton("Edit", onClick = { edit(custom) }, enabled = editing == null)
                }
            }
        }
        val draft = editing?.takeIf { e -> state.customSources.none { it.id == e.id } }
        if (draft != null) {
            CustomSourceEditor(form!!, saved = false, viewModel) { edit(null) }
        } else if (editing == null) {
            SettingRow(
                label = "Add a source",
                description = "Start a new one, or paste one someone shared.",
                icon = Icons.Rounded.Add,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    SpicyButton("Paste", onClick = {
                        val pasted = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        val shared = CustomLyricsSource.fromShare(pasted)
                        if (shared == null) viewModel.showMessage("The clipboard has no shared source in it.") else edit(shared)
                    })
                    SpicyButton("Add", onClick = { edit(CustomLyricsSource(CustomLyricsSource.newId(), "", "")) })
                }
            }
        }
    }
}

/** A source being edited and what's typed into its form so far. */
internal class CustomSourceForm(val source: CustomLyricsSource) {
    var name by mutableStateOf(source.name)
    var url by mutableStateOf(source.url)
    var path by mutableStateOf(source.path)
    var headers by mutableStateOf(CustomLyricsSource.headersText(source.headers))
    var result by mutableStateOf<SourceTest?>(null)
}

/**
 * A source's form: a header with copy and delete, the fields in groups, the test's result in
 * a strip, then Cancel on its own and Test and Save together.
 */
@Composable
private fun CustomSourceEditor(
    form: CustomSourceForm,
    saved: Boolean,
    viewModel: ExternalPlaybackViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = form.source
    var name by form::name
    var url by form::url
    var path by form::path
    var headers by form::headers
    var result by form::result
    var testing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    fun draft() = source.copy(
        name = name.trim().take(40).ifBlank { "Custom source" },
        url = url.trim(),
        path = path.trim(),
        headers = CustomLyricsSource.parseHeaders(headers),
    )
    /** The draft, or null with the address's problem shown. */
    fun valid(): CustomLyricsSource? {
        val candidate = draft()
        val problem = CustomLyricsSource.problem(candidate.url) ?: return candidate
        result = SourceTest(false, problem)
        return null
    }

    Column(
        // Set off from the text and rows above it, which the settings rows' own padding does for them.
        Modifier.padding(top = SpicySpacing.S2, bottom = SpicySpacing.S1).fillMaxWidth().outlinedCard(tinted = true).padding(SpicySpacing.S3),
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            Text(
                if (saved) "Edit source" else "New source",
                style = SpicyType.Body.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            if (saved) {
                SpicyIconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Spicy Lyrics Mobile source", CustomLyricsSource.share(draft())))
                    viewModel.showMessage("Copied, without header values.")
                }) {
                    Icon(Icons.Rounded.ContentCopy, "Copy to share", tint = SpicyColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                if (confirmDelete) {
                    SpicyButton("Confirm", brand = SpicyColors.StatusDanger.copy(alpha = 1f), horizontalPadding = 10.dp, onClick = {
                        viewModel.removeCustomSource(source.id)
                        onClose()
                    })
                } else {
                    SpicyIconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, "Delete source", tint = SpicyColors.TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Field("Name") {
            SpicyTextField(name, { name = it }, placeholder = "My lyrics API", modifier = Modifier.fillMaxWidth())
        }
        Field("Address", "Fill in ${CustomLyricsSource.PLACEHOLDERS.joinToString { "{$it}" }}. Duration is in seconds.") {
            SpicyTextField(url, { url = it; result = null }, placeholder = "https://…?title={title}&artist={artist}", modifier = Modifier.fillMaxWidth(), multiline = true)
        }
        Field("Where the lyrics are", "For JSON answers. Empty tries the usual fields.") {
            SpicyTextField(path, { path = it; result = null }, placeholder = "e.g. data.lyrics[0].ttml", modifier = Modifier.fillMaxWidth())
        }
        Field("Headers", "One per line. Values stay on this phone: backups and shared copies leave them out.") {
            SpicyTextField(headers, { headers = it }, placeholder = "Authorization: Bearer …", modifier = Modifier.fillMaxWidth(), multiline = true)
        }
        result?.let { TestStrip(it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyModalButton("Cancel", onClick = onClose, style = SpicyButtonStyle.Quiet)
            Spacer(Modifier.weight(1f))
            SpicyModalButton(if (testing) "Testing…" else "Test", onClick = {
                if (testing) return@SpicyModalButton
                val candidate = valid() ?: return@SpicyModalButton
                testing = true
                scope.launch {
                    try {
                        result = viewModel.testCustomSource(candidate)
                    } finally {
                        testing = false
                    }
                }
            })
            SpicyModalButton("Save", style = SpicyButtonStyle.Primary, onClick = {
                val candidate = valid() ?: return@SpicyModalButton
                viewModel.saveCustomSource(candidate)
                onClose()
            })
        }
    }
}

/** A label, its field, and an optional note under it, kept close together. */
@Composable
private fun Field(label: String, note: String? = null, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = SpicyType.Caption.copy(color = SpicyColors.TextSecondary, fontWeight = FontWeight.Medium))
        field()
        note?.let { Text(it, style = DescriptionStyle) }
    }
}

/** What the test got: a green dot for lyrics, a red one for anything else. */
@Composable
private fun TestStrip(test: SourceTest) {
    val shape = RoundedCornerShape(SpicyRadii.Sm)
    val tone = if (test.found) SpicyColors.StatusSuccess else SpicyColors.StatusDanger
    Row(
        Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.10f), shape)
            .border(1.dp, tone.copy(alpha = 0.30f), shape)
            .padding(horizontal = SpicySpacing.S3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(tone.copy(alpha = 1f), CircleShape))
        Text(test.message, style = SpicyType.Caption.copy(color = SpicyColors.TextPrimary))
    }
}
