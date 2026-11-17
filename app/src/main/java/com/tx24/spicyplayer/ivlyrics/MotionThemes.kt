package com.tx24.spicyplayer.ivlyrics

import com.tx24.spicyplayer.network.data.ItunesGenre
import com.tx24.spicyplayer.network.data.SongGenre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A look for motion lyrics: the pools the page's director picks each shot's composition,
 * entrance, word effect, accent, typeface and background shapes from (see
 * ivlyrics/src/main/assets/motion/SpicyThemes.js), with its own colours and mood.
 */
internal data class MotionThemeSpec(
    val mood: String,
    val palette: List<String>,
    val layouts: List<String>,
    val enters: List<String>,
    val fx: List<String>,
    val accents: List<String>,
    val types: List<String>,
    val motifs: List<String> = emptyList(),
    val upper: Boolean? = null,
    val bars: Boolean? = null,
    val rule: Boolean? = null,
    val serif: Boolean? = null,
    /** How far shots lean, against the director's own (1). */
    val tilt: Float = 1f,
)

/** Motion lyrics' look (Settings → Lyrics → Motion lyrics theme). */
enum class MotionTheme(val label: String, internal val spec: MotionThemeSpec?) {
    /** The song's genre picks one of the themes below; Classic where it isn't known. */
    Auto("Auto (by genre)", null),
    /** ivLyrics' own direction, in the cover's colours. */
    Classic("Classic", null),
    Bubblegum("Bubblegum", MotionThemeSpec(
        mood = "playful", palette = listOf("#ff5fa2", "#7ad7ff"),
        layouts = listOf("cards", "collage", "zigzag", "carousel", "spread", "arch"),
        enters = listOf("roll", "flip", "drop", "spin", "panel", "orbit"),
        fx = listOf("pop", "bounce", "assemble", "wave", "flip", "spin"),
        accents = listOf("sticker", "circle", "mark", "spark", "color", "gradient"),
        types = listOf("round"), motifs = listOf("heart", "star", "sparkle", "flower", "dots"),
    )),
    Street("Street", MotionThemeSpec(
        mood = "intense", palette = listOf("#ffd400", "#ff3b30"),
        layouts = listOf("poster", "wordwall", "stairs", "split", "wall", "slot"),
        enters = listOf("cut", "drop", "whip", "panel", "push"),
        fx = listOf("slam", "pop", "stretch", "glitch", "bounce", "zoom"),
        accents = listOf("stamp", "sticker", "giant", "extrude", "scribble", "bracket"),
        types = listOf("impact"), motifs = listOf("crown", "burst", "arrow", "scribble", "zigzag", "cross"),
        upper = true, bars = true, tilt = 1.3f,
    )),
    Velvet("Velvet", MotionThemeSpec(
        mood = "warm", palette = listOf("#c86bff", "#ff7aa8"),
        layouts = listOf("hero", "column", "swell", "crawl", "arch"),
        enters = listOf("fade", "rise", "orbit", "pull"),
        fx = listOf("blur", "rise", "wave", "wipe"),
        accents = listOf("script", "glow", "gradient", "underline"),
        types = listOf("serif", "round"), motifs = listOf("heart", "moon", "halo", "sparkle"),
        upper = false, tilt = 0.6f,
    )),
    Stadium("Stadium", MotionThemeSpec(
        mood = "energetic", palette = listOf("#ff4d00", "#ffd166"),
        layouts = listOf("wall", "tower", "poster", "pillar", "split", "stairs"),
        enters = listOf("drop", "push", "tumble", "roll", "cut"),
        fx = listOf("slam", "zoom", "drop", "stretch", "pop"),
        accents = listOf("extrude", "giant", "stamp", "outline"),
        types = listOf("impact"), motifs = listOf("bolt", "flame", "star", "burst"),
        upper = true, rule = true, tilt = 1.2f,
    )),
    Inferno("Inferno", MotionThemeSpec(
        mood = "dark", palette = listOf("#ff1a1a", "#ff8a00"),
        layouts = listOf("tunnel", "pillar", "tower", "wall", "wordwall"),
        enters = listOf("through", "tumble", "spin", "cut", "drop"),
        fx = listOf("glitch", "slam", "flicker", "zoom", "stretch"),
        accents = listOf("outline", "extrude", "scribble", "giant", "stamp"),
        types = listOf("impact"), motifs = listOf("flame", "cross", "bolt", "eye"),
        upper = true, bars = true, tilt = 1.6f,
    )),
    Daydream("Daydream", MotionThemeSpec(
        mood = "dreamy", palette = listOf("#9ad1ff", "#ffc6a8"),
        layouts = listOf("collage", "crawl", "stairs", "spiral", "carousel", "strip"),
        enters = listOf("fade", "orbit", "pan", "roll", "pull"),
        fx = listOf("blur", "type", "wave", "wipe", "track", "rise"),
        accents = listOf("scribble", "underline", "circle", "script", "mark"),
        types = listOf("round", "serif"), motifs = listOf("cloud", "leaf", "moon", "spiral", "wave"),
        upper = false, tilt = 0.8f,
    )),
    NeonNight("Neon Night", MotionThemeSpec(
        mood = "energetic", palette = listOf("#ff2bd6", "#22e6ff"),
        layouts = listOf("tunnel", "radial", "strip", "reel", "wordwall", "slot"),
        enters = listOf("whip", "through", "spin", "cut", "push"),
        fx = listOf("glitch", "flicker", "zoom", "slam", "stretch", "track"),
        accents = listOf("glow", "outline", "gradient", "trail"),
        types = listOf("impact"), motifs = listOf("bolt", "burst", "zigzag", "bars", "sparkle"),
        upper = true, tilt = 1.2f,
    )),
    Campfire("Campfire", MotionThemeSpec(
        mood = "warm", palette = listOf("#ffb347", "#d98c5f"),
        layouts = listOf("column", "hero", "stairs", "crawl", "arch"),
        enters = listOf("fade", "rise", "pan", "pull"),
        fx = listOf("type", "rise", "wipe", "assemble"),
        accents = listOf("underline", "script", "mark", "circle"),
        types = listOf("serif", "round"), motifs = listOf("sun", "leaf", "flame", "star"),
        upper = false, rule = true, tilt = 0.6f,
    )),
    Fiesta("Fiesta", MotionThemeSpec(
        mood = "playful", palette = listOf("#ff6b35", "#00c2a8"),
        layouts = listOf("zigzag", "radial", "carousel", "cards", "spread", "swell"),
        enters = listOf("spin", "roll", "orbit", "whip", "flip"),
        fx = listOf("bounce", "pop", "wave", "spin", "slide"),
        accents = listOf("sticker", "color", "spark", "gradient", "circle"),
        types = listOf("round", "impact"), motifs = listOf("sun", "sunburst", "flower", "burst", "sparkle"),
        tilt = 1.2f,
    )),
    Idol("Idol", MotionThemeSpec(
        mood = "energetic", palette = listOf("#ff4fd8", "#7b61ff"),
        layouts = listOf("slot", "cards", "reel", "wordwall", "poster", "strip"),
        enters = listOf("flip", "spin", "whip", "panel", "cut"),
        fx = listOf("pop", "glitch", "zoom", "assemble", "flicker", "bounce"),
        accents = listOf("sticker", "glow", "outline", "spark", "gradient"),
        types = listOf("round", "impact"), motifs = listOf("star", "sparkle", "heart", "burst", "dots"),
    )),
    SmokyClub("Smoky Club", MotionThemeSpec(
        mood = "melancholic", palette = listOf("#d4a24c", "#3fa7a0"),
        layouts = listOf("column", "hero", "spread", "arch", "crawl"),
        enters = listOf("fade", "pan", "pull", "orbit"),
        fx = listOf("blur", "type", "wipe", "rise"),
        accents = listOf("script", "underline", "bracket", "mark"),
        types = listOf("serif"), motifs = listOf("note", "moon", "ring", "arc"),
        upper = false, bars = true, tilt = 0.5f,
    )),
    Cinematic("Cinematic", MotionThemeSpec(
        mood = "calm", palette = listOf("#e8d8a8", "#8fb3ff"),
        layouts = listOf("hero", "crawl", "swell", "pillar", "arch"),
        enters = listOf("fade", "pull", "orbit", "rise"),
        fx = listOf("blur", "rise", "wipe", "track"),
        accents = listOf("glow", "gradient", "underline"),
        types = listOf("serif"), motifs = listOf("halo", "ring", "star", "crown"),
        upper = true, bars = true, rule = true, tilt = 0.3f,
    )),
    Island("Island", MotionThemeSpec(
        mood = "warm", palette = listOf("#2ecc40", "#ffdc00"),
        layouts = listOf("zigzag", "swell", "carousel", "collage", "spread"),
        enters = listOf("roll", "orbit", "pan", "rise"),
        fx = listOf("wave", "bounce", "slide", "rise"),
        accents = listOf("sticker", "color", "mark", "circle"),
        types = listOf("round"), motifs = listOf("sun", "leaf", "wave", "sunburst"),
    )),
    Heavenly("Heavenly", MotionThemeSpec(
        mood = "dreamy", palette = listOf("#fff1b8", "#a8d8ff"),
        layouts = listOf("hero", "column", "arch", "swell"),
        enters = listOf("rise", "fade", "orbit"),
        fx = listOf("rise", "blur", "wave"),
        accents = listOf("glow", "gradient", "underline"),
        types = listOf("serif"), motifs = listOf("halo", "sparkle", "sun", "star"),
        upper = false, tilt = 0.3f,
    )),
    RetroArcade("Retro Arcade", MotionThemeSpec(
        mood = "playful", palette = listOf("#39ff14", "#ff00ff"),
        layouts = listOf("wordwall", "slot", "strip", "wall", "cards"),
        enters = listOf("cut", "panel", "drop", "push"),
        fx = listOf("type", "flicker", "glitch", "track", "pop"),
        accents = listOf("outline", "bracket", "stamp", "color"),
        types = listOf("impact"), motifs = listOf("square", "triangle", "plus", "cross", "zigzag"),
        upper = true, tilt = 0f,
    )),
    Noir("Noir", MotionThemeSpec(
        mood = "dark", palette = listOf("#ffffff", "#9e9e9e"),
        layouts = listOf("hero", "poster", "column", "pillar"),
        enters = listOf("cut", "fade", "pull"),
        fx = listOf("wipe", "type", "slam"),
        accents = listOf("outline", "underline", "bracket"),
        types = listOf("serif", "impact"), motifs = listOf("frame", "bars", "eye"),
        bars = true, rule = true, tilt = 0.4f,
    )),
    SunsetDrive("Sunset Drive", MotionThemeSpec(
        mood = "energetic", palette = listOf("#ff7e5f", "#7b2ff7"),
        layouts = listOf("crawl", "tunnel", "strip", "reel"),
        enters = listOf("through", "push", "pan"),
        fx = listOf("track", "stretch", "slide", "zoom"),
        accents = listOf("gradient", "glow", "trail", "extrude"),
        types = listOf("impact"), motifs = listOf("sun", "arc", "bars", "zigzag"),
        upper = true,
    )),
    Ocean("Ocean", MotionThemeSpec(
        mood = "calm", palette = listOf("#00b4d8", "#90e0ef"),
        layouts = listOf("swell", "carousel", "crawl", "spiral"),
        enters = listOf("orbit", "roll", "fade"),
        fx = listOf("wave", "blur", "slide"),
        accents = listOf("gradient", "glow", "trail"),
        types = listOf("round"), motifs = listOf("wave", "drop", "spiral", "ring"),
        upper = false, tilt = 0.7f,
    )),
    Galaxy("Galaxy", MotionThemeSpec(
        mood = "dreamy", palette = listOf("#8e7dff", "#ff9de6"),
        layouts = listOf("radial", "spiral", "tunnel", "carousel"),
        enters = listOf("orbit", "spin", "through"),
        fx = listOf("zoom", "blur", "flicker", "spin"),
        accents = listOf("glow", "spark", "trail"),
        types = listOf("round", "impact"), motifs = listOf("star", "sparkle", "moon", "ring", "dot"),
    )),
    Comic("Comic", MotionThemeSpec(
        mood = "playful", palette = listOf("#ffe600", "#ff2d55"),
        layouts = listOf("cards", "collage", "poster", "zigzag"),
        enters = listOf("panel", "cut", "drop", "flip"),
        fx = listOf("pop", "slam", "bounce", "stretch"),
        accents = listOf("sticker", "stamp", "extrude", "giant", "circle"),
        types = listOf("impact"), motifs = listOf("burst", "bolt", "star", "sunburst"),
        upper = true, tilt = 1.4f,
    )),
    Valentine("Valentine", MotionThemeSpec(
        mood = "warm", palette = listOf("#ff4d6d", "#ffb3c1"),
        layouts = listOf("hero", "arch", "swell", "cards"),
        enters = listOf("fade", "rise", "orbit"),
        fx = listOf("rise", "wave", "pop"),
        accents = listOf("script", "glow", "circle"),
        types = listOf("serif", "round"), motifs = listOf("heart", "flower", "sparkle"),
        upper = false,
    )),
    Frost("Frost", MotionThemeSpec(
        mood = "calm", palette = listOf("#bde0fe", "#e0f7ff"),
        layouts = listOf("pillar", "column", "crawl", "hero"),
        enters = listOf("drop", "fade", "rise"),
        fx = listOf("blur", "drop", "flicker"),
        accents = listOf("glow", "outline"),
        types = listOf("impact", "round"), motifs = listOf("snow", "sparkle", "star"),
        tilt = 0.5f,
    )),
    // Hip-hop's other sides.
    TrapHouse("Trap House", MotionThemeSpec(
        mood = "intense", palette = listOf("#b026ff", "#ff2e63"),
        layouts = listOf("poster", "wordwall", "split", "stairs", "slot"),
        enters = listOf("cut", "drop", "whip", "push"),
        fx = listOf("slam", "glitch", "stretch", "zoom", "flicker"),
        accents = listOf("extrude", "glow", "stamp", "giant"),
        types = listOf("impact"), motifs = listOf("crown", "bolt", "cross", "burst"),
        upper = true, bars = true, tilt = 1.1f,
    )),
    CloudRap("Cloud Rap", MotionThemeSpec(
        mood = "dreamy", palette = listOf("#c3b1ff", "#ffc6e0"),
        layouts = listOf("swell", "column", "crawl", "hero", "spiral"),
        enters = listOf("fade", "rise", "orbit", "pull"),
        fx = listOf("blur", "wave", "rise", "flicker"),
        accents = listOf("glow", "gradient", "script", "trail"),
        types = listOf("round", "serif"), motifs = listOf("moon", "star", "sparkle", "heart", "halo"),
        upper = false, tilt = 0.5f,
    )),
    Drill("Drill", MotionThemeSpec(
        mood = "dark", palette = listOf("#d6e2ff", "#5c7cfa"),
        layouts = listOf("poster", "pillar", "wall", "split", "strip"),
        enters = listOf("cut", "panel", "push", "whip"),
        fx = listOf("slam", "type", "track", "glitch"),
        accents = listOf("stamp", "bracket", "outline", "underline"),
        types = listOf("impact"), motifs = listOf("cross", "square", "bars", "arrow"),
        upper = true, bars = true, rule = true, tilt = 0.6f,
    )),
    Phonk("Phonk", MotionThemeSpec(
        mood = "dark", palette = listOf("#ff1f3d", "#3a0a12"),
        layouts = listOf("tunnel", "hero", "poster", "reel", "wordwall"),
        enters = listOf("through", "cut", "pull", "drop"),
        fx = listOf("blur", "flicker", "glitch", "zoom", "slam"),
        accents = listOf("extrude", "glow", "trail", "outline"),
        types = listOf("impact"), motifs = listOf("eye", "cross", "triangle", "zigzag"),
        upper = true, bars = true, tilt = 0.9f,
    )),
    // R&B after dark, and its softer side.
    AfterHours("After Hours", MotionThemeSpec(
        mood = "dark", palette = listOf("#ff1744", "#ffd1dc"),
        layouts = listOf("hero", "column", "tunnel", "crawl", "pillar"),
        enters = listOf("fade", "pull", "through", "rise"),
        fx = listOf("blur", "wipe", "flicker", "rise"),
        accents = listOf("glow", "outline", "trail", "underline"),
        types = listOf("serif", "impact"), motifs = listOf("moon", "eye", "frame", "ring"),
        bars = true, tilt = 0.4f,
    )),
    Blonde("Blonde", MotionThemeSpec(
        mood = "calm", palette = listOf("#ffb26b", "#fff4e0"),
        layouts = listOf("hero", "column", "swell", "arch"),
        enters = listOf("fade", "rise", "pan"),
        fx = listOf("wipe", "rise", "wave", "blur"),
        accents = listOf("underline", "script", "gradient"),
        types = listOf("serif"), motifs = listOf("sun", "wave", "flower", "dot"),
        upper = false, serif = true, tilt = 0.2f,
    )),
    // Indie and psychedelic.
    Psychedelia("Psychedelia", MotionThemeSpec(
        mood = "dreamy", palette = listOf("#ff5ec4", "#3dfcff"),
        layouts = listOf("spiral", "radial", "swell", "carousel", "tunnel"),
        enters = listOf("orbit", "spin", "roll", "through"),
        fx = listOf("wave", "spin", "blur", "zoom", "stretch"),
        accents = listOf("gradient", "glow", "trail", "extrude"),
        types = listOf("round"), motifs = listOf("spiral", "sun", "ring", "flower", "wave"),
        upper = false, tilt = 1.2f,
    )),
    BedroomTape("Bedroom Tape", MotionThemeSpec(
        mood = "warm", palette = listOf("#ff9eb5", "#fff0d6"),
        layouts = listOf("cards", "collage", "column", "hero", "strip"),
        enters = listOf("fade", "drop", "panel", "flip"),
        fx = listOf("type", "flicker", "wipe", "pop"),
        accents = listOf("script", "circle", "underline", "sticker"),
        types = listOf("serif", "round"), motifs = listOf("heart", "star", "flower", "scribble"),
        upper = false, tilt = 0.8f,
    )),
    WitchHouse("Witch House", MotionThemeSpec(
        mood = "dark", palette = listOf("#e0e0e0", "#7a00ff"),
        layouts = listOf("pillar", "hero", "tunnel", "column"),
        enters = listOf("fade", "cut", "through", "pull"),
        fx = listOf("flicker", "glitch", "blur", "type"),
        accents = listOf("outline", "glow", "bracket"),
        types = listOf("serif", "impact"), motifs = listOf("triangle", "cross", "eye", "moon"),
        upper = true, rule = true, tilt = 0.3f,
    )),
    // Ambient, scores and game music.
    Blocky("Blocky", MotionThemeSpec(
        mood = "calm", palette = listOf("#7ccf5c", "#9b7653"),
        layouts = listOf("wall", "cards", "strip", "column", "slot"),
        enters = listOf("drop", "fade", "panel"),
        fx = listOf("drop", "type", "pop", "slide"),
        accents = listOf("outline", "color", "bracket"),
        types = listOf("impact", "round"), motifs = listOf("square", "plus", "dot", "sun"),
        upper = false, tilt = 0f,
    )),
    Nocturne("Nocturne", MotionThemeSpec(
        mood = "calm", palette = listOf("#a8c0ff", "#3f2b96"),
        layouts = listOf("hero", "column", "arch", "swell"),
        enters = listOf("fade", "rise", "pan"),
        fx = listOf("blur", "wipe", "rise"),
        accents = listOf("glow", "underline", "script"),
        types = listOf("serif"), motifs = listOf("moon", "star", "halo", "snow"),
        upper = false, serif = true, tilt = 0.2f,
    )),
    // Rock and pop's odder corners.
    GrungeTape("Grunge Tape", MotionThemeSpec(
        mood = "intense", palette = listOf("#c9b458", "#6b705c"),
        layouts = listOf("poster", "collage", "wordwall", "stairs"),
        enters = listOf("cut", "drop", "whip", "panel"),
        fx = listOf("glitch", "slam", "stretch", "type"),
        accents = listOf("scribble", "stamp", "extrude", "bracket"),
        types = listOf("impact"), motifs = listOf("scribble", "cross", "zigzag", "bolt"),
        upper = true, tilt = 1.3f,
    )),
    Oddball("Oddball", MotionThemeSpec(
        mood = "playful", palette = listOf("#ffd60a", "#00f5d4"),
        layouts = listOf("zigzag", "cards", "spread", "carousel", "collage"),
        enters = listOf("roll", "flip", "spin", "drop"),
        fx = listOf("bounce", "stretch", "spin", "pop", "wave"),
        accents = listOf("sticker", "giant", "circle", "extrude"),
        types = listOf("impact", "round"), motifs = listOf("star", "burst", "dots", "arrow"),
        upper = true, tilt = 1.5f,
    ));

