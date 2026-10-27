package com.khmerauto.nav.ui.cockpit

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.core.calendar.ChankitekCalendar
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.calendar.ThemeMode
import com.khmerauto.nav.core.i18n.AppLanguage
import com.khmerauto.nav.core.i18n.LanguageManager
import com.khmerauto.nav.core.i18n.LocalAppStrings
import com.khmerauto.nav.hal.*
import com.khmerauto.nav.map.CambodiaOfflineMapView
import com.khmerauto.nav.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Design tokens (EZNav dark palette) ────────────────────────────────────
private val BgDeep        = Color(0xFF0D0B1A)
private val CardBg        = Color(0xFF16132A).copy(alpha = 0.93f)
private val CardBgLight   = Color(0xFF1E1B35).copy(alpha = 0.95f)
private val BorderSubtle  = Color(0xFFFFFFFF).copy(alpha = 0.07f)
private val BorderAccent  = Color(0xFF00E5FF).copy(alpha = 0.25f)
private val TxtPrimary    = Color(0xFFF4F2FF)
private val TxtSecondary  = Color(0xFF8B87A8)
private val TxtMuted      = Color(0xFF4D4A66)
private val AccentCyan    = Color(0xFF00E5FF)
private val AccentEmerald = Color(0xFF00E676)
private val AccentAmber   = Color(0xFFFFB300)
private val AccentCoral   = Color(0xFFFF5252)
private val AccentPurple  = Color(0xFF8B5CF6)

// ─── Neon glow helper ────────────────────────────────────────────────────────
fun Modifier.ezGlow(color: Color, radius: Dp = 8.dp): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(radius.toPx(), 0f, 0f, color.copy(0.55f).toArgb())
            }
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, 14.dp.toPx(), 14.dp.toPx(), paint)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Main Cockpit Screen
// ═══════════════════════════════════════════════════════════════════════════
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
    val coroutineScope  = rememberCoroutineScope()
    val telemetry       by currentHal.telemetryState.collectAsState()
    val strings         = LocalAppStrings.current
    val currentLang     by LanguageManager.currentLanguage.collectAsState()
    val themeMode       by SolarCalculator.themeMode.collectAsState()

    // Clock
    var clock by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    }

    val today     = remember { LocalDate.now() }
    val chankitek = remember(today) { ChankitekCalendar.calculate(today) }

    // Panel visibility states
    var leftPanelVisible  by remember { mutableStateOf(true) }
    var rightPanelVisible by remember { mutableStateOf(true) }
    var isFullMap         by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(BgDeep)) {

        // ── Layer 0 : Full-screen live map ──────────────────────────────────
        CambodiaOfflineMapView(
            vehicleLat          = telemetry.latitude,
            vehicleLng          = telemetry.longitude,
            vehicleHeading      = telemetry.headingDegrees,
            vehicleSpeedKmh     = telemetry.speedKmh,
            bottomDockPaddingDp = 0,
            modifier            = Modifier.fillMaxSize()
        )

        // ── Layer 1 : Top control strip ─────────────────────────────────────
        AnimatedVisibility(
            visible  = !isFullMap,
            enter    = slideInVertically(tween(380)) { -it } + fadeIn(tween(380)),
            exit     = slideOutVertically(tween(320)) { -it } + fadeOut(tween(320)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            EzTopStrip(
                currentLang      = currentLang,
                themeMode        = themeMode,
                leftPanelVisible = leftPanelVisible,
                onToggleLeft     = { leftPanelVisible = !leftPanelVisible },
                onLanguageToggle = { LanguageManager.toggleLanguage() },
                onThemeToggle    = {
                    SolarCalculator.setThemeMode(
                        when (themeMode) {
                            ThemeMode.AUTO  -> ThemeMode.DAY
                            ThemeMode.DAY   -> ThemeMode.NIGHT
                            ThemeMode.NIGHT -> ThemeMode.AUTO
                        }
                    )
                }
            )
        }

        // ── Layer 2 : Left floating panel (search + destinations) ───────────
        AnimatedVisibility(
            visible  = leftPanelVisible && !isFullMap,
            enter    = slideInHorizontally(tween(380)) { -it } + fadeIn(tween(380)),
            exit     = slideOutHorizontally(tween(320)) { -it } + fadeOut(tween(320)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 56.dp, bottom = 0.dp)
        ) {
            EzLeftPanel(
                strings    = strings,
                telemetry  = telemetry,
                onNavigate = onNavigateToMap
            )
        }

        // ── Layer 3 : Right floating dashboard card ─────────────────────────
        AnimatedVisibility(
            visible  = rightPanelVisible && !isFullMap,
            enter    = slideInHorizontally(tween(380)) { it } + fadeIn(tween(380)),
            exit     = slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp)
        ) {
            EzRightDashboard(
                clock     = clock,
                telemetry = telemetry,
                chankitek = chankitek,
                strings   = strings,
                onNavigateToSpending = onNavigateToSpending,
                onNavigateToCabin    = onNavigateToCabin
            )
        }

        // ── Layer 4 : Floating map control buttons ───────────────────────────
        // AC button — top-right of map area
        AnimatedVisibility(
            visible  = !isFullMap,
            enter    = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 338.dp, end = 16.dp)
        ) {
            EzMapPill(
                label   = "AC",
                isOn    = telemetry.hvacState.isAcOn,
                onClick = { coroutineScope.launch { currentHal.setAcMode(ac = !telemetry.hvacState.isAcOn) } }
            )
        }

        // Fullscreen toggle & nav buttons — bottom-right of map
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EzMapIconBtn(
                icon    = if (isFullMap) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                onClick = {
                    isFullMap         = !isFullMap
                    if (isFullMap) { leftPanelVisible = false; rightPanelVisible = false }
                    else           { leftPanelVisible = true;  rightPanelVisible = true  }
                }
            )
            EzMapIconBtn(icon = Icons.Default.Add,       onClick = {})
            EzMapIconBtn(icon = Icons.Default.Remove,    onClick = {})
        }

        // Cabin shortcut — bottom-left
        AnimatedVisibility(
            visible  = !isFullMap,
            enter    = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 18.dp)
        ) {
            EzCabinChip(onClick = onNavigateToCabin)
        }

        // ── Layer 5 : Voice microphone FAB ───────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .ezGlow(AccentPurple, 10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(AccentPurple.copy(0.35f), CardBg))
                    )
                    .border(1.dp, AccentPurple.copy(0.6f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToVoice() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "Voice",
                    tint               = TxtPrimary,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Top Strip (collapse arrow + media + lang + theme)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzTopStrip(
    currentLang      : AppLanguage,
    themeMode        : ThemeMode,
    leftPanelVisible : Boolean,
    onToggleLeft     : () -> Unit,
    onLanguageToggle : () -> Unit,
    onThemeToggle    : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Collapse chevron
        EzStripBtn(
            icon  = if (leftPanelVisible) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
            onClick = onToggleLeft
        )

        // Media controls (design only — tapping voice navigates)
        EzStripBtn(icon = Icons.Default.MusicNote, onClick = {})
        EzStripBtn(icon = Icons.Default.SkipPrevious, onClick = {})
        EzStripBtn(icon = Icons.Default.PlayArrow, onClick = {})
        EzStripBtn(icon = Icons.Default.SkipNext, onClick = {})

        Spacer(Modifier.weight(1f))

        // Lang pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onLanguageToggle() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text       = if (currentLang == AppLanguage.KHMER) "ខ្មែរ" else "EN",
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = TxtPrimary
            )
        }

        // Theme toggle
        EzStripBtn(
            icon = when (themeMode) {
                ThemeMode.DAY   -> Icons.Default.WbSunny
                ThemeMode.NIGHT -> Icons.Default.Nightlight
                ThemeMode.AUTO  -> Icons.Default.BrightnessAuto
            },
            onClick = onThemeToggle
        )
    }
}

