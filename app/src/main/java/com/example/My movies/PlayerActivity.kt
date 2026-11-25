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
import android.widget.TextView
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mymovie.app.appContainer
import kotlinx.coroutines.launch

/**
 * Integrated Media3 player: MP4 / HLS / DASH / local files, resume, subtitles, audio tracks, speed (built-in
 * controls), quality presets + the real qualities the source offers, loading indicator and error recovery.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var bar: LinearLayout
    private lateinit var errorBox: LinearLayout
    private lateinit var errorText: TextView

    private lateinit var url: String
    private lateinit var title: String
    private lateinit var uid: String
    private lateinit var mediaKey: String
    private var subtitle: String? = null
    private var poster: String? = null
    private var backdrop: String? = null
    private var live = false
    private var sidecarSubs: List<String> = emptyList()
    private var pickedSub: Uri? = null
    private var pickedSubMime: String = MimeTypes.APPLICATION_SUBRIP
    private var resumeMs = -1L

    private class QualityOption(val height: Int, val bitrate: Int, val group: Tracks.Group, val index: Int)

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: ""
            pickedSubMime = if (name.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            pickedSub = uri
            player?.let { val pos = it.currentPosition; it.setMediaItem(buildItem(), pos); it.prepare() }
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
        live = intent.getBooleanExtra("live", false)
        sidecarSubs = intent.getStringArrayListExtra("subs") ?: emptyList()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowSubtitleButton(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
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
            this.text = text; setTextColor(Color.WHITE); setBackgroundColor(0x99000000.toInt())
            setOnClickListener { onClick() }; bar.addView(this)
        }
        barButton("Quality") { showQuality() }
        barButton("Subtitle file") { pickSubtitle.launch(arrayOf("*/*")) }
        barButton("Close") { finish() }
        root.addView(bar)

        errorText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(48, 24, 48, 24) }
        errorBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = View.GONE
            setBackgroundColor(0xCC000000.toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(errorText)
            addView(Button(this@PlayerActivity).apply {
                text = "Retry"
                setOnClickListener { errorBox.visibility = View.GONE; player?.let { it.prepare(); it.playWhenReady = true } }
            })
            addView(Button(this@PlayerActivity).apply { text = "Close"; setOnClickListener { finish() } })
        }
        root.addView(errorBox)

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
            if (resumeMs < 0) resumeMs = if (live) 0L else appContainer.repo.getProgress(uid)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) initPlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        persist()
        player?.release(); player = null; playerView.player = null
    }

    private fun capFor(preset: String): Pair<Int?, Boolean> = when (preset) {
        "DATA_SAVER" -> 360 to false
        "BALANCED" -> 720 to false
        "HD" -> 1080 to false
        "FULL" -> null to true
        else -> null to false
    }

    private fun applyCap(cap: Int?, forceHighest: Boolean) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, cap ?: Int.MAX_VALUE)
            .setForceHighestSupportedBitrate(forceHighest).build()
    }

    private fun initPlayer() {
        if (player != null) return
        val prefs = appContainer.prefs
        val p = ExoPlayer.Builder(this).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) errorBox.visibility = View.GONE }
            override fun onPlayerError(error: PlaybackException) {
                errorText.text = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Network problem. Check your connection and retry."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "The source refused the request. The link may have expired or be restricted in your region."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "This stream could not be decoded on this device."
                    else -> "Playback error (${error.errorCodeName})."
                }
                errorBox.visibility = View.VISIBLE
            }
        })
        val tp = p.trackSelectionParameters.buildUpon()
        if (prefs.audioLang.isNotBlank()) tp.setPreferredAudioLanguage(prefs.audioLang)
        if (prefs.subtitleLang.isNotBlank()) tp.setPreferredTextLanguage(prefs.subtitleLang)
        p.trackSelectionParameters = tp.build()
        playerView.player = p
        player = p
        val (cap, force) = capFor(prefs.qualityPreset)
        applyCap(cap, force)
        p.setMediaItem(buildItem(), resumeMs.coerceAtLeast(0))
        p.playWhenReady = true
        p.prepare()
    }

    private fun mimeFor(u: String): String? {
        val l = u.substringBefore('?').lowercase()
        return when { l.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8; l.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD; else -> null }
    }

    private fun buildItem(): MediaItem {
        val subs = sidecarSubs.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it))
                .setMimeType(if (it.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLabel("Subtitles").build()
        }.toMutableList()
        pickedSub?.let {
            subs += MediaItem.SubtitleConfiguration.Builder(it).setMimeType(pickedSubMime).setLabel("Selected file")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        }
        return MediaItem.Builder().setUri(url).apply { mimeFor(url)?.let { setMimeType(it) } }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setSubtitleConfigurations(subs).build()
    }

    private fun hLabel(h: Int) = if (h >= 2160) "4K (${h}p)" else "${h}p"

    /** Lists only qualities the loaded source actually provides. */
    private fun qualityOptions(p: Player): List<QualityOption> =
        p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it) && g.getTrackFormat(it).height > 0 }
                .map { i -> QualityOption(g.getTrackFormat(i).height, g.getTrackFormat(i).bitrate, g, i) }
        }.sortedWith(compareBy({ it.height }, { -it.bitrate })).distinctBy { it.height }

    private fun showQuality() {
        val p = player ?: return
        val opts = qualityOptions(p)
        if (opts.size <= 1) {
            val only = opts.firstOrNull()
            AlertDialog.Builder(this).setTitle("Video quality")
                .setMessage(if (only != null) "This source provides a single quality: ${hLabel(only.height)}." else "Quality information is not available yet.")
                .setPositiveButton("OK", null).show()
            return
        }
        fun effective(cap: Int?): QualityOption? =
            if (cap == null) opts.maxByOrNull { it.height } else (opts.filter { it.height <= cap }.maxByOrNull { it.height } ?: opts.minByOrNull { it.height })
        val presets = listOf("AUTO" to "Auto (adaptive)", "DATA_SAVER" to "Data Saver", "BALANCED" to "Balanced", "HD" to "HD", "FULL" to "Full Quality")
        val labels = ArrayList<String>()
        presets.forEach { (k, name) ->
            val (cap, _) = capFor(k)
            labels += if (k == "AUTO") name else "$name · up to ${effective(cap)?.let { hLabel(it.height) } ?: "?"}"
        }
        opts.forEach { labels += "Fixed: ${hLabel(it.height)}" }
        AlertDialog.Builder(this).setTitle("Video quality")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < presets.size) {
                    val k = presets[i].first
                    appContainer.prefs.qualityPreset = k
                    val (cap, force) = capFor(k)
                    applyCap(cap, force)
                } else {
                    val o = opts[i - presets.size]
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, listOf(o.index))).build()
                }
            }.show()
    }

    private fun persist() {
        val p = player ?: return
        if (live || p.playbackState == Player.STATE_IDLE) return
        val pos = p.currentPosition
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        resumeMs = pos
        val c = appContainer
        c.appScope.launch { c.repo.saveProgress(uid, mediaKey, title, subtitle, poster, backdrop, url, pos, dur) }
    }
}
