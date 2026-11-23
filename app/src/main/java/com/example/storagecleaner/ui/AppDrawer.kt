package com.example.storagecleaner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.RecycleBin

@Composable
fun AppDrawerContent(
    currentScreen: String,
    trashCount: Int,
    themeMode: String,
    onSelectScreen: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onShowReminders: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current
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
                .padding(vertical = 24.dp)
        ) {
            // ── Drawer Header ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
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
                            modifier = Modifier.size(26.dp)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(pal.Navy.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v3.0 Pro",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pal.Navy
                                )
                            }
                            Text(
                                text = "Build 4",
                                fontSize = 11.sp,
                                color = pal.TextSecond
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Privacy assurance pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.CardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, pal.DividerColor.copy(0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                            text = "100% On-Device  •  Zero Telemetry",
                            fontSize = 11.sp,
                            color = pal.TextSecond,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = pal.DividerColor.copy(0.4f), thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            // ── Navigation Section ──────────────────────────────────────────
            Text(
                text = "MAIN MENU",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
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
                label = "App Manager & Cache",
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

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = pal.DividerColor.copy(0.4f), thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            // ── System & About Section ──────────────────────────────────────
            Text(
                text = "SECURITY & ABOUT",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = pal.TextSecond,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )

            DrawerNavItem(
                icon = Icons.Default.Shield,
                label = "DoD 5220.22-M Engine",
                sublabel = "3-pass secure overwrite",
                isSelected = false,
                onClick = { /* Informational */ }
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
                border = androidx.compose.foundation.BorderStroke(1.dp, pal.DividerColor.copy(0.5f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "THEME MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal.TextSecond
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThemeOptionPill(
                            label = "System",
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
        AboutAppDialog(onDismiss = { showAboutDialog = false })
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
    val bg = if (isSelected) pal.Navy.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = if (isSelected) pal.Navy else pal.TextPrimary

    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
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
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        fontSize = 10.sp,
                        color = pal.TextSecond
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
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
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
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}

@Composable
fun AboutAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pal = appPalette()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pal.Navy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Storage Cleaner", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("v3.0.0 Pro • MIT License", fontSize = 11.sp, color = pal.TextSecond)
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
                    text = "A modern, secure on-device storage optimization utility designed to reclaim storage and permanently destroy unwanted files.",
                    fontSize = 13.sp,
                    color = pal.TextPrimary
                )

                HorizontalDivider(color = pal.DividerColor.copy(0.4f))

                // Developer Information Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.Background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("DEVELOPER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.Navy)
                        Text("Niranjan Kumar Singh", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.TextPrimary)
                        Text("Android Engineer & System Developer", fontSize = 11.sp, color = pal.TextSecond)
                    }
                }

                // Architecture Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.Background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ARCHITECTURE & SPECS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.Navy)
                        Text("• Jetpack Compose & Material 3", fontSize = 11.sp, color = pal.TextPrimary)
                        Text("• DoD 5220.22-M 3-Pass Secure Overwrite Engine", fontSize = 11.sp, color = pal.TextPrimary)
                        Text("• Localized Recycle Bin with metadata restore", fontSize = 11.sp, color = pal.TextPrimary)
                        Text("• 100% Offline execution without analytics", fontSize = 11.sp, color = pal.TextPrimary)
                    }
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pal.Navy)
                ) {
                    Icon(Icons.Default.Code, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View GitHub Repository", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
