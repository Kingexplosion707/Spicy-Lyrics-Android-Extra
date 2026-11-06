package com.tx24.spicyplayer.haptics

import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import com.tx24.spicyplayer.network.data.spotify.Rhythm
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What a music haptic marks. The first three follow the drums ([MusicHapticsStyle.Drums]), [Note]
 * the notes where there are no drums; [Beat] and [Downbeat] are the steady beat
 * ([MusicHapticsStyle.Beat]); [Accent] and [Drop] are the big moments in either.
 */
enum class MusicPulse { Kick, Snare, Note, Beat, Downbeat, Accent, Drop }

/** What the music haptics follow. */
enum class MusicHapticsStyle(val label: String) { Drums("The drums"), Beat("The beat") }

/**
 * One vibration in a song, at [atMs] into it, [strength] 0..1, with [roomMs] of quiet before the
 * next one (a long effect only fits where there is room for it). A [MusicPulse.Drop] starts its
 * swell at [atMs] and lands [DROP_RISE_MS] later.
 */
class MusicHaptic(val atMs: Long, val pulse: MusicPulse, val strength: Float, val roomMs: Long = Long.MAX_VALUE)

/** How long a [MusicPulse.Drop]'s swell runs before it lands. */
const val DROP_RISE_MS = 500L

/**
 * Turns a song's audio analysis into the vibrations that play along with it.
 *
 * [MusicHapticsStyle.Drums] feels what is played, from the song's [Rhythm]. A snare, a clap or an
 * acoustic kick starts a sound in nearly every frequency band at once, where a bass note or a
 * hi-hat starts one in a band or two, so the hits reaching most bands are drums, on the beat or off
 * it, and the more bands the harder. Each is a kick or a snare by its timbre against the hits
 * around it: a snare or clap sounds brighter than the kick. Most produced kicks (an 808, say) only
 * reach the lowest band, like the bass does; in a section with drums whose downbeats aren't
 * full-band hits, the kick must be one of those, so the loud, sharp onsets in the lowest band are
 * taken for it, and they join the hits the full-band ones are compared with (so a clap among 808s
 * sounds as snary as it is). Sections without full-band drum hits follow
 * the notes instead, lightly, from the smaller onsets: a piano's low notes are no kick. A song with
 * no rhythm in its analysis gets the steady beat.
 *
 * Judged one by one, a hit near a threshold lands on a different side of it from bar to bar, and
 * a groove that repeats would flicker. So each drum section is then played as its groove: what is
 * hit on a sixteenth in most of its bars is played in every bar, as a kick or a snare by what it
 * is most often, at its usual strength, wherever the audio has a sound there ([grooves]).
 *
 * [MusicHapticsStyle.Beat] is a steady pulse: every beat, firmer on each bar's first.
 *
 * Both mark drops (a section that comes in much louder than the one before, with a swell into the
 * bigger ones) and big hits, and both follow the loudness: quiet parts are faint, silence still.
 */
object MusicHapticScore {
    /** Beats this unsure are left out. */
    private const val MIN_BEAT_CONFIDENCE = 0.05f
    /** Pulses quieter than this (0..1) are left out. */
    private const val MIN_STRENGTH = 0.12f
    /** How far a bar may start from a beat and still make it a downbeat, in seconds. */
    private const val DOWNBEAT_WINDOW = 0.06f

