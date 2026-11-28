package com.example.storagecleaner.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storagecleaner.data.KINDS
import com.example.storagecleaner.data.fmt

private val SEGMENT_COLORS = listOf(
    Color(0xFF64748B),  // Other    – Slate
    Color(0xFF0EA5E9),  // Images   – Sky
    Color(0xFFF43F5E),  // Videos   – Rose
    Color(0xFF10B981),  // Audio    – Emerald
    Color(0xFFF59E0B),  // Docs     – Amber
    Color(0xFF14B8A6),  // Archives – Teal
    Color(0xFF2563EB),  // APKs     – Sapphire
)

@Composable
fun StoragePieChart(
    kindBytes: LongArray,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    ringSize: Dp = 26.dp
) {
    val pal = appPalette()
    val total = kindBytes.sum().coerceAtLeast(1L)

    val sweeps = kindBytes.mapIndexed { i, bytes ->
        val target = (bytes.toFloat() / total) * 360f
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            label = "seg$i"
        )
        animated
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(172.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = ringSize.toPx(), cap = StrokeCap.Butt)
                val padding = ringSize.toPx() / 2 + 4f
                var startAngle = -90f

                sweeps.forEachIndexed { i, sweep ->
                    if (sweep > 0.6f) {
                        drawArc(
                            color = SEGMENT_COLORS[i],
                            startAngle = startAngle,
                            sweepAngle = (sweep - 1.2f).coerceAtLeast(0.5f),
                            useCenter = false,
                            style = stroke,
                            size = androidx.compose.ui.geometry.Size(
                                size.width - padding * 2,
                                size.height - padding * 2
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(padding, padding)
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = fmt(totalBytes),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.TextPrimary,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                Text(
                    text = "analyzed",
                    fontSize = 11.sp,
                    color = pal.TextSecond,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val cols = 2
        val rows = (KINDS.size + cols - 1) / cols
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until rows) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until cols) {
                        val idx = row * cols + col
                        if (idx < KINDS.size) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(SEGMENT_COLORS[idx])
                                )
                                Column {
                                    Text(
                                        KINDS[idx],
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = pal.TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        fmt(kindBytes[idx]),
                                        fontSize = 11.sp,
                                        color = pal.TextSecond,
                                        style = TextStyle(fontFeatureSettings = "tnum")
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