@Composable
fun EzStripBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = TxtSecondary, modifier = Modifier.size(17.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Left Floating Panel (search + quick destinations)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzLeftPanel(
    strings   : com.khmerauto.nav.core.i18n.AppStrings,
    telemetry : VehicleTelemetryState,
    onNavigate: () -> Unit
) {
    Column(
        modifier            = Modifier.width(220.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onNavigate() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TxtMuted, modifier = Modifier.size(16.dp))
            Text("${strings.map}...", fontSize = 13.sp, color = TxtMuted)
        }

        // Quick destinations
        val destinations = listOf(
            Pair(Icons.Default.Home,      "ផ្ទះ • Home / ការងារ • Office"),
            Pair(Icons.Default.Flight,    "អាកាសយានដ្ឋានភ្នំពេញ"),
            Pair(Icons.Default.Store,     "ផ្សារធំ • Central Market"),
            Pair(Icons.Default.ShoppingCart, "ផ្សារ អ៊ីអន • AEON Mall"),
            Pair(Icons.Default.LocalGasStation, "ស្ថានីយ EV • Charger")
        )
        destinations.forEach { (icon, label) ->
            EzDestinationRow(icon = icon, label = label, onClick = onNavigate)
        }

        // Speed indicator pill at bottom of left panel
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(AccentEmerald))
            Text(
                text       = "${telemetry.speedKmh.toInt()} km/h",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = TxtPrimary
            )
            Spacer(Modifier.weight(1f))
            // Gear
            GearState.values().forEach { g ->
                Text(
                    text       = g.name,
                    fontSize   = 12.sp,
                    fontWeight = if (g == telemetry.gear) FontWeight.Black else FontWeight.Normal,
                    color      = when {
                        g != telemetry.gear   -> TxtMuted
                        g == GearState.P      -> AccentCoral
                        g == GearState.R      -> AccentAmber
                        else                  -> AccentEmerald
                    }
                )
            }
        }
    }
}

