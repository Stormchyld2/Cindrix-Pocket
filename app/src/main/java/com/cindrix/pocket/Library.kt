package com.cindrix.pocket

// The cards and the Academy that travel inside the app, so they work with no Hub and no network.
// The files are the same plain-text files the Cindrix Ops program reads (content/cards and content/academy).
// Nothing here touches Android, so it is tested on its own.

enum class BlockKind { HEADING, PARAGRAPH, BULLETS, STEPS, NOTE }

data class Block(val kind: BlockKind, val text: String = "", val items: List<String> = emptyList())

data class Card(val id: String, val title: String, val kind: String, val blocks: List<Block>, val draft: Boolean)

data class Question(val prompt: String, val options: List<String>, val correct: Int, val why: String)

data class Lesson(val id: String, val title: String, val minutes: Int, val source: String, val blocks: List<Block>, val quiz: List<Question>)

data class Module(val id: String, val title: String, val summary: String, val lessons: List<Lesson>)

data class Course(val id: String, val title: String, val summary: String, val author: String, val tier: Int, val modules: List<Module>) {
    val lessonCount: Int get() = modules.sumOf { it.lessons.size }
    val minutes: Int get() = modules.sumOf { m -> m.lessons.sumOf { it.minutes } }
}

/** Where the files come from: the app's assets on a phone, a folder in tests. */
interface ContentSource {
    /** Names (not paths) inside a folder; folders and files both. */
    fun list(dir: String): List<String>
    fun read(path: String): String?
    fun isDir(path: String): Boolean
}

object Md {
    /** The header lines between the two `---` lines, and the text after them. */
    fun front(text: String): Pair<Map<String, String>, String> {
        val lines = text.replace("\r\n", "\n").split("\n")
        if (lines.firstOrNull()?.trim() != "---") return emptyMap<String, String>() to text.trim()
        val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return emptyMap<String, String>() to text.trim()
        val map = LinkedHashMap<String, String>()
        for (i in 1 until end) {
            val l = lines[i]
            val c = l.indexOf(':')
            if (c > 0) map[l.substring(0, c).trim().lowercase()] = l.substring(c + 1).trim()
        }
        return map to lines.drop(end + 1).joinToString("\n").trim()
    }

    private val numbered = Regex("^\\d+[.)]\\s+(.*)$")

    /** Headings (`## `), bullets (`- `), numbered steps, notes (`> `) and paragraphs. Pictures and tables are skipped. */
    fun blocks(body: String): List<Block> {
        val out = ArrayList<Block>()
        val para = StringBuilder()
        var list: MutableList<String>? = null
        var listKind = BlockKind.BULLETS
        fun flushPara() {
            if (para.isNotBlank()) out.add(Block(BlockKind.PARAGRAPH, para.toString().trim()))
            para.clear()
        }
        fun flushList() {
            list?.let { if (it.isNotEmpty()) out.add(Block(listKind, items = it.toList())) }
            list = null
        }
        for (raw in body.replace("\r\n", "\n").split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) { flushPara(); flushList(); continue }
            if (line.startsWith("![") || line.startsWith("|")) continue
            if (line.startsWith("#")) {
                flushPara(); flushList()
                out.add(Block(BlockKind.HEADING, line.trimStart('#').trim()))
                continue
            }
            if (line.startsWith("> ") || line == ">") {
                flushPara(); flushList()
                out.add(Block(BlockKind.NOTE, line.removePrefix(">").trim()))
                continue
            }
            val num = numbered.find(line)
            if (num != null) {
                flushPara()
                if (list != null && listKind != BlockKind.STEPS) flushList()
                if (list == null) { list = ArrayList(); listKind = BlockKind.STEPS }
                list!!.add(num.groupValues[1])
                continue
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                flushPara()
                if (list != null && listKind != BlockKind.BULLETS) flushList()
                if (list == null) { list = ArrayList(); listKind = BlockKind.BULLETS }
                list!!.add(line.substring(2).trim())
                continue
            }
            flushList()
            if (para.isNotEmpty()) para.append(' ')
            para.append(line)
        }
        flushPara(); flushList()
        return out
    }

    /** Splits `**bold**` out of a line: pairs of (text, isBold). */
    fun bold(text: String): List<Pair<String, Boolean>> {
        val parts = text.split("**")
        return parts.mapIndexedNotNull { i, t -> if (t.isEmpty()) null else t to (i % 2 == 1) }
    }

    /** Quiz lines after `## Check yourself`: `? question`, `+ right`, `- wrong`, `> why`. */
    fun quiz(text: String): List<Question> {
        val out = ArrayList<Question>()
        var prompt: String? = null
        val opts = ArrayList<String>()
        var right = -1
        var why = ""
        fun flush() {
            val p = prompt
            if (p != null && opts.size >= 2 && right >= 0) out.add(Question(p, opts.toList(), right, why))
            prompt = null; opts.clear(); right = -1; why = ""
        }
        for (raw in text.replace("\r\n", "\n").split("\n")) {
            val l = raw.trim()
            when {
                l.startsWith("? ") -> { flush(); prompt = l.substring(2).trim() }
                l.startsWith("+ ") -> { if (right < 0) right = opts.size; opts.add(l.substring(2).trim()) }
                l.startsWith("- ") -> opts.add(l.substring(2).trim())
                l.startsWith("> ") -> why = l.substring(2).trim()
            }
        }
        flush()
        // Same rule as the program: a quiz has at least three questions, or none.
        return if (out.size >= 3) out else emptyList()
    }
}

