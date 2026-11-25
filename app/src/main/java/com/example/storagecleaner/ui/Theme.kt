package com.example.storagecleaner.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.fmt

// ─── Colour palette data class ────────────────────────────────────────────
data class AppPalette(
    val Navy: Color, val NavyLight: Color, val NavyDark: Color,
    val PrimaryText: Color,
    val Accent: Color, val AccentDark: Color,
    val Surface: Color, val Background: Color, val CardBg: Color,
    val Danger: Color, val DangerLight: Color, val Warning: Color, val Success: Color,
    val TextPrimary: Color, val TextSecond: Color, val DividerColor: Color,
    val StorageBar: Color, val StorageFree: Color,
    val GlowIndigo: Color, val GlowCyan: Color
)

// ─── Light palette (Crisp Alabaster & Deep Cobalt, WCAG AA+ verified) ──────
val LightPalette = AppPalette(
    Navy         = Color(0xFF3653C9), NavyLight  = Color(0xFF5C7CFA), NavyDark = Color(0xFF283E9E),
    PrimaryText  = Color(0xFF2F4AC4),
    Accent       = Color(0xFF0F766E), AccentDark = Color(0xFF0F766E),
    Surface      = Color(0xFFFFFFFF), Background = Color(0xFFF4F6FA), CardBg   = Color(0xFFFFFFFF),
    Danger       = Color(0xFFDC2626), DangerLight= Color(0xFFFFE4E6), Warning  = Color(0xFFB45309), Success = Color(0xFF047857),
    TextPrimary  = Color(0xFF0F172A), TextSecond = Color(0xFF475569), DividerColor = Color(0xFFE2E8F0),
    StorageBar   = Color(0xFF3653C9), StorageFree = Color(0xFFE2E8F0),
    GlowIndigo   = Color(0xFF748FFC), GlowCyan   = Color(0xFF0D9488)
)

// ─── Dark palette (Midnight Obsidian & Electric Cobalt / Teal) ────────────
val DarkPalette = AppPalette(
    Navy         = Color(0xFF4263EB), NavyLight  = Color(0xFF748FFC), NavyDark = Color(0xFF364FC7),
    PrimaryText  = Color(0xFF829BFF),
    Accent       = Color(0xFF0D9488), AccentDark = Color(0xFF2DD4BF),
    Surface      = Color(0xFF111622), Background = Color(0xFF090C12), CardBg   = Color(0xFF161D2D),
    Danger       = Color(0xFFE11D48), DangerLight= Color(0xFF3B0715), Warning  = Color(0xFFFBBF24), Success = Color(0xFF10B981),
    TextPrimary  = Color(0xFFF8FAFC), TextSecond = Color(0xFF94A3B8), DividerColor = Color(0xFF232D42),
    StorageBar   = Color(0xFF4263EB), StorageFree = Color(0xFF1E293B),
    GlowIndigo   = Color(0xFF91A7FF), GlowCyan   = Color(0xFF2DD4BF)
)

val LocalAppPalette = staticCompositionLocalOf { DarkPalette }

/** Returns the active palette matching the user's selected theme mode. */
@Composable
fun appPalette(): AppPalette = LocalAppPalette.current

// ─── Material3 color schemes ──────────────────────────────────────────────
fun lightAppColorScheme() = lightColorScheme(
    primary              = LightPalette.Navy,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFEDF2FF),
    onPrimaryContainer   = LightPalette.NavyDark,
    secondary            = LightPalette.Accent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFCCFBF1),
    onSecondaryContainer = LightPalette.AccentDark,
    surface              = LightPalette.Surface,
    onSurface            = LightPalette.TextPrimary,
    surfaceVariant       = Color(0xFFF1F5F9),
    onSurfaceVariant     = LightPalette.TextSecond,
    background           = LightPalette.Background,
    onBackground         = LightPalette.TextPrimary,
    error                = LightPalette.Danger,
    onError              = Color.White,
    outline              = LightPalette.DividerColor,
    outlineVariant       = LightPalette.DividerColor
)

