package com.mymovie.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymovie.app.AppContainer
import com.mymovie.app.data.*
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.PosterGrid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CatalogKind(val title: String) { MOVIES("Movies"), TV("TV Series"), ANIME("Anime") }

data class Category(val label: String, val type: MediaType, val params: Map<String, String>, val trending: Boolean = false)

private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun categoriesFor(kind: CatalogKind): List<Category> {
    val t = today()
    return when (kind) {
        CatalogKind.MOVIES -> listOf(
            Category("Popular", MediaType.MOVIE, mapOf("sort_by" to "popularity.desc")),
            Category("Top rated", MediaType.MOVIE, mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "500")),
            Category("Trending", MediaType.MOVIE, emptyMap(), trending = true),
            Category("Latest", MediaType.MOVIE, mapOf("sort_by" to "primary_release_date.desc", "primary_release_date.lte" to t, "vote_count.gte" to "30"))
        )
        CatalogKind.TV -> listOf(
            Category("Popular", MediaType.TV, mapOf("sort_by" to "popularity.desc")),
            Category("Top rated", MediaType.TV, mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "300")),
            Category("Trending", MediaType.TV, emptyMap(), trending = true),
            Category("Latest", MediaType.TV, mapOf("sort_by" to "first_air_date.desc", "first_air_date.lte" to t, "vote_count.gte" to "10"))
        )
        CatalogKind.ANIME -> listOf(
            Category("Popular series", MediaType.TV, ANIME_PARAMS + ("sort_by" to "popularity.desc")),
            Category("Top rated", MediaType.TV, ANIME_PARAMS + mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "200")),
            Category("Anime movies", MediaType.MOVIE, ANIME_PARAMS + ("sort_by" to "popularity.desc")),
            Category("Latest", MediaType.TV, ANIME_PARAMS + mapOf("sort_by" to "first_air_date.desc", "first_air_date.lte" to t, "vote_count.gte" to "5"))
        )
    }
}

class CatalogViewModel(c: AppContainer, private val kind: CatalogKind) : ViewModel() {
    private val repo = c.repo
    val categories = categoriesFor(kind)
    var category by mutableIntStateOf(0); private set
    var genre by mutableStateOf<Genre?>(null); private set

    val paginator = Paginator(viewModelScope) { page -> fetch(page) }

    init { paginator.reset() }

    fun selectCategory(i: Int) { if (i != category) { category = i; paginator.reset() } }
    fun selectGenre(g: Genre?) { genre = if (genre == g) null else g; paginator.reset() }

    private suspend fun fetch(page: Int): Page<MediaItem> {
        val cat = categories[category]
        val g = if (kind == CatalogKind.ANIME) null else genre
        return if (cat.trending) {
            val r = repo.trending(cat.type, page)
            if (g == null) r else r.copy(items = r.items.filter { g.idFor(cat.type) in it.genreIds })
        } else {
            val params = if (g == null) cat.params else cat.params + ("with_genres" to g.idFor(cat.type).toString())
            repo.discover(cat.type, page, params)
        }
    }
}

@Composable
fun CatalogScreen(kind: CatalogKind, onItem: (MediaItem) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel(key = "catalog-${kind.name}") { CatalogViewModel(it, kind) }
    Column(Modifier.fillMaxSize()) {
        Text(kind.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.categories) { i, c ->
                FilterChip(selected = vm.category == i, onClick = { vm.selectCategory(i) }, label = { Text(c.label) })
            }
        }
        if (kind != CatalogKind.ANIME) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(Genres.all) { _, g ->
                    FilterChip(selected = vm.genre == g, onClick = { vm.selectGenre(g) }, label = { Text(g.name) })
                }
            }
        }
        PosterGrid(vm.paginator, onItem, onOpenSettings, Modifier.weight(1f))
    }
}