    companion object {
        /** The themes Auto picks among for each genre; a song keeps the same one ([forGenre]). */
        private val byGenre: Map<SongGenre, List<MotionTheme>> = mapOf(
            SongGenre.Pop to listOf(Bubblegum, Oddball),
            SongGenre.HipHop to listOf(Street, TrapHouse, CloudRap, Drill, Phonk),
            SongGenre.RnB to listOf(Velvet, AfterHours, Blonde),
            SongGenre.Rock to listOf(Stadium, GrungeTape),
            SongGenre.Metal to listOf(Inferno),
            SongGenre.Alternative to listOf(Daydream, Psychedelia, BedroomTape, WitchHouse),
            SongGenre.Electronic to listOf(NeonNight, SunsetDrive, RetroArcade),
            SongGenre.Country to listOf(Campfire),
            SongGenre.Latin to listOf(Fiesta),
            SongGenre.AsianPop to listOf(Idol),
            SongGenre.Jazz to listOf(SmokyClub),
            SongGenre.Classical to listOf(Cinematic, Blocky, Nocturne),
            SongGenre.Reggae to listOf(Island),
            SongGenre.Gospel to listOf(Heavenly),
        )

        /** The theme Auto picks for a song of [genre]: one of its genre's, chosen by [song] so it stays put. */
        fun forGenre(genre: SongGenre?, song: String = ""): MotionTheme {
            val pool = genre?.let { byGenre[it] }.orEmpty().ifEmpty { return Classic }
            return pool[Math.floorMod(song.hashCode(), pool.size)]
        }
    }
}

