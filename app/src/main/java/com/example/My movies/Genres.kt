package com.mymovie.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mymovie.app.data.MediaType

/** TMDB genre ids differ between movies and TV, so each genre maps both. */
data class Genre(val name: String, val movieId: Int, val tvId: Int, val colors: List<Color>, val icon: ImageVector) {
    fun idFor(t: MediaType) = if (t == MediaType.MOVIE) movieId else tvId
}

object Genres {
    // TV has no Horror/Romance/Thriller genre: closest TV genres are used (Mystery / Drama).
    val all = listOf(
        Genre("Action", 28, 10759, listOf(Color(0xFF7C2D12), Color(0xFF111827)), Icons.Filled.Bolt),
        Genre("Adventure", 12, 10759, listOf(Color(0xFF065F46), Color(0xFF0B1220)), Icons.Filled.Explore),
        Genre("Animation", 16, 16, listOf(Color(0xFF9D174D), Color(0xFF1E1B4B)), Icons.Filled.Brush),
        Genre("Comedy", 35, 35, listOf(Color(0xFFB45309), Color(0xFF1F1300)), Icons.Filled.SentimentVerySatisfied),
        Genre("Documentary", 99, 99, listOf(Color(0xFF155E75), Color(0xFF0B1220)), Icons.Filled.Videocam),
        Genre("Fantasy", 14, 10765, listOf(Color(0xFF6D28D9), Color(0xFF0F172A)), Icons.Filled.AutoAwesome),
        Genre("Horror", 27, 9648, listOf(Color(0xFF7F1D1D), Color(0xFF0A0A0A)), Icons.Filled.DarkMode),
        Genre("Romance", 10749, 18, listOf(Color(0xFFBE185D), Color(0xFF1F0A14)), Icons.Filled.Favorite),
        Genre("Sci-Fi", 878, 10765, listOf(Color(0xFF1D4ED8), Color(0xFF020617)), Icons.Filled.Science),
        Genre("Thriller", 53, 9648, listOf(Color(0xFF334155), Color(0xFF020617)), Icons.Filled.Visibility)
    )

    fun byName(n: String?) = all.firstOrNull { it.name.equals(n, ignoreCase = true) }
}
