package com.khmerauto.nav.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.hal.*
import com.khmerauto.nav.hal.geely.PropertyDataType
import com.khmerauto.nav.hal.sniffer.SnifferPropertyItem
import com.khmerauto.nav.hal.sniffer.VhalSnifferEngine
import com.khmerauto.nav.hal.wifi.CarHalWebSocketServer
import com.khmerauto.nav.hal.wifi.RemoteWifiHalAdapter
import com.khmerauto.nav.hal.wifi.WifiConnectionState
import com.khmerauto.nav.ui.components.QrCodeImage
import com.khmerauto.nav.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase1DiagnosticScreen(
    currentHal: VehicleHalContract,
    runtimeMode: RuntimeMode,
    onModeSelected: (RuntimeMode) -> Unit,
    snifferEngine: VhalSnifferEngine,
    onIpUpdated: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetry by currentHal.telemetryState.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("CABIN CONTROLS", "GEELY M7 SNIFFER", "RAW INJECTOR & WI-FI")

    // State for IP input dialog
    var showIpDialog by remember { mutableStateOf(false) }
    var inputIpText by remember { mutableStateOf("192.168.43.1") }

    // Dialog state for "Assign to Action"
    var propertyToAssign by remember { mutableStateOf<SnifferPropertyItem?>(null) }

    Scaffold(
        containerColor = GeelyNavyDark,
        topBar = {
            Surface(
                color = GeelySurfaceDark,
                border = BorderStroke(1.dp, GeelyCardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (telemetry.speedKmh > 0f) GeelyEmeraldGreen else GeelyAccentCyan)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "VDrive • 2026 Geely Galaxy M7",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = GeelyTextPrimary
                                )
                                Text(
                                    text = "Flyme Auto / ECARX Dual-Mode Diagnostic",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GeelyTextSecondary
                                )
                            }
                        }

                        // Runtime Mode Switcher Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RuntimeMode.values().forEach { mode ->
                                val isSelected = mode == runtimeMode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onModeSelected(mode) },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                RuntimeMode.SIMULATOR -> "SIMULATOR"
                                                RuntimeMode.TABLET_WIFI_CLIENT -> "TABLET REMOTE"
                                                RuntimeMode.CAR_NATIVE_HOST -> "CAR HOST (M7)"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GeelyAccentCyan,
                                        selectedLabelColor = GeelyNavyDark,
                                        containerColor = GeelyCardDark,
                                        labelColor = GeelyTextSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Mode Status Banner
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GeelyCardDark)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (runtimeMode) {
                            RuntimeMode.SIMULATOR -> {
                                Text(
                                    text = "● MODE: Realistic M7 PHEV Physics Simulator (Phnom Penh Norodom Blvd)",
                                    fontSize = 12.sp,
                                    color = GeelyAccentCyan
                                )
                            }
                            RuntimeMode.TABLET_WIFI_CLIENT -> {
                                val remoteAdapter = currentHal as? RemoteWifiHalAdapter
                                val wifiStatus by (remoteAdapter?.connectionState?.collectAsState()
                                    ?: remember { mutableStateOf(WifiConnectionState.CONNECTED) })

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "● TABLET CLIENT: ws://${remoteAdapter?.carIp ?: "192.168.43.1"}:8888/ws/hal  [$wifiStatus]",
                                        fontSize = 12.sp,
                                        color = if (wifiStatus == WifiConnectionState.CONNECTED) GeelyEmeraldGreen else GeelyCoralRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { showIpDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Change IP", fontSize = 11.sp, color = GeelyAccentCyan)
                                    }
                                }
                            }
                            RuntimeMode.CAR_NATIVE_HOST -> {
                                val localIp = remember { CarHalWebSocketServer.getLocalIpAddress() }
                                Text(
                                    text = "● CAR HOST: Native Geely Galaxy M7 (ECARX AdaptAPI) • Server: ws://$localIp:8888/ws/hal",
                                    fontSize = 12.sp,
                                    color = GeelyEmeraldGreen
                                )
                            }
                        }
                    }

                    // Navigation Tabs
                    Spacer(modifier = Modifier.height(6.dp))
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = GeelyAccentCyan,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> CabinControlsTab(telemetry = telemetry, hal = currentHal)
                1 -> GeelySnifferTab(
                    snifferEngine = snifferEngine,
                    onAssignClicked = { propertyToAssign = it }
                )
                2 -> RawInjectorTab(
                    hal = currentHal,
                    runtimeMode = runtimeMode
                )
            }
        }
    }

    // IP Input Dialog
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Connect to Geely Galaxy M7 Hotspot", color = GeelyTextPrimary) },
            text = {
                Column {
                    Text(
                        "Enter the IP address of the Geely Galaxy M7 head unit (defaults to Wi-Fi hotspot gateway 192.168.43.1):",
                        fontSize = 13.sp,
                        color = GeelyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputIpText,
                        onValueChange = { inputIpText = it },
                        label = { Text("Car IP Address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GeelyAccentCyan,
                            unfocusedBorderColor = GeelyCardBorder,
                            focusedTextColor = GeelyTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onIpUpdated?.invoke(inputIpText)
                        (currentHal as? RemoteWifiHalAdapter)?.updateHost(inputIpText)
                        showIpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
                ) {
                    Text("Connect", color = GeelyNavyDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel", color = GeelyTextSecondary)
                }
            },
            containerColor = GeelySurfaceDark
        )
    }

    // Assign to Action Dialog
    propertyToAssign?.let { item ->
        AssignActionDialog(
            item = item,
            onDismiss = { propertyToAssign = null },
            onAssign = { actionName, dataType ->
                val success = snifferEngine.assignToAction(item.propId, item.areaId, actionName, dataType)
                if (success) {
                    Toast.makeText(context, "Saved '$actionName' into geely_m7_vhal_map.json!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed saving mapping", Toast.LENGTH_SHORT).show()
                }
                propertyToAssign = null
            }
        )
    }
}

// =========================================================================
// TAB 1: Cabin & Vehicle Controls
// =========================================================================

@Composable
fun CabinControlsTab(
    telemetry: VehicleTelemetryState,
    hal: VehicleHalContract
) {
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Powertrain & Telemetry Dashboard Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "GEELY GALAXY M7 POWERTRAIN TELEMETRY",
                        style = MaterialTheme.typography.titleMedium,
                        color = GeelyAccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryMetric("BATTERY", "${telemetry.batteryPercent}%", "${telemetry.usableKwh} / ${telemetry.maxCapacityKwh} kWh")
                        TelemetryMetric("EV RANGE", "${telemetry.evRangeKm} km", "CLTC 218 km")
                        TelemetryMetric("FUEL RANGE", "${telemetry.fuelRangeKm} km", "Combined 1300+ km")
                        TelemetryMetric("SPEED", "${telemetry.speedKmh.toInt()} km/h", "Gear ${telemetry.gear.name}")
                        TelemetryMetric("ODOMETER", String.format(Locale.US, "%.1f km", telemetry.odometerKm), "Hardware")
                    }
                }
            }
        }

        // 4-Wheel TPMS Visualizer Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "4-WHEEL TPMS TIRE PRESSURE & TEMPERATURE",
                        style = MaterialTheme.typography.titleMedium,
                        color = GeelyEmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TpmsWheel("FRONT LEFT", telemetry.tpmsState.flBar, telemetry.tpmsState.flTempC)
                        TpmsWheel("FRONT RIGHT", telemetry.tpmsState.frBar, telemetry.tpmsState.frTempC)
                        TpmsWheel("REAR LEFT", telemetry.tpmsState.rlBar, telemetry.tpmsState.rlTempC)
                        TpmsWheel("REAR RIGHT", telemetry.tpmsState.rrBar, telemetry.tpmsState.rrTempC)
                    }
                }
            }
        }

        // Dual-Zone Climate Control Card
        item {
            val hvac = telemetry.hvacState
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "DUAL-ZONE CLIMATE CONTROL",
                            style = MaterialTheme.typography.titleMedium,
                            color = GeelyAccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("POWER", fontSize = 12.sp, color = GeelyTextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = hvac.isPowerOn,
                                onCheckedChange = { coroutineScope.launch { hal.setHvacPower(it) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = GeelyNavyDark,
                                    checkedTrackColor = GeelyEmeraldGreen
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Temperature Controls (Driver & Passenger)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Driver Temp
                        TemperatureDial(
                            label = "DRIVER",
                            tempC = hvac.driverTempC,
                            onTempChanged = { coroutineScope.launch { hal.setTemperature(Zone.DRIVER, it) } },
                            modifier = Modifier.weight(1f)
                        )
                        // Passenger Temp
                        TemperatureDial(
                            label = "PASSENGER",
                            tempC = hvac.passengerTempC,
                            onTempChanged = { coroutineScope.launch { hal.setTemperature(Zone.PASSENGER, it) } },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fan Speed Slider (1..7) with onValueChangeFinished Debouncing
                    var fanSliderPos by remember(hvac.fanSpeed) { mutableFloatStateOf(hvac.fanSpeed.toFloat()) }
                    Text("Blower Fan Speed: ${fanSliderPos.toInt()} / 7", fontSize = 13.sp, color = GeelyTextSecondary)
                    Slider(
                        value = fanSliderPos,
                        onValueChange = { fanSliderPos = it },
                        onValueChangeFinished = { coroutineScope.launch { hal.setFanSpeed(fanSliderPos.toInt()) } },
                        valueRange = 1f..7f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = GeelyAccentCyan,
                            activeTrackColor = GeelyAccentCyan,
                            inactiveTrackColor = GeelySurfaceDark
                        ),
                        modifier = Modifier.height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // HVAC Modes Quick Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeToggleButton("A/C", hvac.isAcOn, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setAcMode(ac = !hvac.isAcOn) }
                        }
                        ModeToggleButton("AUTO", hvac.isAutoOn, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setAcMode(auto = !hvac.isAutoOn) }
                        }
                        ModeToggleButton("MAX A/C", hvac.isMaxAcOn, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setAcMode(maxAc = !hvac.isMaxAcOn) }
                        }
                        ModeToggleButton("RECIRC", hvac.isRecircOn, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setAcMode(recirc = !hvac.isRecircOn) }
                        }
                        ModeToggleButton("DEFROST", hvac.isFrontDefrostOn, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setAcMode(frontDefrost = !hvac.isFrontDefrostOn) }
                        }
                    }
                }
            }
        }

        // Electric Windows, Sunroof & Shade Card
        item {
            val cabin = telemetry.cabinState
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "WINDOWS & PANORAMIC SUNROOF",
                        style = MaterialTheme.typography.titleMedium,
                        color = GeelyAccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // All Windows Quick Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { coroutineScope.launch { hal.setWindowPosition(WindowTarget.ALL, 100) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GeelySurfaceDark)
                        ) {
                            Text("Open All (100%)", fontSize = 11.sp, color = GeelyTextPrimary)
                        }
                        Button(
                            onClick = { coroutineScope.launch { hal.setWindowPosition(WindowTarget.ALL, 20) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GeelySurfaceDark)
                        ) {
                            Text("Vent All (20%)", fontSize = 11.sp, color = GeelyTextPrimary)
                        }
                        Button(
                            onClick = { coroutineScope.launch { hal.setWindowPosition(WindowTarget.ALL, 0) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GeelySurfaceDark)
                        ) {
                            Text("Close All (0%)", fontSize = 11.sp, color = GeelyTextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Individual Windows
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WindowSlider("Front Left", cabin.windowFlPercent, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setWindowPosition(WindowTarget.FRONT_LEFT, it) }
                        }
                        WindowSlider("Front Right", cabin.windowFrPercent, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setWindowPosition(WindowTarget.FRONT_RIGHT, it) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WindowSlider("Rear Left", cabin.windowRlPercent, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setWindowPosition(WindowTarget.REAR_LEFT, it) }
                        }
                        WindowSlider("Rear Right", cabin.windowRrPercent, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setWindowPosition(WindowTarget.REAR_RIGHT, it) }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sunroof & Sunshade
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WindowSlider("Sunroof Glass", cabin.sunroofPercent, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setSunroofAndShade(sunroofPercent = it) }
                        }
                        WindowSlider("Electric Shade", cabin.roofShadePercent, Modifier.weight(1f)) {
                            coroutineScope.launch { hal.setSunroofAndShade(shadePercent = it) }
                        }
                    }
                }
            }
        }

        // Doors, Powered Tailgate & Seat Climate Card
        item {
            val cabin = telemetry.cabinState
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CABIN ACCESS & SEAT CLIMATE",
                        style = MaterialTheme.typography.titleMedium,
                        color = GeelyAccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Central Lock
                        Button(
                            onClick = { coroutineScope.launch { hal.setDoorLocks(!cabin.isDoorsLocked) } },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (cabin.isDoorsLocked) GeelyCoralRed.copy(alpha = 0.3f) else GeelyEmeraldGreen.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, if (cabin.isDoorsLocked) GeelyCoralRed else GeelyEmeraldGreen)
                        ) {
                            Text(
                                if (cabin.isDoorsLocked) "DOORS: LOCKED" else "DOORS: UNLOCKED",
                                fontWeight = FontWeight.Bold,
                                color = GeelyTextPrimary
                            )
                        }

                        // Smart Tailgate with Drive-State Safety Interlock
                        Button(
                            onClick = {
                                val willOpen = !cabin.isTailgateOpen
                                if (willOpen && (telemetry.gear != GearState.P || telemetry.speedKmh > 0.0f)) {
                                    Toast.makeText(
                                        context,
                                        "មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! / Cannot open tailgate while vehicle is in motion or not in Park!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    coroutineScope.launch {
                                        val res = hal.setTailgate(willOpen)
                                        if (res.isFailure) {
                                            Toast.makeText(
                                                context,
                                                res.exceptionOrNull()?.message ?: "Tailgate command failed",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (cabin.isTailgateOpen) GeelyAmberGold.copy(alpha = 0.3f) else GeelySurfaceDark
                            ),
                            border = BorderStroke(1.dp, if (cabin.isTailgateOpen) GeelyAmberGold else GeelyCardBorder)
                        ) {
                            Text(
                                if (cabin.isTailgateOpen) "TAILGATE: OPEN" else "TAILGATE: CLOSED",
                                fontWeight = FontWeight.Bold,
                                color = GeelyTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Front Seats: Ventilation & Heating
                    Text("Front Seat Ventilation & Heating (0..3 Levels)", fontSize = 13.sp, color = GeelyTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SeatClimateBlock(
                            label = "Driver Seat",
                            vent = cabin.driverSeatVentLevel,
                            heat = cabin.driverSeatHeatLevel,
                            onVentChange = { coroutineScope.launch { hal.setSeatClimate(SeatTarget.DRIVER, ventLevel = it) } },
                            onHeatChange = { coroutineScope.launch { hal.setSeatClimate(SeatTarget.DRIVER, heatLevel = it) } },
                            modifier = Modifier.weight(1f)
                        )
                        SeatClimateBlock(
                            label = "Passenger Seat",
                            vent = cabin.passengerSeatVentLevel,
                            heat = cabin.passengerSeatHeatLevel,
                            onVentChange = { coroutineScope.launch { hal.setSeatClimate(SeatTarget.PASSENGER, ventLevel = it) } },
                            onHeatChange = { coroutineScope.launch { hal.setSeatClimate(SeatTarget.PASSENGER, heatLevel = it) } },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ambient Lighting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("256-Color Ambient Light", fontSize = 13.sp, color = GeelyTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("#00E5FF", "#00E676", "#FF5252", "#FFD600", "#D500F9", "#80D8FF").forEach { colorHex ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(colorHex)))
                                        .clickable { coroutineScope.launch { hal.setAmbientLight(true, colorHex) } }
                                        .border(
                                            2.dp,
                                            if (cabin.ambientLightColorHex == colorHex) Color.White else Color.Transparent,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: Live Geely Galaxy M7 VHAL Property Sniffer
// =========================================================================

@Composable
fun GeelySnifferTab(
    snifferEngine: VhalSnifferEngine,
    onAssignClicked: (SnifferPropertyItem) -> Unit
) {
    val context = LocalContext.current
    val properties by snifferEngine.propertiesList.collectAsState()
    val isSniffing by snifferEngine.isSniffing.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(properties, searchQuery) {
        if (searchQuery.isBlank()) properties
        else properties.filter {
            it.propIdHex.contains(searchQuery, ignoreCase = true) ||
                    it.propId.toString().contains(searchQuery) ||
                    (it.assignedAction?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Sniffer Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "GEELY GALAXY M7 PROPERTY SNIFFER",
                    style = MaterialTheme.typography.titleMedium,
                    color = GeelyEmeraldGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Press buttons on the Geely M7 head unit/steering wheel to capture changes in real time.",
                    fontSize = 12.sp,
                    color = GeelyTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { snifferEngine.scanAllCarProperties(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = GeelySurfaceDark)
                ) {
                    Text("Scan All", fontSize = 12.sp, color = GeelyAccentCyan)
                }
                Button(
                    onClick = {
                        if (isSniffing) snifferEngine.stopSniffing() else snifferEngine.startSniffing()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSniffing) GeelyCoralRed.copy(alpha = 0.3f) else GeelyEmeraldGreen.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (isSniffing) GeelyCoralRed else GeelyEmeraldGreen)
                ) {
                    Text(if (isSniffing) "PAUSE" else "RESUME", fontSize = 12.sp, color = GeelyTextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter by Property Hex, Decimal, or Action Name...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GeelyAccentCyan,
                unfocusedBorderColor = GeelyCardBorder,
                focusedTextColor = GeelyTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Property List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredList, key = { "${it.propId}_${it.areaId}" }) { item ->
                SnifferPropertyRow(item = item, onAssign = { onAssignClicked(item) })
            }
        }
    }
}

@Composable
fun SnifferPropertyRow(
    item: SnifferPropertyItem,
    onAssign: () -> Unit
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (item.isRecentlyChanged) GlowingGreenGlow.copy(alpha = 0.25f) else GeelyCardDark,
        animationSpec = tween(durationMillis = 400),
        label = "snifferGlow"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (item.isRecentlyChanged) GlowingGreenGlow else GeelyCardBorder,
        animationSpec = tween(durationMillis = 400),
        label = "snifferBorder"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = animatedBgColor,
        border = BorderStroke(1.dp, animatedBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.propIdHex,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (item.isRecentlyChanged) GlowingGreenGlow else GeelyAccentCyan,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${item.propId}) Area: ${item.areaId}",
                        fontSize = 12.sp,
                        color = GeelyTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeelySurfaceDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(item.source, fontSize = 10.sp, color = GeelyTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VAL: ${item.currentValue}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GeelyTextPrimary
                    )
                    item.previousValue?.let { prev ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(prev: $prev)",
                            fontSize = 12.sp,
                            color = GeelyTextMuted
                        )
                    }
                }

                item.assignedAction?.let { action ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "★ Assigned Action: $action",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeelyAmberGold
                    )
                }
            }

            Button(
                onClick = onAssign,
                colors = ButtonDefaults.buttonColors(containerColor = GeelySurfaceDark),
                border = BorderStroke(1.dp, GeelyAccentCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Assign to Action", fontSize = 11.sp, color = GeelyAccentCyan)
            }
        }
    }
}

// =========================================================================
// TAB 3: Raw Injector & Wi-Fi Network Setup
// =========================================================================

@Composable
fun RawInjectorTab(
    hal: VehicleHalContract,
    runtimeMode: RuntimeMode
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var propInput by remember { mutableStateOf("0x15600503") }
    var areaInput by remember { mutableStateOf("1") }
    var valueInput by remember { mutableStateOf("22.5") }
    var selectedType by remember { mutableStateOf("FLOAT") }
    var lastExecutionResult by remember { mutableStateOf<String?>(null) }

    val localIp = remember { CarHalWebSocketServer.getLocalIpAddress() }
    val pairingUrl = "ws://$localIp:8888/ws/hal"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Wi-Fi Pairing Info Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "GEELY GALAXY M7 WI-FI HOTSPOT PAIRING",
                            style = MaterialTheme.typography.titleMedium,
                            color = GeelyAccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "1. Connect external Android Tablet to Geely Galaxy M7 Wi-Fi hotspot.",
                            fontSize = 12.sp,
                            color = GeelyTextSecondary
                        )
                        Text(
                            "2. Scan QR code or enter Car IP on the Tablet client.",
                            fontSize = 12.sp,
                            color = GeelyTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Active Bridge URL: $pairingUrl",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = GeelyEmeraldGreen
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    QrCodeImage(content = pairingUrl, sizeDp = 120.dp)
                }
            }
        }

        // Raw Property Injector
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = GeelyCardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "RAW HARDWARE PROPERTY INJECTOR",
                        style = MaterialTheme.typography.titleMedium,
                        color = GeelyAccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Directly sends raw commands through the 4-tier chain (ECARX -> AOSP -> Shell cmd car_service).",
                        fontSize = 12.sp,
                        color = GeelyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = propInput,
                            onValueChange = { propInput = it },
                            label = { Text("Property ID (Hex or Dec)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = areaInput,
                            onValueChange = { areaInput = it },
                            label = { Text("Area ID") },
                            modifier = Modifier.weight(0.6f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = valueInput,
                            onValueChange = { valueInput = it },
                            label = { Text("Value") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        // Data type buttons
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("INT", "FLOAT", "BOOLEAN").forEach { t ->
                                Button(
                                    onClick = { selectedType = t },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedType == t) GeelyAccentCyan else GeelySurfaceDark
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(56.dp)
                                ) {
                                    Text(
                                        t,
                                        fontSize = 11.sp,
                                        color = if (selectedType == t) GeelyNavyDark else GeelyTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val propId = if (propInput.startsWith("0x", ignoreCase = true)) {
                                        java.lang.Long.parseLong(propInput.substring(2), 16).toInt()
                                    } else {
                                        propInput.toInt()
                                    }
                                    val areaId = areaInput.toIntOrNull() ?: 0
                                    val value: Any = when (selectedType) {
                                        "FLOAT" -> valueInput.toFloat()
                                        "BOOLEAN" -> valueInput.toBoolean()
                                        else -> valueInput.toInt()
                                    }

                                    val result = hal.executeRawProperty(propId, areaId, value)
                                    if (result.isSuccess) {
                                        lastExecutionResult = "SUCCESS: Property executed successfully into Geely M7 VHAL!"
                                    } else {
                                        lastExecutionResult = "FAILED: ${result.exceptionOrNull()?.message}"
                                    }
                                } catch (e: Exception) {
                                    lastExecutionResult = "ERROR: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
                    ) {
                        Text("EXECUTE PROPERTY ON CAR", fontWeight = FontWeight.Bold, color = GeelyNavyDark)
                    }

                    lastExecutionResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = res,
                            fontSize = 13.sp,
                            color = if (res.startsWith("SUCCESS")) GeelyEmeraldGreen else GeelyCoralRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// Dialog & Helper Composables
// =========================================================================

@Composable
fun AssignActionDialog(
    item: SnifferPropertyItem,
    onDismiss: () -> Unit,
    onAssign: (actionName: String, dataType: PropertyDataType) -> Unit
) {
    val availableActions = listOf(
        "TEMP_DRIVER", "TEMP_PASSENGER", "FAN_SPEED", "HVAC_POWER",
        "AC_SWITCH", "AUTO_SWITCH", "RECIRC_SWITCH", "DEFROST_FRONT",
        "WINDOW_FL", "WINDOW_FR", "WINDOW_RL", "WINDOW_RR",
        "SUNROOF", "SUNSHADE", "CENTRAL_LOCK", "TAILGATE",
        "SEAT_VENT_FL", "SEAT_VENT_FR", "SEAT_HEAT_FL", "AMBIENT_LIGHT_SWITCH",
        "SPEED", "ODOMETER", "BATTERY_LEVEL", "GEAR", "TPMS_PRESSURE_FL"
    )

    var selectedAction by remember { mutableStateOf(item.assignedAction ?: availableActions.first()) }
    var selectedDataType by remember { mutableStateOf(item.dataType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Property ${item.propIdHex}", color = GeelyTextPrimary) },
        text = {
            Column {
                Text(
                    "Bind property 0x${Integer.toHexString(item.propId).uppercase()} (Area ${item.areaId}) directly into geely_m7_vhal_map.json:",
                    fontSize = 13.sp,
                    color = GeelyTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Target Action:", fontSize = 12.sp, color = GeelyAccentCyan)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(availableActions) { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAction = action }
                                .background(if (selectedAction == action) GeelyAccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAction == action,
                                onClick = { selectedAction = action },
                                colors = RadioButtonDefaults.colors(selectedColor = GeelyAccentCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(action, color = GeelyTextPrimary, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Data Type:", fontSize = 12.sp, color = GeelyAccentCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PropertyDataType.values().forEach { dt ->
                        FilterChip(
                            selected = selectedDataType == dt,
                            onClick = { selectedDataType = dt },
                            label = { Text(dt.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAssign(selectedAction, selectedDataType) },
                colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
            ) {
                Text("Save Mapping", color = GeelyNavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GeelyTextSecondary)
            }
        },
        containerColor = GeelySurfaceDark
    )
}

@Composable
fun TelemetryMetric(label: String, primary: String, secondary: String) {
    Column {
        Text(label, fontSize = 11.sp, color = GeelyTextSecondary)
        Text(primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GeelyTextPrimary)
        Text(secondary, fontSize = 11.sp, color = GeelyTextMuted)
    }
}

@Composable
fun TpmsWheel(wheelName: String, bar: Float, tempC: Int) {
    Surface(
        color = GeelySurfaceDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GeelyCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(wheelName, fontSize = 11.sp, color = GeelyTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                String.format(Locale.US, "%.2f Bar", bar),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (bar in 2.2f..2.6f) GeelyEmeraldGreen else GeelyCoralRed
            )
            Text("$tempC °C", fontSize = 12.sp, color = GeelyTextSecondary)
        }
    }
}

@Composable
fun TemperatureDial(
    label: String,
    tempC: Float,
    onTempChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = GeelySurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, GeelyCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = GeelyTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onTempChanged(tempC - 0.5f) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("-", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GeelyAccentCyan)
                }
                Text(
                    text = String.format(Locale.US, "%.1f°C", tempC),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = GeelyTextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onTempChanged(tempC + 0.5f) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GeelyCoralRed)
                }
            }
        }
    }
}

@Composable
fun ModeToggleButton(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) GeelyAccentCyan else GeelySurfaceDark
        ),
        border = BorderStroke(1.dp, if (isActive) GeelyAccentCyan else GeelyCardBorder)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) GeelyNavyDark else GeelyTextPrimary
        )
    }
}

@Composable
fun WindowSlider(
    label: String,
    percent: Int,
    modifier: Modifier = Modifier,
    onPercentChanged: (Int) -> Unit
) {
    var localPos by remember(percent) { mutableFloatStateOf(percent.toFloat()) }

    Surface(
        modifier = modifier,
        color = GeelySurfaceDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GeelyCardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 12.sp, color = GeelyTextSecondary)
                Text("${localPos.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeelyTextPrimary)
            }
            Slider(
                value = localPos,
                onValueChange = { localPos = it },
                onValueChangeFinished = { onPercentChanged(localPos.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = GeelyAccentCyan,
                    activeTrackColor = GeelyAccentCyan,
                    inactiveTrackColor = GeelyCardDark
                )
            )
        }
    }
}

@Composable
fun SeatClimateBlock(
    label: String,
    vent: Int,
    heat: Int,
    onVentChange: (Int) -> Unit,
    onHeatChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = GeelySurfaceDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GeelyCardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 12.sp, color = GeelyTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { onVentChange((vent + 1) % 4) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vent > 0) GeelyAccentCyan.copy(alpha = 0.25f) else GeelyCardDark
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Vent: $vent", fontSize = 11.sp, color = if (vent > 0) GeelyAccentCyan else GeelyTextSecondary)
                }
                Button(
                    onClick = { onHeatChange((heat + 1) % 4) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (heat > 0) GeelyCoralRed.copy(alpha = 0.25f) else GeelyCardDark
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Heat: $heat", fontSize = 11.sp, color = if (heat > 0) GeelyCoralRed else GeelyTextSecondary)
                }
            }
        }
    }
}
