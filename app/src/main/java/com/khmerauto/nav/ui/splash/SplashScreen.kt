package com.khmerauto.nav.ui.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Sleek Automotive Welcome / Startup Splash Screen for Geely Galaxy M7.
 *
 * Visual Highlights:
 * 1. Deep titanium night gradient with glowing cyan/emerald radial energy pulse.
 * 2. Animated metallic "VD" emblem with smooth spring scale and glow ring.
 * 3. Letter-spaced "VDrive" branding + "ប្រព័ន្ធបញ្ជាឆ្លាតវៃ • Intelligent Cockpit" greeting.
 * 4. Micro-animated diagnostic checklist (VHAL, Map, Voice, Telemetry).
 * 5. Horizontal high-tech energy loading beam.
 * 6. Ergonomic tap-to-skip so the driver can access the cockpit immediately without delay.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation Controllers
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }

    // Step sequence for diagnostic checklist
    var activeStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(300)
        activeStep = 1 // VHAL connected
        delay(400)
        activeStep = 2 // Offline Map ready
        delay(450)
        activeStep = 3 // Voice Engine loaded
        delay(450)
        activeStep = 4 // Telemetry synced
        delay(600)
        onSplashComplete()
    }

    // Continuous breathing glow for automotive atmosphere
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val energyProgress by animateFloatAsState(
        targetValue = when (activeStep) {
            0 -> 0.1f
            1 -> 0.35f
            2 -> 0.65f
            3 -> 0.88f
            else -> 1.0f
        },
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "energyProgress"
    )

    // Tap anywhere to skip immediately for safety / instant cockpit access
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F1E36),
                        Color(0xFF090F1C),
                        Color(0xFF04070D)
                    ),
                    center = Offset(Float.POSITIVE_INFINITY / 2, Float.POSITIVE_INFINITY / 2),
                    radius = 1600f
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onSplashComplete()
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient energy grid lines
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.12f)) {
            val step = 80.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = GeelyAccentCyan,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        ) {
            // 1. Animated Metallic Logo Badge with Glowing Aura
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outer Radial Glow Pulse
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GeelyAccentCyan.copy(alpha = pulseGlow * 0.45f),
                                    GeelyEmeraldGreen.copy(alpha = pulseGlow * 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Main Metallic Badge
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = GeelySurfaceDark,
                    border = BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            listOf(
                                GeelyAccentCyan,
                                GeelyEmeraldGreen,
                                GeelyAccentCyan.copy(alpha = 0.3f)
                            )
                        )
                    ),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .size(92.dp)
                        .scale(if (transitionState.targetState) 1f else 0.7f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        GeelyAccentCyan.copy(alpha = 0.95f),
                                        GeelyEmeraldGreen.copy(alpha = 0.90f)
                                    )
                                )
                            )
                    ) {
                        Text(
                            text = "VD",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 38.sp,
                            letterSpacing = 1.sp,
                            color = GeelyNavyDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. High-Tech Brand Title
            Text(
                text = "V D R I V E",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 8.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Khmer & English Welcome Tagline
            Text(
                text = "ប្រព័ន្ធបញ្ជាឆ្លាតវៃ • Intelligent Cockpit",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GeelyAccentCyan,
                letterSpacing = 1.sp
            )

            Text(
                text = "Geely Galaxy M7 • Thor PHEV Edition",
                fontSize = 11.sp,
                color = GeelyTextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Horizontal Energy Loading Beam
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(energyProgress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(GeelyAccentCyan, GeelyEmeraldGreen)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Sequential Automotive Diagnostic System Checklist
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BootCheckItem(
                    label = "ECARX VHAL",
                    isReady = activeStep >= 1
                )
                BootCheckItem(
                    label = "Offline Map",
                    isReady = activeStep >= 2
                )
                BootCheckItem(
                    label = "Khmer Voice",
                    isReady = activeStep >= 3
                )
                BootCheckItem(
                    label = "Thor Telemetry",
                    isReady = activeStep >= 4
                )
            }
        }

        // Tap to skip hint at bottom
        Text(
            text = "ចុចអេក្រង់ដើម្បីរំលង / Tap screen to start immediately",
            fontSize = 11.sp,
            color = GeelyTextMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun BootCheckItem(
    label: String,
    isReady: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.alpha(if (isReady) 1.0f else 0.35f)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isReady) GeelyEmeraldGreen else Color.Gray,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isReady) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isReady) GeelyTextPrimary else GeelyTextMuted
        )
    }
}
