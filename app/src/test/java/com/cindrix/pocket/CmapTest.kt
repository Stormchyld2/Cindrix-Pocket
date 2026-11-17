package com.cindrix.pocket

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CmapTest {
    /** A real file written by the Cindrix Ops program's own writer (copied from the map viewer's tests). */
    private fun fixture(): File {
        val f = File.createTempFile("real", ".cmap")
        f.deleteOnExit()
        f.writeBytes(javaClass.getResourceAsStream("/real-core-v1.cmap")!!.readBytes())
        return f
    }

    private fun u32(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    @Test
    fun theFileFromTheMainProgramOpens() {
        val m = Cmap.open(fixture())
        assertEquals(1, m.layers.size)
        assertEquals("street", m.layers[0].kind)
        assertEquals(39.485, m.centerLat, 0.0001)
        assertEquals(-80.139, m.centerLon, 0.0001)
        assertTrue(m.unsupported.isEmpty())
        assertTrue(m.mapJson.contains("Camp"))
    }

    @Test
    fun everyListedTileCanBeFoundAndAnUnlistedOneIsNot() {
        val f = fixture()
        val m = Cmap.open(f)
        val raw = f.readBytes()
        val l = m.layers[0]
        for (i in 0 until l.indexCount.toInt()) {
            val at = l.indexOffset.toInt() + i * 20
            val piece = m.tile("street", u32(raw, at).toInt(), u32(raw, at + 4).toInt(), u32(raw, at + 8).toInt())
            assertNotNull(piece)
            assertEquals(u32(raw, at + 16).toInt(), piece!!.bytes.size)
            assertEquals("application/x-protobuf", piece.mime)
        }
        assertNull(m.tile("street", 3, 1, 1))
        assertNull(m.tile("aerial", 12, 1, 1))
    }

    @Test
    fun lettingFilesAreFoundByTheFirstFontOfAList() {
        val m = Cmap.open(fixture())
        assertEquals("glyphs-a", String(m.glyph("Noto Sans Regular,Noto Sans Medium", "0-255")!!.bytes))
        assertNull(m.glyph("Other", "0-255"))
    }

    @Test
    fun aFileThatIsNotAMapOrNotFinishedIsRefusedInPlainWords() {
        val junk = File.createTempFile("junk", ".cmap").apply { writeText("this is not a map file at all, sorry") ; deleteOnExit() }
        try { Cmap.open(junk); fail() } catch (e: CmapException) { assertEquals("This is not a CindrixOps map file.", e.message) }
        val whole = fixture().readBytes()
        val cut = File.createTempFile("cut", ".cmap").apply { writeBytes(whole.copyOf(whole.size - 30)); deleteOnExit() }
        try { Cmap.open(cut); fail() } catch (e: CmapException) { assertEquals(Cmap.DAMAGED, e.message) }
    }
}
