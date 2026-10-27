package com.khmerauto.nav.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class PlaceCategory {
    EV_CHARGER,
    REPAIR_SHOP,
    PETROL_STATION,
    SHOPPING,
    RESTAURANT,
    TOURISM_LANDMARK,
    SAVED_HOME,
    SAVED_WORK,
    FAVORITE
}

@Entity(tableName = "places")
@Serializable
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nameKm: String,
    val nameEn: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val addressKm: String,
    val addressEn: String,
    val city: String = "Phnom Penh",
    
    // EV Charging Station Specifications
    val isEvCharger: Boolean = false,
    val operator: String = "",           // TotalEnergies, EVX, Caltex, PTT, Geely Thor Supercharge
    val maxPowerKw: Int = 0,             // 60, 120, 180, 240 kW DC Fast
    val connectorTypes: String = "",     // "CCS2, GB-T, Type 2"
    val totalPlugs: Int = 2,
    val availablePlugs: Int = 2,
    val pricePerKwhKhr: Int = 1200,      // Tariff in Cambodian Riel (៛)
    
    // Offline user pinning & synchronization queue
    val isCustomPinnedByUser: Boolean = false,
    val isSyncedWithServer: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)
