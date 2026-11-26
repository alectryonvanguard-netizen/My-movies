package com.mymovie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mymovie.app.data.VideoInfo
import com.mymovie.app.web.WebActivity
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays a trailer/clip inside the app with YouTube's embedded player. If a video is blocked in your
 * country or can't be embedded, it automatically tries the next trailer in [queue] instead of dead-ending.
 * Region blocks are never bypassed.
 */
@Composable
fun YouTubePlayerDialog(queue: List<VideoInfo>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var index by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableIntStateOf(0) }
    var allFailed by remember { mutableStateOf(false) }
    val view = remember { YouTubePlayerView(ctx) }
    val current = queue[index.coerceIn(0, queue.lastIndex)]

    DisposableEffect(Unit) {
        owner.lifecycle.addObserver(view)
        view.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) { youTubePlayer.loadVideo(queue[index].key, 0f) }
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                if (index + 1 < queue.size) {
                    index += 1; skipped += 1
                    youTubePlayer.loadVideo(queue[index].key, 0f)
                } else allFailed = true
            }
        })
        onDispose { owner.lifecycle.removeObserver(view); view.release() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, Modifier.weight(1f), maxLines = 1, color = Color.White)
                TextButton(onClick = onClose) { Text("Close") }
            }
            if (allFailed) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "None of this title's trailers could be played here. They may be blocked in your country by the uploader, or embedding is turned off. Region blocks can't be bypassed.",
                        color = Color.White, textAlign = TextAlign.Center
                    )
                    OutlinedButton(onClick = { WebActivity.open(ctx, "https://m.youtube.com/watch?v=${current.key}", current.name); onClose() }) { Text("Try the YouTube page inside the app") }
                }
            } else {
                AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                if (skipped > 0) Text("Skipped $skipped unavailable video(s) and moved to the next one.", color = Color(0xFFA3A3B5), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Text("Promotional video from YouTube, not the full movie.", color = Color(0xFFA3A3B5), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}
