package com.example.storagecleaner.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest

// ─── Size formatter ───────────────────────────────────────────────────────
fun fmt(b: Long): String {
    if (b <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = 0
    while (v >= 1024.0 && i < 4) { v /= 1024.0; i++ }
    return if (i == 0) "$b B" else "%.1f %s".format(v, units[i])
}

fun parentOf(p: String): String = p.replace('\\', '/').substringBeforeLast('/', "")

fun covered(p: String, sel: Set<String>): Boolean {
    var q = p.replace('\\', '/')
    while (q.isNotEmpty()) { if (q in sel) return true; q = parentOf(q) }
    return false
}

/**
 * Resolves `.` and `..` path segments lexically so traversal strings like
 * `/storage/emulated/0/Download/../Android/data` are caught accurately even when
 * checking paths that do not exist yet.
 */
fun normalizePathLexically(path: String): String {
    val unified = path.replace('\\', '/')
    val isAbsolute = unified.startsWith("/")
    val stack = ArrayDeque<String>()
    for (part in unified.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (stack.isNotEmpty()) stack.removeLast()
            else -> stack.addLast(part)
        }
    }
    val joined = stack.joinToString("/")
    return if (isAbsolute) "/$joined" else joined
}

fun isSystemPath(path: String): Boolean {
    val norm = normalizePathLexically(path)
    val low = norm.lowercase()
    return SYSTEM_PROTECTED_PATHS.any { prot ->
        norm == prot || norm.startsWith("$prot/")
    } || SYSTEM_PATH_SEGMENTS.any { seg ->
        low.contains(seg) || "$low/".contains(seg)
    }
}

private fun isInternalTrashPath(path: String): Boolean {
    val low = path.replace('\\', '/').lowercase()
    return low.endsWith("/.storagecleaner_trash") || low.contains("/.storagecleaner_trash/")
}

fun makeItem(f: File, now: Long): FileItem {
    val path = f.path; val low = path.lowercase().replace('\\', '/')
    val name = f.name.lowercase(); val ext = name.substringAfterLast('.', "")
    val mod = f.lastModified(); val size = f.length()
    var tags = 0
    if (ext in JUNK_EXT || name.startsWith(".trashed") || name.startsWith(".thumbdata") ||
        "/.thumbnails/" in low || "/lost.dir/" in low || "/.trash" in low || "/.cache" in low)
        tags = tags or T_JUNK
    if ("/screenshots/" in low || "/screenshot/" in low) tags = tags or T_SHOT
    if ("/download/" in low || "/downloads/" in low) tags = tags or T_DL
    if ("whatsapp" in low || "telegram" in low || "signal" in low || "gbwhatsapp" in low) tags = tags or T_CHAT
    if (mod > 0 && now - mod > 365L * 24 * 3600 * 1000 && size >= 5_000_000) tags = tags or T_OLD
    return FileItem(path, size, mod, false, EXT[ext] ?: KIND_OTHER, tags)
}