/** [this] as the theme that plays: Auto resolves by [genre]. */
internal fun MotionTheme.resolve(genre: SongGenre?, song: String = ""): MotionTheme = if (this == MotionTheme.Auto) MotionTheme.forGenre(genre, song) else this

/** The theme as the page's director takes it (window.spicyMotionTheme); empty for none. */
internal fun MotionTheme.json(): String {
    val spec = spec ?: return ""
    val out = StringBuilder(512).append("{\"id\":\"").append(name).append('"')
    fun list(key: String, values: List<String>) {
        out.append(",\"").append(key).append("\":[")
        values.forEachIndexed { index, value -> if (index > 0) out.append(','); out.append('"').append(value).append('"') }
        out.append(']')
    }
    out.append(",\"mood\":\"").append(spec.mood).append('"')
    list("palette", spec.palette)
    list("layouts", spec.layouts)
    list("enters", spec.enters)
    list("fx", spec.fx)
    list("accents", spec.accents)
    list("types", spec.types)
    list("motifs", spec.motifs)
    spec.upper?.let { out.append(",\"upper\":").append(it) }
    spec.bars?.let { out.append(",\"bars\":").append(it) }
    spec.rule?.let { out.append(",\"rule\":").append(it) }
    spec.serif?.let { out.append(",\"serif\":").append(it) }
    out.append(",\"tilt\":").append(spec.tilt)
    return out.append('}').toString()
}

