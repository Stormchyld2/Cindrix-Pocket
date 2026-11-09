package com.cindrix.pocket

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS fix. [t] is seconds since 1970 (UTC), [ele] metres above sea level or null. */
data class TrackPoint(val t: Long, val lat: Double, val lon: Double, val ele: Double?)

data class TrackStats(
    val points: Int = 0,
    val metres: Double = 0.0,
    val climb: Double = 0.0,
    val drop: Double = 0.0,
    val low: Double? = null,
    val high: Double? = null,
    val seconds: Long = 0,
    val start: Long = 0,
)

/**
 * Adds up distance, climb and time one point at a time, so a long walk costs nothing extra per fix.
 * Same rules as the Hub (core/src/tracksheet.rs): a climb or a drop counts only after the height has moved
 * [CLIMB_STEP_M] from the last counted height, so GPS wobble does not add up to a mountain.
 */
class TrackAccumulator {
    private var first: TrackPoint? = null
    private var last: TrackPoint? = null
    private var count = 0
    private var metres = 0.0
    private var climb = 0.0
    private var drop = 0.0
    private var low: Double? = null
    private var high: Double? = null
    private var reference: Double? = null

    fun add(p: TrackPoint) {
        val prev = last
        if (first == null) first = p
        if (prev != null) metres += TrackMath.haversine(prev, p)
        last = p
        count++
        val e = p.ele
        if (e != null) {
            low = low?.let { min(it, e) } ?: e
            high = high?.let { max(it, e) } ?: e
            val r = reference
            if (r == null) {
                reference = e
            } else if (e - r >= TrackMath.CLIMB_STEP_M) {
                climb += e - r
                reference = e
            } else if (r - e >= TrackMath.CLIMB_STEP_M) {
                drop += r - e
                reference = e
            }
        }
    }

    fun stats(): TrackStats {
        val f = first ?: return TrackStats()
        val l = last ?: return TrackStats()
        return TrackStats(count, metres, climb, drop, low, high, l.t - f.t, f.t)
    }
}

object TrackMath {
    const val CLIMB_STEP_M = 3.0

    fun haversine(a: TrackPoint, b: TrackPoint): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = Math.toRadians(b.lat - a.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun stats(points: List<TrackPoint>): TrackStats {
        val acc = TrackAccumulator()
        points.forEach { acc.add(it) }
        return acc.stats()
    }

    fun miles(metres: Double) = metres / 1609.344
    fun feet(metres: Double) = metres * 3.28084

    /** "1 h 05 min" or "12 min". */
    fun span(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) String.format(Locale.US, "%d h %02d min", h, m) else String.format(Locale.US, "%d min", m)
    }
}

/** The file the Hub reads (docs/45 in Cindrix-Ops, `# format: track-sheet 1`). */
object TrackSheet {
    private val created = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).withZone(ZoneOffset.UTC)

    /** The phone's short code in the Hub's file names: lower-case letters and digits, 10 at most. */
    fun deviceCode(callsign: String): String =
        callsign.lowercase(Locale.US).filter { it in 'a'..'z' || it in '0'..'9' }.take(10).ifEmpty { "pocket" }

    fun trackId(device: String, startSeconds: Long) = "$device-t$startSeconds"

    /** Mailbox file names are lower-case letters, digits and dashes ending .txt. */
    fun fileName(device: String, startSeconds: Long) = "$device-track-$startSeconds.txt"

    fun build(device: String, person: String, now: Instant, id: String, outing: String, points: List<TrackPoint>): String {
        val sb = StringBuilder()
        sb.append("# format: track-sheet 1\n")
        sb.append("# device: ").append(device).append('\n')
        sb.append("# person: ").append(person.replace('\t', ' ').replace('\n', ' ').trim()).append('\n')
        sb.append("# created: ").append(created.format(now)).append('\n')
        sb.append("# track: ").append(id).append('\n')
        sb.append("# outing: ").append(outing).append('\n')
        points.forEach { sb.append(line(it)) }
        return sb.toString()
    }

    /** One point as a line, also how the unfinished track is kept on the phone. */
    fun line(p: TrackPoint): String =
        String.format(Locale.US, "%d\t%.6f\t%.6f\t%s\n", p.t, p.lat, p.lon, p.ele?.let { String.format(Locale.US, "%.1f", it) } ?: "")

    fun parseLine(text: String): TrackPoint? {
        val f = text.split('\t')
        if (f.size != 4) return null
        return try {
            TrackPoint(f[0].trim().toLong(), f[1].trim().toDouble(), f[2].trim().toDouble(), f[3].trim().takeIf { it.isNotEmpty() }?.toDouble())
        } catch (e: NumberFormatException) {
            null
        }
    }
}
