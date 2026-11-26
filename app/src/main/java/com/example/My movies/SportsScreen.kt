@file:OptIn(ExperimentalMaterial3Api::class)

package com.mymovie.app.ui.screens

import com.mymovie.app.web.WebActivity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mymovie.app.AppContainer
import com.mymovie.app.appContainer
import com.mymovie.app.data.*
import com.mymovie.app.data.local.SourceEntity
import com.mymovie.app.data.remote.Network
import com.mymovie.app.player.PlayerLauncher
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.*
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats in the device's own time zone. */
fun formatLocal(ms: Long?): String? = ms?.let { SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(it)) }

class SportsViewModel(private val c: AppContainer, sel: String?) : ViewModel() {
    private val repo = c.sports
    val premium get() = repo.premiumTsdb
    val hasFd get() = repo.hasFd
    var sportIdx by mutableIntStateOf(0); private set
    var tab by mutableIntStateOf(0); private set
    var league by mutableStateOf<SportLeague?>(null); private set
    val leagues = MutableStateFlow<UiState<List<SportLeague>>>(UiState.Loading)
    val events = MutableStateFlow<UiState<List<SportEvent>>>(UiState.Loading)
    val table = MutableStateFlow<UiState<List<StandingRow>>>(UiState.Loading)
    val scorers = MutableStateFlow<UiState<List<ScorerRow>>>(UiState.Loading)
    var selected by mutableStateOf<SportEvent?>(null); private set
    val extras = MutableStateFlow<UiState<EventExtras>>(UiState.Loading)
    private var job: Job? = null

    init {
        sel?.split("|")?.takeIf { it.size >= 4 }?.let { p ->
            sportIdx = SPORT_OPTIONS.indexOfFirst { it.label == p[2] }.coerceAtLeast(0)
            league = SportLeague(p[1], p[3], p[2], p[0]); tab = 1
        }
        loadLeagues()
    }

    fun selectSport(i: Int) { sportIdx = i; league = null; loadLeagues() }
    fun selectLeague(l: SportLeague?) { league = l; refresh() }
    fun selectTab(t: Int) {
        tab = t
        if (t != 0 && league == null) league = (leagues.value as? UiState.Success)?.data?.firstOrNull()
        refresh()
    }

    fun loadLeagues() {
        leagues.value = UiState.Loading
        viewModelScope.launch {
            val r = runUi { repo.leagues(SPORT_OPTIONS[sportIdx]) }
            leagues.value = r
            if (league == null && tab != 0 && r is UiState.Success) league = r.data.firstOrNull()
            refresh()
        }
    }

    fun refresh(silent: Boolean = false) {
        job?.cancel()
        val l = league
        job = viewModelScope.launch {
            when (tab) {
                0 -> {
                    if (!silent) events.value = UiState.Loading
                    val r = runUi { repo.today(SPORT_OPTIONS[sportIdx]).let { all -> if (l != null) all.filter { it.leagueId == l.id } else all } }
                    if (!(silent && r is UiState.Error)) events.value = r
                }
                1 -> { if (l == null) return@launch; events.value = UiState.Loading; events.value = runUi { repo.fixtures(l) } }
                2 -> { if (l == null) return@launch; events.value = UiState.Loading; events.value = runUi { repo.results(l) } }
                3 -> { if (l == null) return@launch; table.value = UiState.Loading; table.value = runUi { repo.standings(l) } }
                else -> { if (l == null) return@launch; scorers.value = UiState.Loading; scorers.value = runUi { repo.scorers(l) } }
            }
        }
    }

    fun forceRefresh() { Network.forceRefresh(); loadLeagues() }

    fun open(e: SportEvent) {
        selected = e
        extras.value = UiState.Loading
        viewModelScope.launch { extras.value = runUi { repo.extras(e) } }
    }
    fun close() { selected = null }

    fun sources(key: String) = c.repo.sources(key)
    fun addSource(key: String, label: String, url: String) { viewModelScope.launch { c.repo.addSource(key, label, url) } }
    fun deleteSource(id: Long) { viewModelScope.launch { c.repo.deleteSource(id) } }
}