    /** A segment starting this close to a beat is the sound on that beat, in seconds. */
    private const val ON_BEAT = 0.07f
    /** A section with hits reaching this many of the rhythm's bands surely has drums... */
    private const val FULL_BANDS = 6
    /** ...and within one with drums, a hit reaching this many is a drum. */
    private const val DRUM_BANDS = 5
    /** ...and one reaching this many, where there are no drums, a note. */
    private const val NOTE_BANDS = 4
    /** A section with as many full-band hits as this share of its beats has drums (a backbeat alone is half)... */
    private const val DRUM_SECTION_SHARE = 0.2f
    /**
     * ...and so does one with drum hits on this share of its beats, in a song with drums elsewhere,
     * no more than [DRUM_SECTION_QUIETER_DB] quieter than those: a mix whose drums seldom reach
     * every band (lo-fi, say) mustn't drop out of its drums halfway.
     */
    private const val DRUM_HIT_SHARE = 0.3f
    private const val DRUM_SECTION_QUIETER_DB = 3f
    /** A section with full-band hits on fewer than this share of its downbeats has a low kick... */
    private const val FULL_DOWNBEATS = 0.5f
    /** ...taken from onsets in the lowest band this loud (0..1)... */
    private const val LOW_KICK_LEVEL = 0.75f
    /** ...that rise at least this many dB. */
    private const val LOW_KICK_JUMP_DB = 8f
    /** A full-band hit this close to a downbeat is on it, in seconds. */
    private const val ON_DOWNBEAT = 0.05f
    /** How much snarier than the hits around it (in track standard deviations) a snare sounds. */
    private const val SNARE_CONTRAST = 0.3f
    /** The hits this close either side are what a hit's sound is compared with, in seconds. */
    private const val SNARE_WINDOW = 4f
    /** A hit takes the segment starting up to this long after it as its sound, in seconds. */
    private const val SOUND_LAG = 0.03f
    /** Notes (in sections without drums) at least this far apart, in seconds. */
    private const val NOTE_GAP = 0.15f

    /** A bar's slots: sixteenths. */
    private const val SLOTS = 16
    /** A hit this far (in slots) from a sixteenth is on it. */
    private const val ON_SLOT = 0.3f
    /** A section needs this many bars to have a groove. */
    private const val GROOVE_MIN_BARS = 4
    /** A sixteenth hit in this share of a section's bars is part of its groove. */
    private const val GROOVE_SHARE = 0.5f
    /** The groove plays only where a sound reaching this many bands starts within [GROOVE_SOUND_WINDOW] (seconds). */
    private const val GROOVE_SOUND_BANDS = 3
    private const val GROOVE_SOUND_WINDOW = 0.04f
    /** A hit off the groove (a fill, a crash) still plays when this strong. */
    private const val FILL_STRENGTH = 0.7f

    /** A section this many dB louder than the one before comes in with an accent... */
    private const val DROP_DB = 4f
    /** ...and with a swell into it from this many. */
    private const val RISE_DB = 6f
    /** A segment whose peak is this many dB over its start is a big hit... */
    private const val HIT_ATTACK_DB = 20f
    /** ...when it also peaks this many dB over the track's average loudness. */
    private const val HIT_OVER_TRACK_DB = 1f
    /** Following the drums, a hit at least this loud (0..1)... */
    private const val COMEBACK_LEVEL = 0.6f
    /** ...after this long with nothing louder than [QUIET_LEVEL] is an accent, in seconds. */
    private const val COMEBACK_PAUSE = 1f
    private const val QUIET_LEVEL = 0.2f
    /** Accents closer together than this blur into one, in seconds. */
    private const val MIN_ACCENT_GAP = 3f
    /** An accent takes the place of a pulse this close, in seconds. */
    private const val ACCENT_SNAP = 0.12f
    /** Pulses closer together than this blur on the motor; the weaker goes, in milliseconds. */
    private const val MIN_GAP_MS = 90L
    /** HapticBeat's fused streams: a lyric word's tick, a lyric line's thump, the beat grid. */
    private const val WORD_STRENGTH = 0.5f
    private const val LINE_STRENGTH = 0.95f
    private const val GRID_BEAT_STRENGTH = 0.6f
    private const val GRID_DOWNBEAT_STRENGTH = 0.9f
    /** Precision keeps only onsets at least this strong. */
    private const val PRECISION_MIN_STRENGTH = 0.30f

    fun build(analysis: AudioAnalysis, style: MusicHapticsStyle = MusicHapticsStyle.Drums): List<MusicHaptic> {
        val song = Song(analysis)
        val pulses = when (style) {
            MusicHapticsStyle.Drums -> drums(song)
            MusicHapticsStyle.Beat -> steadyBeat(song)
        }
        return finish(withAccents(pulses, song, style))
    }

