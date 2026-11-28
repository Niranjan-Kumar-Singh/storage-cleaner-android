package com.example.storagecleaner.data

import android.content.Context

/**
 * Persists user-configured folder exclusions (skipped during storage scans)
 * and automatic Recycle Bin retention policy.
 */
object ExclusionManager {
    private const val PREF_NAME = "storage_cleaner_prefs"
    private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
    private const val KEY_TRASH_RETENTION_DAYS = "trash_retention_days"

    enum class TrashRetention(val days: Int, val label: String) {
        NEVER(0, "Keep forever"),
        DAYS_7(7, "Auto-erase after 7 days"),
        DAYS_14(14, "Auto-erase after 14 days"),
        DAYS_30(30, "Auto-erase after 30 days")
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getExcludedFolders(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXCLUDED_FOLDERS, emptySet())
            ?.map { normalizePathLexically(it) }
            ?.toSet()
            ?: emptySet()

    fun addExcludedFolder(context: Context, path: String) {
        val norm = normalizePathLexically(path)
        val updated = getExcludedFolders(context).toMutableSet().apply { add(norm) }
        prefs(context).edit().putStringSet(KEY_EXCLUDED_FOLDERS, updated).apply()
    }

    fun removeExcludedFolder(context: Context, path: String) {
        val norm = normalizePathLexically(path)
        val updated = getExcludedFolders(context).toMutableSet().apply { remove(norm) }
        prefs(context).edit().putStringSet(KEY_EXCLUDED_FOLDERS, updated).apply()
    }

    fun getTrashRetention(context: Context): TrashRetention {
        val days = prefs(context).getInt(KEY_TRASH_RETENTION_DAYS, 30)
        return TrashRetention.values().firstOrNull { it.days == days } ?: TrashRetention.DAYS_30
    }

    fun setTrashRetention(context: Context, retention: TrashRetention) {
        prefs(context).edit().putInt(KEY_TRASH_RETENTION_DAYS, retention.days).apply()
    }
}
