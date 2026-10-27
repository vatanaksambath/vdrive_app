package com.khmerauto.nav.ui.cockpit

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
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
import com.khmerauto.nav.telemetry.GapReconciliationEngine
import com.khmerauto.nav.telemetry.GapReconciliationSummary
import com.khmerauto.nav.ui.components.*
import com.khmerauto.nav.voice.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class CockpitOverlay {
    NONE,
    CABIN,
    VOICE,
    SPENDING
}

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
    gapSummary: GapReconciliationSummary? = null,
    voiceService: VoiceAssistantService? = null,
    navController: NavigationController? = null,
    gapEngine: GapReconciliationEngine? = null,
    initialOverlay: CockpitOverlay = CockpitOverlay.NONE,
    onDismissGapSummary: () -> Unit = {},
    onNavigateToCabin: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToSpending: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context         = LocalContext.current
    val coroutineScope  = rememberCoroutineScope()
    val telemetry       by currentHal.telemetryState.collectAsState()
    val strings         = LocalAppStrings.current
    val currentLang     by LanguageManager.currentLanguage.collectAsState()
    val themeMode       by SolarCalculator.themeMode.collectAsState()

    // Active overlay modal state (NONE, CABIN, VOICE, SPENDING)
    var activeOverlay by remember { mutableStateOf(initialOverlay) }

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

    Box(modifier = modifier.fillMaxSize().background(EzBgDeep)) {

        // ── Layer 0 : Full-screen live map (Always present in background) ───
        CambodiaOfflineMapView(
            vehicleLat          = telemetry.latitude,
            vehicleLng          = telemetry.longitude,
            vehicleHeading      = telemetry.headingDegrees,
            vehicleSpeedKmh     = telemetry.speedKmh,
            isNavigationActive  = false,
            bottomDockPaddingDp = 0,
            modifier            = Modifier.fillMaxSize()
        )

        // ── Layer 1 : Top control strip & navigation tabs ───────────────────
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
                activeOverlay    = activeOverlay,
                onSelectOverlay  = { overlay ->
                    activeOverlay = if (activeOverlay == overlay) CockpitOverlay.NONE else overlay
                },
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

        // ── Layer 2 : Left content area ─────────────────────────────────────
        // Depending on activeOverlay:
        // - When NONE: Left search & 5 destinations panel
        // - When CABIN: Left Cabin Floating Suite (matching EZNav layout)
        AnimatedVisibility(
            visible  = leftPanelVisible && !isFullMap && activeOverlay != CockpitOverlay.VOICE && activeOverlay != CockpitOverlay.SPENDING,
            enter    = slideInHorizontally(tween(380)) { -it } + fadeIn(tween(380)),
            exit     = slideOutHorizontally(tween(320)) { -it } + fadeOut(tween(320)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 56.dp, bottom = 12.dp)
        ) {
            when (activeOverlay) {
                CockpitOverlay.CABIN -> {
                    EzCabinSuite(
                        currentHal = currentHal,
                        telemetry  = telemetry,
                        strings    = strings,
                        onClose    = { activeOverlay = CockpitOverlay.NONE }
                    )
                }
                else -> {
                    EzLeftPanel(
                        strings    = strings,
                        telemetry  = telemetry,
                        onNavigate = onNavigateToMap
                    )
                }
            }
        }

        // ── Layer 3 : Right floating dashboard card ─────────────────────────
        AnimatedVisibility(
            visible  = rightPanelVisible && !isFullMap && activeOverlay != CockpitOverlay.VOICE && activeOverlay != CockpitOverlay.SPENDING,
            enter    = slideInHorizontally(tween(380)) { it } + fadeIn(tween(380)),
            exit     = slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 56.dp)
        ) {
            EzRightDashboard(
                clock     = clock,
                telemetry = telemetry,
                chankitek = chankitek,
                strings   = strings,
                onNavigateToSpending = { activeOverlay = CockpitOverlay.SPENDING },
                onNavigateToCabin    = { activeOverlay = CockpitOverlay.CABIN }
            )
        }

        // ── Layer 4 : Modal Overlays (Voice / Spending) ─────────────────────
        AnimatedVisibility(
            visible  = activeOverlay == CockpitOverlay.VOICE,
            enter    = fadeIn(tween(300)) + scaleIn(initialScale = 0.95f),
            exit     = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 50.dp, bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            EzVoiceModal(
                currentHal    = currentHal,
                voiceService  = voiceService,
                navController = navController,
                onClose       = { activeOverlay = CockpitOverlay.NONE }
            )
        }

        AnimatedVisibility(
            visible  = activeOverlay == CockpitOverlay.SPENDING,
            enter    = fadeIn(tween(300)) + scaleIn(initialScale = 0.95f),
            exit     = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 50.dp, bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            EzSpendingModal(
                currentHal = currentHal,
                gapEngine  = gapEngine,
                gapSummary = gapSummary,
                onClose    = { activeOverlay = CockpitOverlay.NONE }
            )
        }

        // ── Layer 5 : Floating map control buttons ───────────────────────────
        // AC toggle button — on the map
        AnimatedVisibility(
            visible  = !isFullMap && activeOverlay == CockpitOverlay.NONE,
            enter    = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 380.dp, end = 16.dp)
        ) {
            EzMapPill(
                label   = "AC",
                isOn    = telemetry.hvacState.isAcOn,
                onClick = { coroutineScope.launch { currentHal.setAcMode(ac = !telemetry.hvacState.isAcOn) } }
            )
        }

        // Fullscreen toggle & zoom buttons — bottom-right of map
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
            EzMapIconBtn(icon = Icons.Default.Add,    onClick = { navController?.zoomIn() })
            EzMapIconBtn(icon = Icons.Default.Remove, onClick = { navController?.zoomOut() })
        }

        // Cabin shortcut — bottom-left
        AnimatedVisibility(
            visible  = !isFullMap && activeOverlay == CockpitOverlay.NONE,
            enter    = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 18.dp)
        ) {
            EzCabinChip(onClick = { activeOverlay = CockpitOverlay.CABIN })
        }

        // Voice microphone FAB — bottom-center
        AnimatedVisibility(
            visible  = !isFullMap && activeOverlay == CockpitOverlay.NONE,
            enter    = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .ezGlow(EzAccentPurple, 10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(EzAccentPurple.copy(0.4f), EzCardBg))
                    )
                    .border(1.dp, EzAccentPurple.copy(0.7f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { activeOverlay = CockpitOverlay.VOICE },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "Voice",
                    tint               = EzTxtPrimary,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Top Navigation Strip (Tabs + Media + Lang + Theme)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzTopStrip(
    currentLang      : AppLanguage,
    themeMode        : ThemeMode,
    leftPanelVisible : Boolean,
    activeOverlay    : CockpitOverlay,
    onSelectOverlay  : (CockpitOverlay) -> Unit,
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

        // VDrive Logo Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderAccent, RoundedCornerShape(8.dp))
                .clickable { onSelectOverlay(CockpitOverlay.NONE) }
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = "VDrive",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = EzAccentCyan,
                letterSpacing = 0.5.sp
            )
        }

        // Top Navigation Tabs matching EZNav specification
        EzTopNavTab(
            label = "ទំព័រដើម",
            subLabel = "Home",
            isActive = activeOverlay == CockpitOverlay.NONE,
            onClick = { onSelectOverlay(CockpitOverlay.NONE) }
        )
        EzTopNavTab(
            label = "កាប៊ីន",
            subLabel = "Cabin",
            isActive = activeOverlay == CockpitOverlay.CABIN,
            onClick = { onSelectOverlay(CockpitOverlay.CABIN) }
        )
        EzTopNavTab(
            label = "សំឡេង",
            subLabel = "Voice",
            isActive = activeOverlay == CockpitOverlay.VOICE,
            onClick = { onSelectOverlay(CockpitOverlay.VOICE) }
        )
        EzTopNavTab(
            label = "ចំណាយ",
            subLabel = "Spending",
            isActive = activeOverlay == CockpitOverlay.SPENDING,
            onClick = { onSelectOverlay(CockpitOverlay.SPENDING) }
        )

        Spacer(Modifier.weight(1f))

        // Media controls
        EzStripBtn(icon = Icons.Default.MusicNote, onClick = {})
        EzStripBtn(icon = Icons.Default.SkipPrevious, onClick = {})
        EzStripBtn(icon = Icons.Default.PlayArrow, onClick = {})
        EzStripBtn(icon = Icons.Default.SkipNext, onClick = {})

        // Lang pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(16.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onLanguageToggle() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text       = if (currentLang == AppLanguage.KHMER) "ខ្មែរ" else "EN",
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = EzTxtPrimary
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
fun EzTopNavTab(
    label: String,
    subLabel: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color.White else EzCardBg)
            .border(0.5.dp, if (isActive) Color.White else EzBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFF0D0B1A) else EzTxtPrimary
            )
            Text(
                text = "• $subLabel",
                fontSize = 9.sp,
                color = if (isActive) Color(0xFF33304E) else EzTxtMuted
            )
        }
    }
}

