package com.cindrix.pocket

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BooksTest {
    private fun tmp(): File = File.createTempFile("books", "").let { it.delete(); it.mkdirs(); it }

    @Test
    fun aSavedBookIsListedAndCanBeRemoved() {
        val b = Books(tmp())
        b.fileFor("b12", "md").writeText("# Hello")
        b.register("b12", "Field\tGuide", "md")
        assertEquals(listOf("Field Guide"), b.list().map { it.title })
        assertTrue(b.has("b12"))
        b.register("b12", "Field Guide 2", "md")
        assertEquals(1, b.list().size)
        b.remove("b12")
        assertFalse(b.has("b12"))
        assertFalse(b.fileFor("b12", "md").exists())
    }

    @Test
    fun anIndexLineWithoutItsFileIsLeftOut() {
        val b = Books(tmp())
        b.register("u9", "Gone", "pdf")
        assertTrue(b.list().isEmpty())
    }

    @Test
    fun idsCannotReachOtherFolders() {
        assertEquals("b1x", Books.safeId("../b1/x"))
        assertTrue(Books.OPENABLE.containsAll(listOf("pdf", "md", "txt")))
    }
}
