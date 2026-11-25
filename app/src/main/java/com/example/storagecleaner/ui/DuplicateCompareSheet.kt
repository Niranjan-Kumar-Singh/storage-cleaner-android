package com.example.storagecleaner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.storagecleaner.data.FileItem
import com.example.storagecleaner.data.ScanResult
import com.example.storagecleaner.data.fmt
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCompareSheet(
    item: FileItem,
    scanResult: ScanResult,
    onPreview: (FileItem) -> Unit,
    onDeleteMany: (pathsToDelete: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val pal = appPalette()
    val group = remember(item, scanResult) {
        scanResult.duplicateGroups.firstOrNull { g ->
            g.any { it.path == item.path }
        }?.sortedBy { it.modified } ?: emptyList()
    }

    LaunchedEffect(group.size) {
        if (group.size <= 1) onDismiss()
    }
    if (group.size <= 1) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copies = group.drop(1)
    val recoverable = copies.sumOf { it.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.Surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Identical Files (${group.size} copies)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal.TextPrimary
                    )
                    Text(
                        "Tap any copy to preview. Oldest file is preserved by default.",
                        fontSize = 12.sp,
                        color = pal.TextSecond
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(group, key = { _, f -> f.path }) { idx, copy ->
                    val isOriginal = idx == 0
                    val accent = if (isOriginal) pal.Success else pal.Danger
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pal.CardBg,
                        border = BorderStroke(1.dp, if (isOriginal) accent.copy(0.4f) else pal.DividerColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPreview(copy) }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileThumbnail(copy.path, copy.kind)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = accent.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (isOriginal) "ORIGINAL" else "DUPLICATE #$idx",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        fmt(copy.size),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pal.TextPrimary
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    copy.path.replace('\\', '/').substringAfterLast('/'),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = pal.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    copy.path.removePrefix("/storage/emulated/0").substringBeforeLast('/').ifEmpty { "/" } +
                                        if (copy.modified > 0) " • ${DateFormat.getDateInstance().format(Date(copy.modified))}" else "",
                                    fontSize = 11.sp,
                                    color = pal.TextSecond,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!isOriginal) {
                                IconButton(onClick = {
                                    onDeleteMany(listOf(copy.path))
                                    onDismiss()
                                }) {
                                    Icon(Icons.Default.DeleteOutline, "Delete this copy", tint = pal.Danger)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (copies.isNotEmpty()) {
                CleanPrimaryButton(
                    text = "Keep Oldest • Delete ${copies.size} ${if (copies.size == 1) "Copy" else "Copies"} (${fmt(recoverable)})",
                    icon = Icons.Default.DeleteSweep,
                    containerColor = pal.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDeleteMany(copies.map { it.path })
                        onDismiss()
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            CleanOutlinedButton(
                text = "Cancel — Keep All",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss
            )
        }
    }
}
