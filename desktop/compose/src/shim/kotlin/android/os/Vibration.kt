// No vibration motor on a desktop: haptics are accepted and do nothing.
@file:Suppress("unused", "UNUSED_PARAMETER")

package android.os

open class Vibrator {
    open fun hasVibrator(): Boolean = false
    open fun hasAmplitudeControl(): Boolean = false
    open fun areAllPrimitivesSupported(vararg ids: Int): Boolean = false
    open fun arePrimitivesSupported(vararg ids: Int): BooleanArray = BooleanArray(ids.size)
    open fun vibrate(e: VibrationEffect?) {}
    open fun vibrate(e: VibrationEffect?, a: VibrationAttributes?) {}
    open fun vibrate(e: VibrationEffect?, a: android.media.AudioAttributes?) {}
    open fun vibrate(ms: Long) {}
    open fun cancel() {}
    open val id: Int get() = 0
    open val resonantFrequency: Float get() = Float.NaN
    open val qFactor: Float get() = Float.NaN
    open fun areEnvelopeEffectsSupported(): Boolean = false
    open val envelopeEffectInfo: EnvelopeInfo? get() = null
    open fun getPrimitiveDurations(vararg ids: Int): IntArray = IntArray(ids.size)
    class EnvelopeInfo { val minControlPointDurationMillis: Long = 20; val maxControlPointDurationMillis: Long = 1000; val maxSize: Int = 16 }
}
class VibratorManager { val defaultVibrator: Vibrator = Vibrator(); val vibratorIds: IntArray = IntArray(0); fun getVibrator(id: Int) = defaultVibrator; fun cancel() {} }
open class VibrationEffect {
    class Composition { fun addPrimitive(id: Int, scale: Float = 1f, delay: Int = 0) = this; fun compose() = VibrationEffect()
        companion object { const val PRIMITIVE_CLICK = 1; const val PRIMITIVE_THUD = 2; const val PRIMITIVE_SPIN = 3; const val PRIMITIVE_QUICK_RISE = 4; const val PRIMITIVE_SLOW_RISE = 5; const val PRIMITIVE_QUICK_FALL = 6; const val PRIMITIVE_TICK = 7; const val PRIMITIVE_LOW_TICK = 8 } }
    class BasicEnvelopeBuilder {
        fun setInitialSharpness(s: Float) = this
        fun addControlPoint(intensity: Float, sharpness: Float, durationMs: Long) = this
        fun build() = VibrationEffect()
    }
    companion object {
        const val DEFAULT_AMPLITUDE = -1
        const val EFFECT_CLICK = 0; const val EFFECT_DOUBLE_CLICK = 1; const val EFFECT_TICK = 2; const val EFFECT_HEAVY_CLICK = 5
        @JvmStatic fun createOneShot(ms: Long, amp: Int) = VibrationEffect()
        @JvmStatic fun createWaveform(t: LongArray, rep: Int) = VibrationEffect()
        @JvmStatic fun createWaveform(t: LongArray, a: IntArray, rep: Int) = VibrationEffect()
        @JvmStatic fun createPredefined(id: Int) = VibrationEffect()
        @JvmStatic fun startComposition() = Composition()
    }
}
class VibrationAttributes private constructor() {
    class Builder { fun setUsage(u: Int) = this; fun build() = VibrationAttributes() }
    companion object { const val USAGE_MEDIA = 19; const val USAGE_TOUCH = 18; const val USAGE_ASSISTANCE_SONIFICATION = 13; const val USAGE_ALARM = 17; @JvmStatic fun createForUsage(u: Int) = VibrationAttributes() }
}
