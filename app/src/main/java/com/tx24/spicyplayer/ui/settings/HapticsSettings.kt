package com.tx24.spicyplayer.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.components.SpicyButton
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.haptics.HapticBeatDetection
import com.tx24.spicyplayer.haptics.HapticBeatEngine
import com.tx24.spicyplayer.haptics.HapticBeatMode
import com.tx24.spicyplayer.haptics.HapticBeatTiming
import com.tx24.spicyplayer.haptics.HapticPlayer
import com.tx24.spicyplayer.haptics.MusicHapticsStyle
import com.tx24.spicyplayer.haptics.MusicPulse
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSection
import com.tx24.spicyplayer.ui.components.SpicyBipolarSlider
import com.tx24.spicyplayer.ui.components.SpicySelect
import com.tx24.spicyplayer.ui.components.ToggleRow

/** Device → Haptics: touch feedback, and vibrating with the music. */
@Composable
internal fun HapticsSection(settings: AppSettings) {
    val context = LocalContext.current
    val player = remember { HapticPlayer(context) }
    // The "Try the music haptics" buttons below feel the chosen engine too.
    LaunchedEffect(settings.hapticBeatEngine) { player.renderEngine = settings.hapticBeatEngine }
    SettingsSection("Haptics") {
        ToggleRow(
            label = "Touch haptics",
            checked = settings.touchHaptics,
            onCheckedChange = { settings.touchHaptics = it },
            description = "Tick on buttons, switches, sliders and gestures. Your phone's touch feedback setting applies too.",
            icon = Icons.Rounded.Vibration,
        )
        ToggleRow(
            label = "Haptics to the music",
            checked = settings.musicHaptics && player.canPlayMusic,
            onCheckedChange = { settings.musicHaptics = it },
            description = if (player.canPlayMusic) {
                "Feel the song while the lyrics are on screen, with a swell into every drop. Works for songs found on Spotify."
            } else {
                "Needs a vibration motor and Android 8 or newer."
            },
            icon = Icons.Rounded.Equalizer,
            enabled = player.canPlayMusic,
        )
        ToggleRow(
            label = "Background haptics",
            checked = settings.musicHaptics && settings.backgroundMusicHaptics && player.canPlayMusic,
            onCheckedChange = { settings.backgroundMusicHaptics = it },
            description = if (player.canPlayMusic) {
                "Keep feeling the song when the app is not in front, on the home screen or in another app. Stops when the music pauses."
            } else {
                "Needs a vibration motor and Android 8 or newer."
            },
            icon = Icons.Rounded.Home,
            enabled = settings.musicHaptics && player.canPlayMusic,
        )
        SettingRow(
            label = "Music haptics follow",
            description = "The drums: kicks deep, snares crisp, and the notes where there are no drums. The beat: a steady pulse, firmer on each bar.",
            icon = Icons.Rounded.GraphicEq,
            enabled = settings.musicHaptics && player.canPlayMusic,
        ) {
            SpicySelect(
                value = settings.musicHapticsStyle.name,
                options = MusicHapticsStyle.entries.map { it.name },
                labels = MusicHapticsStyle.entries.map { it.label },
                onChange = { settings.musicHapticsStyle = MusicHapticsStyle.valueOf(it) },
                enabled = settings.musicHaptics && player.canPlayMusic,
            )
        }
        SettingRow(
            label = "Music haptics strength",
            description = "How strong the vibrations to the music are. Android's media vibration setting can also turn them off.",
            icon = Icons.Rounded.LinearScale,
            enabled = settings.musicHaptics && player.canPlayMusic,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.musicHapticsStrength,
                range = 25..200,
                step = 25,
                onValueChange = {
                    settings.musicHapticsStrength = it
                    // Feel the new strength right away.
                    player.play(MusicPulse.Snare, 0.8f * it / 100f)
                },
                default = 100,
                unit = "%",
                enabled = settings.musicHaptics && player.canPlayMusic,
            )
        }
        SettingRow(
            label = "Try the music haptics",
            description = "Feel each one at the strength set above.",
            icon = Icons.Rounded.TouchApp,
            enabled = settings.musicHaptics && player.canPlayMusic,
            stacked = true,
        ) {
            val enabled = settings.musicHaptics && player.canPlayMusic
            Row(horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
                listOf("Kick" to MusicPulse.Kick, "Snare" to MusicPulse.Snare, "Note" to MusicPulse.Note, "Drop" to MusicPulse.Drop)
                    .forEach { (label, pulse) ->
                        SpicyButton(
                            label,
                            { player.play(pulse, (0.9f * settings.musicHapticsStrength / 100f).coerceAtMost(1f)) },
                            Modifier.weight(1f),
                            enabled,
                            horizontalPadding = 0.dp,
                        )
                    }
            }
        }
        // HapticBeat, inside Spicy: its styles, engine, timing and detection, over the same
        // score. The master switch above stays in charge; these only shape what it plays.
        val beatEnabled = settings.musicHaptics && player.canPlayMusic
        SettingRow(
            label = "HapticBeat style",
            description = "Which pulses fire: the bass, the beat, the drums, the full mix, or one instrument's feel. Full mix keeps the haptics as they were.",
            icon = Icons.Rounded.GraphicEq,
            enabled = beatEnabled,
        ) {
            SpicySelect(
                value = settings.hapticBeatMode.name,
                options = HapticBeatMode.entries.map { it.name },
                labels = HapticBeatMode.entries.map { it.label },
                onChange = { settings.hapticBeatMode = HapticBeatMode.valueOf(it) },
                enabled = beatEnabled,
            )
        }
        SettingRow(
            label = "HapticBeat engine",
            description = "How a pulse is rendered: the best the phone has, Apple's composed primitives, or a plain waveform.",
            icon = Icons.Rounded.Equalizer,
            enabled = beatEnabled,
        ) {
            SpicySelect(
                value = settings.hapticBeatEngine.name,
                options = HapticBeatEngine.entries.map { it.name },
                labels = HapticBeatEngine.entries.map { it.label },
                onChange = { settings.hapticBeatEngine = HapticBeatEngine.valueOf(it) },
                enabled = beatEnabled,
            )
        }
        SettingRow(
            label = "HapticBeat timing",
            description = "Where the haptics come from: the song's analysis and its lyrics fused, the analysis alone, or the lyrics alone.",
            icon = Icons.Rounded.LinearScale,
            enabled = beatEnabled,
        ) {
            SpicySelect(
                value = settings.hapticBeatTiming.name,
                options = HapticBeatTiming.entries.map { it.name },
                labels = HapticBeatTiming.entries.map { it.label },
                onChange = { settings.hapticBeatTiming = HapticBeatTiming.valueOf(it) },
                enabled = beatEnabled,
            )
        }
        SettingRow(
            label = "HapticBeat detection",
            description = "How the pulses are gated and fused: strict onsets, the lyrics folded in, the classic feel, or everything at once.",
            icon = Icons.Rounded.GraphicEq,
            enabled = beatEnabled,
        ) {
            SpicySelect(
                value = settings.hapticBeatDetection.name,
                options = HapticBeatDetection.entries.map { it.name },
                labels = HapticBeatDetection.entries.map { it.label },
                onChange = { settings.hapticBeatDetection = HapticBeatDetection.valueOf(it) },
                enabled = beatEnabled,
            )
        }
        SettingRow(
            label = "HapticBeat sensitivity",
            description = "Higher lets quieter pulses through; lower keeps only the strong ones.",
            icon = Icons.Rounded.LinearScale,
            enabled = beatEnabled,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.hapticBeatSensitivity,
                range = 0..100,
                step = 5,
                onValueChange = { settings.hapticBeatSensitivity = it },
                default = 50,
                enabled = beatEnabled,
            )
        }
        SettingRow(
            label = "HapticBeat sync offset",
            description = "Fires the haptics this much earlier, against the delay in the position reports and the motor spinning up.",
            icon = Icons.Rounded.LinearScale,
            enabled = beatEnabled,
            stacked = true,
        ) {
            SpicyBipolarSlider(
                value = settings.hapticBeatRemoteOffset,
                range = -600..600,
                step = 50,
                onValueChange = { settings.hapticBeatRemoteOffset = it },
                default = 150,
                unit = "ms",
                enabled = beatEnabled,
            )
        }
        SettingRow(
            label = "Sync offset preset",
            description = "A starting point for the offset above, by how the music reaches you.",
            icon = Icons.Rounded.Home,
            enabled = beatEnabled,
        ) {
            val preset = when (settings.hapticBeatRemoteOffset) {
                150 -> "Tv"
                300 -> "Bluetooth"
                400 -> "Wifi"
                100 -> "Phone"
                else -> "Custom"
            }
            SpicySelect(
                value = preset,
                options = listOf("Custom", "Tv", "Bluetooth", "Wifi", "Phone"),
                labels = listOf("Custom", "TV or speaker", "Bluetooth", "Wi-Fi or Sonos", "Same-room phone speaker"),
                onChange = {
                    settings.hapticBeatRemoteOffset = when (it) {
                        "Tv" -> 150
                        "Bluetooth" -> 300
                        "Wifi" -> 400
                        "Phone" -> 100
                        else -> settings.hapticBeatRemoteOffset
                    }
                },
                enabled = beatEnabled,
            )
        }
        ToggleRow(
            label = "Vocals only",
            checked = settings.hapticBeatVocalsOnly,
            onCheckedChange = { settings.hapticBeatVocalsOnly = it },
            description = "In the Apple-style, only the lyric and word pulses fire — no drums, no beat.",
            icon = Icons.Rounded.Vibration,
            enabled = beatEnabled && settings.hapticBeatMode == HapticBeatMode.Apple,
        )
    }
}
