package com.example.storagecleaner

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

// ---------- data ----------
data class Item(
    val path: String, val size: Long, val modified: Long,
    val dir: Boolean = false, val kind: Int = 0, val tags: Int = 0
)

class Scan(val files: List<Item>, val empty: List<Item>, val dups: Set<String>)

const val T_JUNK = 1
const val T_SHOT = 2
const val T_DL = 4
const val T_CHAT = 8
const val T_OLD = 16

val KINDS = listOf("Other", "Images", "Videos", "Audio", "Docs", "Archives", "APKs")
val TABS = listOf(
    "Folders", "All files", "Large", "Duplicates", "Images", "Videos", "Audio", "Docs",
    "Archives", "APKs", "Downloads", "Screenshots", "Chat media", "Junk", "Old", "Empty folders"
)
val SORTS = listOf("Size ↓", "Newest", "Oldest", "Name")

val EXT = HashMap<String, Int>().apply {
    listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng").forEach { put(it, 1) }
    listOf("mp4", "mkv", "3gp", "mov", "avi", "webm", "m4v", "flv").forEach { put(it, 2) }
    listOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "wma").forEach { put(it, 3) }
    listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "odt", "rtf").forEach { put(it, 4) }
    listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz").forEach { put(it, 5) }
    listOf("apk", "apks", "xapk", "aab").forEach { put(it, 6) }
}
val JUNK_EXT = setOf("tmp", "temp", "log", "bak", "old", "cache", "chk", "dmp", "thumb")

// ---------- helpers ----------
fun fmt(b: Long): String {
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < 4) { v /= 1024; i++ }
    return if (i == 0) "$b B" else "%.1f %s".format(v, u[i])
}

fun parentOf(p: String) = p.substringBeforeLast('/', "")

fun covered(p: String, sel: Set<String>): Boolean {
    var q = p
    while (q.isNotEmpty()) {
        if (q in sel) return true
        q = parentOf(q)
    }
    return false
}

fun makeItem(f: File, size: Long, now: Long): Item {
    val path = f.path
    val low = path.lowercase()
    val name = f.name.lowercase()
    val ext = name.substringAfterLast('.', "")
    val mod = f.lastModified()
    var tags = 0
    if (ext in JUNK_EXT || name.startsWith(".trashed") || name.startsWith(".thumbdata") ||
        "/.thumbnails/" in low || "/lost.dir/" in low || "/.trash" in low
    ) tags = tags or T_JUNK
    if ("/screenshots/" in low) tags = tags or T_SHOT
    if ("/download/" in low) tags = tags or T_DL
    if ("whatsapp" in low || "telegram" in low) tags = tags or T_CHAT
    if (mod > 0 && now - mod > 365L * 24 * 3600 * 1000 && size >= 5_000_000) tags = tags or T_OLD
    return Item(path, size, mod, false, EXT[ext] ?: 0, tags)
}

