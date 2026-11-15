package com.tx24.spicyplayer.lyrics.spicy

import com.tx24.spicyplayer.lyrics.spicy.animation.BloomMotion

/** Whether a word's syllables are drawn as one word, filling evenly from its first syllable to its last. */
enum class SyllableMerge(val label: String) {
    Off("Off"),
    /** Every split word. */
    Full("Full"),
    /** Every split word whose parts are sung steadily: no gap or space between them, none much faster or slower. */
    Steady("Steadily sung words"),
    /** Only words long enough, merged, for the held-word letter emphasis. */
    Held("Held words only"),
}

/** How a romanization row timed by syllable lights up under its line. */
enum class RomanizationHighlight(val label: String) {
    /** Each syllable fills from its start to its end as it's sung. */
    Sweep("Sweep"),
    /** Each syllable brightens whole while it's sung. */
    Word("Word by word"),
}

/**
 * The "no pace known" default, one shared instance: a lambda written in a default argument is a new
 * object on every call, which made every `ScrollConfig()` unequal to the last and, through the
 * layout cache key, re-measured the lyrics on every recomposition.
 */
private val NO_STATIC_PACE: () -> com.tx24.spicyplayer.network.data.spotify.StaticPace? = { null }

/** How the lyrics follow the song and answer a tap. None of it changes how lines are measured. */
data class ScrollConfig(
    /** Early Scroll: the view picks its line as if the song were this far ahead. 0 is off. */
    val leadMs: Long = 0L,
    /** Smooth Scrolling: a spring moves the view, carrying its speed into the next line. */
    val smooth: Boolean = false,
    /** Seek Fade-in Compensation: tapping a line seeks [SEEK_FADE_COMPENSATION_MS] before it. */
    val seekFadeCompensation: Boolean = false,
    /**
     * Static lyrics scroll through over this long (the song's length), top to bottom, and a run of
     * untimed lines in synced lyrics over the gap it fills; 0 leaves
     * their scrolling to the user.
     */
    val staticSongMs: Long = 0L,
    /**
     * The song's pace for static lyrics, once known: the lyrics hold through quiet intros, outros
     * and breaks. Null (or until it arrives) they move evenly over [staticSongMs].
     */
    val staticPace: () -> com.tx24.spicyplayer.network.data.spotify.StaticPace? = NO_STATIC_PACE,
    /**
     * Bloom's early scroll (the floating window): its timed scroll sets off early enough to reach
     * a line as it's sung, in place of Early Scroll's lead.
     */
    val bloomEarly: Boolean = false,
) {
    companion object {
        /** Players fade audio in for about this long after a seek, swallowing a line's first syllable. */
        const val SEEK_FADE_COMPENSATION_MS = 300L
    }
}

data class RenderConfig(
    /** The Bloom look (see `animation/BloomMotion`) instead of Spring. */
    val bloom: Boolean = false,
    /** Fade Away Previous Lines; already scoped to a fullscreen, non-compact surface by the caller. */
    val minimalLyricsMode: Boolean = false,
    /** A word's lit colour (`--gradient-alpha`). */
    val gradientAlphaBright: Float = if (bloom) BloomMotion.LIT else 0.85f,
    /** A word's unlit colour (`--gradient-alpha-end`). */
    val gradientAlphaDim: Float = if (bloom) BloomMotion.UNLIT else 0.5f,
    /** A whole line's unlit colour in line-synced lyrics. */
    val lineGradientAlphaDim: Float = if (bloom) BloomMotion.UNLIT else 0.35f,
    /** A background line's lit and unlit colours. */
    val backgroundAlphaBright: Float = if (bloom) BloomMotion.BACKGROUND_LIT else 0.6f,
    val backgroundAlphaDim: Float = if (bloom) BloomMotion.UNLIT else 0.3f,
    val opacityActive: Float = 1f,
    /** `--Vocal-NotSung-opacity` / `--Vocal-Sung-opacity`. Bloom dims by tone instead, so its lines stay at 1. */
    val opacityNotSung: Float = if (bloom) 1f else 0.51f,
    val opacitySung: Float = if (bloom) 1f else 0.497f,
    val distanceBlurEnabled: Boolean = true,
    /** False drops every glow halo (the Glow setting, low performance mode). */
    val glowEnabled: Boolean = true,
    /** How strong every glow is drawn: its brightness and spread. 1 is the desktop amount. */
    val glowStrength: Float = 1f,
    /** Fade Away Previous Lines is a line-visibility layer and does not disable word/letter motion. */
    val lettersEnabled: Boolean = true,
    val letterDurationThresholdMs: Long = 1000L,
    /** Held words split into letters have at least and at most this many letters. */
    val letterMinLength: Int = if (bloom) 2 else 1,
    val letterMaxLength: Int = if (bloom) 7 else Int.MAX_VALUE,
    val syllableMerge: SyllableMerge = SyllableMerge.Off,
    /** A merged held word's letters keep each syllable's own pace instead of an even one. */
    val syllablePace: Boolean = false,
    /**
     * Multiplies how far sung words and letters grow (their scale away from 1) and lift
     * (their y offset). 1 is the desktop amount.
     */
    val wordMotionBoost: Float = 1f,
    /** Duet Line Padding: duet lines inset 15cqw on the side they lean away from, not 5cqw. */
    val wideDuetPadding: Boolean = true,
    /** Credits Follow the Last Singer: a song ending on a duet line sung from the right has its credits there too. */
    val creditsFollowLastLine: Boolean = false,
    /** Refined Credits: quiet type under the last line, dim until the song's last line has been sung. */
    val refinedCredits: Boolean = true,
    /** Background Vocal Pop-Out: background lines stay tucked under their line until it is sung. */
    val backgroundPop: Boolean = false,
    /** Keep Overlapping Lines (Minimal): a line sung while one above it is still active stays on screen, dimmed. */
    val keepOverlappingLines: Boolean = true,
    /** How bright the lines not being sung are, against their usual brightness (Other lines). */
    val otherLinesOpacity: Float = 1f,
    val romanizationHighlight: RomanizationHighlight = RomanizationHighlight.Sweep,
    /**
     * The rows under a line move like the lyrics: a romanization row timed by syllable rises,
     * swells and glows with the words above it, and a translation row sweeps with the line.
     */
    val animatedUnderRows: Boolean = false,
    val scroll: ScrollConfig = ScrollConfig(),
) {
    val isBloom: Boolean get() = bloom
    val isMinimal: Boolean get() = minimalLyricsMode

    companion object {
        val FULL = RenderConfig()
        val BLOOM = RenderConfig(bloom = true)
        val MINIMAL = RenderConfig(minimalLyricsMode = true)
    }
}
