package br.archplayer.android

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.archplayer.android.player.PlayerActivity
import br.archplayer.core.live.EpgLookup
import br.archplayer.core.tvbox.TvBoxConfig
import br.archplayer.core.xciptv.XtreamClient
import br.archplayer.core.xciptv.XtreamCredentials
import br.archplayer.core.xciptv.XtreamUserInfo
import br.archplayer.core.xmltv.XmltvParser
import br.archplayer.core.xmltv.XmltvProgramme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSession(
    val client: XtreamClient,
    val credentials: XtreamCredentials,
    val userInfo: XtreamUserInfo?
)

enum class MainTab(val label: String) {
    LIVE("TV ao vivo"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    TVBOX("TVBox"),
    SETTINGS("Config")
}

@Composable
fun ArchPlayerApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var session by remember { mutableStateOf<AppSession?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }
    var epgIndex by remember { mutableStateOf<Map<String, List<XmltvProgramme>>>(emptyMap()) }
    var config by remember { mutableStateOf<TvBoxConfig?>(null) }
    var playRequest by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun doConnect(host: String, user: String, pass: String, save: Boolean) {
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            loginError = "Preencha host, usuario e senha."
            return
        }
        loggingIn = true
        loginError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val creds = XtreamCredentials(host.trim(), user.trim(), pass.trim())
                    val client = XtreamClient(creds)
                    val hs = client.handshake()
                    if (hs.userInfo?.auth == 0) error("Usuario ou senha invalidos.")
                    if (hs.userInfo == null) error("Portal nao respondeu. Verifique host, usuario e senha.")
                    client to hs
                }
            }
            result.onSuccess { (client, hs) ->
                session = AppSession(client, XtreamCredentials(host.trim(), user.trim(), pass.trim()), hs.userInfo)
                if (save) {
                    prefs.edit()
                        .putString("xtream_host", host.trim())
                        .putString("xtream_user", user.trim())
                        .putString("xtream_pass", pass.trim())
                        .apply()
                }
                screen = Screen.Main
                loadEpgAsync(client) { epgIndex = it }
            }.onFailure {
                loginError = friendlyError(it)
            }
            loggingIn = false
        }
    }

    LaunchedEffect(Unit) {
        UpdateManager.checkAndPrompt(activity)
        val savedConfigUrl = prefs.getString("config_url", null)
        if (!savedConfigUrl.isNullOrBlank()) {
            config = withContext(Dispatchers.IO) {
                runCatching { loadTvBoxConfig(savedConfigUrl) }.getOrNull()
            }
        }
        val h = prefs.getString("xtream_host", null)
        val u = prefs.getString("xtream_user", null)
        val p = prefs.getString("xtream_pass", null)
        if (h.isNullOrBlank() || u.isNullOrBlank() || p.isNullOrBlank()) {
            screen = Screen.Login
        } else {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val creds = XtreamCredentials(h, u, p)
                    val client = XtreamClient(creds)
                    val hs = client.handshake()
                    if (hs.userInfo == null) error("invalid")
                    session = AppSession(client, creds, hs.userInfo)
                    loadEpgAsync(client) { idx -> epgIndex = idx }
                    true
                }.getOrElse { false }
            }
            screen = if (ok) Screen.Main else Screen.Login
            if (!ok) loginError = "Sessao anterior expirada. Entre novamente."
        }
    }

    playRequest?.let { (url, ua) ->
        if (url.isNotBlank()) {
            LaunchedEffect(url) {
                PlayerActivity.start(context, url, ua.ifBlank { null })
                playRequest = null
            }
        } else {
            playRequest = null
        }
    }

    when (screen) {
        Screen.Splash -> Splash()
        Screen.Login -> LoginScreen(
            initialHost = prefs.getString("xtream_host", "") ?: "",
            initialUser = prefs.getString("xtream_user", "") ?: "",
            error = loginError,
            loading = loggingIn,
            version = UpdateManager.currentVersion(activity),
            onLogin = { host, user, pass -> doConnect(host, user, pass, true) }
        )
        Screen.Main -> {
            val s = session ?: run { Splash(); return }
            MainShell(
                session = s,
                epgIndex = epgIndex,
                config = config,
                prefs = prefs,
                onPlay = { url, ua -> playRequest = url to (ua ?: "") },
                onConfigLoaded = { config = it },
                onLogout = {
                    prefs.edit().remove("xtream_host").remove("xtream_user").remove("xtream_pass").apply()
                    session = null
                    epgIndex = emptyMap()
                    screen = Screen.Login
                }
            )
        }
    }
}