/**
 * The playing song's genre, which Auto picks a theme by. Set wherever the song is learnt, as
 * [MotionAccent] is: the lyrics screen, and the background session the wallpaper, overlay and
 * always-on display share. Looked up once per song, only while motion lyrics want it.
 */
internal object MotionGenre {
    private val current = MutableStateFlow<SongGenre?>(null)
    val genre: StateFlow<SongGenre?> = current
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val finder by lazy { ItunesGenre(okhttp3.OkHttpClient.Builder().build()) }
    private val known = java.util.concurrent.ConcurrentHashMap<String, Optional>()
    private var lastSong: String? = null
    private var job: Job? = null

    private data class Optional(val genre: SongGenre?)

    @Synchronized
    fun update(title: String?, artist: String?, wanted: Boolean) {
        if (!wanted || title.isNullOrBlank() || artist.isNullOrBlank()) return
        val song = "${title.trim().lowercase()}\u001f${artist.trim().lowercase()}"
        if (song == lastSong) return
        lastSong = song
        job?.cancel()
        known[song]?.let { current.value = it.genre; return }
        current.value = null
        job = scope.launch {
            val found = SongGenre.of(finder.find(title, artist))
            known[song] = Optional(found)
            synchronized(this@MotionGenre) { if (lastSong == song) current.value = found }
        }
    }
}
