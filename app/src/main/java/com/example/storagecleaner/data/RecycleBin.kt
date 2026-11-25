package com.example.storagecleaner.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.storagecleaner.security.SecureDelete
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A single item in the recycle bin */
data class TrashItem(
    val trashFile:    File,
    val originalPath: String,
    val deletedAt:    Long,
    val size:         Long,
    val isDir:        Boolean = false
)

/**
 * Soft-delete manager.
 * Trash folder: /sdcard/.StorageCleaner_Trash/ (hidden dot-folder on primary storage).
 * Handles both same-volume atomic moves and cross-volume (e.g. SD card) copy+delete fallback.
 */
object RecycleBin {

    private const val TRASH_DIR_NAME = ".StorageCleaner_Trash"
    private const val META_NAME      = ".metadata.json"

    private val trashDir: File
        get() = File(Environment.getExternalStorageDirectory(), TRASH_DIR_NAME).also { it.mkdirs() }

    private val metaFile: File
        get() = File(trashDir, META_NAME)

    // ── Load / save metadata ────────────────────────────────────────────
    fun loadItems(): List<TrashItem> {
        val f = metaFile
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).getJSONArray("entries")
            (0 until arr.length()).mapNotNull { i ->
                val e  = arr.getJSONObject(i)
                val tf = File(trashDir, e.getString("trashName"))
                if (tf.exists()) TrashItem(
                    trashFile    = tf,
                    originalPath = e.getString("originalPath"),
                    deletedAt    = e.getLong("deletedAt"),
                    size         = e.getLong("size"),
                    isDir        = e.optBoolean("isDir", false)
                ) else null
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveItems(items: List<TrashItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().apply {
                put("trashName",    item.trashFile.name)
                put("originalPath", item.originalPath)
                put("deletedAt",   item.deletedAt)
                put("size",        item.size)
                put("isDir",       item.isDir)
            })
        }
        try { metaFile.writeText(JSONObject().apply { put("entries", arr) }.toString(2)) }
        catch (_: Exception) { }
    }

    /**
     * Moves [src] to [dest] using fast atomic `renameTo`, falling back to
     * copy + verify + delete when crossing mount points (e.g., SD card to internal storage).
     */
    internal fun moveSafely(src: File, dest: File): Boolean {
        if (!src.exists()) return false
        dest.parentFile?.mkdirs()
        if (src.renameTo(dest)) return true

        return try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false)
                src.deleteRecursively()
            } else {
                src.copyTo(dest, overwrite = false)
                if (dest.exists() && dest.length() == src.length()) {
                    src.delete()
                } else {
                    dest.delete()
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Builds a collision-free destination file if [desired] already exists. */
    internal fun uniqueDestination(desired: File): File {
        if (!desired.exists()) return desired
        val parent = desired.parentFile
        val base = desired.nameWithoutExtension
        val ext = desired.extension
        var counter = 1
        while (counter < 1000) {
            val candidateName = if (ext.isNotEmpty() && !desired.isDirectory) {
                "${base}_restored_$counter.$ext"
            } else {
                "${desired.name}_restored_$counter"
            }
            val candidate = File(parent, candidateName)
            if (!candidate.exists()) return candidate
            counter++
        }
        return File(parent, "${System.currentTimeMillis()}_${desired.name}")
    }

    // ── Move a file or directory to trash ──────────────────────────────
    fun moveToTrash(file: File, context: Context? = null): Boolean {
        return try {
            if (!file.exists() || isSystemPath(file.path)) return false
            val now       = System.currentTimeMillis()
            val trashName = "${now}_${file.name}"
            val dest      = uniqueDestination(File(trashDir, trashName))
            val moved     = moveSafely(file, dest)
            if (moved) {
                val sz = if (dest.isDirectory) dest.walkTopDown().sumOf { it.length() } else dest.length()
                val current = loadItems().toMutableList()
                current.add(TrashItem(dest, file.absolutePath, now, sz, dest.isDirectory))
                saveItems(current)
                notifyMediaScanner(context, file.absolutePath)
            }
            moved
        } catch (_: Exception) { false }
    }

    // ── Restore from trash ─────────────────────────────────────────────
    fun restore(item: TrashItem, context: Context? = null): Boolean {
        return try {
            val dest = uniqueDestination(File(item.originalPath))
            val ok = moveSafely(item.trashFile, dest)
            if (ok) {
                val current = loadItems().toMutableList()
                current.removeAll { it.trashFile.path == item.trashFile.path }
                saveItems(current)
                notifyMediaScanner(context, dest.absolutePath)
            }
            ok
        } catch (_: Exception) { false }
    }

    private fun notifyMediaScanner(context: Context?, path: String) {
        if (context == null) return
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) { }
    }

    // ── Permanently delete one item from trash ─────────────────────────
    fun deletePermanently(ctx: Context, item: TrashItem, secure: Boolean = true): Boolean {
        return try {
            val result = SecureDelete.deleteAll(
                context    = ctx,
                targets    = listOf(item.trashFile.path),
                dirSizes   = emptyMap(),
                mode       = if (secure) SecureDelete.Mode.SECURE_WIPE else SecureDelete.Mode.STANDARD_DELETE,
                onProgress = {}
            )
            val removed = result.deleted > 0
            if (removed) {
                val current = loadItems().toMutableList()
                current.removeAll { it.trashFile.path == item.trashFile.path }
                saveItems(current)
            }
            removed
        } catch (_: Exception) { false }
    }

    // ── Empty entire trash ─────────────────────────────────────────────
    fun emptyTrash(ctx: Context, secure: Boolean = true): Int {
        val items = loadItems(); var count = 0
        for (item in items) { if (deletePermanently(ctx, item, secure)) count++ }
        try { metaFile.delete() } catch (_: Exception) { }
        return count
    }

    fun totalSize(): Long = loadItems().sumOf { it.size }
}