    /**
     * HapticBeat's score: the analysis score ([build]) fused with the synced lyrics' word and
     * line times and the analysis's own beat grid, by [options]' timing, detection and style.
     *
     * Timing picks the base streams (Spotify: the score; Lyrics: the words and lines, or the
     * beat grid when there are none; Auto: the score plus the words and lines). Detection adds
     * fusion on top: Combined adds the words (or the score, when the timing is lyrics-only),
     * All in one adds everything, Precision keeps only strong onsets in the score, and Classic
     * adds nothing. The style then keeps only the kinds it fires, the sensitivity drops the
     * quiet ones, and [finish] thins what is left to one pulse per 90 ms.
     *
     * A steady rumble ([HapticBeatMode.Continuous]) has no score: it is empty here, and the
     * loop plays it instead.
     */
    fun buildWithBeat(
        analysis: AudioAnalysis,
        style: MusicHapticsStyle = MusicHapticsStyle.Drums,
        options: HapticBeatOptions = HapticBeatOptions(),
        wordTimes: List<Long> = emptyList(),
        lineTimes: List<Long> = emptyList(),
    ): List<MusicHaptic> {
        if (options.mode == HapticBeatMode.Continuous) return emptyList()
        val mode = options.mode
        var score = build(analysis, style)
        if (options.detection == HapticBeatDetection.Precision) {
            score = score.filter {
                (it.pulse == MusicPulse.Kick || it.pulse == MusicPulse.Snare ||
                    it.pulse == MusicPulse.Downbeat || it.pulse == MusicPulse.Accent ||
                    it.pulse == MusicPulse.Drop) && it.strength >= PRECISION_MIN_STRENGTH
            }
        }
        val words = wordTimes.map { MusicHaptic(it, MusicPulse.Note, WORD_STRENGTH) }
        val lines = lineTimes.map { MusicHaptic(it, MusicPulse.Accent, LINE_STRENGTH) }
        val grid = analysis.beats.filter { it.confidence >= MIN_BEAT_CONFIDENCE }.map { beat ->
            val atMs = (beat.start * 1000f).toLong()
            val downbeat = analysis.bars.any { kotlin.math.abs(it - beat.start) <= DOWNBEAT_WINDOW }
            if (downbeat) MusicHaptic(atMs, MusicPulse.Downbeat, GRID_DOWNBEAT_STRENGTH)
            else MusicHaptic(atMs, MusicPulse.Beat, GRID_BEAT_STRENGTH)
        }
        val includeScore = options.timing != HapticBeatTiming.Lyrics ||
            options.detection == HapticBeatDetection.Combined ||
            options.detection == HapticBeatDetection.AllInOne
        val includeWords = options.timing != HapticBeatTiming.Spotify ||
            options.detection == HapticBeatDetection.Combined ||
            options.detection == HapticBeatDetection.AllInOne
        val includeLines = includeWords
        val includeGrid = options.detection == HapticBeatDetection.AllInOne ||
            (options.timing == HapticBeatTiming.Lyrics && words.isEmpty() && lines.isEmpty())
        // Vocals-only (Apple-style): only the lyric streams, never the score or the grid.
        val vocalsOnly = options.vocalsOnly && mode == HapticBeatMode.Apple
        val cutoff = 0.5f * (1f - options.sensitivity / 100f)
        val out = ArrayList<MusicHaptic>()
        if (includeScore && !vocalsOnly) {
            out += score.filter { mode.allowsScore(it.pulse) && it.strength >= cutoff }
        }
        if (includeWords && mode.allowsWord()) {
            out += words.filter { it.strength >= cutoff }
        }
        if (includeLines && mode.allowsLine()) {
            out += lines.filter { it.strength >= cutoff }
        }
        if (includeGrid && !vocalsOnly) {
            out += grid.filter { mode.allowsScore(it.pulse) && it.strength >= cutoff }
        }
        return finish(out.distinctBy { it.atMs to it.pulse }.toMutableList())
    }

    // Drums

