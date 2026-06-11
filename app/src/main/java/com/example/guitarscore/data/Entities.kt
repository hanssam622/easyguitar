package com.example.guitarscore.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "",
    val pdfUri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val lastOpenedPage: Int = 0
)

@Entity(
    tableName = "score_metadata",
    primaryKeys = ["scoreId"],
    foreignKeys = [ForeignKey(
        entity = ScoreEntity::class,
        parentColumns = ["id"],
        childColumns = ["scoreId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ScoreMetadataEntity(
    val scoreId: Long,
    val bpm: Int = 120,
    val timeSignature: Int = 4,
    val tuningPreset: String = "Standard",
    val capo: Int = 0,
    val notes: String = ""
)

@Entity(
    tableName = "turn_cues",
    indices = [Index("scoreId")],
    foreignKeys = [ForeignKey(
        entity = ScoreEntity::class,
        parentColumns = ["id"],
        childColumns = ["scoreId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TurnCueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scoreId: Long,
    val pageIndex: Int,
    val triggerBeat: Float?,
    val triggerMillis: Long?
)

data class ScoreWithMetadata(
    val score: ScoreEntity,
    val metadata: ScoreMetadataEntity
)
