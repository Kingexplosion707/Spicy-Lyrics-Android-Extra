package android.text.format

object DateFormat {
    /** The system's clock setting (Windows' short time pattern has an H when it's 24-hour). */
    @JvmStatic fun is24HourFormat(c: android.content.Context?): Boolean =
        (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) as? java.text.SimpleDateFormat)?.toPattern()?.contains('H') ?: true
    @JvmStatic fun format(pattern: CharSequence, time: Long): CharSequence = java.text.SimpleDateFormat(pattern.toString()).format(java.util.Date(time))
    @JvmStatic fun getBestDateTimePattern(l: java.util.Locale, skeleton: String): String = skeleton
}
