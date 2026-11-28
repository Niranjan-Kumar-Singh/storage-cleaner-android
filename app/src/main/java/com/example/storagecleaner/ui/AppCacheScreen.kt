package com.example.storagecleaner.ui

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.storagecleaner.data.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppInfo(
    val packageName: String,
    val label: String,
    val apkSize: Long,
    val totalStorageBytes: Long,
    val cacheBytes: Long,
    val isSystem: Boolean
)

@Suppress("DEPRECATION")
fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

@Composable
fun AppCacheScreen(context: Context, onBack: () -> Unit) {
    val pal = appPalette()
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(0) } // 0 = Total/APK Size, 1 = Cache Size, 2 = Name A–Z
    var refreshTick by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var usageGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // Re-check Usage Access permission when user returns from System Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = hasUsageStatsPermission(context)
                if (nowGranted != usageGranted) {
                    usageGranted = nowGranted
                    refreshTick++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshTick) {
        loading = true
        usageGranted = hasUsageStatsPermission(context)
        apps = withContext(Dispatchers.IO) { loadApps(context, usageGranted) }
        loading = false
    }

    val displayed = remember(apps, showSystem, sortMode, query) {
        val filtered = apps
            .filter { showSystem || !it.isSystem }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        when (sortMode) {
            2 -> filtered.sortedBy { it.label.lowercase() }
            1 -> filtered.sortedWith(compareByDescending<AppInfo> { it.cacheBytes }.thenByDescending { it.totalStorageBytes })
            else -> filtered.sortedWith(compareByDescending<AppInfo> { it.totalStorageBytes }.thenBy { it.label })
        }
    }

    val totalCacheBytes = remember(displayed) { displayed.sumOf { it.cacheBytes } }
    val sortLabel = when (sortMode) {
        1 -> "Sorted by Cache"
        2 -> "Sorted A–Z"
        else -> if (usageGranted) "Sorted by Total Storage" else "Sorted by APK Size"
    }

    Scaffold(
        containerColor = pal.Background,
        topBar = {
            SubScreenTopBar(
                title = "App Manager",
                subtitle = buildString {
                    append("${displayed.size} apps  •  $sortLabel")
                    if (usageGranted && totalCacheBytes > 0) append("  •  Cache ${fmt(totalCacheBytes)}")
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { sortMode = (sortMode + 1) % 3 }) {
                        Icon(
                            if (sortMode == 2) Icons.Default.SortByAlpha else Icons.Default.Storage,
                            contentDescription = "Cycle sort",
                            tint = if (sortMode != 0) pal.PrimaryText else pal.TextSecond
                        )
                    }
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh apps", tint = pal.TextPrimary)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // Search + System Filter Row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CleanSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search apps or packages…",
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    onClick = { showSystem = !showSystem },
                    shape = RoundedCornerShape(12.dp),
                    color = if (showSystem) pal.Navy else pal.Surface,
                    border = BorderStroke(1.dp, if (showSystem) pal.Navy else pal.DividerColor),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showSystem) "System: On" else "System: Off",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showSystem) Color.White else pal.TextSecond,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Optional Usage Access prompt for real cache + data metrics
            if (!usageGranted) {
                Surface(
                    color = pal.Navy.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, pal.Navy.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Insights, null, Modifier.size(18.dp), tint = pal.PrimaryText)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Unlock Real Cache & Data Sizes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = pal.TextPrimary
                            )
                            Text(
                                "Currently showing base APK sizes. Grant Usage Access to inspect exact app data & cache.",
                                fontSize = 11.sp,
                                color = pal.TextSecond
                            )
                        }
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) { }
                            }
                        ) {
                            Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.PrimaryText)
                        }
                    }
                }
            } else {
                Surface(
                    color = pal.Accent.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, Modifier.size(15.dp), tint = pal.AccentDark)
                        Text(
                            "Tap any app to open its System Storage page to clear cache directly.",
                            fontSize = 11.sp,
                            color = pal.TextPrimary
                        )
                    }
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = pal.Navy)
                    }
                }
                displayed.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching applications found", fontSize = 14.sp, color = pal.TextSecond)
                    }
                }
                else -> {
                    LazyColumn(Modifier.weight(1f)) {
                        items(displayed, key = { it.packageName }) { app ->
                            AppRow(context, app, usageGranted) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) { }
                            }
                            HorizontalDivider(
                                color = pal.DividerColor.copy(0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    context: Context,
    app: AppInfo,
    usageGranted: Boolean,
    onClick: () -> Unit
) {
    val pal = appPalette()
    var iconBmp by remember(app.packageName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(app.packageName) {
        iconBmp = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96)
            } catch (_: Exception) { null }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(pal.CardBg),
            contentAlignment = Alignment.Center
        ) {
            val bmp = iconBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.Navy
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = pal.TextPrimary
            )
            Text(
                app.packageName,
                fontSize = 11.sp,
                color = pal.TextSecond,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (app.totalStorageBytes > 0) {
                Text(
                    fmt(app.totalStorageBytes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.TextPrimary,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
            if (usageGranted && app.cacheBytes > 0) {
                Text(
                    "Cache ${fmt(app.cacheBytes)}",
                    fontSize = 10.sp,
                    color = pal.AccentDark,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            } else if (app.isSystem) {
                Text("System", fontSize = 10.sp, color = pal.PrimaryText, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = pal.TextSecond)
    }
}

private fun loadApps(ctx: Context, usageGranted: Boolean): List<AppInfo> {
    val pm = ctx.packageManager
    val ssm = if (usageGranted) ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager else null
    val user = Process.myUserHandle()

    return try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA).map { ai ->
            val label = pm.getApplicationLabel(ai).toString()
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val apkSize = try {
                ai.publicSourceDir?.let { File(it).length() } ?: 0L
            } catch (_: Exception) { 0L }

            var totalBytes = apkSize
            var cacheBytes = 0L
            if (ssm != null) {
                try {
                    val stats = ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, user)
                    totalBytes = stats.appBytes + stats.dataBytes
                    cacheBytes = stats.cacheBytes
                } catch (_: Exception) { }
            }

            AppInfo(
                packageName = ai.packageName,
                label = label,
                apkSize = apkSize,
                totalStorageBytes = totalBytes,
                cacheBytes = cacheBytes,
                isSystem = isSystem
            )
        }
    } catch (_: Exception) { emptyList() }
}
