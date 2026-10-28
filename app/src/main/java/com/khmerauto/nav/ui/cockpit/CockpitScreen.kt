package com.khmerauto.nav.ui.cockpit

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.khmerauto.nav.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.khmerauto.nav.core.calendar.ChankitekCalendar
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.calendar.ThemeMode
import com.khmerauto.nav.core.database.entity.PlaceCategory
import com.khmerauto.nav.core.database.entity.PlaceEntity
import com.khmerauto.nav.core.i18n.AppLanguage
import com.khmerauto.nav.core.i18n.LanguageManager
import com.khmerauto.nav.core.i18n.LocalAppStrings
import com.khmerauto.nav.hal.*
import com.khmerauto.nav.map.CambodiaOfflineMapView
import com.khmerauto.nav.map.routing.ActiveNavigationState
import com.khmerauto.nav.map.routing.ManeuverType
import com.khmerauto.nav.map.routing.NavigationRouteEngine
import com.khmerauto.nav.map.routing.calculateHaversineDistanceKm
import kotlin.math.roundToInt
import com.khmerauto.nav.map.search.OfflinePlaceSearchRepository
import com.khmerauto.nav.telemetry.GapReconciliationEngine
import com.khmerauto.nav.telemetry.GapReconciliationSummary
import com.khmerauto.nav.ui.components.*
import com.khmerauto.nav.voice.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    placeRepository: OfflinePlaceSearchRepository? = null,
    routeEngine: NavigationRouteEngine? = null,
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

    // Navigation and Places reactive states
    val navState by (routeEngine?.navState ?: remember { MutableStateFlow(ActiveNavigationState()) }).collectAsState()
    var allPlaces by remember { mutableStateOf<List<PlaceEntity>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<PlaceEntity?>(null) }
    var recenterTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(placeRepository) {
        if (placeRepository != null) {
            allPlaces = placeRepository.getAllPlaces()
        }
    }

    // Active overlay modal state (NONE, CABIN, VOICE, SPENDING)
    var activeOverlay by remember { mutableStateOf(initialOverlay) }

    // Dynamic Splitter & Fullscreen Map State
    // User can dynamically drag the divider to make either panel larger or smaller (0.20f to 0.80f)
    var splitRatio by remember { mutableFloatStateOf(0.48f) }
    var isDraggingSplitter by remember { mutableStateOf(false) }
    var isFullMap by remember { mutableStateOf(false) }

    val animatedSplitRatio by animateFloatAsState(
        targetValue = if (isFullMap) 0f else splitRatio,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "splitRatioAnim"
    )
    val effectiveSplitRatio = if (isDraggingSplitter) splitRatio else animatedSplitRatio

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(EzBgDeep)) {
        val totalWidth = maxWidth
        val density = LocalDensity.current
        val totalWidthPx = with(density) { totalWidth.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Navigation Strip (Tabs + Lang + Theme + Diagnostics) ────
            AnimatedVisibility(
                visible  = !isFullMap,
                enter    = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
                exit     = slideOutVertically(tween(250)) { -it } + fadeOut(tween(250))
            ) {
                EzTopStrip(
                    currentLang      = currentLang,
                    themeMode        = themeMode,
                    leftPanelVisible = effectiveSplitRatio > 0.05f,
                    activeOverlay    = activeOverlay,
                    onSelectOverlay  = { overlay ->
                        activeOverlay = if (activeOverlay == overlay) CockpitOverlay.NONE else overlay
                    },
                    onToggleLeft     = {
                        splitRatio = if (splitRatio > 0.15f) 0f else 0.48f
                    },
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

            // ── Dynamic Draggable Two-Panel Workspace ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        start = if (isFullMap) 0.dp else 10.dp,
                        end = if (isFullMap) 0.dp else 10.dp,
                        bottom = if (isFullMap) 0.dp else 8.dp,
                        top = if (isFullMap) 0.dp else 2.dp
                    )
            ) {
                // 1. LEFT PANEL: Cabin Suite / Telemetry Overview (Dynamically resizable)
                if (effectiveSplitRatio > 0.05f) {
                    val leftWidth = (totalWidth - 16.dp) * effectiveSplitRatio
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(leftWidth)
                    ) {
                        when (activeOverlay) {
                            CockpitOverlay.VOICE -> {
                                EzVoiceModal(
                                    currentHal    = currentHal,
                                    voiceService  = voiceService,
                                    navController = navController,
                                    onClose       = { activeOverlay = CockpitOverlay.NONE }
                                )
                            }
                            CockpitOverlay.SPENDING -> {
                                EzSpendingModal(
                                    currentHal = currentHal,
                                    gapEngine  = gapEngine,
                                    gapSummary = gapSummary,
                                    onClose    = { activeOverlay = CockpitOverlay.NONE }
                                )
                            }
                            CockpitOverlay.CABIN -> {
                                EzCabinSuite(
                                    currentHal = currentHal,
                                    telemetry  = telemetry,
                                    strings    = strings,
                                    onClose    = { /* Stay in cockpit view */ },
                                    modifier   = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                EzHomeScreen(
                                    currentHal = currentHal,
                                    telemetry  = telemetry,
                                    strings    = strings,
                                    onNavigateToCabin = { activeOverlay = CockpitOverlay.CABIN },
                                    modifier   = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // 2. DRAGGABLE SPLITTER HANDLE (Between panels: drag left/right, double-tap to reset)
                    if (!isFullMap) {
                        EzSplitterDivider(
                            isDragging   = isDraggingSplitter,
                            splitRatio   = splitRatio,
                            onDrag       = { deltaPx ->
                                val deltaRatio = deltaPx / totalWidthPx
                                splitRatio = (splitRatio + deltaRatio).coerceIn(0.20f, 0.80f)
                            },
                            onDragStart  = { isDraggingSplitter = true },
                            onDragEnd    = { isDraggingSplitter = false },
                            onDoubleTap  = { splitRatio = 0.50f }
                        )
                    }
                }

                // 3. RIGHT PANEL: Framed Offline Cambodia Map & Navigation Card
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    EzMapPanelCard(
                        telemetry               = telemetry,
                        places                  = allPlaces,
                        navState                = navState,
                        selectedPlace           = selectedPlace,
                        recenterTrigger         = recenterTrigger,
                        isFullMap               = isFullMap,
                        onToggleFullMap         = { isFullMap = !isFullMap },
                        onPlaceSelected         = { place ->
                            selectedPlace = place
                        },
                        onQuickDestinationClick = { place ->
                            selectedPlace = place
                        },
                        onConfirmNavigation     = { place ->
                            coroutineScope.launch {
                                routeEngine?.startNavigation(
                                    destination           = place,
                                    currentBatteryPercent = telemetry.batteryPercent,
                                    startLat              = telemetry.latitude,
                                    startLng              = telemetry.longitude
                                )
                                navController?.navigateTo(place)
                            }
                            selectedPlace = null
                        },
                        onCancelSelection       = {
                            selectedPlace = null
                        },
                        onOpenVoice             = { activeOverlay = CockpitOverlay.VOICE },
                        onRecenter              = { recenterTrigger++ },
                        onZoomIn                = { navController?.zoomIn() },
                        onZoomOut               = { navController?.zoomOut() },
                        onStopNavigation        = {
                            routeEngine?.stopNavigation()
                            navController?.stopNavigation()
                            selectedPlace = null
                        },
                        onSearchQuery           = { query ->
                            placeRepository?.searchPlaces(query) ?: emptyList()
                        },
                        modifier                = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Draggable Splitter Divider between Left & Right Cockpit Panels
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzSplitterDivider(
    isDragging: Boolean,
    splitRatio: Float,
    onDrag: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Vertical divider guide line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(if (isDragging) EzAccentCyan.copy(0.5f) else EzBorderSubtle)
        )

        // Center Grip Handle Capsule
        Box(
            modifier = Modifier
                .width(if (isDragging) 6.dp else 4.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isDragging) EzAccentCyan else Color(0xFF33304E))
                .border(
                    0.5.dp,
                    if (isDragging) EzAccentCyan else EzBorderSubtle,
                    RoundedCornerShape(3.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(2.dp)
                            .clip(CircleShape)
                            .background(if (isDragging) Color(0xFF0D0B1A) else EzTxtMuted)
                    )
                }
            }
        }

        // Dynamic Percentage Badge during active dragging
        if (isDragging) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xF0121024),
                border = BorderStroke(1.dp, EzAccentCyan),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 20.dp)
                    .zIndex(10f)
            ) {
                Text(
                    text = "${(splitRatio * 100).toInt()}%  |  ${((1f - splitRatio) * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EzAccentCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Framed Offline Map Panel Card with Search, Quick Chips & Navigation Banner
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzMapPanelCard(
    telemetry: VehicleTelemetryState,
    places: List<PlaceEntity>,
    navState: ActiveNavigationState,
    selectedPlace: PlaceEntity?,
    recenterTrigger: Int,
    isFullMap: Boolean,
    onToggleFullMap: () -> Unit,
    onPlaceSelected: (PlaceEntity) -> Unit,
    onQuickDestinationClick: (PlaceEntity) -> Unit,
    onConfirmNavigation: (PlaceEntity) -> Unit,
    onCancelSelection: () -> Unit,
    onOpenVoice: () -> Unit,
    onRecenter: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onStopNavigation: () -> Unit,
    onSearchQuery: suspend (String) -> List<PlaceEntity>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceEntity>>(emptyList()) }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchResults = onSearchQuery(searchQuery)
        } else {
            searchResults = emptyList()
        }
    }

    // 5 Geely Cockpit Shortcuts (matching photo: 🏠 ផ្ទះ, 💼 កន្លែងធ្វើការ, bong ka, ម៉ឺងយ៉ា, KEL)
    val homePlace = remember(places) {
        places.firstOrNull { it.category == PlaceCategory.SAVED_HOME || it.nameKm.contains("ផ្ទះ") }
            ?: PlaceEntity(nameKm = "ផ្ទះ", nameEn = "Home", category = PlaceCategory.SAVED_HOME, latitude = 11.5580, longitude = 104.9200, addressKm = "បឹងកេងកង ១", addressEn = "BKK1")
    }
    val workPlace = remember(places) {
        places.firstOrNull { it.category == PlaceCategory.SAVED_WORK || it.nameKm.contains("ការងារ") }
            ?: PlaceEntity(nameKm = "កន្លែងធ្វើការ", nameEn = "Work", category = PlaceCategory.SAVED_WORK, latitude = 11.5724, longitude = 104.9196, addressKm = "អគារកាណាឌីយ៉ា", addressEn = "Canadia Tower")
    }
    val bongKaPlace = remember(places) {
        places.firstOrNull { it.nameEn.contains("bong ka", ignoreCase = true) }
            ?: PlaceEntity(nameKm = "bong ka", nameEn = "bong ka", category = PlaceCategory.FAVORITE, latitude = 11.5510, longitude = 104.9270, addressKm = "បឹងកេងកង", addressEn = "BKK")
    }
    val meungYaPlace = remember(places) {
        places.firstOrNull { it.nameKm.contains("ម៉ឺងយ៉ា") || it.nameEn.contains("Meung Ya", ignoreCase = true) }
            ?: PlaceEntity(nameKm = "ម៉ឺងយ៉ា", nameEn = "Meung Ya", category = PlaceCategory.FAVORITE, latitude = 11.5760, longitude = 104.8980, addressKm = "ទួលគោក", addressEn = "Toul Kork")
    }
    val kelPlace = remember(places) {
        places.firstOrNull { it.nameEn.contains("KEL", ignoreCase = true) }
            ?: PlaceEntity(nameKm = "KEL", nameEn = "KEL", category = PlaceCategory.FAVORITE, latitude = 11.5490, longitude = 104.9320, addressKm = "បាសាក់", addressEn = "Bassac")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isFullMap) 0.dp else 16.dp))
            .background(EzCardBg)
            .border(
                width = if (isFullMap) 0.dp else 0.5.dp,
                color = if (isFullMap) Color.Transparent else EzBorderSubtle,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        // ── 1. MapLibre 100% Offline Vector/Raster Engine ────────────────────
        CambodiaOfflineMapView(
            vehicleLat          = telemetry.latitude,
            vehicleLng          = telemetry.longitude,
            vehicleHeading      = telemetry.headingDegrees,
            vehicleSpeedKmh     = telemetry.speedKmh,
            places              = places,
            navigationState     = navState,
            selectedPlace       = selectedPlace,
            recenterTrigger     = recenterTrigger,
            onPlaceSelected     = onPlaceSelected,
            bottomDockPaddingDp = if (navState.isNavigating) 80 else 10,
            modifier            = Modifier.fillMaxSize()
        )

        // ── 2. Top Floating Navigation Banner (When actively navigating) ────
        if (navState.isNavigating) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF2121024),
                border = BorderStroke(1.dp, EzBorderAccent),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EzAccentCyan.copy(0.2f))
                            .border(1.dp, EzAccentCyan, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (navState.currentManeuver?.maneuverType) {
                                ManeuverType.TURN_LEFT -> Icons.Default.TurnLeft
                                ManeuverType.TURN_RIGHT -> Icons.Default.TurnRight
                                ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                                ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                                ManeuverType.U_TURN -> Icons.Default.RotateLeft
                                ManeuverType.ROUNDABOUT -> Icons.Default.Refresh
                                ManeuverType.ARRIVED -> Icons.Default.CheckCircle
                                else -> Icons.Default.Straight
                            },
                            contentDescription = null,
                            tint = EzAccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val dist = navState.currentManeuver?.distanceMeters ?: 0
                        val distStr = if (dist >= 1000) String.format("%.1f km", dist / 1000f) else "$dist m"
                        val instruction = navState.currentManeuver?.instructionKm ?: "បន្តដំណើរទៅមុខ"
                        Text(
                            text = "$distStr • $instruction",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${String.format("%.1f", navState.remainingDistanceKm)} km  •  ${navState.remainingMinutes} នាទី  •  ETA ${navState.etaString}  •  ${navState.estimatedArrivalBatteryPercent}% SoC",
                            fontSize = 11.sp,
                            color = EzTxtSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Cancel Navigation Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C1625))
                            .border(0.5.dp, EzAccentCoral, CircleShape)
                            .clickable { onStopNavigation() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop Navigation",
                            tint = EzAccentCoral,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            // ── Top Search Bar & Quick Destination Chips (When idle) ────────
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Bar: 🔍 ទៅណា?
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xDD121024),
                    border = BorderStroke(0.5.dp, EzBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = EzAccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                isSearchActive = it.isNotBlank()
                            },
                            placeholder = {
                                Text("🔍 ទៅណា? / Where to?", fontSize = 12.sp, color = EzTxtMuted)
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = EzAccentCyan,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = EzTxtMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Instant Search Results Dropdown
                if (isSearchActive && searchResults.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xF2121024),
                        border = BorderStroke(0.5.dp, EzBorderAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            items(searchResults.take(5)) { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            searchQuery = ""
                                            isSearchActive = false
                                            onQuickDestinationClick(place)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (place.isEvCharger) Icons.Default.EvStation else Icons.Default.Place,
                                        contentDescription = null,
                                        tint = EzAccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            place.nameKm.ifBlank { place.nameEn },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(place.nameEn, fontSize = 10.sp, color = EzTxtMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Destination Chips: 🏠 ផ្ទះ, 💼 កន្លែងធ្វើការ, bong ka, ម៉ឺងយ៉ា, KEL
                if (!isSearchActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EzMapShortcutChip(icon = "🏠", label = "ផ្ទះ", onClick = { onQuickDestinationClick(homePlace) })
                        EzMapShortcutChip(icon = "💼", label = "កន្លែងធ្វើការ", onClick = { onQuickDestinationClick(workPlace) })
                        EzMapShortcutChip(icon = "⭐", label = "bong ka", onClick = { onQuickDestinationClick(bongKaPlace) })
                        EzMapShortcutChip(icon = "⭐", label = "ម៉ឺងយ៉ា", onClick = { onQuickDestinationClick(meungYaPlace) })
                        EzMapShortcutChip(icon = "⭐", label = "KEL", onClick = { onQuickDestinationClick(kelPlace) })
                    }
                }
            }
        }

        val bottomControlsPadding by animateDpAsState(
            targetValue = if (selectedPlace != null) 220.dp else 14.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "bottomControlsPadding"
        )

        // ── 3. Bottom-Left Floating Voice Assistant FAB ─────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = bottomControlsPadding)
                .size(48.dp)
                .ezGlow(EzAccentPurple, 8.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(EzAccentPurple.copy(0.45f), EzCardBg))
                )
                .border(1.dp, EzAccentPurple.copy(0.7f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenVoice() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Mic,
                contentDescription = "Voice",
                tint               = Color.White,
                modifier           = Modifier.size(20.dp)
            )
        }

        // ── 4. Bottom-Right Floating Map Controls & Maximize Button ─────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = bottomControlsPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fullscreen Map Maximize / Restore Toggle Button
            EzMapIconBtn(
                icon = if (isFullMap) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                onClick = onToggleFullMap
            )
            // Recenter Vehicle GPS Position Button
            EzMapIconBtn(icon = Icons.Default.NearMe, onClick = onRecenter)
            // Zoom In
            EzMapIconBtn(icon = Icons.Default.Add, onClick = onZoomIn)
            // Zoom Out
            EzMapIconBtn(icon = Icons.Default.Remove, onClick = onZoomOut)
        }

        // ── 5. Google Maps Style Place Confirmation Card ────────────────────
        AnimatedVisibility(
            visible = selectedPlace != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            selectedPlace?.let { place ->
                EzPlaceConfirmationCard(
                    place          = place,
                    vehicleLat     = telemetry.latitude,
                    vehicleLng     = telemetry.longitude,
                    vehicleBattery = telemetry.batteryPercent,
                    isNavigating   = navState.isNavigating,
                    onConfirm      = { onConfirmNavigation(place) },
                    onDismiss      = onCancelSelection
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Map Shortcut Chip for Driver Destinations
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzMapShortcutChip(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xDD1E1B35),
        border = BorderStroke(0.5.dp, EzBorderSubtle),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 10.sp)
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Google Maps Style Place Confirmation Card (Appears before starting navigation)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzPlaceConfirmationCard(
    place: PlaceEntity,
    vehicleLat: Double,
    vehicleLng: Double,
    vehicleBattery: Int,
    isNavigating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distKm = remember(vehicleLat, vehicleLng, place.latitude, place.longitude) {
        calculateHaversineDistanceKm(vehicleLat, vehicleLng, place.latitude, place.longitude)
    }
    val distStr = remember(distKm) {
        if (distKm >= 1.0f) {
            String.format(java.util.Locale.US, "%.1f km", distKm)
        } else {
            "${(distKm * 1000).toInt()} m"
        }
    }
    val estMinutes = remember(distKm) {
        maxOf(2, (distKm / 28.0f * 60).roundToInt())
    }
    val socDrain = remember(distKm) {
        (distKm * 0.22f).roundToInt().coerceAtLeast(1)
    }
    val estArrivalSoc = remember(vehicleBattery, socDrain) {
        (vehicleBattery - socDrain).coerceIn(0, 100)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF5100E22),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(EzAccentCyan.copy(0.7f), EzAccentPurple.copy(0.4f)))),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .ezGlow(EzAccentCyan.copy(0.2f), 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drag handle accent indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x44FFFFFF))
            )

            // Header: Category Icon + Place Name & Address + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category Icon Badge
                val (badgeColor, iconVector) = when (place.category) {
                    PlaceCategory.SAVED_HOME -> EzAccentCyan to Icons.Default.Home
                    PlaceCategory.SAVED_WORK -> EzAccentPurple to Icons.Default.Work
                    PlaceCategory.EV_CHARGER -> Color(0xFF00E676) to Icons.Default.EvStation
                    PlaceCategory.REPAIR_SHOP -> EzAccentCoral to Icons.Default.Build
                    PlaceCategory.FAVORITE -> Color(0xFFFFD700) to Icons.Default.Star
                    else -> Color(0xFFFF5252) to Icons.Default.Place
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Place Names & Address
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = place.nameKm.ifBlank { place.nameEn },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = place.nameEn,
                        fontSize = 11.sp,
                        color = Color(0xFFA0A5B5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val fullAddress = listOfNotNull(
                        place.addressKm.takeIf { it.isNotBlank() },
                        place.city.takeIf { it.isNotBlank() }
                    ).joinToString(" • ").ifBlank { "Cambodia" }
                    Text(
                        text = fullAddress,
                        fontSize = 11.sp,
                        color = EzTxtMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Close Button (Dismiss without navigating)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Route & Trip Estimation Strip (Google Maps Style Metrics)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x401A1830))
                    .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Driving Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$estMinutes នាទី (~${estMinutes}m)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }

                // Distance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        tint = EzAccentCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = distStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Battery SoC Arrival
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = EzAccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$estArrivalSoc% SoC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = EzAccentCyan
                    )
                }
            }

            // EV Station Badges (if EV Charger)
            if (place.isEvCharger) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EzMiniBadge(
                        text = "⚡ ${place.maxPowerKw} kW DC",
                        color = Color(0xFF00E676)
                    )
                    EzMiniBadge(
                        text = "🟢 ${place.availablePlugs}/${place.totalPlugs} ទំនេរ",
                        color = EzAccentCyan
                    )
                    if (place.pricePerKwhKhr > 0) {
                        EzMiniBadge(
                            text = "៛ ${place.pricePerKwhKhr}/kWh",
                            color = Color(0xFFFFD54F)
                        )
                    }
                }
            }

            // Action Buttons: Cancel vs Start Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, Color(0x40FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x18FFFFFF),
                        contentColor = Color(0xFFC0C5D0)
                    ),
                    modifier = Modifier
                        .weight(0.36f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "បោះបង់ / Cancel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                // Start Navigation Button (Google Maps Style Primary CTA)
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(0.64f)
                        .height(44.dp)
                        .ezGlow(Color(0xFF00B0FF), 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1976D2), Color(0xFF00B0FF))
                            )
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isNavigating) "ប្តូរគោលដៅ / Reroute" else "ចាប់ផ្តើម / Start Navigation",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EzMiniBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
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
// Driver Cockpit Home Suite (ទំព័រដើម • Home)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun EzHomeScreen(
    currentHal: VehicleHalContract,
    telemetry: VehicleTelemetryState,
    strings: com.khmerauto.nav.core.i18n.AppStrings,
    onNavigateToCabin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val hvac = telemetry.hvacState
    var activeTempZone by remember { mutableStateOf(Zone.DRIVER) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── 1. Top Vehicle Overview Card (Photo + Drive Modes) ───────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Top Telemetry Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AEB & Gear Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E1B35))
                                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = EzAccentCyan, modifier = Modifier.size(10.dp))
                                Text("AEB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EzAccentEmerald.copy(0.18f))
                                .border(0.5.dp, EzAccentEmerald.copy(0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(telemetry.gear.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentEmerald)
                        }
                    }

                    // Compact Telemetry Metrics
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 9.sp)
                            Text("${telemetry.evRangeKm}km", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentCyan)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⛽", fontSize = 9.sp)
                            Text("${telemetry.fuelRangeKm}km", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentAmber)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔋", fontSize = 9.sp)
                            Text("${telemetry.batteryPercent}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        }
                        Text("${telemetry.odometerKm.toLong()}km", fontSize = 9.sp, color = EzTxtMuted)
                    }
                }

                // Real 2026 Geely Galaxy M7 Full Photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF070611))
                        .border(0.5.dp, EzBorderSubtle.copy(0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.geely_galaxy_m7),
                        contentDescription = "Geely Galaxy M7",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to Color(0xFF070611),
                                    0.10f to Color.Transparent,
                                    0.90f to Color.Transparent,
                                    1.0f to Color(0xFF070611)
                                )
                            )
                    )
                }

                // 2026 Geely Thor EM-P Drive Modes
                val geelyModes = listOf(
                    GeelyDriveMode.PURE_EV to "PURE EV",
                    GeelyDriveMode.SMART to "SMART",
                    GeelyDriveMode.POWER to "POWER",
                    GeelyDriveMode.RANGE_EXT to "RANGE EXT"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    geelyModes.forEach { (mode, label) ->
                        val isSelected = telemetry.driveMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isSelected) EzAccentCyan.copy(0.25f) else Color(0xFF1E1B35))
                                .border(
                                    0.5.dp,
                                    if (isSelected) EzAccentCyan else EzBorderSubtle,
                                    RoundedCornerShape(7.dp)
                                )
                                .clickable {
                                    coroutineScope.launch { currentHal.setDriveMode(mode) }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) EzAccentCyan else EzTxtSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Quick Vehicle Functions & Driving Energy Status ───────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "មុខងាររហ័ស & ថាមពល (Quick Controls & Energy)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EzTxtPrimary
                    )
                    Text(
                        text = "THOR EM-P",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = EzAccentCyan,
                        letterSpacing = 0.5.sp
                    )
                }

                // 4 Quick Automotive Action Chips
                var isMirrorsFolded by remember { mutableStateOf(false) }
                var isAmbientLightOn by remember { mutableStateOf(true) }
                var isAutoHeadlamps by remember { mutableStateOf(true) }
                var isChildLockOn by remember { mutableStateOf(true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    EzQuickFeatureToggle(
                        icon = Icons.Default.FlipCameraAndroid,
                        label = "បត់កញ្ចក់",
                        subLabel = "Mirrors",
                        isActive = isMirrorsFolded,
                        modifier = Modifier.weight(1f)
                    ) {
                        isMirrorsFolded = !isMirrorsFolded
                    }
                    EzQuickFeatureToggle(
                        icon = Icons.Default.Lightbulb,
                        label = "ភ្លើងបំភ្លឺ",
                        subLabel = "Ambient",
                        isActive = isAmbientLightOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        isAmbientLightOn = !isAmbientLightOn
                    }
                    EzQuickFeatureToggle(
                        icon = Icons.Default.Highlight,
                        label = "ចង្កៀងមុខ",
                        subLabel = "Headlamps",
                        isActive = isAutoHeadlamps,
                        modifier = Modifier.weight(1f)
                    ) {
                        isAutoHeadlamps = !isAutoHeadlamps
                    }
                    EzQuickFeatureToggle(
                        icon = Icons.Default.ChildCare,
                        label = "សោកុមារ",
                        subLabel = "Child Lock",
                        isActive = isChildLockOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        isChildLockOn = !isChildLockOn
                    }
                }

                // Powertrain & Hybrid Efficiency Metrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141226))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⚡", fontSize = 11.sp)
                        Column {
                            Text("ម៉ូទ័រ / Motor Power", fontSize = 8.sp, color = EzTxtMuted)
                            Text("${if (telemetry.speedKmh > 0) (telemetry.speedKmh * 0.45f).toInt() else 0} kW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentCyan)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔋", fontSize = 11.sp)
                        Column {
                            Text("កម្រិតប្រើប្រាស់ / Avg", fontSize = 8.sp, color = EzTxtMuted)
                            Text("15.6 kWh/100km", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentEmerald)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔄", fontSize = 11.sp)
                        Column {
                            Text("Regen / ចាប់ហ្វ្រាំង", fontSize = 8.sp, color = EzTxtMuted)
                            Text("High (ខ្លាំង)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentAmber)
                        }
                    }
                }
            }
        }

        // ── 3. Primary Climate Quick Access Card ──────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ម៉ាស៊ីនត្រជាក់ (HVAC)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (hvac.isPowerOn) EzAccentCyan.copy(0.2f) else Color(0xFF1E1B35))
                                .border(0.5.dp, if (hvac.isPowerOn) EzAccentCyan else EzBorderSubtle, CircleShape)
                                .clickable { coroutineScope.launch { currentHal.setHvacPower(!hvac.isPowerOn) } }
                                .padding(3.dp)
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "HVAC Power",
                                tint = if (hvac.isPowerOn) EzAccentCyan else EzTxtMuted,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Text(
                        text = "កាប៊ីនពេញលេញ >",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EzAccentCyan,
                        modifier = Modifier.clickable { onNavigateToCabin() }
                    )
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── 1. Top Vehicle Overview Card ─────────────────────────────────────
        Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EzCardBg)
                    .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Top Telemetry Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // AEB & Gear Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E1B35))
                                    .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = EzAccentCyan, modifier = Modifier.size(10.dp))
                                    Text("AEB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EzAccentEmerald.copy(0.18f))
                                    .border(0.5.dp, EzAccentEmerald.copy(0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(telemetry.gear.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentEmerald)
                            }
                        }

                        // Compact Telemetry Metrics
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 9.sp)
                                Text("${telemetry.evRangeKm}km", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentCyan)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⛽", fontSize = 9.sp)
                                Text("${telemetry.fuelRangeKm}km", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentAmber)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔋", fontSize = 9.sp)
                                Text("${telemetry.batteryPercent}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                            }
                            Text("${telemetry.odometerKm.toLong()}km", fontSize = 9.sp, color = EzTxtMuted)
                        }
                    }

                    // Real 2026 Geely Galaxy M7 Full Photo (Complete car with wheels and bumpers visible)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF070611))
                            .border(0.5.dp, EzBorderSubtle.copy(0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.geely_galaxy_m7),
                            contentDescription = "Geely Galaxy M7",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        // Subtle gradient edge fades so showroom photo melts seamlessly into card
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.0f to Color(0xFF070611),
                                        0.10f to Color.Transparent,
                                        0.90f to Color.Transparent,
                                        1.0f to Color(0xFF070611)
                                    )
                                )
                        )
                    }

                    // Actual 2026 Geely Galaxy M7 (Thor EM-P) Drive Modes
                    // Modes: PURE EV (纯电), SMART (智能), POWER (性能), RANGE EXT (增程 / 保电)
                    val geelyModes = listOf(
                        GeelyDriveMode.PURE_EV to "PURE EV",
                        GeelyDriveMode.SMART to "SMART",
                        GeelyDriveMode.POWER to "POWER",
                        GeelyDriveMode.RANGE_EXT to "RANGE EXT"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        geelyModes.forEach { (mode, label) ->
                            val isSelected = telemetry.driveMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (isSelected) EzAccentCyan.copy(0.25f) else Color(0xFF1E1B35))
                                    .border(
                                        0.5.dp,
                                        if (isSelected) EzAccentCyan else EzBorderSubtle,
                                        RoundedCornerShape(7.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch { currentHal.setDriveMode(mode) }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) EzAccentCyan else EzTxtSecondary
                                )
                            }
                        }
                    }

                }
            }

        // ── 2. Climate Control Card (Real Geely Galaxy M7 Flyme Auto Functions) ──
        Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EzCardBg)
                    .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Header with Khmer title and Master Power Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("ម៉ាស៊ីនត្រជាក់ (HVAC)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (hvac.isPowerOn) EzAccentCyan.copy(0.2f) else Color(0xFF1E1B35))
                                .border(0.5.dp, if (hvac.isPowerOn) EzAccentCyan else EzBorderSubtle, CircleShape)
                                .clickable { coroutineScope.launch { currentHal.setHvacPower(!hvac.isPowerOn) } }
                                .padding(3.dp)
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "HVAC Power",
                                tint = if (hvac.isPowerOn) EzAccentCyan else EzTxtMuted,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Text("FAN SPEED ${hvac.fanSpeed}/7", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted, letterSpacing = 0.5.sp)
                }

                // 7-speed vertical bars
                EzFanSpeedTicks(
                    currentSpeed = hvac.fanSpeed,
                    onSpeedSelected = { speed ->
                        coroutineScope.launch { currentHal.setFanSpeed(speed) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Real Geely Galaxy M7 Flyme Auto Functions: Row 1
                // ❄ A/C (Compressor), AUTO (Intelligent Auto), MAX A/C (Rapid Cooling), SYNC (Dual-Zone Sync)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EzClimatePill(
                        label = "❄ A/C",
                        isActive = hvac.isAcOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(ac = !hvac.isAcOn) }
                    }
                    EzClimatePill(
                        label = "AUTO",
                        isActive = hvac.isAutoOn,
                        isHighlightWhite = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(auto = !hvac.isAutoOn) }
                    }
                    EzClimatePill(
                        label = "MAX A/C",
                        isActive = hvac.isMaxAcOn,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(maxAc = !hvac.isMaxAcOn) }
                    }
                    EzClimatePill(
                        label = "SYNC",
                        isActive = hvac.isSyncOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(sync = !hvac.isSyncOn) }
                    }
                }

                // Real Geely Galaxy M7 Flyme Auto Functions: Row 2
                // RECIRC (Recirculation), FRONT DEF (Defrost), REAR DEF (Defog), CLEAN AIR (CleanZone Ion Purifier)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EzClimatePill(
                        label = "RECIRC",
                        isActive = hvac.isRecircOn,
                        modifier = Modifier.weight(1.05f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(recirc = !hvac.isRecircOn) }
                    }
                    EzClimatePill(
                        label = "FRONT DEF",
                        isActive = hvac.isFrontDefrostOn,
                        modifier = Modifier.weight(1.15f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(frontDefrost = !hvac.isFrontDefrostOn) }
                    }
                    EzClimatePill(
                        label = "REAR DEF",
                        isActive = hvac.isRearDefrostOn,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(rearDefrost = !hvac.isRearDefrostOn) }
                    }
                    EzClimatePill(
                        label = "CLEAN AIR",
                        isActive = hvac.isCleanAirOn,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        coroutineScope.launch { currentHal.setAcMode(cleanAir = !hvac.isCleanAirOn) }
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

        // ── 3. Bottom Row Cards (Windows/Doors, Roof/Tailgate, TPMS) ─────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                // Windows & Doors
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("កញ្ចក់ និងទ្វារ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted)

                    // 4 window status pills
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (cabin.isDoorsLocked) Color.White else Color(0xFF1E1B35))
                            .clickable {
                                coroutineScope.launch { currentHal.setDoorLocks(!cabin.isDoorsLocked) }
                            }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                if (cabin.isDoorsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (cabin.isDoorsLocked) Color(0xFF0D0B1A) else EzTxtPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                if (cabin.isDoorsLocked) "Locked • All" else "Unlocked",
                                fontSize = 10.sp,
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("ដំបូល & គូទ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF1E1B35))
                                .clickable { coroutineScope.launch { currentHal.setSunroofAndShade(shadePercent = 100) } }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▲ OPEN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF1E1B35))
                                .clickable { coroutineScope.launch { currentHal.setSunroofAndShade(shadePercent = 0) } }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▼ CLOSE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                        }
                    }

                    // Smart Tailgate with Drive-State Interlock Guardrail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (cabin.isTailgateOpen) EzAccentCoral.copy(0.25f) else Color(0xFF1E1B35))
                            .border(0.5.dp, if (cabin.isTailgateOpen) EzAccentCoral else EzBorderSubtle, RoundedCornerShape(6.dp))
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
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (cabin.isTailgateOpen) "Close Trunk" else "Open Trunk",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cabin.isTailgateOpen) EzAccentCoral else EzTxtPrimary
                        )
                    }
                }

                // TPMS 4-Tire Pressure Card
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EzCardBg)
                        .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("សម្ពាធកង់", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EzTxtMuted)
                    EzCarTopTpms(
                        flBar = tpms.flBar,
                        frBar = tpms.frBar,
                        rlBar = tpms.rlBar,
                        rrBar = tpms.rrBar,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

        // ── 4. Quick Vehicle Functions & Driving Energy Status ─────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EzCardBg)
                .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "មុខងាររហ័ស & ថាមពល (Quick Controls & Energy)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EzTxtPrimary
                    )
                    Text(
                        text = "THOR EM-P",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = EzAccentCyan,
                        letterSpacing = 0.5.sp
                    )
                }

                // Row 1: 4 Quick Automotive Action Chips
                var isMirrorsFolded by remember { mutableStateOf(false) }
                var isAmbientLightOn by remember { mutableStateOf(true) }
                var isAutoHeadlamps by remember { mutableStateOf(true) }
                var isChildLockOn by remember { mutableStateOf(true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    EzQuickFeatureToggle(
                        icon = Icons.Default.FlipCameraAndroid,
                        label = "បត់កញ្ចក់",
                        subLabel = "Mirrors",
                        isActive = isMirrorsFolded,
                        modifier = Modifier.weight(1f)
                    ) {
                        isMirrorsFolded = !isMirrorsFolded
                    }
                    EzQuickFeatureToggle(
                        icon = Icons.Default.Lightbulb,
                        label = "ភ្លើងបំភ្លឺ",
                        subLabel = "Ambient",
                        isActive = isAmbientLightOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        isAmbientLightOn = !isAmbientLightOn
                    }
                    EzQuickFeatureToggle(
                        icon = Icons.Default.Highlight,
                        label = "ចង្កៀងមុខ",
                        subLabel = "Headlamps",
                        isActive = isAutoHeadlamps,
                        modifier = Modifier.weight(1f)
                    ) {
                        isAutoHeadlamps = !isAutoHeadlamps
                    }
                    EzQuickFeatureToggle(
                        icon = Icons.Default.ChildCare,
                        label = "សោកុមារ",
                        subLabel = "Child Lock",
                        isActive = isChildLockOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        isChildLockOn = !isChildLockOn
                    }
                }

                // Row 2: Powertrain & Hybrid Efficiency Metrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141226))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⚡", fontSize = 11.sp)
                        Column {
                            Text("ម៉ូទ័រ / Motor Power", fontSize = 8.sp, color = EzTxtMuted)
                            Text("${if (telemetry.speedKmh > 0) (telemetry.speedKmh * 0.45f).toInt() else 0} kW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentCyan)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔋", fontSize = 11.sp)
                        Column {
                            Text("កម្រិតប្រើប្រាស់ / Avg", fontSize = 8.sp, color = EzTxtMuted)
                            Text("15.6 kWh/100km", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentEmerald)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔄", fontSize = 11.sp)
                        Column {
                            Text("Regen / ចាប់ហ្វ្រាំង", fontSize = 8.sp, color = EzTxtMuted)
                            Text("High (ខ្លាំង)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentAmber)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EzQuickFeatureToggle(
    icon: ImageVector,
    label: String,
    subLabel: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) EzAccentCyan.copy(0.18f) else Color(0xFF1E1B35))
            .border(0.5.dp, if (isActive) EzAccentCyan.copy(0.6f) else EzBorderSubtle, RoundedCornerShape(7.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isActive) EzAccentCyan else EzTxtMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) EzAccentCyan else EzTxtPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                fontSize = 8.sp,
                color = EzTxtMuted,
                maxLines = 1
            )
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
            .clip(RoundedCornerShape(6.dp))
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
                RoundedCornerShape(6.dp)
            )
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
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
            .clip(RoundedCornerShape(5.dp))
            .background(if (percent > 0) EzAccentCyan.copy(0.18f) else Color(0xFF1E1B35))
            .border(0.5.dp, if (percent > 0) EzAccentCyan.copy(0.5f) else EzBorderSubtle, RoundedCornerShape(5.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (percent > 0) "$label Open" else "$label Shut",
            fontSize = 8.sp,
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
            .fillMaxSize()
            .ezGlow(EzAccentPurple, 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(EzCardBg)
            .border(1.dp, EzAccentPurple.copy(0.4f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Modal Header ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B35))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = EzTxtPrimary, modifier = Modifier.size(17.dp))
                    }

                    Column {
                        Text(
                            text = "ជ្រើសរើសពាក្យបញ្ជា",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = EzTxtPrimary
                        )
                        Text(
                            text = "CHOOSE A COMMAND",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EzTxtMuted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Glowing Voice Orb + status
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(EzAccentPurple.copy(0.7f), Color.Transparent))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Body: Category Sidebar (Left) + Adaptive Command Grid (Right) ─
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Categories Column (Smoothly scrollable, compact width)
                Column(
                    modifier = Modifier
                        .width(125.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        Pair(VoiceCategory.CLIMATE,        "អាកាសធាតុ\nCLIMATE"),
                        Pair(VoiceCategory.WINDOWS,        "បង្អួច\nWINDOWS"),
                        Pair(VoiceCategory.DOORS_TAILGATE, "ទ្វារ & គូទ\nDOORS & TRUNK"),
                        Pair(VoiceCategory.SEATS_LIGHT,    "កៅអី & ភ្លើង\nSEATS & LIGHT"),
                        Pair(VoiceCategory.MEDIA,          "កម្សាន្ត\nMEDIA"),
                        Pair(VoiceCategory.NAVIGATION,     "ការនាំផ្លូវ\nNAVIGATION"),
                        Pair(VoiceCategory.SCENES,         "ឈុតឆ្លាតវៃ\nSCENES")
                    )

                    categories.forEach { (cat, title) ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) Color.White else Color(0xFF1E1B35))
                                .border(0.5.dp, if (isSelected) Color.White else EzBorderSubtle, RoundedCornerShape(9.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D0B1A) else EzTxtSecondary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // Right Command Cards Grid (Adaptive width to prevent truncation)
                val commandsForCat = remember(selectedCategory) {
                    VoiceCommandCatalog.allCommands.filter { it.category == selectedCategory }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1730))
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onExecute() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = command.titleKm,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EzTxtPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = command.titleEn,
                    fontSize = 10.sp,
                    color = EzAccentCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (command.acceptedPhrasesKm.isNotEmpty()) {
                    Text(
                        text = "« ${command.acceptedPhrasesKm.first()} »",
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
                modifier = Modifier.size(15.dp)
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
            .fillMaxSize()
            .ezGlow(EzAccentCyan, 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(EzCardBg)
            .border(1.dp, EzAccentCyan.copy(0.4f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B35))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = EzTxtPrimary, modifier = Modifier.size(17.dp))
                    }
                    Text(
                        text = "ចំណាយ និងការថែទាំ • Spending & Service",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = EzTxtPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E1B35))
                        .border(0.5.dp, EzAccentCyan.copy(0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ODO: ${telemetry.odometerKm.toLong()} km",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = EzAccentCyan,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            // Tab Buttons (Horizontal scroll to prevent letter wrapping)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ចំណាយ • Spending",
                    "ការថែទាំ • Service",
                    "យានដ្ឋាន EV • Workshops"
                ).forEachIndexed { idx, title ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color(0xFF1E1B35))
                            .border(0.5.dp, if (isSelected) Color.White else EzBorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0D0B1A) else EzTxtSecondary,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> {
                        // Spending & Trip Efficiency
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EzMetricCard(label = "THIS WEEK SPENT", value = "124,500", unit = "KHR", accent = EzAccentEmerald, modifier = Modifier.weight(1f))
                                EzMetricCard(label = "ENERGY AVG", value = "21.0", unit = "kWh/100km", accent = EzAccentCyan, modifier = Modifier.weight(1f))
                                EzMetricCard(label = "FUEL SAVED", value = "48.5", unit = "Litres", accent = EzAccentAmber, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text("Recent Charging & Fuel Activity", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary)
                            EzMaintenanceRow("Canadia Tower DC 120kW Fast Charger", 15, 60)
                            EzMaintenanceRow("Home Smart AC Charger (Thor 3.3kW)", 8, 30)
                        }
                    }
                    1 -> {
                        // Maintenance Countdowns
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EzMaintenanceRow("Tire Rotation (បង្វិលសំបកកង់)", 8717, 10000)
                            EzMaintenanceRow("Brake Fluid Inspection (ប្រេងហ្វ្រាំង)", 14500, 20000)
                            EzMaintenanceRow("Battery Coolant Flush (ទឹកស្អំអាគុយ)", 28000, 40000)
                            EzMaintenanceRow("Cabin Air Filter (តម្រងខ្យល់កាប៊ីន)", 3200, 15000)
                        }
                    }
                    else -> {
                        // Workshops
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Service in $remainingKm km", fontSize = 10.sp, color = EzAccentCyan)
            }
            Text("$remainingKm / $totalKm km", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = EzTxtSecondary, softWrap = false)
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
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EzTxtPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, fontSize = 10.sp, color = EzTxtSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Tel: $phone", fontSize = 10.sp, color = EzAccentCyan, softWrap = false)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared Dashboard & Map Composables
// ═══════════════════════════════════════════════════════════════════════════

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
            .background(EzCardBg)
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, fontSize = 8.sp, color = EzTxtMuted, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.Bottom) {
            val valFontSize = when {
                value.length > 7 -> 15.sp
                value.length > 5 -> 17.sp
                else             -> 22.sp
            }
            Text(
                text       = value,
                fontSize   = valFontSize,
                fontWeight = FontWeight.Black,
                color      = EzTxtPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.5).sp,
                softWrap   = false,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text     = unit,
                fontSize = 9.sp,
                color    = EzTxtSecondary,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
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
                            Brush.horizontalGradient(listOf(EzAccentCyan, EzAccentEmerald))
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
            Text(label, fontSize = 12.sp, color = EzTxtSecondary)
            Text(km,    fontSize = 12.sp, color = EzTxtPrimary,   fontWeight = FontWeight.SemiBold)
            Text(kwh,   fontSize = 11.sp, color = EzTxtMuted)
        }
        if (isLive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(EzAccentCoral.copy(0.18f))
                    .border(0.5.dp, EzAccentCoral.copy(0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("End trip", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EzAccentCoral)
            }
        } else {
            Text("17.4 kWh/100km", fontSize = 9.sp, color = EzTxtMuted)
        }
    }
}

@Composable
fun EzMapPill(label: String, isOn: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isOn) EzAccentCyan.copy(0.2f) else EzCardBg)
            .border(0.5.dp, if (isOn) EzAccentCyan.copy(0.6f) else EzBorderSubtle, RoundedCornerShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text       = label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            color      = if (isOn) EzAccentCyan else EzTxtSecondary
        )
    }
}

@Composable
fun EzMapIconBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(EzCardBg)
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(9.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = EzTxtSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun EzCabinChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EzCardBg)
            .border(0.5.dp, EzBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.AcUnit, contentDescription = null, tint = EzAccentCyan, modifier = Modifier.size(14.dp))
        Text("Cabin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EzTxtPrimary)
    }
}
