package com.cindrix.pocket

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The inventory import file Cindrix Ops reads (see Cindrix-Formats).
 * Plain CSV, UTF-8, LF line endings, first line is a version marker.
 */
object InventoryCsv {
    const val MARKER = "#cindrix-inventory-import v2"

    val COLUMNS = listOf(
        "scan_id", "action", "item", "barcode", "qty", "unit",
        "use_by", "category", "location", "scanned_at", "source",
    )

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun qtyText(qty: Double): String {
        return if (qty == Math.floor(qty) && !qty.isInfinite()) qty.toLong().toString() else qty.toString()
    }

    fun build(items: List<BatchItem>, source: String): String {
        val sb = StringBuilder()
        sb.append(MARKER).append('\n')
        sb.append(COLUMNS.joinToString(",")).append('\n')
        for (i in items) {
            val row = listOf(
                i.scanId, if (i.remove) "remove" else "add", i.name, i.barcode, qtyText(i.qty), i.unit,
                i.useBy, i.category, i.location, i.scannedAt, source,
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun safe(text: String): String = text.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun fileName(callsign: String, now: Instant): String =
        "inventory-" + safe(callsign) + "-" + stamp.format(now) + ".csv"
}
