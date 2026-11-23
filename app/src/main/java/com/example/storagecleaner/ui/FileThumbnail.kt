package com.example.storagecleaner.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/** Vector and color configuration for file kinds */
data class KindVisual(
    val icon: ImageVector,
    val tint: Color,
    val container: Color
)

fun getKindVisual(kind: Int): KindVisual = when (kind) {
    KIND_IMAGE -> KindVisual(Icons.Default.Image, Color(0xFF38BDF8), Color(0xFF0C4A6E))
    KIND_VIDEO -> KindVisual(Icons.Default.Videocam, Color(0xFFF43F5E), Color(0xFF4C0519))
    KIND_AUDIO -> KindVisual(Icons.Default.Audiotrack, Color(0xFF10B981), Color(0xFF064E3B))
    KIND_DOC -> KindVisual(Icons.Default.Description, Color(0xFFF59E0B), Color(0xFF451A03))
    KIND_ARCHIVE -> KindVisual(Icons.Default.FolderZip, Color(0xFFA855F7), Color(0xFF3B0764))
    KIND_APK -> KindVisual(Icons.Default.Android, Color(0xFF84CC16), Color(0xFF365314))
    else -> KindVisual(Icons.Default.InsertDriveFile, Color(0xFF94A3B8), Color(0xFF1E293B))
}

/** Thumbnail shown beside each file row in the file list */
@Composable
fun FileThumbnail(path: String, kind: Int, modifier: Modifier = Modifier) {
    val visual = getKindVisual(kind)

    if (kind != KIND_IMAGE && kind != KIND_VIDEO) {
        // Non-visual file: show clean vector icon inside tinted rounded square
        Box(
            modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(visual.container.copy(alpha = 0.5f)),
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

    var bmp by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        bmp = null
        failed = false
        bmp = withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 8 // Downsample for thumbnail
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeFile(path, opts)
            } catch (_: Exception) { null }
        }
        if (bmp == null) failed = true
    }

    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(visual.container.copy(alpha = 0.5f)),
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
