package com.example.storagecleaner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.*
import com.example.storagecleaner.security.SecureDelete
import com.example.storagecleaner.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

// ─── Navigation ───────────────────────────────────────────────────────────
private sealed class Screen {
    object FileList   : Screen()
    object RecycleBin : Screen()
    object AppCache   : Screen()
}

// ─── Activity ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private val hasAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val primary = Environment.getExternalStorageDirectory()
        val roots = (listOf(primary) +
            getExternalFilesDirs(null).filterNotNull()
                .map { File(it.path.substringBefore("/Android")) })
            .distinctBy { it.path }.filter { it.exists() && it.canRead() }

        setContent {
            val prefs = remember { getSharedPreferences("storage_cleaner_prefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }

            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MaterialTheme(colorScheme = if (isDark) darkAppColorScheme() else lightAppColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StorageCleanerApp(
                        roots       = roots,
                        primaryPath = primary.path,
                        hasAccess   = hasAccess.value,
                        themeMode   = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            prefs.edit().putString("theme_mode", newMode).apply()
                        },
                        onGrant     = {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName"))
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
        hasAccess.value = Environment.isExternalStorageManager()
    }
}

// ─── Root composable ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanerApp(
    roots: List<File>, primaryPath: String,
    hasAccess: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onGrant: () -> Unit,
    onApps: () -> Unit
) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val pal   = appPalette()

    // ── Navigation state ─────────────────────────────────────────────
    var screen by remember { mutableStateOf<Screen>(Screen.FileList) }

    BackHandler(enabled = screen != Screen.FileList) { screen = Screen.FileList }

    when (val s = screen) {
        is Screen.RecycleBin -> {
            RecycleBinScreen(context = ctx, onBack = { screen = Screen.FileList })
            return
        }
        is Screen.AppCache -> {
            AppCacheScreen(context = ctx, onBack = { screen = Screen.FileList })
            return
        }
        else -> { /* fall through to file list */ }
    }

    // ── File list state ───────────────────────────────────────────────
    var scanning    by remember { mutableStateOf(false) }
    var statusMsg   by remember { mutableStateOf("") }
    var scanResult  by remember { mutableStateOf<ScanResult?>(null) }
    var tabIndex    by remember { mutableStateOf(0) }
    var selected    by remember { mutableStateOf(setOf<String>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf(SecureDelete.Mode.SECURE_WIPE) }
    var feedbackMsg by remember { mutableStateOf("") }
    var feedbackOk  by remember { mutableStateOf(true) }
    var tick        by remember { mutableStateOf(0) }
    var sortIndex   by remember { mutableStateOf(0) }
    var query       by remember { mutableStateOf("") }
    var curDir      by remember { mutableStateOf(if (roots.size == 1) roots[0].path else "") }
    var showChart   by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    // Duplicate comparison sheet
    var comparingItem by remember { mutableStateOf<FileItem?>(null) }

    val rootPaths = remember(roots) { roots.map { it.path }.toSet() }
    val sc        = scanResult

    val meters = remember(tick) {
        roots.map { r ->
            try { val s = StatFs(r.path); Triple(r.path, s.totalBytes, s.availableBytes) }
            catch (_: Exception) { Triple(r.path, 1L, 0L) }
        }
    }
    val dirSizeMap = remember(sc) { if (sc == null) hashMapOf() else dirSizes(sc.files) }
    val tabCounts  = remember(sc) {
        if (sc == null) emptyMap()
        else TABS.associateWith { if (it == "Folders") 0 else listFor(it, sc).size }
    }
    val kindBytes = remember(sc) {
        LongArray(7).also { a -> sc?.files?.forEach { a[it.kind] += it.size } }
    }
    val shown = remember(sc, tabIndex, sortIndex, query, curDir, tick) {
        if (sc == null) emptyList()
        else {
            val base = if (tabIndex == 0) folderChildren(curDir, sc, dirSizeMap, roots)
                       else listFor(TABS[tabIndex], sc)
            val q = query.trim().lowercase()
            val f = if (q.isEmpty()) base else base.filter {
                it.path.substringAfterLast('/').lowercase().contains(q)
            }
            sorted(f, sortIndex)
        }
    }
    val maxSize = remember(shown) { (shown.maxOfOrNull { it.size } ?: 0L).coerceAtLeast(1L) }
    val selInfo = remember(sc, selected) {
        if (sc == null || selected.isEmpty()) 0 to 0L
        else {
            var c = 0; var s = 0L
            sc.files.forEach { f -> if (covered(f.path, selected)) { c++; s += f.size } }
            c to s
        }
    }

    val canGoUp = tabIndex == 0 && curDir.isNotEmpty() &&
        !(roots.size == 1 && curDir in rootPaths)
    fun goUp() { curDir = if (curDir in rootPaths) (if (roots.size > 1) "" else curDir) else parentOf(curDir) }
    BackHandler(enabled = canGoUp) { goUp() }

    // ── Perform delete ────────────────────────────────────────────────
    fun performDelete(targets: List<String>, mode: SecureDelete.Mode) {
        scope.launch {
            scanning  = true
            statusMsg = if (mode == SecureDelete.Mode.SECURE_WIPE) "🔒 Wiping…" else "🗑️ Deleting…"
            var result: SecureDelete.DeleteResult? = null
            var newScan: ScanResult? = null
            withContext(Dispatchers.IO) {
                result  = SecureDelete.deleteAll(ctx, targets, dirSizeMap, mode) { statusMsg = it }
                val tset  = targets.toSet()
                val nf    = sc!!.files.filter    { !covered(it.path, tset) || File(it.path).exists() }
                val ne    = sc.emptyDirs.filter  { !covered(it.path, tset) || File(it.path).exists() }
                val pres  = nf.mapTo(HashSet())  { it.path }
                val newGrp = sc.duplicateGroups.map { g -> g.filter { it.path in pres } }.filter { it.size > 1 }
                newScan = ScanResult(nf, ne, sc.duplicates.filter { it in pres }.toSet(), newGrp)
            }
            scanResult = newScan; selected = emptySet()
            val r = result!!
            feedbackOk  = r.failed == 0
            feedbackMsg = buildString {
                append(if (r.failed == 0) "✅" else "⚠️")
                append(" Deleted ${r.deleted} items, freed ${fmt(r.freedBytes)}")
                if (r.secureWiped > 0)      append(" (${r.secureWiped} securely wiped)")
                if (r.standardDeleted > 0)  append(" (${r.standardDeleted} standard delete)")
                if (r.failed > 0) append(". ${r.failed} could not be deleted (in use or protected).")
            }
            scanning = false; statusMsg = ""; tick++
        }
    }

    // ── Layout ────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(pal.NavyLight),
                            contentAlignment = Alignment.Center) {
                            Text("🧹", fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Storage Cleaner", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("by Niranjan Kumar Singh", fontSize = 10.sp, color = Color.White.copy(0.75f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.Navy),
                actions = {
                    // Scheduled scan reminders
                    IconButton(onClick = { showReminderDialog = true }) {
                        Icon(Icons.Default.Notifications, "Scan Reminders", tint = Color.White)
                    }
                    // Theme switcher (System -> Light -> Dark)
                    IconButton(onClick = {
                        val nextMode = when (themeMode) {
                            "system" -> "light"
                            "light" -> "dark"
                            else -> "system"
                        }
                        onThemeModeChange(nextMode)
                    }) {
                        val icon = when (themeMode) {
                            "light" -> Icons.Default.LightMode
                            "dark" -> Icons.Default.DarkMode
                            else -> Icons.Default.BrightnessAuto
                        }
                        Icon(icon, "Theme: $themeMode", tint = Color.White)
                    }
                    // Recycle bin icon
                    IconButton(onClick = { screen = Screen.RecycleBin }) {
                        Icon(Icons.Default.Delete, "Recycle Bin", tint = Color.White)
                    }
                    // App manager icon
                    IconButton(onClick = { screen = Screen.AppCache }) {
                        Icon(Icons.Outlined.Apps, "App Manager", tint = Color.White)
                    }
                }
            )
        }
    ) { padValues ->
        Column(Modifier.fillMaxSize().padding(padValues)) {

            // ── Storage meters card ───────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((path, total, free) in meters) {
                        StorageBar(
                            label = if (path == primaryPath) "📱 Phone Storage" else "💾 SD Card",
                            used  = total - free, total = total
                        )
                    }
                    // Chart toggle
                    if (sc != null) {
                        TextButton(
                            onClick = { showChart = !showChart },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                if (showChart) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showChart) "Hide breakdown" else "Show storage breakdown",
                                fontSize = 12.sp
                            )
                        }
                        AnimatedVisibility(showChart) {
                            StoragePieChart(
                                kindBytes  = kindBytes,
                                totalBytes = sc.files.sumOf { it.size },
                                modifier   = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // ── Permission wall ───────────────────────────────────────
            if (!hasAccess) { PermissionCard(onGrant = onGrant); return@Column }

            // ── Action row ────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled  = !scanning,
                    onClick  = {
                        scope.launch {
                            scanning = true; selected = emptySet(); feedbackMsg = ""
                            try {
                                scanResult = withContext(Dispatchers.IO) { scan(roots) { statusMsg = it } }
                                curDir     = if (roots.size == 1) roots[0].path else ""
                            } catch (e: Exception) {
                                feedbackMsg = "Scan failed: ${e.message}"; feedbackOk = false
                            }
                            scanning = false; statusMsg = ""; tick++
                        }
                    },
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = pal.Navy),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    if (scanning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(when { scanning -> "Scanning…"; sc == null -> "Scan Storage"; else -> "Rescan" }, fontSize = 14.sp)
                }
            }

            AnimatedVisibility(scanning) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = pal.Accent)
                    Spacer(Modifier.height(4.dp))
                    Text(statusMsg, fontSize = 12.sp, color = pal.TextSecond)
                }
            }

            AnimatedVisibility(feedbackMsg.isNotEmpty()) {
                FeedbackBanner(feedbackMsg, feedbackOk) { feedbackMsg = "" }
            }

            if (sc != null) {
                // ── Summary card ──────────────────────────────────────
                ScanSummaryCard(sc, kindBytes)

                // ── Tab bar ───────────────────────────────────────────
                ScrollableTabRow(
                    selectedTabIndex = tabIndex, edgePadding = 0.dp,
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = pal.Navy,
                    divider          = { HorizontalDivider(color = pal.DividerColor) }
                ) {
                    TABS.forEachIndexed { i, t ->
                        Tab(
                            selected = tabIndex == i,
                            onClick  = { tabIndex = i; query = "" },
                            text = {
                                Text(
                                    if (i == 0) t else "$t (${tabCounts[t] ?: 0})",
                                    fontSize   = 12.sp, maxLines = 1,
                                    fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // ── Search + sort ─────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder  = { Text("Search files…", fontSize = 13.sp) },
                        singleLine   = true,
                        leadingIcon  = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            }
                        },
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    FilterChip(
                        selected = true,
                        onClick  = { sortIndex = (sortIndex + 1) % SORTS.size },
                        label    = { Text(SORTS[sortIndex], fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(14.dp)) }
                    )
                }

                // ── Folder breadcrumb ─────────────────────────────────
                if (tabIndex == 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canGoUp) {
                            IconButton(onClick = { goUp() }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowBack, "Go up", Modifier.size(18.dp))
                            }
                        }
                        Text(
                            if (curDir.isEmpty()) "Storage root"
                            else curDir.removePrefix("/storage/emulated/0").ifEmpty { "/" },
                            fontSize = 11.sp, color = pal.TextSecond,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Count + select-all ────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("${shown.size} items  •  ${fmt(shown.sumOf { it.size })}", fontSize = 11.sp, color = pal.TextSecond)
                    Row {
                        TextButton(
                            onClick = {
                                val paths = shown.map { it.path }
                                selected = if (paths.isNotEmpty() && paths.all { it in selected })
                                    selected - paths.toSet() else selected + paths.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Select all", fontSize = 12.sp) }
                        if (selected.isNotEmpty())
                            TextButton(
                                onClick = { selected = emptySet() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Clear", fontSize = 12.sp, color = pal.Danger) }
                    }
                }

                // ── File list ─────────────────────────────────────────
                LazyColumn(Modifier.weight(1f)) {
                    if (shown.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🎉", fontSize = 40.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Nothing here — this category is empty.", fontSize = 14.sp,
                                        color = pal.TextSecond, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    items(shown, key = { it.path }) { item ->
                        val isDup = item.path in sc.duplicates
                        FileRow(
                            item       = item,
                            isSelected = item.path in selected,
                            maxSize    = maxSize,
                            showBar    = tabIndex == 0,
                            isDup      = isDup,
                            onClick    = {
                                when {
                                    // Folder → navigate
                                    item.isDir && tabIndex == 0 -> curDir = item.path
                                    // Duplicate → open comparison sheet
                                    isDup && tabIndex == 3 /* Duplicates tab */ ->
                                        comparingItem = item
                                    // Otherwise → toggle selection
                                    else -> selected = if (item.path in selected)
                                        selected - item.path else selected + item.path
                                }
                            },
                            onCheck = {
                                selected = if (item.path in selected)
                                    selected - item.path else selected + item.path
                            }
                        )
                        HorizontalDivider(color = pal.DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 72.dp))
                    }
                }

                // ── Delete action bar ─────────────────────────────────
                if (selected.isNotEmpty()) {
                    Surface(
                        modifier        = Modifier.fillMaxWidth(),
                        color           = pal.Navy,
                        shadowElevation = 8.dp
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("${selected.size} selected", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${selInfo.first} files  •  ${fmt(selInfo.second)}", color = Color.White.copy(0.8f), fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Move to Trash
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            scanning = true; statusMsg = "Moving to Recycle Bin…"
                                            var moved = 0; var fail = 0
                                            withContext(Dispatchers.IO) {
                                                for (path in selected) {
                                                    if (RecycleBin.moveToTrash(File(path))) moved++ else fail++
                                                }
                                                // Rebuild scan without moved files
                                                val tset = selected
                                                val nf = sc.files.filter { it.path !in tset || File(it.path).exists() }
                                                val ne = sc.emptyDirs.filter { it.path !in tset || File(it.path).exists() }
                                                val pres = nf.mapTo(HashSet()) { it.path }
                                                val newGrp = sc.duplicateGroups.map { g -> g.filter { it.path in pres } }.filter { it.size > 1 }
                                                scanResult = ScanResult(nf, ne, sc.duplicates.filter { it in pres }.toSet(), newGrp)
                                            }
                                            selected = emptySet()
                                            feedbackOk  = fail == 0
                                            feedbackMsg = "🗑️ Moved $moved items to Recycle Bin" + if (fail > 0) ". $fail failed." else ""
                                            scanning = false; statusMsg = ""; tick++
                                        }
                                    },
                                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border   = BorderStroke(1.dp, Color.White.copy(0.6f))
                                ) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Trash", fontSize = 12.sp)
                                }
                                // Standard delete
                                OutlinedButton(
                                    onClick = { pendingMode = SecureDelete.Mode.STANDARD_DELETE; showConfirm = true },
                                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border   = BorderStroke(1.dp, Color.White.copy(0.6f))
                                ) {
                                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp)
                                }
                                // Secure wipe
                                Button(
                                    onClick  = { pendingMode = SecureDelete.Mode.SECURE_WIPE; showConfirm = true },
                                    modifier = Modifier.weight(1.2f), shape = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = pal.Danger)
                                ) {
                                    Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Wipe", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else if (!scanning) {
                EmptyState()
            }
        }
    }

    // ── Duplicate comparison sheet ────────────────────────────────────────
    val comparing = comparingItem
    if (comparing != null && sc != null) {
        DuplicateCompareSheet(
            item       = comparing,
            scanResult = sc,
            onDelete   = { toDelete, _ ->
                selected = setOf(toDelete)
                pendingMode = SecureDelete.Mode.SECURE_WIPE
                showConfirm = true
            },
            onDismiss = { comparingItem = null }
        )
    }

    // ── Confirm delete dialog ─────────────────────────────────────────────
    if (showConfirm && sc != null) {
        val isSecure = pendingMode == SecureDelete.Mode.SECURE_WIPE
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon  = {
                Icon(if (isSecure) Icons.Default.Shield else Icons.Default.Delete, null,
                    tint = if (isSecure) pal.Danger else pal.Warning)
            },
            title = {
                Text(if (isSecure) "Secure Wipe — Cannot be undone" else "Delete — Cannot be undone",
                    fontWeight = FontWeight.Bold)
            },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${selected.size} items (${selInfo.first} files, ${fmt(selInfo.second)}) will be deleted.", fontSize = 14.sp)
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isSecure) Icons.Default.Security else Icons.Default.Info, null,
                            Modifier.size(16.dp), tint = if (isSecure) pal.Navy else pal.Warning)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isSecure) "DoD 3-pass overwrite (0x00 → 0xFF → random) + fsync. Files cannot be recovered by standard recovery tools."
                            else "Standard OS delete — faster, but file data may still be recoverable by recovery apps.",
                            fontSize = 12.sp, color = pal.TextSecond
                        )
                    }
                    Text("⚠️ Folders will be deleted with all their contents. This cannot be undone.", fontSize = 12.sp, color = pal.Warning)
                }
            },
            dismissButton = { OutlinedButton(onClick = { showConfirm = false }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        performDelete(selected.toList(), pendingMode)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSecure) pal.Danger else pal.Warning)
                ) {
                    Icon(if (isSecure) Icons.Default.DeleteForever else Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSecure) "Secure Wipe" else "Delete")
                }
            }
        )
    }

    // ── Scheduled scan reminder dialog ────────────────────────────────────
    if (showReminderDialog) {
        val currentInterval = ReminderManager.getCurrentInterval(ctx)
        var selectedInterval by remember { mutableStateOf(currentInterval) }

        val notifLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ ->
            ReminderManager.setInterval(ctx, selectedInterval)
        }

        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            icon = { Icon(Icons.Default.Notifications, null, tint = pal.Navy) },
            title = { Text("Scan Reminders", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set periodic reminders to scan storage and remove junk or duplicate files.",
                        fontSize = 13.sp,
                        color = pal.TextSecond
                    )
                    HorizontalDivider()
                    ReminderManager.Interval.values().forEach { interval ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedInterval = interval }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedInterval == interval,
                                onClick = { selectedInterval = interval }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(interval.label, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ReminderManager.getNextReminderText(ctx),
                        fontSize = 11.sp,
                        color = pal.Navy,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = { OutlinedButton(onClick = { showReminderDialog = false }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    onClick = {
                        showReminderDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            selectedInterval != ReminderManager.Interval.OFF
                        ) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReminderManager.setInterval(ctx, selectedInterval)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = pal.Navy)
                ) { Text("Save") }
            }
        )
    }
}

