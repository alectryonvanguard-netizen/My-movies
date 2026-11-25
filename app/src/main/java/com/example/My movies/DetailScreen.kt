@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mymovie.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mymovie.app.AppContainer
import com.mymovie.app.appContainer
import com.mymovie.app.data.*
import com.mymovie.app.data.local.SourceEntity
import com.mymovie.app.player.PlayerLauncher
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.*
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun Context.displayName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment ?: "Video"

class DetailViewModel(c: AppContainer, val type: MediaType, val id: Int) : ViewModel() {
    private val repo = c.repo
    val state = MutableStateFlow<UiState<MediaDetail>>(UiState.Loading)
    val libraryEntry = repo.libraryItem("${type.path}:$id").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val free = MutableStateFlow<UiState<List<VerifiedFree>>>(UiState.Loading)
    val episodes = MutableStateFlow<UiState<List<Episode>>>(UiState.Loading)
    var season by mutableIntStateOf(1); private set

    init { load() }

    fun load() {
        state.value = UiState.Loading
        viewModelScope.launch {
            val r = runUi { repo.details(type, id) }
            state.value = r
            if (r is UiState.Success) {
                val d = r.data
                if (type == MediaType.MOVIE) {
                    launch { free.value = runUi { repo.findVerifiedFree(d.item.title, d.item.year) } }
                } else d.seasons.firstOrNull()?.let { selectSeason(it.number) }
            }
        }
    }

    fun selectSeason(n: Int) {
        season = n
        episodes.value = UiState.Loading
        viewModelScope.launch { episodes.value = runUi { repo.season(id, n) } }
    }

    fun toggleWatchlist(item: MediaItem) = viewModelScope.launch { repo.setFlags(item, watchlist = libraryEntry.value?.inWatchlist != true) }
    fun toggleFavourite(item: MediaItem) = viewModelScope.launch { repo.setFlags(item, favorite = libraryEntry.value?.isFavorite != true) }
    fun sources(key: String): Flow<List<SourceEntity>> = repo.sources(key)
    fun addLink(key: String, label: String, url: String) = viewModelScope.launch { repo.addSource(key, label, url) }
    fun deleteSource(id: Long) = viewModelScope.launch { repo.deleteSource(id) }
}

