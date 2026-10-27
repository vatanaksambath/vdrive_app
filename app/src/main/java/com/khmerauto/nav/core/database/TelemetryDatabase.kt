package com.khmerauto.nav.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Persists the last hardware-reported vehicle state for gap reconciliation
 * when Flyme Auto or Android OS kills background apps during sleep.
 */
@Entity(tableName = "hardware_snapshots")
@Serializable
data class HardwareSnapshotEntity(
    @PrimaryKey val timestamp: Long = System.currentTimeMillis(),
    val odometerKm: Float,
    val batteryPercent: Int,
    val usableKwh: Float,
    val fuelLiters: Float = 45.0f,
    val lat: Double = 11.5564,
    val lng: Double = 104.9282
)

/**
 * Trip log entity recording travel distance, energy, and cost in Cambodian Riel.
 * Marks whether the trip was measured live or reconciled while the app was closed.
 */
@Entity(tableName = "trip_logs")
@Serializable
data class TripLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Float,
    val kwhUsed: Float,
    val costRiel: Long,
    val efficiencyKwhPer100Km: Float,
    val isEstimatedCatchUp: Boolean = false // Reconciled while app was closed
)

/**
 * Charge log entity recording energy added and charging costs in Cambodian Riel.
 */
@Entity(tableName = "charge_logs")
@Serializable
data class ChargeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val startPercent: Int,
    val endPercent: Int,
    val kwhAdded: Float,
    val costRiel: Long,
    val tariffRielPerKwh: Int = 730, // Default EDC domestic rate 730 ៛ / kWh
    val isEstimatedCatchUp: Boolean = false
)

/**
 * Vehicle maintenance schedule countdown items based on odometer mileage.
 */
@Entity(tableName = "maintenance_items")
@Serializable
data class MaintenanceItemEntity(
    @PrimaryKey val id: String,
    val nameKm: String,
    val nameEn: String,
    val intervalKm: Int,
    val lastServicedOdometerKm: Float,
    val preferredShopName: String = "Geely Galaxy Official Service Center",
    val preferredShopAddress: String = "Russian Federation Blvd, Phnom Penh"
)

@Dao
interface TelemetryDao {

    // Hardware Snapshots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHardwareSnapshot(snapshot: HardwareSnapshotEntity)

    @Query("SELECT * FROM hardware_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHardwareSnapshot(): HardwareSnapshotEntity?

    @Query("SELECT * FROM hardware_snapshots ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHardwareSnapshotsFlow(): Flow<List<HardwareSnapshotEntity>>

    // Trip Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripLog(trip: TripLogEntity): Long

    @Query("SELECT * FROM trip_logs WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime DESC")
    suspend fun getTripLogsBetween(startTime: Long, endTime: Long): List<TripLogEntity>

    @Query("SELECT * FROM trip_logs ORDER BY startTime DESC")
    fun getAllTripLogsFlow(): Flow<List<TripLogEntity>>

    @Query("SELECT * FROM trip_logs WHERE startTime >= :sinceTime ORDER BY startTime DESC")
    fun getTripLogsSinceFlow(sinceTime: Long): Flow<List<TripLogEntity>>

    // Charge Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargeLog(charge: ChargeLogEntity): Long

    @Query("SELECT * FROM charge_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getChargeLogsBetween(startTime: Long, endTime: Long): List<ChargeLogEntity>

    @Query("SELECT * FROM charge_logs ORDER BY timestamp DESC")
    fun getAllChargeLogsFlow(): Flow<List<ChargeLogEntity>>

    @Query("SELECT * FROM charge_logs WHERE timestamp >= :sinceTime ORDER BY timestamp DESC")
    fun getChargeLogsSinceFlow(sinceTime: Long): Flow<List<ChargeLogEntity>>

    // Maintenance Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceItems(items: List<MaintenanceItemEntity>)

    @Query("SELECT * FROM maintenance_items ORDER BY intervalKm ASC")
    suspend fun getMaintenanceItems(): List<MaintenanceItemEntity>

    @Query("SELECT * FROM maintenance_items ORDER BY intervalKm ASC")
    fun getMaintenanceItemsFlow(): Flow<List<MaintenanceItemEntity>>

    @Query("UPDATE maintenance_items SET lastServicedOdometerKm = :odoKm WHERE id = :id")
    suspend fun updateMaintenanceServiced(id: String, odoKm: Float)

    @Query("SELECT COUNT(*) FROM maintenance_items")
    suspend fun getMaintenanceItemCount(): Int
}