fun darkAppColorScheme() = darkColorScheme(
    primary              = DarkPalette.Navy,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF1E293B),
    onPrimaryContainer   = Color(0xFFDBE4FF),
    secondary            = DarkPalette.Accent,
    onSecondary          = Color.Black,
    secondaryContainer   = Color(0xFF134E4A),
    onSecondaryContainer = DarkPalette.AccentDark,
    surface              = DarkPalette.Surface,
    onSurface            = DarkPalette.TextPrimary,
    surfaceVariant       = Color(0xFF1E293B),
    onSurfaceVariant     = DarkPalette.TextSecond,
    background           = DarkPalette.Background,
    onBackground         = DarkPalette.TextPrimary,
    error                = DarkPalette.Danger,
    onError              = Color.White,
    outline              = DarkPalette.DividerColor,
    outlineVariant       = DarkPalette.DividerColor
)

// ─── Standardized, never-clipping button components ───────────────────────
@Composable
fun CleanPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color? = null
) {
    val pal = appPalette()
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: pal.Navy,
            contentColor = Color.White,
            disabledContainerColor = (containerColor ?: pal.Navy).copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.8f)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 46.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CleanOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
    borderColor: Color? = null
) {
    val pal = appPalette()
    val fg = contentColor ?: pal.TextPrimary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor ?: pal.DividerColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Unified flat TopAppBar for sub-screens ───────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val pal = appPalette()
    Column(Modifier.background(pal.Background)) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = pal.TextSecond,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = pal.TextPrimary
                    )
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = pal.Background,
                titleContentColor = pal.TextPrimary
            )
        )
        HorizontalDivider(color = pal.DividerColor.copy(alpha = 0.6f), thickness = 0.75.dp)
    }
}

// ─── Storage progress bar ──────────────────────────────────────────────────
@Composable
fun StorageBar(label: String, used: Long, total: Long, modifier: Modifier = Modifier) {
    val pal  = appPalette()
    val frac = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animFrac by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "storageBar"
    )
    val pct  = (frac * 100).toInt()
    val barColor = when {
        pct >= 90 -> pal.Danger
        pct >= 75 -> pal.Warning
        else      -> pal.Navy
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = pal.TextPrimary, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = barColor.copy(alpha = 0.14f)
            ) {
                Text(
                    "$pct% used",
                    fontSize = 11.sp,
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(9.dp)
                .clip(RoundedCornerShape(5.dp)).background(pal.StorageFree)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(animFrac)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = if (pct >= 75) listOf(barColor, barColor) else listOf(pal.Navy, pal.Accent)
                        )
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${fmt(used)} used", fontSize = 12.sp, color = pal.TextSecond)
            Text("${fmt((total - used).coerceAtLeast(0L))} free", fontSize = 12.sp, color = pal.TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Shimmer loading ────────────────────────────────────────────────────────
@Composable
fun ShimmerRow() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.18f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.Gray.copy(alpha = alpha)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.65f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha * 0.7f)))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(48.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha)))
    }
}

// ─── Shared crisp search bar (zero vertical clipping) ───────────────────────
@Composable
fun CleanSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val pal = appPalette()
    Surface(
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = pal.Surface,
        border = BorderStroke(1.dp, pal.DividerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = pal.TextSecond,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        color = pal.TextSecond,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = pal.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(pal.Navy),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = pal.TextSecond,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Shared status / feedback banner (zero emojis) ────────────────────────
@Composable
fun FeedbackBanner(msg: String, ok: Boolean, onDismiss: () -> Unit) {
    val pal = appPalette()
    val accent = if (ok) pal.Success else pal.Danger
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = msg,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = pal.TextPrimary,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onDismiss, Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", Modifier.size(16.dp), tint = pal.TextSecond)
            }
        }
    }
}
