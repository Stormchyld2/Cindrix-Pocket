package com.cindrix.pocket

import android.content.res.AssetManager
import java.io.File

/** Reads the carried files from the app's assets. */
class AssetSource(private val assets: AssetManager) : ContentSource {
    override fun list(dir: String): List<String> = try { assets.list(dir)?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
    override fun read(path: String): String? = try { assets.open(path).bufferedReader().use { it.readText() } } catch (_: Exception) { null }
    override fun isDir(path: String): Boolean = list(path).isNotEmpty()
}

/** Reads the same files from a folder (used by the tests). */
class FolderSource(private val root: File) : ContentSource {
    override fun list(dir: String): List<String> = File(root, dir).list()?.toList() ?: emptyList()
    override fun read(path: String): String? = File(root, path).takeIf { it.isFile }?.readText()
    override fun isDir(path: String): Boolean = File(root, path).isDirectory
}
