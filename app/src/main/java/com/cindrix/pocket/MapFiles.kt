package com.cindrix.pocket

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

/** The phone maps (.cmap files) kept on this phone, in the app's own folder. They work with no Hub. */
class MapFiles(appDir: File) {
    private val dir = File(appDir, "maps").apply { mkdirs() }

    fun list(): List<File> = (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".cmap") }.sortedBy { it.name.lowercase() }

    /**
     * Keeps a copy of a map file read from [input]. The copy is checked before it is kept, so a damaged or wrong file
     * is never added. Returns the saved file, or the plain-words reason it was refused.
     */
    fun add(displayName: String, input: InputStream): Result<File> {
        val tmp = File(dir, "incoming.part")
        return try {
            tmp.outputStream().use { input.copyTo(it) }
            val m = Cmap.open(tmp)
            val dest = File(dir, safeName(displayName.ifBlank { m.name }) + ".cmap")
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw CmapException("The map could not be saved on this phone.")
            Result.success(dest)
        } catch (e: CmapException) {
            tmp.delete()
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(Exception("The map could not be copied. Check there is room on the phone."))
        }
    }

    fun remove(file: File) {
        if (file.parentFile == dir) file.delete()
    }

    companion object {
        /** A file name with nothing that could reach another folder; the `.cmap` ending is left off. */
        fun safeName(name: String): String {
            val base = name.substringAfterLast('/').removeSuffix(".cmap").removeSuffix(".CMAP")
            val clean = base.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }.trim().take(60)
            return clean.ifEmpty { "map" }
        }

        fun sizeText(bytes: Long): String = if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "%d KB".format(maxOf(1L, bytes / 1024))
    }
}

/** What the map page asks the app for, as answers. Kept apart from the web view so it can be tested. */
object MapPage {
    const val HOST = "cmap.pocket"

    class Answer(val status: Int, val mime: String, val bytes: ByteArray)

    private val notFound = Answer(404, "text/plain", ByteArray(0))

    /** The map's description for the page: name, area, layers, the person's pins and so on. */
    fun info(m: Cmap): ByteArray {
        val layers = JSONArray()
        for (l in m.layers) {
            layers.put(JSONObject().put("kind", l.kind).put("tile_type", l.tileType).put("compression", l.compression).put("min_zoom", l.minZoom).put("max_zoom", l.maxZoom))
        }
        val o = JSONObject()
            .put("name", m.name)
            .put("bbox", JSONArray(m.bbox.toList()))
            .put("view", JSONObject().put("center", JSONArray(listOf(m.centerLat, m.centerLon))).put("zoom", m.zoom))
            .put("layers", layers)
            .put("glyphs", m.glyphs.size)
            .put("map", JSONObject(m.mapJson))
            .put("unsupported", JSONArray(m.unsupported))
            .put("metric", false)
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    /** Answers one request to https://cmap.pocket/<path>. [asset] reads a file of the page itself (HTML, script, style). */
    fun answer(path: String, map: Cmap?, asset: (String) -> InputStream?): Answer {
        val p = path.substringBefore('?')
        try {
            when {
                p == "/" || p == "/index.html" -> return page("index.html", "text/html", asset)
                p == "/app.js" -> return page("app.js", "application/javascript", asset)
                p == "/page.css" -> return page("page.css", "text/css", asset)
                p == "/maplibre-gl.css" -> return page("maplibre-gl.css", "text/css", asset)
                p == "/info.json" -> return if (map == null) notFound else Answer(200, "application/json", info(map))
                p.startsWith("/tiles/") -> {
                    val f = p.removePrefix("/tiles/").split('/')
                    if (map == null || f.size != 4) return notFound
                    val piece = map.tile(f[0], f[1].toIntOrNull() ?: return notFound, f[2].toIntOrNull() ?: return notFound, f[3].toIntOrNull() ?: return notFound)
                        ?: return notFound
                    return Answer(200, piece.mime, piece.bytes)
                }
                p.startsWith("/glyphs/") -> {
                    val f = p.removePrefix("/glyphs/").split('/')
                    if (map == null || f.size != 2 || !f[1].endsWith(".pbf")) return notFound
                    val piece = map.glyph(URLDecoder.decode(f[0], "UTF-8"), f[1].removeSuffix(".pbf")) ?: return notFound
                    return Answer(200, piece.mime, piece.bytes)
                }
            }
        } catch (e: CmapException) {
            return Answer(500, "text/plain", (e.message ?: "").toByteArray())
        }
        return notFound
    }

    private fun page(name: String, mime: String, asset: (String) -> InputStream?): Answer {
        val s = asset(name) ?: return notFound
        return Answer(200, mime, s.use { it.readBytes() })
    }
}
