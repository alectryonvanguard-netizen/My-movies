package com.mymovie.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Library (watchlist + favourites)
    @Query("SELECT * FROM library WHERE inWatchlist = 1 ORDER BY addedAt DESC")
    fun watchlist(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun favourites(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library WHERE uid = :uid")
    fun libraryItem(uid: String): Flow<LibraryEntity?>

    @Query("SELECT * FROM library WHERE uid = :uid")
    suspend fun getLibraryItem(uid: String): LibraryEntity?

    @Query("SELECT * FROM library ORDER BY addedAt DESC LIMIT 4")
    suspend fun recentLibrary(): List<LibraryEntity>

    @Upsert suspend fun upsertLibrary(e: LibraryEntity)

    @Query("DELETE FROM library WHERE uid = :uid")
    suspend fun deleteLibrary(uid: String)

    // History / continue watching
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun history(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT 5")
    suspend fun recentHistory(): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE finished = 0 AND positionMs > 5000 ORDER BY updatedAt DESC LIMIT 20")
    fun continueWatching(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE uid = :uid")
    suspend fun getHistory(uid: String): HistoryEntity?

    @Upsert suspend fun upsertHistory(e: HistoryEntity)

    @Query("DELETE FROM history WHERE uid = :uid")
    suspend fun deleteHistory(uid: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // User-added sources (links / local files attached to a title or episode)
    @Query("SELECT * FROM sources WHERE mediaKey = :key ORDER BY id")
    fun sources(key: String): Flow<List<SourceEntity>>

    @Insert suspend fun insertSource(e: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    // Personal media files
    @Query("SELECT * FROM local_videos ORDER BY addedAt DESC")
    fun localVideos(): Flow<List<LocalVideoEntity>>

    @Upsert suspend fun upsertLocal(e: LocalVideoEntity)

    @Query("DELETE FROM local_videos WHERE uri = :uri")
    suspend fun deleteLocal(uri: String)

    // Downloads
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun downloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownload(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE url = :url LIMIT 1")
    suspend fun downloadByUrl(url: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','RUNNING')")
    suspend fun activeDownloads(): List<DownloadEntity>

    @Insert suspend fun insertDownload(e: DownloadEntity): Long

    @Query("UPDATE downloads SET downloadedBytes = :done, totalBytes = :total, status = :status WHERE id = :id")
    suspend fun setProgress(id: Long, done: Long, total: Long, status: String)

    @Query("UPDATE downloads SET status = 'PAUSED' WHERE status IN ('QUEUED','RUNNING')")
    suspend fun pauseAllActive()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    // Search history
    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC LIMIT 40")
    fun searchHistory(): Flow<List<SearchHistoryEntity>>

    @Upsert suspend fun upsertSearch(e: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE term = :term")
    suspend fun deleteSearch(term: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearches()
}