// ─── FileRow ──────────────────────────────────────────────────────────────
@Composable
fun FileRow(item: FileItem, isSelected: Boolean, maxSize: Long, showBar: Boolean,
            isDup: Boolean, onClick: () -> Unit, onCheck: () -> Unit) {
    val pal = appPalette()
    val bg  = if (isSelected) pal.Navy.copy(alpha = 0.06f) else Color.Transparent
    Column(
        Modifier.fillMaxWidth().background(bg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isDir) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(pal.StorageFree),
                    contentAlignment = Alignment.Center) { Text("📁", fontSize = 22.sp) }
            } else {
                FileThumbnail(item.path, item.kind, Modifier)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.path.substringAfterLast('/'),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDup)
                        Badge(containerColor = pal.Warning.copy(0.15f)) {
                            Text("DUP", fontSize = 9.sp, color = pal.Warning, fontWeight = FontWeight.Bold)
                        }
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.path.substringBeforeLast('/').removePrefix("/storage/emulated/0").ifEmpty { "/" },
                        fontSize = 10.sp, color = pal.TextSecond,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.modified > 0)
                        Text("• " + DateFormat.getDateInstance().format(Date(item.modified)),
                            fontSize = 10.sp, color = pal.TextSecond)
                }
                val tagList = buildTagList(item.tags)
                if (tagList.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                        items(tagList) { (label, color) ->
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.12f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(item.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Checkbox(
                    checked = isSelected, onCheckedChange = { onCheck() },
                    modifier = Modifier.size(20.dp).padding(0.dp),
                    colors   = CheckboxDefaults.colors(checkedColor = pal.Navy)
                )
            }
        }
        if (showBar) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(pal.StorageFree)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((item.size.toFloat() / maxSize).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(1.5.dp)).background(pal.Accent))
            }
        }
    }
}

