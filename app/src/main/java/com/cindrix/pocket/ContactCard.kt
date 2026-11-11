package com.cindrix.pocket

import java.io.File

/** The emergency contact card: three blocks of plain words, kept only on this phone. */
data class ContactCard(val contacts: String = "", val plan: String = "", val people: String = "") {
    val isEmpty: Boolean get() = contacts.isBlank() && plan.isBlank() && people.isBlank()
}

/**
 * Stored in the app's private folder (never shared, never put in a folder other apps can read) and removed with
 * everything else when the Hub orders this phone erased.
 */
class ContactCardStore(appDir: File) {
    private val file = File(appDir, "contact-card.txt")

    companion object {
        const val A = "=== CONTACTS ==="
        const val B = "=== PLAN ==="
        const val C = "=== PEOPLE ==="

        fun encode(c: ContactCard): String = listOf(A, c.contacts.trim(), B, c.plan.trim(), C, c.people.trim()).joinToString("\n") + "\n"

        fun decode(text: String): ContactCard {
            val parts = mutableMapOf<String, StringBuilder>()
            var cur: StringBuilder? = null
            for (l in text.replace("\r\n", "\n").split("\n")) {
                when (l.trim()) {
                    A, B, C -> { cur = StringBuilder().also { parts[l.trim()] = it } }
                    else -> cur?.let { if (it.isNotEmpty()) it.append('\n'); it.append(l) }
                }
            }
            fun get(k: String) = parts[k]?.toString()?.trim() ?: ""
            return ContactCard(get(A), get(B), get(C))
        }
    }

    fun load(): ContactCard = try { decode(file.readText(Charsets.UTF_8)) } catch (_: Exception) { ContactCard() }

    fun save(c: ContactCard): Boolean = try {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(encode(c), Charsets.UTF_8)
        tmp.renameTo(file)
    } catch (_: Exception) { false }
}
