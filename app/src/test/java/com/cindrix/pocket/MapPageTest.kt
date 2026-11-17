package com.cindrix.pocket

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPageTest {
    private fun fixtureBytes() = javaClass.getResourceAsStream("/real-core-v1.cmap")!!.readBytes()
    private fun fixture(): Cmap {
        val f = File.createTempFile("page", ".cmap")
        f.deleteOnExit()
        f.writeBytes(fixtureBytes())
        return Cmap.open(f)
    }
    private fun u32(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    private val assets: (String) -> java.io.InputStream? = { name -> if (name == "index.html") ByteArrayInputStream("<html>".toByteArray()) else null }

    @Test
    fun theMapsDescriptionCarriesTheNameLayersAndOwnPins() {
        val o = JSONObject(String(MapPage.info(fixture())))
        assertEquals("street", o.getJSONArray("layers").getJSONObject(0).getString("kind"))
        assertEquals("Camp", o.getJSONObject("map").getJSONArray("pins").getJSONObject(0).getString("label"))
        assertEquals(1, o.getInt("glyphs"))
        assertEquals(0, o.getJSONArray("unsupported").length())
    }

    @Test
    fun theWebViewGetsItsPageTilesAndLettering() {
        val m = fixture()
        assertEquals(200, MapPage.answer("/index.html", m, assets).status)
        assertEquals(404, MapPage.answer("/app.js", m, assets).status)
        assertEquals(200, MapPage.answer("/info.json", m, assets).status)
        val raw = fixtureBytes()
        val l = m.layers[0]
        val at = l.indexOffset.toInt()
        val path = "/tiles/street/${u32(raw, at)}/${u32(raw, at + 4)}/${u32(raw, at + 8)}"
        val tile = MapPage.answer(path, m, assets)
        assertEquals(200, tile.status)
        assertEquals(u32(raw, at + 16).toInt(), tile.bytes.size)
        assertEquals(404, MapPage.answer("/tiles/street/2/0/0", m, assets).status)
        assertEquals(200, MapPage.answer("/glyphs/Noto%20Sans%20Regular,Noto%20Sans%20Medium/0-255.pbf", m, assets).status)
        assertEquals(404, MapPage.answer("/tiles/street/x/0/0", m, assets).status)
        assertEquals(404, MapPage.answer("/info.json", null, assets).status)
        assertEquals(404, MapPage.answer("/../../secret", m, assets).status)
    }

    @Test
    fun aMapFileIsKeptOnlyWhenItChecksOut() {
        val dir = File.createTempFile("maps", "").let { it.delete(); it.mkdirs(); it }
        val files = MapFiles(dir)
        val good = files.add("Camp area.cmap", ByteArrayInputStream(fixtureBytes()))
        assertTrue(good.isSuccess)
        assertEquals(listOf("Camp area.cmap"), files.list().map { it.name })
        val bad = files.add("junk.cmap", ByteArrayInputStream("not a map at all, nothing to see here".toByteArray()))
        assertTrue(bad.isFailure)
        assertEquals("This is not a CindrixOps map file.", bad.exceptionOrNull()?.message)
        assertEquals(1, files.list().size)
        assertFalse(File(dir, "maps/incoming.part").exists())
    }

    @Test
    fun fileNamesCannotReachOtherFolders() {
        assertEquals("passwd", MapFiles.safeName("../../etc/passwd"))
        assertEquals("map", MapFiles.safeName("...cmap"))
        assertEquals("Camp area", MapFiles.safeName("Camp area.cmap"))
    }

    @Test
    fun aHubCopyIsKeptOnlyWhenItIsARealMapAndSpaceIsChecked() {
        val dir = File.createTempFile("maps2", "").let { it.delete(); it.mkdirs(); it }
        val files = MapFiles(dir)
        val part = files.partFor("Camp area.cmap")
        part.writeBytes(fixtureBytes())
        val ok = files.adopt(part, "Camp area")
        assertTrue(ok.isSuccess)
        assertTrue(files.has("Camp area.cmap"))
        assertFalse(part.exists())
        val bad = files.partFor("Broken.cmap")
        bad.writeBytes(fixtureBytes().copyOf(60))
        assertTrue(files.adopt(bad, "Broken").isFailure)
        assertFalse(bad.exists())
        assertFalse(files.has("Broken.cmap"))
        assertEquals(null, MapFiles.spaceProblem(10L * 1024 * 1024, 500L * 1024 * 1024))
        assertTrue(MapFiles.spaceProblem(900L * 1024 * 1024, 500L * 1024 * 1024)!!.startsWith("This map needs 900.0 MB"))
    }
}
