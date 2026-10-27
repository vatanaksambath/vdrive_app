package com.khmerauto.nav.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_state_snapshots")
data class VehicleStateSnapshot(
    @PrimaryKey val timestamp: Long = System.currentTimeMillis(),
    val odometerKm: Float,
    val batteryPercent: Int,
    val usableKwh: Float,
    val isCharging: Boolean,
    val latitude: Double = 11.5564, // Phnom Penh center
    val longitude: Double = 104.9282,
    val speedKmh: Float = 0.0f
)
