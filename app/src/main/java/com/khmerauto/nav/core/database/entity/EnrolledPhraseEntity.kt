package com.khmerauto.nav.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Persisted local acoustic voice template for zero-latency (< 120ms) offline command recognition.
 * Stores the 26-dimensional (13 MFCC + 13 Delta) feature matrix captured during on-car voice enrollment.
 */
@Entity(tableName = "enrolled_phrases")
@Serializable
data class EnrolledPhraseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandId: String,
    val phraseTextKm: String,
    val phraseTextEn: String,
    val category: String,
    
    // Serialized MFCC + Delta feature frames matrix (CSV or flattened float representation)
    val mfccFeatureData: String,
    val frameCount: Int,
    val featureDimension: Int = 26, // 13 MFCC + 13 Delta coefficients
    
    val durationMs: Long,
    val enrolledAtMs: Long = System.currentTimeMillis()
)
