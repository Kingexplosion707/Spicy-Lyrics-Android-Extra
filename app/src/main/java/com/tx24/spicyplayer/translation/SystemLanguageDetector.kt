package com.tx24.spicyplayer.translation

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage

/**
 * The phone's own language identification (Android 10 and later), on the device: nothing is sent
 * anywhere. Null below Android 10, when the phone has no answer, or when it isn't confident.
 * Call it off the main thread.
 */
class SystemLanguageDetector(private val context: Context) : SongLanguageDetector {
    override fun detect(originalLyrics: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || originalLyrics.isBlank()) return null
        return runCatching {
            val classifier = context.getSystemService(TextClassificationManager::class.java)?.textClassifier ?: return null
            val answer = classifier.detectLanguage(TextLanguage.Request.Builder(originalLyrics.take(MAX_CHARS)).build())
            if (answer.localeHypothesisCount == 0) return null
            val top = answer.getLocale(0)
            top.toLanguageTag().takeIf { answer.getConfidenceScore(top) >= MIN_CONFIDENCE }
        }.getOrNull()
    }

    private companion object {
        /** Plenty to tell a language by; more only costs time. */
        const val MAX_CHARS = 2_000
        /** Sure enough to hide a button over: a mixed-language song rarely gets this far. */
        const val MIN_CONFIDENCE = 0.8f
    }
}