@Composable
fun EzStripBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(EzCardBg)
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = EzTxtSecondary, modifier = Modifier.size(17.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Left Floating Panel (Search + Quick Destinations)
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
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onNavigate() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = EzTxtMuted, modifier = Modifier.size(16.dp))
            Text("${strings.map}...", fontSize = 13.sp, color = EzTxtMuted)
        }

        // Quick destinations
        val destinations = listOf(
            Pair(Icons.Default.Home,             "ផ្ទះ • Home / ការងារ • Office"),
            Pair(Icons.Default.Flight,           "អាកាសយានដ្ឋានភ្នំពេញ"),
            Pair(Icons.Default.Store,            "ផ្សារធំ • Central Market"),
            Pair(Icons.Default.ShoppingCart,     "ផ្សារ អ៊ីអន • AEON Mall"),
            Pair(Icons.Default.LocalGasStation,  "ស្ថានីយ EV • Charger")
        )
        destinations.forEach { (icon, label) ->
            EzDestinationRow(icon = icon, label = label, onClick = onNavigate)
        }

        // Speed indicator pill at bottom of left panel
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(EzAccentEmerald))
            Text(
                text       = "${telemetry.speedKmh.toInt()} km/h",
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = EzTxtPrimary
            )
            Spacer(Modifier.weight(1f))
            // Gear
            GearState.values().forEach { g ->
                Text(
                    text       = g.name,
                    fontSize   = 12.sp,
                    fontWeight = if (g == telemetry.gear) FontWeight.Black else FontWeight.Normal,
                    color      = when {
                        g != telemetry.gear   -> EzTxtMuted
                        g == GearState.P      -> EzAccentCoral
                        g == GearState.R      -> EzAccentAmber
                        else                  -> EzAccentEmerald
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
            .background(EzCardBg)
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = EzTxtSecondary, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 12.sp, color = EzTxtPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Right Floating Dashboard (Clock + Weather + Vehicle Stats + Trips)
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
        // Clock + Weather widget
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EzCardBgLight)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(16.dp))
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
                            color      = EzTxtPrimary,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1).sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EzAccentCyan.copy(0.15f))
                                .border(0.5.dp, EzAccentCyan.copy(0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("VDrive", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzAccentCyan)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = chankitek.formattedKhmerFull,
                        fontSize = 11.sp,
                        color = EzTxtSecondary
                    )
                }

                // Weather
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("ភ្នំពេញ", fontSize = 9.sp, color = EzTxtMuted)
                    Text("☀️", fontSize = 26.sp)
                    Text("ពន្លឺថ្ងៃ 32°", fontSize = 9.sp, color = EzAccentAmber, fontWeight = FontWeight.SemiBold)
                    Text(
                        text     = "34°",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color    = EzTxtPrimary
                    )
                }
            }
        }

        // Battery / Range / Odometer row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EzMetricCard(
                label     = "BATTERY",
                value     = "${telemetry.batteryPercent}",
                unit      = "%",
                accent    = if (telemetry.batteryPercent > 20) EzAccentEmerald else EzAccentCoral,
                showBar   = true,
                barFrac   = telemetry.batteryPercent / 100f,
                modifier  = Modifier.weight(1.2f)
            )
            EzMetricCard(
                label    = "RANGE",
                value    = "${telemetry.evRangeKm}",
                unit     = "km",
                accent   = EzAccentCyan,
                modifier = Modifier.weight(1f)
            )
            EzMetricCard(
                label    = "ODOMETER",
                value    = "${telemetry.odometerKm.toLong()}",
                unit     = "km",
                accent   = EzTxtSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Trips section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trips >", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onNavigateToSpending() })
                Text(
                    text = "Avg 21.0 kWh/100km",
                    fontSize = 9.sp,
                    color = EzTxtMuted
                )
            }

            EzTripRow(dot = EzAccentEmerald, label = "Live",  km = "0 km",  kwh = "0.0 kWh", isLive = true)
            EzTripRow(dot = EzTxtMuted,      label = "Today", km = "10 km", kwh = "1.7 kWh", isLive = false)
        }

        // Maintenance notice
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onNavigateToCabin() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(EzAccentAmber))
                Text(
                    "Tyre rotation in 8 717 km",
                    fontSize = 11.sp,
                    color    = EzTxtSecondary
                )
            }
            Text("Maintenance >", fontSize = 10.sp, color = EzTxtMuted)
        }

        // Fuel range (Thor PHEV specific)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = EzAccentAmber, modifier = Modifier.size(14.dp))
            Text("Fuel range  ${telemetry.fuelRangeKm} km", fontSize = 11.sp, color = EzTxtSecondary)
            Spacer(Modifier.weight(1f))
            Text("PHEV  •  Thor 1.5T", fontSize = 10.sp, color = EzTxtMuted)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Cabin Control Suite (Matching EZNav screenshot pixel-for-pixel)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzCabinSuite(
    currentHal: VehicleHalContract,
    telemetry: VehicleTelemetryState,
    strings: com.khmerauto.nav.core.i18n.AppStrings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hvac = telemetry.hvacState
    val cabin = telemetry.cabinState
    val tpms = telemetry.tpmsState

    var activeTempZone by remember { mutableStateOf(Zone.DRIVER) }
    var selectedPreset by remember { mutableStateOf("M1") }

    LazyColumn(
        modifier = modifier
            .width(460.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 1. Top Vehicle Overview Card ─────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EzCardBg)
                    .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // AEB status chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1B35))
                                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = EzAccentCyan, modifier = Modifier.size(12.dp))
                                Text("AEB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                            }
                        }

                        // Right Telemetry Stats
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("BATTERY", fontSize = 10.sp, color = EzTxtMuted)
                                Text("${telemetry.batteryPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ថាមពលអគ្គិសនី", fontSize = 10.sp, color = EzTxtMuted)
                                Text("${telemetry.evRangeKm} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzAccentCyan)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ប្រេងឥន្ធនៈ", fontSize = 10.sp, color = EzTxtMuted)
                                Text("${telemetry.fuelRangeKm} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzAccentAmber)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ODOMETER", fontSize = 10.sp, color = EzTxtMuted)
                                Text("${telemetry.odometerKm.toLong()} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtSecondary)
                            }
                        }
                    }

                    // Sleek Geely Galaxy M7 Silhouette
                    EzCarSideProfile(
                        batteryPercent = telemetry.batteryPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .padding(vertical = 4.dp)
                    )

                    // Bottom Row: "កាប៊ីន" title + M1, M2, M3 preset chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "កាប៊ីន",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = EzTxtPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("M1", "M2", "M3").forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedPreset == preset) Color.White else Color(0xFF1E1B35))
                                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { selectedPreset = preset }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPreset == preset) Color(0xFF0D0B1A) else EzTxtSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Climate Control Card ("ម៉ាស៊ីនត្រជាក់") ────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EzCardBg)
                    .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with Khmer title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FAN SPEED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted, letterSpacing = 0.5.sp)
                    Text("ម៉ាស៊ីនត្រជាក់", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                }

                // 7-speed vertical bars
                EzFanSpeedTicks(
                    currentSpeed = hvac.fanSpeed,
                    onSpeedSelected = { speed ->
                        coroutineScope.launch { currentHal.setFanSpeed(speed) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick buttons Row 1: A/C, AUTO, MAX, RECIRC
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EzClimatePill(label = "❄ A/C", isActive = hvac.isAcOn, modifier = Modifier.weight(1f)) {
                        coroutineScope.launch { currentHal.setAcMode(ac = !hvac.isAcOn) }
                    }
                    EzClimatePill(label = "AUTO", isActive = hvac.isAutoOn, isHighlightWhite = true, modifier = Modifier.weight(1f)) {
                        coroutineScope.launch { currentHal.setAcMode(auto = !hvac.isAutoOn) }
                    }
                    EzClimatePill(label = "MAX", isActive = hvac.isMaxAcOn, modifier = Modifier.weight(1f)) {
                        coroutineScope.launch { currentHal.setAcMode(maxAc = !hvac.isMaxAcOn) }
                    }
                    EzClimatePill(label = "RECIRC", isActive = hvac.isRecircOn, modifier = Modifier.weight(1.1f)) {
                        coroutineScope.launch { currentHal.setAcMode(recirc = !hvac.isRecircOn) }
                    }
                }

                // Quick buttons Row 2: FRONT, REAR, WHEEL, SYNC
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EzClimatePill(label = "FRONT", isActive = hvac.isFrontDefrostOn, modifier = Modifier.weight(1f)) {
                        coroutineScope.launch { currentHal.setAcMode(frontDefrost = !hvac.isFrontDefrostOn) }
                    }
                    EzClimatePill(label = "REAR", isActive = hvac.isRearDefrostOn, modifier = Modifier.weight(1f)) {
                        coroutineScope.launch { currentHal.setAcMode(rearDefrost = !hvac.isRearDefrostOn) }
                    }
                    EzClimatePill(label = "WHEEL", isActive = false, modifier = Modifier.weight(1f)) {
                        // Heated steering wheel toggle
                    }
                    EzClimatePill(label = "SYNC", isActive = true, modifier = Modifier.weight(1f)) {
                        coroutineScope.launch {
                            currentHal.setTemperature(Zone.PASSENGER, hvac.driverTempC)
                        }
                    }
                }

                // Dual zone stepped temperature selector
                EzDualTempSelector(
                    activeZone = activeTempZone,
                    onZoneChange = { activeTempZone = it },
                    driverTempC = hvac.driverTempC,
                    passengerTempC = hvac.passengerTempC,
                    onTempSelected = { zone, temp ->
                        coroutineScope.launch { currentHal.setTemperature(zone, temp) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 3. Bottom Row Cards (Windows/Doors, Roof/Tailgate, TPMS) ─────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Windows & Doors
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("កញ្ចក់ និងទ្វារ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted)

                    // 4 window status pills
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EzWindowBadge("FL", cabin.windowFlPercent, Modifier.weight(1f)) {
                            coroutineScope.launch {
                                currentHal.setWindowPosition(WindowTarget.FRONT_LEFT, if (cabin.windowFlPercent > 0) 0 else 100)
                            }
                        }
                        EzWindowBadge("FR", cabin.windowFrPercent, Modifier.weight(1f)) {
                            coroutineScope.launch {
                                currentHal.setWindowPosition(WindowTarget.FRONT_RIGHT, if (cabin.windowFrPercent > 0) 0 else 100)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EzWindowBadge("RL", cabin.windowRlPercent, Modifier.weight(1f)) {
                            coroutineScope.launch {
                                currentHal.setWindowPosition(WindowTarget.REAR_LEFT, if (cabin.windowRlPercent > 0) 0 else 100)
                            }
                        }
                        EzWindowBadge("RR", cabin.windowRrPercent, Modifier.weight(1f)) {
                            coroutineScope.launch {
                                currentHal.setWindowPosition(WindowTarget.REAR_RIGHT, if (cabin.windowRrPercent > 0) 0 else 100)
                            }
                        }
                    }

                    // Lock All button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (cabin.isDoorsLocked) Color.White else Color(0xFF1E1B35))
                            .clickable {
                                coroutineScope.launch { currentHal.setDoorLocks(!cabin.isDoorsLocked) }
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (cabin.isDoorsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (cabin.isDoorsLocked) Color(0xFF0D0B1A) else EzTxtPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                if (cabin.isDoorsLocked) "Locked • All" else "Unlocked",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cabin.isDoorsLocked) Color(0xFF0D0B1A) else EzTxtPrimary
                            )
                        }
                    }
                }

                // Roof shade & Tailgate (with strict Park safety interlock)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Roof shade", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E1B35))
                                .clickable { coroutineScope.launch { currentHal.setSunroofAndShade(shadePercent = 100) } }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▲ OPEN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E1B35))
                                .clickable { coroutineScope.launch { currentHal.setSunroofAndShade(shadePercent = 0) } }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▼ CLOSE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        }
                    }

                    // Smart Tailgate with Drive-State Interlock Guardrail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (cabin.isTailgateOpen) EzAccentCoral.copy(0.25f) else Color(0xFF1E1B35))
                            .border(0.5.dp, if (cabin.isTailgateOpen) EzAccentCoral else EzBorderSubtle, RoundedCornerShape(8.dp))
                            .clickable {
                                val willOpen = !cabin.isTailgateOpen
                                if (willOpen && (telemetry.gear != GearState.P || telemetry.speedKmh > 0f)) {
                                    Toast.makeText(
                                        context,
                                        "មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! / Cannot open tailgate while vehicle is in motion or not in Park!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    coroutineScope.launch { currentHal.setTailgate(willOpen) }
                                }
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (cabin.isTailgateOpen) "Close tailgate" else "Open tailgate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cabin.isTailgateOpen) EzAccentCoral else EzTxtPrimary
                        )
                    }
                }

                // TPMS 4-Tire Pressure Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("សម្ពាធកង់", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted)
                    EzCarTopTpms(
                        flBar = tpms.flBar,
                        frBar = tpms.frBar,
                        rlBar = tpms.rlBar,
                        rrBar = tpms.rrBar,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun EzClimatePill(
    label: String,
    isActive: Boolean,
    isHighlightWhite: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isActive && isHighlightWhite -> Color.White
                    isActive                     -> EzAccentCyan.copy(0.25f)
                    else                         -> Color(0xFF1E1B35)
                }
            )
            .border(
                0.5.dp,
                if (isActive && !isHighlightWhite) EzAccentCyan.copy(0.6f) else EzBorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isActive && isHighlightWhite -> Color(0xFF0D0B1A)
                isActive                     -> EzAccentCyan
                else                         -> EzTxtSecondary
            }
        )
    }
}

