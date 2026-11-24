package com.mymovie.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LibraryEntity::class, HistoryEntity::class, SourceEntity::class, LocalVideoEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
