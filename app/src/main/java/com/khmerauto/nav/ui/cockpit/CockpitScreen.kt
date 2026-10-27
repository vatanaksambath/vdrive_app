package com.khmerauto.nav.ui.cockpit

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.core.calendar.ChankitekCalendar
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.calendar.ThemeMode
import com.khmerauto.nav.core.i18n.AppLanguage
import com.khmerauto.nav.core.i18n.AppStrings
import com.khmerauto.nav.core.i18n.LanguageManager
import com.khmerauto.nav.core.i18n.LocalAppStrings
import com.khmerauto.nav.hal.*
import com.khmerauto.nav.ui.components.VehicleCanvasDiagram
import com.khmerauto.nav.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CockpitScreen(
    currentHal: VehicleHalContract,
    gapSummary: com.khmerauto.nav.telemetry.GapReconciliationSummary? = null,
    onDismissGapSummary: () -> Unit = {},
    onNavigateToCabin: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSpending: () -> Unit,
    onNavigateToVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetry by currentHal.telemetryState.collectAsState()
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    val currentThemeMode by SolarCalculator.themeMode.collectAsState()

    // Real-time digital clock
    var currentTimeString by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            delay(1000)
        }
    }

    val todayDate = remember { LocalDate.now() }
    val chankitek = remember(todayDate) { ChankitekCalendar.calculate(todayDate) }

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. Left Automotive Navigation Rail
        CockpitNavRail(
            currentScreen = "cockpit",
            currentLanguage = currentLang,
            currentThemeMode = currentThemeMode,
            onLanguageToggle = { LanguageManager.toggleLanguage() },
            onThemeToggle = {
                val next = when (currentThemeMode) {
                    ThemeMode.AUTO -> ThemeMode.DAY
                    ThemeMode.DAY -> ThemeMode.NIGHT
                    ThemeMode.NIGHT -> ThemeMode.AUTO
                }
                SolarCalculator.setThemeMode(next)
            },
            onNavigateToCockpit = { /* Already here */ },
            onNavigateToCabin = onNavigateToCabin,
            onNavigateToMap = onNavigateToMap,
            onNavigateToVoice = onNavigateToVoice,
            onNavigateToSpending = onNavigateToSpending,
            onNavigateToDiagnostics = onNavigateToDiagnostics
        )

        // 2. Main Cockpit Dashboard Surface
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Top Automotive Status Header
            CockpitHeader(
                telemetry = telemetry,
                currentTime = currentTimeString,
                gear = telemetry.gear
            )

            // "Two Numbers Moved Without You" Gap Reconciliation Card (if vehicle moved or charged while KMAutoNav was asleep)
            gapSummary?.let { summary ->
                com.khmerauto.nav.ui.spending.TwoNumbersMovedCard(
                    summary = summary,
                    onDismiss = onDismissGapSummary
                )
            }

            // Main Exploded Cockpit Content (Two responsive columns)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Geely Galaxy M7 3D/Canvas Visualizer & Powertrain Card
                VehiclePowertrainCard(
                    telemetry = telemetry,
                    modifier = Modifier.weight(1.1f).fillMaxHeight()
                )

                // Right Column: Clock, Weather, Buddhist Holy Day & Smart Destinations
                CockpitWidgetsCard(
                    chankitek = chankitek,
                    strings = strings,
                    onNavigateToMap = onNavigateToMap,
                    modifier = Modifier.weight(0.9f).fillMaxHeight()
                )
            }

            // 3. Persistent Quick Climate Dock (Bottom Dock)
            QuickClimateDock(
                hvac = telemetry.hvacState,
                onDriverTempChange = { coroutineScope.launch { currentHal.setTemperature(Zone.DRIVER, it) } },
                onPassengerTempChange = { coroutineScope.launch { currentHal.setTemperature(Zone.PASSENGER, it) } },
                onToggleAc = { coroutineScope.launch { currentHal.setAcMode(ac = !telemetry.hvacState.isAcOn) } },
                onToggleAuto = { coroutineScope.launch { currentHal.setAcMode(auto = !telemetry.hvacState.isAutoOn) } },
                onOpenCabinSheet = onNavigateToCabin
            )
        }
    }
}

// =========================================================================
// Left Navigation Rail
// =========================================================================

