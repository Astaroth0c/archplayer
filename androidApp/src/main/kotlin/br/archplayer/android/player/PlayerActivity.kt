package br.archplayer.android.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val userAgent = intent.getStringExtra(EXTRA_UA) ?: "ArchPlayer/1.0"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    VideoPlayerScreen(
                        url = url,
                        userAgent = userAgent,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "br.archplayer.PLAY_URL"
        const val EXTRA_UA = "br.archplayer.PLAY_UA"

        fun start(context: Context, url: String, userAgent: String? = null) {
            val intent = Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_UA, userAgent ?: "ArchPlayer/1.0")
            context.startActivity(intent)
        }
    }
}

@androidx.compose.runtime.Composable
fun VideoPlayerScreen(url: String, userAgent: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = androidx.compose.runtime.remember {
        ExoPlayer.Builder(context).build().apply {
            val item = when {
                url.contains(".m3u8") -> MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                url.contains(".mpd") -> MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                else -> MediaItem.fromUri(url)
            }
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
}