@Composable
fun SportsScreen(sel: String?, onOpenSettings: () -> Unit) {
    val vm = appViewModel(key = "sports-$sel") { SportsViewModel(it, sel) }
    val leagues by vm.leagues.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val table by vm.table.collectAsStateWithLifecycle()
    val scorers by vm.scorers.collectAsStateWithLifecycle()
    LaunchedEffect(vm.tab, vm.sportIdx, vm.league) {
        if (vm.tab == 0) while (true) { delay(60_000); vm.refresh(silent = true) }
    }
    val tabs = listOf("Live & Today", "Fixtures", "Results", "Tables", "Rankings")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Live Sports", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.forceRefresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(SPORT_OPTIONS) { i, s -> FilterChip(vm.sportIdx == i, { vm.selectSport(i) }, { Text(s.label) }) }
        }
        when (val l = leagues) {
            UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            is UiState.Error -> ErrorView(l, { vm.loadLeagues() }, onOpenSettings)
            is UiState.Success -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.tab == 0) item { FilterChip(vm.league == null, { vm.selectLeague(null) }, { Text("All competitions") }) }
                val shown = if (vm.league != null && l.data.none { it.id == vm.league!!.id && it.provider == vm.league!!.provider }) listOf(vm.league!!) + l.data.take(40) else l.data.take(40)
                items(shown, key = { it.provider + it.id }) { x -> FilterChip(vm.league?.id == x.id && vm.league?.provider == x.provider, { vm.selectLeague(x) }, { Text(x.name) }) }
            }
        }
        ScrollableTabRow(selectedTabIndex = vm.tab, edgePadding = 12.dp, containerColor = Color.Transparent) {
            tabs.forEachIndexed { i, t -> Tab(vm.tab == i, { vm.selectTab(i) }, text = { Text(t) }) }
        }
        Box(Modifier.weight(1f)) {
            if (vm.tab != 0 && vm.league == null) Msg("Select a competition above.")
            else when (vm.tab) {
                0, 1, 2 -> EventList(vm, events, onOpenSettings)
                3 -> when (val t = table) {
                    UiState.Loading -> CenteredLoading()
                    is UiState.Error -> ErrorView(t, { vm.refresh() }, onOpenSettings)
                    is UiState.Success -> if (t.data.isEmpty()) Msg("No league table is available for this competition (it may not use a table, or the data plan doesn't include it).") else StandingsTable(t.data)
                }
                else -> when (val t = scorers) {
                    UiState.Loading -> CenteredLoading()
                    is UiState.Error -> ErrorView(t, { vm.refresh() }, onOpenSettings)
                    is UiState.Success -> if (t.data.isEmpty()) Msg(
                        if (vm.league?.provider == "fd") "No scorer data returned."
                        else "Top scorers and player rankings need a football-data.org key (Settings → Sports data) and a football competition from that provider. Official world rankings (FIFA, ATP…) are not included."
                    ) else ScorersList(t.data)
                }
            }
        }
    }

    vm.selected?.let { e -> EventSheet(vm, e) }
}

@Composable
private fun Msg(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = TextDim, textAlign = TextAlign.Center) }
}

@Composable
private fun EventList(vm: SportsViewModel, state: UiState<List<SportEvent>>, onOpenSettings: () -> Unit) {
    when (state) {
        UiState.Loading -> CenteredLoading()
        is UiState.Error -> ErrorView(state, { vm.refresh() }, onOpenSettings)
        is UiState.Success -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.tab == 0) item {
                Text(
                    if (vm.premium) "Live scores are provided by your TheSportsDB premium key." else
                        "Scores update when the free data providers publish them. Real-time live scores need a premium key (Settings → Sports data). Times are shown in your time zone.",
                    style = MaterialTheme.typography.labelSmall, color = TextDim
                )
            }
            if (state.data.isEmpty()) item { Msg(if (vm.tab == 0) "No events found for today in this selection." else "No events listed.") }
            items(state.data, key = { it.key }) { e -> EventRow(e) { vm.open(e) } }
        }
    }
}

