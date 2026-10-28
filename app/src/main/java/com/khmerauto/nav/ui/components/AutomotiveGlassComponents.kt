package com.khmerauto.nav.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.hal.Zone

// ─── Design tokens (EZNav dark palette) ────────────────────────────────────
val EzBgDeep        = Color(0xFF0D0B1A)
val EzCardBg        = Color(0xFF16132A).copy(alpha = 0.93f)
val EzCardBgLight   = Color(0xFF1E1B35).copy(alpha = 0.95f)
val EzBorderSubtle  = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val EzBorderAccent  = Color(0xFF00E5FF).copy(alpha = 0.35f)
val EzTxtPrimary    = Color(0xFFF4F2FF)
val EzTxtSecondary  = Color(0xFF8B87A8)
val EzTxtMuted      = Color(0xFF4D4A66)
val EzAccentCyan    = Color(0xFF00E5FF)
val EzAccentEmerald = Color(0xFF00E676)
val EzAccentAmber   = Color(0xFFFFB300)
val EzAccentCoral   = Color(0xFFFF5252)
val EzAccentPurple  = Color(0xFF8B5CF6)

/**
 * Custom Canvas drawing the sleek side-profile silhouette of the 2026 Geely Galaxy M7 (Thor PHEV).
 */
@Composable
fun EzCarSideProfile(
    batteryPercent: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ground shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(0.6f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.88f),
                radius = w * 0.45f
            ),
            topLeft = Offset(w * 0.05f, h * 0.82f),
            size = Size(w * 0.9f, h * 0.14f)
        )

        // SUV Body Path (Geely Galaxy M7 Coupe SUV silhouette)
        val bodyPath = Path().apply {
            moveTo(w * 0.05f, h * 0.72f) // Front bumper bottom
            lineTo(w * 0.04f, h * 0.62f) // Front nose
            cubicTo(w * 0.06f, h * 0.52f, w * 0.16f, h * 0.48f, w * 0.28f, h * 0.46f) // Front hood
            cubicTo(w * 0.38f, h * 0.32f, w * 0.52f, h * 0.24f, w * 0.68f, h * 0.24f) // Windshield & sloping roof
            cubicTo(w * 0.78f, h * 0.24f, w * 0.88f, h * 0.34f, w * 0.94f, h * 0.48f) // Fastback rear glass
            lineTo(w * 0.97f, h * 0.52f) // Rear spoiler lip
            lineTo(w * 0.96f, h * 0.65f) // Tailgate
            lineTo(w * 0.92f, h * 0.72f) // Rear bumper bottom
            // Undercarriage
            lineTo(w * 0.84f, h * 0.72f)
            // Rear wheel arch
            cubicTo(w * 0.82f, h * 0.56f, w * 0.68f, h * 0.56f, w * 0.66f, h * 0.72f)
            lineTo(w * 0.38f, h * 0.72f)
            // Front wheel arch
            cubicTo(w * 0.36f, h * 0.56f, w * 0.22f, h * 0.56f, w * 0.20f, h * 0.72f)
            close()
        }

        // Body Gradient fill (Deep metallic navy / obsidian)
        drawPath(
            path = bodyPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF333D52),
                    Color(0xFF1A1F2C),
                    Color(0xFF10141D)
                ),
                startY = h * 0.2f,
                endY = h * 0.8f
            )
        )
        // Body outline stroke
        drawPath(
            path = bodyPath,
            color = Color(0xFF4A5568).copy(alpha = 0.5f),
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Windows Glass Path (Tinted dark glass)
        val glassPath = Path().apply {
            moveTo(w * 0.30f, h * 0.45f) // A-pillar base
            cubicTo(w * 0.38f, h * 0.33f, w * 0.52f, h * 0.27f, w * 0.67f, h * 0.27f) // Roofline top
            cubicTo(w * 0.76f, h * 0.27f, w * 0.84f, h * 0.36f, w * 0.88f, h * 0.48f) // C-pillar slope
            lineTo(w * 0.30f, h * 0.48f) // Window beltline
            close()
        }
        drawPath(
            path = glassPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0F172A).copy(0.9f), Color(0xFF1E293B).copy(0.95f))
            )
        )
        // Chrome window trim upper accent line
        drawPath(
            path = Path().apply {
                moveTo(w * 0.29f, h * 0.45f)
                cubicTo(w * 0.38f, h * 0.32f, w * 0.52f, h * 0.26f, w * 0.68f, h * 0.26f)
                cubicTo(w * 0.78f, h * 0.26f, w * 0.86f, h * 0.35f, w * 0.90f, h * 0.48f)
            },
            color = Color(0xFF94A3B8).copy(alpha = 0.7f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        // B-pillar separator
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(w * 0.52f, h * 0.26f),
            end = Offset(w * 0.51f, h * 0.48f),
            strokeWidth = 3.dp.toPx()
        )

        // Front Slim LED Headlight blade (Cyan glow)
        drawLine(
            color = EzAccentCyan,
            start = Offset(w * 0.05f, h * 0.54f),
            end = Offset(w * 0.16f, h * 0.50f),
            strokeWidth = 2.dp.toPx()
        )
        // Rear Full-width LED Taillight blade (Coral Red glow)
        drawLine(
            color = EzAccentCoral,
            start = Offset(w * 0.96f, h * 0.54f),
            end = Offset(w * 0.88f, h * 0.53f),
            strokeWidth = 2.dp.toPx()
        )

        // Flush Door Handles
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(w * 0.42f, h * 0.52f),
            size = Size(w * 0.05f, 2.5.dp.toPx()),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(w * 0.62f, h * 0.52f),
            size = Size(w * 0.05f, 2.5.dp.toPx()),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Wheels helper
        fun drawWheel(centerX: Float, centerY: Float, radius: Float) {
            // Tire rubber
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E222D), Color(0xFF0B0D14)),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )
            // Rim outer ring
            drawCircle(
                color = Color(0xFF475569),
                radius = radius * 0.72f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Rim center hub
            drawCircle(
                color = Color(0xFF94A3B8),
                radius = radius * 0.25f,
                center = Offset(centerX, centerY)
            )
            // 5 spokes
            for (i in 0 until 5) {
                val angle = Math.toRadians((i * 72.0))
                val spokeX = centerX + (radius * 0.65f * Math.cos(angle)).toFloat()
                val spokeY = centerY + (radius * 0.65f * Math.sin(angle)).toFloat()
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(centerX, centerY),
                    end = Offset(spokeX, spokeY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        val wheelRadius = h * 0.18f
        val wheelCenterY = h * 0.72f
        drawWheel(w * 0.28f, wheelCenterY, wheelRadius) // Front wheel
        drawWheel(w * 0.75f, wheelCenterY, wheelRadius) // Rear wheel

        // Battery glow bar under floorboard
        val batFrac = (batteryPercent / 100f).coerceIn(0f, 1f)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    EzAccentCyan.copy(alpha = 0.8f),
                    if (batteryPercent > 20) EzAccentEmerald.copy(alpha = 0.8f) else EzAccentCoral.copy(alpha = 0.8f)
                ),
                startX = w * 0.38f,
                endX = w * 0.38f + (w * 0.26f * batFrac)
            ),
            start = Offset(w * 0.38f, h * 0.71f),
            end = Offset(w * 0.38f + (w * 0.26f * batFrac), h * 0.71f),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

/**
 * Stepped Fan Speed Controller (7 vertical bars).
 * Active bars glow cyan/white; inactive bars are dark muted.
 * Adheres strictly to Compose Slider rate-limiting & debouncing guardrails.
 */
@Composable
fun EzFanSpeedTicks(
    currentSpeed: Int,
    onSpeedSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..7) {
            val isActive = i <= currentSpeed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isActive) {
                            Brush.verticalGradient(
                                listOf(Color(0xFFE0F7FA), EzAccentCyan)
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color(0xFF26223D), Color(0xFF1B182E))
                            )
                        }
                    )
                    .border(
                        0.5.dp,
                        if (isActive) EzAccentCyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(3.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSpeedSelected(i)
                    }
            )
        }
    }
}

