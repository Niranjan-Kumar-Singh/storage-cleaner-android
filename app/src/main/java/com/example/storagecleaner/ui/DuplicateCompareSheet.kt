package com.example.storagecleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.FileItem
import com.example.storagecleaner.data.ScanResult
import com.example.storagecleaner.data.fmt
import java.text.DateFormat
import java.util.Date

/**
 * Modal bottom sheet for side-by-side duplicate comparison.
 *
 * Shows the duplicate file and its "original" counterpart (oldest in group),
 * plus actions to delete either one.
 *
 * @param item       The file the user tapped (a duplicate).
 * @param scanResult Full scan — used to find the duplicate group.
 * @param onDelete   Called with the path to delete and the other path to keep.
 * @param onDismiss  Called when sheet should close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCompareSheet(
    item:       FileItem,
    scanResult: ScanResult,
    onDelete:   (toDelete: String, toKeep: String) -> Unit,
    onDismiss:  () -> Unit
) {
    // Find the group this duplicate belongs to
    val group = remember(item) {
        scanResult.duplicateGroups.firstOrNull { group ->
            group.any { it.path == item.path }
        } ?: listOf(item)
    }

    // The "original" is the oldest; the duplicate is the tapped item
    val original  = group.minByOrNull { it.modified } ?: item
    val duplicate = if (original.path == item.path) group.getOrNull(1) ?: item else item

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Handle bar hint
            Text(
                "Duplicate Comparison",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 12.dp)
            )

            // ── Side-by-side cards ─────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DupFileCard(
                    label    = "Original (Oldest)",
                    item     = original,
                    modifier = Modifier.weight(1f),
                    tint     = MaterialTheme.colorScheme.primary
                )
                DupFileCard(
                    label    = "Duplicate Copy",
                    item     = duplicate,
                    modifier = Modifier.weight(1f),
                    tint     = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Actions ───────────────────────────────────────────────
            Text(
                "Which one do you want to keep?",
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Keep original, delete duplicate
                OutlinedButton(
                    onClick  = {
                        onDelete(duplicate.path, original.path)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete duplicate", fontSize = 12.sp)
                }

                // Keep duplicate, delete original
                OutlinedButton(
                    onClick  = {
                        onDelete(original.path, duplicate.path)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete original", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel — keep both", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun DupFileCard(label: String, item: FileItem, modifier: Modifier, tint: Color) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.SemiBold)

            // Thumbnail
            FileThumbnail(item.path, item.kind, Modifier)

            Text(
                item.path.substringAfterLast('/'),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                fmt(item.size),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = tint
            )
            if (item.modified > 0) {
                Text(
                    DateFormat.getDateInstance().format(Date(item.modified)),
                    fontSize = 10.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                item.path.removePrefix("/storage/emulated/0").substringBeforeLast('/').ifEmpty { "/" },
                fontSize = 9.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
