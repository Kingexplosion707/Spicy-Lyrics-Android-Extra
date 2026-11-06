package com.tx24.spicyplayer.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * Plays vibrations straight on the phone's motor: the music haptics. Touch feedback on buttons goes through the view instead (Compose's
 * `LocalHapticFeedback`), which follows the system's touch-feedback setting.
 *
 * Music vibrations are marked as media, so Android's "Media vibration" setting mutes them.
 */
class HapticPlayer(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }?.takeIf { it.hasVibrator() }

    /** Music haptics need amplitude control (Android 8) at the least. */
    val canPlayMusic get() = vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Which rendering the pulses use: HapticBeat's engine choice. Auto is the old behaviour
     * (composed primitives where the phone has them, a one-shot or waveform where it hasn't);
     * Apple keeps only the composed primitives (a pulse the phone cannot compose stays silent);
     * Waveform keeps only the one-shot and waveform fallbacks.
     */
    var renderEngine: HapticBeatEngine = HapticBeatEngine.Auto

    fun isSupported(primitive: Primitive): Boolean =
        vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(primitive.id)

    /**
     * One music pulse, [strength] 0..1, with [roomMs] before the next. Each drum has its own shape:
     * - kick: a hard hit that falls away, with a thud's body behind the big ones where there's room;
     * - snare: a crisp click with a short rattle after it; a note: a soft low tick;
     * - beat: a short fall; downbeat: a click;
     * - accent: a click into a thud; drop: a slow swell into a thud.
     *
     * Below about a third of their scale the building blocks can't be felt, so strength is mapped
     * above that: a quiet pulse is light, never missing.
     */
    fun play(pulse: MusicPulse, strength: Float, roomMs: Long = Long.MAX_VALUE) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (strength <= 0f) return
        val s = FELT + (1f - FELT) * strength.coerceIn(0f, 1f)
        // The composed form first, then the one-shot or waveform it falls back to; the engine
        // picks which of the two actually plays.
        val composedEffect = when (pulse) {
            MusicPulse.Kick -> (if (roomMs >= KICK_BODY_ROOM_MS && strength >= KICK_BODY_STRENGTH) {
                composed(Part(s, Primitive.QuickFall), Part(s * 0.7f, Primitive.Thud))
            } else null)
                ?: composed(Part(s, Primitive.QuickFall)) ?: composed(Part(s, Primitive.LowTick))
            MusicPulse.Snare -> (if (roomMs >= SNARE_RATTLE_ROOM_MS) {
                composed(Part(s, Primitive.Click), Part(s * 0.5f, Primitive.Tick, RATTLE_GAP_MS), Part(s * 0.3f, Primitive.Tick, RATTLE_GAP_MS))
            } else null)
                ?: composed(Part(s, Primitive.Click))
            MusicPulse.Note -> composed(Part(s * 0.7f, Primitive.LowTick)) ?: composed(Part(s * 0.7f, Primitive.Tick))
            MusicPulse.Beat -> composed(Part(s * 0.8f, Primitive.QuickFall)) ?: composed(Part(s, Primitive.LowTick))
            MusicPulse.Downbeat -> composed(Part(s, Primitive.Click))
            MusicPulse.Accent -> composed(Part(s, Primitive.Click), Part(s, Primitive.Thud)) ?: composed(Part(s, Primitive.Click))
            // The thud lands DROP_RISE_MS after the start, where the score put the drop: the
            // swell's own length differs by phone, so it waits out the difference first.
            MusicPulse.Drop -> composed(
                Part(s * 0.5f, Primitive.SlowRise, (DROP_RISE_MS - primitiveMs(Primitive.SlowRise)).coerceIn(0L, DROP_RISE_MS).toInt()),
                Part(s, Primitive.Thud),
            )
        }
        val waveformEffect = when (pulse) {
            MusicPulse.Kick -> VibrationEffect.createOneShot(30, amplitude(s * 0.8f))
            MusicPulse.Snare -> VibrationEffect.createOneShot(12, amplitude(s))
            MusicPulse.Note -> VibrationEffect.createOneShot(8, amplitude(s * 0.5f))
            MusicPulse.Beat -> VibrationEffect.createOneShot(16, amplitude(s * 0.6f))
            MusicPulse.Downbeat -> VibrationEffect.createOneShot(20, amplitude(s * 0.8f))
            MusicPulse.Accent -> VibrationEffect.createOneShot(40, amplitude(s))
            MusicPulse.Drop -> VibrationEffect.createWaveform(
                longArrayOf(0, 100, 100, 100, 100, 100, 60),
                intArrayOf(0, amplitude(s * 0.1f), amplitude(s * 0.2f), amplitude(s * 0.3f), amplitude(s * 0.4f), amplitude(s * 0.5f), amplitude(s)),
                -1,
            )
        }
        val effect = when (renderEngine) {
            HapticBeatEngine.Apple -> composedEffect ?: return
            HapticBeatEngine.Waveform -> waveformEffect
            HapticBeatEngine.Auto -> composedEffect ?: waveformEffect
        }
        vibrate(v, effect, media = true)
    }

    /** A steady low rumble for the Continuous style: one short burst, re-issued by the loop. */
    fun playRumble(strength: Float) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (strength <= 0f) return
        val s = FELT + (1f - FELT) * strength.coerceIn(0f, 1f)
        vibrate(v, VibrationEffect.createOneShot(50, amplitude(s * 0.6f)), media = true)
    }

    fun stop() {
        vibrator?.cancel()
    }

    /** How long [primitive] plays on this phone (Android 12+), else a typical length. */
    private fun primitiveMs(primitive: Primitive): Long {
        primitiveLengths[primitive]?.let { return it }
        val measured = if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { vibrator.getPrimitiveDurations(primitive.id).firstOrNull()?.toLong() }.getOrNull()
                ?.takeIf { it > 0L }
        } else null
        return (measured ?: TYPICAL_SLOW_RISE_MS).also { primitiveLengths[primitive] = it }
    }

    private val primitiveLengths = java.util.concurrent.ConcurrentHashMap<Primitive, Long>()

    /** A building block at [scale], [delayMs] after the one before. */
    private class Part(val scale: Float, val primitive: Primitive, val delayMs: Int = 0)

    /** [parts] played one after another, or null when the phone lacks any of them. */
    private fun composed(vararg parts: Part): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (parts.any { !isSupported(it.primitive) }) return null
        return VibrationEffect.startComposition().apply {
            parts.forEach { addPrimitive(it.primitive.id, it.scale.coerceIn(0f, 1f), it.delayMs) }
        }.compose()
    }

    private fun amplitude(strength: Float) = (strength * 255).toInt().coerceIn(1, 255)

    @RequiresApi(Build.VERSION_CODES.O)
    private fun vibrate(v: Vibrator, effect: VibrationEffect, media: Boolean) {
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> v.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(if (media) VibrationAttributes.USAGE_MEDIA else VibrationAttributes.USAGE_TOUCH),
                )
                else -> @Suppress("DEPRECATION") v.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(if (media) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build(),
                )
            }
        }
    }

    // Plain int constants, inlined; each is only played where the phone says it has it.
    /** Android's vibration building blocks (`VibrationEffect.Composition`), . */
    @SuppressLint("InlinedApi")
    enum class Primitive(val id: Int) {
        Tick(VibrationEffect.Composition.PRIMITIVE_TICK),
        LowTick(VibrationEffect.Composition.PRIMITIVE_LOW_TICK),
        Click(VibrationEffect.Composition.PRIMITIVE_CLICK),
        Thud(VibrationEffect.Composition.PRIMITIVE_THUD),
        Spin(VibrationEffect.Composition.PRIMITIVE_SPIN),
        QuickRise(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE),
        SlowRise(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE),
        QuickFall(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL),
    }

    private companion object {
        /** The lowest scale a pulse plays at: the building blocks fade out of feel below it. */
        const val FELT = 0.35f
        /** A kick gets a thud's body only this strong and with this much quiet after it. */
        const val KICK_BODY_STRENGTH = 0.6f
        const val KICK_BODY_ROOM_MS = 350L
        /** A snare rattles only with this much quiet after it, its ticks this far apart. */
        const val SNARE_RATTLE_ROOM_MS = 120L
        const val RATTLE_GAP_MS = 18
        /** A slow rise's length where the phone can't say (Android 11): about what Pixels report. */
        const val TYPICAL_SLOW_RISE_MS = 300L
    }
}
