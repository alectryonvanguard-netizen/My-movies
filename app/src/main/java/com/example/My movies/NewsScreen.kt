package com.mymovie.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.mymovie.app.data.NewsItem
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.CenteredLoading
import com.mymovie.app.ui.components.ErrorView
import com.mymovie.app.ui.theme.*
import com.mymovie.app.web.WebActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class NewsViewModel(private val c: AppContainer) : ViewModel() {
    var category by mutableStateOf("All"); private set
    val state = MutableStateFlow<UiState<List<NewsItem>>>(UiState.Loading)
    val categories get() = c.news.categories
    init { load() }
    fun load() { state.value = UiState.Loading; viewModelScope.launch { state.value = runUi { c.news.load(category) } } }
    fun select(cat: String) { category = cat; load() }
}

@Composable
fun NewsScreen() {
    val vm = appViewModel { NewsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("News & Updates", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.load() }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.categories) { c -> FilterChip(vm.category == c, { vm.select(c) }, { Text(c) }) }
        }
        when (val s = state) {
            UiState.Loading -> CenteredLoading()
            is UiState.Error -> ErrorView(s, { vm.load() }, {})
            is UiState.Success -> if (s.data.isEmpty()) Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No headlines could be loaded. Check your connection and refresh.", color = TextDim)
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.data, key = { it.link }) { n ->
                    Surface(shape = RoundedCornerShape(16.dp), color = CardColor, modifier = Modifier.fillMaxWidth().clickable { WebActivity.open(ctx, n.link, n.source) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (n.image != null) AsyncImage(n.image, null, Modifier.width(104.dp).height(70.dp).clip(RoundedCornerShape(10.dp)).background(Charcoal), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start = if (n.image != null) 12.dp else 0.dp)) {
                                Text(n.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    listOfNotNull(n.source, n.published?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = VioletSoft, modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
