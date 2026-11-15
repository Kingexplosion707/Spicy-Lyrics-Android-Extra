package com.tx24.spicyplayer.lyrics.spicy.animation

/**
 * Represents the animation state of a single letter within a word.
 */
data class LetterAnimState(
    val gradientPosition: Float,
    val scale: Float,
    val yOffset: Float,
    val glow: Float,
    /** Bloom: the sideways push of a held word's letter, in em of its own text. */
    val xOffset: Float = 0f,
    /** Bloom: the glow's blur radius, in em of the letter's own text. */
    val glowRadius: Float = 0f,
)

/**
 * Represents the animation state of a word, including its scale, offset, and potential letter-level states.
 */
data class WordAnimState(
    val scale: Float,
    val yOffset: Float,
    val glow: Float,
    val gradientPosition: Float,
    val state: ElementState,
    /** For interlude dots only: the animated glow level (blur/opacity of the dot's halo). */
    val dotGlow: Float = 0f,
    val isLetterGroup: Boolean = false,
    val letterStates: List<LetterAnimState> = emptyList(),
)

/**
 * Represents the overall animation state of a line, including its words.
 */
data class LineAnimState(
    val opacity: Float,
    val blur: Float,
    val scale: Float,
    val isActive: Boolean,
    val wordStates: List<WordAnimState>,
    val isBackground: Boolean,
    val isSongwriter: Boolean,
    /** Line-mode only: the raw gradient position percent for the whole-line wipe (-20..100). */
    val lineGradientPercent: Float = -20f,
    /** Line-mode only: the whole-line glow spring value (shadow blur 4+8·glow, alpha glow·0.5). */
    val lineGlow: Float = 0f,
    /** Every glow halo is suppressed (low performance mode), not just distance blur. */
    val suppressShadows: Boolean = false,
    val state: ElementState = ElementState.NotSung,
    /** Background Vocal Pop-Out: how far open a background line is (0 folded away and taking no room, 1 open). */
    val presence: Float = 1f,
    /** Where [presence] is headed; the scroll aims at the layout that leaves. */
    val presenceGoal: Float = presence,
    /** Bloom: how lit the line's text is, 1 lit → 0 back at rest (a sung line dimming, a line-synced line fading). */
    val lit: Float = 0f,
    /** Bloom with animated rows: how lit the line's whole-line rows are (see [lit], but its own timing). */
    val rowLit: Float = 0f,
    /** Interlude dots: the whole group's breathing, about its middle, on top of [scale]. */
    val groupScale: Float = 1f,
    /**
     * Animated rows: the under-line romanization's words animated as words of their own, by
     * word of the line. Empty when the row isn't animated.
     */
    val rowWordStates: List<WordAnimState> = emptyList(),
    /**
     * Word-by-word romanization highlight: per word of the line a pair, how lit it is (0 dim,
     * 1 sung, 2 being sung) and its glow. Empty when the row doesn't use it.
     */
    val rowHighlight: List<Float> = emptyList(),
)

/**
 * Possible states for a lyric element (word, line, or dot).
 */
enum class ElementState { NotSung, Active, Sung }

/**
 * A line's measured sizes in px, for Bloom's single fill edge: each word's text width, the [gap]
 * after every word but the last, each held word's letters, and the text's font size; and per word,
 * the wrapped row it starts on and how many letters it draws.
 */
class LineSweepGeometry(
    val wordWidths: FloatArray,
    val gap: Float,
    val letterWidths: Array<FloatArray?>,
    val fontPx: Float,
    val rows: IntArray = IntArray(wordWidths.size),
    val letterCounts: IntArray = IntArray(wordWidths.size),
)
