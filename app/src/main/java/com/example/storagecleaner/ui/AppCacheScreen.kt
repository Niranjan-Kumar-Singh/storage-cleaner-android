package com.example.storagecleaner.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.storagecleaner.data.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppInfo(
    val packageName: String,
    val label: String,
    val apkSize: Long,
    val isSystem: Boolean
)

@Composable
fun AppCacheScreen(context: Context, onBack: () -> Unit) {
    val pal = appPalette()
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    val displayed = remember(apps, showSystem, query) {
        apps
            .filter { showSystem || !it.isSystem }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedWith(compareByDescending<AppInfo> { it.apkSize }.thenBy { it.label })
    }

    Scaffold(
        containerColor = pal.Background,
        topBar = {
            SubScreenTopBar(
                title = "App Manager",
                subtitle = "${displayed.size} installed apps • Tap to clear cache",
                onBack = onBack
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

            Surface(
                color = pal.Navy.copy(alpha = 0.09f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = pal.Navy)
                    Text(
                        "Android restricts direct third-party cache wiping. Tap any app to open its System Storage settings directly.",
                        fontSize = 11.sp,
                        color = pal.TextPrimary
                    )
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
                            AppRow(context, app) {
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
private fun AppRow(context: Context, app: AppInfo, onClick: () -> Unit) {
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
            if (app.apkSize > 0) {
                Text(
                    fmt(app.apkSize),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.TextPrimary
                )
            }
            if (app.isSystem) {
                Text("System", fontSize = 10.sp, color = pal.AccentDark, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = pal.TextSecond)
    }
}

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    return try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA).map { ai ->
            val label = pm.getApplicationLabel(ai).toString()
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val apkSize = try {
                ai.publicSourceDir?.let { File(it).length() } ?: 0L
            } catch (_: Exception) { 0L }
            AppInfo(ai.packageName, label, apkSize, isSystem)
        }
    } catch (_: Exception) { emptyList() }
}
