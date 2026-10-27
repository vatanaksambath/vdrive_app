package com.khmerauto.nav.telemetry

import android.util.Log
import com.khmerauto.nav.core.database.AppDatabase
import com.khmerauto.nav.core.database.HardwareSnapshotEntity
import com.khmerauto.nav.core.database.MaintenanceItemEntity
import com.khmerauto.nav.core.database.TripLogEntity
import com.khmerauto.nav.core.database.ChargeLogEntity
import com.khmerauto.nav.hal.VehicleTelemetryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "GapReconciliation"

/**
 * Summary of vehicle mileage and energy changes that occurred while the app was closed or asleep.
 * Displayed in the "Two Numbers Moved Without You / តួលេខពីរបានផ្លាស់ប្តូរពេលកម្មវិធីបិទ" dashboard alert.
 */
data class GapReconciliationSummary(
    val deltaKm: Float,
    val previousOdoKm: Float,
    val currentOdoKm: Float,
    val previousBatteryPercent: Int,
    val currentBatteryPercent: Int,
    val deltaKwhUsed: Float,
    val deltaKwhCharged: Float,
    val totalCostRiel: Long,
    val gapDurationHours: Float,
    val wasChargingDetected: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * "Two Numbers Moved Without You" Gap Catch-Up & Telemetry Reconciliation Engine.
 *
 * Responsibilities:
 * 1. Solves the automotive problem where Flyme Auto kills apps during deep sleep or the car is driven without KMAutoNav open.
 * 2. On app start / HAL reconnect, detects if (currentOdometer - lastSnapshot.odometer > 0.5 km).
 * 3. Reconciles net energy delta: infers charging events vs. driving consumption using rolling PHEV efficiency (16.4 kWh/100km).
 * 4. Logs estimated catch-up trips and charges into Room DB with cost calculation in Cambodian Riel (៛).
 * 5. Emits GapReconciliationSummary for display in the Cockpit and Spending screen.
 * 6. Manages maintenance countdown items based on live hardware odometer.
 */
class GapReconciliationEngine(
    private val database: AppDatabase,
    var edcDomesticTariffRiel: Int = 730,  // Electricite du Cambodge (EDC) standard home tariff
    var publicFastTariffRiel: Int = 1200,   // Cambodia public DC fast charger tariff
    var assumedEfficiencyKwhPer100Km: Float = 16.4f // Geely Galaxy M7 Thor PHEV benchmark
) {
    private val telemetryDao = database.telemetryDao()

    private val _reconciliationSummary = MutableStateFlow<GapReconciliationSummary?>(null)
    val reconciliationSummary: StateFlow<GapReconciliationSummary?> = _reconciliationSummary.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes default maintenance countdown items if table is empty.
     */
    suspend fun initializeMaintenanceIfNeeded(currentOdoKm: Float) = withContext(Dispatchers.IO) {
        if (telemetryDao.getMaintenanceItemCount() == 0) {
            Log.i(TAG, "Seeding initial maintenance countdown schedules based on Geely Galaxy M7 manual...")
            val baseOdo = if (currentOdoKm > 1000f) currentOdoKm - 1000f else 0f
            val items = listOf(
                MaintenanceItemEntity(
                    id = "TYRE_ROTATION",
                    nameKm = "ប្តូរទីតាំងកង់ (Tyre Rotation)",
                    nameEn = "Tyre Rotation & Balancing",
                    intervalKm = 10000,
                    lastServicedOdometerKm = max(0f, baseOdo - 3500f)
                ),
                MaintenanceItemEntity(
                    id = "CABIN_AIR_FILTER",
                    nameKm = "តម្រងខ្យល់ម៉ាស៊ីនត្រជាក់ (Cabin Filter)",
                    nameEn = "Cabin Air Filter Replacement",
                    intervalKm = 15000,
                    lastServicedOdometerKm = max(0f, baseOdo - 5000f)
                ),
                MaintenanceItemEntity(
                    id = "WIPER_BLADES",
                    nameKm = "ផ្លិតទឹកសម្អាតកញ្ចក់ (Wiper Blades)",
                    nameEn = "Wiper Blades Inspection",
                    intervalKm = 15000,
                    lastServicedOdometerKm = max(0f, baseOdo - 6500f)
                ),
                MaintenanceItemEntity(
                    id = "BRAKE_FLUID",
                    nameKm = "ប្រេងហ្វ្រាំង (Brake Fluid)",
                    nameEn = "Brake Fluid Inspection & Flush",
                    intervalKm = 40000,
                    lastServicedOdometerKm = 0f
                ),
                MaintenanceItemEntity(
                    id = "BATTERY_COOLANT",
                    nameKm = "ទឹកស្អំអាគុយរថយន្តអគ្គិសនី (Battery Coolant)",
                    nameEn = "Thor Battery Coolant Replacement",
                    intervalKm = 60000,
                    lastServicedOdometerKm = 0f
                )
            )
            telemetryDao.insertMaintenanceItems(items)
        }
    }

    /**
     * Performs gap check and reconciliation on startup or HAL reconnection.
     */
    suspend fun reconcileGapIfNeeded(telemetry: VehicleTelemetryState) = withContext(Dispatchers.IO) {
        initializeMaintenanceIfNeeded(telemetry.odometerKm)

        val lastSnapshot = telemetryDao.getLatestHardwareSnapshot()
        val now = System.currentTimeMillis()

        if (lastSnapshot == null) {
            Log.i(TAG, "No previous hardware snapshot found. Recording initial snapshot at Odo=${telemetry.odometerKm} km")
            saveSnapshot(telemetry, now)
            isInitialized = true
            return@withContext
        }

        val deltaKm = telemetry.odometerKm - lastSnapshot.odometerKm
        val durationMs = now - lastSnapshot.timestamp
        val durationHours = (durationMs / 3600000.0f).coerceAtLeast(0.01f)

        // Threshold: If odometer advanced by more than 500 meters while app was not recording
        if (deltaKm > 0.5f) {
            Log.i(TAG, "[GAP DETECTED] Vehicle moved without KMAutoNav active! Delta: +$deltaKm km (Odo: ${lastSnapshot.odometerKm} -> ${telemetry.odometerKm} km)")

            val deltaKwh = telemetry.usableKwh - lastSnapshot.usableKwh
            var kwhUsed = 0.0f
            var kwhCharged = 0.0f
            var totalCostRiel = 0L
            var wasChargingDetected = false

            if (deltaKwh < -0.1f) {
                // Case 1: Pure Driving Discharge (no charging detected in gap)
                kwhUsed = abs(deltaKwh)
                val efficiency = (kwhUsed / deltaKm) * 100.0f
                totalCostRiel = (kwhUsed * edcDomesticTariffRiel).toLong()

                // Insert catch-up trip log
                val trip = TripLogEntity(
                    startTime = lastSnapshot.timestamp,
                    endTime = now,
                    distanceKm = deltaKm,
                    kwhUsed = kwhUsed,
                    costRiel = totalCostRiel,
                    efficiencyKwhPer100Km = efficiency.coerceIn(10.0f, 35.0f),
                    isEstimatedCatchUp = true
                )
                telemetryDao.insertTripLog(trip)
                Log.i(TAG, "Inserted catch-up TripLog: $deltaKm km, $kwhUsed kWh, $totalCostRiel KHR")

            } else {
                // Case 2: Battery Gained Energy or Remained Same (Charging occurred during gap)
                wasChargingDetected = true
                val estimatedDrivingKwh = (deltaKm / 100.0f) * assumedEfficiencyKwhPer100Km
                kwhUsed = estimatedDrivingKwh
                kwhCharged = deltaKwh + estimatedDrivingKwh
                val chargeCostRiel = (kwhCharged * edcDomesticTariffRiel).toLong()
                totalCostRiel = chargeCostRiel

                // Insert catch-up charge log
                val chargeLog = ChargeLogEntity(
                    timestamp = now - 1800000L, // Inferred midpoint of parking period
                    startPercent = lastSnapshot.batteryPercent,
                    endPercent = telemetry.batteryPercent,
                    kwhAdded = kwhCharged,
                    costRiel = chargeCostRiel,
                    tariffRielPerKwh = edcDomesticTariffRiel,
                    isEstimatedCatchUp = true
                )
                telemetryDao.insertChargeLog(chargeLog)

                // Insert catch-up trip log
                val trip = TripLogEntity(
                    startTime = lastSnapshot.timestamp,
                    endTime = now,
                    distanceKm = deltaKm,
                    kwhUsed = estimatedDrivingKwh,
                    costRiel = (estimatedDrivingKwh * edcDomesticTariffRiel).toLong(),
                    efficiencyKwhPer100Km = assumedEfficiencyKwhPer100Km,
                    isEstimatedCatchUp = true
                )
                telemetryDao.insertTripLog(trip)
                Log.i(TAG, "Inserted catch-up ChargeLog (+$kwhCharged kWh) and TripLog ($deltaKm km, $estimatedDrivingKwh kWh)")
            }

            // Emit reconciliation summary for UI banner
            val summary = GapReconciliationSummary(
                deltaKm = deltaKm,
                previousOdoKm = lastSnapshot.odometerKm,
                currentOdoKm = telemetry.odometerKm,
                previousBatteryPercent = lastSnapshot.batteryPercent,
                currentBatteryPercent = telemetry.batteryPercent,
                deltaKwhUsed = kwhUsed,
                deltaKwhCharged = kwhCharged,
                totalCostRiel = totalCostRiel,
                gapDurationHours = durationHours,
                wasChargingDetected = wasChargingDetected,
                timestamp = now
            )
            _reconciliationSummary.value = summary
        } else {
            Log.d(TAG, "No significant gap detected (deltaKm = $deltaKm km). Continuing active tracking.")
        }

        // Save fresh snapshot
        saveSnapshot(telemetry, now)
        isInitialized = true
    }

    /**
     * Saves a snapshot of current telemetry to Room DB.
     */
    suspend fun saveSnapshot(telemetry: VehicleTelemetryState, timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val snapshot = HardwareSnapshotEntity(
            timestamp = timestamp,
            odometerKm = telemetry.odometerKm,
            batteryPercent = telemetry.batteryPercent,
            usableKwh = telemetry.usableKwh,
            fuelLiters = 45.0f,
            lat = telemetry.latitude,
            lng = telemetry.longitude
        )
        telemetryDao.insertHardwareSnapshot(snapshot)
    }

    /**
     * Resets a maintenance service item countdown to current odometer.
     */
    suspend fun recordMaintenanceServiced(itemId: String, currentOdoKm: Float) = withContext(Dispatchers.IO) {
        telemetryDao.updateMaintenanceServiced(itemId, currentOdoKm)
        Log.i(TAG, "Service item '$itemId' reset to current odometer: $currentOdoKm km")
    }

    fun dismissSummary() {
        _reconciliationSummary.value = null
    }
}
