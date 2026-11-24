@file:OptIn(ExperimentalMaterial3Api::class)

package com.mymovie.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mymovie.app.data.FreeItem
import com.mymovie.app.data.MediaItem
import com.mymovie.app.ui.Paginator
import com.mymovie.app.ui.UiState
import com.mymovie.app.ui.theme.CardColor
import com.mymovie.app.ui.theme.Outline
import com.mymovie.app.ui.theme.TextDim
import java.util.Locale

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    if (rating <= 0.0) return
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(String.format(Locale.US, "%.1f", rating), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
fun PosterCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = 130.dp) {
    Column(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(CardColor)) {
            AsyncImage(item.posterUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            RatingBadge(item.rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        if (item.year.isNotBlank()) Text(item.year, style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
fun FreeCard(item: FreeItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(CardColor)) {
            AsyncImage(item.thumb, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        if (!item.year.isNullOrBlank()) Text(item.year, style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See All")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun MediaRow(title: String, items: List<MediaItem>, onItem: (MediaItem) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, onSeeAll = onSeeAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.key }) { PosterCard(it, { onItem(it) }) }
        }
    }
}

@Composable
fun ErrorView(error: UiState.Error, onRetry: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.CloudOff, null, tint = TextDim, modifier = Modifier.size(40.dp))
        Text(error.message, textAlign = TextAlign.Center, color = TextDim)
        if (error.missingKey) Button(onClick = onOpenSettings) { Text("Open Settings") }
        else OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun PosterGrid(p: Paginator, onItem: (MediaItem) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, p.items.size) { if (nearEnd) p.loadMore() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp), state = state, modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(p.items, key = { it.key }) { PosterCard(it, { onItem(it) }, width = null) }
        if (p.loading) item(span = { GridItemSpan(maxLineSpan) }) { CenteredLoading() }
        p.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorView(e, { p.loadMore() }, onOpenSettings) } }
    }
}

@Composable
fun DropdownChip(label: String, options: List<String>, selectedIndex: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedIndex != null, onClick = { open = true },
            label = { Text(if (selectedIndex != null) options[selectedIndex] else label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Any") }, onClick = { onSelect(null); open = false })
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false }) }
        }
    }
}

@Composable
fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = CardColor, border = BorderStroke(1.dp, Outline)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 1)
            }
        }
    }
}
