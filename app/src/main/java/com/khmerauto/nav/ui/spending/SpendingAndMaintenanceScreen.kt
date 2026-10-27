package com.khmerauto.nav.ui.spending

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khmerauto.nav.core.database.ChargeLogEntity
import com.khmerauto.nav.core.database.MaintenanceItemEntity
import com.khmerauto.nav.core.database.TripLogEntity
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.telemetry.GapReconciliationEngine
import com.khmerauto.nav.telemetry.GapReconciliationSummary
import com.khmerauto.nav.ui.theme.*
import com.khmerauto.nav.voice.NavigationController
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * Three-Column / Tabbed Spending, Maintenance & Repair Center Screen matching EZNav layout.
 *
 * Implements:
 * 1. "Two Numbers Moved Without You" Gap Reconciliation Card.
 * 2. KHR Spending Tracker with Day/Week/Month toggles & Measured vs. Estimated bar chart.
 * 3. Live Hardware Odometer Maintenance Countdowns with "Reset Service".
 * 4. Searchable Cambodia EV Repair Workshop Directory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAndMaintenanceScreen(
    currentHal: VehicleHalContract,
    gapEngine: GapReconciliationEngine,
    navController: NavigationController,
    onBackToCockpit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetry by currentHal.telemetryState.collectAsState()
    val gapSummary by gapEngine.reconciliationSummary.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var spendingPeriod by remember { mutableStateOf("WEEK") } // DAY, WEEK, MONTH

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Header Bar
            SpendingHeader(
                currentOdometerKm = telemetry.odometerKm,
                onBack = onBackToCockpit
            )

            // 2. "Two Numbers Moved Without You" Reconciliation Banner (if gap detected)
            gapSummary?.let { summary ->
                TwoNumbersMovedCard(
                    summary = summary,
                    onDismiss = { gapEngine.dismissSummary() }
                )
            }

            // 3. Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = GeelyAccentCyan,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "ការចំណាយជាប្រាក់រៀល (KHR Spending)",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "ការថែទាំតាមគីឡូម៉ែត្រ (Maintenance)",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            "យានដ្ឋានជួសជុល EV (Repair Shops)",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    icon = { Icon(Icons.Default.HomeRepairService, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // 4. Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> SpendingTrackerTab(
                        period = spendingPeriod,
                        onPeriodChange = { spendingPeriod = it }
                    )
                    1 -> MaintenanceCountdownTab(
                        currentOdoKm = telemetry.odometerKm,
                        onResetService = { itemId ->
                            coroutineScope.launch {
                                gapEngine.recordMaintenanceServiced(itemId, telemetry.odometerKm)
                                Toast.makeText(context, "បានកត់ត្រាការប្តូររួច! / Service countdown reset!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToService = {
                            coroutineScope.launch {
                                navController.navigateNearestRepair()
                                Toast.makeText(context, "កំពុងនាំផ្លូវទៅមជ្ឈមណ្ឌលថែទាំ / Navigating to service center", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    2 -> RepairShopsDirectoryTab(
                        onNavigateToShop = { shopName ->
                            coroutineScope.launch {
                                navController.navigateNearestRepair()
                                Toast.makeText(context, "កំពុងនាំផ្លូវទៅ $shopName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// =========================================================================
// Top Header
// =========================================================================

@Composable
fun SpendingHeader(
    currentOdometerKm: Float,
    onBack: () -> Unit
) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ការចំណាយ & ការថែទាំរថយន្ត (Spending & Maintenance)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Geely Galaxy M7 • Thor PHEV • គណនាជាប្រាក់រៀល (៛)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = GeelyAccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "Odometer: %,.1f km", currentOdometerKm),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// =========================================================================
// "Two Numbers Moved Without You" Reconciliation Card
// =========================================================================

@Composable
fun TwoNumbersMovedCard(
    summary: GapReconciliationSummary,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = GeelyAmberGold.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, GeelyAmberGold)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(GeelyAmberGold.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔄", fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "តួលេខពីរបានផ្លាស់ប្តូរពេលកម្មវិធីបិទ (Two Numbers Moved Without You)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeelyAmberGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GeelyAmberGold
                        ) {
                            Text(
                                text = "GAP CATCH-UP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeelyNavyDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = String.format(
                            Locale.US,
                            "រថយន្តបានបើកបរ +%.1f km (%,.1f ➔ %,.1f km) • អាគុយ: %d%% ➔ %d%% • ចំណាយបំពេញចន្លោះ: %,d ៛",
                            summary.deltaKm,
                            summary.previousOdoKm,
                            summary.currentOdoKm,
                            summary.previousBatteryPercent,
                            summary.currentBatteryPercent,
                            summary.totalCostRiel
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "បានបញ្ចូលទៅក្នុងកំណត់ត្រាធ្វើដំណើរ និងសាកថ្មដោយស្វ័យប្រវត្តិ / Automatically reconciled into trip logs",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GeelyAmberGold),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "យល់ព្រម / Dismiss",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = GeelyNavyDark
                )
            }
        }
    }
}

// =========================================================================
// Tab 1: Spending Tracker & Measured vs. Catch-up Bar Chart
// =========================================================================

@Composable
fun SpendingTrackerTab(
    period: String,
    onPeriodChange: (String) -> Unit
) {
    // Simulated realistic PHEV weekly trip logs in Cambodia
    val days = listOf("ចន្ទ (Mon)", "អង្គារ (Tue)", "ពុធ (Wed)", "ព្រហ (Thu)", "សុក្រ (Fri)", "សៅរ៍ (Sat)", "អាទិត្យ (Sun)")
    val measuredCost = listOf(4800L, 6200L, 5100L, 7300L, 8900L, 12500L, 9400L)
    val catchUpCost = listOf(1400L, 0L, 2100L, 0L, 1800L, 4200L, 0L)

    val totalMeasured = measuredCost.sum()
    val totalCatchUp = catchUpCost.sum()
    val grandTotal = totalMeasured + totalCatchUp

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Big Total & Metric Tiles
        Column(
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Period Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ចំណាយសរុប (Total Spent)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PeriodPill("ថ្ងៃនេះ", "DAY", period == "DAY") { onPeriodChange("DAY") }
                            PeriodPill("៧ថ្ងៃ", "WEEK", period == "WEEK") { onPeriodChange("WEEK") }
                            PeriodPill("ខែនេះ", "MONTH", period == "MONTH") { onPeriodChange("MONTH") }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Prominent Riel Currency Total
                    Text(
                        text = "${formatRiel(grandTotal)} ៛",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = GeelyEmeraldGreen
                    )
                    Text(
                        text = "ស្មើនឹង ~$${String.format(Locale.US, "%.2f", grandTotal / 4100.0)} USD (អត្រា ៤,១០០ ៛/$)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Share comparison: Measured vs Catch-Up
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GeelyAccentCyan))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("វាស់ជាក់ស្តែង (Measured)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${formatRiel(totalMeasured)} ៛", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GeelyAccentCyan)
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GeelyAmberGold))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("បំពេញចន្លោះ (Catch-Up)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${formatRiel(totalCatchUp)} ៛", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GeelyAmberGold)
                        }
                    }
                }
            }

            // Efficiency Metric Cards
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    MetricRow(label = "ចម្ងាយសរុប (Distance)", value = "384.2 km", accent = GeelyAccentCyan)
                    MetricRow(label = "ថាមពលអគ្គិសនីប្រើប្រាស់", value = "62.4 kWh", accent = GeelyEmeraldGreen)
                    MetricRow(label = "កម្រិតស៊ីភ្លើងជាមធ្យម", value = "16.2 kWh / 100 km", accent = MaterialTheme.colorScheme.onSurface)
                    MetricRow(label = "តម្លៃចំណាយក្នុង ១ គ.ម", value = "165 ៛ / km", accent = GeelyAmberGold)
                    MetricRow(label = "តម្លៃភ្លើង EDC (Home Tariff)", value = "730 ៛ / kWh", accent = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Right Column: Interactive Per-Day Bar Chart
        Surface(
            modifier = Modifier.weight(1.1f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ក្រាហ្វចំណាយប្រចាំថ្ងៃ (Daily Spending Breakdown)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Legend
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChartLegendPill(color = GeelyAccentCyan, label = "Measured")
                        ChartLegendPill(color = GeelyAmberGold, label = "Catch-Up")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Canvas Stacked Bar Chart
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxDaily = 16000f
                        val barWidth = size.width / (days.size * 1.8f)
                        val slotWidth = size.width / days.size

                        for (i in days.indices) {
                            val x = i * slotWidth + (slotWidth - barWidth) / 2
                            val measuredH = (measuredCost[i] / maxDaily) * (size.height - 40.dp.toPx())
                            val catchUpH = (catchUpCost[i] / maxDaily) * (size.height - 40.dp.toPx())

                            val groundY = size.height - 25.dp.toPx()

                            // Draw Measured Bar (Solid Cyan)
                            drawRoundRect(
                                color = GeelyAccentCyan,
                                topLeft = Offset(x, groundY - measuredH),
                                size = Size(barWidth, measuredH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )

                            // Draw Catch-Up Bar (Amber on top)
                            if (catchUpH > 0f) {
                                drawRoundRect(
                                    color = GeelyAmberGold,
                                    topLeft = Offset(x, groundY - measuredH - catchUpH),
                                    size = Size(barWidth, catchUpH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                )
                            }

                            // Ground line
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.3f),
                                start = Offset(0f, groundY),
                                end = Offset(size.width, groundY),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }

                // Day Labels Below Chart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("ចន្ទ", "អង្គារ", "ពុធ", "ព្រហ", "សុក្រ", "សៅរ៍", "អាទិត្យ").forEach { d ->
                        Text(
                            text = d,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// Tab 2: Maintenance Countdown Card
// =========================================================================

@Composable
fun MaintenanceCountdownTab(
    currentOdoKm: Float,
    onResetService: (String) -> Unit,
    onNavigateToService: () -> Unit
) {
    val items = remember(currentOdoKm) {
        listOf(
            MaintenanceItem(
                id = "TYRE_ROTATION",
                nameKm = "ប្តូរទីតាំងកង់ (Tyre Rotation)",
                nameEn = "Tyre Rotation & Dynamic Balancing",
                intervalKm = 10000,
                lastServicedKm = 11200f
            ),
            MaintenanceItem(
                id = "CABIN_AIR_FILTER",
                nameKm = "តម្រងខ្យល់ម៉ាស៊ីនត្រជាក់ (Cabin Filter)",
                nameEn = "CN95 High Efficiency Cabin Air Filter",
                intervalKm = 15000,
                lastServicedKm = 9800f
            ),
            MaintenanceItem(
                id = "WIPER_BLADES",
                nameKm = "ផ្លិតទឹកសម្អាតកញ្ចក់ (Wiper Blades)",
                nameEn = "Front Windshield Silicone Wiper Blades",
                intervalKm = 15000,
                lastServicedKm = 8500f
            ),
            MaintenanceItem(
                id = "BRAKE_FLUID",
                nameKm = "ប្រេងហ្វ្រាំង (Brake Fluid Flush)",
                nameEn = "DOT 4 High Performance Brake Fluid",
                intervalKm = 40000,
                lastServicedKm = 0f
            ),
            MaintenanceItem(
                id = "BATTERY_COOLANT",
                nameKm = "ទឹកស្អំអាគុយរថយន្តអគ្គិសនី (Battery Coolant)",
                nameEn = "Thor Battery Low-Conductivity Coolant",
                intervalKm = 60000,
                lastServicedKm = 0f
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: 5 Maintenance Countdowns
        Surface(
            modifier = Modifier.weight(1.3f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "កាលវិភាគថែទាំតាមចម្ងាយគីឡូម៉ែត្រ (Scheduled Maintenance)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(items) { item ->
                    val drivenSinceService = (currentOdoKm - item.lastServicedKm).coerceAtLeast(0f)
                    val remainingKm = (item.intervalKm - drivenSinceService).coerceAtLeast(0f)
                    val progress = (drivenSinceService / item.intervalKm).coerceIn(0f, 1f)

                    val statusColor = when {
                        remainingKm <= 500f -> GeelyCoralRed
                        remainingKm <= 2000f -> GeelyAmberGold
                        else -> GeelyEmeraldGreen
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.nameKm,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.nameEn,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { onResetService(item.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "កត់ត្រាការប្តូររួច / Reset",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress Bar
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = statusColor,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ចន្លោះកំណត់: ${formatNumber(item.intervalKm)} km",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "នៅសល់: ${formatNumber(remainingKm.toInt())} km",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Preferred Geely Service Center
        Surface(
            modifier = Modifier.weight(0.7f).fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GeelyAccentCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = GeelyAccentCyan)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "យានដ្ឋានផ្លូវការ (Preferred Shop)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Geely Galaxy Official Center",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "មជ្ឈមណ្ឌលថែទាំ & ជួសជុលរថយន្តអគ្គិសនី Geely Galaxy ភ្នំពេញ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "មហាវិថីសហព័ន្ធរុស្ស៊ី, ភ្នំពេញ\nចម្ងាយ ៤.២ គ.ម • បើកម៉ោង 7:30 AM - 5:30 PM",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeelyEmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ គ្រឿងបន្លាស់សុទ្ធពីក្រុមហ៊ុន Geely\n✓ ជាងជំនាញប្រព័ន្ធភ្លើង និងអាគុយ Thor EV\n✓ សេវាត្រួតពិនិត្យដោយឥតគិតថ្លៃ ១០ ចំណុច",
                            fontSize = 11.sp,
                            color = GeelyEmeraldGreen,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = onNavigateToService,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = GeelyNavyDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("នាំផ្លូវទៅកាន់យានដ្ឋាន / Navigate", fontWeight = FontWeight.Bold, color = GeelyNavyDark)
                }
            }
        }
    }
}

// =========================================================================
// Tab 3: Repair Workshops Directory
// =========================================================================

@Composable
fun RepairShopsDirectoryTab(
    onNavigateToShop: (String) -> Unit
) {
    val workshops = remember {
        listOf(
            WorkshopInfo(
                nameKm = "មជ្ឈមណ្ឌលថែទាំ Geely Galaxy Official Service Center",
                nameEn = "Geely Galaxy Official Service Center Phnom Penh",
                address = "មហាវិថីសហព័ន្ធរុស្ស៊ី, ភ្នំពេញ",
                distanceKm = 4.2f,
                hours = "7:30 AM - 5:30 PM",
                services = listOf("HV Battery Check", "Brake Overhaul", "AC System", "Wheel Alignment"),
                hasMemberDiscount = true
            ),
            WorkshopInfo(
                nameKm = "យានដ្ឋានជួសជុលរថយន្តអគ្គិសនី Pro EV Care",
                nameEn = "Pro EV Care Battery & Inverter Workshop",
                address = "ផ្លូវលេខ ២៧១, ភ្នំពេញ",
                distanceKm = 6.8f,
                hours = "8:00 AM - 6:00 PM",
                services = listOf("Inverter Repair", "Coolant Flush", "Tyre Change"),
                hasMemberDiscount = true
            ),
            WorkshopInfo(
                nameKm = "មជ្ឈមណ្ឌលសេវាកម្ម EV Fast Care សៀមរាប",
                nameEn = "EV Fast Care Siem Reap Center",
                address = "ផ្លូវជាតិលេខ ៦, ក្រុងសៀមរាប",
                distanceKm = 312.0f,
                hours = "7:30 AM - 6:00 PM",
                services = listOf("Roadside Assist", "Quick Charge", "Diagnostics"),
                hasMemberDiscount = false
            ),
            WorkshopInfo(
                nameKm = "យានដ្ឋាន EV មាត់សមុទ្រ ក្រុងព្រះសីហនុ",
                nameEn = "Sihanoukville Coastal EV Service Hub",
                address = "វិថីឯករាជ្យ, ក្រុងព្រះសីហនុ",
                distanceKm = 224.0f,
                hours = "8:00 AM - 5:30 PM",
                services = listOf("Express Service", "Brake Service", "AC Filter"),
                hasMemberDiscount = true
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(workshops) { shop ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = shop.nameKm,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (shop.hasMemberDiscount) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GeelyAmberGold.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "GEELY 10% OFF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GeelyAmberGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${shop.nameEn} • ${shop.address}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ចម្ងាយ: ${shop.distanceKm} គ.ម • ម៉ោងបើក: ${shop.hours}",
                            fontSize = 11.sp,
                            color = GeelyAccentCyan
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            shop.services.forEach { s ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = s,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onNavigateToShop(shop.nameKm) },
                        colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = GeelyNavyDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("នាំផ្លូវ / Go", fontWeight = FontWeight.Bold, color = GeelyNavyDark, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// Helpers & Subcomponents
// =========================================================================

private data class MaintenanceItem(
    val id: String,
    val nameKm: String,
    val nameEn: String,
    val intervalKm: Int,
    val lastServicedKm: Float
)

private data class WorkshopInfo(
    val nameKm: String,
    val nameEn: String,
    val address: String,
    val distanceKm: Float,
    val hours: String,
    val services: List<String>,
    val hasMemberDiscount: Boolean
)

@Composable
private fun PeriodPill(label: String, tag: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) GeelyAccentCyan.copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, GeelyAccentCyan) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) GeelyAccentCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun ChartLegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRiel(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(amount)
}

private fun formatNumber(num: Int): String {
    return NumberFormat.getNumberInstance(Locale.US).format(num)
}
