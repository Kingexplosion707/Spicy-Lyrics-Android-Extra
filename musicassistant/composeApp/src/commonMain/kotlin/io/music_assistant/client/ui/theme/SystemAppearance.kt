package io.music_assistant.client.ui.theme

import androidx.compose.runtime.Composable

@Composable
expect fun isSystemInDarkTheme(): Boolean

@Composable
expect fun SystemAppearance(isDarkTheme: Boolean, followsSystem: Boolean)
