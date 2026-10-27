package com.khmerauto.nav.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_records")
data class TripRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startOdometerKm: Float,
    val endOdometerKm: Float,
    val distanceKm: Float,
    val energyUsedKwh: Float,
    val efficiencyKwhPer100Km: Float,
    val costKhr: Int, // Cost in Cambodian Riel
    val isReconciledGapTrip: Boolean = false
)
