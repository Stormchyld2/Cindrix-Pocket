package com.cindrix.pocket

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

// Reads the phone map file (.cmap) that the Cindrix Ops program writes (Maps > Send to phone).
// Same format and same checks as the standalone Cindrix map viewer (its src-tauri/src/cmap.rs) and
// Cindrix-Ops docs/32-phone-map.md:
//
//   "CMAP0001" | tile data and lettering files | tile lists | manifest (JSON) | manifest length (u32) | "CMAPEND1"
//
// Each tile list entry is five little-endian u32: z, x, y, offset, length. This only reads; it never changes a map file.
// Nothing here touches Android views, so it is tested on its own.

class CmapException(message: String) : Exception(message)

data class CmapLayer(
    val kind: String,
    val tileType: String,
    val compression: String,
    val minZoom: Int,
    val maxZoom: Int,
    val tiles: Long,
    val dataOffset: Long,
    val dataLength: Long,
    val indexOffset: Long,
    val indexCount: Long,
)

data class CmapGlyph(val font: String, val range: String, val offset: Long, val length: Long)

/** One tile or lettering file, with the type to answer it as. */
class CmapPiece(val bytes: ByteArray, val mime: String)

class Cmap private constructor(
    val file: File,
    val name: String,
    /** west, south, east, north */
    val bbox: DoubleArray,
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Double,
    val layers: List<CmapLayer>,
    val glyphs: List<CmapGlyph>,
    /** The person's own pins, route, areas to avoid, drawings and directions, as JSON text, exactly as saved. */
    val mapJson: String,
    private val keys: List<LongArray>,
    private val offsets: List<LongArray>,
    private val lengths: List<LongArray>,
) {
    /** Kinds this reader cannot draw yet (zstd packing needs a decoder). */
    val unsupported: List<String> get() = layers.filter { it.compression == "zstd" }.map { it.kind }

    fun tile(kind: String, z: Int, x: Int, y: Int): CmapPiece? {
        val li = layers.indexOfFirst { it.kind == kind }
        if (li < 0 || z < 0 || x < 0 || y < 0) return null
        val i = keys[li].binarySearch(key(z.toLong(), x.toLong(), y.toLong()))
        if (i < 0) return null
        val layer = layers[li]
        val off = offsets[li][i]
        val len = lengths[li][i]
        if (off + len > layer.dataLength) throw CmapException(DAMAGED)
        return CmapPiece(readAt(layer.dataOffset + off, len.toInt()), mimeOf(layer.tileType))
    }

    /** A lettering file for the first font of a font list such as "Noto Sans Regular,Noto Sans Medium". */
    fun glyph(fontstack: String, range: String): CmapPiece? {
        val first = fontstack.split(',').first().trim()
        val g = glyphs.firstOrNull { it.font == first && it.range == range } ?: return null
        return CmapPiece(readAt(g.offset, g.length.toInt()), "application/x-protobuf")
    }

    private fun readAt(at: Long, len: Int): ByteArray = RandomAccessFile(file, "r").use { f ->
        f.seek(at)
        val b = ByteArray(len)
        try { f.readFully(b) } catch (_: Exception) { throw CmapException(DAMAGED) }
        b
    }

    companion object {
        const val DAMAGED = "This map file is damaged or was not finished copying."
        private const val MAX_MANIFEST = 64L * 1024 * 1024
        /** A phone keeps each layer's tile list in memory (28 bytes a tile here), so a layer is limited. */
        private const val MAX_TILES = 4_000_000L
        private const val MAX_GLYPH = 4L * 1024 * 1024
        private val MAGIC = "CMAP0001".toByteArray(Charsets.US_ASCII)
        private val END = "CMAPEND1".toByteArray(Charsets.US_ASCII)

        fun key(z: Long, x: Long, y: Long): Long = (z shl 50) or (x shl 25) or y

        fun mimeOf(tileType: String) = when (tileType) {
            "mvt" -> "application/x-protobuf"
            "png" -> "image/png"
            "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }

        private fun u32(b: ByteArray, at: Int): Long = ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

        fun open(file: File): Cmap {
            val len = try { file.length() } catch (_: Exception) { throw CmapException("The map file could not be opened.") }
            if (!file.isFile) throw CmapException("The map file could not be opened.")
            if (len < 8 + 12) throw CmapException("This is not a CindrixOps map file.")
            RandomAccessFile(file, "r").use { f ->
                fun read(at: Long, n: Int): ByteArray {
                    f.seek(at)
                    val b = ByteArray(n)
                    try { f.readFully(b) } catch (_: Exception) { throw CmapException(DAMAGED) }
                    return b
                }
                if (!read(0, 8).contentEquals(MAGIC)) throw CmapException("This is not a CindrixOps map file.")
                val foot = read(len - 12, 12)
                if (!foot.copyOfRange(4, 12).contentEquals(END)) throw CmapException(DAMAGED)
                val mlen = u32(foot, 0)
                if (mlen == 0L || mlen > MAX_MANIFEST || mlen + 20 > len) throw CmapException("This map file is damaged.")
                val start = len - 12 - mlen
                val m = try { JSONObject(String(read(start, mlen.toInt()), Charsets.UTF_8)) } catch (_: Exception) {
                    throw CmapException("This map file's description could not be read.")
                }
                if (m.optInt("format", 0) != 1) throw CmapException("This map file was made by a newer Cindrix program. Please update Pocket.")
                val bb = m.optJSONArray("bbox")
                if (bb == null || bb.length() != 4) throw CmapException("This map file's area is not valid.")
                val bbox = DoubleArray(4) { bb.optDouble(it, Double.NaN) }
                if (bbox.any { !it.isFinite() } || bbox[0] >= bbox[2] || bbox[1] >= bbox[3]) throw CmapException("This map file's area is not valid.")
                val view = m.optJSONObject("view")
                val center = view?.optJSONArray("center")
                val lat = center?.optDouble(0, Double.NaN) ?: Double.NaN
                val lon = center?.optDouble(1, Double.NaN) ?: Double.NaN
                val zoom = view?.optDouble("zoom", Double.NaN) ?: Double.NaN
                if (!lat.isFinite() || !lon.isFinite() || !zoom.isFinite()) throw CmapException("This map file's starting view is not valid.")
                val la = m.optJSONArray("layers")
                if (la == null || la.length() == 0) throw CmapException("This map file holds no map.")
                val layers = ArrayList<CmapLayer>()
                for (i in 0 until la.length()) {
                    val o = la.getJSONObject(i)
                    layers.add(
                        CmapLayer(
                            o.optString("kind"), o.optString("tile_type"), o.optString("compression", "none"),
                            o.optInt("min_zoom"), o.optInt("max_zoom"), o.optLong("tiles"),
                            o.optLong("data_offset"), o.optLong("data_length"), o.optLong("index_offset"), o.optLong("index_count"),
                        ),
                    )
                }
                for (l in layers) {
                    val dataEnd = l.dataOffset + l.dataLength
                    val indexEnd = l.indexOffset + l.indexCount * 20
                    val ok = l.dataOffset >= 8 && l.indexOffset >= 8 && l.dataLength >= 0 && l.indexCount in 0..MAX_TILES && dataEnd <= start && indexEnd <= start
                    if (!ok) throw CmapException(if (l.indexCount > MAX_TILES) "This map is too big for a phone to open. Make a smaller area in Cindrix Ops." else "This map file is damaged.")
                }
                val glyphs = ArrayList<CmapGlyph>()
                m.optJSONArray("glyphs")?.let { ga ->
                    for (i in 0 until ga.length()) {
                        val o = ga.getJSONObject(i)
                        val g = CmapGlyph(o.optString("font"), o.optString("range"), o.optLong("offset"), o.optLong("length"))
                        if (g.offset < 8 || g.length < 0 || g.length > MAX_GLYPH || g.offset + g.length > start) throw CmapException("This map file is damaged.")
                        glyphs.add(g)
                    }
                }
                val keys = ArrayList<LongArray>()
                val offs = ArrayList<LongArray>()
                val lens = ArrayList<LongArray>()
                for (l in layers) {
                    val n = l.indexCount.toInt()
                    val raw = read(l.indexOffset, n * 20)
                    var k = LongArray(n)
                    var o = LongArray(n)
                    var ln = LongArray(n)
                    for (i in 0 until n) {
                        val at = i * 20
                        k[i] = key(u32(raw, at), u32(raw, at + 4), u32(raw, at + 8))
                        o[i] = u32(raw, at + 12)
                        ln[i] = u32(raw, at + 16)
                    }
                    // the Cindrix program writes them in order; sort anyway so a lookup never depends on it
                    if ((1 until n).any { k[it - 1] > k[it] }) {
                        val order = (0 until n).sortedBy { k[it] }
                        k = LongArray(n) { k[order[it]] }
                        o = LongArray(n) { o[order[it]] }
                        ln = LongArray(n) { ln[order[it]] }
                    }
                    keys.add(k); offs.add(o); lens.add(ln)
                }
                val own = m.opt("map")
                val mapJson = if (own is JSONObject) own.toString() else "{}"
                return Cmap(file, m.optString("name", file.nameWithoutExtension), bbox, lat, lon, zoom, layers, glyphs, mapJson, keys, offs, lens)
            }
        }
    }
}
