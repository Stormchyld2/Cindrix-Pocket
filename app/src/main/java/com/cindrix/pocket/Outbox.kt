package com.cindrix.pocket

import java.io.File

/**
 * Files that are ready to go to Cindrix Ops but have not been delivered yet. A file stays here
 * until it has been written into the Ops folder (or deleted by hand), so a batch is never lost
 * because the phone and the base camp computer were not connected at the same time.
 * Delivered files are kept in "sent" (the newest 20) in case something went wrong on the other side.
 */
class Outbox(private val appDir: File) {
    private val dir = File(appDir, "outbox").apply { mkdirs() }
    private val sent = File(dir, "sent")

    fun list(): List<String> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".csv") || it.name.endsWith(".txt")) }
            .map { it.name }
            .sorted()

    fun add(name: String, text: String): Boolean = try {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        tmp.renameTo(File(dir, name))
    } catch (e: Exception) {
        false
    }

    fun read(name: String): String? = try {
        File(dir, safe(name)).readText(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun markDelivered(name: String) {
        try {
            sent.mkdirs()
            File(dir, safe(name)).renameTo(File(sent, safe(name)))
            (sent.listFiles() ?: emptyArray()).sortedBy { it.name }.dropLast(20).forEach { it.delete() }
        } catch (e: Exception) {
            // leave it in the outbox rather than lose it
        }
    }

    fun delete(name: String) {
        File(dir, safe(name)).delete()
    }

    private fun safe(name: String) = name.substringAfterLast('/')
}
