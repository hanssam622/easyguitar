package com.example.guitarscore.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scores", indices = [Index("folderId")])
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "",
    val pdfUri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val lastOpenedPage: Int = 0,
    val folderId: Long? = null
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
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
    /** 박자표의 분자. 한 마디에 들어가는 박의 수. */
    val timeSignature: Int = 4,
    /** 박자표의 분모. 4 면 4분음표가 한 박, 8 이면 8분음표가 한 박(6/8 같은 겹박자). */
    val beatUnit: Int = 4,
    val tuningPreset: String = "Standard",
    val capo: Int = 0,
    val notes: String = ""
) {
    /** 사람이 읽는 박자표. 예: 4/4, 6/8 */
    val timeSignatureLabel: String get() = "$timeSignature/$beatUnit"

    /**
     * 강세를 묶는 단위. 6/8·9/8·12/8 같은 겹박자는 3개씩 묶어 세므로 1박과 4박에 강세가 온다.
     * 그 외에는 마디 첫 박에만 강세를 준다.
     */
    val beatGroupSize: Int
        get() = if (beatUnit == 8 && timeSignature % 3 == 0 && timeSignature > 3) 3 else 1
}

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

@Entity(
    tableName = "score_chords",
    primaryKeys = ["scoreId", "chordName"],
    indices = [Index("scoreId")],
    foreignKeys = [ForeignKey(
        entity = ScoreEntity::class,
        parentColumns = ["id"],
        childColumns = ["scoreId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ScoreChordEntity(
    val scoreId: Long,
    val chordName: String,
    val addedAt: Long = System.currentTimeMillis()
)

/** 사용자가 외우려고 따로 모아 둔 코드. 퀴즈의 "내 코드 모음" 출처가 된다. */
@Entity(tableName = "quiz_chords", primaryKeys = ["chordName"])
data class QuizChordEntity(
    val chordName: String,
    val addedAt: Long = System.currentTimeMillis()
)

data class ScoreWithMetadata(
    val score: ScoreEntity,
    val metadata: ScoreMetadataEntity
)