@Composable
fun CockpitNavRail(
    currentScreen: String,
    currentLanguage: AppLanguage,
    currentThemeMode: ThemeMode,
    onLanguageToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    onNavigateToCockpit: () -> Unit,
    onNavigateToCabin: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToSpending: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    Surface(
        modifier = Modifier.width(90.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(GeelyAccentCyan, GeelyEmeraldGreen))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "VD",
                        fontWeight = FontWeight.Bold,
                        color = GeelyNavyDark,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "VDrive",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            // Navigation Icons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavRailItem(
                    icon = Icons.Default.DirectionsCar,
                    label = "Cockpit",
                    isSelected = currentScreen == "cockpit",
                    onClick = onNavigateToCockpit
                )
                NavRailItem(
                    icon = Icons.Default.AcUnit,
                    label = "Cabin",
                    isSelected = currentScreen == "cabin",
                    onClick = onNavigateToCabin
                )
                NavRailItem(
                    icon = Icons.Default.Map,
                    label = "Map",
                    isSelected = currentScreen == "map",
                    onClick = onNavigateToMap
                )
                NavRailItem(
                    icon = Icons.Default.Mic,
                    label = "Voice",
                    isSelected = currentScreen == "voice",
                    onClick = onNavigateToVoice
                )
                NavRailItem(
                    icon = Icons.Default.MonetizationOn,
                    label = "Spending",
                    isSelected = currentScreen == "spending",
                    onClick = onNavigateToSpending
                )
                NavRailItem(
                    icon = Icons.Default.Build,
                    label = "VHAL",
                    isSelected = currentScreen == "diagnostics",
                    onClick = onNavigateToDiagnostics
                )
            }

            // Bottom Toggles: Language & Theme
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Language Switcher Button (ខ្មែរ / EN)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.clickable { onLanguageToggle() }
                ) {
                    Text(
                        text = if (currentLanguage == AppLanguage.KHMER) "ខ្មែរ" else "EN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Solar Day / Night / Auto Theme Toggle
                IconButton(
                    onClick = onThemeToggle,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = when (currentThemeMode) {
                            ThemeMode.DAY -> Icons.Default.WbSunny
                            ThemeMode.NIGHT -> Icons.Default.Nightlight
                            ThemeMode.AUTO -> Icons.Default.BrightnessAuto
                        },
                        contentDescription = "Theme Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NavRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =========================================================================
// Cockpit Top Header
// =========================================================================

@Composable
fun CockpitHeader(
    telemetry: VehicleTelemetryState,
    currentTime: String,
    gear: GearState
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Digital Speed & Gear
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${telemetry.speedKmh.toInt()}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "km/h",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(20.dp))

                // Transmission Gear Selector (P R N D)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GearState.values().forEach { g ->
                        val isCurrent = g == gear
                        Text(
                            text = g.name,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            color = if (isCurrent) {
                                if (g == GearState.P) GeelyCoralRed else GeelyEmeraldGreen
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }

            // Center Vehicle Model Banner
            Text(
                text = "GEELY GALAXY M7 • THOR PHEV",
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Right Connectivity & Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wi-Fi Bridge",
                    tint = GeelyEmeraldGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "GPS",
                    tint = GeelyAccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = currentTime,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// =========================================================================
// Vehicle Status & Powertrain Card (Left Column)
// =========================================================================

@Composable
fun VehiclePowertrainCard(
    telemetry: VehicleTelemetryState,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header Row: Battery & Usable kWh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.batteryPercent > 20) GeelyEmeraldGreen else GeelyCoralRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${strings.battery}: ${telemetry.batteryPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = String.format(Locale.US, "%.1f / %.1f kWh", telemetry.usableKwh, telemetry.maxCapacityKwh),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // SoC Progress Bar
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (telemetry.batteryPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (telemetry.batteryPercent > 20) GeelyAccentCyan else GeelyCoralRed,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            // Vehicle Top-Down Live Visualizer (Canvas)
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                VehicleCanvasDiagram(
                    cabinState = telemetry.cabinState,
                    tpmsState = telemetry.tpmsState,
                    isHeadlightsOn = telemetry.speedKmh > 0f || telemetry.gear == GearState.D
                )
            }

            // Powertrain Stats Row: EV Range, Fuel Range, Odometer
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RangeMetricTile(
                    label = strings.evRange,
                    value = "${telemetry.evRangeKm} km",
                    accentColor = GeelyAccentCyan
                )
                RangeMetricTile(
                    label = strings.fuelRange,
                    value = "${telemetry.fuelRangeKm} km",
                    accentColor = GeelyAmberGold
                )
                RangeMetricTile(
                    label = strings.odometer,
                    value = String.format(Locale.US, "%.0f km", telemetry.odometerKm),
                    accentColor = GeelyEmeraldGreen
                )
            }
        }
    }
}

@Composable
fun RangeMetricTile(
    label: String,
    value: String,
    accentColor: Color
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

// =========================================================================
// Widgets Card: Buddhist Holy Day, Weather, Destination (Right Column)
// =========================================================================

@Composable
fun CockpitWidgetsCard(
    chankitek: ChankitekCalendar.ChankitekDate,
    strings: AppStrings,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Buddhist Holy Day Card (ថ្ងៃសីល & Cambodian Chankitek Lunar Date)
            BuddhistHolyDayCard(chankitek = chankitek, strings = strings)

            // 2. Phnom Penh Weather Widget
            WeatherWidget()

            // 3. Quick Navigation Destination Tile
            QuickDestinationWidget(onNavigateToMap = onNavigateToMap)
        }
    }
}

@Composable
fun BuddhistHolyDayCard(
    chankitek: ChankitekCalendar.ChankitekDate,
    strings: AppStrings
) {
    val isHoly = chankitek.isBuddhistHolyDay

    // Pulsing Lotus glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "lotusGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isHoly) LotusHolyDayGold.copy(alpha = 0.15f * glowAlpha) else MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, if (isHoly) LotusHolyDayGold else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isHoly) {
                        Text(
                            text = "🪷 ${strings.holyDayBadge}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LotusHolyDayGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "ប្រតិទិនចន្ទគតិខ្មែរ (Chankitek)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chankitek.formattedKhmerFull,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isHoly) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(LotusHolyDayGold.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🪷", fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
fun WeatherWidget() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("រាជធានីភ្នំពេញ • Phnom Penh", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("34°C", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ពន្លឺថ្ងៃខ្លាំង / Sunny", fontSize = 12.sp, color = GeelyAmberGold)
                    }
                }
                Text("☀️", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniForecastDay("ចន្ទ", "34°", "☀️")
                MiniForecastDay("អង្គារ", "33°", "🌤️")
                MiniForecastDay("ពុធ", "32°", "🌦️")
                MiniForecastDay("ព្រហ", "33°", "☀️")
                MiniForecastDay("សុក្រ", "35°", "☀️")
            }
        }
    }
}

