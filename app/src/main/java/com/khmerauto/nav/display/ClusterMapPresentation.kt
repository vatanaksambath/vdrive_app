package com.khmerauto.nav.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.map.routing.ActiveNavigationState
import com.khmerauto.nav.map.routing.ManeuverType
import com.khmerauto.nav.map.routing.NavigationRouteEngine
import com.khmerauto.nav.ui.theme.*

/**
 * Secondary Display Presentation for Windscreen HUD and Digital Instrument Cluster.
 *
 * Runs natively on secondary automotive displays discovered via Android DisplayManager.
 * Renders high-contrast dark 3D navigation graphics, turn arrow, distance countdown,
 * and live speed directly in driver's line of sight on the Geely Galaxy M7.
 */
class ClusterMapPresentation(
    context: Context,
    display: Display,
    private val routeEngine: NavigationRouteEngine
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val composeView = ComposeView(context).apply {
            setContent {
                val navState by routeEngine.navState.collectAsState()
                ClusterHudContent(navState = navState)
            }
        }
        setContentView(composeView)
    }
}

/**
 * Reusable Compose layout for both physical Secondary Display Presentation
 * and Tablet In-App Simulated HUD Preview Window.
 */
@Composable
fun ClusterHudContent(
    navState: ActiveNavigationState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black // Deepest black for optical HUD projection transparency
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Next Maneuver Arrow & Distance Countdown
            Row(verticalAlignment = Alignment.CenterVertically) {
                val maneuver = navState.currentManeuver
                val turnIcon = when (maneuver?.maneuverType) {
                    ManeuverType.TURN_LEFT -> Icons.Default.TurnLeft
                    ManeuverType.TURN_RIGHT -> Icons.Default.TurnRight
                    ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                    ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                    ManeuverType.U_TURN -> Icons.Default.RotateLeft
                    ManeuverType.ROUNDABOUT -> Icons.Default.RotateRight
                    ManeuverType.ARRIVED -> Icons.Default.Place
                    else -> Icons.Default.Straight
                }

                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(GeelyAccentCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = turnIcon,
                        contentDescription = "Turn Maneuver",
                        tint = GeelyAccentCyan,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Text(
                        text = if (navState.isNavigating) "${maneuver?.distanceMeters ?: 350} m" else "--",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                    Text(
                        text = maneuver?.roadNameKm ?: "រង់ចាំគោលដៅ...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GeelyTextPrimary
                    )
                    Text(
                        text = maneuver?.roadNameEn ?: "Preah Norodom Blvd",
                        fontSize = 14.sp,
                        color = GeelyTextSecondary
                    )
                }
            }

            // Center: Live Vehicle Speed & Gear
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${navState.currentSpeedKmh.toInt()}",
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = GeelyAccentCyan
                )
                Text(
                    text = "KM/H",
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    color = GeelyTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right: ETA, Distance Remaining & Arrival Battery %
            Column(horizontalAlignment = Alignment.End) {
                if (navState.isNavigating) {
                    Text(
                        text = "ETA ${navState.etaString}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeelyEmeraldGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f km remaining", navState.remainingDistanceKm),
                        fontSize = 16.sp,
                        color = GeelyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Arrival Battery",
                            tint = GeelyAmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Arrival SoC: ${navState.estimatedArrivalBatteryPercent}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GeelyAmberGold
                        )
                    }
                } else {
                    Text(
                        text = "HUD READY",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeelyTextSecondary
                    )
                    Text(
                        text = "Geely Galaxy M7",
                        fontSize = 14.sp,
                        color = GeelyTextMuted
                    )
                }
            }
        }
    }
}
