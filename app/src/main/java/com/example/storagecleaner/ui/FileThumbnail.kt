package com.example.storagecleaner.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.KIND_IMAGE
import com.example.storagecleaner.data.KIND_VIDEO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Thumbnail shown beside each image/video row in the file list */
@Composable
fun FileThumbnail(path: String, kind: Int, modifier: Modifier = Modifier) {
    val pal = appPalette()
    if (kind != KIND_IMAGE && kind != KIND_VIDEO) {
        // Non-visual file: show kind icon placeholder
        Box(
            modifier.size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(pal.StorageFree),
            contentAlignment = Alignment.Center
        ) {
            Text(kindEmoji(kind), fontSize = 20.sp, textAlign = TextAlign.Center)
        }
        return
    }

    var bmp by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        bmp = null; failed = false
        bmp = withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 8      // downsample heavily — just a thumbnail
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeFile(path, opts)
            } catch (_: Exception) { null }
        }
        if (bmp == null) failed = true
    }

    Box(
        modifier.size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(pal.StorageFree),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap       = b.asImageBitmap(),
                contentDescription = null,
                modifier     = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(if (failed) "🖼️" else "⏳", fontSize = 18.sp)
        }
    }
}

fun kindEmoji(kind: Int) = when (kind) {
    1 -> "🖼️"
    2 -> "🎬"
    3 -> "🎵"
    4 -> "📄"
    5 -> "📦"
    6 -> "📱"
    else -> "📁"
}

fun kindLabel(kind: Int) = when (kind) {
    1 -> "Image"
    2 -> "Video"
    3 -> "Audio"
    4 -> "Document"
    5 -> "Archive"
    6 -> "APK"
    else -> "File"
}
