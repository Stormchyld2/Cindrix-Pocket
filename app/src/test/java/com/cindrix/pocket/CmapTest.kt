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

    /**
     * A map file written the way the Cindrix program writes one: one layer, a tile list of [entries] (z, x, y) in the
     * order given, and a one-byte tile for each (the byte is the entry's position). [compression] goes in the manifest.
     */
    private fun synthetic(entries: Int, order: (Int) -> IntArray, compression: String = "gzip"): File {
        val f = File.createTempFile("big", ".cmap")
        f.deleteOnExit()
        java.io.BufferedOutputStream(f.outputStream(), 1 shl 20).use { out ->
            fun le(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            out.write("CMAP0001".toByteArray())
            for (i in 0 until entries) out.write(i and 0xFF)
            val indexOffset = 8L + entries
            for (i in 0 until entries) {
                val e = order(i)
                out.write(le(e[0].toLong())); out.write(le(e[1].toLong())); out.write(le(e[2].toLong()))
                out.write(le(i.toLong())); out.write(le(1))
            }
            val manifest = """{"format":1,"name":"Synthetic","created":1,"bbox":[-80.2,39.4,-80.0,39.6],"view":{"center":[39.5,-80.1],"zoom":14},
                "layers":[{"kind":"street","tile_type":"mvt","compression":"$compression","min_zoom":14,"max_zoom":14,"tiles":$entries,
                "data_offset":8,"data_length":$entries,"index_offset":$indexOffset,"index_count":$entries}],"glyphs":[],"map":{}}""".toByteArray()
            out.write(manifest)
            out.write(le(manifest.size.toLong()))
            out.write("CMAPEND1".toByteArray())
        }
        return f
    }

    // entry i is tile 14 / (i / 1000) / (i % 1000): in order, and unique
    private val inOrder: (Int) -> IntArray = { i -> intArrayOf(14, i / 1000, i % 1000) }

    @Test
    fun aMapOfManyTilesIsSearchedWhereItLies() {
        val m = Cmap.open(synthetic(200_000, inOrder))
        for (i in listOf(0, 1, 777, 99_999, 150_123, 199_999)) {
            val p = m.tile("street", 14, i / 1000, i % 1000)
            assertNotNull("tile $i", p)
            assertEquals(i and 0xFF, p!!.bytes[0].toInt() and 0xFF)
        }
        assertNull(m.tile("street", 14, 200, 0))
        assertNull(m.tile("street", 13, 0, 0))
    }

    @Test
    fun aMapBiggerThanTheOldFourMillionTileLimitStillOpens() {
        val m = Cmap.open(synthetic(4_200_000, inOrder))
        val last = 4_199_999
        assertEquals(last and 0xFF, m.tile("street", 14, last / 1000, last % 1000)!!.bytes[0].toInt() and 0xFF)
        assertNull(m.tile("street", 14, 4_200, 0))
    }

    @Test
    fun aTileListOutOfOrderIsStillFoundWhenSmall() {
        // reversed on purpose
        val m = Cmap.open(synthetic(5_000, { i -> inOrder(4_999 - i) }))
        val p = m.tile("street", 14, 2, 345)
        assertNotNull(p)
        // entry at position (4999 - 2345) holds the data byte for that position
        assertEquals((4_999 - 2_345) and 0xFF, p!!.bytes[0].toInt() and 0xFF)
    }

    @Test
    fun zstdPackedPicturesAreOfferedToThePageAndOtherPackingIsNot() {
        assertTrue(Cmap.open(synthetic(10, inOrder, "zstd")).unsupported.isEmpty())
        assertEquals(listOf("street"), Cmap.open(synthetic(10, inOrder, "brotli")).unsupported)
    }
}
