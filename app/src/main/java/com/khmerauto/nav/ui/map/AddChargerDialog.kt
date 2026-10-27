package com.khmerauto.nav.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.khmerauto.nav.ui.theme.*

/**
 * Automotive "Add / Verify EV Charger" Dialog.
 * Enables drivers to pin new charging stations offline directly to Room SQLite,
 * automatically queuing them for server synchronization when connectivity returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChargerDialog(
    currentLat: Double,
    currentLng: Double,
    onDismiss: () -> Unit,
    onConfirm: (
        nameKm: String,
        nameEn: String,
        latitude: Double,
        longitude: Double,
        operator: String,
        maxPowerKw: Int,
        connectorTypes: String,
        totalPlugs: Int,
        pricePerKwhKhr: Int
    ) -> Unit
) {
    var nameKm by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var selectedOperator by remember { mutableStateOf("EVX") }
    var selectedPowerKw by remember { mutableIntStateOf(120) }
    var selectedConnectors by remember { mutableStateOf(setOf("CCS2", "GB-T")) }
    var totalPlugs by remember { mutableIntStateOf(4) }
    var priceKhr by remember { mutableIntStateOf(1250) }

    val operators = listOf("EVX", "TotalEnergies", "Caltex", "PTT", "Geely Thor Supercharge", "Other")
    val powerOptions = listOf(60, 120, 160, 180, 240, 360)
    val connectorOptions = listOf("CCS2", "GB-T", "Type 2", "CHAdeMO")
    val priceOptions = listOf(1100, 1200, 1250, 1300, 1400)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GeelyEmeraldGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EvStation,
                                contentDescription = "Add Charger",
                                tint = GeelyEmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "បន្ថែមស្ថានីយសាកថ្ម / Pin EV Charger",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "រក្សាទុកក្រៅបណ្តាញ (Offline Save & Queue Sync)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current GPS Location Badge
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = "GPS",
                            tint = GeelyAccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("GPS Coords: %.5f, %.5f (ទីតាំងរថយន្តបច្ចុប្បន្ន)", currentLat, currentLng),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Name inputs
                OutlinedTextField(
                    value = nameKm,
                    onValueChange = { nameKm = it },
                    label = { Text("ឈ្មោះជាភាសាខ្មែរ (Name in Khmer)") },
                    placeholder = { Text("ឧ. ស្ថានីយសាក EVX មហាវិថីព្រះមុនីវង្ស") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text("ឈ្មោះជាភាសាអង់គ្លេស (Name in English)") },
                    placeholder = { Text("e.g. EVX Monivong Charging Hub") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Operator Selection
                Text(
                    text = "ក្រុមហ៊ុនប្រតិបត្តិករ (Charging Operator)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(operators) { op ->
                        FilterChip(
                            selected = selectedOperator == op,
                            onClick = { selectedOperator = op },
                            label = { Text(op, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeelyAccentCyan.copy(alpha = 0.25f),
                                selectedLabelColor = GeelyAccentCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Max Power Selection (kW)
                Text(
                    text = "កម្លាំងសាកអតិបរមា (Max Power kW)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(powerOptions) { kw ->
                        FilterChip(
                            selected = selectedPowerKw == kw,
                            onClick = { selectedPowerKw = kw },
                            label = { Text("$kw kW", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeelyEmeraldGreen.copy(alpha = 0.25f),
                                selectedLabelColor = GeelyEmeraldGreen
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connector Types
                Text(
                    text = "ប្រភេទក្បាលសាក (Connector Types)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    connectorOptions.forEach { conn ->
                        val isSelected = selectedConnectors.contains(conn)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedConnectors = if (isSelected) {
                                    if (selectedConnectors.size > 1) selectedConnectors - conn else selectedConnectors
                                } else {
                                    selectedConnectors + conn
                                }
                            },
                            label = { Text(conn, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tariff (៛ / kWh)
                Text(
                    text = "តម្លៃសាកក្នុង ១ គីឡូវ៉ាត់ម៉ោង (Tariff Riel / kWh)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(priceOptions) { p ->
                        FilterChip(
                            selected = priceKhr == p,
                            onClick = { priceKhr = p },
                            label = { Text("៛ $p", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeelyAmberGold.copy(alpha = 0.25f),
                                selectedLabelColor = GeelyAmberGold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("បោះបង់ / Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val finalKm = nameKm.ifBlank { "ស្ថានីយសាក $selectedOperator" }
                            val finalEn = nameEn.ifBlank { "$selectedOperator Fast Charger" }
                            val connectorStr = selectedConnectors.joinToString(", ")

                            onConfirm(
                                finalKm,
                                finalEn,
                                currentLat,
                                currentLng,
                                selectedOperator,
                                selectedPowerKw,
                                connectorStr,
                                totalPlugs,
                                priceKhr
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeelyEmeraldGreen)
                    ) {
                        Text(
                            text = "រក្សាទុកក្រៅបណ្តាញ (Save Offline)",
                            fontWeight = FontWeight.Bold,
                            color = GeelyNavyDark
                        )
                    }
                }
            }
        }
    }
}
