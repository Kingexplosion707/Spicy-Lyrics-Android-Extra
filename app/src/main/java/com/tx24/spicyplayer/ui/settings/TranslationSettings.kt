package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.ui.components.SpicyGlyphs
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import com.tx24.spicyplayer.playback.AiTranslationSettings
import com.tx24.spicyplayer.translation.GeminiModels
import com.tx24.spicyplayer.translation.OnDeviceModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.DeepLKey
import com.tx24.spicyplayer.translation.TranslationLanguages
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationProvider
import com.tx24.spicyplayer.ui.components.*
import com.tx24.spicyplayer.ui.theme.SpicySpacing

@Composable
internal fun RomanizationTranslationContent(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, settings: AppSettings) {
    val preferences = state.translationPreferences
    // Only languages the provider can translate into; any language can be left untranslated.
    val targets = TranslationLanguages.targets(preferences.provider, preferences.targetLanguage)
    val languages = TranslationLanguages.all(preferences.excludedLanguages)
    var key by remember { mutableStateOf("") }
    // The page header names both, so each group gets its own title.
    SettingsSection("Romanization", divider = false) {
        SettingRow("Romanization", description = "Replace lyrics or show romanization below.", icon = Icons.Rounded.ViewAgenda,
            searchDescription = "Replace the synced lyric words or show romanization below each line.") {
            SpicySelect(settings.romanizationMode.name, RomanizationMode.entries.map { it.name },
                { settings.romanizationMode = RomanizationMode.valueOf(it) }, labels = RomanizationMode.entries.map { it.label })
        }
        ToggleRow("Human romanizations", state.humanRomanizations, viewModel::setHumanRomanizations,
            description = "Use matching romanizations from Genius, or NetEase while it's on.", icon = Icons.Rounded.People,
            searchDescription = "Use a romanization written by people on Genius, or on NetEase while it's on as a source, where it lines up with the lyrics, for readings no romanizer can guess. Asks Genius.")
    }
    SettingsSection("Translation") {
        SettingRow("Target language", description = "Translate lyrics into this language.", icon = Icons.Rounded.Language) {
            SpicySelect(preferences.targetLanguage, targets.map { it.first }, viewModel::setTranslationTarget, labels = targets.map { it.second })
        }
        ToggleRow("Translate automatically", preferences.automatic, viewModel::setTranslateAutomatically,
            description = "Translate when lyrics arrive.", icon = Icons.Rounded.AutoAwesome,
            searchDescription = "Translate songs in other languages as their lyrics arrive.")
        SettingRow("Don't translate", description = "Keep these languages untranslated.", icon = Icons.Rounded.Block,
            searchDescription = "Leave songs in languages you already read in their original form.") {
            SpicyMultiSelect(preferences.excludedLanguages, languages.map { it.first }, viewModel::setTranslationExcluded,
                labels = languages.map { it.second })
        }
    }
    MoreOptions("The romanize and translate buttons, how translations show, the provider, Genius translations") {
        ToggleRow("Romanize button", settings.romanizeButton, { settings.romanizeButton = it },
            description = "Show the romanize button.", icon = SpicyGlyphs.EnableRomanization,
            searchDescription = "Show the button that romanizes lyrics, on songs that have them.")
        ToggleRow("Translate button", settings.translateButton, { settings.translateButton = it },
            description = "Show the translate button on songs in other languages.", icon = Icons.Rounded.Translate,
            searchDescription = "Show the button that translates lyrics, on songs that aren't in your language or one you left untranslated.")
        SettingRow("Display mode", description = "Show translations below or replace lyrics.", icon = Icons.Rounded.ViewAgenda,
            searchDescription = "Show translations below the lyrics or in their place.") {
            SpicySelect(settings.translationMode.name, TranslationMode.entries.map { it.name },
                { settings.translationMode = TranslationMode.valueOf(it) }, labels = TranslationMode.entries.map { it.label })
        }
        SettingRow("Provider", description = "Choose who translates the lyric lines.", icon = Icons.Rounded.Public,
            searchDescription = "Choose who translates the original lyric lines.") {
            // Gemini Nano is greyed out on phones that can't run it.
            LaunchedEffect(Unit) { viewModel.checkOnDeviceModel() }
            SpicySelect(preferences.provider.name, TranslationProvider.entries.map { it.name },
                { viewModel.setTranslationProvider(TranslationProvider.valueOf(it)) }, labels = TranslationProvider.entries.map { it.label },
                unavailable = if (state.onDeviceModel == OnDeviceModel.Status.Unavailable) setOf(TranslationProvider.GeminiNano.name) else emptySet())
        }
        if (preferences.provider == TranslationProvider.DeepL) {
            SettingRow("DeepL API key", description = if (state.deepLKeyPresent) "Key saved. Paste a new one to replace it." else "Free keys end in :fx.", icon = Icons.Rounded.Key, stacked = true,
                searchDescription = if (state.deepLKeyPresent) "A key is saved on this device. Paste a new key to replace it." else "Paste your DeepL API key. Free keys end in :fx.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                    SpicyTextField(key, { key = it }, placeholder = "DeepL API key", password = true, modifier = Modifier.weight(1f))
                    SpicyButton("Use", onClick = {
                        viewModel.useDeepLKey(key)
                        if (DeepLKey.valid(key)) key = ""
                    }, enabled = key.isNotBlank())
                }
            }
            if (state.deepLKeyPresent) {
                SettingRow("Remove DeepL key", description = "Forget the key on this device.", icon = Icons.Rounded.KeyOff) {
                    SpicyButton("Remove", onClick = { viewModel.useDeepLKey("") })
                }
            }
        }
        AiProviderRows(state, viewModel, preferences.provider)
        ToggleRow("Use human translations from Genius", preferences.humanTranslations, viewModel::setHumanTranslations,
            description = "Prefer matching translations from Genius.", icon = Icons.Rounded.People,
            searchDescription = "Use human translations where Genius's original lyrics confidently match, then fill gaps with the selected provider.")
    }
}

