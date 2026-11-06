package com.tx24.spicyplayer.ui.nowplaying

/**
 * What may fill the cover stage, in the order it's tried: a Spotify Canvas, then the record's
 * animated cover, then the still cover art. A song gets the first picked one it has; with none of
 * them, or nothing picked, there's no stage.
 */
enum class StageArt(val label: String) {
    Canvas("Spotify Canvas"),
    AnimatedCover("Animated cover"),
    CoverArt("Cover art");

    companion object {
        /** Unpicked: the Canvas, else the animated cover; a song with neither gets no stage. */
        val DEFAULT: Set<StageArt> = setOf(Canvas, AnimatedCover)

        /** What the old `coverStage` switch meant when on: the animated cover, else the still one. */
        val COVER_STAGE_ON: Set<StageArt> = setOf(AnimatedCover, CoverArt)

        /** Stored as names joined by commas, in list order; an unknown name is dropped. */
        fun parse(stored: String): Set<StageArt> =
            stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()

        fun store(arts: Set<StageArt>): String = entries.filter { it in arts }.joinToString(",") { it.name }
    }
}

/** Where the song's canvas lookup stands. */
sealed interface CanvasLookup {
    /** Not answered yet for this song. */
    data object Pending : CanvasLookup
    /** The song has no canvas, or none could be found. */
    data object None : CanvasLookup
    data class Found(val url: String) : CanvasLookup
}

/** What the cover stage shows. The still cover is always beneath, so [Still] adds nothing. */
sealed interface StagePicture {
    data class Canvas(val url: String) : StagePicture
    data class Animated(val url: String) : StagePicture
    data object Still : StagePicture
}

/** Picks the cover stage's picture from the setting and what the song has. */
object StageArtChoice {
    /** Whether the song's canvas should be looked up. Off in low performance mode, like the stage. */
    fun wantsCanvas(arts: Set<StageArt>, lowPerformance: Boolean): Boolean =
        StageArt.Canvas in arts && !lowPerformance

    /**
     * Whether the record's animated cover should be looked up for the stage: it's picked, and
     * Animated Cover (Now Playing) is on, as the stage shares the header's loop.
     */
    fun wantsAnimated(arts: Set<StageArt>, animatedCover: Boolean, lowPerformance: Boolean): Boolean =
        StageArt.AnimatedCover in arts && animatedCover && !lowPerformance

    /**
     * The stage's picture, or null for no stage: nothing picked that the song has. While a wanted
     * canvas is still being looked up, nothing later in the list shows, so the animated cover
     * doesn't play first and give way to the canvas a moment later; the still cover holds the
     * stage meanwhile when it's picked. [motionUrl] is the animated cover's stream when there is one.
     * With [animatedFirst] the animated cover goes before the canvas, which then only shows on songs
     * without one; while it's [motionPending], the canvas waits the same way.
     */
    fun pick(
        arts: Set<StageArt>,
        animatedCover: Boolean,
        lowPerformance: Boolean,
        canvas: CanvasLookup,
        motionUrl: String?,
        animatedFirst: Boolean = false,
        motionPending: Boolean = false,
    ): StagePicture? {
        val still = StagePicture.Still.takeIf { StageArt.CoverArt in arts }
        if (animatedFirst && wantsAnimated(arts, animatedCover, lowPerformance)) {
            if (motionUrl != null) return StagePicture.Animated(motionUrl)
            if (motionPending) return still
        }
        if (wantsCanvas(arts, lowPerformance)) {
            when (canvas) {
                is CanvasLookup.Found -> return StagePicture.Canvas(canvas.url)
                CanvasLookup.Pending -> return still
                CanvasLookup.None -> Unit
            }
        }
        if (motionUrl != null && wantsAnimated(arts, animatedCover, lowPerformance)) return StagePicture.Animated(motionUrl)
        return still
    }
}
