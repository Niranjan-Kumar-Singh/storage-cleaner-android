package com.example.storagecleaner.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.fmt

// ─── Colour palette data class ────────────────────────────────────────────
data class AppPalette(
    val Navy: Color, val NavyLight: Color, val NavyDark: Color,
    val Accent: Color, val AccentDark: Color,
    val Surface: Color, val Background: Color, val CardBg: Color,
    val Danger: Color, val DangerLight: Color, val Warning: Color, val Success: Color,
    val TextPrimary: Color, val TextSecond: Color, val DividerColor: Color,
    val StorageBar: Color, val StorageFree: Color
)

// ─── Light palette ────────────────────────────────────────────────────────
val LightPalette = AppPalette(
    Navy         = Color(0xFF1A237E), NavyLight  = Color(0xFF3949AB), NavyDark = Color(0xFF0D1B6E),
    Accent       = Color(0xFF00BCD4), AccentDark = Color(0xFF0097A7),
    Surface      = Color(0xFFFFFFFF), Background = Color(0xFFF5F7FA), CardBg  = Color(0xFFFFFFFF),
    Danger       = Color(0xFFD32F2F), DangerLight= Color(0xFFFFCDD2), Warning = Color(0xFFF57F17), Success = Color(0xFF2E7D32),
    TextPrimary  = Color(0xFF1A1A2E), TextSecond = Color(0xFF6B7280), DividerColor = Color(0xFFE5E7EB),
    StorageBar   = Color(0xFF3949AB), StorageFree = Color(0xFFE8EAF6)
)

// ─── Dark palette ─────────────────────────────────────────────────────────
val DarkPalette = AppPalette(
    Navy         = Color(0xFF7986CB), NavyLight  = Color(0xFF5C6BC0), NavyDark = Color(0xFF3949AB),
    Accent       = Color(0xFF4DD0E1), AccentDark = Color(0xFF00ACC1),
    Surface      = Color(0xFF1A1E2E), Background = Color(0xFF0F1119), CardBg  = Color(0xFF1E2234),
    Danger       = Color(0xFFEF5350), DangerLight= Color(0xFF4A1515), Warning = Color(0xFFFFB74D), Success = Color(0xFF66BB6A),
    TextPrimary  = Color(0xFFE8EAF6), TextSecond = Color(0xFF9BA3B6), DividerColor = Color(0xFF2D3250),
    StorageBar   = Color(0xFF7986CB), StorageFree = Color(0xFF1E2234)
)

/** Legacy singleton — kept for backward compatibility; resolves to LightPalette */
object AppColors {
    val Navy         get() = LightPalette.Navy
    val NavyLight    get() = LightPalette.NavyLight
    val NavyDark     get() = LightPalette.NavyDark
    val Accent       get() = LightPalette.Accent
    val AccentDark   get() = LightPalette.AccentDark
    val Surface      get() = LightPalette.Surface
    val Background   get() = LightPalette.Background
    val CardBg       get() = LightPalette.CardBg
    val Danger       get() = LightPalette.Danger
    val DangerLight  get() = LightPalette.DangerLight
    val Warning      get() = LightPalette.Warning
    val Success      get() = LightPalette.Success
    val TextPrimary  get() = LightPalette.TextPrimary
    val TextSecond   get() = LightPalette.TextSecond
    val DividerColor get() = LightPalette.DividerColor
    val StorageBar   get() = LightPalette.StorageBar
    val StorageFree  get() = LightPalette.StorageFree

    val navyGradient = Brush.linearGradient(
        colors = listOf(LightPalette.Navy, LightPalette.NavyLight),
        start  = Offset(0f, 0f),
        end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
}

/** Returns the correct palette for the current system theme. */
@Composable
fun appPalette(): AppPalette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

// ─── Material3 color schemes ──────────────────────────────────────────────
fun lightAppColorScheme() = lightColorScheme(
    primary            = LightPalette.Navy,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFE8EAF6),
    onPrimaryContainer = LightPalette.NavyDark,
    secondary          = LightPalette.Accent,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = LightPalette.AccentDark,
    surface            = LightPalette.Surface,
    onSurface          = LightPalette.TextPrimary,
    surfaceVariant     = Color(0xFFF0F2FF),
    onSurfaceVariant   = LightPalette.TextSecond,
    background         = LightPalette.Background,
    onBackground       = LightPalette.TextPrimary,
    error              = LightPalette.Danger,
    outlineVariant     = LightPalette.DividerColor
)

fun darkAppColorScheme() = darkColorScheme(
    primary            = DarkPalette.Navy,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF1A237E),
    onPrimaryContainer = Color(0xFFE8EAF6),
    secondary          = DarkPalette.Accent,
    onSecondary        = Color.Black,
    secondaryContainer = Color(0xFF00363D),
    onSecondaryContainer = DarkPalette.Accent,
    surface            = DarkPalette.Surface,
    onSurface          = DarkPalette.TextPrimary,
    surfaceVariant     = Color(0xFF242840),
    onSurfaceVariant   = DarkPalette.TextSecond,
    background         = DarkPalette.Background,
    onBackground       = DarkPalette.TextPrimary,
    error              = DarkPalette.Danger,
    outlineVariant     = DarkPalette.DividerColor
)

// ─── Storage progress bar ──────────────────────────────────────────────────
@Composable
fun StorageBar(label: String, used: Long, total: Long, modifier: Modifier = Modifier) {
    val pal  = appPalette()
    val frac = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val pct  = (frac * 100).toInt()
    val barColor = when {
        pct >= 90 -> pal.Danger
        pct >= 75 -> pal.Warning
        else      -> pal.Navy
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = pal.TextSecond, fontWeight = FontWeight.Medium)
            Text("$pct% used", fontSize = 13.sp, color = barColor, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(pal.StorageFree)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .clip(RoundedCornerShape(5.dp)).background(barColor)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Used: ${fmt(used)}", fontSize = 11.sp, color = pal.TextSecond)
            Text("Free: ${fmt(total - used)}", fontSize = 11.sp, color = pal.TextSecond)
        }
    }
}

// ─── Shimmer loading ────────────────────────────────────────────────────────
@Composable
fun ShimmerRow() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = alpha)))
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
