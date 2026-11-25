
package com.mymovie.app.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.mymovie.app.data.MediaType
import com.mymovie.app.data.local.DownloadEntity
import com.mymovie.app.downloads.StorageInfo
import com.mymovie.app.player.PlayerLauncher
import com.mymovie.app.ui.appViewModel
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadsViewModel(private val c: AppContainer) : ViewModel() {
    val items = c.db.dao().downloads().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val storage = MutableStateFlow(StorageInfo(0, 0, 0))
    val dl get() = c.downloader

    init {
        viewModelScope.launch {
            while (true) {
                storage.value = withContext(Dispatchers.IO) {
                    runCatching { c.downloader.storage() }
                        .getOrDefault(StorageInfo(0, 0, 0))
                }
                delay(2000)
            }
        }
    }

    fun pause(id: Long) = dl.pause(id)

    fun resume(id: Long) = dl.resume(id)

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dl.delete(id)
        }
    }
}

@Composable
fun DownloadRow(
    e: DownloadEntity,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (() -> Unit)?
) {
    val ctx = LocalContext.current
    val done = e.status == "COMPLETED"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardColor
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    e.posterUrl,
                    e.title,
                    Modifier
                        .width(48.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Charcoal),
                    contentScale = ContentScale.Crop
                )

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .then(
                            if (onOpen != null) Modifier.clickable(onClick = onOpen)
                            else Modifier
                        )
                ) {
                    Text(
                        e.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )

                    val size = if (e.totalBytes > 0) {
                        Formatter.formatShortFileSize(ctx, e.totalBytes)
                    } else ""

                    Text(
                        when (e.status) {
                            "COMPLETED" -> "Ready offline · $size"
                            "FAILED" -> "Failed — tap resume to retry"
                            "PAUSED" -> "Paused · ${Formatter.formatShortFileSize(ctx, e.downloadedBytes)} of $size"
                            "QUEUED" -> "Waiting…"
                            else -> if (e.totalBytes > 0) {
                                "Downloading ${e.downloadedBytes * 100 / e.totalBytes}% · $size"
                            } else {
                                "Downloading…"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (e.status == "FAILED") {
                            MaterialTheme.colorScheme.error
                        } else {
                            TextDim
                        }
                    )
                }

                if (done) {
                    FilledIconButton(
                        onClick = onPlay,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Violet
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play")
                    }
                } else if (e.status == "RUNNING" || e.status == "QUEUED") {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, "Pause")
                    }
                } else {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, "Resume")
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete")
                }
            }

            if (!done && e.totalBytes > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        (e.downloadedBytes.toFloat() / e.totalBytes)
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Violet,
                    trackColor = Outline
                )
            }
        }
    }
}

@Composable
fun DownloadsScreen(onOpen: (MediaType, Int) -> Unit) {
    val vm = appViewModel { DownloadsViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val st by vm.storage.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Text(
            "Downloads",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Storage", style = MaterialTheme.typography.labelLarge)

                Text(
                    "Downloads use ${Formatter.formatShortFileSize(ctx, st.usedByDownloads)} · ${Formatter.formatShortFileSize(ctx, st.free)} free of ${Formatter.formatShortFileSize(ctx, st.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )

                if (st.total > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (1f - st.free.toFloat() / st.total)
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = Violet,
                        trackColor = Outline
                    )
                }

                Text(
                    "Downloads are offered only where the source explicitly permits it (public-domain / licensed items).",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No downloads yet.", color = TextDim)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { e ->
                    DownloadRow(
                        e,
                        onPlay = {
                            PlayerLauncher.play(
                                ctx,
                                Uri.fromFile(File(e.filePath)).toString(),
                                e.title,
                                "dl:${e.id}",
                                e.mediaKey ?: "dl:${e.id}",
                                poster = e.posterUrl
                            )
                        },
                        onPause = { vm.pause(e.id) },
                        onResume = { vm.resume(e.id) },
                        onDelete = { vm.delete(e.id) },
                        onOpen = e.mediaKey
                            ?.split(":")
                            ?.takeIf {
                                it.size == 2 && it[1].toIntOrNull() != null
                            }
                            ?.let { p ->
                                {
                                    onOpen(
                                        MediaType.from(p[0]),
                                        p[1].toInt()
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}
