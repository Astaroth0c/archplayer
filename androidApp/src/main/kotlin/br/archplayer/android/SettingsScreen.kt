package br.archplayer.android

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.archplayer.core.tvbox.TvBoxConfig
import br.archplayer.core.xciptv.XtreamUserInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    userInfo: XtreamUserInfo?,
    activity: Activity,
    onLogout: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuracoes", style = MaterialTheme.typography.headlineSmall)

        Text("Conta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        userInfo?.let { info ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                info.username?.let { Text("Usuario: $it", style = MaterialTheme.typography.bodyMedium) }
                info.status?.let { Text("Status: $it", style = MaterialTheme.typography.bodyMedium) }
                info.expDate?.toLongOrNull()?.let { secs ->
                    Text("Expira em: ${fmt.format(Date(secs * 1000))}", style = MaterialTheme.typography.bodyMedium)
                }
                if (info.activeConnections != null && info.maxConnections != null) {
                    Text("Conexoes: ${info.activeConnections}/${info.maxConnections}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } ?: Text("Login via portal nao configurado.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Atualizacao", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = { UpdateManager.checkAndPrompt(activity) }) {
                Text("Verificar atualizacoes")
            }
            Text(
                "Versao ${UpdateManager.currentVersion(activity)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "O aplicativo verifica novas versoes automaticamente ao abrir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Sair da conta")
        }

    }
}
