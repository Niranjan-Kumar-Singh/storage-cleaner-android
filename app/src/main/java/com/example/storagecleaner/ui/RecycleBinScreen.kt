package com.example.storagecleaner.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun RecycleBinScreen(
    context: Context,
    onBack: () -> Unit
) {
    val pal = appPalette()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<TrashItem>()) }
    var loading by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val totalBytes = remember(items) { items.sumOf { it.size } }

    LaunchedEffect(Unit) {
        loading = true
        items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
        loading = false
    }

    Scaffold(
        containerColor = pal.Background,
        topBar = {
            SubScreenTopBar(
                title = "Recycle Bin",
                subtitle = "${items.size} items  •  ${fmt(totalBytes)}",
                onBack = onBack,
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Default.DeleteSweep, "Empty bin", tint = pal.Danger)
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (msg.isNotEmpty()) {
                FeedbackBanner(msg = msg, ok = msgOk) { msg = "" }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = pal.Navy)
                    }
                }
                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(pal.Navy.copy(0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = pal.Navy,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Recycle Bin is Empty", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Files moved to Trash stay here safely until you restore them or wipe them permanently.",
                                fontSize = 13.sp,
                                color = pal.TextSecond,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 36.dp)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items, key = { it.trashFile.path }) { trash ->
                            TrashItemRow(
                                item = trash,
                                onRestore = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { RecycleBin.restore(trash, context) }
                                        msgOk = ok
                                        msg = if (ok) "Restored: ${trash.trashFile.name.substringAfter("_")}"
                                        else "Could not restore file to original path"
                                        items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { RecycleBin.deletePermanently(context, trash, secure = true) }
                                        msgOk = ok
                                        msg = if (ok) "Permanently wiped using DoD 3-pass engine"
                                        else "Could not erase file"
                                        items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                                    }
                                }
                            )
                            HorizontalDivider(
                                color = pal.DividerColor.copy(0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                    }

                    // Bottom Dock with Info + Empty Trash CTA
                    Surface(
                        color = pal.Surface,
                        border = BorderStroke(1.dp, pal.DividerColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Shield, null, Modifier.size(18.dp), tint = pal.Navy)
                            Text(
                                "Trash items still occupy storage until permanently wiped.",
                                fontSize = 12.sp,
                                color = pal.TextSecond,
                                modifier = Modifier.weight(1f)
                            )
                            CleanPrimaryButton(
                                text = "Empty Bin",
                                containerColor = pal.Danger,
                                onClick = { confirmEmpty = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = pal.CardBg,
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = pal.Danger) },
            title = { Text("Empty Recycle Bin?", fontWeight = FontWeight.Bold, color = pal.TextPrimary) },
            text = {
                Text(
                    "All ${items.size} items (${fmt(totalBytes)}) will be permanently wiped using DoD 5220.22-M 3-pass overwrite. This cannot be undone.",
                    fontSize = 13.sp,
                    color = pal.TextSecond
                )
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { confirmEmpty = false }
                    )
                    CleanPrimaryButton(
                        text = "Empty All",
                        containerColor = pal.Danger,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmEmpty = false
                            scope.launch {
                                val n = withContext(Dispatchers.IO) { RecycleBin.emptyTrash(context) }
                                msgOk = true
                                msg = "Emptied Recycle Bin — $n items permanently erased"
                                items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                            }
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun TrashItemRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    val pal = appPalette()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(pal.CardBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isDir) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = pal.Navy,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.trashFile.name.substringAfter("_"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = pal.TextPrimary
            )
            Text(
                "From: ${item.originalPath.removePrefix("/storage/emulated/0")}",
                fontSize = 11.sp,
                color = pal.TextSecond,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${fmt(item.size)}  •  ${DateFormat.getDateInstance().format(Date(item.deletedAt))}",
                fontSize = 11.sp,
                color = pal.AccentDark,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onRestore, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Restore, "Restore", Modifier.size(20.dp), tint = pal.Navy)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.DeleteForever, "Erase forever", Modifier.size(20.dp), tint = pal.Danger)
        }
    }
}
