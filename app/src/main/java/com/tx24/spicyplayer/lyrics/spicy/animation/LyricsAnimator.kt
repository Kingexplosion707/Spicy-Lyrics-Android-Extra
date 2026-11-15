package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.RomanizationHighlight
import com.tx24.spicyplayer.lyrics.spicy.parser.LetterSynthesizer
import com.tx24.spicyplayer.translation.romanizationNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The lyrics animator, stepped once per frame with the playback position:
 * - Only **Active** lines have their word/letter/dot springs retargeted and stepped.
 * - **NotSung** lines are never touched — their words freeze at whatever state they last had
 *   (initially the resting state). There is no snap-to-resting reset pass.
 * - A **Sung** line keeps stepping toward its final targets (the `checkNextLine` pass) only
 *   while the following line is itself not yet Sung (or when it's the last line); afterwards
 *   it freezes. This is what makes a just-finished line settle out gracefully instead of
 *   stopping dead the moment its time window ends.
 * - Line-level opacity is the Compose equivalent of the CSS class transition
 *   (200ms cubic-bezier(0.61,1,0.88,1)); distance blur is recomputed only when the active
 *   line index changes.
 *
 * Bloom works its word and letter poses out from the time alone, sung tails included.
 * Springs are [SpringSimulation] (exact spr.lua port); dt is in **seconds**, positions in ms.
 */
class LyricsAnimator(
    private val coroutineScope: CoroutineScope,
    config: RenderConfig = RenderConfig.FULL,
) {

    var config: RenderConfig = config
        set(value) {
            // The motion boost is applied at draw time; only real mode changes restart the springs.
            val modeChanged = value.copy(wordMotionBoost = 1f) != field.copy(wordMotionBoost = 1f)
            field = value
            if (modeChanged) {
                rebuildModeSplines()
                reset()
            }
        }

    // ── Splines ──────────────────────────────────────────────────────────────────────────
    private val scaleSpline = spline(0f to 0.95f, 0.7f to 1.0505f, 1f to 1f)
    private val glowSpline = spline(0f to 0f, 0.15f to 1f, 0.6f to 1f, 1f to 0f)
    private val dotScaleSpline = spline(0f to 0.75f, 0.7f to 1.05f, 1f to 1f)
    private val dotYOffsetSpline = spline(0f to 0f, 0.9f to -0.12f, 1f to 0f)
    private val dotGlowSpline = spline(0f to 0f, 0.6f to 1f, 1f to 1f)
    private val lineGlowSpline = spline(0f to 0f, 0.5f to 1f, 1f to 0f)

    // Mode-dependent splines (rebuilt when the quality mode changes).
    private lateinit var yOffsetSpline: CubicSplineInterpolator
    private lateinit var letterScaleSpline: CubicSplineInterpolator
    private lateinit var letterYOffsetSpline: CubicSplineInterpolator
    private lateinit var dotOpacitySpline: CubicSplineInterpolator

    init { rebuildModeSplines() }

    private fun rebuildModeSplines() {
        val bloom = config.isBloom
        yOffsetSpline = if (bloom) spline(0f to 0.01f, 1f to -0.033f)
            else spline(0f to 0.01f, 0.9f to -1f / 60f, 1f to 0f)
        letterScaleSpline = if (bloom) spline(0f to 0.95f, 0.7f to 1.07f, 1f to 1f)
            else spline(0f to 0.95f, 0.7f to 1.175f, 1f to 1f)
        letterYOffsetSpline = if (bloom) spline(0f to 0.01f, 0.9f to -1f / 62f, 1f to 0f)
            else spline(0f to 0.01f, 0.9f to -1f / 56f, 1f to 0f)
        dotOpacitySpline = spline(0f to (if (bloom) 0.27f else 0.35f), 0.6f to 1f, 1f to 1f)
    }

    internal companion object {
        // Word/letter spring tuning (reference "active" values).
        const val SCALE_FREQUENCY = 0.88f
        const val SCALE_DAMPING = 0.64f
        const val Y_OFFSET_FREQUENCY = 1.45f
        const val Y_OFFSET_DAMPING = 0.4f
        const val GLOW_FREQUENCY = 1.18f
        const val GLOW_DAMPING = 0.56f

        // Dot spring tuning.
        const val DOT_SCALE_FREQUENCY = 0.7f
        const val DOT_SCALE_DAMPING = 0.6f
        const val DOT_Y_OFFSET_FREQUENCY = 1.25f
        const val DOT_Y_OFFSET_DAMPING = 0.4f
        const val DOT_GLOW_FREQUENCY = 1f
        const val DOT_GLOW_DAMPING = 0.5f
        const val DOT_OPACITY_FREQUENCY = 1f
        const val DOT_OPACITY_DAMPING = 0.5f

        /** Added to a line's index to key the springs of its under-line row apart from the line's own. */
        const val ROW_KEY = 1 shl 20
        val NO_HIGHLIGHT: List<Float> = emptyList()

        // Line-mode whole-line glow spring.
        const val LINE_GLOW_FREQUENCY = 1f
        const val LINE_GLOW_DAMPING = 0.5f

        /** GlowSpline input for a finished letter while its word still has an active letter path. */
        const val SUNG_LETTER_GLOW = 0.2f

        // Distance blur (Shared.ts).
        const val BLUR_MULTIPLIER = 1.25f
        val MAX_BLUR = BLUR_MULTIPLIER * 5f + BLUR_MULTIPLIER * 0.465f  // 6.83125

        /** Dots collapse this long before their line ends (lyrics.ts preHiddenDotLineMs). */
        const val PRE_HIDDEN_DOT_LINE_MS = 500L

        val OPACITY_EASING = CubicBezierEasing(0.61f, 1f, 0.88f, 1f)
        val SCALE_EASING = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
        /** Reference dot-group collapse (Mixed.css .pre-hidden .dotGroup): 0.4s w/ dip-then-overshoot. */
        val DOT_COLLAPSE_STOPS = floatArrayOf(
            0f, 0f, 0.094f, -0.006f, 0.18f, -0.029f, 0.433f, -0.157f,
            0.514f, -0.185f, 0.559f, -0.189f, 0.6f, -0.182f, 0.639f, -0.163f,
            0.676f, -0.133f, 0.723f, -0.074f, 0.767f, 0.006f, 0.85f, 0.238f,
            0.927f, 0.566f, 1f, 1f,
        )
        val DOT_GROUP_COLLAPSE_EASING = Easing { fraction ->
            val stops = DOT_COLLAPSE_STOPS
            var index = 0
            while (index < stops.size - 4 && fraction > stops[index + 2]) index += 2
            val x0 = stops[index]
            val y0 = stops[index + 1]
            val x1 = stops[index + 2]
            val y1 = stops[index + 3]
            y0 + (y1 - y0) * ((fraction - x0) / (x1 - x0).coerceAtLeast(0.0001f))
        }
        const val MUSICAL_LINE_TRANSITION_MS = 140
        /** `.line` opacity (and line-synced scale) transition: 0.2s. */
        const val LINE_TRANSITION_MS = 200
        /** Minimal Lyrics Mode (Mixed.css): scale 0.4s ease-in-out, opacity 0.4s linear. */
        const val MINIMAL_TRANSITION_MS = 400
        /**
         * Background Vocal Pop-Out: how long a background line takes to open or tuck away. One
         * length both ways: a line change folds the last one as it opens the next, and the rows
         * below must see the two cancel, not move up past their place and drift back.
         */
        const val POP_MS = 300
        /** How long a tucking background line takes to fade. */
        const val POP_FADE_MS = 120
        val POP_FADE_EASING = CubicBezierEasing(0f, 0f, 0.58f, 1f)
        /** Folded, a background line is this size. */
        const val POP_FOLDED_SCALE = 0.8f
        /** A folding background line moves this far (of the room it gives back) up under its lead. */
        const val BACKGROUND_FOLDED_SHIFT = 0.3f
        val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        const val MINIMAL_SUNG_SCALE = 0.95f
        const val MINIMAL_NOT_SUNG_SCALE = 0.965f
        const val MINIMAL_NOT_SUNG_OPACITY = 0.5f
        const val MINIMAL_AFTER_ACTIVE_OPACITY = 0.45f
        /** CSS `ease`. */
        val CSS_EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        const val DOT_GROUP_EXPANSION_MS = 300
        const val DOT_GROUP_COLLAPSE_MS = 400

        fun spline(vararg points: Pair<Float, Float>) =
            CubicSplineInterpolator(points.map { AnimationPoint(it.first, it.second) })

        /**
         * A background line's opacity for how far open it is: it shows only once its room has
         * mostly opened, and is gone before it closes, so it's never squeezed half-visible.
         */
        fun backgroundAlpha(presence: Float): Float {
            val x = ((presence - 0.3f) / 0.7f).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
    }

    /** Word/letter-group container springs, snapped to resting on creation (reference lazy-init). */
    private inner class WordSprings {
        val scale = SpringSimulation(scaleSpline.at(0f), SCALE_FREQUENCY, SCALE_DAMPING)
        val yOffset = SpringSimulation(yOffsetSpline.at(0f), Y_OFFSET_FREQUENCY, Y_OFFSET_DAMPING)
        val glow = SpringSimulation(glowSpline.at(0f), GLOW_FREQUENCY, GLOW_DAMPING)
    }

    private inner class LetterSprings {
        val scale = SpringSimulation(letterScaleSpline.at(0f), SCALE_FREQUENCY, SCALE_DAMPING)
        val yOffset = SpringSimulation(letterYOffsetSpline.at(0f), Y_OFFSET_FREQUENCY, Y_OFFSET_DAMPING)
        val glow = SpringSimulation(glowSpline.at(0f), GLOW_FREQUENCY, GLOW_DAMPING)
    }

    private inner class DotSprings {
        val scale = SpringSimulation(dotScaleSpline.at(0f), DOT_SCALE_FREQUENCY, DOT_SCALE_DAMPING)
        val yOffset = SpringSimulation(dotYOffsetSpline.at(0f), DOT_Y_OFFSET_FREQUENCY, DOT_Y_OFFSET_DAMPING)
        val glow = SpringSimulation(dotGlowSpline.at(0f), DOT_GLOW_FREQUENCY, DOT_GLOW_DAMPING)
        val opacity = SpringSimulation(dotOpacitySpline.at(0f), DOT_OPACITY_FREQUENCY, DOT_OPACITY_DAMPING)
    }

    // ── Per-element animation state ─────────────────────────────────────────────────
    private val wordSpringsMap = mutableMapOf<Long, WordSprings>()
    private val letterSpringsMap = mutableMapOf<Long, LetterSprings>()
    private val dotSpringsMap = mutableMapOf<Long, DotSprings>()
    private val lineGlowSprings = mutableMapOf<Int, SpringSimulation>()

    private val lineOpacityAnims = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()
    private val lineScaleAnims = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()

    /** Frozen word states per line — reused verbatim on frames where the line isn't touched. */
    private val cachedWordStates = mutableMapOf<Int, List<WordAnimState>>()
    private val cachedLineGradient = mutableMapOf<Int, Float>()
    private val cachedLineGlow = mutableMapOf<Int, Float>()

    /** The under-line row's words (see [LetterSynthesizer.rowWord]) and their frozen states, per line. */
    private val rowWordsCache = mutableMapOf<Int, List<Word>>()
    private val cachedRowStates = mutableMapOf<Int, List<WordAnimState>>()

    /** Word-by-word row highlight easing per active line: pairs of (lit level, glow) per word. */
    private val rowHighlights = mutableMapOf<Int, FloatArray>()

    /**
     * The lines the word caches were built from. Lines get rebuilt (romanization switched on,
     * letters re-synthesized) with different words and letter counts, so the per-word caches and
     * springs are dropped then. Otherwise an upcoming line kept its old letter states and drew
     * only the first letter or two of a held word until the line became active.
     */
    private var cachedFor: List<Line>? = null
    private var cachedRomanizationFor: List<Line>? = null

    /** Blur is recomputed only when the active line index changes (reference Blurring_LastLine). */
    private var blurringLastLine: Int = -1
    private var blurAmounts = FloatArray(0)

    fun reset() {
        wordSpringsMap.clear()
        letterSpringsMap.clear()
        dotSpringsMap.clear()
        lineGlowSprings.clear()
        lineOpacityAnims.clear()
        lineScaleAnims.clear()
        cachedWordStates.clear()
        cachedLineGradient.clear()
        cachedLineGlow.clear()
        rowWordsCache.clear()
        cachedRowStates.clear()
        rowHighlights.clear()
        presenceAnims.clear()
        popFadeAnims.clear()
        popLeads = null
        clearBloom()
        blurringLastLine = -1
        blurAmounts = FloatArray(0)
    }

    /**
     * Computes the animation state for all lines at [currentTimeMs].
     *
     * @param deltaTime seconds since the last frame, capped at two frame intervals.
     * @param suppressBlur true from the user's first touch until auto-scroll resumes.
     */
    fun animate(
        lines: List<Line>,
        currentTimeMs: Long,
        deltaTime: Float,
        suppressBlur: Boolean = false,
        lyricsType: LyricsType = LyricsType.Syllable,
        /** Original word timings for romanization below a whole-line translation replacement. */
        romanizationLines: List<Line>? = null,
        /** Bloom: the measured widths of a line's words and letters, for the fill's edge. */
        lineGeometry: ((Int) -> LineSweepGeometry?)? = null,
        /** Bloom: the same for a line's animated romanization row. */
        rowGeometry: ((Int) -> LineSweepGeometry?)? = null,
        /** The line the scroll is moving to, if any: under Bloom it grows to full size as the scroll brings it in. */
        scrollTarget: Int? = null,
        /**
         * Bloom's early scroll: lines above this one that have finished singing count as sung,
         * though their time runs on to the next line. -1 for none.
         */
        earlySungBefore: Int = -1,
    ): List<LineAnimState> {
        if (lines.isEmpty()) return emptyList()

        // Static lyrics have no timing: full-white, no animation (reference Static mode).
        if (lyricsType == LyricsType.Static) {
            return lines.map {
                LineAnimState(
                    opacity = 1f, blur = 0f, scale = 1f, isActive = false,
                    wordStates = emptyList(), isBackground = false, isSongwriter = false,
                    state = ElementState.NotSung,
                )
            }
        }

        if (lines !== cachedFor || romanizationLines !== cachedRomanizationFor) {
            cachedFor = lines
            cachedRomanizationFor = romanizationLines
            cachedWordStates.clear()
            rowWordsCache.clear()
            cachedRowStates.clear()
            rowHighlights.clear()
            wordSpringsMap.clear()
            letterSpringsMap.clear()
            dotSpringsMap.clear()
            presenceAnims.clear()
            popFadeAnims.clear()
            popLeads = null
            clearBloom()
        }

        val processedPosition = currentTimeMs.toDouble() -
            if (config.isBloom) BloomMotion.POSITION_SHIFT_MS else 0.0

        if (blurAmounts.size != lines.size) blurAmounts = FloatArray(lines.size)

        val states = lines.mapIndexed { i, line ->
            val state = elementState(processedPosition, line.startMs, line.endMs)
            if (state == ElementState.Active && i < earlySungBefore && processedPosition >= sungEnd(line)) ElementState.Sung else state
        }
        // Minimal's `.LyricsContent:not(.HideLineBlur)` rules drop out while the user scrolls.
        val minimalLook = config.isMinimal && !suppressBlur
        // `.LyricsContent:not(:has(.line.Active)):not(:has(.line.NotSung))`: the song is over.
        val allSung = states.none { it != ElementState.Sung }
        val firstActive = states.indexOf(ElementState.Active)

        return lines.mapIndexed { lineIdx, line ->
            val romanization = if (line.translationReplaces && lyricsType == LyricsType.Syllable) romanizationLines?.getOrNull(lineIdx) else null
            val lyricsType = if (line.translationReplaces || line.lineTimed && lyricsType == LyricsType.Syllable) LyricsType.Line else lyricsType
            val lineState = states[lineIdx]
            val isActive = !line.isSongwriter && lineState == ElementState.Active

            // Distance blur: recompute only when the active line changes.
            if (isActive && !line.isBackground && blurringLastLine != lineIdx) {
                applyBlur(lines, lineIdx, processedPosition)
                blurringLastLine = lineIdx
            }

            // A line whose own words just finished can still be mid-settle (checkNextLine) while
            // a concurrent bg line — or the array-adjacency check — keeps it from freezing yet;
            // this only governs how long the scale/glow springs keep relaxing smoothly instead of
            // freezing abruptly. It must NOT keep the line's fill brighter than any other inactive
            // line — the renderer treats every non-active line's words identically regardless of
            // Sung/NotSung, so line opacity can go back to tracking lineState alone.
            val stillFinalizing = lineState == ElementState.Sung &&
                (shouldFinalize(lines, lineIdx, processedPosition) || !isSettled(cachedWordStates[lineIdx]) ||
                    cachedRowStates[lineIdx]?.let { !isSettled(it) } == true)
            // A sung line that ends while one above it is still being sung (overlapping lines,
            // background vocals) stays on screen, dimmed, instead of fading out early.
            val keptAfterActive = config.keepOverlappingLines && lineState == ElementState.Sung && !line.isInterlude &&
                firstActive in 0 until lineIdx
            val afterActive = keptAfterActive || !config.keepOverlappingLines && states.getOrNull(lineIdx - 1) == ElementState.Active
            val opacity = animateLineOpacity(lineIdx, line, lineState, isActive, lyricsType, minimalLook, allSung, afterActive)
            // Bloom: how lit the line's text is, for a sung line dimming back to rest and for a
            // line-synced line lighting up as a whole.
            val lit = if (config.isBloom && !line.isInterlude && !line.isSongwriter) bloomTone(lineIdx, isActive, lyricsType) else 0f
            val lineScale = when {
                line.isInterlude -> animateInterludeScale(lineIdx, line, processedPosition, isActive)
                // Mixed.css `.Fullscreen.MinimalLyricsMode .line:not(.musical-line)`.
                config.isMinimal && !line.isSongwriter -> animateTweenScale(lineIdx, when {
                    lineState == ElementState.Active || allSung || !minimalLook || keptAfterActive -> 1f
                    lineState == ElementState.Sung -> MINIMAL_SUNG_SCALE
                    else -> MINIMAL_NOT_SUNG_SCALE
                })
                // Bloom: lines rest a little smaller and grow as the scroll brings them in.
                config.isBloom && !line.isSongwriter && !line.isBackground ->
                    bloomScale(lineIdx, isActive || scrollTarget == lineIdx)
                config.isBloom -> 1f
                // Line-mode active line scales to 1.05 (CSS: data-lyrics-type="Line" .line.Active).
                lyricsType == LyricsType.Line && !line.isSongwriter ->
                    animateTweenScale(lineIdx, if (isActive) LyricsLayoutCalculator.ACTIVE_LINE_SCALE else 1f)
                else -> 1f
            }

            val wordStates: List<WordAnimState>
            var lineGradient = cachedLineGradient[lineIdx] ?: -20f
            var lineGlow = cachedLineGlow[lineIdx] ?: 0f

            if (lyricsType == LyricsType.Line && !line.isInterlude) {
                // ── Line mode: whole-line gradient + glow spring; no word processing. ──
                when (lineState) {
                    ElementState.Active -> {
                        val pct = progress(processedPosition, line.startMs, line.endMs)
                        // A line with no timing of its own is lit whole: there's nothing to wipe by.
                        val whole = config.isBloom || line.untimed
                        lineGradient = if (whole) 100f else pct * 100f
                        if (whole) {
                            lineGlow = 0f
                        } else {
                            val spring = lineGlowSprings.getOrPut(lineIdx) {
                                SpringSimulation(lineGlowSpline.at(0f), LINE_GLOW_FREQUENCY, LINE_GLOW_DAMPING)
                            }
                            spring.setGoal(lineGlowSpline.at(pct))
                            lineGlow = spring.step(deltaTime)
                        }
                    }
                    ElementState.NotSung -> lineGradient = if (config.isBloom || line.untimed) 100f else -20f
                    ElementState.Sung -> lineGradient = 100f
                }
                cachedLineGradient[lineIdx] = lineGradient
                cachedLineGlow[lineIdx] = lineGlow
                wordStates = if (isActive && romanization != null) {
                    if (config.isBloom) bloomLine(lineIdx + ROW_KEY, romanization.words, processedPosition, line, null)
                    else romanization.words.mapIndexed { wordIdx, word ->
                        animateWord(word, processedPosition, deltaTime, lineIdx, wordIdx)
                    }
                } else emptyList()
            } else {
                wordStates = when {
                    config.isBloom && !line.isSongwriter -> {
                        if (line.isInterlude) bloomDots(line, processedPosition)
                        else bloomLine(lineIdx, line.words,
                            if (lineState == ElementState.NotSung) Double.NEGATIVE_INFINITY else processedPosition,
                            line, lineGeometry, endMs = if (lineIdx < earlySungBefore) minOf(line.endMs, sungEnd(line)) else line.endMs)
                    }
                    line.isSongwriter -> cachedWordStates.getOrPut(lineIdx) {
                        line.words.map { songwriterWordState(it) }
                    }
                    lineState == ElementState.Active -> {
                        val states = if (line.isInterlude) {
                            animateInterludeDots(line, processedPosition, deltaTime, lineIdx, finalize = false)
                        } else {
                            line.words.mapIndexed { wordIdx, word ->
                                animateWord(word, processedPosition, deltaTime, lineIdx, wordIdx)
                            }
                        }
                        cachedWordStates[lineIdx] = states
                        states
                    }
                    stillFinalizing -> {
                        // checkNextLine: keep settling toward final targets until the next line is Sung.
                        val states = if (line.isInterlude) {
                            animateInterludeDots(line, processedPosition, deltaTime, lineIdx, finalize = true)
                        } else {
                            line.words.mapIndexed { wordIdx, word ->
                                finalizeWord(word, deltaTime, lineIdx, wordIdx)
                            }
                        }
                        cachedWordStates[lineIdx] = states
                        states
                    }
                    else -> {
                        // Frozen: NotSung lines and long-finished Sung lines are never touched. A seek
                        // back leaves lines that were sung ahead of the song again, so they start over.
                        if (lineState == ElementState.NotSung && cachedWordStates[lineIdx]?.any { it.state != ElementState.NotSung } == true) {
                            forgetLine(lineIdx)
                        }
                        cachedWordStates.getOrPut(lineIdx) { line.words.map { restingWordState(it) } }
                    }
                }
            }

            if (config.animatedUnderRows && !config.isBloom && lyricsType == LyricsType.Syllable && !line.isInterlude && !line.isSongwriter) {
                // The translation row under a word-synced line sweeps over the line's whole time.
                when (lineState) {
                    ElementState.Active -> {
                        val pct = progress(processedPosition, line.startMs, line.endMs)
                        lineGradient = pct * 100f
                        val spring = lineGlowSprings.getOrPut(lineIdx) {
                            SpringSimulation(lineGlowSpline.at(0f), LINE_GLOW_FREQUENCY, LINE_GLOW_DAMPING)
                        }
                        spring.setGoal(lineGlowSpline.at(pct))
                        lineGlow = spring.step(deltaTime)
                    }
                    ElementState.NotSung -> { lineGradient = -20f; lineGlow = 0f }
                    ElementState.Sung -> { lineGradient = 100f; lineGlow = 0f }
                }
                cachedLineGradient[lineIdx] = lineGradient
                cachedLineGlow[lineIdx] = lineGlow
            }

            var rowWordStates: List<WordAnimState> = emptyList()
            var rowHighlight = NO_HIGHLIGHT
            if (lyricsType == LyricsType.Syllable && !line.isInterlude && !line.isSongwriter && !line.lineTimed) {
                val noteLine = romanizationLines?.getOrNull(lineIdx) ?: line
                if (config.animatedUnderRows) {
                    // The under-line romanization is a line of its own: its words run through the same
                    // springs as the lyrics, each held one split into its own letters.
                    val rowWords = rowWordsCache.getOrPut(lineIdx) {
                        romanizationNote(noteLine)?.pieces?.map { LetterSynthesizer.rowWord(it.word, it.text, config) } ?: emptyList()
                    }
                    if (rowWords.isNotEmpty()) rowWordStates = when {
                        // Under Bloom the row moves as the line's words do: one fill edge along it, each piece
                        // lifting as it is sung, held ones swelling, all coming back down once the line ends.
                        config.isBloom -> bloomRow(lineIdx, rowWords, processedPosition, line, lineState, rowGeometry,
                            endMs = if (lineIdx < earlySungBefore) minOf(line.endMs, sungEnd(line)) else line.endMs)
                        lineState == ElementState.Active -> rowWords.mapIndexed { i, word ->
                            animateWord(word, processedPosition, deltaTime, lineIdx + ROW_KEY, i)
                        }.also { cachedRowStates[lineIdx] = it }
                        stillFinalizing -> rowWords.mapIndexed { i, word ->
                            finalizeWord(word, deltaTime, lineIdx + ROW_KEY, i)
                        }.also { cachedRowStates[lineIdx] = it }
                        else -> {
                            cachedRowStates.remove(lineIdx)
                            emptyList()
                        }
                    }
                } else if (config.romanizationHighlight == RomanizationHighlight.Word && lineState == ElementState.Active) {
                    rowHighlight = stepRowHighlight(lineIdx, noteLine, processedPosition, deltaTime)
                } else rowHighlights.remove(lineIdx)
            }

            val blur = if (!config.distanceBlurEnabled || suppressBlur || isActive || minimalLook && keptAfterActive) 0f
                else blurAmounts.getOrElse(lineIdx) { 0f }

            val (presence, presenceGoal, presenceAlpha) = popPresence(lines, states, lineIdx, lyricsType)
            val folded = POP_FOLDED_SCALE
            LineAnimState(
                opacity = opacity * presenceAlpha,
                blur = blur,
                scale = if (presence == 1f) lineScale else lineScale * (folded + (1f - folded) * presence),
                presence = presence,
                presenceGoal = presenceGoal,
                groupScale = if (config.isBloom && line.isInterlude)
                    BloomMotion.dotGroup(processedPosition - line.startMs, line.duration, PRE_HIDDEN_DOT_LINE_MS).scale else 1f,
                lit = lit,
                rowLit = if (config.isBloom && config.animatedUnderRows && !line.isInterlude && !line.isSongwriter) bloomRowTone(lineIdx, isActive) else 0f,
                isActive = isActive,
                wordStates = wordStates,
                rowWordStates = rowWordStates,
                rowHighlight = rowHighlight,
                isBackground = line.isBackground,
                isSongwriter = line.isSongwriter,
                lineGradientPercent = lineGradient,
                lineGlow = lineGlow,
                suppressShadows = !config.glowEnabled,
                state = lineState,
            )
        }
    }

    // ── Background Vocal Pop-Out ────────────────────────────────────────────────────

    private val POP_EASING = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Each background line's lead (same group), or -1; null until worked out for these lines. */
    private var popLeads: IntArray? = null

    /** A background line's openness under Background Vocal Pop-Out, where it's heading, and its opacity for it. */
    private data class PopPresence(val presence: Float, val goal: Float, val alpha: Float)

    /**
     * How far open a background line is under Background Vocal Pop-Out: closed (taking no room)
     * until it or its lead is sung, then sliding out. Once both have been sung it stays open as a
     * sung line until the list moves on to a later line, and folds with that scroll: folding a
     * line at rest would shift the rows below under the reader. So in Minimal Lyrics Mode it fades
     * out with its lead, as a sung line, before it folds. Folding, it's gone in a quick fade before
     * the rows below glide up over it. 1 for every other line, and whenever the option is off.
     */
    private fun popPresence(lines: List<Line>, states: List<ElementState>, lineIdx: Int, lyricsType: LyricsType): PopPresence {
        val line = lines[lineIdx]
        if (!config.backgroundPop || !line.isBackground || line.isInterlude || lyricsType == LyricsType.Static) return PopPresence(1f, 1f, 1f)
        val leads = popLeads ?: IntArray(lines.size) { i ->
            val other = lines[i]
            if (!other.isBackground || other.groupId == null) -1
            else lines.indices.firstOrNull { j ->
                lines[j].groupId == other.groupId && !lines[j].isBackground && !lines[j].isInterlude
            } ?: -1
        }.also { popLeads = it }
        val lead = leads[lineIdx]
        val anim = presenceAnims[lineIdx]
        val sung = states[lineIdx] == ElementState.Active || lead >= 0 && states[lead] == ElementState.Active
        val kept = !sung && anim?.targetValue == 1f && states[lineIdx] == ElementState.Sung &&
            (lead < 0 || states[lead] == ElementState.Sung) && !movedOn(lines, states, lineIdx)
        val target = if (sung || kept) 1f else 0f
        val presence = anim ?: Animatable(target).also { presenceAnims[lineIdx] = it }
        val fade = popFadeAnims.getOrPut(lineIdx) { Animatable(target) }
        if (presence.targetValue != target) {
            // Quick ease-out glides: the scroll aims at the settled layout, so the room opening
            // up must not lag behind it.
            coroutineScope.launch { presence.animateTo(target, tween(POP_MS, easing = POP_EASING)) }
            if (target == 1f) coroutineScope.launch { fade.snapTo(1f) }
            else coroutineScope.launch { fade.animateTo(0f, tween(POP_FADE_MS, easing = POP_FADE_EASING)) }
        }
        val alpha = if (target == 1f) backgroundAlpha(presence.value) else fade.value
        return PopPresence(presence.value, target, alpha)
    }

    /** Whether a line after [lineIdx]'s group is being sung, so the list has scrolled on past it. */
    private fun movedOn(lines: List<Line>, states: List<ElementState>, lineIdx: Int): Boolean {
        val group = lines[lineIdx].groupId
        for (j in states.indices.reversed()) {
            if (j <= lineIdx) return false
            if (states[j] == ElementState.Active && (group == null || lines[j].groupId != group)) return true
        }
        return false
    }

    /** Each background line's fade as it closes under Background Vocal Pop-Out. */
    private val popFadeAnims = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()

    /** Each background line's openness under Background Vocal Pop-Out. */
    private val presenceAnims = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()

    // ── Line-level pieces ───────────────────────────────────────────────────────────

    private fun animateLineOpacity(
        lineIdx: Int,
        line: Line,
        lineState: ElementState,
        isActive: Boolean,
        lyricsType: LyricsType,
        minimalLook: Boolean,
        allSung: Boolean,
        afterActive: Boolean,
    ): Float {
        val target = when {
            line.isSongwriter -> 0.6f
            line.isInterlude -> if (isActive) 1f else 0f
            // Reference: bg-lines have NO opacity override — they use the standard .line state
            // opacities; only their gradient alphas differ (0.6/0.3, applied in the renderer).
            minimalLook && allSung -> config.opacitySung
            // Bloom dims by tone, so the lines still to come and a finished line kept beside the
            // one being sung keep full opacity.
            minimalLook -> when (lineState) {
                ElementState.Active -> config.opacityActive
                ElementState.NotSung -> if (config.isBloom) 1f else MINIMAL_NOT_SUNG_OPACITY
                // A finished line below an active one stays faint.
                ElementState.Sung -> if (!afterActive) 0f else if (config.isBloom) 1f else MINIMAL_AFTER_ACTIVE_OPACITY
            }
            else -> when (lineState) {
                ElementState.Active -> config.opacityActive
                ElementState.NotSung -> (config.opacityNotSung * config.otherLinesOpacity).coerceAtMost(1f)
                ElementState.Sung -> (config.opacitySung * config.otherLinesOpacity).coerceAtMost(1f)
            }
        }
        val animatable = lineOpacityAnims.getOrPut(lineIdx) { Animatable(target) }
        if (animatable.targetValue != target) {
            val spec = when {
                line.isInterlude -> tween<Float>(MUSICAL_LINE_TRANSITION_MS, easing = OPACITY_EASING)
                config.isMinimal && !line.isSongwriter -> tween<Float>(MINIMAL_TRANSITION_MS, easing = LinearEasing)
                // The line-synced `.line` rule swaps the easing for its scale curve.
                lyricsType == LyricsType.Line -> tween<Float>(LINE_TRANSITION_MS, easing = SCALE_EASING)
                else -> tween<Float>(LINE_TRANSITION_MS, easing = OPACITY_EASING)
            }
            coroutineScope.launch { animatable.animateTo(target, spec) }
        }
        return animatable.value
    }

    /**
     * Interlude dot-group visibility (the line's height collapse and its pre-hidden state): 1 while active until 500ms before the line ends,
     * 0 otherwise. Non-interlude lines have no line-level scale (reference Syllable mode).
     */
    private fun animateInterludeScale(
        lineIdx: Int,
        line: Line,
        processedPosition: Double,
        isActive: Boolean,
    ): Float {
        if (!line.isInterlude) return 1f
        val preHidden = processedPosition > line.endMs - PRE_HIDDEN_DOT_LINE_MS
        val target = if (isActive && !preHidden) 1f else 0f
        val animatable = lineScaleAnims.getOrPut(lineIdx) { Animatable(target) }
        if (animatable.targetValue != target) {
            // The collapse (1→0, at pre-hidden) uses a slower 0.4s dip-then-overshoot
            // curve; expansion (0→1, on activation) keeps the default line-transition tween.
            val duration = if (target == 0f) DOT_GROUP_COLLAPSE_MS else DOT_GROUP_EXPANSION_MS
            val easing = if (target == 0f) DOT_GROUP_COLLAPSE_EASING else SCALE_EASING
            coroutineScope.launch {
                animatable.animateTo(target, tween(duration, easing = easing))
            }
        }
        return animatable.value
    }

    /** Generic line-scale tween (Line-mode active-line 1.05, Minimal's shrink; CSS transitions). */
    private fun animateTweenScale(lineIdx: Int, target: Float): Float {
        val animatable = lineScaleAnims.getOrPut(lineIdx) { Animatable(target) }
        if (animatable.targetValue != target) {
            val spec = if (config.isMinimal) tween<Float>(MINIMAL_TRANSITION_MS, easing = EASE_IN_OUT)
                else tween(LINE_TRANSITION_MS, easing = SCALE_EASING)
            coroutineScope.launch { animatable.animateTo(target, spec) }
        }
        return animatable.value
    }

    private fun applyBlur(lines: List<Line>, activeIndex: Int, processedPosition: Double) {
        for (i in lines.indices) {
            val state = elementState(processedPosition, lines[i].startMs, lines[i].endMs)
            val distance = abs(i - activeIndex)
            blurAmounts[i] = if (state == ElementState.Active || distance == 0) 0f
                else min(BLUR_MULTIPLIER * distance, MAX_BLUR)
        }
    }

    /** A Sung line keeps stepping while the next line is NotSung/Active, or when it's the last line. */
    private fun shouldFinalize(lines: List<Line>, lineIdx: Int, processedPosition: Double): Boolean {
        val next = lines.getOrNull(lineIdx + 1) ?: return true
        return elementState(processedPosition, next.startMs, next.endMs) != ElementState.Sung
    }

    /**
     * True once a settled line's springs have actually reached their final (fully-sung) values.
     * The array-adjacency check in [shouldFinalize] can flip false before that happens — e.g. a
     * background line's "next line" in list order may already be Sung from a much earlier point
     * in the song — which would otherwise freeze the line mid-transition, never fully lighting up.
     */
    private fun isSettled(states: List<WordAnimState>?): Boolean {
        if (states == null) return false
        return states.all { w ->
            w.gradientPosition >= 99.5f && w.glow < 0.02f &&
                (!w.isLetterGroup || w.letterStates.all { it.gradientPosition >= 99.5f && it.glow < 0.02f })
        }
    }

    // ── Word processing (Active line) ───────────────────────────────────────────────

    private fun animateWord(
        word: Word,
        processedPosition: Double,
        deltaTime: Float,
        lineIndex: Int,
        wordIndex: Int,
    ): WordAnimState {
        val springs = wordSprings(lineIndex, wordIndex)
        val wordState = elementState(processedPosition, word.startMs, word.endMs)
        val percentage = progress(processedPosition, word.startMs, word.endMs)
        val gradientBase = -20f

        val targetScale: Float
        val targetYOffset: Float
        val targetGlow: Float
        val targetGradientPos: Float
        when (wordState) {
            ElementState.Active -> {
                targetScale = scaleSpline.at(percentage)
                targetYOffset = yOffsetSpline.at(percentage)
                targetGlow = glowSpline.at(percentage)
                targetGradientPos = gradientBase + 120f * percentage
            }
            ElementState.NotSung -> {
                targetScale = scaleSpline.at(0f)
                targetYOffset = yOffsetSpline.at(0f)
                targetGlow = glowSpline.at(0f)
                targetGradientPos = gradientBase
            }
            ElementState.Sung -> {
                targetScale = scaleSpline.at(1f)
                targetYOffset = yOffsetSpline.at(1f)
                targetGlow = glowSpline.at(1f)
                targetGradientPos = 100f
            }
        }

        springs.scale.setGoal(targetScale)
        springs.yOffset.setGoal(targetYOffset)
        springs.glow.setGoal(targetGlow)
        val currentScale = springs.scale.step(deltaTime)
        val currentYOffset = springs.yOffset.step(deltaTime)
        val currentGlow = springs.glow.step(deltaTime)

        val isLetterGroup = word.isLetterGroup && word.letters.isNotEmpty()
        val letterStates = if (isLetterGroup) {
            animateLetters(word, wordState, processedPosition, deltaTime, lineIndex, wordIndex)
        } else emptyList()

        return WordAnimState(
            scale = currentScale,
            yOffset = currentYOffset,
            glow = currentGlow,
            gradientPosition = targetGradientPos,
            state = wordState,
            isLetterGroup = isLetterGroup,
            letterStates = letterStates,
        )
    }

    // ── Bloom ───────────────────────────────────────────────────────────────────────

    /** Bloom: each line's tone animation (0 at rest, 1 lit), and its size. */
    private val toneAnims = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()
    /** Bloom's animated rows: the tone of a line's whole-line rows (translation, plain romanization). */
    private val rowToneAnims = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()
    /** Each line's word runs ([LetterSynthesizer.runStarts]), for the words list they were worked out from. */
    private val runStartsCache = mutableMapOf<Int, Pair<List<Word>, IntArray>>()
    /** A sung line's words once they've come to rest, and from when: reused from then on. */
    private val bloomFinal = mutableMapOf<Int, Pair<Double, List<WordAnimState>>>()
    /** A line still to come is at rest, whatever the time: worked out once. */
    private val bloomRest = mutableMapOf<Int, List<WordAnimState>>()

    private fun clearBloom() {
        toneAnims.clear()
        rowToneAnims.clear()
        runStartsCache.clear()
        bloomFinal.clear()
        bloomRest.clear()
    }

    /**
     * How lit a line's text is under Bloom: lit while it's sung; after, a word-synced line dims
     * back to rest a moment after it ends, while a line-synced one fades up and down as a whole.
     */
    private fun bloomTone(lineIdx: Int, isActive: Boolean, lyricsType: LyricsType): Float {
        val target = if (isActive) 1f else 0f
        val anim = toneAnims.getOrPut(lineIdx) { Animatable(target) }
        if (anim.targetValue != target) {
            val lineSynced = lyricsType == LyricsType.Line
            when {
                target == 1f && !lineSynced -> coroutineScope.launch { anim.snapTo(1f) }
                target == 1f -> coroutineScope.launch { anim.animateTo(1f, tween(BloomMotion.LINE_LIGHT_MS, easing = BloomMotion.LINE_TONE_EASE)) }
                lineSynced -> coroutineScope.launch { anim.animateTo(0f, tween(BloomMotion.LINE_DIM_MS, easing = BloomMotion.LINE_TONE_EASE)) }
                else -> coroutineScope.launch {
                    anim.animateTo(0f, tween(BloomMotion.DIM_MS, delayMillis = BloomMotion.DIM_DELAY_MS, easing = BloomMotion.DIM_EASE))
                }
            }
        }
        return anim.value
    }

    /**
     * How lit a line's whole-line rows are under Bloom, whatever the line's own kind: they come up
     * in 0.3 s as the line starts, and go back down a moment after it ends, as a word-synced
     * line's words do.
     */
    private fun bloomRowTone(lineIdx: Int, isActive: Boolean): Float {
        val target = if (isActive) 1f else 0f
        val anim = rowToneAnims.getOrPut(lineIdx) { Animatable(target) }
        if (anim.targetValue != target) {
            val spec = if (isActive) tween<Float>(BloomMotion.LINE_LIGHT_MS, easing = BloomMotion.LINE_TONE_EASE)
                else tween(BloomMotion.DIM_MS, delayMillis = BloomMotion.DIM_DELAY_MS, easing = BloomMotion.DIM_EASE)
            coroutineScope.launch { anim.animateTo(target, spec) }
        }
        return anim.value
    }

    /**
     * The animated romanization row under Bloom: its pieces run like a line's words ([bloomLine]),
     * from the line's start until they have settled back down after its end. Empty before and after.
     */
    private fun bloomRow(
        lineIdx: Int, words: List<Word>, t: Double, line: Line, lineState: ElementState,
        geometryOf: ((Int) -> LineSweepGeometry?)?, endMs: Long,
    ): List<WordAnimState> {
        if (lineState == ElementState.NotSung) return emptyList()
        val key = lineIdx + ROW_KEY
        val states = bloomLine(key, words, t, line, geometryOf?.let { f -> { k: Int -> f(k - ROW_KEY) } }, endMs)
        // Settled and sung: the plain row says the same.
        return if (lineState == ElementState.Sung && bloomFinal.containsKey(key)) emptyList() else states
    }

    /** A line's size under Bloom: full while it's [focused], a little smaller at rest. */
    private fun bloomScale(lineIdx: Int, focused: Boolean): Float {
        val target = if (focused) 1f else BloomMotion.INACTIVE_SCALE
        val anim = lineScaleAnims.getOrPut(lineIdx) { Animatable(target) }
        if (anim.targetValue != target) {
            val spec = if (focused) tween<Float>(BloomMotion.SCALE_IN_MS, BloomMotion.SCALE_IN_DELAY_MS, BloomMotion.SCALE_EASE)
                else tween(BloomMotion.SCALE_OUT_MS, BloomMotion.SCALE_OUT_DELAY_MS, BloomMotion.SCALE_EASE)
            coroutineScope.launch { anim.animateTo(target, spec) }
        }
        return anim.value
    }

    /**
     * A word-synced line under Bloom at [t] (-infinity before it starts), from the time alone. One
     * soft edge runs along the whole line; words lift as they start and never above the one before
     * them; a held word's letters swell one after another, a word sung in syllables as one. Once the
     * line ends, everything settles back down on the spring it lifted on.
     */
    private fun bloomLine(
        key: Int, words: List<Word>, t: Double, line: Line, geometryOf: ((Int) -> LineSweepGeometry?)?,
        /** When the line counts as sung, after which its words settle. */
        endMs: Long = line.endMs,
    ): List<WordAnimState> {
        if (t == Double.NEGATIVE_INFINITY) bloomRest[key]?.let { return it }
        // Settling only moves on with the time, so the rest pose holds from when it was first
        // reached; earlier (a seek back) the line settles again.
        if (t < endMs) bloomFinal.remove(key)
        else bloomFinal[key]?.let { (from, states) -> if (t >= from) return states }
        val geometry = geometryOf?.invoke(key)
        val n = words.size
        val fits = geometry != null && geometry.wordWidths.size == n && geometry.fontPx > 0f
        val fontPx = geometry?.fontPx ?: 0f
        val fade = if (fits) fontPx * BloomMotion.FADE_EM else 0f
        val runStarts = runStartsCache[key]?.takeIf { it.first === words }?.second
            ?: LetterSynthesizer.runStarts(words).also { runStartsCache[key] = words to it }
        fun isGroup(i: Int) = words[i].isLetterGroup && words[i].letters.isNotEmpty()

        // The words laid end to end on one strip, each box holding the space after it.
        val x = FloatArray(n)
        val spans = arrayOfNulls<BloomMotion.Span>(n)
        if (fits) {
            val g = geometry!!
            val box = FloatArray(n) { g.wordWidths[it] + if (it < n - 1 && !words[it + 1].isPartOfWord) g.gap else 0f }
            var run = 0f
            for (i in 0 until n) { x[i] = run; run += box[i] }
            // The letters of the whole word each word belongs to.
            val wordLetters = IntArray(n)
            for (i in 0 until n) wordLetters[runStarts[i]] += g.letterCounts.getOrElse(i) { 0 }
            var prev = -1
            for (i in 0 until n) {
                if (box[i] <= 0f) continue
                val rowStart = prev < 0 || g.rows.getOrElse(i) { 0 } > g.rows.getOrElse(prev) { 0 }
                val rowEnd = i == n - 1 || g.rows.getOrElse(i + 1) { 0 } > g.rows.getOrElse(i) { 0 }
                spans[i] = BloomMotion.Span(x[i], box[i],
                    BloomMotion.reach(wordLetters[runStarts[i]], isGroup(i), rowEnd), rowStart, rowEnd)
                prev = i
            }
        }
        // A merged word's letters keep their syllables' pace: the light crosses each in its time.
        fun letterX(i: Int): FloatArray? = geometry?.letterWidths?.getOrNull(i)?.takeIf { fits && it.size == words[i].letters.size }
        val edges = if (!fits) DoubleArray(n) { Double.NEGATIVE_INFINITY } else BloomMotion.sweepEdges(
            spans, LongArray(n) { words[it].startMs }, LongArray(n) { words[it].endMs }, fade, t,
        ) { i ->
            val word = words[i]
            val widths = letterX(i)
            if (!config.syllablePace || word.syllables.size < 2 || !isGroup(i) || widths == null) null
            else {
                val starts = FloatArray(widths.size)
                for (j in 1 until widths.size) starts[j] = starts[j - 1] + widths[j - 1]
                BloomMotion.pacedShare(LongArray(word.letters.size) { word.letters[it].startMs },
                    LongArray(word.letters.size) { word.letters[it].endMs }, starts, widths, t)
            }
        }

        // Once the line ends its words settle back down.
        val ended = t >= endMs
        val liftLeft = if (ended) 1.0 - BloomMotion.lift(t - endMs) else 1.0
        // A word sung in several syllables swells as one: worked out once over all its letters.
        val flames = HashMap<Int, BloomMotion.Flame>()
        val flameOffsets = IntArray(n)
        fun flameOf(i: Int): BloomMotion.Flame {
            val first = runStarts[i]
            return flames.getOrPut(first) {
                var last = first
                while (last + 1 < n && runStarts[last + 1] == first) last++
                val members = (first..last).filter(::isGroup)
                var offset = 0
                val widths = ArrayList<Float>()
                for (m in members) {
                    flameOffsets[m] = offset
                    offset += words[m].letters.size
                    val w = letterX(m)
                    words[m].letters.indices.forEach { widths += if (w != null && fontPx > 0f) w[it] / fontPx else 0f }
                }
                val head = words[members.first()]
                val start = head.letters.first().startMs
                val end = members.maxOf { words[it].endMs }
                BloomMotion.flame(t - start, end - head.startMs, offset, widths.toFloatArray())
            }
        }

        var settled = ended && liftLeft == 0.0 && t >= (words.maxOfOrNull { it.endMs } ?: 0L) + BloomMotion.RUSH_MS
        var floatCap = 1.0
        val states = words.mapIndexed { i, word ->
            val state = elementState(t, word.startMs, word.endMs)
            val swept = spans[i] != null
            val width = if (fits) geometry!!.wordWidths[i] else 0f
            val wordPos = if (swept) BloomMotion.gradientPosition(edges[i], x[i], width, fade)
                else -50f + 150f * progress(t, word.startMs, word.endMs)
            if (!isGroup(i)) {
                floatCap = minOf(floatCap, BloomMotion.lift(t - word.startMs))
                return@mapIndexed WordAnimState(scale = 1f, yOffset = (-BloomMotion.LIFT_EM * floatCap * liftLeft).toFloat(),
                    glow = 0f, gradientPosition = wordPos, state = state)
            }
            val whole = flameOf(i)
            val flame = whole.slice(flameOffsets[i], word.letters.size)
            if (whole.rest != 1) settled = false
            val widths = letterX(i)
            var lx = 0f
            val letters = word.letters.mapIndexed { li, letter ->
                // The line's edge where it can be measured; otherwise one letter after another.
                val position = if (widths != null) {
                    BloomMotion.gradientPosition(edges[i], x[i] + lx, widths[li], fade).also { lx += widths[li] }
                } else -50f + 150f * progress(t, letter.startMs, letter.endMs)
                LetterAnimState(
                    gradientPosition = position,
                    scale = flame.scale(li),
                    yOffset = (flame.y(li) * liftLeft).toFloat(),
                    xOffset = flame.x(li),
                    glow = flame.glow(li) * BloomMotion.letterTone(position),
                    glowRadius = BloomMotion.GLOW_RADIUS_EM,
                )
            }
            // A split word's letters lift one by one instead of the word.
            WordAnimState(scale = 1f, yOffset = 0f, glow = 0f, gradientPosition = wordPos, state = state,
                isLetterGroup = true, letterStates = letters)
        }
        if (settled) bloomFinal[key] = t to states
        if (t == Double.NEGATIVE_INFINITY) bloomRest[key] = states
        return states
    }

    /** An interlude's three dots under Bloom: they fade in, fill in turn, and leave with the group. */
    private fun bloomDots(line: Line, t: Double): List<WordAnimState> {
        val elapsed = t - line.startMs
        val group = BloomMotion.dotGroup(elapsed, line.duration, PRE_HIDDEN_DOT_LINE_MS)
        val state = elementState(t, line.startMs, line.endMs)
        return List(3) { index ->
            WordAnimState(
                scale = 1f, yOffset = 0f,
                // 'glow' carries a dot's opacity for the interlude draw.
                glow = BloomMotion.dotOpacity(elapsed, line.duration, index, 3, PRE_HIDDEN_DOT_LINE_MS) * group.opacity,
                gradientPosition = 0f, state = state,
            )
        }
    }

    private fun animateLetters(
        word: Word,
        wordState: ElementState,
        processedPosition: Double,
        deltaTime: Float,
        lineIndex: Int,
        wordIndex: Int,
    ): List<LetterAnimState> {
        val gradientBase = -20f

        // 4b/4c: NotSung/Sung word inside an Active line — letters chase resting/final targets.
        if (wordState != ElementState.Active) {
            val t = if (wordState == ElementState.NotSung) 0f else 1f
            val gradient = if (wordState == ElementState.NotSung) gradientBase else 100f
            return word.letters.mapIndexed { li, _ ->
                val s = letterSprings(lineIndex, wordIndex, li)
                s.scale.setGoal(letterScaleSpline.at(t))
                s.yOffset.setGoal(letterYOffsetSpline.at(t))
                s.glow.setGoal(glowSpline.at(t))
                LetterAnimState(
                    gradientPosition = gradient,
                    scale = s.scale.step(deltaTime),
                    yOffset = s.yOffset.step(deltaTime),
                    glow = s.glow.step(deltaTime),
                )
            }
        }

        // 4a: Active word — two-phase per-letter targets.
        var activeLetterIndex = -1
        var activeLetterPercentage = 0f
        for (i in word.letters.indices) {
            val l = word.letters[i]
            if (elementState(processedPosition, l.startMs, l.endMs) == ElementState.Active) {
                activeLetterIndex = i
                activeLetterPercentage = progress(processedPosition, l.startMs, l.endMs)
                break
            }
        }

        return word.letters.mapIndexed { k, letter ->
            val letterState = elementState(processedPosition, letter.startMs, letter.endMs)

            // Defaults: resting.
            var targetScale = letterScaleSpline.at(0f)
            var targetYOffset = letterYOffsetSpline.at(0f)
            var targetGlow = glowSpline.at(0f)

            if (activeLetterIndex != -1) {
                // Blended by the active letter's own progress.
                val baseScale = letterScaleSpline.at(activeLetterPercentage)
                val baseYOffset = letterYOffsetSpline.at(activeLetterPercentage)
                val baseGlow = glowSpline.at(activeLetterPercentage)

                val restingScale = letterScaleSpline.at(0f)
                val restingYOffset = letterYOffsetSpline.at(0f)
                val restingGlow = glowSpline.at(0f)

                val distance = abs(k - activeLetterIndex).toFloat()
                val falloff = (1f / (1f + distance.pow(2.8f))).coerceAtLeast(0f)
                val glowFalloff = (1f / (1f + distance * 0.9f)).coerceAtLeast(0f)

                targetScale = restingScale + (baseScale - restingScale) * falloff
                targetYOffset = restingYOffset + (baseYOffset - restingYOffset) * falloff
                targetGlow = restingGlow + (baseGlow - restingGlow) * glowFalloff
            }

            // Overrides: NotSung letters reset to resting; Sung letters in a word with no active
            // letter hold GlowSpline.at(SungLetterGlow).
            if (letterState == ElementState.NotSung) {
                targetScale = letterScaleSpline.at(0f)
                targetYOffset = letterYOffsetSpline.at(0f)
                targetGlow = glowSpline.at(0f)
            } else if (letterState == ElementState.Sung && activeLetterIndex == -1) {
                targetGlow = glowSpline.at(SUNG_LETTER_GLOW)
            }

            // Gradient: only the actual active letter sweeps (eased); the rest hold base/final.
            val targetGradient = when (letterState) {
                ElementState.NotSung -> gradientBase
                ElementState.Sung -> 100f
                ElementState.Active -> when {
                    k == activeLetterIndex -> gradientBase + 120f * easeSinOut(activeLetterPercentage)
                    else -> gradientBase
                }
            }

            val s = letterSprings(lineIndex, wordIndex, k)
            s.scale.setGoal(targetScale)
            s.yOffset.setGoal(targetYOffset)
            s.glow.setGoal(targetGlow)

            LetterAnimState(
                gradientPosition = targetGradient,
                scale = s.scale.step(deltaTime),
                yOffset = s.yOffset.step(deltaTime),
                glow = s.glow.step(deltaTime),
            )
        }
    }

    /** The Sung-line settle pass (`checkNextLine`): step everything toward its final state. */
    private fun finalizeWord(
        word: Word,
        deltaTime: Float,
        lineIndex: Int,
        wordIndex: Int,
    ): WordAnimState {
        val springs = wordSprings(lineIndex, wordIndex)
        springs.scale.setGoal(scaleSpline.at(1f))
        springs.yOffset.setGoal(yOffsetSpline.at(1f))
        springs.glow.setGoal(glowSpline.at(1f))
        val currentScale = springs.scale.step(deltaTime)
        val currentYOffset = springs.yOffset.step(deltaTime)
        val currentGlow = springs.glow.step(deltaTime)

        val isLetterGroup = word.isLetterGroup && word.letters.isNotEmpty()
        val letterStates = if (isLetterGroup) {
            word.letters.mapIndexed { li, _ ->
                val s = letterSprings(lineIndex, wordIndex, li)
                s.scale.setGoal(letterScaleSpline.at(1f))
                s.yOffset.setGoal(letterYOffsetSpline.at(1f))
                s.glow.setGoal(glowSpline.at(1f))
                LetterAnimState(
                    gradientPosition = 100f,
                    scale = s.scale.step(deltaTime),
                    yOffset = s.yOffset.step(deltaTime),
                    glow = s.glow.step(deltaTime),
                )
            }
        } else emptyList()

        return WordAnimState(
            scale = currentScale,
            yOffset = currentYOffset,
            glow = currentGlow,
            gradientPosition = 100f,
            state = ElementState.Sung,
            isLetterGroup = isLetterGroup,
            letterStates = letterStates,
        )
    }

    // ── Interlude dots ──────────────────────────────────────────────────────────────

    private fun animateInterludeDots(
        line: Line,
        processedPosition: Double,
        deltaTime: Float,
        lineIndex: Int,
        finalize: Boolean,
    ): List<WordAnimState> {
        // Dot timing: each dot ends slightly early (applier: dotPadding = -550/3 per dot).
        val span = ((line.duration - 550L).coerceAtLeast(90L)).toFloat() / 3f
        return (0 until 3).map { dotIdx ->
            val dotStart = line.startMs + (dotIdx * span).toLong()
            val dotEnd = line.startMs + ((dotIdx + 1) * span).toLong()
            val state = if (finalize) ElementState.Sung
                else elementState(processedPosition, dotStart, dotEnd)
            val pct = progress(processedPosition, dotStart, dotEnd)

            fun sample(sp: CubicSplineInterpolator) = when (state) {
                ElementState.Active -> sp.at(pct)
                ElementState.NotSung -> sp.at(0f)
                ElementState.Sung -> sp.at(1f)
            }

            val key = lineIndex.toLong() * 3L + dotIdx.toLong()
            val springs = dotSpringsMap.getOrPut(key) { DotSprings() }
            springs.scale.setGoal(sample(dotScaleSpline))
            springs.yOffset.setGoal(sample(dotYOffsetSpline))
            springs.glow.setGoal(sample(dotGlowSpline))
            springs.opacity.setGoal(sample(dotOpacitySpline))

            WordAnimState(
                scale = springs.scale.step(deltaTime),
                yOffset = springs.yOffset.step(deltaTime),
                glow = springs.opacity.step(deltaTime),   // 'glow' carries dot opacity for the base draw
                dotGlow = springs.glow.step(deltaTime),
                gradientPosition = if (state == ElementState.Sung) 100f else 0f,
                state = state,
            )
        }
    }

    // ── Frozen-state synthesis ──────────────────────────────────────────────────────

    /** Initial resting state for a word that has never been animated (reference build-time styles). */
    private fun restingWordState(word: Word): WordAnimState {
        val gradientBase = -20f
        val isLetterGroup = word.isLetterGroup && word.letters.isNotEmpty()
        return WordAnimState(
            scale = scaleSpline.at(0f),
            yOffset = yOffsetSpline.at(0f),
            glow = 0f,
            gradientPosition = gradientBase,
            state = ElementState.NotSung,
            isLetterGroup = isLetterGroup,
            letterStates = if (isLetterGroup) word.letters.map {
                LetterAnimState(
                    gradientPosition = gradientBase,
                    scale = letterScaleSpline.at(0f),
                    yOffset = letterYOffsetSpline.at(0f),
                    glow = 0f,
                )
            } else emptyList(),
        )
    }

    /** Songwriter credits render as plain, fully-swept text (they aren't lyrics). */
    private fun songwriterWordState(word: Word): WordAnimState = WordAnimState(
        scale = 1f,
        yOffset = 0f,
        glow = 0f,
        gradientPosition = 100f,
        state = ElementState.Sung,
        isLetterGroup = false,
        letterStates = emptyList(),
    )

    /**
     * The word-by-word romanization highlight of an active line, eased over time: a word being
     * sung is lit whole (level 2) with a glow, then settles to the line's lit colour (1) once
     * sung; the words ahead stay dim (0). Lighting up is quick and settling slow.
     */
    private fun stepRowHighlight(lineIdx: Int, noteLine: Line, t: Double, deltaTime: Float): List<Float> {
        val words = noteLine.words
        // Per word: lit level, its speed, glow, its speed. Each eases to its target without
        // overshoot, so a word fades up as it's sung and fades back down after, never snaps.
        var eased = rowHighlights[lineIdx]
        if (eased == null || eased.size != words.size * 4) {
            eased = FloatArray(words.size * 4)
            rowHighlights[lineIdx] = eased
        }
        fun ease(at: Int, target: Float, omega: Float) {
            val accel = omega * omega * (target - eased[at]) - 2f * omega * eased[at + 1]
            eased[at + 1] += accel * deltaTime
            eased[at] = (eased[at] + eased[at + 1] * deltaTime).coerceIn(0f, 2f)
        }
        val out = FloatArray(words.size * 2)
        words.forEachIndexed { i, word ->
            val (start, end) = LetterSynthesizer.sungSpan(word, config)
            val state = elementState(t, start, end)
            val p = progress(t, start, end)
            val litTarget = when (state) { ElementState.Active -> 2f; ElementState.Sung -> 1f; ElementState.NotSung -> 0f }
            val glowTarget = when {
                state != ElementState.Active -> 0f
                p < 0.15f -> p / 0.15f
                p <= 0.6f -> 1f
                else -> (1f - p) / 0.4f
            }
            // Fading up takes about a third of a second, settling back to the line's colour about a second.
            ease(i * 4, litTarget, if (litTarget == 2f) 9f else if (litTarget == 1f) 3.5f else 6f)
            ease(i * 4 + 2, glowTarget, 8f)
            out[i * 2] = eased[i * 4]
            out[i * 2 + 1] = eased[i * 4 + 2].coerceAtLeast(0f)
        }
        return out.asList()
    }

    // ── Spring lookup ───────────────────────────────────────────────────────────────

    /** Drops what a line remembers of being sung, so it animates from the start the next time. */
    private fun forgetLine(lineIdx: Int) {
        cachedWordStates.remove(lineIdx)
        cachedRowStates.remove(lineIdx)
        rowHighlights.remove(lineIdx)
        cachedLineGradient.remove(lineIdx)
        cachedLineGlow.remove(lineIdx)
        lineGlowSprings.remove(lineIdx)
        wordSpringsMap.keys.removeAll { it / 100000L == lineIdx.toLong() || it / 100000L == (lineIdx + ROW_KEY).toLong() }
        letterSpringsMap.keys.removeAll { it / 1000000L == lineIdx.toLong() || it / 1000000L == (lineIdx + ROW_KEY).toLong() }
        dotSpringsMap.keys.removeAll { it / 3L == lineIdx.toLong() }
        bloomFinal.remove(lineIdx)
    }

    private fun wordSprings(lineIndex: Int, wordIndex: Int): WordSprings =
        wordSpringsMap.getOrPut(lineIndex.toLong() * 100000L + wordIndex.toLong()) { WordSprings() }

    private fun letterSprings(lineIndex: Int, wordIndex: Int, letterIndex: Int): LetterSprings =
        letterSpringsMap.getOrPut(
            lineIndex.toLong() * 1000000L + wordIndex.toLong() * 100L + letterIndex.toLong()
        ) { LetterSprings() }

    // ── Helpers ─────────────────────────────────────────────────────────────────────

    /** Where a line's own singing ends: its time may run on to the next line. */
    private fun sungEnd(line: Line): Long = line.words.maxOfOrNull { it.endMs } ?: line.endMs

    private fun elementState(t: Double, startMs: Long, endMs: Long): ElementState = when {
        t < startMs -> ElementState.NotSung
        t >= endMs -> ElementState.Sung
        else -> ElementState.Active
    }

    private fun progress(t: Double, startMs: Long, endMs: Long): Float {
        if (t <= startMs) return 0f
        if (t >= endMs) return 1f
        return (t - startMs).toFloat() / (endMs - startMs).toFloat()
    }

    private fun easeSinOut(t: Float): Float = sin(t * (PI.toFloat() / 2f))
}
