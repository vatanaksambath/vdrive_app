package com.khmerauto.nav.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charge_sessions")
data class ChargeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startSocPercent: Int,
    val endSocPercent: Int,
    val energyAddedKwh: Float,
    val peakPowerKw: Float,
    val costKhr: Int, // Cost in Cambodian Riel (default tariff ~730 KHR / kWh)
    val chargerLocation: String = "Home Charging",
    val isReconciledGapCharge: Boolean = false
)
