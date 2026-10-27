package com.khmerauto.nav.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Full-Text Search virtual table for lightning-fast (< 30ms) offline search
 * across Khmer Unicode and English POI place names.
 */
@Entity(tableName = "places_fts")
@Fts4(contentEntity = PlaceEntity::class)
data class PlaceFtsEntity(
    val nameKm: String,
    val nameEn: String,
    val addressKm: String,
    val addressEn: String,
    val city: String,
    val operator: String
)
