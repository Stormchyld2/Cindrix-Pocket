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
    private val indexes: List<TileIndex>,
) {
    /** Kinds this reader cannot draw: a packing other than none, gzip or zstd (the map page unpacks those). */
    val unsupported: List<String> get() = layers.filter { it.compression !in KNOWN_PACKING }.map { it.kind }

    fun tile(kind: String, z: Int, x: Int, y: Int): CmapPiece? {
        val li = layers.indexOfFirst { it.kind == kind }
        if (li < 0 || z < 0 || x < 0 || y < 0) return null
        val found = indexes[li].find(z.toLong(), x.toLong(), y.toLong()) ?: return null
        val layer = layers[li]
        val off = found.first
        val len = found.second
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
        /** The most tiles one picture may list (20 bytes each, read straight from the file, not kept in memory). */
        private const val MAX_TILES = 40_000_000L
        private val KNOWN_PACKING = setOf("none", "gzip", "zstd")
        private const val MAX_GLYPH = 4L * 1024 * 1024
        private val MAGIC = "CMAP0001".toByteArray(Charsets.US_ASCII)
        private val END = "CMAPEND1".toByteArray(Charsets.US_ASCII)

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
                val indexes = ArrayList<TileIndex>()
                val channel = f.channel
                for (l in layers) indexes.add(TileIndex.open(channel, l.indexOffset, l.indexCount))
                val own = m.opt("map")
                val mapJson = if (own is JSONObject) own.toString() else "{}"
                return Cmap(file, m.optString("name", file.nameWithoutExtension), bbox, lat, lon, zoom, layers, glyphs, mapJson, indexes)
            }
        }
    }
}

/**
 * One picture's tile list (20 bytes a tile: z, x, y, offset, length as little-endian u32), searched where it lies in the
 * file. A list in order (the Cindrix program writes them that way) is searched through a read-only window onto the
 * file, so even a map of tens of millions of tiles takes almost no memory.
 */
internal abstract class TileIndex {
    /** (offset, length) of the tile, or null when the list has none. */
    abstract fun find(z: Long, x: Long, y: Long): Pair<Long, Long>?

    companion object {
        /** The order the lists are in: by z, then x, then y. Valid while x and y stay under 2^25 (zoom 24 and finer is never cut). */
        fun key(z: Long, x: Long, y: Long): Long = (z shl 50) or (x shl 25) or y

        fun open(channel: java.nio.channels.FileChannel, offset: Long, count: Long): TileIndex {
            if (count == 0L) return Mapped(java.nio.ByteBuffer.allocate(0), 0)
            val buf = try {
                channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, offset, count * 20).order(ByteOrder.LITTLE_ENDIAN)
            } catch (_: Throwable) {
                throw CmapException("This map is too big for this phone to open. Make a smaller area in Cindrix Ops.")
            }
            var sorted = true
            var prev = Long.MIN_VALUE
            for (i in 0 until count.toInt()) {
                val k = keyAt(buf, i)
                if (k < prev) { sorted = false; break }
                prev = k
            }
            if (sorted) return Mapped(buf, count.toInt())
            if (count > 4_000_000L) throw CmapException("This map file is not in order and is too big to fix on a phone. Make it again in Cindrix Ops.")
            return Sorted(buf, count.toInt())
        }

        internal fun u32(b: java.nio.ByteBuffer, at: Int): Long = b.getInt(at).toLong() and 0xFFFFFFFFL
        internal fun keyAt(b: java.nio.ByteBuffer, i: Int): Long = key(u32(b, i * 20), u32(b, i * 20 + 4), u32(b, i * 20 + 8))
    }

    private class Mapped(val buf: java.nio.ByteBuffer, val n: Int) : TileIndex() {
        override fun find(z: Long, x: Long, y: Long): Pair<Long, Long>? {
            val want = key(z, x, y)
            var lo = 0
            var hi = n - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val k = keyAt(buf, mid)
                when {
                    k < want -> lo = mid + 1
                    k > want -> hi = mid - 1
                    else -> return u32(buf, mid * 20 + 12) to u32(buf, mid * 20 + 16)
                }
            }
            return null
        }
    }

    /** A list that was not in order, sorted once in memory. */
    private class Sorted(buf: java.nio.ByteBuffer, n: Int) : TileIndex() {
        private val order = (0 until n).sortedBy { keyAt(buf, it) }
        private val keys = LongArray(n) { keyAt(buf, order[it]) }
        private val offs = LongArray(n) { u32(buf, order[it] * 20 + 12) }
        private val lens = LongArray(n) { u32(buf, order[it] * 20 + 16) }
        override fun find(z: Long, x: Long, y: Long): Pair<Long, Long>? {
            val i = keys.binarySearch(key(z, x, y))
            return if (i < 0) null else offs[i] to lens[i]
        }
    }
}