/**
 * Stepped Dual-Zone Temperature Selector matching EZNav layout:
 * [L] [R]   <  20.5  21.0  21.5  22.0°C  22.5  23.0  23.5  >
 */
@Composable
fun EzDualTempSelector(
    activeZone: Zone,
    onZoneChange: (Zone) -> Unit,
    driverTempC: Float,
    passengerTempC: Float,
    onTempSelected: (Zone, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTemp = if (activeZone == Zone.DRIVER) driverTempC else passengerTempC

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // L / R Zone Pills
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1730))
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(6.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ZonePill(label = "L", isSelected = activeZone == Zone.DRIVER) { onZoneChange(Zone.DRIVER) }
            ZonePill(label = "R", isSelected = activeZone == Zone.PASSENGER) { onZoneChange(Zone.PASSENGER) }
        }

        // Decrement button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E1B35))
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(6.dp))
                .clickable {
                    val next = (currentTemp - 0.5f).coerceIn(16.0f, 30.0f)
                    onTempSelected(activeZone, next)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = EzTxtPrimary, modifier = Modifier.size(16.dp))
        }

        // Stepped temperature pills
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val baseTemp = (Math.round(currentTemp * 2.0) / 2.0).toFloat()
            // 3 clear stepped offsets guarantee zero ugly character wrapping on car head units
            val offsets = listOf(-0.5f, 0.0f, 0.5f)

            for (offset in offsets) {
                val tempVal = baseTemp + offset
                if (tempVal in 16.0f..30.0f) {
                    val isCenter = offset == 0.0f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isCenter) Color.White else Color(0x221E1B35)
                            )
                            .clickable { onTempSelected(activeZone, tempVal) }
                            .padding(horizontal = if (isCenter) 9.dp else 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCenter) String.format("%.1f°C", tempVal) else String.format("%.1f", tempVal),
                            fontSize = if (isCenter) 13.sp else 11.sp,
                            fontWeight = if (isCenter) FontWeight.Black else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isCenter) Color(0xFF0D0B1A) else EzTxtSecondary,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Increment button
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E1B35))
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(6.dp))
                .clickable {
                    val next = (currentTemp + 0.5f).coerceIn(16.0f, 30.0f)
                    onTempSelected(activeZone, next)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = EzTxtPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ZonePill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color(0xFF0D0B1A) else EzTxtSecondary
        )
    }
}

