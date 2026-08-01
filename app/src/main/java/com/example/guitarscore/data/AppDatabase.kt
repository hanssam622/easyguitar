package com.example.guitarscore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScoreEntity::class,
        FolderEntity::class,
        ScoreMetadataEntity::class,
        TurnCueEntity::class,
        ScoreChordEntity::class
    ],
    version = 3,
    // 손으로 쓴 마이그레이션을 검증하려면 스키마 JSON 이 있어야 한다.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scores ADD COLUMN folderId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scores_folderId ON scores(folderId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS score_chords (
                        scoreId INTEGER NOT NULL,
                        chordName TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(scoreId, chordName),
                        FOREIGN KEY(scoreId) REFERENCES scores(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_score_chords_scoreId ON score_chords(scoreId)")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "guitar-score.db"
        ).addMigrations(migration1To2, migration2To3).build()
    }
}
