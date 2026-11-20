package br.archplayer.android.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var playUrl: String = ""
    private var userAgent: String = USER_AGENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onWindowFocusChanged(true)
        playerView = PlayerView(this)
        setContentView(buildOverlay())
        openStream(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openStream(intent)
    }

    private fun openStream(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        playUrl = url
        intent.getStringExtra(EXTRA_UA)?.takeIf { it.isNotBlank() }?.let { userAgent = it }
        playerView.subtitleView?.visibility = android.view.View.VISIBLE
        startPlayback(url)
    }

    private fun startPlayback(url: String) {
        player?.release()
        val trackSelector = DefaultTrackSelector(this)
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val newItem = buildMediaItem(url)
        player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
            .apply {
                setMediaItem(newItem)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        showError(error.errorCodeName)
                    }
                })
            }
        playerView.player = player
        playerView.showController()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    private fun buildMediaItem(url: String): MediaItem {
        val mime = when {
            url.contains(".m3u8", ignoreCase = true) || url.contains("/live/", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        mime?.let { builder.setMimeType(it) }
        val title = Uri.parse(url).lastPathSegment
        if (!title.isNullOrBlank()) {
            builder.setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        }
        return builder.build()
    }

    private fun buildOverlay(): android.view.View {
        val root = android.widget.FrameLayout(this)
        root.addView(playerView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        playerView.setControllerAutoShow(true)
        playerView.controllerShowTimeoutMs = 4500
        playerView.setFullscreenButtonClickListener { toggleExternal() }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        fun addBtn(label: String, action: () -> Unit) {
            val tv = TextView(this).apply {
                text = label
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                setPadding(18, 14, 18, 14)
                setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
                setOnClickListener { action() }
            }
            val wrap = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            bar.addView(tv, wrap)
        }
        addBtn("\u266A \u00c1udio") { showTrackDialog(C.TRACK_TYPE_AUDIO, "Faixas de \u00e1udio") }
        addBtn("\u2261 Legendas") { showTrackDialog(C.TRACK_TYPE_TEXT, "Legendas") }
        addBtn("\u00d7 Velocidade") { showSpeedMenu() }
        addBtn("\u2922 Escala") { showScaleMenu() }
        addBtn("\u21ba Externo") { toggleExternal() }

        val barParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        }
        root.addView(bar, barParams)
        return root
    }

    private fun showTrackDialog(type: Int, title: String) {
        val p = player ?: return
        val dialog = TrackSelectionDialogBuilder(this, title, p, type)
            .setShowDisableOption(true)
            .setAllowAdaptiveSelections(false)
            .build()
        dialog.show()
    }

    private fun showSpeedMenu() {
        val p = player ?: return
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val labels = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val chosen = speeds.indexOfFirst { it == p.playbackParameters.speed }.takeIf { it >= 0 } ?: 2
        AlertDialog.Builder(this)
            .setTitle("Velocidade")
            .setSingleChoiceItems(labels, chosen) { d, which ->
                p.setPlaybackSpeed(speeds[which])
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showScaleMenu() {
        val modes = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
        val labels = arrayOf("Ajustar", "Esticar", "Zoom (cortar)", "Largura fixa", "Altura fixa")
        val current = modes.indexOfFirst { it == playerView.resizeMode }.takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle("Escala de v\u00eddeo")
            .setSingleChoiceItems(labels, current) { d, which ->
                playerView.resizeMode = modes[which]
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleExternal() {
        val uri = Uri.parse(playUrl)
        val ext = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(Intent.createChooser(ext, "Abrir com"))
        }.onFailure {
            Toast.makeText(this, "Nenhum player externo encontrado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showError(what: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Erro de reprodu\u00e7\u00e3o")
                .setMessage("$what\n\nTentar novamente ou abrir em outro player?")
                .setPositiveButton("Tentar de novo") { _, _ -> startPlayback(playUrl) }
                .setNeutralButton("Player externo") { _, _ -> toggleExternal() }
                .setNegativeButton("Fechar") { _, _ -> finish() }
                .show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val insets = WindowInsetsControllerCompat(window, window.decorView)
            insets.hide(WindowInsetsCompat.Type.systemBars())
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        player?.release()
        player = null
        playerView.player = null
        super.onDestroy()
    }

    companion object {
        const val USER_AGENT = "ArchPlayer/1.0"
        const val EXTRA_URL = "br.archplayer.PLAY_URL"
        const val EXTRA_UA = "br.archplayer.PLAY_UA"

        fun start(context: Context, url: String, userAgent: String? = null) {
            val intent = Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_UA, userAgent ?: USER_AGENT)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
