package com.example.storagecleaner.data

// ─── File categories (kind) ───────────────────────────────────────────────
const val KIND_OTHER    = 0
const val KIND_IMAGE    = 1
const val KIND_VIDEO    = 2
const val KIND_AUDIO    = 3
const val KIND_DOC      = 4
const val KIND_ARCHIVE  = 5
const val KIND_APK      = 6

// ─── Tag bitmask ─────────────────────────────────────────────────────────
const val T_JUNK = 1
const val T_SHOT = 2
const val T_DL   = 4
const val T_CHAT = 8
const val T_OLD  = 16

val KINDS = listOf("Other", "Images", "Videos", "Audio", "Docs", "Archives", "APKs")

val TABS = listOf(
    "Folders", "All files", "Large (50MB+)", "Duplicates",
    "Images", "Videos", "Audio", "Docs",
    "Archives", "APKs", "Downloads", "Screenshots",
    "Chat media", "Junk files", "Old files", "Empty folders"
)

val SORTS = listOf("Size ↓", "Newest", "Oldest", "Name A-Z")

/** Extension → kind mapping */
val EXT: HashMap<String, Int> = HashMap<String, Int>().apply {
    listOf("jpg","jpeg","png","gif","webp","heic","heif","bmp","dng","avif","tiff").forEach { put(it, KIND_IMAGE) }
    listOf("mp4","mkv","3gp","mov","avi","webm","m4v","flv","ts","wmv").forEach { put(it, KIND_VIDEO) }
    listOf("mp3","m4a","aac","wav","ogg","opus","flac","amr","wma","mid").forEach { put(it, KIND_AUDIO) }
    listOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","epub","odt","rtf","pages","numbers").forEach { put(it, KIND_DOC) }
    listOf("zip","rar","7z","tar","gz","bz2","xz","zst").forEach { put(it, KIND_ARCHIVE) }
    listOf("apk","apks","xapk","aab").forEach { put(it, KIND_APK) }
}

/** Extensions considered junk */
val JUNK_EXT = setOf(
    "tmp","temp","log","bak","old","cache","chk","dmp","thumb","nomedia","orf","ds_store"
)

/** Folders that must NEVER be deleted (system-critical) */
val SYSTEM_PROTECTED_PATHS = setOf(
    "/storage/emulated/0/Android",
    "/storage/emulated/0/Android/data",
    "/storage/emulated/0/Android/obb",
    "/data", "/system", "/proc", "/sys", "/dev", "/cache"
)

/** Partial path segments that signal system/important content */
val SYSTEM_PATH_SEGMENTS = setOf(
    "/android/data/", "/android/obb/", "/android/media/", "/.android_secure/", "/proc/", "/sys/", "/dev/",
    "/system/", "/vendor/", "/odm/"
)

data class FileItem(
    val path: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean = false,
    val kind: Int = KIND_OTHER,
    val tags: Int = 0
)

data class ScanResult(
    val files: List<FileItem>,
    val emptyDirs: List<FileItem>,
    val duplicates: Set<String>,
    /** Groups of identical files sorted oldest→newest (oldest = kept original) */
    val duplicateGroups: List<List<FileItem>> = emptyList()
)
