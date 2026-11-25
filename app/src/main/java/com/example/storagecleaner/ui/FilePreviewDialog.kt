package com.example.storagecleaner.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

fun getMimeType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return "*/*"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "apk", "apks", "xapk" -> "application/vnd.android.package-archive"
        "log", "txt", "md", "json", "xml", "csv", "ini", "conf" -> "text/plain"
        "pdf" -> "application/pdf"
        "mp3", "m4a", "wav", "aac", "ogg", "flac", "opus" -> "audio/*"
        "mp4", "mkv", "mov", "avi", "webm", "3gp" -> "video/*"
        "jpg", "jpeg", "png", "webp", "gif", "heic", "avif" -> "image/*"
        "zip", "tar", "gz", "7z", "rar" -> "application/zip"
        else -> "*/*"
    }
}

fun openFileExternally(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "File no longer exists", Toast.LENGTH_SHORT).show()
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
        val chooser = Intent.createChooser(intent, "Open with")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No compatible app found: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun decodeOrientedBitmap(path: String, maxDim: Int = 1200): Triple<Bitmap?, Int, Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return Triple(null, 0, 0)

    var sample = 1
    while (w / sample > maxDim || h / sample > maxDim) sample *= 2
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return Triple(null, w, h)

    val rotated = try {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val angle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (angle != 0f) {
            val matrix = Matrix().apply { postRotate(angle) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } else raw
    } catch (_: Exception) { raw }

    return Triple(rotated, w, h)
}

private val TEXT_PREVIEW_EXTS = setOf("txt", "log", "json", "xml", "csv", "md", "ini", "conf", "prop")
val KIND_NAMES = listOf("Other", "Image", "Video", "Audio", "Document", "Archive", "APK")

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
    var textSnippet by remember { mutableStateOf<String?>(null) }
    var mediaExtra by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var confirmWipe by remember { mutableStateOf(false) }

    LaunchedEffect(item.path) {
        loading = true
        bitmap = null
        textSnippet = null
        mediaExtra = ""
        withContext(Dispatchers.IO) {
            try {
                if (item.isDir) {
                    val childrenCount = File(item.path).list()?.size ?: 0
                    mediaExtra = "$childrenCount items inside folder"
                } else when (item.kind) {
                    KIND_IMAGE -> {
                        val (bmp, w, h) = decodeOrientedBitmap(item.path)
                        bitmap = bmp
                        if (w > 0 && h > 0) mediaExtra = "$w × $h px"
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
                    else -> {
                        val ext = item.path.substringAfterLast('.', "").lowercase()
                        if (ext in TEXT_PREVIEW_EXTS && item.size in 1..2_000_000) {
                            val file = File(item.path)
                            if (file.canRead()) {
                                file.inputStream().bufferedReader().use { reader ->
                                    val buf = CharArray(3000)
                                    val read = reader.read(buf)
                                    if (read > 0) {
                                        val str = String(buf, 0, read)
                                        textSnippet = if (read == 3000) "$str…" else str
                                    }
                                }
                            }
                        }
                    }
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
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = pal.CardBg,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, pal.DividerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.path.replace('\\', '/').substringAfterLast('/'),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = pal.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (item.isDir) "Folder  •  ${fmt(item.size)}"
                            else "${KIND_NAMES.getOrElse(item.kind) { "File" }}  •  ${fmt(item.size)}",
                            fontSize = 12.sp,
                            color = pal.AccentDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = pal.TextSecond)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Visual / Text Preview Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.Background)
                        .border(1.dp, pal.DividerColor.copy(0.6f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> CircularProgressIndicator(color = pal.Navy, strokeWidth = 2.5.dp)
                        bitmap != null -> {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "File preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        textSnippet != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = textSnippet!!,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = pal.TextPrimary
                                )
                            }
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (item.isDir) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(pal.Navy.copy(0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Folder, null, tint = pal.Navy, modifier = Modifier.size(34.dp))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("DIRECTORY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
                                } else {
                                    val isDark = pal.Background == DarkPalette.Background
                                    val visual = getKindVisual(item.kind, isDark)
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(visual.container),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(visual.icon, null, tint = visual.tint, modifier = Modifier.size(34.dp))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = item.path.substringAfterLast('.').uppercase().ifEmpty { "FILE" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                if (mediaExtra.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(mediaExtra, fontSize = 12.sp, color = pal.TextSecond, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(12.dp))

                // Metadata Table
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = pal.Background,
                    border = BorderStroke(1.dp, pal.DividerColor.copy(0.6f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Location", fontSize = 11.sp, color = pal.TextSecond)
                        Text(
                            item.path,
                            fontSize = 11.sp,
                            color = pal.TextPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider(color = pal.DividerColor.copy(0.6f), thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Modified", fontSize = 11.sp, color = pal.TextSecond)
                            Text(
                                if (item.modified > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.modified)) else "Unknown",
                                fontSize = 11.sp,
                                color = pal.TextPrimary
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Size", fontSize = 11.sp, color = pal.TextSecond)
                            Text("${fmt(item.size)} (${item.size} B)", fontSize = 11.sp, color = pal.TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Crisp Action Buttons — Zero Wrapping / Zero Clipping
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!item.isDir) {
                        CleanPrimaryButton(
                            text = "Open in External App",
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openFileExternally(context, item.path) }
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CleanOutlinedButton(
                            text = "Trash",
                            icon = Icons.Default.DeleteOutline,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onTrash(item.path)
                                onDismiss()
                            }
                        )
                        CleanPrimaryButton(
                            text = "Wipe",
                            icon = Icons.Default.DeleteForever,
                            containerColor = pal.Danger,
                            modifier = Modifier.weight(1f),
                            onClick = { confirmWipe = true }
                        )
                    }
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = pal.CardBg,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = pal.Danger) },
            title = { Text("Permanently Erase?", fontWeight = FontWeight.Bold, color = pal.TextPrimary) },
            text = {
                Text(
                    "DoD 5220.22-M 3-pass overwrite will erase this ${if (item.isDir) "folder" else "file"} permanently.\n\n" +
                    "${item.path.replace('\\', '/').substringAfterLast('/')} (${fmt(item.size)})",
                    fontSize = 13.sp,
                    color = pal.TextSecond
                )
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanOutlinedButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { confirmWipe = false }
                    )
                    CleanPrimaryButton(
                        text = "Erase Now",
                        containerColor = pal.Danger,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmWipe = false
                            onDelete(item.path, SecureDelete.Mode.SECURE_WIPE)
                            onDismiss()
                        }
                    )
                }
            }
        )
    }
}