private fun friendlyError(error: Throwable): String {
    val cause = generateSequence(error as Throwable?) { it.cause }
        .take(6)
        .firstOrNull { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.SocketTimeoutException || it is javax.net.ssl.SSLException }
    when (cause) {
        is java.net.UnknownHostException -> return "Nao foi possivel resolver o endereco do portal. Verifique o host digitado e se o Wi-Fi/4G esta ativo."
        is java.net.ConnectException -> return "O portal recusou a conexao. Tente outro endereco do servidor (ex.: http://x99.lat) ou outra rede."
        is java.net.SocketTimeoutException -> return "O portal nao respondeu a tempo (timeout). Tente outra rede (Wi-Fi ou 4G) ou outro endereco do servidor."
        is javax.net.ssl.SSLException -> return "Falha de SSL com o portal. Tente usar o endereco com http:// na frente."
    }
    val msg = (error.message ?: "Falha na conexao").lineSequence().firstOrNull().orEmpty()
    return if (msg.length > 140) msg.take(137) + "..." else msg
}

private fun loadEpgAsync(client: XtreamClient, onLoaded: (Map<String, List<XmltvProgramme>>) -> Unit) {
    Thread {
        val idx = runCatching {
            EpgLookup.byChannelId(XmltvParser.parse(fetchText(client.epgUrl())))
        }.getOrNull() ?: emptyMap()
        if (idx.isNotEmpty()) onLoaded(idx)
    }.start()
}

@Composable
private fun Splash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "ArchPlayer",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text("IPTV & TVBox Universal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

private enum class Screen { Splash, Login, Main }

@Composable
private fun MainShell(
    session: AppSession,
    epgIndex: Map<String, List<XmltvProgramme>>,
    config: TvBoxConfig?,
    prefs: SharedPreferences,
    onPlay: (String, String?) -> Unit,
    onConfigLoaded: (TvBoxConfig) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.LIVE) }
    val activity = LocalContext.current as ComponentActivity

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            when (item) {
                                MainTab.LIVE -> Icon(Icons.Filled.LiveTv, contentDescription = item.label)
                                MainTab.MOVIES -> Icon(Icons.Filled.Movie, contentDescription = item.label)
                                MainTab.SERIES -> Icon(Icons.Filled.Tv, contentDescription = item.label)
                                MainTab.TVBOX -> Icon(Icons.Filled.VideoLibrary, contentDescription = item.label)
                                MainTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = item.label)
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
                MainTab.LIVE -> LiveScreen(
                    client = session.client,
                    epgIndex = epgIndex,
                    tvboxConfig = config,
                    onPlay = onPlay
                )
                MainTab.MOVIES -> MoviesScreen(client = session.client, onPlay = onPlay)
                MainTab.SERIES -> SeriesScreen(client = session.client, onPlay = onPlay)
                MainTab.TVBOX -> SourcesScreen(config = config, onPlay = onPlay)
                MainTab.SETTINGS -> SettingsScreen(
                    userInfo = session.userInfo,
                    prefs = prefs,
                    config = config,
                    activity = activity,
                    onConfigLoaded = onConfigLoaded,
                    onLogout = onLogout
                )
            }
        }
    }
}
