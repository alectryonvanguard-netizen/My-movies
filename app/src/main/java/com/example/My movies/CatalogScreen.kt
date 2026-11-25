package com.mymovie.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymovie.app.AppContainer
import com.mymovie.app.data.*
import com.mymovie.app.data.remote.Network
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.DropdownChip
import com.mymovie.app.ui.components.PosterGrid
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class CatalogKind(val title: String) { MOVIES("Movies"), TV("TV Series"), ANIME("Anime") }

data class Category(val label: String, val type: MediaType, val params: Map<String, String>, val trending: Boolean = false)
data class Region(val label: String, val countries: String?)

val REGIONS = listOf(
    Region("Worldwide", null), Region("Korea", "KR"), Region("Japan", "JP"), Region("China", "CN|HK|TW"),
    Region("India", "IN"), Region("Nigeria", "NG"), Region("Kenya", "KE"),
    Region("Africa", "NG|KE|ZA|GH|EG|MA|TZ|UG|ET|SN|CI|CM|DZ|TN|RW|ZW"),
    Region("Europe", "GB|FR|DE|IT|ES|SE|DK|NO|PL|NL|BE|CH|AT|IE|FI|PT|GR|CZ|HU"),
    Region("Americas", "US|CA|MX|BR|AR|CO|CL|PE")
)
val SORTS = listOf(
    "Default" to null, "Popularity" to "popularity.desc", "Rating" to "vote_average.desc",
    "Release date" to "primary_release_date.desc", "Revenue" to "revenue.desc"
)
val MOVIE_CATEGORIES = listOf("Trending", "Popular", "Now playing", "Upcoming", "Top rated", "Latest")

private fun dayOffset(days: Int): String {
    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

fun categoriesFor(kind: CatalogKind): List<Category> {
    val t = dayOffset(0)
    return when (kind) {
        CatalogKind.MOVIES -> MOVIE_CATEGORIES.map { Category(it, MediaType.MOVIE, emptyMap(), it == "Trending") }
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
    var regionIdx by mutableIntStateOf(0); private set
    var sortIdx by mutableIntStateOf(0); private set

    val paginator = Paginator(viewModelScope) { page -> fetch(page) }

    init { paginator.reset() }

    fun selectCategory(i: Int) { if (i != category) { category = i; paginator.reset() } }
    fun selectGenre(g: Genre?) { genre = if (genre == g) null else g; paginator.reset() }
    fun selectRegion(i: Int) { regionIdx = i; paginator.reset() }
    fun selectSort(i: Int) { sortIdx = i; paginator.reset() }
    fun refresh() { Network.forceRefresh(); paginator.reset() }

    private fun movieParams(label: String): Map<String, String> {
        val region = REGIONS[regionIdx]
        val regional = region.countries != null
        val base = when (label) {
            "Popular" -> mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "20")
            "Now playing" -> mapOf("sort_by" to "popularity.desc", "primary_release_date.gte" to dayOffset(-45), "primary_release_date.lte" to dayOffset(0))
            "Upcoming" -> mapOf("sort_by" to "popularity.desc", "primary_release_date.gte" to dayOffset(1), "primary_release_date.lte" to dayOffset(240))
            "Top rated" -> mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to if (regional) "150" else "500")
            "Latest" -> mapOf("sort_by" to "primary_release_date.desc", "primary_release_date.lte" to dayOffset(0), "vote_count.gte" to if (regional) "2" else "10")
            else -> mapOf("sort_by" to "popularity.desc", "primary_release_date.gte" to dayOffset(-365), "primary_release_date.lte" to dayOffset(0))
        }
        var p = base
        SORTS[sortIdx].second?.let { s ->
            p = p + ("sort_by" to s)
            if (s.startsWith("vote_average")) p = p + ("vote_count.gte" to "200")
        }
        region.countries?.let { p = p + ("with_origin_country" to it) }
        return p
    }

    private suspend fun fetch(page: Int): Page<MediaItem> {
        val cat = categories[category]
        val g = if (kind == CatalogKind.ANIME) null else genre
        val plainTrending = cat.trending && (kind != CatalogKind.MOVIES || (regionIdx == 0 && sortIdx == 0))
        if (plainTrending) {
            val r = repo.trending(cat.type, page)
            return if (g == null) r else r.copy(items = r.items.filter { g.idFor(cat.type) in it.genreIds })
        }
        val base = if (kind == CatalogKind.MOVIES) movieParams(cat.label) else cat.params
        val params = if (g == null) base else base + ("with_genres" to g.idFor(cat.type).toString())
        return repo.discover(cat.type, page, params)
    }
}

@Composable
fun CatalogScreen(kind: CatalogKind, onItem: (MediaItem) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel(key = "catalog-${kind.name}") { CatalogViewModel(it, kind) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.categories) { i, c ->
                FilterChip(selected = vm.category == i, onClick = { vm.selectCategory(i) }, label = { Text(c.label) })
            }
        }
        if (kind == CatalogKind.MOVIES) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(REGIONS) { i, r ->
                    FilterChip(selected = vm.regionIdx == i, onClick = { vm.selectRegion(i) }, label = { Text(r.label) })
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownChip("Sort", SORTS.drop(1).map { it.first }, vm.sortIdx.takeIf { it > 0 }?.minus(1)) { vm.selectSort(if (it == null) 0 else it + 1) }
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