fun quickHash(f: File): String = try {
    RandomAccessFile(f, "r").use { r ->
        val md = MessageDigest.getInstance("SHA-1")
        val b = ByteArray(65536)
        var n = r.read(b); if (n > 0) md.update(b, 0, n)
        val len = f.length()
        if (len > 131072) { r.seek(len - 65536); n = r.read(b); if (n > 0) md.update(b, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    }
} catch (e: Exception) { f.path }

fun fullHash(f: File): String = try {
    val md = MessageDigest.getInstance("SHA-1")
    val buf = ByteArray(65536)
    f.inputStream().use { s ->
        while (true) { val r = s.read(buf); if (r < 0) break; md.update(buf, 0, r) }
    }
    md.digest().joinToString("") { "%02x".format(it) }
} catch (e: Exception) { f.path }

fun scan(roots: List<File>, status: (String) -> Unit): Scan {
    val now = System.currentTimeMillis()
    val files = ArrayList<Item>()
    val empty = ArrayList<Item>()
    var n = 0
    for (root in roots) {
        root.walkTopDown()
            .onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .forEach { f ->
                try {
                    if (f.isDirectory) {
                        if (f != root) {
                            val l = f.list()
                            if (l != null && l.isEmpty()) empty.add(Item(f.path, 0, f.lastModified(), true))
                        }
                    } else if (f.isFile) {
                        if (++n % 500 == 0) status("Scanning… $n files")
                        files.add(makeItem(f, f.length(), now))
                    }
                } catch (e: Exception) { }
                Unit
            }
    }
    status("Finding duplicates…")
    val dups = HashSet<String>()
    files.filter { it.size in 100_000L..2_000_000_000L }
        .groupBy { it.size }.values.filter { it.size > 1 }
        .forEach { g ->
            g.groupBy { quickHash(File(it.path)) }.values.filter { it.size > 1 }.forEach { q ->
                q.groupBy { fullHash(File(it.path)) }.values.filter { it.size > 1 }.forEach { h ->
                    h.sortedBy { it.modified }.drop(1).forEach { dups.add(it.path) }
                }
            }
        }
    return Scan(files, empty, dups)
}

fun dirSizes(files: List<Item>): HashMap<String, Long> {
    val m = HashMap<String, Long>()
    for (f in files) {
        var q = parentOf(f.path)
        while (q.isNotEmpty()) { m[q] = (m[q] ?: 0L) + f.size; q = parentOf(q) }
    }
    return m
}

fun listFor(t: String, s: Scan): List<Item> = when (t) {
    "All files" -> s.files
    "Large" -> s.files.filter { it.size >= 50_000_000 }
    "Duplicates" -> s.files.filter { it.path in s.dups }
    "Images" -> s.files.filter { it.kind == 1 }
    "Videos" -> s.files.filter { it.kind == 2 }
    "Audio" -> s.files.filter { it.kind == 3 }
    "Docs" -> s.files.filter { it.kind == 4 }
    "Archives" -> s.files.filter { it.kind == 5 }
    "APKs" -> s.files.filter { it.kind == 6 }
    "Downloads" -> s.files.filter { it.tags and T_DL != 0 }
    "Screenshots" -> s.files.filter { it.tags and T_SHOT != 0 }
    "Chat media" -> s.files.filter { it.tags and T_CHAT != 0 }
    "Junk" -> s.files.filter { it.tags and T_JUNK != 0 }
    "Old" -> s.files.filter { it.tags and T_OLD != 0 }
    "Empty folders" -> s.empty
    else -> emptyList()
}

fun children(cur: String, s: Scan, ds: Map<String, Long>, roots: List<File>): List<Item> {
    if (cur.isEmpty()) return roots.map { Item(it.path, ds[it.path] ?: 0L, 0L, true) }
    val out = ArrayList<Item>()
    for ((k, v) in ds) if (parentOf(k) == cur) out.add(Item(k, v, File(k).lastModified(), true))
    for (e in s.empty) if (parentOf(e.path) == cur) out.add(e)
    for (f in s.files) if (parentOf(f.path) == cur) out.add(f)
    return out
}

fun sorted(l: List<Item>, s: Int): List<Item> = when (s) {
    0 -> l.sortedByDescending { it.size }
    1 -> l.sortedByDescending { it.modified }
    2 -> l.sortedBy { it.modified }
    else -> l.sortedBy { it.path.substringAfterLast('/').lowercase() }
}

// ---------- activity ----------
class MainActivity : ComponentActivity() {
    private val access = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val primary = Environment.getExternalStorageDirectory()
        val roots = (listOf(primary) +
            getExternalFilesDirs(null).filterNotNull().map { File(it.path.substringBefore("/Android")) })
            .distinctBy { it.path }.filter { it.exists() && it.canRead() }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1F7A5A))) {
                Surface(Modifier.fillMaxSize()) {
                    App(
                        roots = roots,
                        primaryPath = primary.path,
                        access = access.value,
                        onGrant = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onApps = {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        access.value = Environment.isExternalStorageManager()
    }
}

// ---------- UI ----------
@Composable
fun App(roots: List<File>, primaryPath: String, access: Boolean, onGrant: () -> Unit, onApps: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var scanR by remember { mutableStateOf<Scan?>(null) }
    var tab by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var sortI by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf(if (roots.size == 1) roots[0].path else "") }
    val rootPaths = remember(roots) { roots.map { it.path }.toSet() }
    val sc = scanR

    val meters = remember(tick) {
        roots.map { r ->
            try { val s = StatFs(r.path); Triple(r.path, s.totalBytes, s.availableBytes) }
            catch (e: Exception) { Triple(r.path, 1L, 0L) }
        }
    }
    val dirSize = remember(sc) { if (sc == null) HashMap<String, Long>() else dirSizes(sc.files) }
    val counts = remember(sc) {
        if (sc == null) emptyMap<String, Int>() else TABS.associateWith { if (it == "Folders") 0 else listFor(it, sc).size }
    }
    val kindSize = remember(sc) {
        LongArray(7).also { a -> sc?.files?.forEach { a[it.kind] += it.size } }
    }
    val shown = remember(sc, tab, sortI, query, cur, tick) {
        if (sc == null) emptyList<Item>() else {
            val base = if (tab == 0) children(cur, sc, dirSize, roots) else listFor(TABS[tab], sc)
            val q = query.trim().lowercase()
            val f = if (q.isEmpty()) base else base.filter { it.path.substringAfterLast('/').lowercase().contains(q) }
            sorted(f, sortI)
        }
    }
    val maxSize = remember(shown) { (shown.maxOfOrNull { it.size } ?: 0L).coerceAtLeast(1L) }
    val selInfo = remember(sc, sel) {
        if (sc == null || sel.isEmpty()) 0 to 0L else {
            var c = 0; var s = 0L
            for (f in sc.files) if (covered(f.path, sel)) { c++; s += f.size }
            c to s
        }
    }
    val canUp = tab == 0 && cur.isNotEmpty() && !(roots.size == 1 && cur in rootPaths)
    fun up() { cur = if (cur in rootPaths) (if (roots.size > 1) "" else cur) else parentOf(cur) }
    BackHandler(enabled = canUp) { up() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Storage Cleaner", style = MaterialTheme.typography.titleLarge)
        for ((p, total, free) in meters) {
            val used = total - free
            Text(
                (if (p == primaryPath) "Phone" else "SD card") +
                    ": ${fmt(used)} of ${fmt(total)} used, ${fmt(free)} free",
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
        }

        if (!access) {
            Spacer(Modifier.height(12.dp))
            Text("To scan and delete files, allow \"All files access\" for this app, then come back.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGrant) { Text("Grant access") }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !scanning, onClick = {
                scope.launch {
                    scanning = true; sel = emptySet(); msg = ""
                    try {
                        scanR = withContext(Dispatchers.IO) { scan(roots) { status = it } }
                        cur = if (roots.size == 1) roots[0].path else ""
                    } catch (e: Exception) {
                        msg = "Scan failed: ${e.message}"
                    }
                    scanning = false; status = ""; tick++
                }
            }) { Text(if (scanning) "Scanning…" else if (sc == null) "Scan storage" else "Rescan") }
            OutlinedButton(onClick = onApps) { Text("Apps & cache") }
        }
        if (scanning) Text(status, style = MaterialTheme.typography.bodySmall)
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)

        if (sc != null) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        text = { Text(if (i == 0) t else "$t (${counts[t] ?: 0})") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search name") }, modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { sortI = (sortI + 1) % SORTS.size }) { Text(SORTS[sortI]) }
            }
            if (tab == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = canUp, onClick = { up() }) { Text("↑ Up") }
                    Text(
                        if (cur.isEmpty()) "Storage" else cur.removePrefix("/storage/emulated/0").ifEmpty { "/" },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} items, ${fmt(shown.sumOf { it.size })}", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val p = shown.map { it.path }
                    sel = if (p.isNotEmpty() && p.all { it in sel }) sel - p.toSet() else sel + p
                }) { Text("Select all") }
                if (sel.isNotEmpty()) TextButton(onClick = { sel = emptySet() }) { Text("Clear") }
            }
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        "${sc.files.size} files found, ${fmt(sc.files.sumOf { it.size })}\n" +
                            KINDS.indices.joinToString("  •  ") { "${KINDS[it]} ${fmt(kindSize[it])}" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (shown.isEmpty()) item { Text("Nothing here.", Modifier.padding(16.dp)) }
                items(shown, key = { it.path }) { x ->
                    val checked = x.path in sel
                    val toggle = { sel = if (checked) sel - x.path else sel + x.path }
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            if (x.dir && tab == 0) cur = x.path else toggle()
                        }
                    ) {
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (x.dir) "📁 " else "") + x.path.substringAfterLast('/'),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    parentOf(x.path).removePrefix("/storage/emulated/0").ifEmpty { "/" } +
                                        (if (x.modified > 0) "  •  " + DateFormat.getDateInstance().format(Date(x.modified)) else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(fmt(x.size), Modifier.padding(start = 8.dp))
                        }
                        if (tab == 0) LinearProgressIndicator(
                            progress = { (x.size.toFloat() / maxSize).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp)
                        )
                    }
                }
            }
            Button(
                enabled = sel.isNotEmpty(),
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete ${sel.size} selected (${fmt(selInfo.second)})") }
        }
    }

    if (confirm && sc != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete permanently?") },
            text = {
                Text("${sel.size} selected items (${selInfo.first} files, ${fmt(selInfo.second)}) will be deleted. " +
                    "Folders are deleted with everything inside. This cannot be undone.")
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val targets = sel.filter { !covered(parentOf(it), sel) }
                    scope.launch {
                        scanning = true; status = "Deleting…"
                        var ok = 0; var fail = 0; var freed = 0L
                        var newScan: Scan? = null
                        withContext(Dispatchers.IO) {
                            for (p in targets) {
                                val f = File(p)
                                val isDir = f.isDirectory
                                val sz = if (isDir) (dirSize[p] ?: 0L) else f.length()
                                val done = try { if (isDir) f.deleteRecursively() else f.delete() } catch (e: Exception) { false }
                                if (done) { ok++; freed += sz } else fail++
                            }
                            val tset = targets.toSet()
                            val nf = sc.files.filter { !covered(it.path, tset) || File(it.path).exists() }
                            val ne = sc.empty.filter { !covered(it.path, tset) || File(it.path).exists() }
                            val present = nf.mapTo(HashSet()) { it.path }
                            newScan = Scan(nf, ne, sc.dups.filter { it in present }.toSet())
                            try { MediaScannerConnection.scanFile(ctx, targets.toTypedArray(), null, null) } catch (e: Exception) { }
                        }
                        scanR = newScan
                        sel = emptySet()
                        msg = "Deleted $ok items, freed ${fmt(freed)}" +
                            if (fail > 0) ". $fail could not be deleted (in use or protected)." else "."
                        scanning = false; status = ""; tick++
                    }
                }) { Text("Delete") }
            }
        )
    }
}
