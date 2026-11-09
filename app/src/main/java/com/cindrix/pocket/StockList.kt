package com.cindrix.pocket

/** One line of the stock list Cindrix Ops publishes (read-only here). */
data class StockLine(
    val name: String,
    val barcode: String,
    val unit: String,
    val location: String,
    val qty: Double,
    val useBy: String,
    val category: String,
    val updated: String,
)

/** Reads `#cindrix-stock-list v1` files. Scan never edits the list; changes go back as import files. */
object StockList {
    const val MARKER = "#cindrix-stock-list v1"

    fun splitLine(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    /** Returns null when the text is not a stock list. */
    fun parse(text: String): List<StockLine>? {
        val lines = text.lines().map { it.trimEnd('\r') }
        if (lines.isEmpty() || !lines[0].startsWith(MARKER)) return null
        val header = splitLine(lines.getOrNull(1) ?: return null)
        fun col(n: String) = header.indexOf(n)
        val cName = col("name")
        val cQty = col("qty")
        if (cName < 0 || cQty < 0) return null
        val out = ArrayList<StockLine>()
        for (line in lines.drop(2)) {
            if (line.isBlank()) continue
            val f = splitLine(line)
            fun get(n: String) = col(n).let { if (it >= 0) f.getOrNull(it) ?: "" else "" }
            val q = f.getOrNull(cQty)?.toDoubleOrNull() ?: continue
            out.add(StockLine(f.getOrNull(cName) ?: continue, get("barcode"), get("unit"), get("location"), q, get("use_by"), get("category"), get("updated")))
        }
        return out
    }

    /** Total on hand for a barcode, or null when the barcode is not in the list. */
    fun onHand(lines: List<StockLine>, barcode: String): Pair<Double, String>? {
        if (barcode.isEmpty()) return null
        val hits = lines.filter { it.barcode == barcode }
        if (hits.isEmpty()) return null
        val soonest = hits.map { it.useBy }.filter { it.isNotEmpty() }.minOrNull() ?: ""
        return Pair(hits.sumOf { it.qty }, soonest)
    }

    fun search(lines: List<StockLine>, query: String): List<StockLine> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return lines
        return lines.filter { listOf(it.name, it.location, it.category, it.barcode).any { s -> s.lowercase().contains(q) } }
    }
}
