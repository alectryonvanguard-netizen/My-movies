package com.mymovie.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val uid: String,
    val tmdbId: Int,
    val type: String,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String,
    val rating: Double,
    val inWatchlist: Boolean,
    val isFavorite: Boolean,
    val addedAt: Long
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val uid: String,
    val mediaKey: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val sourceUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaKey: String,
    val label: String,
    val url: String
)

@Entity(tableName = "local_videos")
data class LocalVideoEntity(@PrimaryKey val uri: String, val name: String, val addedAt: Long)

/** status: QUEUED, RUNNING, PAUSED, COMPLETED, FAILED */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val filePath: String,
    val posterUrl: String?,
    val mediaKey: String?,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val addedAt: Long
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val term: String, val kind: String, val updatedAt: Long)
