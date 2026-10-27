package com.khmerauto.nav.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.khmerauto.nav.core.database.dao.PlaceDao
import com.khmerauto.nav.core.database.dao.VehicleDao
import com.khmerauto.nav.core.database.dao.VoiceEnrollmentDao
import com.khmerauto.nav.core.database.entity.ChargeSession
import com.khmerauto.nav.core.database.entity.EnrolledPhraseEntity
import com.khmerauto.nav.core.database.entity.PlaceEntity
import com.khmerauto.nav.core.database.entity.PlaceFtsEntity
import com.khmerauto.nav.core.database.entity.TripRecord
import com.khmerauto.nav.core.database.entity.VehicleStateSnapshot

@Database(
    entities = [
        VehicleStateSnapshot::class,
        TripRecord::class,
        ChargeSession::class,
        PlaceEntity::class,
        PlaceFtsEntity::class,
        EnrolledPhraseEntity::class,
        HardwareSnapshotEntity::class,
        TripLogEntity::class,
        ChargeLogEntity::class,
        MaintenanceItemEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun placeDao(): PlaceDao
    abstract fun voiceEnrollmentDao(): VoiceEnrollmentDao
    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kmautonav_geely_m7.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
