package com.mymovie.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymovie.app.AppContainer
import com.mymovie.app.data.*
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.DropdownChip
import com.mymovie.app.ui.components.PosterGrid
import kotlinx.coroutines.delay

data class Option(val label: String, val code: String)

val LANGUAGES = listOf(
    Option("English", "en"), Option("Spanish", "es"), Option("French", "fr"), Option("German", "de"),
    Option("Italian", "it"), Option("Portuguese", "pt"), Option("Japanese", "ja"), Option("Korean", "ko"),
    Option("Hindi", "hi"), Option("Chinese", "zh"), Option("Russian", "ru"), Option("Arabic", "ar"), Option("Turkish", "tr")
)
val COUNTRIES = listOf(
    Option("United States", "US"), Option("United Kingdom", "GB"), Option("France", "FR"), Option("Germany", "DE"),
    Option("Italy", "IT"), Option("Spain", "ES"), Option("Japan", "JP"), Option("South Korea", "KR"),
    Option("India", "IN"), Option("China", "CN"), Option("Brazil", "BR"), Option("Canada", "CA"), Option("Australia", "AU")
)

class DiscoverViewModel(c: AppContainer, preset: String?, presetCountry: String?, presetLang: String?) : ViewModel() {
    private val repo = c.repo
    var query by mutableStateOf("")
    var type by mutableStateOf(MediaType.MOVIE)
    var genre by mutableStateOf(Genres.byName(preset))
    var year by mutableStateOf("")
    var language by mutableStateOf(LANGUAGES.firstOrNull { it.code == presetLang })
    var country by mutableStateOf(COUNTRIES.firstOrNull { it.code == presetCountry })
    private var lastKey: String? = null

    val paginator = Paginator(viewModelScope) { page -> fetch(page) }

    private fun filterKey() = listOf(query.trim(), type, genre?.name, year, language?.code, country?.code).joinToString("|")

    /** Reloads only when filters changed, so returning from a detail page keeps the scroll position. */
    fun refreshIfNeeded() {
        val k = filterKey()
        if (k == lastKey && (paginator.items.isNotEmpty() || paginator.loading)) return
        lastKey = k
        paginator.reset()
    }

    private suspend fun fetch(page: Int): Page<MediaItem> {
        val q = query.trim(); val t = type; val g = genre; val lang = language; val ctry = country
        val y = year.takeIf { it.length == 4 }
        if (q.isEmpty()) {
            val p = buildMap {
                put("sort_by", "popularity.desc")
                put("vote_count.gte", "20")
                g?.let { put("with_genres", it.idFor(t).toString()) }
                y?.let { put(if (t == MediaType.MOVIE) "primary_release_year" else "first_air_date_year", it) }
                lang?.let { put("with_original_language", it.code) }
                ctry?.let { put("with_origin_country", it.code) }
            }
            return repo.discover(t, page, p)
        }
        val r = repo.search(t, q, page, y)
        return r.copy(items = r.items.filter {
            (g == null || g.idFor(t) in it.genreIds) && (lang == null || it.language == lang.code)
        })
    }
}

@Composable
fun DiscoverScreen(presetGenre: String?, presetCountry: String?, presetLang: String?, onItem: (MediaItem) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel(key = "discover-$presetGenre-$presetCountry-$presetLang") { DiscoverViewModel(it, presetGenre, presetCountry, presetLang) }
    LaunchedEffect(vm.query, vm.type, vm.genre, vm.year, vm.language, vm.country) {
        delay(400)
        vm.refreshIfNeeded()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
            placeholder = { Text("Search by title…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (vm.query.isNotEmpty()) IconButton({ vm.query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(vm.type == MediaType.MOVIE, { vm.type = MediaType.MOVIE }, { Text("Movies") }) }
            item { FilterChip(vm.type == MediaType.TV, { vm.type = MediaType.TV }, { Text("TV Series") }) }
            item {
                DropdownChip("Genre", Genres.all.map { it.name }, Genres.all.indexOf(vm.genre).takeIf { it >= 0 }) { vm.genre = it?.let(Genres.all::get) }
            }
            item {
                DropdownChip("Language", LANGUAGES.map { it.label }, LANGUAGES.indexOf(vm.language).takeIf { it >= 0 }) { vm.language = it?.let(LANGUAGES::get) }
            }
            item {
                DropdownChip("Country", COUNTRIES.map { it.label }, COUNTRIES.indexOf(vm.country).takeIf { it >= 0 }) { vm.country = it?.let(COUNTRIES::get) }
            }
            item {
                OutlinedTextField(
                    value = vm.year, onValueChange = { v -> vm.year = v.filter(Char::isDigit).take(4) }, singleLine = true,
                    placeholder = { Text("Year") }, modifier = Modifier.width(96.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        PosterGrid(vm.paginator, onItem, onOpenSettings, Modifier.weight(1f))
    }
}
