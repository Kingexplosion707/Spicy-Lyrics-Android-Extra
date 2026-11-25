package com.tx24.spicyplayer.ui.settings

// The phone-only settings sections of :app's WallpaperSettings.kt and HapticsSettings.kt: the live
// wallpaper, the notification, the floating overlay and vibration aren't on a desktop.

import android.content.Context
import androidx.compose.runtime.Composable

@Composable
internal fun WallpaperSection(settings: AppSettings, showPickerButton: Boolean = true, titled: Boolean = true) {}

@Composable
internal fun LyricsNotificationSection(settings: AppSettings) {}

@Composable
internal fun LyricsOverlaySection(settings: AppSettings) {}

internal fun askOverlayPermission(context: Context) {}

@Composable
internal fun ResyncOnResume() {}

internal fun openWallpaperPicker(context: Context) {}

@Composable
internal fun HapticsSection(settings: AppSettings) {}
