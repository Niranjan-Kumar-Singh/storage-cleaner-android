package com.example.storagecleaner.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.KINDS
import com.example.storagecleaner.data.fmt

/** Colour palette for the pie/donut segments, one per KINDS entry */
private val SEGMENT_COLORS = listOf(
    Color(0xFF78909C),  // Other   – blue-grey
    Color(0xFF42A5F5),  // Images  – blue
    Color(0xFF7E57C2),  // Videos  – purple
    Color(0xFF26A69A),  // Audio   – teal
    Color(0xFFFF7043),  // Docs    – deep orange
    Color(0xFFEC407A),  // Archives– pink
    Color(0xFF66BB6A),  // APKs    – green
)

/**
 * Animated donut chart showing storage breakdown by file category.
 *
 * @param kindBytes  LongArray of size 7 — bytes per category matching KINDS indices.
 * @param totalBytes Grand total to show in the centre.
 * @param ringSize   Stroke width of the donut ring.
 */
@Composable
fun StoragePieChart(
    kindBytes: LongArray,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    ringSize: Dp = 28.dp
) {
    val total = kindBytes.sum().coerceAtLeast(1L)

    // Build animated sweep angles for each segment
    val sweeps = kindBytes.mapIndexed { i, bytes ->
        val target = (bytes.toFloat() / total) * 360f
        val animated by animateFloatAsState(
            targetValue    = target,
            animationSpec  = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            label          = "seg$i"
        )
        animated
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Donut ring ───────────────────────────────────────────────────
        Box(
            Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = ringSize.toPx(), cap = StrokeCap.Butt)
                val padding = ringSize.toPx() / 2 + 4f
                var startAngle = -90f

                sweeps.forEachIndexed { i, sweep ->
                    if (sweep > 0.5f) {
                        drawArc(
                            color      = SEGMENT_COLORS[i],
                            startAngle = startAngle,
                            sweepAngle = sweep - 1f,   // 1° gap between segments
                            useCenter  = false,
                            style      = stroke,
                            size       = androidx.compose.ui.geometry.Size(
                                size.width  - padding * 2,
                                size.height - padding * 2
                            ),
                            topLeft    = androidx.compose.ui.geometry.Offset(padding, padding)
                        )
                        startAngle += sweep
                    }
                }
            }
            // Centre label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = fmt(totalBytes),
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    textAlign  = TextAlign.Center
                )
                Text(
                    text      = "total",
                    fontSize  = 11.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Legend ───────────────────────────────────────────────────────
        val cols = 2
        val rows = (KINDS.size + cols - 1) / cols
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (row in 0 until rows) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until cols) {
                        val idx = row * cols + col
                        if (idx < KINDS.size) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(SEGMENT_COLORS[idx])
                                )
                                Column {
                                    Text(
                                        KINDS[idx],
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color      = MaterialTheme.colorScheme.onSurface,
                                        maxLines   = 1
                                    )
                                    Text(
                                        fmt(kindBytes[idx]),
                                        fontSize = 10.sp,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
