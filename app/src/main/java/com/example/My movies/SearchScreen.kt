package com.mymovie.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mymovie.app.AppContainer
import com.mymovie.app.data.*
import com.mymovie.app.data.local.SearchHistoryEntity
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.*
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SportsHits(val leagues: List<SportLeague>, val teams: List<TeamResult>, val players: List<PlayerResult>, val events: List<SportEvent>)

class SearchViewModel(private val c: AppContainer, initial: String?) : ViewModel() {
    var query by mutableStateOf(initial.orEmpty())
    var scope by mutableIntStateOf(0) // 0 movies, 1 people, 2 sports
    private val dao = c.db.dao()
    val history = dao.searchHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val movies = MutableStateFlow<UiState<List<MediaItem>>?>(null)
    val people = MutableStateFlow<UiState<List<PersonBrief>>?>(null)
    val sports = MutableStateFlow<UiState<SportsHits>?>(null)
    private var job: Job? = null

    fun run() {
        job?.cancel()
        val q = query.trim()
        if (q.length < 2) { movies.value = null; people.value = null; sports.value = null; return }
        val s = scope
        job = viewModelScope.launch {
            delay(350)
            when (s) {
                0 -> { movies.value = UiState.Loading; movies.value = runUi { coroutineScope {
                    val m = async { c.repo.search(MediaType.MOVIE, q, 1, null).items }
                    val t = async { c.repo.search(MediaType.TV, q, 1, null).items }
                    (m.await() + t.await()).sortedByDescending { it.rating }
                } } }
                1 -> { people.value = UiState.Loading; people.value = runUi { c.repo.searchPeople(q) } }
                else -> { sports.value = UiState.Loading; sports.value = runUi { coroutineScope {
                    val sp = c.sports
                    val l = async { runCatching { sp.searchLeagues(q) }.getOrDefault(emptyList()) }
                    val t = async { runCatching { sp.searchTeams(q) }.getOrDefault(emptyList()) }
                    val p = async { runCatching { sp.searchPlayers(q) }.getOrDefault(emptyList()) }
                    val e = async { runCatching { sp.searchEvents(q) }.getOrDefault(emptyList()) }
                    SportsHits(l.await(), t.await(), p.await(), e.await())
                } } }
            }
        }
    }

    fun commit() {
        val q = query.trim()
        if (q.length >= 2) viewModelScope.launch { dao.upsertSearch(SearchHistoryEntity(q, listOf("movies", "people", "sports")[scope], System.currentTimeMillis())) }
    }
    fun deleteHistory(t: String) { viewModelScope.launch { dao.deleteSearch(t) } }
    fun clearHistory() { viewModelScope.launch { dao.clearSearches() } }
}

