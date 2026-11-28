package com.example.storagecleaner.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.*
import com.example.storagecleaner.security.SecureDelete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun RecycleBinScreen(
    context: Context,
    onBack: () -> Unit,
    onRestored: (List<TrashItem>) -> Unit = {}
) {
    val pal = appPalette()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<TrashItem>()) }
    var loading by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var previewTrash by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmSingleErase by remember { mutableStateOf<TrashItem?>(null) }
    var confirmBatchErase by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var currentRetention by remember { mutableStateOf(ExclusionManager.getTrashRetention(context)) }

    val totalBytes = remember(items) { items.sumOf { it.size } }
    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items
        else items.filter {
            it.originalPath.substringAfterLast('/').lowercase().contains(q) ||
                it.originalPath.lowercase().contains(q)
        }
    }

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
                    IconButton(onClick = { showRetentionDialog = true }) {
                        Icon(Icons.Default.Schedule, "Auto-purge schedule", tint = pal.TextPrimary)
                    }
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Default.DeleteSweep, "Empty bin", tint = pal.Danger)
                        }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                if (msg.isNotEmpty()) {
                    FeedbackBanner(msg = msg, ok = msgOk) { msg = "" }
                }

                if (items.isNotEmpty() && !loading) {
                    CleanSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search trash by name or folder…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentRetention == ExclusionManager.TrashRetention.NEVER) {
                                "Retention: Keep forever"
                            } else {
                                "Retention: ${currentRetention.days} days"
                            },
                            fontSize = 11.sp,
                            color = pal.TextSecond
                        )
                        TextButton(
                            onClick = {
                                val paths = filtered.map { it.trashFile.path }.toSet()
                                selected = if (paths.isNotEmpty() && paths.all { it in selected }) {
                                    selected - paths
                                } else {
                                    selected + paths
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (filtered.isNotEmpty() && filtered.all { it.trashFile.path in selected }) "Deselect all" else "Select all",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = pal.PrimaryText
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
                    filtered.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, tint = pal.TextSecond, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("No trashed files match \"$query\"", fontSize = 14.sp, color = pal.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                CleanOutlinedButton(text = "Clear Search", icon = Icons.Default.Close, onClick = { query = "" })
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = 4.dp, bottom = if (selected.isNotEmpty()) 128.dp else 12.dp)
                        ) {
                            items(filtered, key = { it.trashFile.path }) { trash ->
                                val isSel = trash.trashFile.path in selected
                                TrashItemRow(
                                    item = trash,
                                    isSelected = isSel,
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            selected = if (isSel) selected - trash.trashFile.path else selected + trash.trashFile.path
                                        } else {
                                            previewTrash = trash
                                        }
                                    },
                                    onToggleSelect = {
                                        selected = if (isSel) selected - trash.trashFile.path else selected + trash.trashFile.path
                                    },
                                    onRestore = {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) { RecycleBin.restore(trash, context) }
                                            msgOk = ok
                                            val displayName = trash.originalPath.substringAfterLast('/')
                                            msg = if (ok) {
                                                onRestored(listOf(trash))
                                                "Restored: $displayName"
                                            } else {
                                                "Could not restore file to original path"
                                            }
                                            selected = selected - trash.trashFile.path
                                            items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                                        }
                                    },
                                    onDelete = { confirmSingleErase = trash }
                                )
                                HorizontalDivider(
                                    color = pal.DividerColor.copy(0.5f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 72.dp)
                                )
                            }
                        }

                        if (selected.isEmpty()) {
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
                                        "Tap any item to preview, or long-press for multi-select.",
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

            // Batch Selection Dock for Recycle Bin
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val chosenItems = items.filter { it.trashFile.path in selected }
                val chosenBytes = chosenItems.sumOf { it.size }
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
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${chosenItems.size} selected  •  ${fmt(chosenBytes)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = pal.TextPrimary,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                            TextButton(
                                onClick = { selected = emptySet() },
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp, color = pal.TextSecond, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CleanOutlinedButton(
                                text = "Restore",
                                icon = Icons.Default.Restore,
                                contentColor = pal.PrimaryText,
                                borderColor = pal.PrimaryText.copy(0.45f),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        val restoredOk = mutableListOf<TrashItem>()
                                        withContext(Dispatchers.IO) {
                                            chosenItems.forEach { t ->
                                                if (RecycleBin.restore(t, context)) restoredOk.add(t)
                                            }
                                        }
                                        if (restoredOk.isNotEmpty()) onRestored(restoredOk)
                                        msgOk = restoredOk.size == chosenItems.size
                                        msg = "Restored ${restoredOk.size} of ${chosenItems.size} items"
                                        selected = emptySet()
                                        items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                                    }
                                }
                            )
                            CleanPrimaryButton(
                                text = "Erase Forever",
                                icon = Icons.Default.DeleteForever,
                                containerColor = pal.Danger,
                                modifier = Modifier.weight(1f),
                                onClick = { confirmBatchErase = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Preview trashed item using shared FilePreviewDialog
    val previewing = previewTrash
    if (previewing != null) {
        val ext = previewing.originalPath.substringAfterLast('.', "").lowercase()
        val syntheticItem = FileItem(
            path = previewing.trashFile.path,
            size = previewing.size,
            modified = previewing.deletedAt,
            isDir = previewing.isDir,
            kind = EXT[ext] ?: KIND_OTHER
        )
        FilePreviewDialog(
            item = syntheticItem,
            isInTrash = true,
            onDismiss = { previewTrash = null },
            onTrash = {},
            onRestore = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { RecycleBin.restore(previewing, context) }
                    msgOk = ok
                    msg = if (ok) {
                        onRestored(listOf(previewing))
                        "Restored: ${previewing.originalPath.substringAfterLast('/')}"
                    } else "Could not restore file"
                    items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                }
            },
            onDelete = { _, _ ->
                previewTrash = null
                confirmSingleErase = previewing
            }
        )
    }

    val pendingItem = confirmSingleErase
    if (pendingItem != null) {
        val displayName = pendingItem.originalPath.substringAfterLast('/')
        AlertDialog(
            onDismissRequest = { confirmSingleErase = null },
            containerColor = pal.CardBg,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = pal.Danger) },
            title = { Text("Erase Item Permanently?", fontWeight = FontWeight.Bold, color = pal.TextPrimary) },
            text = {
                Text(
                    "DoD 5220.22-M 3-pass overwrite will permanently destroy \"$displayName\" (${fmt(pendingItem.size)}). This cannot be undone.",
                    fontSize = 13.sp,
                    color = pal.TextSecond
                )
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton("Cancel", { confirmSingleErase = null }, Modifier.weight(1f))
                    CleanPrimaryButton(
                        text = "Erase Now",
                        containerColor = pal.Danger,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmSingleErase = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    RecycleBin.deletePermanently(context, pendingItem, secure = true)
                                }
                                msgOk = ok
                                msg = if (ok) "Permanently wiped using DoD 3-pass engine" else "Could not erase file"
                                selected = selected - pendingItem.trashFile.path
                                items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                            }
                        }
                    )
                }
            }
        )
    }

    if (confirmBatchErase) {
        val chosenItems = items.filter { it.trashFile.path in selected }
        val chosenBytes = chosenItems.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { confirmBatchErase = false },
            containerColor = pal.CardBg,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = pal.Danger) },
            title = { Text("Erase ${chosenItems.size} Items?", fontWeight = FontWeight.Bold, color = pal.TextPrimary) },
            text = {
                Text(
                    "DoD 5220.22-M 3-pass overwrite will permanently destroy ${chosenItems.size} selected items (${fmt(chosenBytes)}). This cannot be undone.",
                    fontSize = 13.sp,
                    color = pal.TextSecond
                )
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton("Cancel", { confirmBatchErase = false }, Modifier.weight(1f))
                    CleanPrimaryButton(
                        text = "Erase Selected",
                        containerColor = pal.Danger,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmBatchErase = false
                            scope.launch {
                                var count = 0
                                withContext(Dispatchers.IO) {
                                    chosenItems.forEach { t ->
                                        if (RecycleBin.deletePermanently(context, t, secure = true)) count++
                                    }
                                }
                                msgOk = count == chosenItems.size
                                msg = "Permanently wiped $count of ${chosenItems.size} items"
                                selected = emptySet()
                                items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                            }
                        }
                    )
                }
            }
        )
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
                    CleanOutlinedButton("Cancel", { confirmEmpty = false }, Modifier.weight(1f))
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
                                selected = emptySet()
                                items = withContext(Dispatchers.IO) { RecycleBin.loadItems() }
                            }
                        }
                    )
                }
            }
        )
    }

    if (showRetentionDialog) {
        var picked by remember { mutableStateOf(currentRetention) }
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            containerColor = pal.CardBg,
            icon = { Icon(Icons.Default.Schedule, null, tint = pal.Navy) },
            title = { Text("Trash Auto-Purge Policy", fontWeight = FontWeight.Bold, color = pal.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Automatically permanently wipe old items inside the Recycle Bin on app launch.",
                        fontSize = 12.sp,
                        color = pal.TextSecond
                    )
                    Spacer(Modifier.height(4.dp))
                    ExclusionManager.TrashRetention.values().forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { picked = opt }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = picked == opt,
                                onClick = { picked = opt },
                                colors = RadioButtonDefaults.colors(selectedColor = pal.PrimaryText, unselectedColor = pal.TextSecond)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(opt.label, fontSize = 13.sp, color = pal.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton("Cancel", { showRetentionDialog = false }, Modifier.weight(1f))
                    CleanPrimaryButton(
                        text = "Save Policy",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentRetention = picked
                            ExclusionManager.setTrashRetention(context, picked)
                            showRetentionDialog = false
                            msgOk = true
                            msg = "Retention set to: ${picked.label}"
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun TrashItemRow(
    item: TrashItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val pal = appPalette()
    val displayName = item.originalPath.replace('\\', '/').substringAfterLast('/')
    val ext = displayName.substringAfterLast('.', "").lowercase()
    val kind = EXT[ext] ?: KIND_OTHER
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) pal.Navy.copy(alpha = 0.09f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onToggleSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isDir) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.Navy.copy(0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = pal.Navy, modifier = Modifier.size(22.dp))
            }
        } else {
            FileThumbnail(item.trashFile.path, kind)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                displayName,
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
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        Spacer(Modifier.width(4.dp))
        if (isSelected) {
            Checkbox(
                checked = true,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = pal.Navy, checkmarkColor = Color.White)
            )
        } else {
            IconButton(onClick = onRestore, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Restore, "Restore", Modifier.size(20.dp), tint = pal.PrimaryText)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.DeleteForever, "Erase forever", Modifier.size(20.dp), tint = pal.Danger)
            }
        }
    }
}
