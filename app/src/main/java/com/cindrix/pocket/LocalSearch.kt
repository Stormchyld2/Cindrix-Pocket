package com.cindrix.pocket

// Looks through what travels inside Pocket (cards, Academy lessons, plans) and the books kept on the phone,
// so Ask still helps with no Hub. It finds the right pages; it does not write a summary.
// Plain Kotlin, no Android, tested on its own.

data class Hit(
    /** "Card", "Lesson", "Plan" or "Book" */
    val kind: String,
    val title: String,
    /** A short piece of the text around the match. */
    val snippet: String,
    /** The full text to show when the hit is opened. */
    val blocks: List<Block>,
    val score: Int,
)

object LocalSearch {
    private val STOP = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from", "how", "i", "if", "in", "is", "it",
        "my", "of", "on", "or", "should", "the", "to", "we", "what", "when", "where", "which", "who", "why", "with", "you", "your",
    )

    /** Lower-case words of three letters or more, without the little words, and with a plural "s" trimmed. */
    fun words(question: String): List<String> =
        question.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }
            .map { if (it.length > 4 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
            .distinct()

    private fun plain(blocks: List<Block>): String =
        blocks.joinToString("\n") { b -> if (b.items.isNotEmpty()) b.items.joinToString("\n") else b.text }.replace("**", "")

    private fun score(words: List<String>, title: String, body: String): Int {
        val t = title.lowercase()
        val b = body.lowercase()
        var s = 0
        var matched = 0
        for (w in words) {
            var hit = false
            if (t.contains(w)) { s += 6; hit = true }
            val n = countOf(b, w)
            if (n > 0) { s += minOf(n, 5); hit = true }
            if (hit) matched++
        }
        // a hit that covers more of the question's words is worth more
        return if (matched == 0) 0 else s + matched * 3
    }

    private fun countOf(text: String, w: String): Int {
        var n = 0
        var i = text.indexOf(w)
        while (i >= 0 && n < 50) { n++; i = text.indexOf(w, i + w.length) }
        return n
    }

    /** About 160 characters around the first place a word of the question appears. */
    fun snippet(body: String, words: List<String>): String {
        val flat = body.replace(Regex("\\s+"), " ").trim()
        val low = flat.lowercase()
        val at = words.map { low.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val from = maxOf(0, at - 40)
        val to = minOf(flat.length, from + 160)
        return (if (from > 0) "…" else "") + flat.substring(from, to).trim() + (if (to < flat.length) "…" else "")
    }

    private fun make(kind: String, title: String, blocks: List<Block>, words: List<String>): Hit? {
        val body = plain(blocks)
        val s = score(words, title, body)
        return if (s > 0) Hit(kind, title, snippet(body, words), blocks, s) else null
    }

    fun search(
        question: String,
        cards: List<Card>,
        courses: List<Course>,
        plans: List<Plan>,
        books: List<Pair<String, String>> = emptyList(),
        limit: Int = 12,
    ): List<Hit> {
        val w = words(question)
        if (w.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        for (c in cards) make("Card", c.title, c.blocks, w)?.let { out.add(it) }
        for (course in courses) for (m in course.modules) for (l in m.lessons) make("Lesson", l.title, l.blocks, w)?.let { out.add(it) }
        for (p in plans) {
            val blocks = ArrayList<Block>()
            if (p.summary.isNotBlank()) blocks.add(Block(BlockKind.PARAGRAPH, p.summary))
            for ((i, s) in p.steps.withIndex()) {
                blocks.add(Block(BlockKind.HEADING, (i + 1).toString() + ". " + s.title))
                val t = s.text.joinToString(" ").trim()
                if (t.isNotEmpty()) blocks.add(Block(BlockKind.PARAGRAPH, t))
            }
            make("Plan", p.title, blocks, w)?.let { out.add(it) }
        }
        for ((title, text) in books) make("Book", title, Md.blocks(text), w)?.let { out.add(it) }
        return out.sortedByDescending { it.score }.take(limit)
    }
}
