package com.example.storagecleaner.data

import android.content.Context
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
 * Metadata stored in a hidden JSON file inside the trash folder.
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

    // ── Move a file or directory to trash ──────────────────────────────
    fun moveToTrash(file: File): Boolean {
        return try {
            val now       = System.currentTimeMillis()
            val trashName = "${now}_${file.name}"
            val dest      = File(trashDir, trashName)
            val moved     = file.renameTo(dest)
            if (moved) {
                val sz      = if (dest.isDirectory) dest.walkTopDown().sumOf { it.length() } else dest.length()
                val current = loadItems().toMutableList()
                current.add(TrashItem(dest, file.absolutePath, now, sz, dest.isDirectory))
                saveItems(current)
            }
            moved
        } catch (_: Exception) { false }
    }

    // ── Restore from trash ─────────────────────────────────────────────
    fun restore(item: TrashItem): Boolean {
        return try {
            val dest = File(item.originalPath)
            dest.parentFile?.mkdirs()
            val ok = item.trashFile.renameTo(dest)
            if (ok) {
                val current = loadItems().toMutableList()
                current.removeAll { it.trashFile.path == item.trashFile.path }
                saveItems(current)
            }
            ok
        } catch (_: Exception) { false }
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
