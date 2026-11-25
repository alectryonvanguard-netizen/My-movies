package com.mymovie.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mymovie.app.AppContainer
import com.mymovie.app.data.MediaItem
import com.mymovie.app.data.PersonInfo
import com.mymovie.app.ui.*
import com.mymovie.app.ui.components.CenteredLoading
import com.mymovie.app.ui.components.ErrorView
import com.mymovie.app.ui.components.MediaRow
import com.mymovie.app.ui.theme.CardColor
import com.mymovie.app.ui.theme.TextDim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PersonViewModel(private val c: AppContainer, private val id: Int) : ViewModel() {
    val state = MutableStateFlow<UiState<PersonInfo>>(UiState.Loading)
    init { load() }
    fun load() { state.value = UiState.Loading; viewModelScope.launch { state.value = runUi { c.repo.person(id) } } }
}

@Composable
fun PersonScreen(id: Int, onBack: () -> Unit, onItem: (MediaItem) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel(key = "person-$id") { PersonViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> CenteredLoading(Modifier.align(Alignment.Center))
            is UiState.Error -> ErrorView(s, vm::load, onOpenSettings, Modifier.align(Alignment.Center))
            is UiState.Success -> LazyColumn(contentPadding = PaddingValues(top = 72.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(s.data.photoUrl, s.data.name, Modifier.size(110.dp).clip(CircleShape).background(CardColor), contentScale = ContentScale.Crop)
                        Column {
                            Text(s.data.name, style = MaterialTheme.typography.headlineMedium)
                            Text(s.data.department, color = TextDim)
                            Text(listOfNotNull(s.data.birthday, s.data.birthplace).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextDim)
                        }
                    }
                }
                if (s.data.bio.isNotBlank()) item { Text(s.data.bio, maxLines = 12, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
                item { MediaRow("Known for", s.data.known, onItem) }
                item { MediaRow("Directed", s.data.directed, onItem) }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }
}