    private fun drums(song: Song): MutableList<MusicHaptic> {
        val rhythm = song.a.rhythm ?: return steadyBeat(song)
        val out = ArrayList<MusicHaptic>()
        val fullHits = rhythm.hits.filter { it.bandCount >= DRUM_BANDS }
        fun count(hits: List<Rhythm.Hit>, from: Float, to: Float, bands: Int) = hits.count { it.at >= from && it.at < to && it.bandCount >= bands }
        val fullTimes = fullHits.map { it.at }.toFloatArray()
        // Sections whose kick only reaches the lowest band: their downbeats aren't full-band hits.
        val lowKickSections = song.a.sections.map { section ->
            val downbeats = song.a.bars.filter { it >= section.start && it < section.start + section.duration }
            downbeats.isNotEmpty() &&
                downbeats.count { song.hasNear(fullTimes, it, ON_DOWNBEAT) } < FULL_DOWNBEATS * downbeats.size
        }
        fun inSection(flags: List<Boolean>, t: Float): Boolean {
            val s = song.sectionIndex(t)
            // Before the first section (or with none), go with the most of the song.
            return if (s >= 0) flags[s] else flags.count { it } * 2 >= flags.size
        }
        val beatsIn = song.a.sections.map { section -> song.beats.count { it.start >= section.start && it.start < section.start + section.duration } }
        val sureDrums = song.a.sections.mapIndexed { i, section ->
            beatsIn[i] > 0 && count(fullHits, section.start, section.start + section.duration, FULL_BANDS) >= DRUM_SECTION_SHARE * beatsIn[i]
        }
        val drumLoudness = song.a.sections.filterIndexed { i, _ -> sureDrums[i] }.map { it.loudness }.average().toFloat()
        val drumSections = song.a.sections.mapIndexed { i, section ->
            sureDrums[i] || (
                sureDrums.any { it } && beatsIn[i] > 0 && section.loudness >= drumLoudness - DRUM_SECTION_QUIETER_DB &&
                    count(fullHits, section.start, section.start + section.duration, DRUM_BANDS) >= DRUM_HIT_SHARE * beatsIn[i]
                )
        }
        fun drumsAt(t: Float) = inSection(drumSections, t)
        fun lowKickAt(t: Float) = drumsAt(t) && inSection(lowKickSections, t)
        val lowKicks = rhythm.hits.filter { hit ->
            hit.bands and 1 != 0 && hit.bandCount < DRUM_BANDS && lowKickAt(hit.at) &&
                song.segmentAt(hit.at + SOUND_LAG)?.let { song.level(it) >= LOW_KICK_LEVEL && song.jump(it) >= LOW_KICK_JUMP_DB } == true
        }

        val drumHits = (fullHits + lowKicks).sortedBy { it.at }
        val sounds = drumHits.map { song.segmentAt(it.at + SOUND_LAG) }
        drumHits.forEachIndexed { h, hit ->
            if (!drumsAt(hit.at)) return@forEachIndexed
            val seg = sounds[h] ?: return@forEachIndexed
            if (hit.bandCount < DRUM_BANDS) {
                out += drum(song, hit.at, MusicPulse.Kick, song.level(seg) * 0.8f)
                return@forEachIndexed
            }
            // The fewest bands a drum reaches is a light hit, every band a full one.
            val fullness = ((hit.bandCount - DRUM_BANDS + 1f) / (rhythm.bands.size - DRUM_BANDS + 1f)).coerceIn(0f, 1f)
            val snare = isSnare(song, drumHits, sounds, h)
            out += drum(song, hit.at, if (snare) MusicPulse.Snare else MusicPulse.Kick, song.level(seg) * (0.45f + 0.55f * fullness))
        }
        val sounds3 = rhythm.hits.filter { it.bandCount >= GROOVE_SOUND_BANDS }.map { it.at }.toFloatArray()
        val steady = grooves(out, song, sounds3, drumSections)
        out.clear()
        out += steady
        out.removeAll { it.strength < MIN_STRENGTH }

        // No drums: a light touch on each clear note.
        var lastNote = Float.NEGATIVE_INFINITY
        for (hit in rhythm.hits) {
            if (hit.bandCount < NOTE_BANDS || hit.at - lastNote < NOTE_GAP || drumsAt(hit.at)) continue
            val level = song.segmentAt(hit.at + SOUND_LAG)?.let(song::level) ?: continue
            if (level < 0.25f) continue
            out += MusicHaptic(ms(hit.at), MusicPulse.Note, 0.2f + 0.4f * level)
            lastNote = hit.at
        }
        return out
    }