@Composable
fun DetailScreen(type: MediaType, id: Int, onBack: () -> Unit, onItem: (MediaItem) -> Unit, onPerson: (Int) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel(key = "detail-${type.path}-$id") { DetailViewModel(it, type, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> CenteredLoading(Modifier.align(Alignment.Center))
            is UiState.Error -> ErrorView(s, vm::load, onOpenSettings, Modifier.align(Alignment.Center))
            is UiState.Success -> DetailContent(vm, s.data, onItem, onPerson)
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x99000000))
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextDim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailContent(vm: DetailViewModel, d: MediaDetail, onItem: (MediaItem) -> Unit, onPerson: (Int) -> Unit) {
    val ctx = LocalContext.current
    val entry by vm.libraryEntry.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Pair<Int, Episode>?>(null) }
    val item = d.item
    val isTv = item.type == MediaType.TV

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(item.backdropUrl ?: item.posterUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Black))))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    AsyncImage(item.posterUrl, null, Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(CardColor), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.headlineMedium)
                        if (d.originalTitle.isNotBlank() && d.originalTitle != item.title) Text(d.originalTitle, color = TextDim, style = MaterialTheme.typography.labelLarge)
                        Text(
                            listOfNotNull(if (item.rating > 0) "★ %.1f".format(item.rating) else null, item.year.ifBlank { null },
                                d.runtime?.let { "$it min" }, d.genres.take(3).joinToString(", ").ifBlank { null }).joinToString("  •  "),
                            style = MaterialTheme.typography.labelLarge, color = VioletSoft
                        )
                    }
                }
                if (d.tagline.isNotBlank()) Text(d.tagline, fontStyle = FontStyle.Italic, color = TextDim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val wl = entry?.inWatchlist == true
                    val fav = entry?.isFavorite == true
                    FilledTonalButton(onClick = { vm.toggleWatchlist(item) }) {
                        Icon(if (wl) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null); Spacer(Modifier.width(6.dp)); Text(if (wl) "In watchlist" else "Watchlist")
                    }
                    FilledTonalButton(onClick = { vm.toggleFavourite(item) }) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null); Spacer(Modifier.width(6.dp)); Text("Favourite")
                    }
                }
                if (item.overview.isNotBlank()) Text(item.overview, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD0D0DC))
                if (d.altTitles.isNotEmpty()) Text("Also known as: " + d.altTitles.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-30).dp)) {
                SectionTitle("Details")
                Fact("Release date", d.releaseDate)
                Fact("Director", d.directors.joinToString(", "))
                Fact("Runtime", d.runtime?.let { "$it min" }.orEmpty())
                Fact("Status", d.status)
                Fact("Original language", d.originalLanguage)
                Fact("Spoken languages", d.languages.joinToString(", "))
                Fact("Countries", d.countries.joinToString(", "))
                Fact("Rating", if (item.rating > 0) "%.1f / 10 (%d votes)".format(item.rating, d.voteCount) else "")
                Fact("Popularity", "%.0f".format(d.popularity))
            }
        }
        if (d.crew.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-30).dp)) {
                SectionTitle("Crew")
                d.crew.forEach { Fact(it.job, it.name) }
            }
        }
        if (!isTv) item {
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-30).dp)) {
                SectionTitle("Watch")
                SourcesPanel(vm, d, item.key, item.title, null, showFree = true, showProviders = true)
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp).offset(y = (-30).dp)) {
                    SectionTitle("External viewing options")
                    ProvidersBlock(d.providers)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Seasons & episodes")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(d.seasons, key = { it.number }) { s -> FilterChip(vm.season == s.number, { vm.selectSeason(s.number) }, { Text("Season ${s.number}") }) }
                    }
                }
            }
            when (val e = episodes) {
                UiState.Loading -> item { CenteredLoading() }
                is UiState.Error -> item { ErrorView(e, { vm.selectSeason(vm.season) }, {}) }
                is UiState.Success -> items(e.data, key = { "ep-${vm.season}-${it.number}" }) { ep -> EpisodeRow(ep) { sheet = vm.season to ep } }
            }
        }
        if (d.videos.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Trailers, clips & previews")
                Text("These are promotional videos on YouTube, not the full movie.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                d.videos.take(8).forEach { v ->
                    SourceCard(
                        badge = v.kind.label.uppercase(), badgeColor = if (v.kind == VideoKind.TRAILER || v.kind == VideoKind.TEASER) KindTrailer else KindClip,
                        title = v.name, caption = "Opens YouTube (external)",
                        onPlay = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url))) } }
                    )
                }
            }
        }
        if (d.cast.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Cast")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(d.cast, key = { it.id.toString() + it.name + it.character }) { m ->
                        Column(Modifier.width(80.dp).clickable(enabled = m.id > 0) { onPerson(m.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(m.photoUrl, m.name, Modifier.size(72.dp).clip(CircleShape).background(CardColor), contentScale = ContentScale.Crop)
                            Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { MediaRow("Similar", d.similar, onItem) }
        item { MediaRow("Recommended", d.recommended, onItem) }
    }

    sheet?.let { (season, ep) ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Charcoal) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                val label = "S$season·E${ep.number}"
                Text("$label  ${ep.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SourcesPanel(vm, d, "tv:${item.id}:S${season}E${ep.number}", item.title, "$label ${ep.name}", showFree = false, showProviders = false)
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(ep.stillUrl, ep.name, Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(12.dp)).background(CardColor), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("${ep.number}. ${ep.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text(listOfNotNull(ep.runtime?.let { "$it min" }, ep.airDate).joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = TextDim)
            if (ep.overview.isNotBlank()) Text(ep.overview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
        Icon(Icons.Filled.PlayCircle, "Sources", tint = VioletSoft)
    }
}

@Composable
private fun ProvidersBlock(p: Providers?) {
    val ctx = LocalContext.current
    if (p == null) {
        Text("No provider information for your region (change region in Settings).", color = TextDim, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Free / with ads" to p.free, "Subscription" to p.subscription, "Rent or buy" to p.rentBuy).forEach { (label, names) ->
            if (names.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) { SourceBadge("EXTERNAL", KindExternal); Spacer(Modifier.width(8.dp)); Text(label, style = MaterialTheme.typography.labelLarge, color = TextDim) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }
        if (p.free.isEmpty() && p.subscription.isEmpty() && p.rentBuy.isEmpty())
            Text("Not currently listed on any provider in your region.", color = TextDim, style = MaterialTheme.typography.bodySmall)
        p.link?.let { link ->
            OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } }) {
                Icon(Icons.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text("Open provider page")
            }
        }
    }
}

/** Playable options: verified Internet Archive films, your own links/files, and external options. Nothing is invented. */
@Composable
private fun SourcesPanel(vm: DetailViewModel, d: MediaDetail, mediaKey: String, playTitle: String, subtitle: String?, showFree: Boolean, showProviders: Boolean) {
    val ctx = LocalContext.current
    val container = ctx.appContainer
    val scope = rememberCoroutineScope()
    val sources by remember(mediaKey) { vm.sources(mediaKey) }.collectAsStateWithLifecycle(emptyList())
    val free by vm.free.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.addLink(mediaKey, ctx.displayName(uri), uri.toString())
        }
    }
    val poster = d.item.posterUrl
    val backdrop = d.item.backdropUrl
    val verified = (free as? UiState.Success)?.data.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showFree) {
            when (free) {
                UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                    Text("Checking free and public-domain sources…", color = TextDim, style = MaterialTheme.typography.bodySmall)
                }
                else -> verified.forEach { v ->
                    SourceCard(
                        badge = "FULL MOVIE", badgeColor = KindFull,
                        title = "${v.item.title}${v.item.year?.let { " ($it)" } ?: ""} · Internet Archive",
                        caption = "Rights: ${v.rights}" + if (!v.downloadable) "\nDownload not offered (no explicit licence)" else "",
                        onPlay = { PlayerLauncher.play(ctx, v.url, playTitle, mediaKey, d.item.key, subtitle, poster, backdrop, v.subtitles) },
                        onDownload = if (v.downloadable) ({
                            scope.launch {
                                container.downloader.enqueue(playTitle, v.url, d.item.key, poster)
                                Toast.makeText(ctx, "Download started — see Downloads", Toast.LENGTH_SHORT).show()
                            }
                        }) else null
                    )
                }
            }
        }
        if (showProviders) {
            Text("External viewing options", style = MaterialTheme.typography.labelLarge, color = TextDim)
            ProvidersBlock(d.providers)
        }
        if (sources.isNotEmpty()) {
            Text("Your sources", style = MaterialTheme.typography.labelLarge, color = TextDim)
            sources.forEach { s ->
                val direct = PlayerLauncher.isDirect(s.url)
                val isFile = s.url.startsWith("content://") || s.url.startsWith("file://")
                SourceCard(
                    badge = if (direct) "FULL MOVIE" else "EXTERNAL", badgeColor = if (direct) KindFull else KindExternal, title = s.label,
                    caption = if (isFile) "File on this device" else if (direct) "Direct link you added (plays in app)" else "Opens in your browser or provider app",
                    onPlay = { PlayerLauncher.openSource(ctx, s, playTitle, mediaKey, d.item.key, subtitle, poster, backdrop) },
                    onDelete = { vm.deleteSource(s.id) }
                )
            }
        }
        if (sources.isEmpty() && verified.isEmpty() && free !is UiState.Loading) {
            Text("No authorized full-length source is available for this title. Add a link you are authorized to use, or attach a video file from your device.", color = TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(6.dp)); Text("Add link") }
            OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Add file") }
        }
    }
    if (showAdd) AddLinkDialog(onDismiss = { showAdd = false }) { label, url ->
        vm.addLink(mediaKey, label.ifBlank { Uri.parse(url).host ?: "Link" }, url); showAdd = false
    }
}
