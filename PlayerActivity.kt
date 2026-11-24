package com.mymovie.app.player

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mymovie.app.appContainer
import kotlinx.coroutines.launch

/** Integrated Media3 player: resume, subtitles (embedded, sidecar, or picked file), audio tracks, quality cap. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var bar: LinearLayout

    private lateinit var url: String
    private lateinit var title: String
    private lateinit var uid: String
    private lateinit var mediaKey: String
    private var subtitle: String? = null
    private var poster: String? = null
    private var backdrop: String? = null
    private var sidecarSubs: List<String> = emptyList()
    private var pickedSub: Uri? = null
    private var pickedSubMime: String = MimeTypes.APPLICATION_SUBRIP
    private var resumeMs = -1L

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: ""
            pickedSubMime = if (name.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            pickedSub = uri
            player?.let {
                val pos = it.currentPosition
                it.setMediaItem(buildItem(), pos)
                it.prepare()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra("url") ?: return finish()
        title = intent.getStringExtra("title").orEmpty()
        uid = intent.getStringExtra("uid") ?: url
        mediaKey = intent.getStringExtra("mediaKey") ?: uid
        subtitle = intent.getStringExtra("subtitle")
        poster = intent.getStringExtra("poster")
        backdrop = intent.getStringExtra("backdrop")
        sidecarSubs = intent.getStringArrayListExtra("subs") ?: emptyList()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowSubtitleButton(true)
            controllerShowTimeoutMs = 3500
            keepScreenOn = true
        }
        root.addView(playerView)

        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply { setMargins(16, 16, 16, 0) }
        }
        fun barButton(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { onClick() }
            bar.addView(this)
        }
        barButton("Subtitle file") { pickSubtitle.launch(arrayOf("*/*")) }
        barButton("Quality") { showQuality() }
        barButton("Close") { finish() }
        root.addView(bar)
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            bar.visibility = if (v == View.VISIBLE) View.VISIBLE else View.GONE
        })
        setContentView(root)

        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            if (resumeMs < 0) resumeMs = appContainer.repo.getProgress(uid)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) initPlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        persist()
        player?.release()
        player = null
        playerView.player = null
    }

    private fun initPlayer() {
        if (player != null) return
        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Playback error: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        playerView.player = p
        p.setMediaItem(buildItem(), resumeMs.coerceAtLeast(0))
        p.playWhenReady = true
        p.prepare()
        player = p
    }

    private fun buildItem(): MediaItem {
        val subs = sidecarSubs.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it))
                .setMimeType(if (it.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLabel("Subtitles")
                .build()
        }.toMutableList()
        pickedSub?.let {
            subs += MediaItem.SubtitleConfiguration.Builder(it)
                .setMimeType(pickedSubMime)
                .setLabel("Selected file")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        return MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setSubtitleConfigurations(subs)
            .build()
    }

    private fun showQuality() {
        val labels = arrayOf("Auto (best available)", "2160p (4K)", "1080p (Full HD)", "720p (HD)", "480p")
        val heights = intArrayOf(Int.MAX_VALUE, 2160, 1080, 720, 480)
        AlertDialog.Builder(this)
            .setTitle("Maximum video quality")
            .setItems(labels) { _, i ->
                player?.let {
                    it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(Int.MAX_VALUE, heights[i]).build()
                }
            }.show()
    }

    private fun persist() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_IDLE) return
        val pos = p.currentPosition
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        resumeMs = pos
        val c = appContainer
        c.appScope.launch {
            c.repo.saveProgress(uid, mediaKey, title, subtitle, poster, backdrop, url, pos, dur)
        }
    }
}
