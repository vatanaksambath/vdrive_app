package com.khmerauto.nav.core.database.dao

import androidx.room.*
import com.khmerauto.nav.core.database.entity.PlaceCategory
import com.khmerauto.nav.core.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<PlaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity): Long

    @Query("SELECT * FROM places ORDER BY id ASC")
    fun getAllPlacesFlow(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY id ASC")
    suspend fun getAllPlaces(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE isEvCharger = 1 ORDER BY maxPowerKw DESC")
    suspend fun getAllEvChargers(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE category = :category ORDER BY id ASC")
    suspend fun getPlacesByCategory(category: PlaceCategory): List<PlaceEntity>

    @Query("""
        SELECT places.* FROM places
        JOIN places_fts ON places.id = places_fts.rowid
        WHERE places_fts MATCH :query
        LIMIT 50
    """)
    suspend fun searchFts(query: String): List<PlaceEntity>

    @Query("""
        SELECT * FROM places
        WHERE nameKm LIKE '%' || :query || '%'
           OR nameEn LIKE '%' || :query || '%'
           OR addressKm LIKE '%' || :query || '%'
           OR addressEn LIKE '%' || :query || '%'
           OR operator LIKE '%' || :query || '%'
        ORDER BY isEvCharger DESC, id ASC
        LIMIT 50
    """)
    suspend fun searchLike(query: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE isCustomPinnedByUser = 1 AND isSyncedWithServer = 0")
    suspend fun getUnsyncedPinnedPlaces(): List<PlaceEntity>

    @Query("UPDATE places SET isSyncedWithServer = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT COUNT(*) FROM places")
    suspend fun getPlaceCount(): Int
}
