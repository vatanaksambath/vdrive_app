package com.khmerauto.nav.ui.cabin

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.core.i18n.AppStrings
import com.khmerauto.nav.core.i18n.LocalAppStrings
import com.khmerauto.nav.hal.*
import com.khmerauto.nav.ui.components.VehicleCanvasDiagram
import com.khmerauto.nav.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Full Automotive Cabin & Climate Control Screen for 2026 Geely Galaxy M7 (Flyme Auto).
 *
 * Enforces all Automotive Safety Guardrails:
 * - All sliders (Fan, Windows, Sunroof, Shade) write only in onValueChangeFinished
 * - 200ms rate-limited hardware dispatch
 * - Tailgate Drive-State Safety Interlock (checks Gear == P && Speed == 0)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CabinControlScreen(
    currentHal: VehicleHalContract,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetry by currentHal.telemetryState.collectAsState()
    val strings = LocalAppStrings.current

    val hvac = telemetry.hvacState
    val cabin = telemetry.cabinState
    val tpms = telemetry.tpmsState

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.cabin,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Master Power Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.power, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = hvac.isPowerOn,
                            onCheckedChange = { coroutineScope.launch { currentHal.setHvacPower(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GeelyNavyDark,
                                checkedTrackColor = GeelyEmeraldGreen
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Dual-Zone Interactive Temperature & Climate Panel
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.climateControl,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dual Temperature Dials (Driver & Passenger)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AutomotiveTempGauge(
                                label = strings.driver,
                                tempC = hvac.driverTempC,
                                onTempChange = { coroutineScope.launch { currentHal.setTemperature(Zone.DRIVER, it) } },
                                modifier = Modifier.weight(1f)
                            )
                            AutomotiveTempGauge(
                                label = strings.passenger,
                                tempC = hvac.passengerTempC,
                                onTempChange = { coroutineScope.launch { currentHal.setTemperature(Zone.PASSENGER, it) } },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 7-Speed Blower Fan Bar (Debounced onValueChangeFinished)
                        var fanSliderPos by remember(hvac.fanSpeed) { mutableFloatStateOf(hvac.fanSpeed.toFloat()) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${strings.fanSpeed}: ${fanSliderPos.toInt()} / 7",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = fanSliderPos,
                            onValueChange = { fanSliderPos = it },
                            onValueChangeFinished = { coroutineScope.launch { currentHal.setFanSpeed(fanSliderPos.toInt()) } },
                            valueRange = 1f..7f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.height(48.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Climate Operational Pills: A/C, AUTO, MAX A/C, RECIRC, FRONT DEFROST, REAR DEFROST
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClimatePillButton(strings.ac, hvac.isAcOn, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setAcMode(ac = !hvac.isAcOn) }
                            }
                            ClimatePillButton(strings.auto, hvac.isAutoOn, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setAcMode(auto = !hvac.isAutoOn) }
                            }
                            ClimatePillButton(strings.maxAc, hvac.isMaxAcOn, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setAcMode(maxAc = !hvac.isMaxAcOn) }
                            }
                            ClimatePillButton(strings.recirc, hvac.isRecircOn, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setAcMode(recirc = !hvac.isRecircOn) }
                            }
                            ClimatePillButton(strings.frontDefrost, hvac.isFrontDefrostOn, Modifier.weight(1.2f)) {
                                coroutineScope.launch { currentHal.setAcMode(frontDefrost = !hvac.isFrontDefrostOn) }
                            }
                            ClimatePillButton(strings.rearDefrost, hvac.isRearDefrostOn, Modifier.weight(1.2f)) {
                                coroutineScope.launch { currentHal.setAcMode(rearDefrost = !hvac.isRearDefrostOn) }
                            }
                        }
                    }
                }
            }

            // 2. Driver & Passenger Seat Ventilation and Heating
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.seatClimate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SeatControlCard(
                                title = strings.driver,
                                ventLevel = cabin.driverSeatVentLevel,
                                heatLevel = cabin.driverSeatHeatLevel,
                                onVentCycle = {
                                    val next = (cabin.driverSeatVentLevel + 1) % 4
                                    coroutineScope.launch { currentHal.setSeatClimate(SeatTarget.DRIVER, ventLevel = next) }
                                },
                                onHeatCycle = {
                                    val next = (cabin.driverSeatHeatLevel + 1) % 4
                                    coroutineScope.launch { currentHal.setSeatClimate(SeatTarget.DRIVER, heatLevel = next) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SeatControlCard(
                                title = strings.passenger,
                                ventLevel = cabin.passengerSeatVentLevel,
                                heatLevel = cabin.passengerSeatHeatLevel,
                                onVentCycle = {
                                    val next = (cabin.passengerSeatVentLevel + 1) % 4
                                    coroutineScope.launch { currentHal.setSeatClimate(SeatTarget.PASSENGER, ventLevel = next) }
                                },
                                onHeatCycle = {
                                    val next = (cabin.passengerSeatHeatLevel + 1) % 4
                                    coroutineScope.launch { currentHal.setSeatClimate(SeatTarget.PASSENGER, heatLevel = next) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. 4-Window Sliders, Panoramic Sunroof & Shade
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.windowsAndRoof,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.ALL, 100) } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(strings.openAll, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = { coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.ALL, 15) } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(strings.ventAll, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = { coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.ALL, 0) } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(strings.closeAll, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4 Windows
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DebouncedWindowSlider(strings.windowFl, cabin.windowFlPercent, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.FRONT_LEFT, it) }
                            }
                            DebouncedWindowSlider(strings.windowFr, cabin.windowFrPercent, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.FRONT_RIGHT, it) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DebouncedWindowSlider(strings.windowRl, cabin.windowRlPercent, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.REAR_LEFT, it) }
                            }
                            DebouncedWindowSlider(strings.windowRr, cabin.windowRrPercent, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setWindowPosition(WindowTarget.REAR_RIGHT, it) }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Sunroof & Electric Shade
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DebouncedWindowSlider(strings.sunroof, cabin.sunroofPercent, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setSunroofAndShade(sunroofPercent = it) }
                            }
                            DebouncedWindowSlider(strings.roofShade, cabin.roofShadePercent, Modifier.weight(1f)) {
                                coroutineScope.launch { currentHal.setSunroofAndShade(shadePercent = it) }
                            }
                        }
                    }
                }
            }

            // 4. Central Door Locks, Smart Tailgate with Park Interlock & TPMS
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.doorsAndTailgate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Central Lock
                            Button(
                                onClick = { coroutineScope.launch { currentHal.setDoorLocks(!cabin.isDoorsLocked) } },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cabin.isDoorsLocked) GeelyCoralRed.copy(alpha = 0.25f) else GeelyEmeraldGreen.copy(alpha = 0.25f)
                                ),
                                border = BorderStroke(1.5.dp, if (cabin.isDoorsLocked) GeelyCoralRed else GeelyEmeraldGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (cabin.isDoorsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (cabin.isDoorsLocked) "${strings.centralLock}: ${strings.locked}" else "${strings.centralLock}: ${strings.unlocked}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Smart Tailgate with Drive-State Interlock Guardrail
                            Button(
                                onClick = {
                                    val willOpen = !cabin.isTailgateOpen
                                    if (willOpen && (telemetry.gear != GearState.P || telemetry.speedKmh > 0f)) {
                                        Toast.makeText(
                                            context,
                                            "មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! / Cannot open tailgate while vehicle is in motion or not in Park!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        coroutineScope.launch {
                                            val res = currentHal.setTailgate(willOpen)
                                            if (res.isFailure) {
                                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Tailgate failed", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cabin.isTailgateOpen) GeelyAmberGold.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer
                                ),
                                border = BorderStroke(1.5.dp, if (cabin.isTailgateOpen) GeelyAmberGold else MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (cabin.isTailgateOpen) strings.closeTailgate else strings.openTailgate,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // TPMS 4-Wheel Diagram
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.tpmsTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TpmsCard(strings.frontLeftTire, tpms.flBar, tpms.flTempC, Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            TpmsCard(strings.frontRightTire, tpms.frBar, tpms.frTempC, Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            TpmsCard(strings.rearLeftTire, tpms.rlBar, tpms.rlTempC, Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            TpmsCard(strings.rearRightTire, tpms.rrBar, tpms.rrTempC, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Sub-Components
// =========================================================================

@Composable
fun AutomotiveTempGauge(
    label: String,
    tempC: Float,
    onTempChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onTempChange(tempC - 0.5f) },
                    modifier = Modifier.size(46.dp)
                ) {
                    Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GeelyAccentCyan)
                }
                Text(
                    text = String.format(Locale.US, "%.1f°C", tempC),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                IconButton(
                    onClick = { onTempChange(tempC + 0.5f) },
                    modifier = Modifier.size(46.dp)
                ) {
                    Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GeelyCoralRed)
                }
            }
        }
    }
}

@Composable
fun ClimatePillButton(
    title: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) GeelyNavyDark else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SeatControlCard(
    title: String,
    ventLevel: Int,
    heatLevel: Int,
    onVentCycle: () -> Unit,
    onHeatCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Vent
                Button(
                    onClick = onVentCycle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ventLevel > 0) GeelyAccentCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (ventLevel > 0) GeelyAccentCyan else MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Vent: $ventLevel/3",
                        fontSize = 11.sp,
                        color = if (ventLevel > 0) GeelyAccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Heat
                Button(
                    onClick = onHeatCycle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (heatLevel > 0) GeelyCoralRed.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (heatLevel > 0) GeelyCoralRed else MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Heat: $heatLevel/3",
                        fontSize = 11.sp,
                        color = if (heatLevel > 0) GeelyCoralRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DebouncedWindowSlider(
    label: String,
    percent: Int,
    modifier: Modifier = Modifier,
    onPercentChanged: (Int) -> Unit
) {
    var localPos by remember(percent) { mutableFloatStateOf(percent.toFloat()) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${localPos.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Slider(
                value = localPos,
                onValueChange = { localPos = it },
                onValueChangeFinished = { onPercentChanged(localPos.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.height(36.dp)
            )
        }
    }
}

@Composable
fun TpmsCard(
    wheelName: String,
    bar: Float,
    temp: Int,
    modifier: Modifier = Modifier
) {
    val isNormal = bar in 2.2f..2.6f
    val strokeColor = if (isNormal) GeelyEmeraldGreen else GeelyCoralRed

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(wheelName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                String.format(Locale.US, "%.2f Bar", bar),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNormal) GeelyEmeraldGreen else GeelyCoralRed
            )
            Text("$temp °C", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