@Composable
private fun EzWindowBadge(
    label: String,
    percent: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (percent > 0) EzAccentCyan.copy(0.18f) else Color(0xFF1E1B35))
            .border(0.5.dp, if (percent > 0) EzAccentCyan.copy(0.5f) else EzBorderSubtle, RoundedCornerShape(6.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (percent > 0) "$label Open" else "$label Shut",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (percent > 0) EzAccentCyan else EzTxtSecondary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Voice Command Picker Modal (Matching EZNav screenshot pixel-for-pixel)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzVoiceModal(
    currentHal: VehicleHalContract,
    voiceService: VoiceAssistantService?,
    navController: NavigationController?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(VoiceCategory.CLIMATE) }
    var lastFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // Pulsing Voice Orb animation
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.88f)
            .ezGlow(EzAccentPurple, 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(EzCardBg)
            .border(1.dp, EzAccentPurple.copy(0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Modal Header ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B35))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = EzTxtPrimary, modifier = Modifier.size(18.dp))
                    }

                    Column {
                        Text(
                            text = "ជ្រើសរើសពាក្យបញ្ជា",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = EzTxtPrimary
                        )
                        Text(
                            text = "CHOOSE A COMMAND",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EzTxtMuted,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Glowing Voice Orb + status
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    lastFeedbackMessage?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 11.sp,
                            color = EzAccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(EzAccentPurple.copy(0.7f), Color.Transparent))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Body: Category Sidebar (Left) + 2-Column Command Grid (Right) ─
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left Categories Column
                Column(
                    modifier = Modifier
                        .width(170.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        Pair(VoiceCategory.CLIMATE,        "អាកាសធាតុ\nCLIMATE"),
                        Pair(VoiceCategory.WINDOWS,        "បង្អួច\nWINDOWS"),
                        Pair(VoiceCategory.DOORS_TAILGATE, "ទ្វារ & គូទក្រោយ\nDOORS & TAILGATE"),
                        Pair(VoiceCategory.SEATS_LIGHT,    "កៅអី & ភ្លើង\nSEATS & LIGHT"),
                        Pair(VoiceCategory.MEDIA,          "ប្រព័ន្ធកម្សាន្ត\nMEDIA"),
                        Pair(VoiceCategory.NAVIGATION,     "ការនាំផ្លូវ\nNAVIGATION"),
                        Pair(VoiceCategory.SCENES,         "ឈុតឆ្លាតវៃ\nSCENES")
                    )

                    categories.forEach { (cat, title) ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color.White else Color(0xFF1E1B35))
                                .border(0.5.dp, if (isSelected) Color.White else EzBorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D0B1A) else EzTxtSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Right 2-Column Command Cards Grid
                val commandsForCat = remember(selectedCategory) {
                    VoiceCommandCatalog.allCommands.filter { it.category == selectedCategory }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(commandsForCat) { cmd ->
                        EzVoiceCommandCard(
                            command = cmd,
                            onExecute = {
                                coroutineScope.launch {
                                    val fallbackController = navController ?: object : NavigationController {
                                        override suspend fun navigateTo(destination: com.khmerauto.nav.core.database.entity.PlaceEntity) {}
                                        override suspend fun navigateHome() {}
                                        override suspend fun navigateWork() {}
                                        override suspend fun navigateNearestCharger() {}
                                        override suspend fun navigateNearestRepair() {}
                                        override fun stopNavigation() {}
                                        override fun zoomIn() {}
                                        override fun zoomOut() {}
                                        override fun recenter() {}
                                        override fun setThemeMode(mode: ThemeMode) {}
                                        override fun setVoiceMuted(muted: Boolean) {}
                                    }
                                    val res = cmd.execute(currentHal, fallbackController)
                                    lastFeedbackMessage = res
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EzVoiceCommandCard(
    command: VoiceCommandEntry,
    onExecute: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1730))
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onExecute() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = command.titleKm,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EzTxtPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = command.titleEn,
                    fontSize = 11.sp,
                    color = EzAccentCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (command.acceptedPhrasesKm.isNotEmpty()) {
                    Text(
                        text = "« ${command.acceptedPhrasesKm.take(2).joinToString(" • ")} »",
                        fontSize = 9.sp,
                        color = EzTxtMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = EzTxtSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Spending & Maintenance Modal
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzSpendingModal(
    currentHal: VehicleHalContract,
    gapEngine: GapReconciliationEngine?,
    gapSummary: GapReconciliationSummary?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by currentHal.telemetryState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.88f)
            .ezGlow(EzAccentCyan, 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(EzCardBg)
            .border(1.dp, EzAccentCyan.copy(0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B35))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = EzTxtPrimary, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "ចំណាយ និងការថែទាំ • Spending & Service",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = EzTxtPrimary
                    )
                }
                Text(
                    text = "ODO: ${telemetry.odometerKm.toLong()} km",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = EzAccentCyan
                )
            }

            // Tab Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ចំណាយ / Spending", "ការថែទាំ / Maintenance", "យានដ្ឋាន EV / Workshops").forEachIndexed { idx, title ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color(0xFF1E1B35))
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0D0B1A) else EzTxtSecondary
                        )
                    }
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> {
                        // Spending & Trip Efficiency
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EzMetricCard(label = "THIS WEEK SPENT", value = "124,500", unit = "KHR", accent = EzAccentEmerald, modifier = Modifier.weight(1f))
                                EzMetricCard(label = "ENERGY AVG", value = "21.0", unit = "kWh/100km", accent = EzAccentCyan, modifier = Modifier.weight(1f))
                                EzMetricCard(label = "FUEL SAVED", value = "48.5", unit = "Litres", accent = EzAccentAmber, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    1 -> {
                        // Maintenance Countdowns
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EzMaintenanceRow("Tire Rotation (បង្វិលសំបកកង់)", 8717, 10000)
                            EzMaintenanceRow("Brake Fluid Inspection (ប្រេងហ្វ្រាំង)", 14500, 20000)
                            EzMaintenanceRow("Battery Coolant Flush (ទឹកស្អំអាគុយ)", 28000, 40000)
                            EzMaintenanceRow("Cabin Air Filter (តម្រងខ្យល់កាប៊ីន)", 3200, 15000)
                        }
                    }
                    else -> {
                        // Workshops
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EzWorkshopCard("Geely Phnom Penh Official Service", "Russian Blvd, Phnom Penh", "023 888 999")
                            EzWorkshopCard("EV Cambodia Pro Tech Center", "St 2004, Phnom Penh", "012 345 678")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EzMaintenanceRow(title: String, remainingKm: Int, totalKm: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1730))
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                Text("Service in $remainingKm km", fontSize = 10.sp, color = EzAccentCyan)
            }
            Text("$remainingKm / $totalKm km", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = EzTxtSecondary)
        }
    }
}

@Composable
private fun EzWorkshopCard(name: String, address: String, phone: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1730))
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
            Text(address, fontSize = 10.sp, color = EzTxtSecondary)
            Text("Tel: $phone", fontSize = 10.sp, color = EzAccentCyan)
        }
    }
}