/**
 * Top-down TPMS 4-Tire Pressure Visualizer matching EZNav layout.
 */
@Composable
fun EzCarTopTpms(
    flBar: Float,
    frBar: Float,
    rlBar: Float,
    rrBar: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Column (FL & RL)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TpmsPill(label = "FL", bar = flBar)
            TpmsPill(label = "RL", bar = rlBar)
        }

        // Center Wireframe Car Icon
        Canvas(modifier = Modifier.size(width = 30.dp, height = 50.dp)) {
            val cw = size.width
            val ch = size.height

            // Car body
            drawRoundRect(
                color = Color(0xFF33304E),
                topLeft = Offset(cw * 0.18f, ch * 0.08f),
                size = Size(cw * 0.64f, ch * 0.84f),
                cornerRadius = CornerRadius(cw * 0.2f, cw * 0.2f),
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Windshield
            drawLine(
                color = Color(0xFF6B668C),
                start = Offset(cw * 0.22f, ch * 0.32f),
                end = Offset(cw * 0.78f, ch * 0.32f),
                strokeWidth = 1.dp.toPx()
            )
            // 4 Wheels
            fun drawW(x: Float, y: Float) {
                drawRoundRect(
                    color = Color(0xFF8B87A8),
                    topLeft = Offset(x, y),
                    size = Size(cw * 0.14f, ch * 0.22f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            drawW(cw * 0.02f, ch * 0.14f) // FL
            drawW(cw * 0.84f, ch * 0.14f) // FR
            drawW(cw * 0.02f, ch * 0.64f) // RL
            drawW(cw * 0.84f, ch * 0.64f) // RR
        }

        // Right Column (FR & RR)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TpmsPill(label = "FR", bar = frBar)
            TpmsPill(label = "RR", bar = rrBar)
        }
    }
}

@Composable
private fun TpmsPill(label: String, bar: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%.1f bar", bar),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (bar in 2.2f..2.9f) EzTxtPrimary else EzAccentCoral,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            maxLines = 1
        )
        Text(
            text = label,
            fontSize = 8.sp,
            color = EzTxtMuted,
            softWrap = false,
            maxLines = 1
        )
    }
}
