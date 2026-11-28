package com.example.storagecleaner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.storagecleaner.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Vector and color configuration for file kinds */
data class KindVisual(
    val icon: ImageVector,
    val tint: Color,
    val container: Color
)

fun getKindVisual(kind: Int, isDark: Boolean = true): KindVisual {
    val tint = when (kind) {
        KIND_IMAGE   -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        KIND_VIDEO   -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
        KIND_AUDIO   -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        KIND_DOC     -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
        KIND_ARCHIVE -> if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        KIND_APK     -> if (isDark) Color(0xFFA3E635) else Color(0xFF4D7C0F)
        else         -> if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    }
    val icon = when (kind) {
        KIND_IMAGE   -> Icons.Default.Image
        KIND_VIDEO   -> Icons.Default.Videocam
        KIND_AUDIO   -> Icons.Default.Audiotrack
        KIND_DOC     -> Icons.Default.Description
        KIND_ARCHIVE -> Icons.Default.FolderZip
        KIND_APK     -> Icons.Default.Android
        else         -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    return KindVisual(icon = icon, tint = tint, container = tint.copy(alpha = if (isDark) 0.16f else 0.12f))
}

/** Thumbnail shown beside each file row in the file list (renders real image and video frames). */
@Composable
fun FileThumbnail(path: String, kind: Int, modifier: Modifier = Modifier) {
    val pal = appPalette()
    val isDark = pal.Background == DarkPalette.Background
    val visual = getKindVisual(kind, isDark)

    if (kind != KIND_IMAGE && kind != KIND_VIDEO) {
        Box(
            modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(visual.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier.size(22.dp)
            )
        }
        return
    }

    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path, kind) {
        bmp = null
        failed = false
        bmp = withContext(Dispatchers.IO) {
            try {
                if (kind == KIND_VIDEO) {
                    runCatching {
                        ThumbnailUtils.createVideoThumbnail(File(path), Size(128, 128), null)
                    }.getOrNull() ?: run {
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(path)
                            mmr.frameAtTime
                        } catch (_: Exception) {
                            null
                        } finally {
                            mmr.release()
                        }
                    }
                } else {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 8
                        inJustDecodeBounds = false
                    }
                    BitmapFactory.decodeFile(path, opts)
                }
            } catch (_: Exception) {
                null
            }
        }
        if (bmp == null) failed = true
    }

    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(visual.container),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (failed) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier.size(20.dp)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = visual.tint
            )
        }
    }
}
