package com.example.guitarscore.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores ORDER BY favorite DESC, updatedAt DESC")
    fun observeScores(): Flow<List<ScoreEntity>>

    @Query("SELECT * FROM scores WHERE id = :id")
    suspend fun getScore(id: Long): ScoreEntity?

    @Query("SELECT * FROM score_metadata WHERE scoreId = :scoreId")
    suspend fun getMetadata(scoreId: Long): ScoreMetadataEntity?

    @Query("SELECT * FROM turn_cues WHERE scoreId = :scoreId ORDER BY COALESCE(triggerBeat, triggerMillis / 1000.0), pageIndex")
    fun observeCues(scoreId: Long): Flow<List<TurnCueEntity>>

    @Insert
    suspend fun insertScore(score: ScoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: ScoreMetadataEntity)

    @Insert
    suspend fun insertCue(cue: TurnCueEntity): Long

    @Update
    suspend fun updateScore(score: ScoreEntity)

    @Query("DELETE FROM turn_cues WHERE id = :cueId")
    suspend fun deleteCue(cueId: Long)

    @Transaction
    suspend fun addScore(score: ScoreEntity): Long {
        val id = insertScore(score)
        upsertMetadata(ScoreMetadataEntity(scoreId = id))
        return id
    }
}
