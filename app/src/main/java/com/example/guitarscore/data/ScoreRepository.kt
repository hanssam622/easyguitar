package com.example.guitarscore.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.Flow

class ScoreRepository(private val dao: ScoreDao) {
    fun observeScores(): Flow<List<ScoreEntity>> = dao.observeScores()
    fun observeCues(scoreId: Long): Flow<List<TurnCueEntity>> = dao.observeCues(scoreId)

    suspend fun addPdf(contentResolver: ContentResolver, uri: Uri): Long {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val title = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: "Untitled score"
        return dao.addScore(ScoreEntity(title = title.removeSuffix(".pdf"), pdfUri = uri.toString()))
    }

    suspend fun loadScore(id: Long): ScoreWithMetadata? {
        val score = dao.getScore(id) ?: return null
        val metadata = dao.getMetadata(id) ?: ScoreMetadataEntity(scoreId = id)
        return ScoreWithMetadata(score, metadata)
    }

    suspend fun updateScore(score: ScoreEntity) = dao.updateScore(score)
    suspend fun saveMetadata(metadata: ScoreMetadataEntity) = dao.upsertMetadata(metadata)
    suspend fun addCue(cue: TurnCueEntity) = dao.insertCue(cue)
    suspend fun deleteCue(cueId: Long) = dao.deleteCue(cueId)
}
