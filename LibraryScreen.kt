package com.mymovie.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mymovie.app.AppContainer
import com.mymovie.app.data.MediaItem
import com.mymovie.app.data.local.HistoryEntity
import com.mymovie.app.data.local.LocalVideoEntity
import com.mymovie.app.data.toItem
import com.mymovie.app.player.PlayerLauncher
import com.mymovie.app.ui.appViewModel
import com.mymovie.app.ui.components.PosterCard
import com.mymovie.app.ui.theme.CardColor
import com.mymovie.app.ui.theme.TextDim
import com.mymovie.app.ui.theme.VioletSoft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(c: AppContainer) : ViewModel() {
    private val dao = c.db.dao()
    private fun <T> kotlinx.coroutines.flow.Flow<T>.hot(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)
    val watchlist = dao.watchlist().map { l -> l.map { it.toItem() } }.hot(emptyList())
    val favourites = dao.favourites().map { l -> l.map { it.toItem() } }.hot(emptyList())
    val history = dao.history().hot(emptyList())
    val local = dao.localVideos().hot(emptyList())

    fun removeHistory(uid: String) = viewModelScope.launch { dao.deleteHistory(uid) }
    fun removeLocal(uri: String) = viewModelScope.launch { dao.deleteLocal(uri) }
    fun addLocal(uri: String, name: String) = viewModelScope.launch { dao.upsertLocal(LocalVideoEntity(uri, name, System.currentTimeMillis())) }
}

@Composable
fun LibraryScreen(onItem: (MediaItem) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val tabs = listOf("Watchlist", "Favourites", "History", "My files")
    var tab by rememberSaveable { mutableStateOf(0) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Text("My Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> PosterList(vm.watchlist.collectAsStateWithLifecycle().value, "Your watchlist is empty. Open a title and tap Watchlist.", onItem)
            1 -> PosterList(vm.favourites.collectAsStateWithLifecycle().value, "No favourites yet.", onItem)
            2 -> {
                val h by vm.history.collectAsStateWithLifecycle()
                if (h.isEmpty()) Empty("Nothing watched yet.") else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(h, key = { it.uid }) { HistoryRow(it, { PlayerLauncher.playHistory(ctx, it) }, { vm.removeHistory(it.uid) }) }
                }
            }
            else -> LocalFiles(vm)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = TextDim) }
}

@Composable
private fun PosterList(items: List<MediaItem>, empty: String, onItem: (MediaItem) -> Unit) {
    if (items.isEmpty()) { Empty(empty); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { items(items, key = { it.key }) { PosterCard(it, { onItem(it) }, width = null) } }
}

@Composable
private fun HistoryRow(h: HistoryEntity, onPlay: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onPlay, shape = RoundedCornerShape(16.dp), color = CardColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(h.backdropUrl ?: h.posterUrl, h.title, Modifier.width(110.dp).height(66.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                val state = if (h.finished) "Watched" else if (h.durationMs > 0) "${(h.positionMs * 100 / h.durationMs)}% watched" else "In progress"
                Text(listOfNotNull(h.subtitle, state).joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove") }
            Icon(Icons.Filled.PlayCircle, null, tint = VioletSoft, modifier = Modifier.padding(end = 10.dp))
        }
    }
}

@Composable
private fun LocalFiles(vm: LibraryViewModel) {
    val ctx = LocalContext.current
    val files by vm.local.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.addLocal(uri.toString(), ctx.displayName(uri))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add videos from this device")
        }
        if (files.isEmpty()) Empty("Personal videos you add here play offline in the built-in player.") else LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(files, key = { it.uri }) { f ->
                Surface(onClick = { PlayerLauncher.play(ctx, f.uri, f.name, "local:${f.uri}", "local:${f.uri}") }, shape = RoundedCornerShape(16.dp), color = CardColor) {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VideoFile, null, tint = VioletSoft)
                        Text(f.name, Modifier.weight(1f).padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.removeLocal(f.uri) }) { Icon(Icons.Filled.Delete, "Remove") }
                    }
                }
            }
        }
    }
}
