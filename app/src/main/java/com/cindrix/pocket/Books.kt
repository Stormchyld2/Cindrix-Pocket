package com.cindrix.pocket

import java.io.File

/** A book kept on the phone. */
data class SavedBook(val id: String, val title: String, val kind: String, val file: File)

/** What the Hub's shelf lists; only the kinds Pocket can open are offered. */
data class ShelfBook(val id: String, val title: String, val author: String, val kind: String)

/**
 * Books the person chose to keep on the phone (docs/45: the same formats as the Windows version: PDF, Markdown, plain text).
 * They sit in the app's private folder, listed in one small index file, and work with no Hub.
 */
class Books(appDir: File) {
    private val dir = File(appDir, "books").apply { mkdirs() }
    private val index = File(dir, "index.txt")

    companion object {
        val OPENABLE = setOf("pdf", "md", "txt")

        /** `id TAB title TAB kind` per line; tabs and newlines in a title become spaces. */
        fun line(id: String, title: String, kind: String) = id + "\t" + title.replace('\t', ' ').replace('\n', ' ') + "\t" + kind

        fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    fun list(): List<SavedBook> {
        val text = try { index.readText(Charsets.UTF_8) } catch (_: Exception) { return emptyList() }
        return text.lines().mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 3) return@mapNotNull null
            val file = fileFor(f[0], f[2])
            if (file.isFile) SavedBook(f[0], f[1], f[2], file) else null
        }.sortedBy { it.title.lowercase() }
    }

    fun fileFor(id: String, kind: String) = File(dir, safeId(id) + "." + kind.lowercase())

    fun has(id: String) = list().any { it.id == id }

    /** Call after the file for `id` has been written. */
    fun register(id: String, title: String, kind: String) {
        val others = try { index.readLines(Charsets.UTF_8) } catch (_: Exception) { emptyList() }.filter { it.isNotBlank() && !it.startsWith(id + "\t") }
        index.writeText((others + line(id, title, kind)).joinToString("\n") + "\n", Charsets.UTF_8)
    }

    fun remove(id: String) {
        val b = list().firstOrNull { it.id == id }
        b?.file?.delete()
        val others = try { index.readLines(Charsets.UTF_8) } catch (_: Exception) { emptyList() }.filter { it.isNotBlank() && !it.startsWith(id + "\t") }
        index.writeText(others.joinToString("\n") + (if (others.isEmpty()) "" else "\n"), Charsets.UTF_8)
    }
}