@Composable
fun EzDestinationRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TxtSecondary, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 12.sp, color = TxtPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Right Floating Dashboard (clock + weather + battery/range/odo + trips)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzRightDashboard(
    clock       : String,
    telemetry   : VehicleTelemetryState,
    chankitek   : ChankitekCalendar.ChankitekDate,
    strings     : com.khmerauto.nav.core.i18n.AppStrings,
    onNavigateToSpending: () -> Unit,
    onNavigateToCabin   : () -> Unit
) {
    Column(
        modifier = Modifier.width(310.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Clock + Weather widget ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBgLight)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text       = clock,
                            fontSize   = 38.sp,
                            fontWeight = FontWeight.Black,
                            color      = TxtPrimary,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1).sp
                        )
                        // VDrive brand badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentCyan.copy(0.15f))
                                .border(0.5.dp, AccentCyan.copy(0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("VDrive", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = chankitek.formattedKhmerFull,
                        fontSize = 11.sp,
                        color = TxtSecondary
                    )
                }

                // Weather
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(0.5.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("ព្នំពេញ", fontSize = 9.sp, color = TxtMuted)
                    Text("☀️", fontSize = 26.sp)
                    Text("ពន្លឺថ្ងៃ 32°", fontSize = 9.sp, color = AccentAmber, fontWeight = FontWeight.SemiBold)
                    Text(
                        text     = "34°",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color    = TxtPrimary
                    )
                }
            }
        }

        // ── Battery / Range / Odometer row ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EzMetricCard(
                label     = "BATTERY",
                value     = "${telemetry.batteryPercent}",
                unit      = "%",
                accent    = if (telemetry.batteryPercent > 20) AccentEmerald else AccentCoral,
                showBar   = true,
                barFrac   = telemetry.batteryPercent / 100f,
                modifier  = Modifier.weight(1.2f)
            )
            EzMetricCard(
                label    = "RANGE",
                value    = "${telemetry.evRangeKm}",
                unit     = "km",
                accent   = AccentCyan,
                modifier = Modifier.weight(1f)
            )
            EzMetricCard(
                label    = "ODOMETER",
                value    = "${telemetry.odometerKm.toLong()}",
                unit     = "km",
                accent   = TxtSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Trips section ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trips >", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TxtPrimary,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onNavigateToSpending() })
                Text(
                    text = "Avg 21.0 kWh/100km",
                    fontSize = 9.sp,
                    color = TxtMuted
                )
            }

            EzTripRow(dot = AccentEmerald, label = "Live",  km = "0 km",  kwh = "0.0 kWh", isLive = true)
            EzTripRow(dot = TxtMuted,      label = "Today", km = "10 km", kwh = "1.7 kWh", isLive = false)
        }

        // ── Maintenance notice ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onNavigateToCabin() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(AccentAmber))
                Text(
                    "Tyre rotation in 8 717 km",
                    fontSize = 11.sp,
                    color    = TxtSecondary
                )
            }
            Text("Maintenance >", fontSize = 10.sp, color = TxtMuted)
        }

        // ── Fuel range (PHEV specific) ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
            Text("Fuel range  ${telemetry.fuelRangeKm} km", fontSize = 11.sp, color = TxtSecondary)
            Spacer(Modifier.weight(1f))
            Text("PHEV  •  Thor 1.5T", fontSize = 10.sp, color = TxtMuted)
        }
    }
}

@Composable
fun EzMetricCard(
    label   : String,
    value   : String,
    unit    : String,
    accent  : Color,
    showBar : Boolean = false,
    barFrac : Float = 0f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 8.sp, color = TxtMuted, letterSpacing = 0.8.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text       = value,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Black,
                color      = TxtPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.5).sp
            )
            Text(
                text     = unit,
                fontSize = 10.sp,
                color    = TxtSecondary,
                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
            )
        }
        if (showBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barFrac.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(listOf(AccentCyan, AccentEmerald))
                        )
                )
            }
        }
    }
}

@Composable
fun EzTripRow(dot: Color, label: String, km: String, kwh: String, isLive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Text(label, fontSize = 12.sp, color = TxtSecondary)
            Text(km,    fontSize = 12.sp, color = TxtPrimary,   fontWeight = FontWeight.SemiBold)
            Text(kwh,   fontSize = 11.sp, color = TxtMuted)
        }
        if (isLive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentCoral.copy(0.18f))
                    .border(0.5.dp, AccentCoral.copy(0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("End trip", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCoral)
            }
        } else {
            Text("17.4 kWh/100km", fontSize = 9.sp, color = TxtMuted)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Map floating controls
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun EzMapPill(label: String, isOn: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isOn) AccentCyan.copy(0.2f) else CardBg)
            .border(0.5.dp, if (isOn) AccentCyan.copy(0.6f) else BorderSubtle, RoundedCornerShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text       = label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            color      = if (isOn) AccentCyan else TxtSecondary
        )
    }
}

@Composable
fun EzMapIconBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(CardBg)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(9.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = TxtSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun EzCabinChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.AcUnit, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
        Text("Cabin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
    }
}
