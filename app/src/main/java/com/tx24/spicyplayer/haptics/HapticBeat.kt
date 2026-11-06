package com.tx24.spicyplayer.haptics

/**
 * HapticBeat's haptics, inside Spicy: the styles, engine, timing and detection from
 * HapticBeat (com.behnoud.hapticbeat), driving Spicy's own score — Spotify audio analysis plus
 * synced lyrics — instead of HapticBeat's live microphone / playback-capture audio.
 *
 * HapticBeat's live-audio engines (its Precision / Combined / Classic / All-in-one loops over
 * mic or MediaProjection capture PCM) are NOT ported: they need a RECORD_AUDIO permission and a
 * capture-consent flow Spicy does not have, and they would duplicate the score path, since
 * Spicy already follows the song's analysis. The [HapticBeatDetection] choice below gates and
 * fuses the same analysis + lyric streams instead, so it changes the feel without new
 * permissions. The one thing it cannot do is feel audio Spicy has no analysis or lyrics for.
 */

/** Which pulses fire, from HapticBeat's style spinner. */
enum class HapticBeatMode(val label: String) {
    Bass("Bass pulse"),
    Beat("Beat only"),
    Continuous("Continuous rumble"),
    BeatBass("Beat + Bass"),
    Drums("Drums"),
    Full("Full mix"),
    Apple("Apple-style"),
    Movie("Movie / YouTube"),
    Sources("Sources"),
}

/** How a pulse is rendered on the motor, from HapticBeat's engine spinner. */
enum class HapticBeatEngine(val label: String) {
    Auto("Auto (best available)"),
    /** Android 16's envelopes: each pulse shaped in strength and sharpness over time (Pixel 9 and later). */
    Envelope("Sharpness envelopes (Android 16)"),
    Apple("Apple primitives"),
    Waveform("Waveform"),
}

/** Which streams the haptics come from, from HapticBeat's timing spinner. */
enum class HapticBeatTiming(val label: String) {
    Auto("Auto — best of all, fused"),
    Spotify("Spotify analysis only"),
    Lyrics("Synced lyrics only"),
}

/**
 * How the score's pulses are gated and fused, from HapticBeat's live-detection spinner.
 * The live-audio half is not ported (see the file note); these are the same choices applied
 * to the analysis + lyric streams Spicy has.
 */
enum class HapticBeatDetection(val label: String) {
    Auto("Auto (lyrics + precision when available)"),
    Precision("Precision (flux + beat tracking)"),
    Combined("Combined (precision + lyric timing)"),
    Classic("Classic (1.5.4 engine)"),
    AllInOne("All in one (everything, fused)"),
}

/** The HapticBeat choices that shape one song's score. */
data class HapticBeatOptions(
    val mode: HapticBeatMode = HapticBeatMode.Full,
    val timing: HapticBeatTiming = HapticBeatTiming.Auto,
    val detection: HapticBeatDetection = HapticBeatDetection.Auto,
    /** 0..100: higher lets quieter pulses through. */
    val sensitivity: Int = 50,
    /** Apple-style only: only lyric and word pulses fire, no drums or beat. */
    val vocalsOnly: Boolean = false,
)

/**
 * Whether a score pulse of [pulse] fires in [mode]: HapticBeat's `kindOk` table from its
 * Connect loop, on Spicy's pulse kinds.
 */
internal fun HapticBeatMode.allowsScore(pulse: MusicPulse): Boolean = when (this) {
    HapticBeatMode.Bass -> pulse == MusicPulse.Kick || pulse == MusicPulse.Downbeat ||
        pulse == MusicPulse.Accent || pulse == MusicPulse.Drop
    HapticBeatMode.Beat -> pulse == MusicPulse.Beat || pulse == MusicPulse.Downbeat
    HapticBeatMode.BeatBass -> pulse == MusicPulse.Beat || pulse == MusicPulse.Downbeat ||
        pulse == MusicPulse.Kick
    HapticBeatMode.Drums -> pulse == MusicPulse.Kick || pulse == MusicPulse.Snare
    HapticBeatMode.Movie -> pulse == MusicPulse.Downbeat || pulse == MusicPulse.Accent ||
        pulse == MusicPulse.Drop
    HapticBeatMode.Continuous, HapticBeatMode.Full, HapticBeatMode.Apple, HapticBeatMode.Sources -> true
}

/** Whether a lyric-line pulse fires in [mode]: HapticBeat's `shouldFire("lyric")`. */
internal fun HapticBeatMode.allowsLine(): Boolean = when (this) {
    HapticBeatMode.Beat, HapticBeatMode.Drums -> false
    else -> true
}

/** Whether a lyric-word tick fires in [mode]: HapticBeat's `shouldFire("word")`. */
internal fun HapticBeatMode.allowsWord(): Boolean = when (this) {
    HapticBeatMode.Bass, HapticBeatMode.Beat, HapticBeatMode.Movie -> false
    else -> true
}
