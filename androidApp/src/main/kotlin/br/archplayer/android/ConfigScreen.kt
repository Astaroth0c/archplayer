package br.archplayer.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import br.archplayer.core.tvbox.TvBoxConfig
import kotlinx.coroutines.launch

@Composable
fun ConfigScreen(
    status: String,
    onLoaded: (TvBoxConfig, String) -> Unit,
    onStatus: (String) -> Unit
) {
    var configUrl by remember { mutableStateOf("") }
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
            label = { Text("URL da interface (config TVBox .json)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            onStatus("Carregando...")
            scope.launch {
                runCatching {
                    loadTvBoxConfig(configUrl.trim())
                }.onSuccess { loaded ->
                    onLoaded(loaded, configUrl.trim())
                }.onFailure {
                    onStatus("Erro: ${it.message}")
                }
            }
        }) {
            Text("Carregar interface")
        }
        Text(text = status)

        Text(
            text = "Dica: cole o endereço de um arquivo .json no formato TVBox " +
                "(sites/parses/lives). Veja docs/exemplo-interface.json do projeto.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
