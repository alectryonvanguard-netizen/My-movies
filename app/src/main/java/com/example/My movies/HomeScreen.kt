@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.mymovie.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.mymovie.app.AppContainer
import com.mymovie.app.appContainer
import com.mymovie.app.data.*
import com.mymovie.app.data.local.HistoryEntity
import com.mymovie.app.player.PlayerLauncher
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.*
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    data class Data(
        val hero: List<MediaItem>, val trending: List<MediaItem>, val movies: List<MediaItem>,
        val series: List<MediaItem>, val anime: List<MediaItem>, val recommended: List<MediaItem>
    )

    private val repo = c.repo
    private val dao = c.db.dao()
    val state = MutableStateFlow<UiState<Data>>(UiState.Loading)
    val free = MutableStateFlow<List<FreeItem>>(emptyList())
    val continueWatching = dao.continueWatching().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchlistCount = dao.watchlist().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val downloadCount = dao.downloads().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val historyCount = dao.history().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init { load() }

    fun load() {
        state.value = UiState.Loading
        viewModelScope.launch {
            launch { free.value = runCatching { repo.freeToWatch() }.getOrDefault(emptyList()) }
            state.value = runUi {
                coroutineScope {
                    val trending = async { repo.trending(null).items }
                    val movies = async { repo.popular(MediaType.MOVIE).items }
                    val tv = async { repo.popular(MediaType.TV).items }
                    val anime = async { repo.discover(MediaType.TV, 1, ANIME_PARAMS + ("sort_by" to "popularity.desc")).items }
                    val rec = async { runCatching { repo.personalRecommendations() }.getOrDefault(emptyList()) }
                    val t = trending.await()
                    Data(t.filter { it.backdropUrl != null }.take(6), t, movies.await(), tv.await(), anime.await(), rec.await())
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onItem: (MediaItem) -> Unit, onGenre: (Genre) -> Unit, onAnime: () -> Unit,
    onLibrary: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit
) {
    val vm = appViewModel { HomeViewModel(it) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()
    val free by vm.free.collectAsStateWithLifecycle()
    val cont by vm.continueWatching.collectAsStateWithLifecycle()
    val wl by vm.watchlistCount.collectAsStateWithLifecycle()
    val dl by vm.downloadCount.collectAsStateWithLifecycle()
    val hist by vm.historyCount.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        when (val s = state) {
            UiState.Loading -> item { CenteredLoading(Modifier.height(280.dp)) }
            is UiState.Error -> item { ErrorView(s, vm::load, onSettings) }
            is UiState.Success -> {
                item { HeroPager(s.data.hero, onItem) }
                item { MediaRow("Trending Now 🔥", s.data.trending, onItem) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Popular Genres")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Genres.all, key = { it.name }) { g -> GenreTile(g) { onGenre(g) } }
                    item { AnimeTile(onAnime) }
                }
            }
        }
        if (cont.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Continue Watching")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cont, key = { it.uid }) { h -> ContinueCard(h) { PlayerLauncher.playHistory(ctx, h) } }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Filled.Favorite, "Watchlist", "$wl items", onLibrary, Modifier.weight(1f))
                StatTile(Icons.Filled.Download, "Downloads", "$dl items", onDownloads, Modifier.weight(1f))
                StatTile(Icons.Filled.History, "History", "$hist items", onLibrary, Modifier.weight(1f))
            }
        }
        if (free.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Free to watch · public domain")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(free, key = { it.identifier }) { f ->
                        FreeCard(f, {
                            scope.launch {
                                if (!PlayerLauncher.playFree(ctx, ctx.appContainer.repo, f))
                                    Toast.makeText(ctx, "No playable file found for this title.", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }
        (state as? UiState.Success)?.data?.let { d ->
            item { MediaRow("Recommended for you", d.recommended, onItem) }
            item { MediaRow("Popular Movies", d.movies, onItem) }
            item { MediaRow("Popular TV Series", d.series, onItem) }
            item { MediaRow("Anime", d.anime, onItem, onAnime) }
        }
    }
}

@Composable
private fun HeroPager(items: List<MediaItem>, onItem: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager.settledPage, items.size) {
        delay(6000)
        pager.animateScrollToPage((pager.currentPage + 1) % items.size)
    }
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i -> HeroSlide(items[i], onItem) }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
            repeat(items.size) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 20.dp else 6.dp, label = "dot")
                Box(
                    Modifier.padding(3.dp).width(w).height(6.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) Violet else Color(0x55FFFFFF))
                )
            }
        }
    }
}

@Composable
private fun HeroSlide(item: MediaItem, onItem: (MediaItem) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(item.backdropUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000), Black))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xAA000000), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("%.1f/10  |  %s  |  %s".format(item.rating, item.year, if (item.type == MediaType.TV) "TV Series" else "Movie"),
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(item.overview, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFFD0D0DC), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onItem(item) }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Watch Now")
                }
                OutlinedButton(onClick = { onItem(item) }) {
                    Icon(Icons.Outlined.Info, null); Spacer(Modifier.width(4.dp)); Text("More Info")
                }
            }
        }
    }
}

@Composable
private fun GenreTile(g: Genre, onClick: () -> Unit) {
    Box(
        Modifier.width(140.dp).height(86.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(g.colors)).clickable(onClick = onClick)
    ) {
        Icon(g.icon, null, tint = Color(0x33FFFFFF), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp))
        Text(g.name, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

@Composable
private fun AnimeTile(onClick: () -> Unit) {
    Box(
        Modifier.width(140.dp).height(86.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFF1E3A8A)))).clickable(onClick = onClick)
    ) {
        Icon(Icons.Filled.Face, null, tint = Color(0x33FFFFFF), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp))
        Text("Anime", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

@Composable
fun ContinueCard(h: HistoryEntity, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = CardColor, modifier = Modifier.width(300.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(h.backdropUrl ?: h.posterUrl, h.title, Modifier.width(120.dp).height(76.dp), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    val left = ((h.durationMs - h.positionMs) / 60000).coerceAtLeast(0)
                    Text(
                        listOfNotNull(h.subtitle, if (h.durationMs > 0) "$left min left" else null).joinToString(" • ").ifBlank { "Continue" },
                        style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 1
                    )
                }
                Icon(Icons.Filled.PlayCircle, null, tint = VioletSoft, modifier = Modifier.padding(end = 12.dp).size(34.dp))
            }
            LinearProgressIndicator(
                progress = { if (h.durationMs > 0) (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp), color = Violet, trackColor = Outline
            )
        }
    }
}
