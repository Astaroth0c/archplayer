package br.archplayer.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import br.archplayer.core.tvbox.TvBoxConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ArchPlayer",
        state = rememberWindowState(width = 1100.dp, height = 700.dp)
    ) {
        MaterialTheme(colors = darkColors(background = Color(0xFF0B0E13))) {
            DesktopHome()
        }
    }
}

@Composable
fun DesktopHome() {
    var configUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var siteNames by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ArchPlayer", style = MaterialTheme.typography.h4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = configUrl,
                onValueChange = { configUrl = it },
                label = { Text("URL da interface (config TVBox)") },
                modifier = Modifier.width(520.dp)
            )
            Button(onClick = {
                scope.launch {
                    status = "Carregando..."
                    runCatching {
                        val text = withContext(Dispatchers.IO) { fetchText(configUrl.trim()) }
                        val config = TvBoxConfigParser().parse(text)
                        siteNames = config.sites.map { it.name }
                        status = "${config.sites.size} fontes - ${config.lives.size} listas ao vivo"
                    }.onFailure {
                        status = "Erro: ${it.message}"
                    }
                }
            }) {
                Text("Carregar")
            }
        }
        Text(status)
        LazyColumn {
            items(siteNames) { name ->
                Text(name, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private val client = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(10))
    .build()

internal fun fetchText(url: String): String {
    val request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", "ArchPlayer/1.0")
        .GET()
        .build()
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
}
