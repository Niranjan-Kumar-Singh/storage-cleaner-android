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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }

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
    onGrant: () -> Unit
) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val pal   = appPalette()

    // ── Navigation state ─────────────────────────────────────────────
    var screen by remember { mutableStateOf<Screen>(Screen.FileList) }

    BackHandler(enabled = screen != Screen.FileList) { screen = Screen.FileList }

    when (screen) {
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

    // Preview and comparison modals
    var previewItem   by remember { mutableStateOf<FileItem?>(null) }
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
            statusMsg = if (mode == SecureDelete.Mode.SECURE_WIPE) "🔒 Secure Wiping (DoD 3-pass)…" else "🗑️ Deleting…"
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
                if (r.secureWiped > 0)      append(" (${r.secureWiped} permanently wiped)")
                if (r.standardDeleted > 0)  append(" (${r.standardDeleted} deleted)")
                if (r.failed > 0) append(". ${r.failed} could not be deleted (system locked).")
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
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(pal.Navy),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Storage Cleaner",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = pal.TextPrimary
                            )
                            Text(
                                "by Niranjan Kumar Singh",
                                fontSize = 10.sp,
                                color = pal.TextSecond
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pal.Background
                ),
                actions = {
                    // Scheduled scan reminders
                    IconButton(onClick = { showReminderDialog = true }) {
                        Icon(Icons.Default.Notifications, "Scan Reminders", tint = pal.TextPrimary)
                    }
                    // Theme switcher
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
                        Icon(icon, "Theme: $themeMode", tint = pal.TextPrimary)
                    }
                    // Recycle bin icon
                    IconButton(onClick = { screen = Screen.RecycleBin }) {
                        Icon(Icons.Default.Delete, "Recycle Bin", tint = pal.TextPrimary)
                    }
                    // App manager icon
                    IconButton(onClick = { screen = Screen.AppCache }) {
                        Icon(Icons.Outlined.Apps, "App Manager", tint = pal.TextPrimary)
                    }
                }
            )
        }
    ) { padValues ->
        Box(Modifier.fillMaxSize().padding(padValues)) {
            if (!hasAccess) {
                PermissionCard(onGrant = onGrant)
            } else {
                Column(Modifier.fillMaxSize()) {

                    // ── Pinned Category Tabs (when scan has completed) ────
                    if (sc != null) {
                        ScrollableTabRow(
                            selectedTabIndex = tabIndex,
                            edgePadding = 12.dp,
                            containerColor = pal.Background,
                            contentColor = pal.Navy,
                            divider = { HorizontalDivider(color = pal.DividerColor.copy(0.6f)) }
                        ) {
                            TABS.forEachIndexed { i, t ->
                                Tab(
                                    selected = tabIndex == i,
                                    onClick  = { tabIndex = i; query = "" },
                                    text = {
                                        Text(
                                            if (i == 0) t else "$t (${tabCounts[t] ?: 0})",
                                            fontSize   = 12.sp, maxLines = 1,
                                            fontWeight = if (tabIndex == i) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tabIndex == i) pal.Navy else pal.TextSecond
                                        )
                                    }
                                )
                            }
                        }

                        // ── Pinned Search + Sort Row ───────────────────────────
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                placeholder  = { Text("Search files…", fontSize = 12.sp) },
                                singleLine   = true,
                                leadingIcon  = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                    }
                                },
                                shape    = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            FilterChip(
                                selected = true,
                                onClick  = { sortIndex = (sortIndex + 1) % SORTS.size },
                                label    = { Text(SORTS[sortIndex], fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(14.dp)) }
                            )
                        }
                    }

                    // ── Main Scrollable Area ────────────────────────────────
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = if (selected.isNotEmpty()) 100.dp else 24.dp)
                    ) {
                        // 1. Scrollable Dashboard Card (Meters + Rescan + Chart)
                        item {
                            Card(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                shape     = RoundedCornerShape(18.dp),
                                colors    = CardDefaults.cardColors(containerColor = pal.CardBg),
                                elevation = CardDefaults.cardElevation(2.dp),
                                border    = BorderStroke(1.dp, pal.DividerColor.copy(0.5f))
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    for ((path, total, free) in meters) {
                                        StorageBar(
                                            label = if (path == primaryPath) "📱 Phone Storage" else "💾 SD Card",
                                            used  = total - free, total = total
                                        )
                                    }

                                    // Scan / Rescan Trigger Button
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
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        if (scanning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            when { scanning -> "Scanning…"; sc == null -> "Scan Storage"; else -> "Rescan Storage" },
                                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
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
                                                null, Modifier.size(16.dp), tint = pal.AccentDark
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (showChart) "Hide breakdown" else "Show storage breakdown",
                                                fontSize = 12.sp, color = pal.AccentDark
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
                        }

                        // 2. Scanning Progress Banner
                        if (scanning) {
                            item {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = pal.Accent)
                                    Spacer(Modifier.height(4.dp))
                                    Text(statusMsg, fontSize = 12.sp, color = pal.TextSecond)
                                }
                            }
                        }

                        // 3. Feedback Banner
                        if (feedbackMsg.isNotEmpty()) {
                            item {
                                FeedbackBanner(feedbackMsg, feedbackOk) { feedbackMsg = "" }
                            }
                        }

                        // 4. Scan Summary Stats
                        if (sc != null) {
                            item {
                                ScanSummaryCard(sc, kindBytes)
                            }

                            // 5. Folder Breadcrumb
                            if (tabIndex == 0) {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (canGoUp) {
                                            IconButton(onClick = { goUp() }, Modifier.size(28.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go up", Modifier.size(16.dp), tint = pal.Navy)
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
                            }

                            // 6. Count and Select-all bar
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${shown.size} items  •  ${fmt(shown.sumOf { it.size })}",
                                        fontSize = 11.sp, color = pal.TextSecond, fontWeight = FontWeight.Medium
                                    )
                                    Row {
                                        TextButton(
                                            onClick = {
                                                val paths = shown.map { it.path }
                                                selected = if (paths.isNotEmpty() && paths.all { it in selected })
                                                    selected - paths.toSet() else selected + paths.toSet()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) { Text("Select all", fontSize = 11.sp, color = pal.Navy) }
                                        if (selected.isNotEmpty()) {
                                            TextButton(
                                                onClick = { selected = emptySet() },
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) { Text("Clear", fontSize = 11.sp, color = pal.Danger) }
                                        }
                                    }
                                }
                            }
                        } else if (!scanning) {
                            item { EmptyState() }
                        }

                        // 7. File List Items
                        if (sc != null) {
                            if (shown.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🎉", fontSize = 36.sp)
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "Clean & Clear!\nNo files found in this category.",
                                                fontSize = 14.sp,
                                                color = pal.TextSecond,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
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
                                                // Directory in folder view → navigate inside
                                                item.isDir && tabIndex == 0 -> curDir = item.path
                                                // Duplicates tab → open side-by-side compare sheet
                                                isDup && tabIndex == 3 -> comparingItem = item
                                                // Any other file → open rich preview dialog so user can see it!
                                                else -> previewItem = item
                                            }
                                        },
                                        onCheck = {
                                            selected = if (item.path in selected)
                                                selected - item.path else selected + item.path
                                        }
                                    )
                                    HorizontalDivider(
                                        color = pal.DividerColor.copy(0.4f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 72.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Floating Selection Action Bar (Glassmorphic Pill) ────────
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = pal.CardBg,
                    shadowElevation = 14.dp,
                    border = BorderStroke(1.dp, pal.GlowIndigo.copy(0.35f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "${selected.size} selected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = pal.TextPrimary
                            )
                            Text(
                                fmt(selInfo.second),
                                fontSize = 11.sp,
                                color = pal.AccentDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { selected = emptySet() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Clear", color = pal.TextSecond, fontSize = 12.sp)
                            }

                            // Move to Trash button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        scanning = true; statusMsg = "Moving to Recycle Bin…"
                                        var moved = 0; var fail = 0
                                        withContext(Dispatchers.IO) {
                                            for (path in selected) {
                                                if (RecycleBin.moveToTrash(File(path))) moved++ else fail++
                                            }
                                            val tset = selected
                                            val nf = sc!!.files.filter { it.path !in tset || File(it.path).exists() }
                                            val ne = sc.emptyDirs.filter { it.path !in tset || File(it.path).exists() }
                                            val pres = nf.mapTo(HashSet()) { it.path }
                                            val newGrp = sc.duplicateGroups.map { g -> g.filter { it.path in pres } }.filter { it.size > 1 }
                                            scanResult = ScanResult(nf, ne, sc.duplicates.filter { it in pres }.toSet(), newGrp)
                                        }
                                        selected = emptySet()
                                        feedbackOk = fail == 0
                                        feedbackMsg = "🗑️ Moved $moved items to Recycle Bin" + if (fail > 0) ". $fail failed." else ""
                                        scanning = false; statusMsg = ""; tick++
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = pal.TextPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Trash", fontSize = 12.sp)
                            }

                            // Permanent Secure Wipe Button
                            Button(
                                onClick = {
                                    pendingMode = SecureDelete.Mode.SECURE_WIPE
                                    showConfirm = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = pal.Danger),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Wipe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Rich File Preview Dialog ──────────────────────────────────────────
    val previewing = previewItem
    if (previewing != null) {
        FilePreviewDialog(
            item = previewing,
            onDismiss = { previewItem = null },
            onTrash = { path ->
                previewItem = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        RecycleBin.moveToTrash(File(path))
                    }
                    if (ok) {
                        feedbackOk = true
                        feedbackMsg = "🗑️ Moved to Recycle Bin: ${path.substringAfterLast('/')}"
                        val nf = sc?.files?.filter { it.path != path } ?: emptyList()
                        val ne = sc?.emptyDirs?.filter { it.path != path } ?: emptyList()
                        val pres = nf.mapTo(HashSet()) { it.path }
                        val newGrp = sc?.duplicateGroups?.map { g -> g.filter { it.path in pres } }?.filter { it.size > 1 } ?: emptyList()
                        scanResult = ScanResult(nf, ne, sc?.duplicates?.filter { it in pres }?.toSet() ?: emptySet(), newGrp)
                        tick++
                    } else {
                        feedbackOk = false
                        feedbackMsg = "Could not move file to Recycle Bin."
                    }
                }
            },
            onDelete = { path, mode ->
                previewItem = null
                performDelete(listOf(path), mode)
            }
        )
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileRow(
    item: FileItem,
    isSelected: Boolean,
    maxSize: Long,
    showBar: Boolean,
    isDup: Boolean,
    onClick: () -> Unit,
    onCheck: () -> Unit
) {
    val pal = appPalette()
    val bg  = if (isSelected) pal.Navy.copy(alpha = 0.08f) else Color.Transparent

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isDir) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(pal.StorageFree),
                    contentAlignment = Alignment.Center
                ) { Text("📁", fontSize = 22.sp) }
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
                    if (isDup) {
                        Spacer(Modifier.width(6.dp))
                        Badge(containerColor = pal.Warning.copy(0.18f)) {
                            Text("DUP", fontSize = 9.sp, color = pal.Warning, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.path.substringBeforeLast('/').removePrefix("/storage/emulated/0").ifEmpty { "/" },
                        fontSize = 11.sp, color = pal.TextSecond,
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
                Spacer(Modifier.height(4.dp))
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onCheck() },
                    modifier = Modifier.size(22.dp),
                    colors   = CheckboxDefaults.colors(checkedColor = pal.Navy)
                )
            }
        }
        if (showBar) {
            Spacer(Modifier.height(5.dp))
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
        Card(
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(6.dp),
            colors = CardDefaults.cardColors(containerColor = pal.CardBg),
            border = BorderStroke(1.dp, pal.DividerColor.copy(0.5f))
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(pal.Navy.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🛡️", fontSize = 36.sp)
                }
                Text("Permission Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
                Text(
                    "Storage Cleaner requires 'All files access' to scan, detect hidden cache, and permanently secure-wipe unwanted files.\n\nYour privacy is completely protected: 100% of processing happens offline on your device.",
                    fontSize = 13.sp, color = pal.TextSecond, textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGrant,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pal.Navy),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Grant All Files Access", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(appPalette().Navy.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🧹", fontSize = 40.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Ready to Clean Your Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = appPalette().TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap 'Scan Storage' above to discover duplicate photos, residual junk files, huge videos, and empty folders.",
                fontSize = 13.sp, color = appPalette().TextSecond,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ScanSummaryCard(sc: ScanResult, kindBytes: LongArray) {
    val pal = appPalette()
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = pal.CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, pal.DividerColor.copy(0.4f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            SummaryStat("📁", "${sc.files.size}", "Files")
            SummaryStat("📋", "${sc.duplicates.size}", "Duplicates")
            SummaryStat("💾", fmt(sc.files.sumOf { it.size }), "Total")
            SummaryStat("🗑️", "${sc.emptyDirs.size}", "Empty")
        }
    }
}

@Composable
fun SummaryStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appPalette().Navy)
        Text(label, fontSize = 10.sp, color = appPalette().TextSecond)
    }
}

@Composable
fun FeedbackBanner(msg: String, ok: Boolean, onDismiss: () -> Unit) {
    val pal = appPalette()
    val bg  = if (ok) pal.Success.copy(0.12f) else pal.Danger.copy(0.12f)
    val txt = if (ok) pal.Success else pal.Danger
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(msg, Modifier.weight(1f), fontSize = 13.sp, color = txt, fontWeight = FontWeight.Medium)
        IconButton(onClick = onDismiss, Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = txt)
        }
    }
}

fun buildTagList(tags: Int): List<Pair<String, Color>> {
    val list = mutableListOf<Pair<String, Color>>()
    if (tags and T_JUNK != 0) list.add("JUNK" to Color(0xFFF43F5E))
    if (tags and T_SHOT != 0) list.add("SHOT" to Color(0xFF6366F1))
    if (tags and T_DL   != 0) list.add("DL"   to Color(0xFF06B6D4))
    if (tags and T_CHAT != 0) list.add("CHAT" to Color(0xFFF59E0B))
    if (tags and T_OLD  != 0) list.add("OLD"  to Color(0xFF94A3B8))
    return list
}
