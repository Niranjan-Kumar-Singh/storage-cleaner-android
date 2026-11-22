package com.example.storagecleaner.ui

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.RecycleBin
import com.example.storagecleaner.data.TrashItem
import com.example.storagecleaner.data.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    context: Context,
    onBack:  () -> Unit
) {
    val scope   = rememberCoroutineScope()
    var items   by remember { mutableStateOf(listOf<TrashItem>()) }
    var loading by remember { mutableStateOf(true) }
    var msg     by remember { mutableStateOf("") }
    var confirmEmpty by remember { mutableStateOf(false) }

    // Load trash on start
    LaunchedEffect(Unit) {
        loading = true
        items   = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recycle Bin 🗑️", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${items.size} items  •  ${fmt(RecycleBin.totalSize())}",
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onPrimary.copy(0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Default.DeleteSweep, "Empty trash", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Feedback
            if (msg.isNotEmpty()) {
                Surface(
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = { msg = "" }, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🗑️", fontSize = 56.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Recycle Bin is empty", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Files you send to trash will appear here.\nYou can restore or permanently delete them.",
                                fontSize = 13.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(Modifier.weight(1f)) {
                        items(items, key = { it.trashFile.path }) { trash ->
                            TrashItemRow(
                                item      = trash,
                                onRestore = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { RecycleBin.restore(trash) }
                                        msg   = if (ok) "✅ Restored: ${trash.trashFile.name}" else "⚠️ Could not restore"
                                        items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                                    }
                                },
                                onDelete  = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { RecycleBin.deletePermanently(context, trash, secure = true) }
                                        msg   = if (ok) "🔒 Permanently deleted" else "⚠️ Could not delete"
                                        items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }

                    // Info banner
                    Surface(
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                "Files in Recycle Bin still occupy storage. 'Permanently Delete' uses DoD 3-pass wipe.",
                                fontSize = 11.sp,
                                color    = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Empty-trash confirm ───────────────────────────────────────────────
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon  = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Empty recycle bin?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "All ${items.size} items (${fmt(RecycleBin.totalSize())}) will be permanently wiped using DoD 3-pass secure delete. This cannot be undone.",
                    fontSize = 14.sp
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEmpty = false
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { RecycleBin.emptyTrash(context) }
                            msg   = "🔒 Emptied trash — $n items permanently deleted"
                            items = emptyList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Empty Trash") }
            }
        )
    }
}

@Composable
private fun TrashItemRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (item.isDir) "📁" else "📄", fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.trashFile.name.substringAfter("_"),   // strip timestamp prefix
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Originally: ${item.originalPath.removePrefix("/storage/emulated/0")}",
                fontSize = 10.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${fmt(item.size)}  •  Deleted ${DateFormat.getDateInstance().format(Date(item.deletedAt))}",
                fontSize = 10.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        // Restore
        IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Restore, "Restore", Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        // Permanent delete
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.DeleteForever, "Delete forever", Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}
