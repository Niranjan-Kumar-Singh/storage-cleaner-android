package com.example.storagecleaner

import android.app.Activity
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.*
import com.example.storagecleaner.security.SecureDelete
import com.example.storagecleaner.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
            .distinctBy { file -> runCatching { file.canonicalPath }.getOrElse { file.path } }
            .filter { it.exists() && it.canRead() }

        setContent {
            val prefs = remember { getSharedPreferences("storage_cleaner_prefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }

            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val activePalette = if (isDark) DarkPalette else LightPalette
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    window.statusBarColor = activePalette.Background.toArgb()
                    window.navigationBarColor = activePalette.Surface.toArgb()
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }

            CompositionLocalProvider(LocalAppPalette provides activePalette) {
                MaterialTheme(colorScheme = if (isDark) darkAppColorScheme() else lightAppColorScheme()) {
                    Surface(Modifier.fillMaxSize(), color = activePalette.Background) {
                        StorageCleanerApp(
                            roots = roots,
                            primaryPath = primary.path,
                            hasAccess = hasAccess.value,
                            themeMode = themeMode,
                            onThemeModeChange = { newMode ->
                                themeMode = newMode
                                prefs.edit().putString("theme_mode", newMode).apply()
                            },
                            onGrant = {
                                try {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                } catch (_: Exception) {
                                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess.value = try {
            Environment.isExternalStorageManager()
        } catch (_: Exception) {
            false
        }
    }
}

// ─── Root composable ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanerApp(
    roots: List<File>,
    primaryPath: String,
    hasAccess: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onGrant: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pal = appPalette()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // ── Navigation state ─────────────────────────────────────────────
    var screen by remember { mutableStateOf<Screen>(Screen.FileList) }
    var tick by remember { mutableStateOf(0) }
    var trashCount by remember { mutableStateOf(0) }

    LaunchedEffect(screen, tick) {
        trashCount = withContext(Dispatchers.IO) { RecycleBin.loadItems().size }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && screen != Screen.FileList) {
        screen = Screen.FileList
        tick++
    }

    when (screen) {
        is Screen.RecycleBin -> {
            RecycleBinScreen(context = ctx, onBack = {
                screen = Screen.FileList
                tick++
            })
            return
        }
        is Screen.AppCache -> {
            AppCacheScreen(context = ctx, onBack = {
                screen = Screen.FileList
                tick++
            })
            return
        }
        else -> Unit
    }

    // ── File list state ───────────────────────────────────────────────
    var scanning by remember { mutableStateOf(false) }
    var scanCancelled by remember { mutableStateOf(false) }
    val statusFlow = remember { MutableStateFlow("") }
    val statusMsg by statusFlow.collectAsState()
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var tabIndex by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingDeleteTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf(SecureDelete.Mode.SECURE_WIPE) }
    var feedbackMsg by remember { mutableStateOf("") }
    var feedbackOk by remember { mutableStateOf(true) }
    var sortIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var curDir by remember { mutableStateOf(if (roots.size == 1) roots[0].path else "") }
    var showChart by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var previewItem by remember { mutableStateOf<FileItem?>(null) }
    var comparingItem by remember { mutableStateOf<FileItem?>(null) }

    val rootPaths = remember(roots) { roots.map { it.path }.toSet() }
    val sc = scanResult

    val meters = remember(tick) {
        roots.map { r ->
            try { val s = StatFs(r.path); Triple(r.path, s.totalBytes, s.availableBytes) }
            catch (_: Exception) { Triple(r.path, 1L, 0L) }
        }
    }
    val dirSizeMap = remember(sc) { if (sc == null) hashMapOf() else dirSizes(sc.files) }
    val tabCounts = remember(sc) {
        if (sc == null) emptyMap()
        else TABS.associateWith { if (it == "Folders") 0 else listFor(it, sc).size }
    }
    val kindBytes = remember(sc) {
        LongArray(7).also { a -> sc?.files?.forEach { a[it.kind] += it.size } }
    }
    val smartInsights = remember(sc) {
        if (sc == null) emptyList()
        else {
            val junkSize = sc.files.filter { it.tags and T_JUNK != 0 }.sumOf { it.size }
            val dupSize = sc.files.filter { it.path in sc.duplicates }.sumOf { it.size }
            val apkSize = sc.files.filter { it.kind == KIND_APK }.sumOf { it.size }
            buildList {
                if (junkSize > 0L) add(Triple("Junk files", fmt(junkSize), TABS.indexOf("Junk files")))
                if (dupSize > 0L) add(Triple("Duplicate copies", fmt(dupSize), TABS.indexOf("Duplicates")))
                if (apkSize > 0L) add(Triple("Installer APKs", fmt(apkSize), TABS.indexOf("APKs")))
                if (sc.emptyDirs.isNotEmpty()) add(Triple("${sc.emptyDirs.size} Empty folders", "0 B junk", TABS.indexOf("Empty folders")))
            }
        }
    }

    val shown = remember(sc, tabIndex, sortIndex, query, curDir, tick) {
        if (sc == null) emptyList()
        else {
            val base = if (tabIndex == 0) folderChildren(curDir, sc, dirSizeMap, roots)
            else listFor(TABS[tabIndex], sc)
            val q = query.trim().lowercase()
            val f = if (q.isEmpty()) base else base.filter {
                it.path.replace('\\', '/').substringAfterLast('/').lowercase().contains(q)
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
            scanning = true
            statusFlow.value = if (mode == SecureDelete.Mode.SECURE_WIPE) "Secure wiping (DoD 3-pass)…" else "Deleting…"
            var result: SecureDelete.DeleteResult? = null
            var newScan: ScanResult? = null
            withContext(Dispatchers.IO) {
                result = SecureDelete.deleteAll(ctx, targets, dirSizeMap, mode) { statusFlow.value = it }
                val tset = targets.toSet()
                val nf = sc!!.files.filter { !covered(it.path, tset) || File(it.path).exists() }
                val ne = sc.emptyDirs.filter { !covered(it.path, tset) || File(it.path).exists() }
                val pres = nf.mapTo(HashSet()) { it.path }
                val newGrp = sc.duplicateGroups.map { g -> g.filter { it.path in pres } }.filter { it.size > 1 }
                newScan = ScanResult(nf, ne, sc.duplicates.filter { it in pres }.toSet(), newGrp)
            }
            scanResult = newScan
            selected = emptySet()
            pendingDeleteTargets = emptyList()
            val r = result!!
            feedbackOk = r.failed == 0
            feedbackMsg = buildString {
                append("Deleted ${r.deleted} items, freed ${fmt(r.freedBytes)}")
                if (r.secureWiped > 0) append(" (${r.secureWiped} permanently wiped)")
                if (r.standardDeleted > 0) append(" (${r.standardDeleted} deleted)")
                if (r.failed > 0) append(". ${r.failed} could not be deleted (system protected or locked).")
            }
            scanning = false
            statusFlow.value = ""
            tick++
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentScreen = when (screen) {
                    is Screen.RecycleBin -> "RecycleBin"
                    is Screen.AppCache -> "AppCache"
                    else -> "FileList"
                },
                trashCount = trashCount,
                themeMode = themeMode,
                onSelectScreen = { sel ->
                    screen = when (sel) {
                        "RecycleBin" -> Screen.RecycleBin
                        "AppCache" -> Screen.AppCache
                        else -> Screen.FileList
                    }
                },
                onThemeModeChange = onThemeModeChange,
                onShowReminders = { showReminderDialog = true },
                onCheckUpdates = { showUpdateDialog = true },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = pal.Background,
            topBar = {
                Column(Modifier.background(pal.Background)) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Storage Cleaner",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = pal.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Open Menu", tint = pal.TextPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.Background),
                        actions = {
                            IconButton(onClick = { screen = Screen.RecycleBin }) {
                                BadgedBox(
                                    badge = {
                                        if (trashCount > 0) {
                                            Badge(containerColor = pal.Navy, contentColor = Color.White) {
                                                Text(
                                                    text = if (trashCount > 99) "99+" else "$trashCount",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, "Recycle Bin", tint = pal.TextPrimary)
                                }
                            }
                            IconButton(onClick = {
                                val nextMode = when (themeMode) {
                                    "system" -> "dark"
                                    "dark" -> "light"
                                    else -> "system"
                                }
                                onThemeModeChange(nextMode)
                            }) {
                                val icon = when (themeMode) {
                                    "light" -> Icons.Default.LightMode
                                    "dark" -> Icons.Default.DarkMode
                                    else -> Icons.Default.BrightnessAuto
                                }
                                Icon(icon, "Cycle Theme", tint = pal.TextPrimary)
                            }
                        }
                    )
                    HorizontalDivider(color = pal.DividerColor.copy(0.6f), thickness = 0.75.dp)
                }
            }
        ) { padValues ->
            Box(Modifier.fillMaxSize().padding(padValues)) {
                if (!hasAccess) {
                    PermissionCard(onGrant = onGrant)
                } else {
                    Column(Modifier.fillMaxSize()) {

                        // ── Pill Category Filter Chips ─────────────────────────
                        if (sc != null) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(pal.Background)
                                    .padding(vertical = 8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(TABS) { i, title ->
                                    val isSel = tabIndex == i
                                    val count = tabCounts[title] ?: 0
                                    Surface(
                                        onClick = { tabIndex = i; query = "" },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) pal.Navy else pal.CardBg,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSel) pal.Navy else pal.DividerColor
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else pal.TextPrimary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            if (i > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (isSel) Color.White.copy(alpha = 0.22f)
                                                            else pal.Background
                                                        )
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "$count",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSel) Color.White else pal.TextSecond
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Crisp Search + Sort Row ────────────────────────
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CleanSearchField(
                                    query = query,
                                    onQueryChange = { query = it },
                                    placeholder = "Search files in ${TABS[tabIndex]}…",
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    onClick = { sortIndex = (sortIndex + 1) % SORTS.size },
                                    shape = RoundedCornerShape(12.dp),
                                    color = pal.CardBg,
                                    border = BorderStroke(1.dp, pal.DividerColor),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Sort",
                                            tint = pal.Navy,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = SORTS[sortIndex],
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = pal.TextPrimary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // ── Main Scrollable Area ────────────────────────────────
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = if (selected.isNotEmpty()) 136.dp else 28.dp)
                        ) {
                            // 1. Hero Storage Card
                            item {
                                HeroStorageCard(
                                    meters = meters,
                                    primaryPath = primaryPath,
                                    scanning = scanning,
                                    hasScanned = sc != null,
                                    showChart = showChart,
                                    kindBytes = kindBytes,
                                    totalScannedBytes = sc?.files?.sumOf { it.size } ?: 0L,
                                    onToggleChart = { showChart = !showChart },
                                    onCancelScan = { scanCancelled = true },
                                    onStartScan = {
                                        scope.launch {
                                            scanning = true
                                            scanCancelled = false
                                            selected = emptySet()
                                            feedbackMsg = ""
                                            statusFlow.value = "Preparing file index…"
                                            try {
                                                val res = withContext(Dispatchers.IO) {
                                                    scan(roots, isCancelled = { scanCancelled }) {
                                                        statusFlow.value = it
                                                    }
                                                }
                                                if (scanCancelled) {
                                                    feedbackOk = false
                                                    feedbackMsg = "Scan cancelled by user."
                                                } else {
                                                    scanResult = res
                                                    curDir = if (roots.size == 1) roots[0].path else ""
                                                }
                                            } catch (e: Exception) {
                                                feedbackMsg = "Scan failed: ${e.message}"
                                                feedbackOk = false
                                            }
                                            scanning = false
                                            statusFlow.value = ""
                                            tick++
                                        }
                                    }
                                )
                            }

                            // 2. Scanning Progress Banner
                            if (scanning) {
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = pal.CardBg,
                                        border = BorderStroke(1.dp, pal.Navy.copy(0.35f))
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                                color = pal.Navy,
                                                trackColor = pal.StorageFree
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                statusMsg.ifEmpty { "Working…" },
                                                fontSize = 12.sp,
                                                color = pal.TextSecond
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Feedback Banner
                            if (feedbackMsg.isNotEmpty()) {
                                item {
                                    FeedbackBanner(feedbackMsg, feedbackOk) { feedbackMsg = "" }
                                }
                            }

                            // 4. Scan Summary & Quick Suggestions
                            if (sc != null) {
                                item {
                                    ScanSummaryCard(sc)
                                }

                                if (smartInsights.isNotEmpty()) {
                                    item {
                                        Column(Modifier.padding(top = 6.dp)) {
                                            Text(
                                                "QUICK CLEAN HIGHLIGHTS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp,
                                                color = pal.TextSecond,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                            )
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(smartInsights) { (label, metric, targetTabIdx) ->
                                                    if (targetTabIdx >= 0) {
                                                        Surface(
                                                            onClick = { tabIndex = targetTabIdx; query = "" },
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = pal.CardBg,
                                                            border = BorderStroke(1.dp, pal.DividerColor)
                                                        ) {
                                                            Row(
                                                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    Modifier
                                                                        .size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(pal.Accent)
                                                                )
                                                                Column {
                                                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pal.TextPrimary)
                                                                    Text(metric, fontSize = 11.sp, color = pal.AccentDark, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 5. Folder Breadcrumb
                                if (tabIndex == 0) {
                                    item {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (canGoUp) {
                                                IconButton(onClick = { goUp() }, Modifier.size(32.dp)) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        "Parent folder",
                                                        Modifier.size(18.dp),
                                                        tint = pal.Navy
                                                    )
                                                }
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                if (curDir.isEmpty()) "Storage volumes"
                                                else curDir.removePrefix("/storage/emulated/0").ifEmpty { "/Internal Storage" },
                                                fontSize = 12.sp,
                                                color = pal.TextSecond,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                // 6. Count and Select-all bar
                                item {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${shown.size} items  •  ${fmt(shown.sumOf { it.size })}",
                                            fontSize = 12.sp,
                                            color = pal.TextSecond,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val paths = shown.map { it.path }
                                                    selected = if (paths.isNotEmpty() && paths.all { it in selected })
                                                        selected - paths.toSet() else selected + paths.toSet()
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    if (shown.isNotEmpty() && shown.all { it.path in selected }) "Deselect all" else "Select all",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = pal.PrimaryText
                                                )
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
                                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    Icons.Default.CheckCircleOutline,
                                                    null,
                                                    tint = pal.Success,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                Spacer(Modifier.height(10.dp))
                                                Text(
                                                    "Clean & Clear\nNo files found in this category.",
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
                                            item = item,
                                            isSelected = item.path in selected,
                                            maxSize = maxSize,
                                            showBar = tabIndex == 0,
                                            isDup = isDup,
                                            onClick = {
                                                when {
                                                    item.isDir && tabIndex == 0 -> curDir = item.path
                                                    isDup && tabIndex == 3 -> comparingItem = item
                                                    else -> previewItem = item
                                                }
                                            },
                                            onCheck = {
                                                selected = if (item.path in selected)
                                                    selected - item.path else selected + item.path
                                            }
                                        )
                                        HorizontalDivider(
                                            color = pal.DividerColor.copy(0.45f),
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(start = 72.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Two-Row Floating Selection Dock (Zero text clipping) ───
                AnimatedVisibility(
                    visible = selected.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = pal.CardBg,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, pal.Navy.copy(0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "${selected.size} selected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = pal.TextPrimary
                                    )
                                    Text("•", color = pal.TextSecond)
                                    Text(
                                        fmt(selInfo.second),
                                        fontSize = 13.sp,
                                        color = pal.AccentDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { selected = emptySet() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.heightIn(min = 28.dp)
                                ) {
                                    Text("Clear", color = pal.TextSecond, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CleanOutlinedButton(
                                    text = "Move to Trash",
                                    icon = Icons.Default.DeleteOutline,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch {
                                            scanning = true
                                            statusFlow.value = "Moving selected items to Recycle Bin…"
                                            var moved = 0; var fail = 0
                                            withContext(Dispatchers.IO) {
                                                for (path in selected) {
                                                    if (RecycleBin.moveToTrash(File(path), ctx)) moved++ else fail++
                                                }
                                                val tset = selected
                                                val nf = sc!!.files.filter { !covered(it.path, tset) || File(it.path).exists() }
                                                val ne = sc.emptyDirs.filter { !covered(it.path, tset) || File(it.path).exists() }
                                                val pres = nf.mapTo(HashSet()) { it.path }
                                                val newGrp = sc.duplicateGroups.map { g -> g.filter { it.path in pres } }.filter { it.size > 1 }
                                                scanResult = ScanResult(nf, ne, sc.duplicates.filter { it in pres }.toSet(), newGrp)
                                            }
                                            selected = emptySet()
                                            feedbackOk = fail == 0
                                            feedbackMsg = "Moved $moved items to Recycle Bin" + if (fail > 0) " ($fail failed)" else ""
                                            scanning = false
                                            statusFlow.value = ""
                                            tick++
                                        }
                                    }
                                )

                                CleanPrimaryButton(
                                    text = "Secure Wipe",
                                    icon = Icons.Default.DeleteForever,
                                    containerColor = pal.Danger,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        pendingDeleteTargets = selected.toList()
                                        pendingMode = SecureDelete.Mode.SECURE_WIPE
                                        showConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Rich File / Folder Preview Dialog ─────────────────────────────────
    val previewing = previewItem
    if (previewing != null) {
        FilePreviewDialog(
            item = previewing,
            onDismiss = { previewItem = null },
            onTrash = { path ->
                previewItem = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { RecycleBin.moveToTrash(File(path), ctx) }
                    if (ok) {
                        feedbackOk = true
                        feedbackMsg = "Moved to Recycle Bin: ${path.replace('\\', '/').substringAfterLast('/')}"
                        val tset = setOf(path)
                        val nf = sc?.files?.filter { !covered(it.path, tset) } ?: emptyList()
                        val ne = sc?.emptyDirs?.filter { !covered(it.path, tset) } ?: emptyList()
                        val pres = nf.mapTo(HashSet()) { it.path }
                        val newGrp = sc?.duplicateGroups?.map { g -> g.filter { it.path in pres } }?.filter { it.size > 1 } ?: emptyList()
                        scanResult = ScanResult(nf, ne, sc?.duplicates?.filter { it in pres }?.toSet() ?: emptySet(), newGrp)
                        tick++
                    } else {
                        feedbackOk = false
                        feedbackMsg = "Could not move item to Recycle Bin."
                    }
                }
            },
            onDelete = { path, mode ->
                previewItem = null
                performDelete(listOf(path), mode)
            }
        )
    }

    // ── Duplicate Comparison Bottom Sheet ─────────────────────────────────
    val comparing = comparingItem
    if (comparing != null && sc != null) {
        DuplicateCompareSheet(
            item = comparing,
            scanResult = sc,
            onPreview = { fileToPreview ->
                previewItem = fileToPreview
            },
            onDeleteMany = { toDelete ->
                pendingDeleteTargets = toDelete
                pendingMode = SecureDelete.Mode.SECURE_WIPE
                showConfirm = true
            },
            onDismiss = { comparingItem = null }
        )
    }

    // ── Confirm Delete Dialog ─────────────────────────────────────────────
    if (showConfirm && sc != null) {
        val isSecure = pendingMode == SecureDelete.Mode.SECURE_WIPE
        val targetList = if (pendingDeleteTargets.isNotEmpty()) pendingDeleteTargets else selected.toList()
        val targetBytes = sc.files.filter { covered(it.path, targetList.toSet()) }.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { showConfirm = false; pendingDeleteTargets = emptyList() },
            containerColor = pal.CardBg,
            icon = {
                Icon(
                    if (isSecure) Icons.Default.Shield else Icons.Default.Delete,
                    null,
                    tint = if (isSecure) pal.Danger else pal.Warning
                )
            },
            title = {
                Text(
                    if (isSecure) "Confirm Secure Wipe" else "Confirm Delete",
                    fontWeight = FontWeight.Bold,
                    color = pal.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${targetList.size} item(s) (${fmt(targetBytes)}) will be permanently destroyed.",
                        fontSize = 14.sp,
                        color = pal.TextPrimary
                    )
                    HorizontalDivider(color = pal.DividerColor)
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Security,
                            null,
                            Modifier.size(16.dp).padding(top = 2.dp),
                            tint = pal.Navy
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DoD 5220.22-M 3-pass overwrite (0x00 → 0xFF → random) + length truncation. Cannot be undone.",
                            fontSize = 12.sp,
                            color = pal.TextSecond
                        )
                    }
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { showConfirm = false; pendingDeleteTargets = emptyList() }
                    )
                    CleanPrimaryButton(
                        text = "Erase Forever",
                        icon = Icons.Default.DeleteForever,
                        containerColor = pal.Danger,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showConfirm = false
                            performDelete(targetList, pendingMode)
                        }
                    )
                }
            }
        )
    }

    // ── Check for Updates Dialog ──────────────────────────────────────────
    if (showUpdateDialog) {
        UpdateCheckDialog(onDismiss = { showUpdateDialog = false })
    }

    // ── Scheduled Scan Reminder Dialog ────────────────────────────────────
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
            containerColor = pal.CardBg,
            icon = { Icon(Icons.Default.Notifications, null, tint = pal.Navy) },
            title = { Text("Scan Reminders", fontWeight = FontWeight.Bold, color = pal.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Get periodic notifications to inspect junk, APKs, and duplicate files.",
                        fontSize = 13.sp,
                        color = pal.TextSecond
                    )
                    HorizontalDivider(color = pal.DividerColor)
                    ReminderManager.Interval.values().forEach { interval ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedInterval = interval }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedInterval == interval,
                                onClick = { selectedInterval = interval },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = pal.PrimaryText,
                                    unselectedColor = pal.TextSecond
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(interval.label, fontSize = 14.sp, color = pal.TextPrimary)
                        }
                    }
                    Text(
                        ReminderManager.getNextReminderText(ctx),
                        fontSize = 12.sp,
                        color = pal.PrimaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { showReminderDialog = false }
                    )
                    CleanPrimaryButton(
                        text = "Save Schedule",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showReminderDialog = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                selectedInterval != ReminderManager.Interval.OFF
                            ) {
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                ReminderManager.setInterval(ctx, selectedInterval)
                            }
                        }
                    )
                }
            }
        )
    }
}

// ─── Hero Storage Card ────────────────────────────────────────────────────
@Composable
fun HeroStorageCard(
    meters: List<Triple<String, Long, Long>>,
    primaryPath: String,
    scanning: Boolean,
    hasScanned: Boolean,
    showChart: Boolean,
    kindBytes: LongArray,
    totalScannedBytes: Long,
    onToggleChart: () -> Unit,
    onCancelScan: () -> Unit,
    onStartScan: () -> Unit
) {
    val pal = appPalette()
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = pal.CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, pal.DividerColor.copy(0.7f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((path, total, free) in meters) {
                StorageBar(
                    label = if (path == primaryPath) "Internal Storage" else "External Storage",
                    used = total - free,
                    total = total
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CleanPrimaryButton(
                    text = when {
                        scanning -> "Scanning…"
                        !hasScanned -> "Scan Storage"
                        else -> "Rescan Storage"
                    },
                    icon = Icons.Default.CleaningServices,
                    loading = scanning,
                    enabled = !scanning,
                    modifier = Modifier.weight(1f),
                    onClick = onStartScan
                )
                if (scanning) {
                    CleanOutlinedButton(
                        text = "Cancel",
                        icon = Icons.Default.Close,
                        contentColor = pal.Danger,
                        borderColor = pal.Danger.copy(0.5f),
                        onClick = onCancelScan
                    )
                }
            }

            if (hasScanned) {
                TextButton(
                    onClick = onToggleChart,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Icon(
                        if (showChart) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        Modifier.size(18.dp),
                        tint = pal.AccentDark
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showChart) "Hide storage breakdown" else "View storage breakdown",
                        fontSize = 13.sp,
                        color = pal.AccentDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AnimatedVisibility(showChart) {
                    StoragePieChart(
                        kindBytes = kindBytes,
                        totalBytes = totalScannedBytes,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

// ─── FileRow ──────────────────────────────────────────────────────────────
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
    val bg = if (isSelected) pal.Navy.copy(alpha = 0.09f) else Color.Transparent

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isDir) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.Navy.copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, null, tint = pal.Navy, modifier = Modifier.size(24.dp))
                }
            } else {
                FileThumbnail(item.path, item.kind, Modifier)
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.path.replace('\\', '/').substringAfterLast('/'),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = pal.TextPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDup) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = pal.Warning.copy(0.16f)
                        ) {
                            Text(
                                "DUP",
                                fontSize = 9.sp,
                                color = pal.Warning,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.path.replace('\\', '/').substringBeforeLast('/').removePrefix("/storage/emulated/0").ifEmpty { "/" },
                        fontSize = 11.sp,
                        color = pal.TextSecond,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.modified > 0) {
                        Text(
                            "• ${DateFormat.getDateInstance().format(Date(item.modified))}",
                            fontSize = 11.sp,
                            color = pal.TextSecond
                        )
                    }
                }
                val tagList = buildTagList(item.tags, pal)
                if (tagList.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(tagList) { (label, color) ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color.copy(0.14f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(item.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.TextPrimary
                )
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onCheck() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = pal.Navy,
                        checkmarkColor = Color.White,
                        uncheckedColor = pal.TextSecond
                    )
                )
            }
        }
        if (showBar) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(pal.StorageFree)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((item.size.toFloat() / maxSize).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(pal.Accent)
                )
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
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = pal.CardBg),
            border = BorderStroke(1.dp, pal.DividerColor)
        ) {
            Column(
                Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(pal.Navy.copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, tint = pal.Navy, modifier = Modifier.size(34.dp))
                }
                Text("Storage Access Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
                Text(
                    "Storage Cleaner needs All Files Access to detect hidden junk, duplicate media, and large residual files.\n\nEverything stays 100% offline on your device.",
                    fontSize = 13.sp,
                    color = pal.TextSecond,
                    textAlign = TextAlign.Center
                )
                CleanPrimaryButton(
                    text = "Grant Storage Access",
                    icon = Icons.Default.Security,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGrant
                )
            }
        }
    }
}

@Composable
fun EmptyState() {
    val pal = appPalette()
    Box(
        Modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(78.dp).clip(CircleShape).background(pal.Navy.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CleaningServices, null, tint = pal.Navy, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Ready to Analyze Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap Scan Storage above to uncover exact duplicate files, temporary junk, oversized videos, and empty directories.",
                fontSize = 13.sp,
                color = pal.TextSecond,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ScanSummaryCard(sc: ScanResult) {
    val pal = appPalette()
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = pal.CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, pal.DividerColor.copy(0.7f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceAround) {
            SummaryStat(Icons.Default.Folder, "${sc.files.size}", "Files")
            SummaryStat(Icons.Default.ContentCopy, "${sc.duplicates.size}", "Duplicates")
            SummaryStat(Icons.Default.Storage, fmt(sc.files.sumOf { it.size }), "Indexed")
            SummaryStat(Icons.Default.FolderOpen, "${sc.emptyDirs.size}", "Empty")
        }
    }
}

@Composable
fun SummaryStat(icon: ImageVector, value: String, label: String) {
    val pal = appPalette()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = pal.PrimaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
        Text(label, fontSize = 11.sp, color = pal.TextSecond)
    }
}

fun buildTagList(tags: Int, pal: AppPalette): List<Pair<String, Color>> {
    val list = mutableListOf<Pair<String, Color>>()
    if (tags and T_JUNK != 0) list.add("JUNK" to pal.Danger)
    if (tags and T_SHOT != 0) list.add("SHOT" to pal.PrimaryText)
    if (tags and T_DL   != 0) list.add("DL"   to pal.AccentDark)
    if (tags and T_CHAT != 0) list.add("CHAT" to pal.Warning)
    if (tags and T_OLD  != 0) list.add("OLD"  to pal.TextSecond)
    return list
}

// ─── Jetpack Compose Previews (for Android Studio & Compose Preview Pane) ─
@Preview(showBackground = true, name = "Hero Storage Card (Dark)")
@Composable
fun HeroStorageCardDarkPreview() {
    CompositionLocalProvider(LocalAppPalette provides DarkPalette) {
        MaterialTheme(colorScheme = darkAppColorScheme()) {
            Surface(color = DarkPalette.Background) {
                HeroStorageCard(
                    meters = listOf(Triple("/storage/emulated/0", 128_000_000_000L, 28_000_000_000L)),
                    primaryPath = "/storage/emulated/0",
                    scanning = false,
                    hasScanned = true,
                    showChart = false,
                    kindBytes = LongArray(7),
                    totalScannedBytes = 90_000_000_000L,
                    onToggleChart = {},
                    onCancelScan = {},
                    onStartScan = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Hero Storage Card (Light)")
@Composable
fun HeroStorageCardLightPreview() {
    CompositionLocalProvider(LocalAppPalette provides LightPalette) {
        MaterialTheme(colorScheme = lightAppColorScheme()) {
            Surface(color = LightPalette.Background) {
                HeroStorageCard(
                    meters = listOf(Triple("/storage/emulated/0", 128_000_000_000L, 48_000_000_000L)),
                    primaryPath = "/storage/emulated/0",
                    scanning = false,
                    hasScanned = false,
                    showChart = false,
                    kindBytes = LongArray(7),
                    totalScannedBytes = 0L,
                    onToggleChart = {},
                    onCancelScan = {},
                    onStartScan = {}
                )
            }
        }
    }
}