@Composable
fun EventRow(e: SportEvent, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = CardColor) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.league.ifBlank { e.sport }, style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.state == EventState.LIVE) LiveBadge() else Text(e.statusText, style = MaterialTheme.typography.labelSmall, color = if (e.state == EventState.POSTPONED) MaterialTheme.colorScheme.error else TextDim)
            }
            Spacer(Modifier.height(8.dp))
            if (e.home != null && e.away != null) {
                TeamLine(e.home, e.homeBadge, e.homeScore)
                Spacer(Modifier.height(6.dp))
                TeamLine(e.away, e.awayBadge, e.awayScore)
            } else Text(e.title, style = MaterialTheme.typography.titleMedium)
            formatLocal(e.startMillis)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = VioletSoft, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun TeamLine(name: String, badge: String?, score: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(badge, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
        Text(name, Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        if (score != null) Text(score.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun StandingsTable(rows: List<StandingRow>) {
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Cell("#", 26); Text("Team", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TextDim)
                listOf("P", "W", "D", "L", "GD", "Pts").forEach { Cell(it, if (it == "GD" || it == "Pts") 34 else 26, true) }
            }
        }
        items(rows, key = { it.rank.toString() + it.team }) { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cell(r.rank.toString(), 26)
                    AsyncImage(r.badge, null, Modifier.size(20.dp), contentScale = ContentScale.Fit)
                    Text(r.team, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Cell(r.played.toString(), 26, true); Cell(r.won.toString(), 26, true); Cell(r.draw.toString(), 26, true); Cell(r.lost.toString(), 26, true)
                    Cell((if (r.gd > 0) "+" else "") + r.gd, 34, true); Cell(r.points.toString(), 34, true, bold = true)
                }
                if (!r.form.isNullOrBlank()) Row(Modifier.padding(start = 34.dp, top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    r.form.takeLast(5).forEach { ch ->
                        Text(ch.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(when (ch.uppercaseChar()) { 'W' -> KindFull; 'L' -> Color(0xFFDC2626); else -> KindExternal }))
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Int, end: Boolean = false, bold: Boolean = false) {
    Text(text, Modifier.width(width.dp), textAlign = if (end) TextAlign.End else TextAlign.Start, style = MaterialTheme.typography.labelSmall,
        color = if (bold) Color.White else TextDim, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun ScorersList(rows: List<ScorerRow>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Top scorers", style = MaterialTheme.typography.titleLarge) }
        items(rows, key = { it.rank.toString() + it.player }) { r ->
            Surface(shape = RoundedCornerShape(14.dp), color = CardColor) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.rank.toString(), Modifier.width(28.dp), color = TextDim)
                    Column(Modifier.weight(1f)) {
                        Text(r.player, style = MaterialTheme.typography.labelLarge)
                        Text(listOfNotNull(r.team, r.played?.let { "$it played" }, r.assists?.let { "$it assists" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                    Text("${r.goals}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = VioletSoft)
                }
            }
        }
    }
}

@Composable
private fun EventSheet(vm: SportsViewModel, e: SportEvent) {
    val ctx = LocalContext.current
    val extras by vm.extras.collectAsStateWithLifecycle()
    val sources by remember(e.key) { vm.sources(e.key) }.collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { vm.close() }, containerColor = Charcoal) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.league.ifBlank { e.sport }, color = TextDim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (e.state == EventState.LIVE) LiveBadge() else Text(e.statusText, color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            if (e.home != null && e.away != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        AsyncImage(e.homeBadge, null, Modifier.size(48.dp)); Text(e.home, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(if (e.homeScore != null && e.awayScore != null) "${e.homeScore} - ${e.awayScore}" else "vs", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        AsyncImage(e.awayBadge, null, Modifier.size(48.dp)); Text(e.away, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else Text(e.title, style = MaterialTheme.typography.titleLarge)
            Text(listOfNotNull(formatLocal(e.startMillis), e.venue?.takeIf { it.isNotBlank() }).joinToString(" · "), color = VioletSoft, style = MaterialTheme.typography.labelLarge)

            Text("Watch", style = MaterialTheme.typography.titleMedium)
            sources.forEach { s ->
                SourceCard(
                    badge = if (PlayerLauncher.isDirect(s.url)) (if (e.state == EventState.LIVE) "LIVE STREAM" else "REPLAY / FULL") else "EXTERNAL",
                    badgeColor = if (PlayerLauncher.isDirect(s.url)) KindFull else KindExternal, title = s.label,
                    caption = "Source you added and confirmed you are authorized to use",
                    onPlay = { PlayerLauncher.openSource(ctx, s, e.title, e.key, e.key, e.statusText, e.homeBadge, e.thumb, live = e.state == EventState.LIVE) },
                    onDelete = { vm.deleteSource(s.id) }
                )
            }
            e.videoUrl?.let { v ->
                SourceCard(badge = "HIGHLIGHTS", badgeColor = KindClip, title = "Highlights from the data provider", caption = "Opens inside the app (not a live broadcast)",
                    onPlay = { WebActivity.open(ctx, v, "Highlights") })
            }
            if (sources.isEmpty()) Text("No authorized live or replay video is available for this event. Scores, schedule and standings keep updating here. Broadcast rights are separate from sports data.", style = MaterialTheme.typography.bodySmall, color = TextDim)
            OutlinedButton(onClick = { showAdd = true }) { Text("Add official / licensed stream link") }

            when (val x = extras) {
                UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Error -> Text(x.message, color = TextDim)
                is UiState.Success -> {
                    if (x.data.timeline.isNotEmpty()) {
                        Text("Timeline", style = MaterialTheme.typography.titleMedium)
                        x.data.timeline.forEach { t -> Text("${t.minute}'  ${t.text}", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (x.data.stats.isNotEmpty()) {
                        Text("Statistics", style = MaterialTheme.typography.titleMedium)
                        x.data.stats.forEach { s -> Row(Modifier.fillMaxWidth()) { Text(s.home, Modifier.width(48.dp)); Text(s.name, Modifier.weight(1f), textAlign = TextAlign.Center, color = TextDim); Text(s.away, Modifier.width(48.dp), textAlign = TextAlign.End) } }
                    }
                    x.data.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextDim) }
                }
            }
        }
    }
    if (showAdd) AddLinkDialog(onDismiss = { showAdd = false }) { label, url ->
        vm.addSource(e.key, label.ifBlank { Uri.parse(url).host ?: "Stream" }, url); showAdd = false
    }
}
