package com.example.storagecleaner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.BuildConfig

@Composable
fun AppDrawerContent(
    currentScreen: String,
    trashCount: Int,
    themeMode: String,
    onSelectScreen: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onShowReminders: () -> Unit,
    onCheckUpdates: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val pal = appPalette()
    var showAboutDialog by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        drawerContainerColor = pal.Surface,
        drawerContentColor = pal.TextPrimary,
        modifier = Modifier.width(310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp)
        ) {
            // ── Drawer Header ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(pal.Navy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Storage Cleaner",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = pal.TextPrimary
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} • ${BuildConfig.GIT_SHA}",
                            fontSize = 11.sp,
                            color = pal.TextSecond
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.CardBg,
                    border = BorderStroke(1.dp, pal.DividerColor.copy(0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pal.Success)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "On-Device Processing  •  Zero Tracking",
                            fontSize = 11.sp,
                            color = pal.TextSecond,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = pal.DividerColor.copy(0.6f), thickness = 0.75.dp)
            Spacer(Modifier.height(10.dp))

            // ── Navigation Section ──────────────────────────────────────────
            Text(
                text = "WORKSPACE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = pal.TextSecond,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )

            DrawerNavItem(
                icon = Icons.Default.CleaningServices,
                label = "Clean Storage",
                isSelected = currentScreen == "FileList",
                onClick = {
                    onSelectScreen("FileList")
                    onCloseDrawer()
                }
            )

            DrawerNavItem(
                icon = Icons.Default.DeleteOutline,
                label = "Recycle Bin",
                isSelected = currentScreen == "RecycleBin",
                badgeText = if (trashCount > 0) "$trashCount" else null,
                onClick = {
                    onSelectScreen("RecycleBin")
                    onCloseDrawer()
                }
            )

            DrawerNavItem(
                icon = Icons.Outlined.Apps,
                label = "App Manager",
                sublabel = "View app sizes & clear cache",
                isSelected = currentScreen == "AppCache",
                onClick = {
                    onSelectScreen("AppCache")
                    onCloseDrawer()
                }
            )

            DrawerNavItem(
                icon = Icons.Default.NotificationsNone,
                label = "Scan Reminders",
                isSelected = false,
                onClick = {
                    onCloseDrawer()
                    onShowReminders()
                }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = pal.DividerColor.copy(0.6f), thickness = 0.75.dp)
            Spacer(Modifier.height(10.dp))

            // ── System & Updates Section ────────────────────────────────────
            Text(
                text = "SYSTEM & ABOUT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = pal.TextSecond,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )

            DrawerNavItem(
                icon = Icons.Default.SystemUpdate,
                label = "Check for Updates",
                sublabel = "Current: v${BuildConfig.VERSION_NAME}",
                isSelected = false,
                onClick = {
                    onCloseDrawer()
                    onCheckUpdates()
                }
            )

            DrawerNavItem(
                icon = Icons.Default.Shield,
                label = "DoD 5220.22-M Engine",
                sublabel = "3-pass secure overwrite + fsync",
                isSelected = false,
                onClick = {
                    onCloseDrawer()
                    showAboutDialog = true
                }
            )

            DrawerNavItem(
                icon = Icons.Default.Info,
                label = "About App & Developer",
                isSelected = false,
                onClick = {
                    onCloseDrawer()
                    showAboutDialog = true
                }
            )

            Spacer(Modifier.weight(1f))

            // ── Drawer Footer: Theme Selector ───────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = pal.CardBg,
                border = BorderStroke(1.dp, pal.DividerColor.copy(0.7f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "APPEARANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = pal.TextSecond
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThemeOptionPill(
                            label = "Auto",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == "system",
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeChange("system") }
                        )
                        ThemeOptionPill(
                            label = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == "dark",
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeChange("dark") }
                        )
                        ThemeOptionPill(
                            label = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == "light",
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeChange("light") }
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutAppDialog(
            onCheckUpdates = {
                showAboutDialog = false
                onCheckUpdates()
            },
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    sublabel: String? = null,
    isSelected: Boolean,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val pal = appPalette()
    val bg = if (isSelected) pal.Navy.copy(alpha = 0.14f) else Color.Transparent
    val contentColor = if (isSelected) pal.PrimaryText else pal.TextPrimary

    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        fontSize = 11.sp,
                        color = pal.TextSecond,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pal.Navy)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOptionPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = appPalette()
    val bg = if (isSelected) pal.Navy else pal.Background
    val contentColor = if (isSelected) Color.White else pal.TextSecond

    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AboutAppDialog(
    onCheckUpdates: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pal = appPalette()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.CardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.Navy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Storage Cleaner", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = pal.TextPrimary)
                    Text(
                        "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE} • ${BuildConfig.GIT_SHA})",
                        fontSize = 11.sp,
                        color = pal.TextSecond
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "A high-precision Android storage utility built for complete privacy, fast duplicate detection, and verifiable DoD 5220.22-M secure file erasure.",
                    fontSize = 13.sp,
                    color = pal.TextPrimary
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.Background,
                    border = BorderStroke(1.dp, pal.DividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("DEVELOPER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.PrimaryText)
                        Text("Niranjan Kumar Singh", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.TextPrimary)
                        Text("MIT Open Source License", fontSize = 11.sp, color = pal.TextSecond)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.Background,
                    border = BorderStroke(1.dp, pal.DividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CAPABILITIES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.PrimaryText)
                        Text("• SHA-256 2-stage byte-accurate duplicate finder", fontSize = 12.sp, color = pal.TextPrimary)
                        Text("• DoD 5220.22-M 3-pass overwrite + fsync truncation", fontSize = 12.sp, color = pal.TextPrimary)
                        Text("• Safe Recycle Bin with cross-volume restore", fontSize = 12.sp, color = pal.TextPrimary)
                        Text("• Canonical path guard protecting OS/Android directories", fontSize = 12.sp, color = pal.TextPrimary)
                    }
                }

                CleanOutlinedButton(
                    text = "Check for Updates",
                    icon = Icons.Default.SystemUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = pal.PrimaryText,
                    borderColor = pal.PrimaryText.copy(alpha = 0.45f),
                    onClick = onCheckUpdates
                )

                CleanPrimaryButton(
                    text = "Source Code on GitHub",
                    icon = Icons.Default.Code,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
        },
        confirmButton = {
            CleanOutlinedButton(text = "Close", onClick = onDismiss)
        }
    )
}
