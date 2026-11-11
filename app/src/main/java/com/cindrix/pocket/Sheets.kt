package com.cindrix.pocket

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One counted line: the Hub's item id, the number the phone saw in its copy, and the number counted. */
data class CountLine(val uid: String, val was: Double, val counted: Double)

/** One check-in line about someone who is already out. */
data class CheckinLine(val id: String, val outing: String, val kind: String, val at: String, val a1: String, val a2: String, val words: String)

/**
 * The count sheet and check-in sheet files the Hub's mailbox reads (docs/45 in Cindrix-Ops;
 * `core/src/countsheet.rs` and `core/src/checkinsheet.rs` there). They wait in the outbox and change nothing
 * until the host accepts them.
 */
object Sheets {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX", Locale.US)

    fun created(now: OffsetDateTime): String = stamp.format(now)

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun num(d: Double): String = if (d == Math.floor(d) && d < 1.0e9) d.toLong().toString() else d.toString()

    fun countName(device: String, epoch: Long) = "$device-count-$epoch.txt"

    fun countSheet(device: String, person: String, now: OffsetDateTime, lines: List<CountLine>): String {
        val sb = StringBuilder()
        sb.append("# format: count-sheet 1\n# device: ").append(device).append("\n# person: ").append(clean(person))
        sb.append("\n# created: ").append(created(now)).append('\n')
        for (l in lines) sb.append(l.uid).append('\t').append(num(l.was)).append('\t').append(num(l.counted)).append('\n')
        return sb.toString()
    }

    fun checkinName(device: String, epoch: Long) = "$device-checkin-$epoch.txt"

    /** The Hub wants ids of lower-case letters, digits and dashes. */
    fun slug(text: String): String {
        val o = StringBuilder()
        for (c in text.trim().lowercase(Locale.US)) {
            if (c in 'a'..'z' || c in '0'..'9') o.append(c) else if (o.isNotEmpty() && o.last() != '-') o.append('-')
        }
        return o.toString().trimEnd('-')
    }

    private fun esc(s: String) = clean(s).replace("\\", "\\\\")

    fun checkinSheet(device: String, person: String, now: OffsetDateTime, lines: List<CheckinLine>): String {
        val sb = StringBuilder()
        sb.append("# format: checkin-sheet 1\n# device: ").append(device).append("\n# person: ").append(clean(person))
        sb.append("\n# created: ").append(created(now)).append('\n')
        for (l in lines) {
            sb.append(l.id).append('\t').append(l.outing).append('\t').append(l.kind).append('\t').append(l.at).append('\t')
            sb.append(esc(l.a1)).append('\t').append(esc(l.a2)).append('\t').append(esc(l.words)).append('\n')
        }
        return sb.toString()
    }

    /**
     * A phone that made sheets or tracks before it joined the Hub used a code made from its callsign. The Hub accepts
     * only its own code, so when the files are sent the old code is swapped for the Hub's. Returns the new name and text.
     */
    fun rebrand(name: String, text: String, newCode: String): Pair<String, String> {
        val old = Regex("(?m)^# device: ([a-z0-9-]+)\\s*$").find(text)?.groupValues?.get(1) ?: return name to text
        if (old == newCode) return name to text
        val out = text.lines().joinToString("\n") { l ->
            when {
                l.startsWith("# device: ") -> "# device: $newCode"
                l.startsWith("# track: $old-") -> "# track: $newCode-" + l.removePrefix("# track: $old-")
                !l.startsWith("#") && l.startsWith("$old-") -> "$newCode-" + l.removePrefix("$old-")
                else -> l
            }
        }
        val newName = if (name.startsWith("$old-")) "$newCode-" + name.removePrefix("$old-") else name
        return newName to out
    }
}
