package com.tx24.spicyplayer.widget

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLinesTest {
    private fun line(startMs: Long, endMs: Long, vararg words: String, role: LineRole = LineRole.LEAD) =
        TimedLine(startMs, endMs, words.map { TimedWord(it, startMs, endMs, attached = false) }, role = role)

    private val leads = WidgetLines.leads(
        listOf(
            line(1_000, 3_000, "Hello", "world"),
            line(2_000, 3_000, "(echo)", role = LineRole.BACKGROUND),
            line(3_000, 5_000, "Second"),
            line(5_000, 6_000, "Third"),
            line(12_000, 14_000, "After", "the", "break"),
        ),
    )

    private fun WidgetLines.texts() = Triple(before.map { it.text }, current.text, after.map { it.text })

    @Test
    fun `background vocals go with their lead line and words are spaced`() {
        assertEquals(listOf("Hello world", "Second", "Third", "After the break"), leads.map { it.text })
        assertEquals(listOf("echo", null, null, null), leads.map { it.background })
    }

    @Test
    fun `background vocals find their lead by group and get brackets`() {
        val grouped = WidgetLines.leads(
            listOf(
                TimedLine(0, 1_000, listOf(TimedWord("One", 0, 1_000, attached = false)), groupId = 1),
                TimedLine(1_000, 2_000, listOf(TimedWord("Two", 1_000, 2_000, attached = false)), groupId = 2, oppositeAligned = true),
                TimedLine(500, 900, listOf(TimedWord("ooh", 500, 900, attached = false)), role = LineRole.BACKGROUND, groupId = 1),
            ),
        )
        assertEquals(listOf("ooh", null), grouped.map { it.background })
        assertEquals(listOf(false, true), grouped.map { it.oppositeAligned })
    }

    @Test
    fun `syllables of one word stay together`() {
        val words = listOf(
            TimedWord("What's", 0, 300, attached = false),
            TimedWord("your", 300, 600, attached = false),
            TimedWord("al", 600, 800, attached = false),
            TimedWord("i", 800, 900, attached = true),
            TimedWord("bi?", 900, 1_200, attached = true),
        )
        assertEquals("What's your alibi?", WidgetLines.leads(listOf(TimedLine(0, 1_200, words))).single().text)
    }

    @Test
    fun `romanization and translation come with their line`() {
        val words = listOf(
            TimedWord("毎", 0, 250, false, romanized = "mai"),
            TimedWord("日", 250, 500, true, romanized = "nichi"),
            TimedWord("同", 500, 750, true, romanized = " ona"),
            TimedWord("じ", 750, 1_000, true, romanized = "ji"),
        )
        val lead = WidgetLines.leads(listOf(TimedLine(0, 1_000, words, translation = " Every  day "))).single()
        assertEquals("毎日同じ", lead.text)
        assertEquals("mainichi onaji", lead.romanized)
        assertEquals("Every day", lead.translation)
        assertNull(leads.first().romanized)
    }

    @Test
    fun `before the first line the break shows with the first lines after it`() {
        val intro = leads.map { it.copy(startMs = it.startMs + 4_000, endMs = it.endMs + 4_000) }
        val shown = WidgetLines.at(intro, 0)
        assertEquals(Triple(emptyList<String>(), WidgetLines.BREAK, listOf("Hello world", "Second")), shown.texts())
        assertTrue(shown.current.interlude)
        // No dot is lit as the break starts; the next change is the first one finishing.
        assertEquals(0, shown.dots)
        assertTrue(shown.changesAtMs!! in 1..4_999)
    }

    @Test
    fun `a line shows with the lines around it until the next one starts`() {
        val shown = WidgetLines.at(leads, 3_500)
        assertEquals(Triple(listOf("Hello world"), "Second", listOf("Third", "After the break")), shown.texts())
        assertEquals(5_000L, shown.changesAtMs)
    }

    @Test
    fun `a long gap shows the break once the line ends`() {
        assertEquals(6_000L, WidgetLines.at(leads, 5_500).changesAtMs)
        val shown = WidgetLines.at(leads, 7_000)
        assertEquals(Triple(listOf("Second", "Third"), WidgetLines.BREAK, listOf("After the break")), shown.texts())
        assertEquals(0, shown.dots)
        // Each dot lights as it finishes, one by one through the break, then the next line.
        val lit = (6_000L until 12_000L step 100).map { WidgetLines.at(leads, it).dots }
        assertEquals(listOf(0, 1, 2, 3), lit.distinct())
        assertEquals(12_000L, WidgetLines.at(leads, 11_900).changesAtMs)
    }

    @Test
    fun `the last line stays with nothing after it`() {
        val shown = WidgetLines.at(leads, 20_000)
        assertEquals(Triple(listOf("Second", "Third"), "After the break", emptyList<String>()), shown.texts())
        assertNull(shown.changesAtMs)
    }

    @Test
    fun `unsynced lyrics show an explicit notice instead of freezing on the first lines`() {
        val shown = WidgetLines.unsynced(leads)
        assertTrue(shown.current.text.startsWith("Unsynced lyrics"))
        assertTrue(shown.before.isEmpty() && shown.after.isEmpty())
        assertNull(shown.changesAtMs)
    }

    @Test
    fun `consecutive backing vocals retain their own rows`() {
        val grouped = WidgetLines.leads(listOf(
            line(0, 4_000, "Lead"),
            line(500, 1_000, "(one)", role = LineRole.BACKGROUND),
            line(2_000, 3_000, "(two)", role = LineRole.BACKGROUND),
        ))
        assertEquals("one\ntwo", grouped.single().background)
    }

    @Test
    fun `overlapping leads stay active together even with no surrounding lines`() {
        val duet = WidgetLines.leads(listOf(line(0, 5_000, "First"), line(2_000, 3_000, "Second"), line(7_000, 8_000, "Next")))
        val shown = WidgetLines.at(duet, 2_500)
        assertEquals("First", shown.current.text)
        assertEquals(listOf("Second"), shown.concurrent.map { it.text })
        assertEquals(3_000L, shown.changesAtMs)
        val after = WidgetLines.at(duet, 3_000)
        assertEquals("First", after.current.text)
        assertTrue(after.concurrent.isEmpty())
        assertEquals(5_000L, after.changesAtMs)
    }

    @Test
    fun `a line that only runs into the next is not shown with it`() {
        val lines = WidgetLines.leads(listOf(line(0, 2_200, "First"), line(2_000, 4_000, "Second")))
        val shown = WidgetLines.at(lines, 2_100)
        assertEquals("Second", shown.current.text)
        assertTrue(shown.concurrent.isEmpty())
    }

    @Test
    fun `same-start leads are both active and expired lines are excluded`() {
        val duet = WidgetLines.leads(listOf(line(0, 5_000, "First"), line(0, 3_000, "Second")))
        assertEquals(listOf("Second"), WidgetLines.at(duet, 1_000).concurrent.map { it.text })
        assertTrue(WidgetLines.at(duet, 3_000).concurrent.isEmpty())
    }

    @Test
    fun `three second gaps match the normal lyric screen interlude threshold`() {
        val lines = WidgetLines.leads(listOf(line(0, 1_000, "First"), line(4_000, 5_000, "Next")))
        assertTrue(WidgetLines.at(lines, 2_000).current.interlude)
    }

    @Test
    fun `a short intro does not invent an interlude`() {
        assertEquals("Hello world", WidgetLines.at(leads, 0).current.text)
        assertEquals(1_000L, WidgetLines.at(leads, 0).changesAtMs)
    }

    @Test
    fun `a shorter overlapping line does not start the interlude early`() {
        val lines = WidgetLines.leads(listOf(line(0, 5_000, "Long"), line(2_000, 3_000, "Short"), line(9_000, 10_000, "Next")))
        val gap = WidgetLines.at(lines, 5_500)
        assertTrue(gap.current.interlude)
        assertEquals(5_000L, gap.current.startMs)
    }

    @Test
    fun `a break goes by the line just before, as the lyrics view decides`() {
        // 3.5s after "Short", but only 2.5s after "Long": the lyrics view shows the break.
        val lines = WidgetLines.leads(listOf(line(0, 4_000, "Long"), line(2_000, 3_000, "Short"), line(6_500, 7_500, "Next")))
        val gap = WidgetLines.at(lines, 4_500)
        assertTrue(gap.current.interlude)
        assertEquals(4_000L, gap.current.startMs)
    }

    @Test
    fun `a cut off syllable cannot wrap away from its dash`() {
        assertEquals("the w\u2060— down", widgetUnbreakableText("the w— down"))
        assertEquals("hello world", widgetUnbreakableText("hello world"))
    }
}