// ─── Hashing ──────────────────────────────────────────────────────────────
internal fun hash(f: File, full: Boolean): String = try {
    val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(65_536)
    if (full) {
        f.inputStream().use { s -> while (true) { val r = s.read(buf); if (r < 0) break; md.update(buf, 0, r) } }
    } else {
        RandomAccessFile(f, "r").use { r ->
            var n = r.read(buf); if (n > 0) md.update(buf, 0, n)
            val len = f.length()
            if (len > 131_072) { r.seek(len - 65_536); n = r.read(buf); if (n > 0) md.update(buf, 0, n) }
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
} catch (_: Exception) { f.path + "_err" }

// ─── Main scan ────────────────────────────────────────────────────────────
fun scan(
    roots: List<File>,
    isCancelled: () -> Boolean = { false },
    status: (String) -> Unit
): ScanResult {
    val now = System.currentTimeMillis()
    val files = ArrayList<FileItem>(2000); val emptyD = ArrayList<FileItem>(); var n = 0

    for (root in roots) {
        if (isCancelled()) break
        root.walkTopDown()
            .onEnter { dir ->
                !isCancelled() &&
                    !Files.isSymbolicLink(dir.toPath()) &&
                    !isSystemPath(dir.path) &&
                    !isInternalTrashPath(dir.path)
            }
            .forEach { f ->
                if (isCancelled()) return@forEach
                try {
                    if (isInternalTrashPath(f.path)) return@forEach
                    if (f.isDirectory) {
                        if (f != root) {
                            val listing = f.list()
                            if (listing != null && listing.isEmpty())
                                emptyD.add(FileItem(f.path, 0, f.lastModified(), true))
                        }
                    } else if (f.isFile && f.canRead()) {
                        if (++n % 400 == 0) status("Scanning… $n files found")
                        files.add(makeItem(f, now))
                    }
                } catch (_: Exception) { }
            }
    }

    if (isCancelled()) {
        return ScanResult(files, emptyD, emptySet(), emptyList())
    }

    // ── Duplicate detection + group building ─────────────────────────────
    status("Analyzing duplicates across $n files…")
    val dups = HashSet<String>()
    val groups = ArrayList<List<FileItem>>()

    for (sameSize in files.filter { it.size in 1_000L..2_000_000_000L }
        .groupBy { it.size }.values.filter { it.size > 1 }) {
        if (isCancelled()) break
        for (sameQuick in sameSize.groupBy { hash(File(it.path), false) }.values.filter { it.size > 1 }) {
            if (isCancelled()) break
            for (sameFull in sameQuick.groupBy { hash(File(it.path), true) }.values.filter { it.size > 1 }) {
                val sorted = sameFull.sortedBy { it.modified }
                groups.add(sorted)                              // oldest first = "original"
                sorted.drop(1).forEach { dups.add(it.path) }   // rest = duplicates
            }
        }
    }

    return ScanResult(files, emptyD, dups, groups)
}

// ─── Helpers ──────────────────────────────────────────────────────────────
fun dirSizes(files: List<FileItem>): HashMap<String, Long> {
    val m = HashMap<String, Long>()
    for (f in files) {
        var q = parentOf(f.path)
        while (q.isNotEmpty()) { m[q] = (m[q] ?: 0L) + f.size; q = parentOf(q) }
    }
    return m
}

fun listFor(tab: String, s: ScanResult): List<FileItem> = when (tab) {
    "All files"      -> s.files
    "Large (50MB+)"  -> s.files.filter { it.size >= 50_000_000 }
    "Duplicates"     -> s.files.filter { it.path in s.duplicates }
    "Images"         -> s.files.filter { it.kind == KIND_IMAGE }
    "Videos"         -> s.files.filter { it.kind == KIND_VIDEO }
    "Audio"          -> s.files.filter { it.kind == KIND_AUDIO }
    "Docs"           -> s.files.filter { it.kind == KIND_DOC }
    "Archives"       -> s.files.filter { it.kind == KIND_ARCHIVE }
    "APKs"           -> s.files.filter { it.kind == KIND_APK }
    "Downloads"      -> s.files.filter { it.tags and T_DL   != 0 }
    "Screenshots"    -> s.files.filter { it.tags and T_SHOT != 0 }
    "Chat media"     -> s.files.filter { it.tags and T_CHAT != 0 }
    "Junk files"     -> s.files.filter { it.tags and T_JUNK != 0 }
    "Old files"      -> s.files.filter { it.tags and T_OLD  != 0 }
    "Empty folders"  -> s.emptyDirs
    else             -> emptyList()
}

fun folderChildren(cur: String, s: ScanResult, ds: Map<String, Long>, roots: List<File>): List<FileItem> {
    if (cur.isEmpty()) return roots.map { FileItem(it.path, ds[it.path] ?: 0L, 0L, true) }
    val out = ArrayList<FileItem>()
    for ((k, v) in ds) if (parentOf(k) == cur) out.add(FileItem(k, v, File(k).lastModified(), true))
    for (e in s.emptyDirs) if (parentOf(e.path) == cur) out.add(e)
    for (f in s.files) if (parentOf(f.path) == cur) out.add(f)
    return out
}

fun sorted(list: List<FileItem>, sortIndex: Int): List<FileItem> = when (sortIndex) {
    0    -> list.sortedByDescending { it.size }
    1    -> list.sortedByDescending { it.modified }
    2    -> list.sortedBy           { it.modified }
    else -> list.sortedBy           { it.path.replace('\\', '/').substringAfterLast('/').lowercase() }
}