@Composable
fun SearchScreen(
    initial: String?, onItem: (MediaItem) -> Unit, onPerson: (Int) -> Unit, onLeague: (SportLeague) -> Unit,
    onDiscover: (genre: String?, country: String?, lang: String?) -> Unit, onOpenSettings: () -> Unit
) {
    val vm = appViewModel(key = "search-$initial") { SearchViewModel(it, initial) }
    val history by vm.history.collectAsStateWithLifecycle()
    val movies by vm.movies.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val sports by vm.sports.collectAsStateWithLifecycle()
    LaunchedEffect(vm.query, vm.scope) { vm.run() }
    val q = vm.query.trim()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                vm.query, { vm.query = it }, singleLine = true, placeholder = { Text("Movies, actors, directors, teams, leagues…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (vm.query.isNotEmpty()) IconButton({ vm.query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.commit() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Movies & TV", "People", "Sports").forEachIndexed { i, t -> FilterChip(vm.scope == i, { vm.scope = i }, { Text(t) }) }
            }
        }
        if (q.length < 2) {
            if (history.isNotEmpty()) {
                item { SectionHeader("Recent searches") }
                items(history, key = { it.term }) { h ->
                    Row(Modifier.fillMaxWidth().clickable { vm.query = h.term; vm.scope = listOf("movies", "people", "sports").indexOf(h.kind).coerceAtLeast(0) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = TextDim)
                        Text(h.term, Modifier.weight(1f).padding(12.dp)); Text(h.kind, color = TextDim, style = MaterialTheme.typography.labelSmall)
                        IconButton({ vm.deleteHistory(h.term) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
                item { TextButton({ vm.clearHistory() }, Modifier.padding(horizontal = 8.dp)) { Text("Clear search history") } }
            } else item { Text("Search movies, actors, directors, genres, countries, languages — or switch to Sports for teams, players and leagues.", color = TextDim, modifier = Modifier.padding(16.dp)) }
        } else {
            if (vm.scope == 0) {
                val g = Genres.all.filter { it.name.contains(q, true) }
                val c = COUNTRIES.filter { it.label.contains(q, true) }
                val l = LANGUAGES.filter { it.label.contains(q, true) }
                if (g.isNotEmpty() || c.isNotEmpty() || l.isNotEmpty()) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(g) { x -> AssistChip({ onDiscover(x.name, null, null) }, { Text("Genre: ${x.name}") }) }
                        items(c) { x -> AssistChip({ onDiscover(null, x.code, null) }, { Text("Country: ${x.label}") }) }
                        items(l) { x -> AssistChip({ onDiscover(null, null, x.code) }, { Text("Language: ${x.label}") }) }
                    }
                }
                when (val m = movies) {
                    null, UiState.Loading -> item { CenteredLoading() }
                    is UiState.Error -> item { ErrorView(m, { vm.run() }, onOpenSettings) }
                    is UiState.Success -> if (m.data.isEmpty()) item { Text("No movies or series found.", color = TextDim, modifier = Modifier.padding(16.dp)) }
                    else items(m.data, key = { it.key }) { it2 -> MediaResultRow(it2) { vm.commit(); onItem(it2) } }
                }
            } else if (vm.scope == 1) {
                when (val p = people) {
                    null, UiState.Loading -> item { CenteredLoading() }
                    is UiState.Error -> item { ErrorView(p, { vm.run() }, onOpenSettings) }
                    is UiState.Success -> if (p.data.isEmpty()) item { Text("No people found.", color = TextDim, modifier = Modifier.padding(16.dp)) }
                    else items(p.data, key = { it.id }) { x ->
                        Row(Modifier.fillMaxWidth().clickable { vm.commit(); onPerson(x.id) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(x.photoUrl, x.name, Modifier.size(56.dp).clip(CircleShape).background(CardColor), contentScale = ContentScale.Crop)
                            Column(Modifier.padding(12.dp)) { Text(x.name, style = MaterialTheme.typography.labelLarge); Text(x.department, color = TextDim, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            } else {
                when (val s = sports) {
                    null, UiState.Loading -> item { CenteredLoading() }
                    is UiState.Error -> item { ErrorView(s, { vm.run() }, onOpenSettings) }
                    is UiState.Success -> {
                        val h = s.data
                        if (h.leagues.isEmpty() && h.teams.isEmpty() && h.players.isEmpty() && h.events.isEmpty()) item { Text("No sports results.", color = TextDim, modifier = Modifier.padding(16.dp)) }
                        if (h.leagues.isNotEmpty()) { item { SectionHeader("Competitions") }; items(h.leagues, key = { it.provider + it.id }) { l ->
                            Text("${l.name}  ·  ${l.sport}", Modifier.fillMaxWidth().clickable { vm.commit(); onLeague(l) }.padding(horizontal = 16.dp, vertical = 10.dp)) } }
                        if (h.teams.isNotEmpty()) { item { SectionHeader("Teams") }; items(h.teams, key = { it.name + it.league }) { t ->
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                                AsyncImage(t.badge, t.name, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(t.name, style = MaterialTheme.typography.labelLarge)
                                    Text(listOf(t.sport, t.league, t.country, t.stadium).filter { it.isNotBlank() }.joinToString(" · "), color = TextDim, style = MaterialTheme.typography.labelSmall)
                                    if (t.description.isNotBlank()) Text(t.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            } } }
                        if (h.players.isNotEmpty()) { item { SectionHeader("Players") }; items(h.players, key = { it.name + it.team }) { p ->
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(p.photo, p.name, Modifier.size(48.dp).clip(CircleShape).background(CardColor), contentScale = ContentScale.Crop)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.labelLarge)
                                    Text(listOf(p.team, p.position, p.nationality, p.sport).filter { it.isNotBlank() }.joinToString(" · "), color = TextDim, style = MaterialTheme.typography.labelSmall)
                                }
                            } } }
                        if (h.events.isNotEmpty()) { item { SectionHeader("Events") }; items(h.events, key = { it.key }) { e ->
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                Text(e.title, style = MaterialTheme.typography.labelLarge)
                                Text(listOfNotNull(e.league.ifBlank { null }, formatLocal(e.startMillis), if (e.homeScore != null) "${e.homeScore}-${e.awayScore}" else null).joinToString(" · "), color = TextDim, style = MaterialTheme.typography.labelSmall)
                            } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaResultRow(m: MediaItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(m.posterUrl, m.title, Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(CardColor), contentScale = ContentScale.Crop)
        Column(Modifier.padding(12.dp)) {
            Text(m.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(if (m.type == MediaType.TV) "TV" else "Movie", m.year, if (m.rating > 0) "★ %.1f".format(m.rating) else "").filter { it.isNotBlank() }.joinToString(" · "), color = TextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