class Library(private val src: ContentSource) {
    fun cards(): List<Card> {
        val out = ArrayList<Card>()
        for (name in src.list("cards").sorted()) {
            if (!name.endsWith(".md") || name.startsWith("_")) continue
            val text = src.read("cards/$name") ?: continue
            val (h, body) = Md.front(text)
            val title = h["title"] ?: continue
            out.add(Card(name.removeSuffix(".md"), title, h["kind"] ?: "skills", Md.blocks(body), (h["status"] ?: "") == "draft"))
        }
        return out.sortedWith(compareBy({ it.kind }, { it.title.lowercase() }))
    }

    fun courses(): List<Course> {
        val out = ArrayList<Course>()
        val top = src.list("academy").sorted()
        // The original Survival basics course is the numbered folders sitting directly in the academy folder.
        val loose = top.filter { it[0].isDigit() && src.isDir("academy/$it") }
        if (loose.isNotEmpty()) {
            val mods = loose.mapNotNull { module("academy", it) }
            if (mods.isNotEmpty()) out.add(Course("survival-basics", "Survival basics", "The core course: the first 72 hours to the first months.", "", 3, mods))
        }
        for (name in top) {
            if (name.startsWith("_") || name.startsWith(".") || !src.isDir("academy/$name") || name[0].isDigit()) continue
            val text = src.read("academy/$name/course.md") ?: continue
            val (h, _) = Md.front(text)
            val title = h["title"] ?: continue
            val tier = Regex("[123]").find(h["tier"] ?: "")?.value?.toInt() ?: 0
            val mods = src.list("academy/$name").sorted().filter { !it.startsWith("_") && src.isDir("academy/$name/$it") }.mapNotNull { module("academy/$name", it) }
            if (mods.isNotEmpty()) out.add(Course(name, title, h["summary"] ?: "", h["author"] ?: "", tier, mods))
        }
        return out
    }

    private fun module(parent: String, folder: String): Module? {
        val dir = "$parent/$folder"
        val (h, _) = Md.front(src.read("$dir/_module.md") ?: "")
        val lessons = ArrayList<Lesson>()
        for (f in src.list(dir).sorted()) {
            if (!f.endsWith(".md") || f.startsWith("_")) continue
            val text = src.read("$dir/$f") ?: continue
            val (lh, body) = Md.front(text)
            val title = lh["title"] ?: continue
            val cut = Regex("(?m)^## Check yourself\\s*$").find(body)
            val main = if (cut != null) body.substring(0, cut.range.first) else body
            val quiz = if (cut != null) Md.quiz(body.substring(cut.range.last + 1)) else emptyList()
            lessons.add(Lesson("$dir/$f".removePrefix("academy/").removeSuffix(".md"), title, lh["minutes"]?.toIntOrNull() ?: 5, lh["source"] ?: "", Md.blocks(main), quiz))
        }
        if (lessons.isEmpty()) return null
        return Module(folder, h["title"] ?: folder.drop(3).replace('-', ' ').replaceFirstChar { it.uppercase() }, h["summary"] ?: "", lessons)
    }
}

fun tierName(tier: Int): String = when (tier) {
    3 -> "Tier 3 · Foundation"
    2 -> "Tier 2 · Practitioner"
    1 -> "Tier 1 · Operator"
    else -> "Other courses"
}

fun cardKindName(kind: String): String = when (kind) {
    "medical" -> "Medical"
    "disaster" -> "Disasters"
    "response" -> "Security and response"
    "home" -> "Home"
    "skills" -> "Skills"
    else -> kind.replaceFirstChar { it.uppercase() }
}
