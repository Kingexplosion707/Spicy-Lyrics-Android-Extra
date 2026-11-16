package com.tx24.spicyplayer.lyrics.motion

/**
 * A look for motion lyrics: colours and effects laid over ivLyrics' page. [Original] leaves the
 * page as it is (with the cover's colours). Auto picks one from the song's genre ([forGenres]).
 */
enum class MotionTheme(val id: String, val label: String) {
    Original("original", "Original"),
    Neon("neon", "Neon"),
    Synthwave("synthwave", "Synthwave"),
    Street("street", "Street"),
    Rock("rock", "Rock"),
    Metal("metal", "Metal"),
    Pop("pop", "Pop"),
    LoFi("lofi", "Lo-fi"),
    Jazz("jazz", "Jazz"),
    Country("country", "Country"),
    Classical("classical", "Classical"),
    Soul("soul", "R&B / Soul"),
    Rave("rave", "Rave"),
    Blue("blue", "Blue"),
    Latin("latin", "Latin"),
    Punk("punk", "Punk");

    companion object {
        const val AUTO = "auto"

        fun of(id: String?): MotionTheme? = entries.firstOrNull { it.id == id }

        /** Words in a genre or tag that point at each theme, most telling first. */
        private val WORDS: List<Pair<MotionTheme, List<String>>> = listOf(
            Metal to listOf("metal", "metalcore", "deathcore", "djent", "thrash", "doom", "black metal", "screamo"),
            Punk to listOf("punk", "hardcore", "emo", "pop punk", "grunge", "riot grrrl", "post-punk"),
            Rave to listOf("edm", "house", "techno", "trance", "dubstep", "drum and bass", "dnb", "electro", "hardstyle", "rave", "garage", "breakbeat", "hyperpop", "phonk"),
            Synthwave to listOf("synthwave", "synth-pop", "synthpop", "new wave", "retrowave", "outrun", "vaporwave", "80s", "darkwave", "electropop"),
            LoFi to listOf("lo-fi", "lofi", "chillhop", "bedroom", "dream pop", "shoegaze", "ambient", "chill", "downtempo", "trip hop", "trip-hop"),
            Street to listOf("hip hop", "hip-hop", "rap", "trap", "drill", "grime", "boom bap", "gangsta", "crunk", "emo rap", "cloud rap", "rage"),
            Soul to listOf("r&b", "rnb", "soul", "neo soul", "funk", "motown", "quiet storm", "gospel", "contemporary r&b"),
            Jazz to listOf("jazz", "swing", "bebop", "big band", "bossa nova", "blues", "lounge", "smooth jazz"),
            Country to listOf("country", "bluegrass", "americana", "folk", "honky tonk", "western", "outlaw"),
            Classical to listOf("classical", "orchestral", "opera", "baroque", "soundtrack", "score", "piano", "instrumental", "neoclassical"),
            Latin to listOf("latin", "reggaeton", "salsa", "bachata", "cumbia", "dembow", "latin pop", "urbano", "flamenco", "samba", "afrobeats", "afrobeat", "dancehall", "reggae"),
            Rock to listOf("rock", "indie rock", "alternative", "alt-rock", "hard rock", "classic rock", "garage rock", "britpop", "indie"),
            Pop to listOf("pop", "k-pop", "kpop", "j-pop", "dance pop", "teen pop", "bubblegum", "idol", "electropop"),
            Blue to listOf("sad", "ballad", "acoustic", "singer-songwriter", "slowcore", "melancholy", "piano ballad"),
            Neon to listOf("electronic", "dance", "club", "disco", "nu-disco"),
        )

        /**
         * The theme for a song tagged [genres] (Spotify, MusicBrainz or Deezer names, any case),
         * weighting earlier tags (the most used) more; null when nothing matches.
         */
        fun forGenres(genres: List<String>): MotionTheme? {
            if (genres.isEmpty()) return null
            val scores = HashMap<MotionTheme, Double>()
            genres.take(12).forEachIndexed { index, raw ->
                val genre = " " + raw.lowercase().replace('_', ' ').trim() + " "
                val weight = 1.0 / (1 + index * 0.35)
                for ((theme, words) in WORDS) {
                    // The first word of a theme that fits counts; longer, more exact words count more.
                    val hit = words.firstOrNull { word -> genre.contains(" $word ") || genre.trim() == word } ?: continue
                    scores[theme] = (scores[theme] ?: 0.0) + weight * (1.0 + hit.length / 12.0)
                }
            }
            return scores.maxByOrNull { it.value }?.key
        }
    }
}
