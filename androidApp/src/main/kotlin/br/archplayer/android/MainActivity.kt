package br.archplayer.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import br.archplayer.android.player.PlayerActivity
import br.archplayer.core.tvbox.TvBoxConfig
import br.archplayer.core.tvbox.TvBoxConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class AppTab(val label: String) {
    SOURCES("Fontes"),
    LIVE("Ao vivo"),
    CONFIG("Config")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var tab by remember { mutableStateOf(AppTab.CONFIG) }
    var config by remember { mutableStateOf<TvBoxConfig?>(null) }
    var configStatus by remember { mutableStateOf("") }
    var playRequest by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("config_url", null)
        if (!saved.isNullOrBlank()) {
            val loaded = runCatching { loadTvBoxConfig(saved) }.getOrNull()
            if (loaded != null) {
                config = loaded
                tab = AppTab.SOURCES
                configStatus = "Interface carregada"
            }
        }
        UpdateManager.checkAndPrompt(context as ComponentActivity)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            when (item) {
                                AppTab.SOURCES -> Icon(Icons.Filled.VideoLibrary, contentDescription = item.label)
                                AppTab.LIVE -> Icon(Icons.Filled.LiveTv, contentDescription = item.label)
                                AppTab.CONFIG -> Icon(Icons.Filled.Settings, contentDescription = item.label)
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.SOURCES -> SourcesScreen(
                    config = config,
                    onPlay = { url, ua -> playRequest = url to (ua ?: "") }
                )
                AppTab.LIVE -> LiveScreen(
                    config = config,
                    prefs = prefs,
                    onPlay = { url, ua -> playRequest = url to (ua ?: "") }
                )
                AppTab.CONFIG -> ConfigScreen(
                    status = configStatus,
                    onLoaded = { loaded, url ->
                        config = loaded
                        prefs.edit().putString("config_url", url).apply()
                        tab = AppTab.SOURCES
                        configStatus = "Interface carregada: ${loaded.sites.size} fontes"
                    },
                    onStatus = { configStatus = it }
                )
            }
        }
    }

    playRequest?.let { (url, ua) ->
        if (url.isNotBlank()) {
            androidx.compose.runtime.LaunchedEffect(url) {
                PlayerActivity.start(context, url, ua.ifBlank { null })
                playRequest = null
            }
        } else {
            playRequest = null
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
