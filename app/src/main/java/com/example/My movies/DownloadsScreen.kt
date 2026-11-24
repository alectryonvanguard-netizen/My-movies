package com.mymovie.app.ui.screens

import android.app.DownloadManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mymovie.app.AppContainer
import com.mymovie.app.data.local.DownloadEntity
import com.mymovie.app.downloads.DownloadHelper
import com.mymovie.app.downloads.DownloadStatus
import com.mymovie.app.player.PlayerLauncher
import com.mymovie.app.ui.appViewModel
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadsViewModel(private val c: AppContainer) : ViewModel() {
    private val dao = c.db.dao()
    val items = dao.downloads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val statuses = MutableStateFlow<Map<Long, DownloadStatus>>(emptyMap())

    init {
        viewModelScope.launch {
            dao.downloads().collectLatest { list ->
                while (true) {
                    statuses.value = withContext(Dispatchers.IO) { DownloadHelper.query(c.context, list.map { it.dmId }) }
                    delay(1000)
                }
            }
        }
    }

    fun delete(e: DownloadEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadHelper.remove(c.context, e)
            dao.deleteDownload(e.dmId)
        }
    }
}

@Composable
fun DownloadsScreen() {
    val vm = appViewModel { DownloadsViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        Text(
            "Offline downloads are offered only for public-domain titles from the Internet Archive.",
            style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No downloads yet. Open a free title and tap the download button.", color = TextDim)
            }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.dmId }) { e ->
                val st = statuses[e.dmId]
                val file = File(e.filePath)
                val done = st?.status == DownloadManager.STATUS_SUCCESSFUL || (st == null && file.exists())
                val failed = st?.status == DownloadManager.STATUS_FAILED
                Surface(shape = RoundedCornerShape(16.dp), color = CardColor) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    when {
                                        done -> "Ready • plays offline"
                                        failed -> "Failed"
                                        st == null -> "Unavailable"
                                        st.status == DownloadManager.STATUS_PAUSED -> "Paused"
                                        st.status == DownloadManager.STATUS_PENDING -> "Waiting…"
                                        st.total > 0 -> "Downloading ${st.downloaded * 100 / st.total}%"
                                        else -> "Downloading…"
                                    },
                                    style = MaterialTheme.typography.labelSmall, color = if (failed) MaterialTheme.colorScheme.error else TextDim
                                )
                            }
                            if (done) FilledIconButton(
                                onClick = { PlayerLauncher.play(ctx, Uri.fromFile(file).toString(), e.title, "dl:${e.dmId}", "dl:${e.dmId}") },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)
                            ) { Icon(Icons.Filled.PlayArrow, "Play") }
                            IconButton(onClick = { vm.delete(e) }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                        if (!done && !failed && st != null && st.total > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { st.downloaded.toFloat() / st.total }, modifier = Modifier.fillMaxWidth(), color = Violet, trackColor = Outline)
                        }
                    }
                }
            }
        }
    }
}
