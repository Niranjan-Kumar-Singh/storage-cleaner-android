package com.example.storagecleaner.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label:       String,
    val dataSize:    Long,   // -1 if unknown
    val isSystem:    Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacheScreen(context: Context, onBack: () -> Unit) {
    var apps    by remember { mutableStateOf(listOf<AppInfo>()) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var query   by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        apps    = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    val displayed = remember(apps, showSystem, query) {
        apps
            .filter { showSystem || !it.isSystem }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .sortedByDescending { it.dataSize }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Manager", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Tap an app to manage its cache", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // ── Search + system toggle ────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value           = query,
                    onValueChange   = { query = it },
                    placeholder     = { Text("Search apps…", fontSize = 13.sp) },
                    singleLine      = true,
                    leadingIcon     = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    trailingIcon    = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    },
                    shape           = RoundedCornerShape(12.dp),
                    modifier        = Modifier.weight(1f),
                    textStyle       = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                FilterChip(
                    selected = showSystem,
                    onClick  = { showSystem = !showSystem },
                    label    = { Text("System", fontSize = 12.sp) }
                )
            }

            // ── Info banner ───────────────────────────────────────────
            Surface(
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${displayed.size} apps shown. Tap an app → open its settings → Clear Cache / Clear Storage.",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                displayed.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(Modifier.weight(1f)) {
                        items(displayed, key = { it.packageName }) { app ->
                            AppRow(app) {
                                // Open app info page in system settings
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.packageName}"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) { }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier  = Modifier.padding(start = 68.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon placeholder (letter avatar)
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color    = MaterialTheme.colorScheme.primaryContainer,
                shape    = RoundedCornerShape(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                app.packageName,
                fontSize = 10.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.isSystem) {
                Text("System app", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.ChevronRight, null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    return try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA).map { ai ->
            val label    = pm.getApplicationLabel(ai).toString()
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(ai.packageName, label, -1L, isSystem)
        }.sortedWith(compareBy({ it.isSystem }, { it.label }))
    } catch (_: Exception) { emptyList() }
}
