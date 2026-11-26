@file:Suppress("unused", "UNUSED_PARAMETER")
package android.media.audiofx

/** No system audio capture on the desktop: a visualizer that never reports. */
class Visualizer(session: Int) {
    var enabled: Boolean = false
    var captureSize: Int = 1024
    var scalingMode: Int = 0
    var measurementMode: Int = 0
    val samplingRate: Int get() = 44_100_000
    fun setEnabled(e: Boolean): Int { enabled = e; return SUCCESS }
    fun setCaptureSize(s: Int): Int { captureSize = s; return SUCCESS }
    fun setDataCaptureListener(l: OnDataCaptureListener?, rate: Int, wave: Boolean, fft: Boolean): Int = SUCCESS
    fun getWaveForm(out: ByteArray): Int = ERROR
    fun getFft(out: ByteArray): Int = ERROR
    fun getMeasurementPeakRms(m: MeasurementPeakRms): Int = ERROR
    fun release() {}
    class MeasurementPeakRms { @JvmField var mPeak = -9600; @JvmField var mRms = -9600 }
    interface OnDataCaptureListener { fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, rate: Int); fun onFftDataCapture(v: Visualizer?, f: ByteArray?, rate: Int) }
    companion object {
        const val SUCCESS = 0; const val ERROR = -1; const val SCALING_MODE_NORMALIZED = 0; const val SCALING_MODE_AS_PLAYED = 1
        const val MEASUREMENT_MODE_PEAK_RMS = 1
        @JvmStatic fun getCaptureSizeRange(): IntArray = intArrayOf(128, 1024)
        @JvmStatic fun getMaxCaptureRate(): Int = 20000
    }
}