/** The AI providers' own settings: where the service is, which model, the key, or Gemini Nano's state. */
@Composable
private fun AiProviderRows(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, provider: TranslationProvider) {
    val ai = state.aiTranslation
    when (provider) {
        TranslationProvider.OpenAi -> {
            TextSettingRow("AI service address", "The service's OpenAI-compatible API (https), ending in /v1.", Icons.Rounded.Link,
                ai.baseUrl, AiTranslationSettings.OPENAI_BASE_URL) { viewModel.setAiTranslation(ai.copy(baseUrl = it)) }
            TextSettingRow("AI model", if (ai.model.isBlank()) "Needed: the model's name at that service." else "Using ${ai.model}.",
                Icons.Rounded.AutoAwesome, ai.model, "Model name") { viewModel.setAiTranslation(ai.copy(model = it)) }
            KeyRows(state, viewModel, provider, "Leave it empty for a service that needs none.")
        }
        TranslationProvider.Gemini -> {
            TextSettingRow("Gemini model", "Using ${ai.geminiModel}. Change it when Google renames its models.",
                Icons.Rounded.AutoAwesome, ai.geminiModel, GeminiModels.DEFAULT) { viewModel.setAiTranslation(ai.copy(geminiModel = it.ifBlank { GeminiModels.DEFAULT })) }
            KeyRows(state, viewModel, provider, "Get one free at aistudio.google.com.")
        }
        TranslationProvider.GeminiNano -> {
            LaunchedEffect(Unit) { viewModel.checkOnDeviceModel() }
            val status = state.onDeviceModel
            val downloaded = state.onDeviceDownloadedBytes
            SettingRow("Gemini Nano", description = when {
                downloaded != null -> "Downloading… ${downloaded / 1_000_000} MB"
                status == null -> "Checking this phone…"
                else -> status.label
            }, icon = Icons.Rounded.PhoneAndroid,
                searchDescription = "Translate on the phone with Gemini Nano, where the phone has it. Nothing is sent anywhere.") {
                if (status == OnDeviceModel.Status.Downloadable && downloaded == null) {
                    SpicyButton("Download", onClick = viewModel::downloadOnDeviceModel)
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun KeyRows(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, provider: TranslationProvider, hint: String) {
    val saved = provider in state.translationKeys
    TextSettingRow("${provider.label} API key", if (saved) "Key saved. Paste a new one to replace it." else hint,
        Icons.Rounded.Key, "", "API key", password = true, clearAfterSave = true) { viewModel.useTranslationKey(provider, it) }
    if (saved) {
        SettingRow("Remove the key", description = "Forget the key on this device.", icon = Icons.Rounded.KeyOff) {
            SpicyButton("Remove", onClick = { viewModel.useTranslationKey(provider, "") })
        }
    }
}

/** A text field with a Use button, which hands what was typed to [onSave]. */
@Composable
private fun TextSettingRow(
    label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: String, placeholder: String, password: Boolean = false, clearAfterSave: Boolean = false,
    onSave: (String) -> Unit,
) {
    var text by remember(current) { mutableStateOf(current) }
    SettingRow(label, description = description, icon = icon, stacked = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
            SpicyTextField(text, { text = it }, placeholder = placeholder, password = password, modifier = Modifier.weight(1f))
            SpicyButton("Use", onClick = {
                onSave(text.trim())
                if (clearAfterSave) text = ""
            }, enabled = text.trim() != current.trim() && (text.isNotBlank() || !clearAfterSave))
        }
    }
}

@Composable
internal fun LyricsLanguageRow(state: PlayerUiState, viewModel: ExternalPlaybackViewModel) {
    val detected = state.lyricsLanguage?.let { "Detected: ${TranslationLanguages.name(it)}" } ?: "Not detected yet"
    val languages = TranslationLanguages.all(listOfNotNull(state.lyricsLanguageOverride))
    SettingRow("Lyrics language", description = detected, icon = Icons.Rounded.Translate) {
        SpicySelect(state.lyricsLanguageOverride ?: "auto", listOf("auto") + languages.map { it.first },
            { viewModel.setLyricsLanguage(it.takeUnless { code -> code == "auto" }) },
            labels = listOf("Auto") + languages.map { it.second }, enabled = state.localLyricsKey != null)
    }
}
