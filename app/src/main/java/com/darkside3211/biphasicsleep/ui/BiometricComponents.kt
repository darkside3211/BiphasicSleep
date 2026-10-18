package com.darkside3211.biphasicsleep.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkside3211.biphasicsleep.domain.GuardrailPermission
import com.darkside3211.biphasicsleep.domain.SleepBlockType
import java.time.LocalTime

/**
 * Custom Compose Canvas circular arc telemetry gauge.
 * Displays ultradian cycle blocks or countdown timers with an Oura-inspired glow.
 */
@Composable
fun CircadianArcGauge(
    progress: Float,
    strokeWidth: Dp = 14.dp,
    arcColor: Color = AnchorCyan,
    trackColor: Color = ObsidianBorderSubtle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "ArcProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            val startAngle = 140f
            val sweepAngle = 260f

            // Background Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active Progress Arc
            if (animatedProgress > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to arcColor.copy(alpha = 0.8f),
                        0.7f to arcColor,
                        1.0f to arcColor
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Segmented horizontal visual bar showing:
 * 14m Onset Buffer + Cycle 1 (90m) + Cycle 2 (90m) + Cycle 3 (90m)
 */
@Composable
fun CycleBreakdownBar(
    blockType: SleepBlockType,
    latencyMinutes: Int = 14,
    cycleMinutes: Int = 90,
    accentColor: Color = AnchorCyan,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ObsidianBorderSubtle),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            when (blockType) {
                SleepBlockType.CORE_3_CYCLES -> {
                    // Latency Buffer (14m)
                    Box(
                        modifier = Modifier
                            .weight(14f)
                            .height(8.dp)
                            .background(accentColor.copy(alpha = 0.45f))
                    )
                    // Cycle 1 (90m)
                    Box(
                        modifier = Modifier
                            .weight(90f)
                            .height(8.dp)
                            .background(accentColor)
                    )
                    // Cycle 2 (90m)
                    Box(
                        modifier = Modifier
                            .weight(90f)
                            .height(8.dp)
                            .background(accentColor)
                    )
                    // Cycle 3 (90m)
                    Box(
                        modifier = Modifier
                            .weight(90f)
                            .height(8.dp)
                            .background(accentColor)
                    )
                }
                SleepBlockType.BRIDGE_2_CYCLES -> {
                    // Latency Buffer (14m)
                    Box(
                        modifier = Modifier
                            .weight(14f)
                            .height(8.dp)
                            .background(CircadianAmber.copy(alpha = 0.45f))
                    )
                    // Cycle 1 (90m)
                    Box(
                        modifier = Modifier
                            .weight(90f)
                            .height(8.dp)
                            .background(CircadianAmber)
                    )
                    // Cycle 2 (90m)
                    Box(
                        modifier = Modifier
                            .weight(90f)
                            .height(8.dp)
                            .background(CircadianAmber)
                    )
                }
                SleepBlockType.FLUSH_NAP_20M -> {
                    // 20m Flush
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(AnchorCyan)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (blockType == SleepBlockType.FLUSH_NAP_20M) {
                Text(
                    text = "Strict 20m Stage 1/2 Cap",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSilver
                )
                Text(
                    text = "Adenosine Flush",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnchorCyan,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "${latencyMinutes}m onset latency + ${blockType.cyclesCount} × ${cycleMinutes}m cycles",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftSilver
                )
                Text(
                    text = "${blockType.cyclesCount} Complete Cycles",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 24-Hour horizontal timeline bar rendered on Canvas.
 * Visually shows where the shift, sleep window, and daylight/caffeine rules fall in a 24-hour day.
 */
@Composable
fun TwentyFourHourTimelineBar(
    sleepStart: LocalTime?,
    sleepEnd: LocalTime?,
    highlightColor: Color = AnchorCyan,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            val totalMinutes = 24f * 60f

            // Full day base track
            drawRect(
                color = ObsidianBorderSubtle,
                topLeft = Offset.Zero,
                size = size
            )

            // Shift Window (21:00 - 06:00)
            val shiftStartMin = 21f * 60f
            val shiftEndMin = 6f * 60f
            val shiftX1 = (shiftStartMin / totalMinutes) * size.width
            val shiftW1 = size.width - shiftX1
            drawRect(
                color = Color(0xFF1E283D),
                topLeft = Offset(shiftX1, 0f),
                size = Size(shiftW1, size.height)
            )
            val shiftW2 = (shiftEndMin / totalMinutes) * size.width
            drawRect(
                color = Color(0xFF1E283D),
                topLeft = Offset.Zero,
                size = Size(shiftW2, size.height)
            )

            // Sleep Window highlight
            if (sleepStart != null && sleepEnd != null) {
                val startMin = sleepStart.hour * 60f + sleepStart.minute
                val endMin = sleepEnd.hour * 60f + sleepEnd.minute

                if (endMin >= startMin) {
                    val x = (startMin / totalMinutes) * size.width
                    val w = ((endMin - startMin) / totalMinutes) * size.width
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(x, 0f),
                        size = Size(w.coerceAtLeast(6f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                } else {
                    // Crosses midnight
                    val x1 = (startMin / totalMinutes) * size.width
                    val w1 = size.width - x1
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(x1, 0f),
                        size = Size(w1.coerceAtLeast(4f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                    val w2 = (endMin / totalMinutes) * size.width
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset.Zero,
                        size = Size(w2.coerceAtLeast(4f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
            }

            // Midnight tick (00:00), 06:00, 12:00, 18:00
            val tickColor = Color(0xFF334563)
            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                val x = fraction * size.width
                drawLine(
                    color = tickColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MutedSlate, fontSize = 9.sp)
            Text("06:00", style = MaterialTheme.typography.labelSmall, color = MutedSlate, fontSize = 9.sp)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MutedSlate, fontSize = 9.sp)
            Text("18:00", style = MaterialTheme.typography.labelSmall, color = MutedSlate, fontSize = 9.sp)
            Text("24:00", style = MaterialTheme.typography.labelSmall, color = MutedSlate, fontSize = 9.sp)
        }
    }
}

/**
 * Replaces raw emoji and ugly badge text with a sleek, rounded pill containing
 * a crisp Material Icon and short label.
 */
@Composable
fun StatusBadgePill(
    permission: GuardrailPermission,
    modifier: Modifier = Modifier
) {
    val (bg, contentColor, icon, text) = when (permission) {
        GuardrailPermission.PRIMARY_RECOMMENDED -> Quadruple(
            VerifiedEmeraldBg,
            VerifiedEmerald,
            Icons.Rounded.CheckCircle,
            "PRIMARY ANCHOR"
        )
        GuardrailPermission.ALLOWED_SECONDARY -> Quadruple(
            Color(0xFF152233),
            AnchorCyan,
            Icons.Rounded.Schedule,
            "SECONDARY"
        )
        GuardrailPermission.WARN_INSOMNIA_OR_CIRCADIAN_RISK -> Quadruple(
            Color(0xFF2E1F0A),
            CircadianAmber,
            Icons.Rounded.WarningAmber,
            "GUARDRAIL WARN"
        )
        GuardrailPermission.LOCKED_ZERO_NAP_PROTOCOL -> Quadruple(
            LockoutCrimsonBg,
            LockoutCrimson,
            Icons.Rounded.Lock,
            "LOCKED"
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, contentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = contentColor,
            fontSize = 10.sp
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Elevated biometric card with subtle gradient highlight.
 */
@Composable
fun BiometricCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianBorder,
    backgroundColor: Color = ObsidianCard,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}
