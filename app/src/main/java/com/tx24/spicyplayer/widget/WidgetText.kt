package com.tx24.spicyplayer.widget

/** Keep a cut-off syllable and its dash together without changing visible text or word spacing. */
internal fun widgetUnbreakableText(text: String): String = text.replace(DASH_AFTER_TEXT, "$1\u2060$2")

private val DASH_AFTER_TEXT = Regex("(\\S)([—–-])")
