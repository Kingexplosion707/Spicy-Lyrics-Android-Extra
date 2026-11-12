package com.tx24.spicyplayer.lyrics

import java.net.URLEncoder

/** Links for the song that's playing (Settings → This song): its Spotify page, and searches for it elsewhere. */
object SongLinks {
    /** The song's page on Spotify, when its ID looks like one. */
    fun spotify(trackId: String?): String? =
        trackId?.trim()?.takeIf { it.length == 22 && it.all(Char::isLetterOrDigit) }?.let { "https://open.spotify.com/track/$it" }

    fun genius(title: String, artist: String): String = "https://genius.com/search?q=" + query(title, artist)

    fun youtube(title: String, artist: String): String = "https://www.youtube.com/results?search_query=" + query(title, artist)

    /** What Share song sends: "Title – Artist", with the Spotify link under it when there is one. */
    fun shareText(title: String, artist: String, trackId: String?): String {
        val name = listOf(title.trim(), artist.trim()).filter { it.isNotEmpty() }.joinToString(" – ")
        return listOfNotNull(name.takeIf { it.isNotEmpty() }, spotify(trackId)).joinToString("\n")
    }

    private fun query(title: String, artist: String): String =
        URLEncoder.encode(listOf(title.trim(), artist.trim()).filter { it.isNotEmpty() }.joinToString(" "), "UTF-8")
}
