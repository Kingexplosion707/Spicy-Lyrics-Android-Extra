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
     * Which rendering the pulses use: HapticBeat's engine choice. Auto takes the best the phone
     * has: Android 16's envelopes (strength and sharpness shaped over time, as on Pixel 9 and
     * later), else composed primitives, else a one-shot or waveform; Envelope keeps only the
     * envelopes (falling back to Auto's next choice on a phone without them);
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
    fun play(pulse: MusicPulse, strength: Float, roomMs: Long = Long.MAX_VALUE, sharpness: Float = Float.NaN) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (strength <= 0f) return
        val s = FELT + (1f - FELT) * strength.coerceIn(0f, 1f)
        if ((renderEngine == HapticBeatEngine.Auto || renderEngine == HapticBeatEngine.Envelope) && envelopesSupported) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                envelope(pulse, s, roomMs, sharpness)?.let {
                    vibrate(v, it, media = true)
                    return
                }
            }
        }
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
            HapticBeatEngine.Auto, HapticBeatEngine.Envelope -> composedEffect ?: waveformEffect
        }
        vibrate(v, effect, media = true)
    }

    /** Whether the motor takes Android 16's envelopes (strength and sharpness over time). */
    private val envelopesSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            runCatching { vibrator?.areEnvelopeEffectsSupported() == true }.getOrDefault(false)
    }

    /** The shortest step between an envelope's points this motor takes. */
    private val envelopeMinStepMs: Long by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runCatching { vibrator?.envelopeEffectInfo?.minControlPointDurationMillis }.getOrNull() ?: DEFAULT_MIN_STEP_MS
        } else DEFAULT_MIN_STEP_MS
    }

    /**
     * A pulse as an envelope: a fast attack to its strength, then a fall shaped for its sound,
     * at a sharpness from the music ([sharpness], the sound's brightness) leaning toward the
     * pulse's own (a kick deep and round, a snare crisp, a word in between). The fall is cut
     * short to fit [roomMs], so a hit never smears into the next.
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun envelope(pulse: MusicPulse, s: Float, roomMs: Long, sharpness: Float): VibrationEffect? = runCatching {
        val own = when (pulse) {
            MusicPulse.Kick -> 0.12f
            MusicPulse.Snare -> 0.8f
            MusicPulse.Note -> 0.55f
            MusicPulse.Beat -> 0.35f
            MusicPulse.Downbeat -> 0.25f
            MusicPulse.Accent -> 0.45f
            MusicPulse.Drop -> 0.3f
        }
        val sharp = if (sharpness.isNaN()) own else (own * 0.55f + sharpness * 0.45f)
        // (intensity, sharpness, ms to reach it) after the start at nothing.
        val points: List<Triple<Float, Float, Long>> = when (pulse) {
            MusicPulse.Kick -> listOf(Triple(s, sharp, 5L), Triple(s * 0.5f, sharp * 0.8f, 22L), Triple(0f, sharp * 0.6f, 60L))
            MusicPulse.Snare -> listOf(Triple(s, sharp, 3L), Triple(s * 0.3f, sharp, 14L), Triple(0f, sharp, 26L))
            MusicPulse.Note -> listOf(Triple(s * 0.65f, sharp, 4L), Triple(0f, sharp, 22L))
            MusicPulse.Beat -> listOf(Triple(s * 0.8f, sharp, 4L), Triple(0f, sharp, 36L))
            MusicPulse.Downbeat -> listOf(Triple(s, sharp, 4L), Triple(s * 0.4f, sharp, 22L), Triple(0f, sharp * 0.8f, 44L))
            MusicPulse.Accent -> listOf(Triple(s, sharp, 4L), Triple(s * 0.6f, sharp, 36L), Triple(0f, sharp * 0.8f, 80L))
            // The swell builds for DROP_RISE_MS and lands where the score put the drop.
            MusicPulse.Drop -> listOf(
                Triple(s * 0.15f, 0.1f, 60L), Triple(s * 0.6f, 0.5f, DROP_RISE_MS - 60L),
                Triple(s, sharp, 5L), Triple(0f, sharp * 0.7f, 150L),
            )
        }
        // Fits before the next pulse: the fall is shortened, never the attack.
        val budget = (roomMs - ROOM_MARGIN_MS).coerceAtLeast(points.first().third + envelopeMinStepMs)
        val builder = VibrationEffect.BasicEnvelopeBuilder().setInitialSharpness(sharp.coerceIn(0f, 1f))
        var used = 0L
        for ((i, point) in points.withIndex()) {
            val last = i == points.lastIndex
            var step = point.third.coerceAtLeast(envelopeMinStepMs)
            if (pulse != MusicPulse.Drop && used + step > budget) step = (budget - used).coerceAtLeast(envelopeMinStepMs)
            builder.addControlPoint(
                if (last || used + step >= budget) 0f else point.first.coerceIn(0f, 1f),
                point.second.coerceIn(0f, 1f),
                step,
            )
            used += step
            if (pulse != MusicPulse.Drop && used >= budget) break
        }
        builder.build()
    }.getOrNull()

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
        val v = vibrator
        val measured = if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { v.getPrimitiveDurations(primitive.id).firstOrNull()?.toLong() }.getOrNull()
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
        /** An envelope's shortest step where the motor doesn't say. */
        const val DEFAULT_MIN_STEP_MS = 1L
        /** An envelope ends this long before the next pulse. */
        const val ROOM_MARGIN_MS = 12L
        /** A slow rise's length where the phone can't say (Android 11): about what Pixels report. */
        const val TYPICAL_SLOW_RISE_MS = 300L
    }
}
