package com.khmerauto.nav.ui.map

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.core.database.entity.PlaceCategory
import com.khmerauto.nav.core.database.entity.PlaceEntity
import com.khmerauto.nav.display.ClusterHudContent
import com.khmerauto.nav.display.SecondaryClusterHudManager
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.map.CambodiaOfflineMapView
import com.khmerauto.nav.map.routing.ActiveNavigationState
import com.khmerauto.nav.map.routing.ManeuverType
import com.khmerauto.nav.map.routing.NavigationRouteEngine
import com.khmerauto.nav.map.search.OfflinePlaceSearchRepository
import com.khmerauto.nav.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Interactive Full-Screen Offline Cambodia Map & Navigation Cockpit.
 *
 * Implements:
 * 1. Offline MapLibre Engine with Day/Night solar theme & MBTiles support.
 * 2. Instant sub-30ms Khmer Unicode and English POI Search with FTS.
 * 3. Verified EV Fast Charging Station Directory with live kW, plugs, and tariffs.
 * 4. Offline Turn-by-Turn Navigation with maneuver countdown and Geely Galaxy M7 SoC prediction.
 * 5. In-App Simulated HUD / Cluster Preview Window for single-screen tablet testing.
 * 6. "Add / Verify Charger" dialog allowing drivers to pin new chargers offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullMapView(
    currentHal: VehicleHalContract,
    placeRepository: OfflinePlaceSearchRepository,
    routeEngine: NavigationRouteEngine,
    hudManager: SecondaryClusterHudManager,
    onBackToCockpit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetry by currentHal.telemetryState.collectAsState()
    val navState by routeEngine.navState.collectAsState()
    val isSimulatedHudVisible by hudManager.isSimulatedHudVisible.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceEntity>>(emptyList()) }
    var allPlaces by remember { mutableStateOf<List<PlaceEntity>>(emptyList()) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var selectedPlace by remember { mutableStateOf<PlaceEntity?>(null) }
    var showAddChargerDialog by remember { mutableStateOf(false) }

    // Load initial places
    LaunchedEffect(Unit) {
        allPlaces = placeRepository.getAllEvChargers()
    }

    // Reactive POI search with sub-30ms response
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchResults = placeRepository.searchPlaces(searchQuery)
        } else {
            searchResults = emptyList()
        }
    }

    // Filter displayed map places based on category chips
    val filteredPlaces = remember(allPlaces, selectedCategoryFilter) {
        when (selectedCategoryFilter) {
            "EV_FAST" -> allPlaces.filter { it.isEvCharger && it.maxPowerKw >= 120 }
            "REPAIR" -> allPlaces.filter { it.category == PlaceCategory.REPAIR_SHOP }
            "FAVORITES" -> allPlaces.filter { it.category == PlaceCategory.FAVORITE || it.category == PlaceCategory.SAVED_HOME || it.category == PlaceCategory.SAVED_WORK }
            else -> allPlaces
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. MapLibre Offline Vector/Raster Engine
        CambodiaOfflineMapView(
            vehicleLat = telemetry.latitude,
            vehicleLng = telemetry.longitude,
            vehicleHeading = telemetry.headingDegrees,
            vehicleSpeedKmh = telemetry.speedKmh,
            places = filteredPlaces,
            navigationState = navState,
            onPlaceSelected = { place ->
                selectedPlace = place
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Search & Filter Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back to Cockpit Button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onBackToCockpit() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Khmer / English Search Bar
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = GeelyAccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "ស្វែងរកទីកន្លែង ឬ ស្ថានីយសាកថ្ម / Search EV Chargers & Places...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Category Filter Pills
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    CategoryFilterChip(
                        label = "ទាំងអស់ (All Places)",
                        isSelected = selectedCategoryFilter == "ALL",
                        onClick = { selectedCategoryFilter = "ALL" }
                    )
                }
                item {
                    CategoryFilterChip(
                        label = "⚡ 120kW+ Fast Chargers",
                        isSelected = selectedCategoryFilter == "EV_FAST",
                        onClick = { selectedCategoryFilter = "EV_FAST" }
                    )
                }
                item {
                    CategoryFilterChip(
                        label = "🔧 យានដ្ឋានជួសជុល EV (Repair)",
                        isSelected = selectedCategoryFilter == "REPAIR",
                        onClick = {
                            selectedCategoryFilter = "REPAIR"
                            coroutineScope.launch {
                                allPlaces = placeRepository.getPlacesByCategory(PlaceCategory.REPAIR_SHOP)
                            }
                        }
                    )
                }
                item {
                    CategoryFilterChip(
                        label = "⭐ ទីតាំងរក្សាទុក (Favorites)",
                        isSelected = selectedCategoryFilter == "FAVORITES",
                        onClick = { selectedCategoryFilter = "FAVORITES" }
                    )
                }
            }

            // Sub-30ms Instant Search Suggestions Dropdown
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 8.dp
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        items(searchResults) { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedPlace = place
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (place.isEvCharger) GeelyEmeraldGreen.copy(alpha = 0.2f)
                                            else GeelyAccentCyan.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (place.isEvCharger) Icons.Default.EvStation else Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (place.isEvCharger) GeelyEmeraldGreen else GeelyAccentCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.nameKm,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = place.nameEn,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (place.isEvCharger) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GeelyEmeraldGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${place.maxPowerKw} kW",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GeelyEmeraldGreen,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Turn-by-Turn Navigation Banner (When active)
        if (navState.isNavigating) {
            TurnByTurnGuidanceBanner(
                navState = navState,
                onStopNavigation = { routeEngine.stopNavigation() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 110.dp, start = 16.dp)
            )
        }

        // 4. Floating Action Controls (Right side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add Charger FAB
            FloatingActionButton(
                onClick = { showAddChargerDialog = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = GeelyEmeraldGreen,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.AddLocation, contentDescription = "Add Charger")
            }

            // HUD Preview Window Toggle FAB
            FloatingActionButton(
                onClick = { hudManager.toggleSimulatedHud() },
                containerColor = if (isSimulatedHudVisible) GeelyAccentCyan else MaterialTheme.colorScheme.surface,
                contentColor = if (isSimulatedHudVisible) GeelyNavyDark else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.ViewCarousel, contentDescription = "Simulate HUD")
            }
        }

        // 5. Selected Place / Charger Details Bottom Card
        selectedPlace?.let { place ->
            PlaceDetailBottomCard(
                place = place,
                isNavigating = navState.isNavigating,
                onStartNavigation = {
                    routeEngine.startNavigation(
                        destination = place,
                        currentBatteryPercent = telemetry.batteryPercent,
                        startLat = telemetry.latitude,
                        startLng = telemetry.longitude
                    )
                    selectedPlace = null
                },
                onClose = { selectedPlace = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        // 6. In-App Simulated HUD / Cluster Preview Window (Single-Screen Tablet Testing)
        if (isSimulatedHudVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .width(420.dp)
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = Color.Black,
                border = BorderStroke(1.5.dp, GeelyAccentCyan),
                shadowElevation = 12.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ClusterHudContent(navState = navState)

                    // Minimize / Close preview button
                    IconButton(
                        onClick = { hudManager.setSimulatedHudVisible(false) },
                        modifier = Modifier.align(Alignment.TopEnd).size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close HUD Preview",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 7. Add / Verify Charger Dialog
        if (showAddChargerDialog) {
            AddChargerDialog(
                currentLat = telemetry.latitude,
                currentLng = telemetry.longitude,
                onDismiss = { showAddChargerDialog = false },
                onConfirm = { nameKm, nameEn, lat, lng, op, kw, conn, total, price ->
                    coroutineScope.launch {
                        placeRepository.addNewCharger(
                            nameKm = nameKm,
                            nameEn = nameEn,
                            latitude = lat,
                            longitude = lng,
                            operator = op,
                            maxPowerKw = kw,
                            connectorTypes = conn,
                            totalPlugs = total,
                            pricePerKwhKhr = price
                        )
                        allPlaces = placeRepository.getAllEvChargers()
                        showAddChargerDialog = false
                        Toast.makeText(
                            context,
                            "បានរក្សាទុកស្ថានីយសាកថ្មដោយជោគជ័យ! / Charger pinned offline!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
}

@Composable
fun CategoryFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) GeelyAccentCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) GeelyAccentCyan else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) GeelyAccentCyan else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TurnByTurnGuidanceBanner(
    navState: ActiveNavigationState,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(380.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.5.dp, GeelyAccentCyan),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            val maneuver = navState.currentManeuver
            val turnIcon = when (maneuver?.maneuverType) {
                ManeuverType.TURN_LEFT -> Icons.Default.TurnLeft
                ManeuverType.TURN_RIGHT -> Icons.Default.TurnRight
                ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
                ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
                ManeuverType.U_TURN -> Icons.Default.UturnLeft
                ManeuverType.ROUNDABOUT -> Icons.Default.RotateRight
                ManeuverType.ARRIVED -> Icons.Default.Place
                else -> Icons.Default.Straight
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(GeelyAccentCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = turnIcon,
                        contentDescription = "Turn",
                        tint = GeelyAccentCyan,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${maneuver?.distanceMeters ?: 300} m",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = maneuver?.roadNameKm ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = maneuver?.roadNameEn ?: "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onStopNavigation) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop Navigation",
                        tint = GeelyCoralRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Trip Progress Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ETA ${navState.etaString}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeelyEmeraldGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${String.format("%.1f km", navState.remainingDistanceKm)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Geely Galaxy M7 Arrival Battery %
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GeelyAmberGold.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Arrival SoC",
                            tint = GeelyAmberGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SoC: ${navState.estimatedArrivalBatteryPercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeelyAmberGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceDetailBottomCard(
    place: PlaceEntity,
    isNavigating: Boolean,
    onStartNavigation: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(0.6f)
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            // Header Row: Title and Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.nameKm,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = place.nameEn,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${place.addressKm} • ${place.city}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // EV Charger Specs Row (if applicable)
            if (place.isEvCharger) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Operator Badge
                    SpecBadge(
                        label = place.operator,
                        color = GeelyAccentCyan
                    )
                    // Power Rating Badge
                    SpecBadge(
                        label = "⚡ ${place.maxPowerKw} kW DC",
                        color = GeelyEmeraldGreen
                    )
                    // Plugs Available
                    SpecBadge(
                        label = "🟢 ${place.availablePlugs}/${place.totalPlugs} Plugs",
                        color = GeelyEmeraldGreen
                    )
                    // Tariff Badge
                    SpecBadge(
                        label = "៛ ${place.pricePerKwhKhr} / kWh",
                        color = GeelyAmberGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onClose) {
                    Text("បិទ / Close")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onStartNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Navigate",
                        tint = GeelyNavyDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNavigating) "ប្តូរគោលដៅ (Reroute)" else "ចាប់ផ្តើមរុករក (Navigate)",
                        fontWeight = FontWeight.Bold,
                        color = GeelyNavyDark
                    )
                }
            }
        }
    }
}

@Composable
fun SpecBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
