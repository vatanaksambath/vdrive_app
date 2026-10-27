package com.khmerauto.nav.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.hal.CabinState
import com.khmerauto.nav.hal.TpmsState
import com.khmerauto.nav.ui.theme.*
import java.util.Locale

/**
 * Custom Compose Canvas rendering the 2026 Geely Galaxy M7 vehicle chassis.
 *
 * Displays:
 * - Dynamic 4-window opening levels (FL, FR, RL, RR)
 * - Panoramic sunroof & electric roller shade positions
 * - Tailgate open/closed status with glowing warning animation
 * - 4-wheel TPMS tire pressure (Bar) and temperature (°C)
 * - Headlights / Taillights state
 */
@Composable
fun VehicleCanvasDiagram(
    cabinState: CabinState,
    tpmsState: TpmsState,
    isHeadlightsOn: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Tailgate pulse animation when open
    val infiniteTransition = rememberInfiniteTransition(label = "tailgatePulse")
    val tailgateAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tailgateGlow"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(260.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val carWidth = 140.dp.toPx()
        val carLength = 230.dp.toPx()
        val carLeft = (canvasWidth - carWidth) / 2f
        val carTop = (canvasHeight - carLength) / 2f

        // 1. Draw Wheels (4 Wheels on the corners)
        val wheelWidth = 18.dp.toPx()
        val wheelLength = 40.dp.toPx()
        val wheelOffsetInsetX = 8.dp.toPx()
        val wheelOffsetFrontY = carTop + 36.dp.toPx()
        val wheelOffsetRearY = carTop + carLength - 64.dp.toPx()

        // Front Left Wheel
        drawWheelWithTpms(
            x = carLeft - wheelWidth + wheelOffsetInsetX,
            y = wheelOffsetFrontY,
            w = wheelWidth,
            h = wheelLength,
            bar = tpmsState.flBar,
            temp = tpmsState.flTempC,
            labelPositionLeft = true
        )

        // Front Right Wheel
        drawWheelWithTpms(
            x = carLeft + carWidth - wheelOffsetInsetX,
            y = wheelOffsetFrontY,
            w = wheelWidth,
            h = wheelLength,
            bar = tpmsState.frBar,
            temp = tpmsState.frTempC,
            labelPositionLeft = false
        )

        // Rear Left Wheel
        drawWheelWithTpms(
            x = carLeft - wheelWidth + wheelOffsetInsetX,
            y = wheelOffsetRearY,
            w = wheelWidth,
            h = wheelLength,
            bar = tpmsState.rlBar,
            temp = tpmsState.rlTempC,
            labelPositionLeft = true
        )

        // Rear Right Wheel
        drawWheelWithTpms(
            x = carLeft + carWidth - wheelOffsetInsetX,
            y = wheelOffsetRearY,
            w = wheelWidth,
            h = wheelLength,
            bar = tpmsState.rrBar,
            temp = tpmsState.rrTempC,
            labelPositionLeft = false
        )

        // 2. Draw Geely Galaxy M7 Body Outline
        val bodyPath = Path().apply {
            // Front Bumper curved
            moveTo(carLeft + 24.dp.toPx(), carTop)
            quadraticBezierTo(carLeft + carWidth / 2f, carTop - 8.dp.toPx(), carLeft + carWidth - 24.dp.toPx(), carTop)
            // Front Right Quarter
            lineTo(carLeft + carWidth, carTop + 28.dp.toPx())
            // Right Side
            lineTo(carLeft + carWidth, carTop + carLength - 30.dp.toPx())
            // Rear Right Quarter
            quadraticBezierTo(carLeft + carWidth, carTop + carLength, carLeft + carWidth - 20.dp.toPx(), carTop + carLength)
            // Rear Tailgate
            lineTo(carLeft + 20.dp.toPx(), carTop + carLength)
            // Rear Left Quarter
            quadraticBezierTo(carLeft, carTop + carLength, carLeft, carTop + carLength - 30.dp.toPx())
            // Left Side
            lineTo(carLeft, carTop + 28.dp.toPx())
            // Front Left Quarter
            close()
        }

        // Body Background & Outline
        drawPath(path = bodyPath, color = GeelySurfaceDark)
        drawPath(path = bodyPath, color = GeelyCardBorder, style = Stroke(width = 2.dp.toPx()))

        // 3. Headlights (Geely Cyan glow)
        if (isHeadlightsOn) {
            drawCircle(
                color = GeelyAccentCyan,
                radius = 7.dp.toPx(),
                center = Offset(carLeft + 18.dp.toPx(), carTop + 6.dp.toPx())
            )
            drawCircle(
                color = GeelyAccentCyan,
                radius = 7.dp.toPx(),
                center = Offset(carLeft + carWidth - 18.dp.toPx(), carTop + 6.dp.toPx())
            )
        }

        // 4. Windshield (Front & Rear)
        val frontGlassPath = Path().apply {
            moveTo(carLeft + 20.dp.toPx(), carTop + 42.dp.toPx())
            lineTo(carLeft + carWidth - 20.dp.toPx(), carTop + 42.dp.toPx())
            lineTo(carLeft + carWidth - 26.dp.toPx(), carTop + 68.dp.toPx())
            lineTo(carLeft + 26.dp.toPx(), carTop + 68.dp.toPx())
            close()
        }
        drawPath(path = frontGlassPath, color = GeelyCardDark)
        drawPath(path = frontGlassPath, color = GeelyAccentCyan.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

        // 5. Panoramic Sunroof & Electric Shade
        val sunroofLeft = carLeft + 28.dp.toPx()
        val sunroofTop = carTop + 76.dp.toPx()
        val sunroofWidth = carWidth - 56.dp.toPx()
        val sunroofLength = 80.dp.toPx()

        drawRoundRect(
            color = GeelyNavyDark,
            topLeft = Offset(sunroofLeft, sunroofTop),
            size = Size(sunroofWidth, sunroofLength),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Sunroof glass position indicator
        val glassOpenHeight = (cabinState.sunroofPercent / 100f) * sunroofLength
        if (glassOpenHeight > 0) {
            drawRoundRect(
                color = GeelyAccentCyan.copy(alpha = 0.6f),
                topLeft = Offset(sunroofLeft, sunroofTop),
                size = Size(sunroofWidth, glassOpenHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }

        drawRoundRect(
            color = GeelyCardBorder,
            topLeft = Offset(sunroofLeft, sunroofTop),
            size = Size(sunroofWidth, sunroofLength),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // 6. Side Windows Fill Level (FL, FR, RL, RR)
        val windowSideWidth = 6.dp.toPx()
        val frontWindowLength = 40.dp.toPx()
        val rearWindowLength = 36.dp.toPx()
        val windowFrontY = carTop + 68.dp.toPx()
        val windowRearY = carTop + 116.dp.toPx()

        // Front Left Window
        drawWindowBar(carLeft + 4.dp.toPx(), windowFrontY, windowSideWidth, frontWindowLength, cabinState.windowFlPercent)
        // Front Right Window
        drawWindowBar(carLeft + carWidth - 10.dp.toPx(), windowFrontY, windowSideWidth, frontWindowLength, cabinState.windowFrPercent)
        // Rear Left Window
        drawWindowBar(carLeft + 4.dp.toPx(), windowRearY, windowSideWidth, rearWindowLength, cabinState.windowRlPercent)
        // Rear Right Window
        drawWindowBar(carLeft + carWidth - 10.dp.toPx(), windowRearY, windowSideWidth, rearWindowLength, cabinState.windowRrPercent)

        // 7. Powered Tailgate Visual
        if (cabinState.isTailgateOpen) {
            // Draw open hatch angled back with glowing gold/coral stroke
            val hatchPath = Path().apply {
                moveTo(carLeft + 16.dp.toPx(), carTop + carLength - 8.dp.toPx())
                lineTo(carLeft + carWidth - 16.dp.toPx(), carTop + carLength - 8.dp.toPx())
                lineTo(carLeft + carWidth - 8.dp.toPx(), carTop + carLength + 20.dp.toPx())
                lineTo(carLeft + 8.dp.toPx(), carTop + carLength + 20.dp.toPx())
                close()
            }
            drawPath(path = hatchPath, color = GeelyCoralRed.copy(alpha = 0.35f * tailgateAlpha))
            drawPath(path = hatchPath, color = GeelyCoralRed.copy(alpha = tailgateAlpha), style = Stroke(width = 2.dp.toPx()))
        } else {
            // Closed Tailgate Red Bar (Geely full-width rear light bar)
            drawLine(
                color = GeelyCoralRed,
                start = Offset(carLeft + 16.dp.toPx(), carTop + carLength - 4.dp.toPx()),
                end = Offset(carLeft + carWidth - 16.dp.toPx(), carTop + carLength - 4.dp.toPx()),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}

private fun DrawScope.drawWindowBar(x: Float, y: Float, w: Float, h: Float, openPercent: Int) {
    // Window frame
    drawRect(color = GeelyNavyDark, topLeft = Offset(x, y), size = Size(w, h))

    // Closed portion (tinted glass)
    val closedFraction = (100 - openPercent) / 100f
    val closedHeight = h * closedFraction
    if (closedHeight > 0) {
        drawRect(
            color = GeelyAccentCyan.copy(alpha = 0.7f),
            topLeft = Offset(x, y + (h - closedHeight)),
            size = Size(w, closedHeight)
        )
    }
    drawRect(color = GeelyCardBorder, topLeft = Offset(x, y), size = Size(w, h), style = Stroke(width = 1f))
}

private fun DrawScope.drawWheelWithTpms(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    bar: Float,
    temp: Int,
    labelPositionLeft: Boolean
) {
    val isNormal = bar in 2.2f..2.6f
    val wheelColor = if (isNormal) GeelyCardDark else GeelyCoralRed.copy(alpha = 0.5f)
    val strokeColor = if (isNormal) GeelyEmeraldGreen else GeelyCoralRed

    // Wheel rectangle
    drawRoundRect(
        color = wheelColor,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
}
