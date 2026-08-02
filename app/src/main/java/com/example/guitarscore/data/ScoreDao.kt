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

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM scores WHERE id = :id")
    suspend fun getScore(id: Long): ScoreEntity?

    @Query("SELECT * FROM score_metadata WHERE scoreId = :scoreId")
    suspend fun getMetadata(scoreId: Long): ScoreMetadataEntity?

    @Query("SELECT * FROM turn_cues WHERE scoreId = :scoreId ORDER BY COALESCE(triggerBeat, triggerMillis / 1000.0), pageIndex")
    fun observeCues(scoreId: Long): Flow<List<TurnCueEntity>>

    @Query("SELECT * FROM score_chords WHERE scoreId = :scoreId ORDER BY addedAt")
    fun observeScoreChords(scoreId: Long): Flow<List<ScoreChordEntity>>

    @Query("SELECT * FROM quiz_chords ORDER BY addedAt")
    fun observeQuizChords(): Flow<List<QuizChordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizChord(chord: QuizChordEntity)

    @Query("DELETE FROM quiz_chords WHERE chordName = :chordName")
    suspend fun deleteQuizChord(chordName: String)

    @Insert
    suspend fun insertScore(score: ScoreEntity): Long

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: ScoreMetadataEntity)

    @Insert
    suspend fun insertCue(cue: TurnCueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScoreChord(chord: ScoreChordEntity)

    @Update
    suspend fun updateScore(score: ScoreEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("UPDATE scores SET folderId = NULL, updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long, updatedAt: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderRow(folderId: Long)

    @Query("DELETE FROM scores WHERE id = :scoreId")
    suspend fun deleteScore(scoreId: Long)

    @Query("DELETE FROM turn_cues WHERE id = :cueId")
    suspend fun deleteCue(cueId: Long)

    @Query("DELETE FROM score_chords WHERE scoreId = :scoreId AND chordName = :chordName")
    suspend fun deleteScoreChord(scoreId: Long, chordName: String)

    @Transaction
    suspend fun addScore(score: ScoreEntity): Long {
        val id = insertScore(score)
        upsertMetadata(ScoreMetadataEntity(scoreId = id))
        return id
    }

    @Transaction
    suspend fun deleteFolder(folderId: Long) {
        clearFolder(folderId, System.currentTimeMillis())
        deleteFolderRow(folderId)
    }
}