@Composable
fun MiniForecastDay(day: String, temp: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(icon, fontSize = 14.sp)
        Text(temp, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun QuickDestinationWidget(onNavigateToMap: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigateToMap() },
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GeelyAccentCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Navigation, contentDescription = "Navigate", tint = GeelyAccentCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ផ្សារទំនើបអ៊ីអន សែនសុខ • AEON Mall",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "ចម្ងាយ ៨.៤ គ.ម • រយៈពេល ១៨ នាទី",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =========================================================================
// Quick Climate Dock (Bottom Bar)
// =========================================================================

@Composable
fun QuickClimateDock(
    hvac: HvacState,
    onDriverTempChange: (Float) -> Unit,
    onPassengerTempChange: (Float) -> Unit,
    onToggleAc: () -> Unit,
    onToggleAuto: () -> Unit,
    onOpenCabinSheet: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Driver Temp Quick Adjust
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDriverTempChange(hvac.driverTempC - 0.5f) }, modifier = Modifier.size(44.dp)) {
                    Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GeelyAccentCyan)
                }
                Text(
                    text = String.format(Locale.US, "%.1f°", hvac.driverTempC),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onDriverTempChange(hvac.driverTempC + 0.5f) }, modifier = Modifier.size(44.dp)) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GeelyCoralRed)
                }
            }

            // Center Quick Mode Pills & Fan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleAc,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hvac.isAcOn) GeelyAccentCyan else MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "A/C",
                        fontWeight = FontWeight.Bold,
                        color = if (hvac.isAcOn) GeelyNavyDark else MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onToggleAuto,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hvac.isAutoOn) GeelyEmeraldGreen else MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "AUTO",
                        fontWeight = FontWeight.Bold,
                        color = if (hvac.isAutoOn) GeelyNavyDark else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Fan: ${hvac.fanSpeed}/7",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Passenger Temp Quick Adjust & Expand Drawer Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPassengerTempChange(hvac.passengerTempC - 0.5f) }, modifier = Modifier.size(44.dp)) {
                    Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GeelyAccentCyan)
                }
                Text(
                    text = String.format(Locale.US, "%.1f°", hvac.passengerTempC),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onPassengerTempChange(hvac.passengerTempC + 0.5f) }, modifier = Modifier.size(44.dp)) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GeelyCoralRed)
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Full Cabin Control Sheet Trigger
                Button(
                    onClick = onOpenCabinSheet,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Full Cabin", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cabin Sheet", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
