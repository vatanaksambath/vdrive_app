package com.khmerauto.nav.core.database.dao

import androidx.room.*
import com.khmerauto.nav.core.database.entity.EnrolledPhraseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceEnrollmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollment(entity: EnrolledPhraseEntity): Long

    @Query("SELECT * FROM enrolled_phrases ORDER BY id DESC")
    suspend fun getAllEnrollments(): List<EnrolledPhraseEntity>

    @Query("SELECT * FROM enrolled_phrases ORDER BY id DESC")
    fun getAllEnrollmentsFlow(): Flow<List<EnrolledPhraseEntity>>

    @Query("SELECT * FROM enrolled_phrases WHERE commandId = :commandId")
    suspend fun getEnrollmentsForCommand(commandId: String): List<EnrolledPhraseEntity>

    @Query("DELETE FROM enrolled_phrases WHERE id = :id")
    suspend fun deleteEnrollment(id: Long)

    @Query("DELETE FROM enrolled_phrases WHERE commandId = :commandId")
    suspend fun deleteEnrollmentsForCommand(commandId: String)

    @Query("SELECT COUNT(*) FROM enrolled_phrases")
    suspend fun getEnrollmentCount(): Int
}
