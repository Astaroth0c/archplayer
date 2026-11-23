package br.archplayer.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import java.awt.image.BufferedImage

data class DPlay(val url: String)

@Composable
fun PlayerWindow(play: DPlay, onClose: () -> Unit) {
    var frame by remember(play.url) { mutableStateOf<BufferedImage?>(null) }
    var status by remember(play.url) { mutableStateOf("Conectando ao stream...") }
    var error by remember(play.url) { mutableStateOf("") }
    val engine = remember(play.url) { VideoEngine(play.url) }

    LaunchedEffect(play.url) {
        engine.start(
            onFrame = { img -> frame = img },
            onStatus = { status = it },
            onFailure = { error = it }
        )
    }
    DisposableEffect(play.url) {
        onDispose { engine.stopped = true }
    }

    val painter = remember(frame) { frame?.let { BitmapPainter(it.toComposeImageBitmap()) } }

    var controls by remember(play.url) { mutableStateOf(true) }
    var interactions by remember(play.url) { mutableStateOf(0) }
    LaunchedEffect(controls, interactions, play.url) {
        if (controls) {
            kotlinx.coroutines.delay(4500)
            controls = false
        }
    }

    Window(
        onCloseRequest = { engine.stopped = true; onClose() },
        title = "ArchPlayer - reproduzindo",
        state = rememberWindowState(width = 1280.dp, height = 760.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(play.url) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Press -> controls = !controls
                                PointerEventType.Move -> interactions++
                            }
                        }
                    }
                }
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = "video",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Text(
                    "v$APP_VERSION - motor FFmpeg",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
            }
            if (painter == null && error.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(status, color = Color.White)
                }
            }
            if (error.isNotEmpty() && painter == null) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(error, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { error = ""; openExternal(play.url) }) { Text("Player externo") }
                        OutlinedButton(onClick = { engine.stopped = true; onClose() }) { Text("Fechar") }
                    }
                }
            }
            if (controls) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { openExternal(play.url) }) { Text("Abrir externo") }
                    OutlinedButton(onClick = { engine.stopped = true; onClose() }) { Text("Fechar") }
                }
            }
        }
    }
}
