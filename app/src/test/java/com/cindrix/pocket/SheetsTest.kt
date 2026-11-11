package com.cindrix.pocket

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsTest {
    private val now = OffsetDateTime.of(2026, 11, 8, 10, 0, 0, 0, ZoneOffset.ofHours(-5))

    @Test
    fun countSheetHasTheHeaderAndTabbedLines() {
        val t = Sheets.countSheet("ab12cd34", "Hawk", now, listOf(CountLine("u1", 7.0, 5.0), CountLine("u2", 2.5, 3.0)))
        assertEquals(
            "# format: count-sheet 1\n# device: ab12cd34\n# person: Hawk\n# created: 2026-11-08T10:00-05:00\nu1\t7\t5\nu2\t2.5\t3\n",
            t,
        )
    }

    @Test
    fun checkinSheetKeepsSevenFieldsPerLine() {
        val t = Sheets.checkinSheet("ab12cd34", "Hawk", now, listOf(CheckinLine("ab12cd34-1", "north-patrol", "contact", "2026-11-08T10:05-05:00", "ok", "", "all fine\tgood")))
        val line = t.lines().last { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(7, line.split('\t').size)
        assertTrue(line.startsWith("ab12cd34-1\tnorth-patrol\tcontact\t"))
        assertTrue(line.endsWith("all fine good"))
    }

    @Test
    fun namesBecomeIds() {
        assertEquals("north-patrol-2", Sheets.slug(" North Patrol #2 "))
        assertEquals("", Sheets.slug("!!!"))
    }

    @Test
    fun oldCodesAreSwappedForTheHubsOwn() {
        val text = "# format: track-sheet 1\n# device: hawk\n# person: Hawk\n# created: 2026-11-08T10:00Z\n# track: hawk-t5\n# outing: \n1.0\t2.0\n"
        val (n, t) = Sheets.rebrand("hawk-track-5.txt", text, "ab12cd34")
        assertEquals("ab12cd34-track-5.txt", n)
        assertTrue(t.contains("# device: ab12cd34\n"))
        assertTrue(t.contains("# track: ab12cd34-t5\n"))
        val sheet = Sheets.checkinSheet("hawk", "Hawk", now, listOf(CheckinLine("hawk-1", "o1", "note", "2026-11-08T10:05-05:00", "", "", "x")))
        val (n2, t2) = Sheets.rebrand("hawk-checkin-1.txt", sheet, "ab12cd34")
        assertEquals("ab12cd34-checkin-1.txt", n2)
        assertTrue(t2.contains("\nab12cd34-1\to1\tnote"))
        assertEquals("keep.txt" to "plain", Sheets.rebrand("keep.txt", "plain", "ab12cd34"))
    }
}
