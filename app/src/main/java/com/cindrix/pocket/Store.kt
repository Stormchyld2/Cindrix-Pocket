package com.cindrix.pocket

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the batch and the catalog as plain JSON files in the app's private storage,
 * so nothing is lost if the app is closed or the battery dies.
 */
class Store(val dir: File, private val prefs: SharedPreferences) {

    /** The Hub this phone joined with an invite, and its sign-in. Both stay on the phone. */
    var hubBase: String?
        get() = prefs.getString("hub_base", null)
        set(value) { prefs.edit().putString("hub_base", value).apply() }

    var hubToken: String?
        get() = prefs.getString("hub_token", null)
        set(value) { prefs.edit().putString("hub_token", value).apply() }

    /** The Hub's erase order: remove everything Cindrix keeps on this phone. */
    fun wipeEverything() {
        prefs.edit().clear().apply()
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    var callsign: String
        get() {
            val saved = prefs.getString("callsign", null)
            if (saved != null) return saved
            val fresh = Callsign.generate()
            prefs.edit().putString("callsign", fresh).apply()
            return fresh
        }
        set(value) {
            prefs.edit().putString("callsign", value).apply()
        }

    var folderUri: String?
        get() = prefs.getString("folder_uri", null)
        set(value) {
            prefs.edit().putString("folder_uri", value).apply()
        }

    var lastLocation: String
        get() = prefs.getString("last_location", "") ?: ""
        set(value) {
            prefs.edit().putString("last_location", value).apply()
        }

    var fastMode: Boolean
        get() = prefs.getBoolean("fast_mode", false)
        set(value) {
            prefs.edit().putBoolean("fast_mode", value).apply()
        }

    var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(value) {
            prefs.edit().putBoolean("sound_on", value).apply()
        }

    var vibrateOn: Boolean
        get() = prefs.getBoolean("vibrate_on", true)
        set(value) {
            prefs.edit().putBoolean("vibrate_on", value).apply()
        }

    fun loadStockText(): String? = try {
        File(dir, "stock-list.csv").takeIf { it.exists() }?.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun saveStockText(text: String) {
        val tmp = File(dir, "stock-list.csv.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        tmp.renameTo(File(dir, "stock-list.csv"))
    }

    fun loadBatch(): List<BatchItem> =
        readArray("batch.json").mapNotNull { obj -> runCatching { itemFromJson(obj) }.getOrNull() }

    fun saveBatch(items: List<BatchItem>) {
        writeArray("batch.json", items.map { itemToJson(it) })
    }

    fun loadCatalog(): Map<String, CatalogEntry> {
        val out = LinkedHashMap<String, CatalogEntry>()
        for (obj in readArray("catalog.json")) {
            runCatching {
                val entry = CatalogEntry(
                    barcode = obj.getString("barcode"),
                    name = obj.getString("name"),
                    category = obj.optString("category", ""),
                    unit = obj.optString("unit", "ea"),
                    lastUseBy = obj.optString("last_use_by", ""),
                )
                out[entry.barcode] = entry
            }
        }
        return out
    }

    fun saveCatalog(catalog: Map<String, CatalogEntry>) {
        writeArray("catalog.json", catalog.values.map { e ->
            JSONObject()
                .put("barcode", e.barcode)
                .put("name", e.name)
                .put("category", e.category)
                .put("unit", e.unit)
                .put("last_use_by", e.lastUseBy)
        })
    }

    private fun readArray(name: String): List<JSONObject> {
        val file = File(dir, name)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting it.
            file.renameTo(File(dir, "$name.corrupt"))
            emptyList()
        }
    }

    private fun writeArray(name: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(arr.toString(2))
        val dest = File(dir, name)
        if (!tmp.renameTo(dest)) {
            dest.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun itemToJson(i: BatchItem): JSONObject =
        JSONObject()
            .put("scan_id", i.scanId)
            .put("item", i.name)
            .put("barcode", i.barcode)
            .put("qty", i.qty)
            .put("unit", i.unit)
            .put("use_by", i.useBy)
            .put("category", i.category)
            .put("location", i.location)
            .put("scanned_at", i.scannedAt)
            .put("remove", i.remove)

    private fun itemFromJson(o: JSONObject): BatchItem =
        BatchItem(
            scanId = o.getString("scan_id"),
            name = o.getString("item"),
            barcode = o.optString("barcode", ""),
            qty = o.getDouble("qty"),
            unit = o.optString("unit", "ea"),
            useBy = o.optString("use_by", ""),
            category = o.optString("category", ""),
            location = o.optString("location", ""),
            scannedAt = o.optString("scanned_at", ""),
            remove = o.optBoolean("remove", false),
        )
}