    /**
     * [pulses] with each drum section's played as its groove. Its bars are laid on sixteenths; a
     * sixteenth hit in [GROOVE_SHARE] of them or more is the groove's, and plays in every bar, as
     * the pulse it is most often and at its median strength, at the hit if the bar has one there,
     * else at the sound starting nearest it in [sounds] (none: a break, and nothing plays). Hits off
     * the groove stay only as strong as a fill. Sections too short for a groove keep their hits.
     */
    private fun grooves(pulses: List<MusicHaptic>, song: Song, sounds: FloatArray, drumSections: List<Boolean>): List<MusicHaptic> {
        val bars = song.a.bars.sorted()
        val out = ArrayList<MusicHaptic>()
        val replaced = HashSet<MusicHaptic>()
        song.a.sections.forEachIndexed { s, section ->
            if (!drumSections[s]) return@forEachIndexed
            val end = section.start + section.duration
            val inSection = bars.indices.filter { it + 1 < bars.size && bars[it] >= section.start - 0.05f && bars[it] < end - 0.05f }
            if (inSection.size < GROOVE_MIN_BARS) return@forEachIndexed
            // Each bar's pulse on each sixteenth, the strongest where two land on one.
            val grid = inSection.map { b ->
                val start = bars[b]
                val length = bars[b + 1] - start
                val slots = arrayOfNulls<MusicHaptic>(SLOTS)
                for (p in pulses) {
                    if (p.pulse != MusicPulse.Kick && p.pulse != MusicPulse.Snare) continue
                    val at = (p.atMs / 1000f - start) / length * SLOTS
                    if (at < -0.5f || at >= SLOTS - 0.5f) continue
                    replaced += p
                    val slot = Math.round(at)
                    if (abs(at - slot) > ON_SLOT) {
                        if (p.strength >= FILL_STRENGTH) out += p
                        continue
                    }
                    if (slots[slot] == null || slots[slot]!!.strength < p.strength) slots[slot] = p
                }
                slots
            }
            for (slot in 0 until SLOTS) {
                val hits = grid.mapNotNull { it[slot] }
                if (hits.size < GROOVE_SHARE * grid.size) {
                    hits.filter { it.strength >= FILL_STRENGTH }.forEach { out += it }
                    continue
                }
                val pulse = if (hits.count { it.pulse == MusicPulse.Snare } * 2 > hits.size) MusicPulse.Snare else MusicPulse.Kick
                val strength = hits.map { it.strength }.sorted()[hits.size / 2]
                grid.forEachIndexed { i, slots ->
                    val at = slots[slot]?.atMs ?: run {
                        val b = inSection[i]
                        val t = bars[b] + (bars[b + 1] - bars[b]) * slot / SLOTS
                        song.nearest(sounds, t, GROOVE_SOUND_WINDOW)?.let(::ms)
                    } ?: return@forEachIndexed
                    out += MusicHaptic(at, pulse, strength)
                }
            }
        }
        return pulses.filterNot { it in replaced } + out
    }

    /** A drum hit at [at]; a kick on a downbeat lands a little firmer. */
    private fun drum(song: Song, at: Float, pulse: MusicPulse, strength: Float): MusicHaptic {
        val firmer = if (pulse == MusicPulse.Kick && song.isDownbeat(at)) strength * 1.15f else strength
        return MusicHaptic(ms(at), pulse, firmer.coerceAtMost(1f))
    }

    /** Whether drum hit [h] sounds snarier than the median of the hits around it. */
    private fun isSnare(song: Song, hits: List<Rhythm.Hit>, sounds: List<Int?>, h: Int): Boolean {
        val tone = sounds[h]?.let { song.snareTone[it] } ?: return false
        val around = ArrayList<Float>()
        var i = h - 1
        while (i >= 0 && hits[h].at - hits[i].at <= SNARE_WINDOW) { sounds[i]?.let { around += song.snareTone[it] }; i-- }
        i = h + 1
        while (i < hits.size && hits[i].at - hits[h].at <= SNARE_WINDOW) { sounds[i]?.let { around += song.snareTone[it] }; i++ }
        if (around.size < 3) return false
        around.sort()
        val mid = around.size / 2
        val median = if (around.size % 2 == 1) around[mid] else (around[mid - 1] + around[mid]) / 2f
        return tone - median > SNARE_CONTRAST
    }

    // Steady beat

