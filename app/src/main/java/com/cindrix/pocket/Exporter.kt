package com.cindrix.pocket

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

object Exporter {

    /**
     * Writes the file into inbox/inventory inside the folder the person picked. That is the
     * mailbox Cindrix Ops reads, so pick the Ops "user" folder (or a folder Ops looks at).
     */
    fun saveToFolder(context: Context, treeUri: Uri, name: String, text: String): Result<String> {
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: return Result.failure(Exception("The chosen folder is not available"))
            if (!root.canWrite()) {
                return Result.failure(Exception("No permission to write to the chosen folder"))
            }
            val dir = subfolder(subfolder(root, "inbox"), boxFor(name))
                ?: return Result.failure(Exception("Could not create the inbox folder"))
            dir.findFile(name)?.delete()
            val file = dir.createFile(mimeFor(name), name)
                ?: return Result.failure(Exception("Could not create the file"))
            val out = context.contentResolver.openOutputStream(file.uri)
                ?: return Result.failure(Exception("Could not open the file for writing"))
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            Result.success(file.name ?: name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** True when the phone can make the default folder with no questions asked (Android 10 and newer). */
    fun canUseDefaultFolder(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Which Hub mailbox a file belongs in: inventory lists go to inbox/inventory, tracks to inbox/pocket. */
    fun boxFor(name: String): String = if (name.endsWith(".csv")) "inventory" else "pocket"

    fun mimeFor(name: String): String = if (name.endsWith(".csv")) "text/csv" else "text/plain"

    fun defaultPathFor(name: String): String = "Documents/Cindrix/inbox/" + boxFor(name)

    const val DEFAULT_PATH = "Documents/Cindrix/inbox"

    /** Writes into Documents/Cindrix/inbox/inventory, creating the folders. No folder picking needed. */
    fun saveToDefault(context: Context, name: String, text: String): Result<String> {
        if (!canUseDefaultFolder()) return Result.failure(Exception("This phone needs a folder chosen in Settings"))
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            // Replace an earlier copy of the same file that this app made.
            resolver.delete(
                collection,
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(name, "${defaultPathFor(name)}/"),
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${defaultPathFor(name)}/")
            }
            val uri = resolver.insert(collection, values)
                ?: return Result.failure(Exception("Could not create the file in Documents/Cindrix"))
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: return Result.failure(Exception("Could not open the file for writing"))
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun subfolder(parent: DocumentFile?, name: String): DocumentFile? {
        if (parent == null) return null
        val existing = parent.findFile(name)
        return if (existing != null && existing.isDirectory) existing else parent.createDirectory(name)
    }

    /** Opens the Android share sheet so the file can go out through Cindrix AirLink (or any other app). */
    fun share(context: Context, name: String, text: String): Result<Unit> {
        return try {
            val dir = File(context.cacheDir, "export")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, name)
            file.writeText(text, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeFor(name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Straight to Cindrix AirLink when it is installed; otherwise the usual share list.
            val airlink = "com.cindrix.airlink"
            val installed = try {
                context.packageManager.getPackageInfo(airlink, 0)
                true
            } catch (e: Exception) {
                false
            }
            if (installed) {
                send.setPackage(airlink)
                send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(send)
            } else {
                context.startActivity(Intent.createChooser(send, "Send file"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
