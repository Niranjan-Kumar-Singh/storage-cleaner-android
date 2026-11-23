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
    val StorageBar: Color, val StorageFree: Color,
    val GlowIndigo: Color, val GlowCyan: Color
)

// ─── Light palette (Ultra Clean Porcelain & Royal Indigo) ─────────────────
val LightPalette = AppPalette(
    Navy         = Color(0xFF4F46E5), NavyLight  = Color(0xFF6366F1), NavyDark = Color(0xFF3730A3),
    Accent       = Color(0xFF06B6D4), AccentDark = Color(0xFF0891B2),
    Surface      = Color(0xFFFFFFFF), Background = Color(0xFFF8FAFC), CardBg  = Color(0xFFFFFFFF),
    Danger       = Color(0xFFF43F5E), DangerLight= Color(0xFFFFE4E6), Warning = Color(0xFFF59E0B), Success = Color(0xFF10B981),
    TextPrimary  = Color(0xFF0F172A), TextSecond = Color(0xFF64748B), DividerColor = Color(0xFFE2E8F0),
    StorageBar   = Color(0xFF4F46E5), StorageFree = Color(0xFFEEF2F6),
    GlowIndigo   = Color(0xFF818CF8), GlowCyan   = Color(0xFF22D3EE)
)

// ─── Dark palette (Luxury Deep Space Obsidian & Cyber Indigo) ─────────────
val DarkPalette = AppPalette(
    Navy         = Color(0xFF6366F1), NavyLight  = Color(0xFF818CF8), NavyDark = Color(0xFF4338CA),
    Accent       = Color(0xFF22D3EE), AccentDark = Color(0xFF06B6D4),
    Surface      = Color(0xFF111827), Background = Color(0xFF090D16), CardBg  = Color(0xFF161F30),
    Danger       = Color(0xFFFB7185), DangerLight= Color(0xFF4C0519), Warning = Color(0xFFFBBF24), Success = Color(0xFF34D399),
    TextPrimary  = Color(0xFFF8FAFC), TextSecond = Color(0xFF94A3B8), DividerColor = Color(0xFF1F2937),
    StorageBar   = Color(0xFF6366F1), StorageFree = Color(0xFF1F293D),
    GlowIndigo   = Color(0xFFA5B4FC), GlowCyan   = Color(0xFF67E8F9)
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

fun primaryGradient(pal: AppPalette) = Brush.horizontalGradient(
    colors = listOf(pal.NavyDark, pal.Navy)
)

fun cyanIndigoGradient(pal: AppPalette) = Brush.horizontalGradient(
    colors = listOf(pal.Navy, pal.Accent)
)

/** Returns the correct palette for the current system theme. */
@Composable
fun appPalette(): AppPalette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

// ─── Material3 color schemes ──────────────────────────────────────────────
fun lightAppColorScheme() = lightColorScheme(
    primary            = LightPalette.Navy,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFEEF2FF),
    onPrimaryContainer = LightPalette.NavyDark,
    secondary          = LightPalette.Accent,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = LightPalette.AccentDark,
    surface            = LightPalette.Surface,
    onSurface          = LightPalette.TextPrimary,
    surfaceVariant     = Color(0xFFF1F5F9),
    onSurfaceVariant   = LightPalette.TextSecond,
    background         = LightPalette.Background,
    onBackground       = LightPalette.TextPrimary,
    error              = LightPalette.Danger,
    outlineVariant     = LightPalette.DividerColor
)

fun darkAppColorScheme() = darkColorScheme(
    primary            = DarkPalette.Navy,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF1E1B4B),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary          = DarkPalette.Accent,
    onSecondary        = Color.Black,
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = DarkPalette.Accent,
    surface            = DarkPalette.Surface,
    onSurface          = DarkPalette.TextPrimary,
    surfaceVariant     = Color(0xFF1E293B),
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
            Text(label, fontSize = 13.sp, color = pal.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("$pct% used", fontSize = 13.sp, color = barColor, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(pal.StorageFree)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .clip(RoundedCornerShape(5.dp)).background(barColor)
            )
        }
        Spacer(Modifier.height(4.dp))
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