    private fun steadyBeat(song: Song): MutableList<MusicHaptic> {
        val out = ArrayList<MusicHaptic>()
        for (beat in song.beats) {
            val level = song.levelAt(beat.start)
            if (level < MIN_STRENGTH) continue
            val sure = 0.7f + 0.3f * beat.confidence.coerceIn(0f, 1f)
            out += if (song.isDownbeat(beat.start)) {
                MusicHaptic(ms(beat.start), MusicPulse.Downbeat, (0.45f + 0.55f * level) * sure)
            } else {
                MusicHaptic(ms(beat.start), MusicPulse.Beat, (0.25f + 0.45f * level) * sure)
            }
        }
        return out
    }

    // Accents and drops

    private fun withAccents(pulses: MutableList<MusicHaptic>, song: Song, style: MusicHapticsStyle): MutableList<MusicHaptic> {
        val candidates = ArrayList<Accent>()
        song.a.sections.zipWithNext { before, section ->
            val jump = section.loudness - before.loudness
            if (jump >= DROP_DB) candidates += Accent(section.start, (0.7f + jump / 20f).coerceAtMost(1f), rise = jump >= RISE_DB)
        }
        var quietSince = 0f
        song.segments.forEachIndexed { i, seg ->
            val attack = seg.loudnessMax - seg.loudnessStart
            val bigHit = attack >= HIT_ATTACK_DB && seg.loudnessMax >= song.a.loudness + HIT_OVER_TRACK_DB
            // Following the drums, the kicks already carry the hits; only the music crashing back
            // in after a pause stands out.
            val comeback = song.level(i) >= COMEBACK_LEVEL && seg.start - quietSince >= COMEBACK_PAUSE
            if (if (style == MusicHapticsStyle.Drums) comeback else bigHit) {
                candidates += Accent(seg.peakAt, (0.6f + (attack - HIT_ATTACK_DB) / 40f).coerceIn(0.6f, 0.9f), rise = false)
            }
            if (song.level(i) >= QUIET_LEVEL) quietSince = seg.start + 0.001f
        }
        // Strongest first, so a drop wins over a hit beside it.
        val kept = ArrayList<Accent>()
        for (c in candidates.sortedByDescending { it.strength }) {
            if (kept.none { abs(it.at - c.at) < MIN_ACCENT_GAP }) kept += c
        }
        for (accent in kept) {
            // It lands on the pulse nearest it, if one is close.
            val near = pulses.filter { abs(it.atMs - ms(accent.at)) <= ms(ACCENT_SNAP) }.minByOrNull { abs(it.atMs - ms(accent.at)) }
            val at = near?.atMs ?: ms(accent.at)
            if (accent.rise) {
                // Nothing may cut the swell short.
                pulses.removeAll { it.atMs in (at - DROP_RISE_MS)..at + MIN_GAP_MS }
                pulses += MusicHaptic(at - DROP_RISE_MS, MusicPulse.Drop, accent.strength)
            } else {
                near?.let(pulses::remove)
                pulses += MusicHaptic(at, MusicPulse.Accent, accent.strength)
            }
        }
        return pulses
    }

    private class Accent(val at: Float, val strength: Float, val rise: Boolean)

    /** In time order, too-close pulses thinned (the stronger stays), each told how much room it has. */
    private fun finish(pulses: MutableList<MusicHaptic>): List<MusicHaptic> {
        pulses.sortBy { it.atMs }
        val thinned = ArrayList<MusicHaptic>(pulses.size)
        for (p in pulses) {
            val last = thinned.lastOrNull()
            // A drop's swell keeps its place; what follows it too closely goes.
            val lastEnd = last?.let { if (it.pulse == MusicPulse.Drop) it.atMs + DROP_RISE_MS else it.atMs }
            when {
                last == null || p.atMs - lastEnd!! >= MIN_GAP_MS -> thinned += p
                last.pulse != MusicPulse.Drop && p.pulse != MusicPulse.Drop && p.strength > last.strength -> thinned[thinned.lastIndex] = p
                p.pulse == MusicPulse.Drop -> thinned[thinned.lastIndex] = p
            }
        }
        return thinned.mapIndexed { i, p ->
            val next = thinned.getOrNull(i + 1)
            MusicHaptic(p.atMs, p.pulse, p.strength, next?.let { it.atMs - p.atMs } ?: Long.MAX_VALUE)
        }
    }