// ─── Helper composables ───────────────────────────────────────────────────
@Composable
fun PermissionCard(onGrant: () -> Unit) {
    val pal = appPalette()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🔒", fontSize = 48.sp)
                Text("Permission Required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Storage Cleaner needs 'All files access' to scan and permanently delete files. Without it, the app cannot see hidden or app-generated files.",
                    fontSize = 13.sp, color = pal.TextSecond, textAlign = TextAlign.Center)
                Button(onClick = onGrant, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pal.Navy),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Grant Access")
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🧹", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("Ready to clean your storage?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Tap 'Scan Storage' to find files you can safely delete.\nImages, videos, duplicates, junk — all in one place.",
                fontSize = 14.sp, color = appPalette().TextSecond,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

@Composable
fun ScanSummaryCard(sc: ScanResult, kindBytes: LongArray) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            SummaryStat("📁", "${sc.files.size}", "files")
            SummaryStat("📋", "${sc.duplicates.size}", "dups")
            SummaryStat("💾", fmt(sc.files.sumOf { it.size }), "total")
            SummaryStat("🗑️", "${sc.emptyDirs.size}", "empty")
        }
    }
}

@Composable
fun SummaryStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 18.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appPalette().Navy)
        Text(label, fontSize = 10.sp, color = appPalette().TextSecond)
    }
}

@Composable
fun FeedbackBanner(msg: String, ok: Boolean, onDismiss: () -> Unit) {
    val pal = appPalette()
    val bg  = if (ok) pal.Success.copy(0.1f) else pal.Danger.copy(0.1f)
    val txt = if (ok) pal.Success else pal.Danger
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(msg, Modifier.weight(1f), fontSize = 13.sp, color = txt)
        IconButton(onClick = onDismiss, Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = txt)
        }
    }
}

fun buildTagList(tags: Int): List<Pair<String, Color>> {
    val list = mutableListOf<Pair<String, Color>>()
    if (tags and T_JUNK != 0) list.add("JUNK" to Color(0xFFD32F2F))
    if (tags and T_SHOT != 0) list.add("SHOT" to Color(0xFF3949AB))
    if (tags and T_DL   != 0) list.add("DL"   to Color(0xFF0097A7))
    if (tags and T_CHAT != 0) list.add("CHAT" to Color(0xFFF57F17))
    if (tags and T_OLD  != 0) list.add("OLD"  to Color(0xFF6B7280))
    return list
}
