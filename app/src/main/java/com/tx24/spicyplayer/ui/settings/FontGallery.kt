package com.tx24.spicyplayer.ui.settings

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The font gallery: open-licensed fonts for the lyrics, downloaded one at a time from Google
 * Fonts' repository on GitHub when picked and kept in the app's storage. The addresses are pinned
 * to one commit, so a font that moves there later still downloads.
 */
object FontGallery {
    class Entry(val id: String, val name: String, val kind: String, internal val path: String)

    val entries: List<Entry> = listOf(
        Entry("inter", "Inter", "Clean sans", "ofl/inter/Inter[opsz,wght].ttf"),
        Entry("manrope", "Manrope", "Clean sans", "ofl/manrope/Manrope[wght].ttf"),
        Entry("outfit", "Outfit", "Round sans", "ofl/outfit/Outfit[wght].ttf"),
        Entry("jakarta", "Plus Jakarta Sans", "Clean sans", "ofl/plusjakartasans/PlusJakartaSans[wght].ttf"),
        Entry("dmsans", "DM Sans", "Clean sans", "ofl/dmsans/DMSans[opsz,wght].ttf"),
        Entry("montserrat", "Montserrat", "Wide sans", "ofl/montserrat/Montserrat[wght].ttf"),
        Entry("nunito", "Nunito", "Round sans", "ofl/nunito/Nunito[wght].ttf"),
        Entry("rubik", "Rubik", "Round sans", "ofl/rubik/Rubik[wght].ttf"),
        Entry("lexend", "Lexend", "Easy to read", "ofl/lexend/Lexend[wght].ttf"),
        Entry("sora", "Sora", "Wide sans", "ofl/sora/Sora[wght].ttf"),
        Entry("spacegrotesk", "Space Grotesk", "Quirky sans", "ofl/spacegrotesk/SpaceGrotesk[wght].ttf"),
        Entry("urbanist", "Urbanist", "Clean sans", "ofl/urbanist/Urbanist[wght].ttf"),
        Entry("figtree", "Figtree", "Clean sans", "ofl/figtree/Figtree[wght].ttf"),
        Entry("bricolage", "Bricolage Grotesque", "Quirky sans", "ofl/bricolagegrotesque/BricolageGrotesque[opsz,wdth,wght].ttf"),
        Entry("unbounded", "Unbounded", "Wide display", "ofl/unbounded/Unbounded[wght].ttf"),
        Entry("syne", "Syne", "Wide display", "ofl/syne/Syne[wght].ttf"),
        Entry("quicksand", "Quicksand", "Round sans", "ofl/quicksand/Quicksand[wght].ttf"),
        Entry("comfortaa", "Comfortaa", "Round display", "ofl/comfortaa/Comfortaa[wght].ttf"),
        Entry("raleway", "Raleway", "Elegant sans", "ofl/raleway/Raleway[wght].ttf"),
        Entry("oswald", "Oswald", "Tall and narrow", "ofl/oswald/Oswald[wght].ttf"),
        Entry("bebas", "Bebas Neue", "Tall capitals", "ofl/bebasneue/BebasNeue-Regular.ttf"),
        Entry("anton", "Anton", "Heavy and narrow", "ofl/anton/Anton-Regular.ttf"),
        Entry("playfair", "Playfair Display", "Serif", "ofl/playfairdisplay/PlayfairDisplay[wght].ttf"),
        Entry("fraunces", "Fraunces", "Soft serif", "ofl/fraunces/Fraunces[SOFT,WONK,opsz,wght].ttf"),
        Entry("lora", "Lora", "Serif", "ofl/lora/Lora[wght].ttf"),
        Entry("cormorant", "Cormorant", "Fine serif", "ofl/cormorant/Cormorant[wght].ttf"),
        Entry("dmserif", "DM Serif Display", "Serif", "ofl/dmserifdisplay/DMSerifDisplay-Regular.ttf"),
        Entry("instrument", "Instrument Serif", "Fine serif", "ofl/instrumentserif/InstrumentSerif-Regular.ttf"),
        Entry("cinzel", "Cinzel", "Roman capitals", "ofl/cinzel/Cinzel[wght].ttf"),
        Entry("jetbrains", "JetBrains Mono", "Monospace", "ofl/jetbrainsmono/JetBrainsMono[wght].ttf"),
        Entry("caveat", "Caveat", "Handwriting", "ofl/caveat/Caveat[wght].ttf"),
        Entry("dancing", "Dancing Script", "Script", "ofl/dancingscript/DancingScript[wght].ttf"),
        Entry("pacifico", "Pacifico", "Brush script", "ofl/pacifico/Pacifico-Regular.ttf"),
        Entry("lobster", "Lobster", "Bold script", "ofl/lobster/Lobster-Regular.ttf"),
        Entry("marker", "Permanent Marker", "Marker pen", "apache/permanentmarker/PermanentMarker-Regular.ttf"),
        Entry("fredoka", "Fredoka", "Bubbly", "ofl/fredoka/Fredoka[wdth,wght].ttf"),
        Entry("baloo", "Baloo 2", "Bubbly", "ofl/baloo2/Baloo2[wght].ttf"),
        Entry("righteous", "Righteous", "Retro display", "ofl/righteous/Righteous-Regular.ttf"),
        Entry("bangers", "Bangers", "Comic", "ofl/bangers/Bangers-Regular.ttf"),
        Entry("orbitron", "Orbitron", "Sci-fi", "ofl/orbitron/Orbitron[wght].ttf"),
        Entry("pressstart", "Press Start 2P", "Pixel", "ofl/pressstart2p/PressStart2P-Regular.ttf"),
        Entry("vt323", "VT323", "Terminal", "ofl/vt323/VT323-Regular.ttf"),
    )

    fun entry(id: String): Entry? = entries.firstOrNull { it.id == id }

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "font-gallery")
    private fun file(context: Context, id: String) = File(dir(context), "$id.ttf")

    fun isDownloaded(context: Context, id: String): Boolean = file(context, id).isFile

    /** The downloaded font as a family, or null when it isn't there. */
    fun family(context: Context, id: String): FontFamily? =
        if (entry(id) == null) null else LyricsFontFile.familyOf(file(context, id))

    /** Downloads [entry]'s font; false when it couldn't be fetched or isn't a font Android reads. */
    suspend fun download(context: Context, entry: Entry): Boolean = withContext(Dispatchers.IO) {
        val target = file(context, entry.id)
        if (target.isFile) return@withContext true
        val temp = File(dir(context).apply { mkdirs() }, "${entry.id}.part")
        val fetched = runCatching {
            val connection = URL(address(entry)).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                if (connection.responseCode != 200) return@runCatching false
                connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                true
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
        val kept = fetched && temp.length() in 1..MAX_BYTES && LyricsFontFile.isFont(temp) && temp.renameTo(target)
        if (!kept) temp.delete()
        kept
    }

    fun delete(context: Context, id: String) {
        file(context, id).delete()
    }

    /** Where [entry]'s file is: each part of its path escaped, as its names carry brackets and commas. */
    internal fun address(entry: Entry): String =
        BASE + entry.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private const val BASE = "https://raw.githubusercontent.com/google/fonts/5bb28733cec313b63fbd415b574b5ae35c8aa8b8/"
    private const val TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 4L * 1024 * 1024
}