    /**
     * How loud [dB] is, 0..1, against the track's average: 15 dB under it is 0, 3 dB over it is 1
     * (a hit's peak usually sits a few dB over the average).
     */
    internal fun level(dB: Float, trackLoudness: Float): Float =
        ((dB - (trackLoudness - 15f)) / 18f).coerceIn(0f, 1f)

    private fun ms(seconds: Float) = (seconds * 1000f).toLong()

    /** The analysis, sorted and measured once. */
    private class Song(val a: AudioAnalysis) {
        val segments = a.segments.sortedBy { it.start }
        private val starts = FloatArray(segments.size) { segments[it].start }
        val beats = a.beats.filter { it.confidence >= MIN_BEAT_CONFIDENCE }
        private val bars = a.bars.sorted().toFloatArray()
        private val sectionStarts = a.sections.map { it.start }.toFloatArray()

        /**
         * How much each segment sounds like a snare rather than a kick, in track standard
         * deviations (timbre is only comparable within one song): brighter, higher on the fifth
         * coefficient, lower on the tenth. On real songs this picks the backbeat out of a
         * kick-snare pattern better than brightness alone.
         */
        val snareTone = standardised { it.brightness }.also { tone ->
            val fifth = standardised { it.timbre4 }
            val tenth = standardised { it.timbre9 }
            for (i in tone.indices) tone[i] += fifth[i] - tenth[i]
        }

        fun level(i: Int) = level(segments[i].loudnessMax, a.loudness)
        fun jump(i: Int) = segments[i].loudnessMax - segments[i].loudnessStart

        /** The time in [sorted] nearest [t], if one is within [window]. */
        fun nearest(sorted: FloatArray, t: Float, window: Float): Float? {
            val i = lastAtOrBefore(sorted, t)
            val before = sorted.getOrNull(i)?.takeIf { t - it <= window }
            val after = sorted.getOrNull(i + 1)?.takeIf { it - t <= window }
            return listOfNotNull(before, after).minByOrNull { abs(it - t) }
        }

        /** Whether [sorted] has a time within [window] of [t]. */
        fun hasNear(sorted: FloatArray, t: Float, window: Float): Boolean {
            val i = lastAtOrBefore(sorted, t + window)
            return i >= 0 && sorted[i] >= t - window
        }

        /** The segment playing at [t], or null before the first. */
        fun segmentAt(t: Float): Int? = lastAtOrBefore(starts, t).takeIf { it >= 0 }

        /** The loudest segment starting within [ON_BEAT] of [t], or null. */
        fun soundAt(t: Float): Int? {
            var i = lastAtOrBefore(starts, t + ON_BEAT)
            var best: Int? = null
            while (i >= 0 && starts[i] >= t - ON_BEAT) {
                if (best == null || segments[i].loudnessMax > segments[best].loudnessMax) best = i
                i--
            }
            return best
        }

        /** How loud it is at [t]: the sound on it, else the segment playing, else the section. */
        fun levelAt(t: Float): Float {
            soundAt(t)?.let { return level(it) }
            val i = lastAtOrBefore(starts, t)
            if (i >= 0) return level(i)
            return level(a.sections.getOrNull(sectionIndex(t))?.loudness ?: a.loudness, a.loudness)
        }

        fun isDownbeat(t: Float): Boolean {
            val i = lastAtOrBefore(bars, t + DOWNBEAT_WINDOW)
            return i >= 0 && abs(bars[i] - t) <= DOWNBEAT_WINDOW
        }

        fun sectionIndex(t: Float) = lastAtOrBefore(sectionStarts, t)

        private inline fun standardised(value: (AudioAnalysis.Segment) -> Float): FloatArray {
            if (segments.isEmpty()) return FloatArray(0)
            val mean = segments.sumOf { value(it).toDouble() } / segments.size
            val sd = sqrt(segments.sumOf { (value(it) - mean).let { d -> d * d } } / segments.size).takeIf { it > 1e-6 } ?: 1.0
            return FloatArray(segments.size) { ((value(segments[it]) - mean) / sd).toFloat() }
        }

        private fun lastAtOrBefore(sorted: FloatArray, t: Float): Int {
            var lo = 0
            var hi = sorted.size - 1
            var at = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid] <= t) { at = mid; lo = mid + 1 } else hi = mid - 1
            }
            return at
        }
    }
}
