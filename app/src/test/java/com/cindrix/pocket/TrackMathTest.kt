package com.cindrix.pocket

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same walk and the same numbers as the Hub's tests (core/src/tracksheet.rs), so the two always agree. */
class TrackMathTest {
    private fun walk() = listOf(
        TrackPoint(1000, 38.000, -80.0, 500.0),
        TrackPoint(1060, 38.001, -80.0, 510.0),
        TrackPoint(1120, 38.002, -80.0, 506.0),
        TrackPoint(1180, 38.002, -80.001, 506.5),
    )

    @Test
    fun distanceClimbAndTimeComeOutRight() {
        val s = TrackMath.stats(walk())
        assertEquals(111.2 * 2 + 87.7, s.metres, 3.0)
        assertEquals(10.0, s.climb, 0.001)
        assertEquals(4.0, s.drop, 0.001)
        assertEquals(180L, s.seconds)
        assertEquals(500.0, s.low!!, 0.001)
        assertEquals(510.0, s.high!!, 0.001)
    }

    @Test
    fun wobbleUnderThreeMetresIsNotClimbing() {
        val wobble = (0 until 20).map { TrackPoint(it.toLong(), 38.0, -80.0, 500.0 + if (it % 2 == 0) 1.0 else -1.0) }
        val s = TrackMath.stats(wobble)
        assertEquals(0.0, s.climb, 0.0)
        assertEquals(0.0, s.drop, 0.0)
    }

    @Test
    fun aTrackWithNoHeightsStillHasADistance() {
        val s = TrackMath.stats(listOf(TrackPoint(0, 38.0, -80.0, null), TrackPoint(10, 38.001, -80.0, null)))
        assertTrue(s.metres > 100.0)
        assertEquals(0.0, s.climb, 0.0)
        assertNull(s.low)
    }

    @Test
    fun anEmptyTrackIsAllZeros() {
        assertEquals(TrackStats(), TrackMath.stats(emptyList()))
    }

    @Test
    fun theSheetHasTheLinesTheHubReads() {
        val text = TrackSheet.build("falcon27", "Falcon-27", Instant.parse("2026-11-08T15:00:00Z"), "falcon27-t1000", "", walk())
        val lines = text.lines()
        assertEquals("# format: track-sheet 1", lines[0])
        assertEquals("# device: falcon27", lines[1])
        assertEquals("# person: Falcon-27", lines[2])
        assertEquals("# created: 2026-11-08T15:00Z", lines[3])
        assertEquals("# track: falcon27-t1000", lines[4])
        assertEquals("# outing: ", lines[5])
        assertEquals("1000\t38.000000\t-80.000000\t500.0", lines[6])
        assertEquals(6 + 4, lines.indexOfLast { it.isNotEmpty() } + 1)
    }

    @Test
    fun aPointSurvivesBeingWrittenAndReadBack() {
        val p = TrackPoint(1060, 38.001, -80.0, 510.0)
        assertEquals(p, TrackSheet.parseLine(TrackSheet.line(p).trimEnd('\n')))
        val noEle = TrackPoint(5, 38.5, -80.25, null)
        assertEquals(noEle, TrackSheet.parseLine(TrackSheet.line(noEle).trimEnd('\n')))
        assertNull(TrackSheet.parseLine("garbage"))
        assertNull(TrackSheet.parseLine("1\t2\tx\t"))
    }

    @Test
    fun namesFollowTheHubsRules() {
        // the Hub accepts lower-case letters, digits and dashes only, and a phone code of 12 or fewer
        val device = TrackSheet.deviceCode("Falcon-27")
        assertEquals("falcon27", device)
        assertEquals("pocket", TrackSheet.deviceCode("---"))
        assertTrue(TrackSheet.deviceCode("A-very-long-callsign-name").length <= 10)
        val name = TrackSheet.fileName(device, 1_700_000_000)
        assertTrue(Regex("^[a-z0-9][a-z0-9-]*\\.txt$").matches(name))
        assertTrue(TrackSheet.trackId(device, 1_700_000_000).startsWith("$device-"))
        assertNotNull(name)
    }
}
