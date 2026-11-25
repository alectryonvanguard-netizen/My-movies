package com.mymovie.app.ui.components

import com.mymovie.app.web.WebActivity
import android.content.Intent
import android.net.Uri
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
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays a YouTube trailer/clip inside the app using YouTube's official embedded player.
 * YouTube controls the player, its quality menu and any ads it shows. If the video owner
 * disallows embedding, a button opens it in YouTube instead.
 */
@Composable
fun YouTubePlayerDialog(videoId: String, title: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var failed by remember { mutableStateOf(false) }
    val view = remember { YouTubePlayerView(ctx) }

    DisposableEffect(videoId) {
        owner.lifecycle.addObserver(view)
        view.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) { youTubePlayer.loadVideo(videoId, 0f) }
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) { failed = true }
        })
        onDispose { owner.lifecycle.removeObserver(view); view.release() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), maxLines = 1, color = Color.White)
                TextButton(onClick = onClose) { Text("Close") }
            }
            if (failed) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The embedded player can't play this video (embedding is turned off or it is unavailable). You can open its YouTube page inside the app instead.", color = Color.White, textAlign = TextAlign.Center)
                    Button(onClick = { WebActivity.open(ctx, "https://m.youtube.com/watch?v=$videoId", title); onClose() }) { Text("Watch the YouTube page here") }
                }
            } else {
                AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            Text("Promotional video from YouTube, not the full movie.", color = Color(0xFFA3A3B5), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}
