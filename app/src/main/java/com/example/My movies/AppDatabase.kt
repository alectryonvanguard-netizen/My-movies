package com.mymovie.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LibraryEntity::class, HistoryEntity::class, SourceEntity::class, LocalVideoEntity::class,
        DownloadEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** Keeps watchlist, history and sources; replaces the v1 system-downloader table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS downloads")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS downloads (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, " +
                        "url TEXT NOT NULL, filePath TEXT NOT NULL, posterUrl TEXT, mediaKey TEXT, status TEXT NOT NULL, " +
                        "downloadedBytes INTEGER NOT NULL, totalBytes INTEGER NOT NULL, addedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS search_history (term TEXT NOT NULL, kind TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(term))")
            }
        }
    }
}
