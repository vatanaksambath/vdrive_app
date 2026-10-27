package com.khmerauto.nav.core.database.dao

import androidx.room.*
import com.khmerauto.nav.core.database.entity.ChargeSession
import com.khmerauto.nav.core.database.entity.TripRecord
import com.khmerauto.nav.core.database.entity.VehicleStateSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: VehicleStateSnapshot)

    @Query("SELECT * FROM vehicle_state_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(): VehicleStateSnapshot?

    @Query("SELECT * FROM vehicle_state_snapshots ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSnapshots(): Flow<List<VehicleStateSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripRecord): Long

    @Query("SELECT * FROM trip_records ORDER BY startTimeMs DESC")
    fun getAllTrips(): Flow<List<TripRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargeSession(session: ChargeSession): Long

    @Query("SELECT * FROM charge_sessions ORDER BY startTimeMs DESC")
    fun getAllChargeSessions(): Flow<List<ChargeSession>>
}
