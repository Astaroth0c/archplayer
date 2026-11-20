package br.archplayer.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.archplayer.core.tvbox.TvBoxConfig
import br.archplayer.core.tvbox.TvBoxConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = ArchColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArchPlayerApp()
                }
            }
        }
    }
}

suspend fun loadTvBoxConfig(url: String): TvBoxConfig = withContext(Dispatchers.IO) {
    TvBoxConfigParser().parse(fetchText(url))
}

fun fetchText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "ArchPlayer/1.0")
    return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
}

const val PREFS_NAME = "archplayer"

val ArchColors = darkColorScheme(
    primary = Color(0xFF4C8DFF),
    onPrimary = Color.White,
    secondary = Color(0xFF7C5CFF),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE7ECF4),
    surface = Color(0xFF11151F),
    onSurface = Color(0xFFE7ECF4),
    surfaceVariant = Color(0xFF1A2233),
    onSurfaceVariant = Color(0xFF9AA7BD),
    error = Color(0xFFFF6B6B)
)
