package com.example.storagecleaner.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.storagecleaner.data.*
import com.example.storagecleaner.security.SecureDelete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Resolves MIME type for a file path.
 */
fun getMimeType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return "*/*"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "apk" -> "application/vnd.android.package-archive"
        "log", "txt", "md" -> "text/plain"
        "pdf" -> "application/pdf"
        "mp3", "m4a", "wav", "aac", "ogg", "flac" -> "audio/*"
        "mp4", "mkv", "mov", "avi", "webm", "3gp" -> "video/*"
        "jpg", "jpeg", "png", "webp", "gif", "heic" -> "image/*"
        "zip", "tar", "gz", "7z", "rar" -> "application/zip"
        else -> "*/*"
    }
}

/**
 * Launches an external app to view the file via FileProvider.
 */
fun openFileExternally(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = getMimeType(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open with…")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

val KIND_NAMES = listOf("Other", "Image", "Video", "Audio", "Document", "Archive", "APK")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onTrash: (path: String) -> Unit,
    onDelete: (path: String, mode: SecureDelete.Mode) -> Unit
) {
    val context = LocalContext.current
    val pal = appPalette()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mediaExtra by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var confirmWipe by remember { mutableStateOf(false) }

    // Load preview bitmap on background thread
    LaunchedEffect(item.path) {
        loading = true
        withContext(Dispatchers.IO) {
            try {
                when (item.kind) {
                    KIND_IMAGE -> {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(item.path, bounds)
                        val w = bounds.outWidth
                        val h = bounds.outHeight
                        if (w > 0 && h > 0) {
                            mediaExtra = "${w} × ${h} px  •  ${bounds.outMimeType?.substringAfter('/')?.uppercase() ?: "IMG"}"
                        }
                        var sample = 1
                        val maxDim = 1200
                        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                            sample *= 2
                        }
                        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                        bitmap = BitmapFactory.decodeFile(item.path, opts)
                    }
                    KIND_VIDEO -> {
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(item.path)
                            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            val vw = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            val vh = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            val secs = dur / 1000
                            val durStr = String.format("%02d:%02d", secs / 60, secs % 60)
                            mediaExtra = if (vw != null && vh != null) "$durStr  •  ${vw}×${vh}" else durStr
                            bitmap = mmr.frameAtTime
                        } catch (_: Exception) {}
                        finally { mmr.release() }
                    }
                    KIND_AUDIO -> {
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(item.path)
                            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val secs = dur / 1000
                            val durStr = String.format("%02d:%02d", secs / 60, secs % 60)
                            mediaExtra = listOfNotNull(title, artist, durStr).joinToString("  •  ")
                        } catch (_: Exception) {}
                        finally { mmr.release() }
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = pal.CardBg,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, pal.DividerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with File Name & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.path.substringAfterLast('/'),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = pal.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = KIND_NAMES.getOrElse(item.kind) { "File" } + "  •  " + fmt(item.size),
                            fontSize = 12.sp,
                            color = pal.AccentDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = pal.TextSecond)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Media Preview Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(pal.Background)
                        .border(1.dp, pal.DividerColor.copy(0.4f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> {
                            CircularProgressIndicator(color = pal.Navy, strokeWidth = 2.5.dp)
                        }
                        bitmap != null -> {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                val (icon, bgCol) = when (item.kind) {
                                    KIND_APK -> "📦" to Color(0xFF689F38)
                                    KIND_AUDIO -> "🎵" to Color(0xFF00897B)
                                    KIND_VIDEO -> "🎬" to Color(0xFFE53935)
                                    KIND_IMAGE -> "🖼️" to Color(0xFF1E88E5)
                                    KIND_DOC -> "📄" to Color(0xFFFB8C00)
                                    KIND_ARCHIVE -> "🗜️" to Color(0xFF8E24AA)
                                    else -> "📁" to pal.NavyLight
                                }
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(bgCol.copy(0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(icon, fontSize = 34.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = item.path.substringAfterLast('.').uppercase().ifEmpty { "FILE" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pal.TextPrimary
                                )
                            }
                        }
                    }
                }

                if (mediaExtra.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = mediaExtra,
                        fontSize = 11.sp,
                        color = pal.TextSecond,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Metadata Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = pal.Background
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Full Path", fontSize = 11.sp, color = pal.TextSecond)
                        }
                        Text(
                            item.path,
                            fontSize = 11.sp,
                            color = pal.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider(color = pal.DividerColor.copy(0.5f), thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date Modified", fontSize = 11.sp, color = pal.TextSecond)
                            Text(
                                if (item.modified > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.modified)) else "Unknown",
                                fontSize = 11.sp,
                                color = pal.TextPrimary
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Exact Size", fontSize = 11.sp, color = pal.TextSecond)
                            Text("${item.size} bytes (${fmt(item.size)})", fontSize = 11.sp, color = pal.TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Action Buttons
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Open with system application button
                    Button(
                        onClick = { openFileExternally(context, item.path) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = pal.Navy)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in App / Player", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Move to Trash (Soft Delete)
                        OutlinedButton(
                            onClick = {
                                onTrash(item.path)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = pal.TextPrimary)
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Move to Trash", fontSize = 12.sp)
                        }

                        // Secure Wipe (DoD 3-pass)
                        Button(
                            onClick = { confirmWipe = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = pal.Danger)
                        ) {
                            Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Secure Wipe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Permanently Erase File?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "DoD 5220.22-M 3-pass secure overwrite will be performed.\n" +
                    "This file CANNOT be recovered by any file recovery tool.\n\n" +
                    "File: ${item.path.substringAfterLast('/')} (${fmt(item.size)})"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmWipe = false
                        onDelete(item.path, SecureDelete.Mode.SECURE_WIPE)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = pal.Danger)
                ) {
                    Text("Permanently Erase", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            }
        )
    }
}
