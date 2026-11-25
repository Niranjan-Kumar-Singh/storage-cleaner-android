package com.example.storagecleaner.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.BuildConfig
import com.example.storagecleaner.data.UpdateChecker
import com.example.storagecleaner.data.UpdateResult
import com.example.storagecleaner.data.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UpdateCheckDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val pal = appPalette()
    var checking by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    var triggerCount by remember { mutableStateOf(0) }

    LaunchedEffect(triggerCount) {
        checking = true
        result = withContext(Dispatchers.IO) {
            UpdateChecker.checkForUpdate(
                currentVersion = BuildConfig.VERSION_NAME,
                currentGitSha = BuildConfig.GIT_SHA,
                buildTimeMs = BuildConfig.BUILD_TIME_MS
            )
        }
        checking = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.CardBg,
        icon = {
            Surface(
                shape = CircleShape,
                color = pal.Navy.copy(alpha = 0.14f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (result) {
                            is UpdateResult.UpdateAvailable -> Icons.Default.SystemUpdate
                            is UpdateResult.UpToDate -> Icons.Default.Verified
                            is UpdateResult.Error -> Icons.Default.CloudOff
                            null -> Icons.Default.Sync
                        },
                        contentDescription = null,
                        tint = when (result) {
                            is UpdateResult.UpdateAvailable -> pal.AccentDark
                            is UpdateResult.UpToDate -> pal.Success
                            is UpdateResult.Error -> pal.Warning
                            null -> pal.Navy
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = when {
                    checking -> "Checking for Updates…"
                    result is UpdateResult.UpdateAvailable -> "Update Available"
                    result is UpdateResult.UpToDate -> "You're Up to Date"
                    else -> "Update Check"
                },
                fontWeight = FontWeight.Bold,
                color = pal.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (checking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = pal.Navy, strokeWidth = 2.5.dp)
                        Text(
                            "Contacting GitHub Releases for Storage Cleaner…",
                            fontSize = 13.sp,
                            color = pal.TextSecond
                        )
                    }
                } else {
                    when (val res = result) {
                        is UpdateResult.UpdateAvailable -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pal.Background,
                                border = BorderStroke(1.dp, pal.Accent.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(res.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
                                    Text(
                                        buildString {
                                            append("Installed: v${res.currentVersion}")
                                            if (BuildConfig.GIT_SHA != "local") append(" (${BuildConfig.GIT_SHA})")
                                            if (res.apkSizeBytes > 0) append("  •  APK: ${fmt(res.apkSizeBytes)}")
                                        },
                                        fontSize = 11.sp,
                                        color = pal.AccentDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Text(
                                text = res.releaseNotes,
                                fontSize = 12.sp,
                                color = pal.TextSecond,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is UpdateResult.UpToDate -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pal.Background,
                                border = BorderStroke(1.dp, pal.DividerColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Storage Cleaner v${res.currentVersion} (${BuildConfig.GIT_SHA})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pal.TextPrimary
                                    )
                                    Text(
                                        "Latest release on GitHub: ${res.latestLabel}",
                                        fontSize = 12.sp,
                                        color = pal.Success,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Text(
                                "You already have the latest available release.",
                                fontSize = 13.sp,
                                color = pal.TextSecond
                            )
                        }
                        is UpdateResult.Error -> {
                            Text(
                                res.message,
                                fontSize = 13.sp,
                                color = pal.TextSecond
                            )
                        }
                        null -> Unit
                    }
                }
            }
        },
        confirmButton = {
            val res = result
            when {
                checking -> {
                    CleanOutlinedButton(text = "Close", onClick = onDismiss)
                }
                res is UpdateResult.UpdateAvailable -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CleanPrimaryButton(
                            text = "Download Latest APK",
                            icon = Icons.Default.Download,
                            containerColor = pal.Accent,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(res.downloadUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(ctx, "No browser available to open download link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CleanOutlinedButton(
                                text = "Release Page",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    try {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(res.releasePageUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {}
                                }
                            )
                            CleanOutlinedButton(
                                text = "Close",
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss
                            )
                        }
                    }
                }
                res is UpdateResult.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CleanOutlinedButton(
                            text = "Close",
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        )
                        CleanPrimaryButton(
                            text = "Retry",
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                            onClick = { triggerCount++ }
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CleanOutlinedButton(
                            text = "Re-check",
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                            onClick = { triggerCount++ }
                        )
                        CleanPrimaryButton(
                            text = "Done",
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    )
}
