package br.archplayer.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.archplayer.core.tvbox.TvBoxConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen()
                }
            }
        }
    }
}

@Composable
fun ConfigScreen() {
    var configUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var siteNames by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "ArchPlayer", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = configUrl,
            onValueChange = { configUrl = it },
            label = { Text("URL da interface (config TVBox)") }
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
            Text("Carregar interface")
        }
        Text(text = status)
        siteNames.take(20).forEach { name ->
            Text(text = name, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun fetchText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.setRequestProperty("User-Agent", "ArchPlayer/1.0")
    return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
}
